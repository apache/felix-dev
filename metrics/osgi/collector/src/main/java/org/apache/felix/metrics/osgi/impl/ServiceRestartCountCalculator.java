/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.metrics.osgi.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.felix.metrics.osgi.ServiceRestartCounter;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;

public class ServiceRestartCountCalculator implements ServiceListener {

    private static final String[] GENERAL_IDENTIFIER_PROPERTIES = new String[] { Constants.SERVICE_PID, "component.name", "jmx.objectname" };
    private static final Map<String, Collection<String>> SPECIFIC_IDENTIFIER_PROPERTIES = new HashMap<>();
    static {
        SPECIFIC_IDENTIFIER_PROPERTIES.put("org.apache.sling.commons.metrics.Gauge", Arrays.asList("name"));
        SPECIFIC_IDENTIFIER_PROPERTIES.put("org.apache.sling.spi.resource.provider.ResourceProvider", Arrays.asList("provider.root"));
        SPECIFIC_IDENTIFIER_PROPERTIES.put("org.apache.sling.servlets.post.PostOperation", Arrays.asList("sling.post.operation"));
        SPECIFIC_IDENTIFIER_PROPERTIES.put("javax.servlet.Servlet", Arrays.asList("felix.webconsole.label"));
        SPECIFIC_IDENTIFIER_PROPERTIES.put("org.apache.felix.inventory.InventoryPrinter", Arrays.asList("felix.inventory.printer.name"));
    }
    
    private final Map<ServiceIdentifier, ServiceRegistrationsTracker> registrations = new HashMap<>();
    private final Map<String, Integer> unidentifiedRegistrationsByClassName = new HashMap<>();
    
    @Override
    public void serviceChanged(ServiceEvent event) {
        
        if ( shouldIgnore(event) ) 
            return;

        ServiceIdentifier id = tryFindIdFromGeneralProperties(event);
        if ( id == null )
            id = tryFindIdFromSpecificProperties(event);
        
        if ( id == null ) {
            logUnknownService(event);
            if ( event.getType() == ServiceEvent.UNREGISTERING )
                recordUnknownServiceUnregistration(event);
            return;
        }

        ServiceRegistrationsTracker tracker;
        synchronized (registrations) {
            
            if ( event.getType() == ServiceEvent.REGISTERED ) {
                tracker = registrations.computeIfAbsent(id, ServiceRegistrationsTracker::new);
                tracker.registered();
            } else if ( event.getType() == ServiceEvent.UNREGISTERING ) {
                
                tracker = registrations.get(id);
                if (tracker == null) {
                    Log.debug(getClass(), "Service with identifier {} was unregistered, but no previous registration data was found", id);
                    return;
                }
                tracker.unregistered();
            }
        }
    }

    private boolean shouldIgnore(ServiceEvent event) {
        
        return event.getType() != ServiceEvent.REGISTERED && event.getType() != ServiceEvent.UNREGISTERING;
    }

    private ServiceIdentifier tryFindIdFromGeneralProperties(ServiceEvent event) {
        for ( String identifierProp : GENERAL_IDENTIFIER_PROPERTIES ) {
            Object identifierVal = event.getServiceReference().getProperty(identifierProp);
            if ( identifierVal != null )
                return new ServiceIdentifier(identifierProp, identifierVal.toString() );
        }
        
        return null;
    }

    private ServiceIdentifier tryFindIdFromSpecificProperties(ServiceEvent event) {
        for ( Map.Entry<String, Collection<String>> entry : SPECIFIC_IDENTIFIER_PROPERTIES.entrySet() ) {
            String[] classNames = (String[]) event.getServiceReference().getProperty(Constants.OBJECTCLASS);
            for ( String className : classNames ) {
                if ( entry.getKey().equals(className) ) {
                    StringBuilder propKey = new StringBuilder();
                    StringBuilder propValue = new StringBuilder();
                    
                    for ( String idPropName : entry.getValue() ) {
                        Object idPropVal = event.getServiceReference().getProperty(idPropName);
                        if ( idPropVal != null ) {
                            propKey.append(idPropName).append('~');
                            propValue.append(idPropVal).append('~');
                        }
                    }
                    
                    if ( propKey.length() != 0 ) {
                        propKey.deleteCharAt(propKey.length() - 1);
                        propValue.deleteCharAt(propValue.length() - 1);
                        ServiceIdentifier id = new ServiceIdentifier(propKey.toString(), propValue.toString());
                        id.setAdditionalInfo(Constants.OBJECTCLASS + "=" + Arrays.toString(classNames));
                        return id;
                    }
                }
            }
        }
        
        return null;
    }

    private void logUnknownService(ServiceEvent event) {
        if ( event.getType() == ServiceEvent.UNREGISTERING ) {
            Map<String, Object> props = new HashMap<>();
            for ( String propertyName : event.getServiceReference().getPropertyKeys() ) {
                Object propVal = event.getServiceReference().getProperty(propertyName);
                if ( propVal.getClass() == String[].class )
                    propVal = Arrays.toString((String[]) propVal);
                props.put(propertyName, propVal);
            }

            Log.debug(getClass(), "Ignoring unregistration of service with props {}, as it has none of identifier properties {}", props, Arrays.toString(GENERAL_IDENTIFIER_PROPERTIES));
        }
    }

    private void recordUnknownServiceUnregistration(ServiceEvent event) {
        String[] classNames = (String[]) event.getServiceReference().getProperty(Constants.OBJECTCLASS);
        synchronized (unidentifiedRegistrationsByClassName) {
            for ( String className : classNames )
                unidentifiedRegistrationsByClassName.compute(className, (k,v) -> v == null ? 1 : ++v);
        }
    }
    
    // visible for testing
    Map<ServiceIdentifier, ServiceRegistrationsTracker> getRegistrations() {
        synchronized (registrations) {
            return new HashMap<>(registrations);
        }
    }
    
    // visible for testing
    Map<String, Integer> getUnidentifiedRegistrationsByClassName() {
        synchronized (unidentifiedRegistrationsByClassName) {
            return unidentifiedRegistrationsByClassName;
        }
    }
    
    public List<ServiceRestartCounter> getServiceRestartCounters() {
        synchronized (registrations) {
            return registrations.values().stream()
                .filter( r -> r.restartCount() > 0)
                .map( ServiceRegistrationsTracker::toServiceRestartCounter )
                .collect(Collectors.toList());
        }
    }
    
    static class ServiceIdentifier {
        private String key;
        private String value;
        private String additionalInfo;

        public ServiceIdentifier(String key, String value) {
            this.key = key;
            this.value = value;
        }
        
        public void setAdditionalInfo(String additionalInfo) {
            this.additionalInfo = additionalInfo;
        }

        @Override
        public int hashCode() {
            
            return Objects.hash(key, value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ServiceIdentifier other = (ServiceIdentifier) obj;
            
            return Objects.equals(key, other.key) && Objects.equals(value, other.value);
        }
        
        @Override
        public String toString() {
            return this.key + "=" + this.value + ( additionalInfo != null ? "(" + additionalInfo + ")" : "") ;
        }
    }
    
    static class ServiceRegistrationsTracker {
        private final ServiceIdentifier id;
        private int registrationCount;
        private int unregistrationCount;
        
        public ServiceRegistrationsTracker(ServiceIdentifier id) {
            this.id = id;
        }
        
        public void registered() {
            this.registrationCount++;
        }
        
        public void unregistered() {
            this.unregistrationCount++;
        }
        
        public int restartCount() {
            if ( unregistrationCount == 0 )
                return 0;
            
            return registrationCount - 1;
        }
        
        public ServiceRestartCounter toServiceRestartCounter() {
            return new ServiceRestartCounter(id.toString(), restartCount());
        }
    }
}

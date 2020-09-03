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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import org.apache.felix.metrics.osgi.impl.ServiceRestartCountCalculator;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

public class ServiceRestartCountCalculatorTest {

    @Test
    public void ignoredEventTypes() {
        
        ServiceRestartCountCalculator srcc = new ServiceRestartCountCalculator();
        srcc.serviceChanged(new ServiceEvent(ServiceEvent.MODIFIED, new DummyServiceReference<>(new HashMap<>())));
        srcc.serviceChanged(new ServiceEvent(ServiceEvent.MODIFIED_ENDMATCH, new DummyServiceReference<>(new HashMap<>())));
        
        assertThat(srcc.getRegistrations().size(), equalTo(0));
    }

    @Test
    public void serviceWithServicePidProperty() {
        
        assertServiceWithPropertyIsTracked(Constants.SERVICE_PID);
    }

    @Test
    public void serviceWithComponentNameProperty() {
        
        assertServiceWithPropertyIsTracked("component.name");
    }

    @Test
    public void serviceWithJmxObjectNameProperty() {
        
        assertServiceWithPropertyIsTracked("jmx.objectname");
    }

    @Test
    public void metricsGaugesAreTracked() {
        HashMap<String, Object> props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[] { "org.apache.sling.commons.metrics.Gauge" });
        props.put("name", "commons.threads.tp.script-cache-thread-pool.Name");
        DummyServiceReference<Object> dsr = new DummyServiceReference<>(props);
        
        ServiceRestartCountCalculator srcc = new ServiceRestartCountCalculator();
        srcc.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, dsr));
        
        assertThat(srcc.getRegistrations().size(), equalTo(1));
    }

    @Test
    public void unknownServiceIsNotTracked() {
        HashMap<String, Object> props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[] { "foo" });
        DummyServiceReference<Object> dsr = new DummyServiceReference<>(props);
        
        ServiceRestartCountCalculator srcc = new ServiceRestartCountCalculator();
        srcc.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, dsr));
        
        assertThat(srcc.getRegistrations().size(), equalTo(0));
        assertThat(srcc.getUnidentifiedRegistrationsByClassName().size(), equalTo(0));
    }
    
    @Test
    public void unknownServiceUnregistrationsAreTracked() {
        HashMap<String, Object> props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[] { "foo", "bar" });
        DummyServiceReference<Object> sr1 = new DummyServiceReference<>(props);
        
        HashMap<String, Object> props2 = new HashMap<>();
        props2.put(Constants.OBJECTCLASS, new String[] { "foo"} );
        DummyServiceReference<Object> sr2 = new DummyServiceReference<>(props2);
        
        ServiceRestartCountCalculator srcc = new ServiceRestartCountCalculator();
        srcc.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sr1));
        srcc.serviceChanged(new ServiceEvent(ServiceEvent.UNREGISTERING, sr1));
        
        srcc.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sr2));
        srcc.serviceChanged(new ServiceEvent(ServiceEvent.UNREGISTERING, sr2));
        
        assertThat(srcc.getRegistrations().size(), equalTo(0));
        Map<String, Integer> unidentifiedRegistrations = srcc.getUnidentifiedRegistrationsByClassName();
        assertThat(unidentifiedRegistrations.size(), equalTo(2));
        assertThat(unidentifiedRegistrations.get("foo"), equalTo(2));
        assertThat(unidentifiedRegistrations.get("bar"), equalTo(1));
    }
    
    private void assertServiceWithPropertyIsTracked(String propertyName) {
        
        HashMap<String, Object> props = new HashMap<>();
        props.put(propertyName, new String[] { "foo.bar" });
        DummyServiceReference<Object> dsr = new DummyServiceReference<>(props);
        
        ServiceRestartCountCalculator srcc = new ServiceRestartCountCalculator();
        srcc.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, dsr));
        
        assertThat(srcc.getRegistrations().size(), CoreMatchers.equalTo(1));
    }
    
    static class DummyServiceRegistration<S> implements ServiceRegistration<S> {
        
        private final DummyServiceReference<S> sr;

        public DummyServiceRegistration(Map<String, Object> props) {
            this.sr = new DummyServiceReference<>(props);
        }

        @Override
        public ServiceReference<S> getReference() {
            return sr;
        }

        @Override
        public void setProperties(Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
            
        }

        @Override
        public void unregister() {
            throw new UnsupportedOperationException();            
        }
    }
    
    static class DummyServiceReference<S> implements ServiceReference<S> {
        
        private final Map<String, Object> props;

        public DummyServiceReference(Map<String, Object> props) {
            this.props = props;
        }

        @Override
        public Object getProperty(String key) {
            return props.get(key);
        }

        @Override
        public String[] getPropertyKeys() {
            return props.keySet().toArray(new String[0]);
        }

        @Override
        public Bundle getBundle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle[] getUsingBundles() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAssignableTo(Bundle bundle, String className) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(Object reference) {
            throw new UnsupportedOperationException();
        }
        
    }

}

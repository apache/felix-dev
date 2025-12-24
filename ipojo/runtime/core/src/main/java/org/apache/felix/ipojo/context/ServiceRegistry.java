/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.ipojo.context;

import org.apache.felix.ipojo.ComponentInstance;
import org.apache.felix.ipojo.util.Logger;
import org.osgi.framework.*;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Internal Service Registry. This class is used for in the composition.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class ServiceRegistry {

    /**
     * Service Id index.
     */
    private final AtomicLong mServiceId = new AtomicLong(1L);
    /**
     * A "real" bundle context to create LDAP filter.
     */
    private final BundleContext mContext; // BundleContext to create Filter
    /**
     * Registry logger.
     */
    private final Logger mLogger;
    /**
     * List of service listeners. It is enough to use nonblocking thread-safe collection
     */
    private List<ListenerInfo> mListeners = new CopyOnWriteArrayList<>(); // ListenerInfo List
    /**
     * List of service registration. It is enough to use nonblocking thread-safe collection
     */
    private List<ServiceRegistration> mRegs = new CopyOnWriteArrayList<>();

    /**
     * Constructor.
     *
     * @param context : bundle context.
     */
    public ServiceRegistry(BundleContext context) {
        mContext = context;
        mLogger = new Logger(mContext, "Registry logger " + mContext.getBundle().getBundleId());
    }

    /**
     * Add a given service listener with no filter.
     *
     * @param arg0 : the service listener to add
     */
    public void addServiceListener(ServiceListener arg0) {
        mListeners.add(new ListenerInfo(arg0, null));
    }

    /**
     * Unget a service.
     *
     * @param instance : instance releasing the service.
     * @param ref      : released reference.
     * @return true if the unget success
     */
    public boolean ungetService(ComponentInstance instance, ServiceReference ref) {
        ServiceRegistrationImpl reg = ((ServiceReferenceImpl) ref).getServiceRegistration();
        if (reg.isValid()) {
            reg.ungetService(instance, reg.getService());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Unregister a service listener.
     *
     * @param arg0 : the service listener to remove
     */
    public void removeServiceListener(ServiceListener arg0) {
        mListeners.removeIf(listenerInfo -> listenerInfo.mListener == arg0);
    }

    /**
     * Register a service.
     *
     * @param instance : provider instance.
     * @param clazz    : provided interface.
     * @param svcObj   : service object of service factory object.
     * @param dict     : service properties.
     * @return the created service registration.
     */
    public ServiceRegistration registerService(ComponentInstance instance, String clazz, Object svcObj, Dictionary dict) {
        final ServiceRegistrationImpl reg = new ServiceRegistrationImpl(this, instance, new String[]{clazz}, mServiceId.getAndIncrement(), svcObj, dict);
        mRegs.add(reg);
        fireServiceChanged(new ServiceEvent(ServiceEvent.REGISTERED, reg.getReference()));
        return reg;
    }

    /**
     * Register a service.
     *
     * @param instance : provider instance.
     * @param clazzes  : provided interfaces.
     * @param svcObj   : service object of service factory object.
     * @param dict     : service properties.
     * @return the created service registration.
     */
    public ServiceRegistration registerService(ComponentInstance instance, String[] clazzes, Object svcObj, Dictionary dict) {
        final ServiceRegistrationImpl reg = new ServiceRegistrationImpl(this, instance, clazzes, mServiceId.getAndIncrement(), svcObj, dict);
        mRegs.add(reg);
        fireServiceChanged(new ServiceEvent(ServiceEvent.REGISTERED, reg.getReference()));
        return reg;
    }

    /**
     * Dispatch a service event.
     *
     * @param event : the service to dispatch
     */
    private void fireServiceChanged(ServiceEvent event) {
        for (ListenerInfo listenerInfo : mListeners) {
            final ServiceReference ref = event.getServiceReference();
            if (listenerInfo.mFilter == null) {
                listenerInfo.mListener.serviceChanged(event);
            }
            Dictionary props = ((ServiceReferenceImpl) ref).getProperties();
            if (listenerInfo.mFilter != null && listenerInfo.mFilter.match(props)) {
                listenerInfo.mListener.serviceChanged(event);
            }
        }

    }

    /**
     * Get available (and accessible) service references.
     *
     * @param className : required interface
     * @param expr      : LDAP filter
     * @return : the list of available service references.
     * @throws InvalidSyntaxException occurs when the LDAP filter is malformed.
     */
    public ServiceReference[] getServiceReferences(String className, String expr) throws InvalidSyntaxException {
        // Define filter if expression is not null.
        Filter filter = null;
        if (expr != null) {
            filter = mContext.createFilter(expr);
        }
        final List<ServiceReference<?>> refs = new ArrayList<>();
        for (ServiceRegistration m_reg : mRegs) {
            ServiceRegistrationImpl reg = (ServiceRegistrationImpl) m_reg;
            // Determine if the registered services matches the search
            // criteria.
            boolean matched = false;

            // If className is null, then look at filter only.
            if ((className == null) && ((filter == null) || filter.match(reg.getProperties()))) {
                matched = true;
            } else if (className != null) {
                // If className is not null, then first match the
                // objectClass property before looking at the
                // filter.
                Dictionary props = reg.getProperties();
                String[] objectClass = (String[]) props.get(Constants.OBJECTCLASS);
                for (String aClass : objectClass) {
                    if (aClass.equals(className) && ((filter == null) || filter.match(props))) {
                        matched = true;
                        break;
                    }
                }
            }

            // Add reference if it was a match.
            if (matched) {
                refs.add(reg.getReference());
            }
        }

        if (!refs.isEmpty()) {
            return refs.toArray(new ServiceReference[refs.size()]);
        }
        // To be honest it is not good. But it is stayed for compatibility
        return null;
    }

    /**
     * Look for a service reference.
     *
     * @param clazz : required interface.
     * @return the first available provider or null if none available.
     */
    public ServiceReference getServiceReference(String clazz) {
        try {
            ServiceReference[] refs = getServiceReferences(clazz, null);
            if (refs != null) {
                return refs[0];
            } // If the refs != null we are sure that it exists one reference or more.
        } catch (InvalidSyntaxException ex) {
            // Cannot happen : null filter.
        }
        return null;
    }

    /**
     * Get a service object.
     *
     * @param instance : component instance requiring the service.
     * @param ref      : the required reference.
     * @return the service object.
     */
    public Object getService(ComponentInstance instance, ServiceReference ref) {
        // Look for the service registration for this ref
        ServiceRegistrationImpl reg = ((ServiceReferenceImpl) ref).getServiceRegistration();
        if (reg.isValid()) {
            // Delegate the service providing to the service registration
            return reg.getService();
        } else {
            return null;
        }
    }

    /**
     * Get all service references consistent with the given interface and
     * filter.
     *
     * @param clazz  : the required interface.
     * @param filter : the LDAP filter.
     * @return the list of all service reference or null if none available.
     * @throws InvalidSyntaxException occurs when the LDAP filter is malformed.
     */
    public ServiceReference[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
        // Can delegate on getServiceReference, indeed their is no test on
        // the "modularity" conflict.
        return getServiceReferences(clazz, filter);
    }

    /**
     * Add a service listener with a filter.
     *
     * @param listener : the service listener to add
     * @param filter   : LDAP filter
     */
    public void addServiceListener(ServiceListener listener, String filter) {
        // If the filter is null, subscribe with no filter.
        if (filter == null) {
            addServiceListener(listener);
            return;
        }

        try {
            final ListenerInfo info = new ListenerInfo(listener, mContext.createFilter(filter));
            mListeners.add(info);
        } catch (InvalidSyntaxException ex) {
            mLogger.log(Logger.ERROR, ex.getMessage(), ex);
        }

    }

    /**
     * Dispatch a service properties modified event.
     *
     * @param reg : the implicated service registration.
     */
    public void servicePropertiesModified(ServiceRegistrationImpl reg) {
        fireServiceChanged(new ServiceEvent(ServiceEvent.MODIFIED, reg.getReference()));
    }

    /**
     * Unregister a service.
     *
     * @param reg : the service registration to unregister
     */
    public void unregisterService(ServiceRegistrationImpl reg) {
        // We should fire event only if registration is removed
        if (mRegs.remove(reg)) {
            fireServiceChanged(new ServiceEvent(ServiceEvent.UNREGISTERING, reg.getReference()));
        }
    }

    /**
     * Reset the service registry.
     */
    public void reset() {
        mServiceId.set(1L);
        mListeners = new CopyOnWriteArrayList<>();
        mRegs = new CopyOnWriteArrayList<>();
    }

    /**
     * Listener info structure.
     */
    private static class ListenerInfo {
        /**
         * Listener object.
         */
        private final ServiceListener mListener;
        /**
         * Filter associated with the filter.
         */
        private final Filter mFilter;

        private ListenerInfo(ServiceListener mListener, Filter mFilter) {
            this.mListener = mListener;
            this.mFilter = mFilter;
        }
    }
}

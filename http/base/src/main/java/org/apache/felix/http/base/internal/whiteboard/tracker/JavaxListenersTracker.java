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
package org.apache.felix.http.base.internal.whiteboard.tracker;

import java.util.EventListener;
import java.util.HashSet;
import java.util.Set;

import org.apache.felix.http.base.internal.jakartawrappers.EventListenerWrapper;
import org.apache.felix.http.base.internal.runtime.ListenerInfo;
import org.apache.felix.http.base.internal.runtime.WhiteboardServiceInfo;
import org.apache.felix.http.base.internal.util.ServiceUtils;
import org.apache.felix.http.base.internal.whiteboard.WhiteboardManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

/**
 * Tracker for http whiteboard listeners
 */
public final class JavaxListenersTracker extends WhiteboardServiceTracker<EventListener>
{
    /**
     * Create a filter expression for all supported listener.
     */
    private static String createListenersFilterExpression()
    {
        return String.format("(&" +
                             "(|(objectClass=%s)(objectClass=%s)(objectClass=%s)(objectClass=%s)(objectClass=%s)(objectClass=%s)(objectClass=%s))" +
                             "(%s=*)(!(%s~=false)))",
                javax.servlet.http.HttpSessionAttributeListener.class.getName(),
                javax.servlet.http.HttpSessionIdListener.class.getName(),
                javax.servlet.http.HttpSessionListener.class.getName(),
                javax.servlet.ServletContextListener.class.getName(),
                javax.servlet.ServletContextAttributeListener.class.getName(),
                javax.servlet.ServletRequestListener.class.getName(),
                javax.servlet.ServletRequestAttributeListener.class.getName(),
                HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER,
                HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER);
    }

    /**
     * Create new tracker
     * @param context bundle context
     * @param manager whiteboard manager
     */
    public JavaxListenersTracker(final BundleContext context, final WhiteboardManager manager)
    {
        super(manager, context, createListenersFilterExpression());
    }

    @Override
    protected WhiteboardServiceInfo<EventListener> getServiceInfo(final ServiceReference<EventListener> ref) {
        return new JavaxListenerInfo(ref);
    }

    /**
     * Info for javax listeners
     */
    private static final class JavaxListenerInfo extends ListenerInfo {

        /**
         * Create new info
         * @param ref Reference to the listener
         */
        public JavaxListenerInfo(final ServiceReference<EventListener> ref) {
            super(ref, getListenerTypes(ref), getDTOListenerTypes(ref));
        }

        @Override
        public EventListener getService(final BundleContext bundleContext) {
            final EventListener listener = ServiceUtils.safeGetServiceObjects(bundleContext, this.getServiceReference());
            if ( listener == null ) {
                return null;
            }
            return new EventListenerWrapper(listener, this.getListenerTypes());
        }

        @Override
        public void ungetService(final BundleContext bundleContext, final EventListener service) {
            if ( service instanceof EventListenerWrapper ) {
                final EventListener listener = ((EventListenerWrapper)service).getListener();
                ServiceUtils.safeUngetServiceObjects(bundleContext, this.getServiceReference(), listener);
            }
        }

        private static String[] getDTOListenerTypes(final ServiceReference<EventListener> ref) {
            final Set<String> services = new HashSet<>();
            final String[] objectClass = (String[])ref.getProperty(Constants.OBJECTCLASS);
            for(final String v : objectClass) {
                if (javax.servlet.http.HttpSessionAttributeListener.class.getName().equals(v)) {
                    services.add(javax.servlet.http.HttpSessionAttributeListener.class.getName());

                } else if (javax.servlet.http.HttpSessionIdListener.class.getName().equals(v)) {
                    services.add(javax.servlet.http.HttpSessionIdListener.class.getName());

                } else if (javax.servlet.http.HttpSessionListener.class.getName().equals(v)) {
                    services.add(javax.servlet.http.HttpSessionListener.class.getName());

                } else if (javax.servlet.ServletContextListener.class.getName().equals(v)) {
                    services.add(javax.servlet.ServletContextListener.class.getName());

                } else if (javax.servlet.ServletContextAttributeListener.class.getName().equals(v)) {
                    services.add(javax.servlet.ServletContextAttributeListener.class.getName());

                } else if (javax.servlet.ServletRequestListener.class.getName().equals(v)) {
                    services.add(javax.servlet.ServletRequestListener.class.getName());

                } else if (javax.servlet.ServletRequestAttributeListener.class.getName().equals(v)) {
                    services.add(javax.servlet.ServletRequestAttributeListener.class.getName());
                }
            }
            return services.toArray(new String[services.size()]);
        }

        private static Set<String> getListenerTypes(final ServiceReference<EventListener> ref) {
            final Set<String> services = new HashSet<>();
            final String[] objectClass = (String[])ref.getProperty(Constants.OBJECTCLASS);
            for(final String v : objectClass) {
                if (javax.servlet.http.HttpSessionAttributeListener.class.getName().equals(v)) {
                    services.add(HttpSessionAttributeListener.class.getName());

                } else if (javax.servlet.http.HttpSessionIdListener.class.getName().equals(v)) {
                    services.add(HttpSessionIdListener.class.getName());

                } else if (javax.servlet.http.HttpSessionListener.class.getName().equals(v)) {
                    services.add(HttpSessionListener.class.getName());

                } else if (javax.servlet.ServletContextListener.class.getName().equals(v)) {
                    services.add(ServletContextListener.class.getName());

                } else if (javax.servlet.ServletContextAttributeListener.class.getName().equals(v)) {
                    services.add(ServletContextAttributeListener.class.getName());

                } else if (javax.servlet.ServletRequestListener.class.getName().equals(v)) {
                    services.add(ServletRequestListener.class.getName());

                } else if (javax.servlet.ServletRequestAttributeListener.class.getName().equals(v)) {
                    services.add(ServletRequestAttributeListener.class.getName());
                }
            }
            return services;
        }
    }
}


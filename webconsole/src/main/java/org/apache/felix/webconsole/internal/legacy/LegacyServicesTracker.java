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
package org.apache.felix.webconsole.internal.legacy;


import java.io.Closeable;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.Servlet;

import org.apache.felix.http.jakartawrappers.ServletWrapper;
import org.apache.felix.http.javaxwrappers.HttpServletRequestWrapper;
import org.apache.felix.http.javaxwrappers.HttpServletResponseWrapper;
import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.apache.felix.webconsole.WebConsoleSecurityProvider;
import org.apache.felix.webconsole.WebConsoleSecurityProvider2;
import org.apache.felix.webconsole.WebConsoleSecurityProvider3;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.servlet.BasicWebConsoleSecurityProvider;
import org.apache.felix.webconsole.internal.servlet.Plugin;
import org.apache.felix.webconsole.internal.servlet.PluginHolder;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.apache.felix.webconsole.spi.SecurityProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@SuppressWarnings("deprecation")
public class LegacyServicesTracker implements Closeable, ServiceTrackerCustomizer<Servlet, LegacyServicesTracker.LegacyServletPlugin> {

    private final ServiceTracker<Servlet, LegacyServletPlugin> servletTracker;

    private final PluginHolder pluginHolder;

    private final LegacySecurityProviderTracker securityProviderTracker;

    public LegacyServicesTracker( final PluginHolder pluginHolder, final BundleContext context ) {
        // try to load wrapper class to fail early
        new org.apache.felix.http.javaxwrappers.ServletWrapper(null);
        this.pluginHolder = pluginHolder;
        Filter filter = null;
        try {
            filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "=" + Servlet.class.getName() + 
                ")(" + ServletConstants.PLUGIN_LABEL + "=*))");
        } catch (final InvalidSyntaxException e) {
            // not expected, thus fail hard
            throw new InternalError( "Failed creating filter: " + e.getMessage() );
        }
        this.servletTracker = new ServiceTracker<>(context, filter, this);
        servletTracker.open();
        this.securityProviderTracker = new LegacySecurityProviderTracker(pluginHolder, context);
    }

    @Override
    public void close() {
        this.servletTracker.close();
        this.securityProviderTracker.close();
    }

    @Override
    public LegacyServletPlugin addingService( final ServiceReference<Servlet> reference ) {
        final String label = Util.getStringProperty( reference, ServletConstants.PLUGIN_LABEL );
        if ( label != null ) {
            this.pluginHolder.getOsgiManager().log(LogService.LOG_WARNING,
                "Legacy webconsole plugin found. Update this to the Jakarta Servlet API: "  + reference);

            final LegacyServletPlugin plugin = new LegacyServletPlugin(this.pluginHolder, reference, label);
            pluginHolder.addPlugin(plugin);
            return plugin;
        }
        return null;
    }

    @Override
    public void modifiedService( final ServiceReference<Servlet> reference, final LegacyServletPlugin service ) {
        this.removedService(reference, service);
        this.addingService(reference);
    }

    @Override
    public void removedService( final ServiceReference<Servlet> reference, final LegacyServletPlugin service ) {
        this.pluginHolder.removePlugin(service);
    }

    public static class LegacyServletPlugin extends Plugin {
            
        @SuppressWarnings({ "rawtypes", "unchecked"})
        public LegacyServletPlugin(final PluginHolder holder, final ServiceReference<Servlet> serviceReference, final String label) {
            super(holder, (ServiceReference)serviceReference, label);
        }

        @SuppressWarnings({ "rawtypes", "unchecked"})
        @Override
        protected jakarta.servlet.Servlet doGetConsolePlugin() {
            Servlet servlet = (Servlet) getHolder().getBundleContext().getService( (ServiceReference)this.getServiceReference() );
            if (servlet != null) {
                if ( servlet instanceof AbstractWebConsolePlugin ) {
                    if (this.title == null) {
                        this.title = ((AbstractWebConsolePlugin)servlet).getTitle();
                    }
                    if (this.category == null) {
                        this.category = ((AbstractWebConsolePlugin)servlet).getCategory();
                    }
                } else {
                    servlet = new WebConsolePluginAdapter(getLabel(), servlet, (ServiceReference)this.getServiceReference());
                }
                return new ServletWrapper(servlet);
            }
            return null;
        }
    }

    public static class LegacySecurityProviderTracker implements ServiceTrackerCustomizer<WebConsoleSecurityProvider, ServiceRegistration<SecurityProvider>> {

        private final ServiceTracker<WebConsoleSecurityProvider, ServiceRegistration<SecurityProvider>> tracker;

        private final BundleContext bundleContext;

        private final PluginHolder pluginHolder;

        public LegacySecurityProviderTracker(final PluginHolder holder, final BundleContext context ) {
            this.bundleContext = context;
            this.pluginHolder = holder;
            this.tracker = new ServiceTracker<>(context, WebConsoleSecurityProvider.class, this);
            tracker.open();
        }

        public void close() {
            tracker.close();
        }

        @Override
        public ServiceRegistration<SecurityProvider> addingService( final ServiceReference<WebConsoleSecurityProvider> reference ) {
            this.pluginHolder.getOsgiManager().log(LogService.LOG_WARNING,
                "Legacy webconsole plugin found. Update this to the Jakarta Servlet API: "  + reference);
            final WebConsoleSecurityProvider provider = this.bundleContext.getService(reference);
            if ( provider != null ) {
                final SecurityProvider wrapper = provider instanceof WebConsoleSecurityProvider2 
                    ? new SecurityProviderWrapper(provider)
                    : new BasicWebConsoleSecurityProvider(this.bundleContext) {

                        @Override
                        public Object authenticate(final String username, final String password) {
                            return provider.authenticate(username, password);
                        }

                        @Override
                        public boolean authorize(final Object user, final String role) {
                            return provider.authorize(user, role);
                        }

                    };
                final Dictionary<String, Object> props = new Hashtable<>();
                if (reference.getProperty(Constants.SERVICE_RANKING) != null) {
                    props.put(Constants.SERVICE_RANKING, reference.getProperty(Constants.SERVICE_RANKING));
                }
                if (reference.getProperty(SecurityProvider.PROPERTY_ID) != null) {
                    props.put(SecurityProvider.PROPERTY_ID, reference.getProperty(SecurityProvider.PROPERTY_ID));
                }
                return reference.getBundle().getBundleContext().registerService(SecurityProvider.class, wrapper, props);
            }
            return null;
        }

        @Override
        public void modifiedService( final ServiceReference<WebConsoleSecurityProvider> reference, final ServiceRegistration<SecurityProvider> service ) {
            // nothing to do
        }

        @Override
        public void removedService( final ServiceReference<WebConsoleSecurityProvider> reference, final ServiceRegistration<SecurityProvider> service ) {
            this.bundleContext.ungetService(reference);
            try {
                service.unregister();
            } catch ( final IllegalStateException ise ) {
                // ignore
            }
        }
    }

    public static class SecurityProviderWrapper implements SecurityProvider {
            
        private final WebConsoleSecurityProvider provider;

        public SecurityProviderWrapper(final WebConsoleSecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public Object authenticate(final HttpServletRequest request, final HttpServletResponse response) {
            if ( provider instanceof WebConsoleSecurityProvider2 ) {
                if (((WebConsoleSecurityProvider2)provider).authenticate(new HttpServletRequestWrapper(request), new HttpServletResponseWrapper(response))) {
                    return request.getAttribute(WebConsoleSecurityProvider2.USER_ATTRIBUTE);
                }
            }
            return null;
        }

        @Override
        public boolean authorize(final Object user, final String role) {
            return provider.authorize(user, role);
        }

        @Override
        public void logout(final HttpServletRequest request, final HttpServletResponse response) {
            if ( provider instanceof WebConsoleSecurityProvider3 ) {
                ((WebConsoleSecurityProvider3)provider).logout(new HttpServletRequestWrapper(request), new HttpServletResponseWrapper(response));
            }               
        }
    }
}

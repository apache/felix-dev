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
package org.apache.felix.webconsole.internal.servlet;


import java.io.Closeable;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.http.jakartawrappers.HttpServletRequestWrapper;
import org.apache.felix.http.jakartawrappers.HttpServletResponseWrapper;
import org.apache.felix.http.javaxwrappers.ServletWrapper;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.felix.webconsole.WebConsoleSecurityProvider3;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.servlet.Plugin.ServletPlugin;
import org.apache.felix.webconsole.servlet.AbstractServlet;
import org.apache.felix.webconsole.spi.SecurityProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import jakarta.servlet.Servlet;

public class JakartaServiceTracker implements Closeable, ServiceTrackerCustomizer<Servlet, JakartaServiceTracker.JakartaServletPlugin> {

    private final ServiceTracker<Servlet, JakartaServletPlugin> servletTracker;

    private final PluginHolder pluginHolder;

    private final JakartaSecurityProviderTracker securityProviderTracker;

    public JakartaServiceTracker( final PluginHolder pluginHolder, final BundleContext context ) {
        // try to load wrapper class to fail early
        new org.apache.felix.http.jakartawrappers.ServletWrapper(null);
        this.pluginHolder = pluginHolder;
        Filter filter = null;
        try {
            filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "=" + Servlet.class.getName() + 
                ")(" + WebConsoleConstants.PLUGIN_LABEL + "=*))");
        } catch (final InvalidSyntaxException e) {
            // not expected, thus fail hard
            throw new InternalError( "Failed creating filter: " + e.getMessage() );
        }
        this.servletTracker = new ServiceTracker<>(context, filter, this);
        servletTracker.open();
        this.securityProviderTracker = new JakartaSecurityProviderTracker(context);
    }

    @Override
    public void close() {
        this.servletTracker.close();
        this.securityProviderTracker.close();
    }

    @Override
    public JakartaServletPlugin addingService( final org.osgi.framework.ServiceReference<Servlet> reference ) {
        final String label = Util.getStringProperty( reference, WebConsoleConstants.PLUGIN_LABEL );
        if ( label != null ) {
            final JakartaServletPlugin plugin = new JakartaServletPlugin(this.pluginHolder, reference, label);
            pluginHolder.addPlugin(plugin);
            return plugin;
        }
        return null;
    }

    @Override
    public void modifiedService( final org.osgi.framework.ServiceReference<Servlet> reference, final JakartaServletPlugin service ) {
        this.removedService(reference, service);
        this.addingService(reference);
    }

    @Override
    public void removedService( final org.osgi.framework.ServiceReference<Servlet> reference, final JakartaServletPlugin service ) {
        this.pluginHolder.removePlugin(service);
    }

    public static class JakartaServletPlugin extends ServletPlugin {
            
        @SuppressWarnings({"unchecked", "rawtypes"})
        public JakartaServletPlugin(PluginHolder holder, ServiceReference<jakarta.servlet.Servlet> serviceReference,
                String label) {
            super(holder, (ServiceReference)serviceReference, label);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        protected javax.servlet.Servlet getService() {
            final Servlet servlet = (Servlet) getHolder().getBundleContext().getService( (ServiceReference)this.getServiceReference() );
            if (servlet != null) {
                if ( servlet instanceof AbstractServlet ) {
                    return new JakartaServletAdapter((AbstractServlet)servlet, this.getServiceReference());
                }
                final String prefix = "/".concat(this.getLabel());
                final String resStart = prefix.concat("/res/");
                return new ServletWrapper(servlet) {

                    @SuppressWarnings("unused")
                    public URL getResource(String path) {
                        if (path != null && path.startsWith(resStart)) {
                            return servlet.getClass().getResource(path.substring(prefix.length()));
                        }
                        return null;
                    }
                };
            }
            return null;
        }
    }

    public static class JakartaSecurityProviderTracker implements ServiceTrackerCustomizer<SecurityProvider, ServiceRegistration<WebConsoleSecurityProvider3>> {

        private final ServiceTracker<SecurityProvider, ServiceRegistration<WebConsoleSecurityProvider3>> tracker;

        private final BundleContext bundleContext;

        public JakartaSecurityProviderTracker( final BundleContext context ) {
            this.bundleContext = context;
            this.tracker = new ServiceTracker<>(context, SecurityProvider.class, this);
            tracker.open();
        }

        public void close() {
            tracker.close();
        }

        @Override
        public ServiceRegistration<WebConsoleSecurityProvider3> addingService( final ServiceReference<SecurityProvider> reference ) {
            final SecurityProvider provider = this.bundleContext.getService(reference);
            if ( provider != null ) {
                final JakartaSecurityProvider jakartaSecurityProvider = new JakartaSecurityProvider(provider);
                final Dictionary<String, Object> props = new Hashtable<>();
                if (reference.getProperty(Constants.SERVICE_RANKING) != null) {
                    props.put(Constants.SERVICE_RANKING, reference.getProperty(Constants.SERVICE_RANKING));
                }
                final ServiceRegistration<WebConsoleSecurityProvider3> reg = this.bundleContext.registerService(WebConsoleSecurityProvider3.class, jakartaSecurityProvider, props);
                return reg;
            }
            return null;
        }

        @Override
        public void modifiedService( final ServiceReference<SecurityProvider> reference, final ServiceRegistration<WebConsoleSecurityProvider3> service ) {
            // nothing to do
        }

        @Override
        public void removedService( final ServiceReference<SecurityProvider> reference, final ServiceRegistration<WebConsoleSecurityProvider3> service ) {
            this.bundleContext.ungetService(reference);
            try {
                service.unregister();
            } catch ( final IllegalStateException ise ) {
                // ignore
            }
        }
    }

    public static class JakartaSecurityProvider implements WebConsoleSecurityProvider3 {
            
        private final SecurityProvider provider;

        public JakartaSecurityProvider(final SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public void logout(final HttpServletRequest request, final HttpServletResponse response) {
            this.provider.logout((jakarta.servlet.http.HttpServletRequest)HttpServletRequestWrapper.getWrapper(request), 
                (jakarta.servlet.http.HttpServletResponse)HttpServletResponseWrapper.getWrapper(response));
        }

        @Override
        public boolean authenticate(final HttpServletRequest request, final HttpServletResponse response) {
            final Object user = this.provider.authenticate((jakarta.servlet.http.HttpServletRequest)HttpServletRequestWrapper.getWrapper(request), 
                (jakarta.servlet.http.HttpServletResponse)HttpServletResponseWrapper.getWrapper(response));
            if (user != null) {
                request.setAttribute(WebConsoleSecurityProvider3.USER_ATTRIBUTE, user);
            }
            return user != null;
        }

        @Override
        public Object authenticate(final String username, final String password) {
            // no need to implement
            return null;
        }

        @Override
        public boolean authorize(final Object user, final String role) {
            return this.provider.authorize(user, role);
        }
    }
}

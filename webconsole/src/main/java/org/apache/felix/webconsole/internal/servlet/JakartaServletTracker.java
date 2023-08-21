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

import org.apache.felix.http.javaxwrappers.ServletWrapper;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.servlet.Plugin.ServletPlugin;
import org.apache.felix.webconsole.servlet.AbstractServlet;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import jakarta.servlet.Servlet;

public class JakartaServletTracker implements Closeable, ServiceTrackerCustomizer<Servlet, JakartaServletTracker.JakartaServletPlugin> {

    private final ServiceTracker<Servlet, JakartaServletPlugin> servletTracker;

    private final PluginHolder pluginHolder;

    public JakartaServletTracker( final PluginHolder pluginHolder, final BundleContext context ) {
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
    }

    @Override
    public void close() {
        servletTracker.close();
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
                return new ServletWrapper(servlet);
            }
            return null;
        }
    }
}

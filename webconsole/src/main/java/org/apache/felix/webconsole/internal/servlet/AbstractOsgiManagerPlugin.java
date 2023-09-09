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
package org.apache.felix.webconsole.internal.servlet;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.apache.felix.webconsole.internal.OsgiManagerPlugin;
import org.apache.felix.webconsole.internal.misc.ServletSupport;
import org.apache.felix.webconsole.servlet.AbstractServlet;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

public abstract class AbstractOsgiManagerPlugin extends AbstractServlet implements OsgiManagerPlugin, ServletSupport {

    /**
     * The name of the request attribute providing a mapping of labels to page
     * titles of registered console plugins (value is "felix.webconsole.labelMap").
     * <p>
     * The type of this request attribute is <code>Map&lt;String, String&gt;</code>.
     */
    public static final String ATTR_LABEL_MAP = "felix.webconsole.labelMap";

    /**
     * Log level to be used by the web console
     */
    public static volatile int LOGLEVEL;

    // used to obtain services. Structure is: service name -> ServiceTracker
    private final Map<String, ServiceTracker<?, ?>> services = new HashMap<>();

    protected volatile BundleContext bundleContext;

    protected volatile ServiceRegistration<Servlet> reg;

    protected abstract String getTitle();

    protected abstract String getLabel();

    protected String getCategory() {
        return null;
    }

    protected String[] getCssReferences() {
        return null;
    }

    @Override
    public void activate(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put( ServletConstants.PLUGIN_LABEL, getLabel() );
        props.put( ServletConstants.PLUGIN_TITLE, getTitle() );
        if ( getCategory() != null ) {
            props.put( ServletConstants.PLUGIN_CATEGORY, getCategory() );
        }
        if ( getCssReferences() != null && getCssReferences().length > 0 ) {
            props.put( ServletConstants.PLUGIN_CSS_REFERENCES, this.getCssReferences() );
        }
        reg = this.bundleContext.registerService( Servlet.class, this, props );
    }

    @Override
    public void deactivate() {
        if ( reg != null ) {
            try {
                reg.unregister();
            } catch ( final IllegalStateException ise ) {
                // ignore, bundle context already invalid
            }
            reg = null;
        }
        for(final ServiceTracker<?, ?> tracker : services.values()) {
            tracker.close();
        }
        services.clear();
        this.bundleContext = null;
    }

    /**
     * Gets the service with the specified class name. Will create a new
     * {@link ServiceTracker} if the service is not already got.
     *
     * @param serviceName the service name to obtain
     * @return the service or <code>null</code> if missing.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public synchronized Object getService( String serviceName ) {
        ServiceTracker<?,?> serviceTracker = services.get( serviceName );
        if ( serviceTracker == null ) {
            serviceTracker = new ServiceTracker( this.bundleContext, serviceName, new ServiceTrackerCustomizer() {
                    public Object addingService( ServiceReference reference ) {
                        return bundleContext.getService( reference );
                    }

                    public void removedService( ServiceReference reference, Object service ) {
                        try {
                            bundleContext.ungetService( reference );
                        } catch ( IllegalStateException ise ) {
                            // ignore, bundle context was shut down
                        }
                    }

                    public void modifiedService( ServiceReference reference, Object service ) {
                        // nothing to do
                    }
            } );
            serviceTracker.open();

            services.put( serviceName, serviceTracker );
        }

        return serviceTracker.getService();
    }

    /**
     * Retrieves a request parameter and converts it to int.
     *
     * @param request the HTTP request
     * @param name the name of the request parameter
     * @param defaultValue the default value returned if the parameter is not set or is not a valid integer.
     * @return the request parameter if set and is valid integer, or the default value
     */
    protected int getParameterInt(final HttpServletRequest request, final String name, final int defaultValue) {
        int ret = defaultValue;
        final String param = request.getParameter(name);
        if (param != null) {
            try {
                ret = Integer.parseInt(param);
            } catch (final NumberFormatException nfe) {
            // don't care, will return default
            }
        }
        return ret;
    }

    /**
     * Calls the <code>ServletContext.log(String)</code> method if the
     * configured log level is less than or equal to the given <code>level</code>.
     * <p>
     * Note, that the <code>level</code> paramter is only used to decide whether
     * the <code>GenericServlet.log(String)</code> method is called or not. The
     * actual implementation of the <code>GenericServlet.log</code> method is
     * outside of the control of this method.
     * <p>
     * If the servlet has not been initialized yet or has already been destroyed
     * the message is printed to stderr.
     *
     * @param level The log level at which to log the message
     * @param message The message to log
     */
    public void log( int level, String message ) {
        if ( LOGLEVEL >= level ) {
            ServletConfig config = getServletConfig();
            if ( config != null ) {
                ServletContext context = config.getServletContext();
                if ( context != null ) {
                    context.log( message );
                    return;
                }
            }

            System.err.println( message );
        }
    }


    /**
     * Calls the <code>ServletContext.log(String, Throwable)</code> method if
     * the configured log level is less than or equal to the given
     * <code>level</code>.
     * <p>
     * Note, that the <code>level</code> paramter is only used to decide whether
     * the <code>GenericServlet.log(String, Throwable)</code> method is called
     * or not. The actual implementation of the <code>GenericServlet.log</code>
     * method is outside of the control of this method.
     *
     * @param level The log level at which to log the message
     * @param message The message to log
     * @param t The <code>Throwable</code> to log with the message
     */
    public void log( int level, String message, Throwable t ) {
        if ( LOGLEVEL >= level ) {
            ServletConfig config = getServletConfig();
            if ( config != null ) {
                ServletContext context = config.getServletContext();
                if ( context != null ) {
                    context.log( message, t );
                    return;
                }
            }

            System.err.println( message );
            if ( t != null ) {
                t.printStackTrace( System.err );
            }
        }
    }


    @Override
    public BundleContext getBundleContext() {
        return this.bundleContext;
    }
}

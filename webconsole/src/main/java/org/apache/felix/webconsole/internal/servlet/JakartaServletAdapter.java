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


import java.io.IOException;
import java.util.*;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.http.jakartawrappers.HttpServletRequestWrapper;
import org.apache.felix.http.jakartawrappers.HttpServletResponseWrapper;
import org.apache.felix.http.jakartawrappers.ServletConfigWrapper;
import org.apache.felix.http.javaxwrappers.ServletExceptionUtil;
import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.apache.felix.webconsole.servlet.AbstractServlet;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.osgi.framework.ServiceReference;

/**
 * The <code>JakartaServletAdapter</code> is an adapter to the
 * {@link AbstractWebConsolePlugin} for regular servlets registered with the
 * {@link org.apache.felix.webconsole.WebConsoleConstants#PLUGIN_TITLE}
 * service attribute using jakarta.servlet.Servlet
 */
public class JakartaServletAdapter extends AbstractWebConsolePlugin {

    /** serial UID */
    private static final long serialVersionUID = 1L;

    // the plugin label (aka address)
    private final String label;

    // the plugin title
    private final String title;

    // the actual plugin to forward rendering requests to
    private final AbstractServlet plugin;

    // the CSS references (null if none)
    private final String[] cssReferences;

    /**
     * Creates a new wrapper for a Web Console Plugin
     *
     * @param plugin the plugin itself
     * @param serviceReference reference to the plugin
     */
    public JakartaServletAdapter( final AbstractServlet plugin, ServiceReference<Servlet> serviceReference ) {
        this.label = (String) serviceReference.getProperty( ServletConstants.PLUGIN_LABEL );
        this.title = (String) serviceReference.getProperty( ServletConstants.PLUGIN_TITLE );
        this.plugin = plugin;
        this.cssReferences = toStringArray( serviceReference.getProperty( ServletConstants.PLUGIN_CSS_REFERENCES ) );

        // activate this abstract plugin (mainly to set the bundle context)
        activate( serviceReference.getBundle().getBundleContext() );
    }


    //---------- AbstractWebConsolePlugin API

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    protected String[] getCssReferences() {
        return cssReferences;
    }

    @Override
    protected void renderContent( final HttpServletRequest req, final HttpServletResponse res )
    throws ServletException, IOException {
        try {
            plugin.renderContent( (jakarta.servlet.http.HttpServletRequest)HttpServletRequestWrapper.getWrapper(req), 
                (jakarta.servlet.http.HttpServletResponse)HttpServletResponseWrapper.getWrapper(res) );
        } catch (final jakarta.servlet.ServletException s) {
            throw ServletExceptionUtil.getServletException(s);
        }
    }

    /**
     * Returns the registered plugin class to be able to call the
     * <code>getResource()</code> method on that object for this plugin to
     * provide additional resources.
     *
     * @see org.apache.felix.webconsole.AbstractWebConsolePlugin#getResourceProvider()
     */
    protected Object getResourceProvider() {
        return plugin;
    }


    //---------- Servlet API overwrite

    /**
     * Initializes this servlet as well as the plugin servlet.
     *
     * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
     */
    public void init( ServletConfig config ) throws ServletException {
        // no need to activate the plugin, this has already been done
        // when the instance was setup
        try {
            // base classe initialization
            super.init( config );

            // plugin initialization
            try {
                plugin.init( new ServletConfigWrapper(config) );
            } catch ( final jakarta.servlet.ServletException s) {
                throw ServletExceptionUtil.getServletException(s);
            }
        } catch ( ServletException se ) {
            // if init fails, the plugin will not be destroyed and thus
            // the plugin not deactivated. Do it here
            deactivate();

            // rethrow the exception
            throw se;
        }
    }

    private static final class CheckHttpServletResponse extends HttpServletResponseWrapper {

        private boolean done = false;
        public CheckHttpServletResponse(HttpServletResponse response) {
            super(response);
        }

        public boolean isDone() {
            return this.done;
        }

        @Override
        public void reset() {
            this.done = false;
            super.reset();
        }

        @Override
        public void sendError(final int sc) throws IOException {
            this.done = true;
            super.sendError(sc);
        }

        @Override
        public void sendError(final int sc, final String msg) throws IOException {
            this.done = true;
            super.sendError(sc, msg);
        }

        @Override
        public void sendRedirect(final String location) throws IOException {
            this.done = true;
            super.sendRedirect(location);
        }

        @Override
        public void setStatus(final int sc) {
            this.done = true;
            super.setStatus(sc);
        }

        @Override
        public void setStatus(final int sc, final String sm) {
            this.done = true;
            super.setStatus(sc, sm);
        }

        @Override
        public void setContentType(final String type) {
            this.done = true;
            super.setContentType(type);
        }
    }

    /**
     * Directly refer to the plugin's service method unless the request method
     * is <code>GET</code> in which case we defer the call into the service method
     * until the abstract web console plugin calls the
     * {@link #renderContent(HttpServletRequest, HttpServletResponse)}
     * method.
     *
     * @see javax.servlet.http.HttpServlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    public void service( HttpServletRequest req, HttpServletResponse resp )
    throws ServletException, IOException {
        final CheckHttpServletResponse checkResponse = new CheckHttpServletResponse(resp);
        // call plugin first
        try {
            plugin.service( (jakarta.servlet.http.HttpServletRequest)HttpServletRequestWrapper.getWrapper(req), 
                (jakarta.servlet.http.HttpServletResponse)HttpServletResponseWrapper.getWrapper(resp) );
        } catch (final jakarta.servlet.ServletException s) {
            throw ServletExceptionUtil.getServletException(s);
        }
        // if a GET request and plugin did not create a response yet, call super to get full HTML response
        if ( !checkResponse.isDone()) {
            this.doGet( req, resp );
        }
    }

    /**
     * Destroys this servlet as well as the plugin servlet.
     *
     * @see javax.servlet.GenericServlet#destroy()
     */
    public void destroy() {
        try {
            plugin.destroy();
            super.destroy();
        } finally {
            deactivate();
        }
    }


    //---------- internal

    @SuppressWarnings("rawtypes")
    private String[] toStringArray( final Object value ) {
        if ( value instanceof String )
        {
            return new String[]
                { ( String ) value };
        }
        else if ( value != null )
        {
            final Collection cssListColl;
            if ( value.getClass().isArray() )
            {
                cssListColl = Arrays.asList( ( Object[] ) value );
            }
            else if ( value instanceof Collection )
            {
                cssListColl = ( Collection ) value;
            }
            else
            {
                cssListColl = null;
            }

            if ( cssListColl != null && !cssListColl.isEmpty() )
            {
                String[] entries = new String[cssListColl.size()];
                int i = 0;
                for ( Iterator cli = cssListColl.iterator(); cli.hasNext(); i++ )
                {
                    entries[i] = String.valueOf( cli.next() );
                }
                return entries;
            }
        }

        return null;
    }
}

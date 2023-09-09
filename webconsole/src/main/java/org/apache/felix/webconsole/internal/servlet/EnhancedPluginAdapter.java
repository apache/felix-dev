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
import java.net.URL;
import org.apache.felix.webconsole.servlet.AbstractServlet;
import org.osgi.framework.ServiceReference;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * The <code>SimplePluginAdapter</code> is an adapter to the
 * {@link AbstractPluginAdapter} for servlets extending
 * {@link AbstractServlet}.
 */
public class EnhancedPluginAdapter extends AbstractPluginAdapter {

    /** serial UID */
    private static final long serialVersionUID = 1L;

    /** The actual plugin to forward rendering requests to */
    private final AbstractServlet plugin;

    /**
     * Creates a new wrapper for a Web Console Plugin
     */
    public EnhancedPluginAdapter(final AbstractServlet plugin, 
            final ServiceReference<Servlet> serviceReference, 
            final String label, final String title, final String[] cssReferences) {
        super(serviceReference.getBundle().getBundleContext(), label, title, cssReferences);
        this.plugin = plugin;
    }

    @Override
    protected URL getResource(final String path) {
        return null;
    }
    /** 
     * Initialize the plugin
     */
    @Override
    public void init( final ServletConfig config ) throws ServletException {
        try {
            // base classe initialization
            super.init( config );

            // plugin initialization
            plugin.init( config );
        } catch ( final ServletException se ) {
            // if init fails, the plugin will not be destroyed and thus
            // the plugin not deactivated. Do it here
            deactivate();

            // rethrow the exception
            throw se;
        }
    }

    private static final class CheckHttpServletResponse extends HttpServletResponseWrapper {

        private boolean done = false;

        public CheckHttpServletResponse(final HttpServletResponse response) {
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

        public void setStatus(final int sc, final String sm) {
            this.done = true;
            // use non deprecated method (servlet api 6)
            super.setStatus(sc);
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
     */
    @Override
    public void service(final HttpServletRequest req, final HttpServletResponse resp )
    throws ServletException, IOException {
        final CheckHttpServletResponse checkResponse = new CheckHttpServletResponse(resp);
        // call plugin service method first
        plugin.service( req, checkResponse);

        // if plugin did not create a response yet, call doGet to get a response
        if ( !checkResponse.isDone()) {
            this.doGet( req, resp );
        }
    }

    @Override
    protected void renderContent(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
        this.plugin.renderContent(req, res);        
    }

    /**
     * Destroys this servlet as well as the plugin servlet.
     */
    @Override
    public void destroy() {
        try {
            plugin.destroy();
            super.destroy();
        } finally {
            deactivate();
        }
    }
}

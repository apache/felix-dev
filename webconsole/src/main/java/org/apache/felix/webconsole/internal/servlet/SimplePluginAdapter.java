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
import org.osgi.framework.ServiceReference;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * The <code>SimplePluginAdapter</code> is an adapter to the
 * {@link AbstractPluginAdapter} for regular servlets.
 */
public class SimplePluginAdapter extends AbstractPluginAdapter {

    /** serial UID */
    private static final long serialVersionUID = 1L;

    // the actual plugin to forward rendering requests to
    private final Servlet plugin;

    private final String resStart;

    /**
     * Creates a new wrapper for a Web Console Plugin
     */
    public SimplePluginAdapter(final Servlet plugin, 
            final ServiceReference<Servlet> serviceReference, 
            final String label, final String title, final String[] cssReferences) {
        super(serviceReference.getBundle().getBundleContext(), label, title, cssReferences);
        this.plugin = plugin;
        final String prefix = "/".concat(label);
        this.resStart = prefix.concat("/res/");

        // activate this abstract plugin (mainly to set the bundle context)
        activate( serviceReference.getBundle().getBundleContext() );
    }

    /**
     * Call the plugin servlet's service method to render the content of this
     * page.
     */
    @Override
    protected void renderContent(final HttpServletRequest req, final HttpServletResponse res )
    throws ServletException, IOException {
        plugin.service( req, res );
    }

    @Override
    protected URL getResource(final String path) {
        if (path != null && path.startsWith(resStart)) {
            return this.plugin.getClass().getResource(path.substring(this.label.length() + 1));
        }
        return null;
    }

    //---------- Servlet API overwrite

    /**
     * Initializes this servlet as well as the plugin servlet.
     */
    @Override
    public void init(final ServletConfig config ) throws ServletException {
        // no need to activate the plugin, this has already been done
        // when the instance was setup
        try {
            // base classe initialization
            super.init( config );

            // plugin initialization
            plugin.init( config );
        } catch (final ServletException se ) {
            // if init fails, the plugin will not be destroyed and thus
            // the plugin not deactivated. Do it here
            deactivate();

            // rethrow the exception
            throw se;
        }
    }

    /**
     * Detects whether this request is intended to have the headers and
     * footers of this plugin be rendered or not. The decision is taken based
     * on whether and what extension the request URI has: If the request URI
     * has no extension or the the extension is <code>.html</code>, the request
     * is assumed to be rendered with header and footer. Otherwise the
     * headers and footers are omitted and the
     * {@link #renderContent(HttpServletRequest, HttpServletResponse)}
     * method is called without any decorations and without setting any
     * response headers.
     */
    @Override
    protected boolean isHtmlRequest( final HttpServletRequest request ) {
        final String requestUri = request.getRequestURI();
        if ( requestUri.endsWith( ".html" ) ) {
            return true;
        }
        // check if there is an extension
        final int lastSlash = requestUri.lastIndexOf('/');
        final int lastDot = requestUri.indexOf('.', lastSlash + 1);
        return lastDot < 0;
    }

    /**
     * Directly refer to the plugin's service method unless the request method
     * is <code>GET</code> in which case we defer the call into the service method
     * until the abstract web console plugin calls the
     * {@link #renderContent(HttpServletRequest, HttpServletResponse)}
     * method.
     */
    @Override
    public void service( final ServletRequest req, final ServletResponse resp ) throws ServletException, IOException {
        if ( ( req instanceof HttpServletRequest ) && ( ( HttpServletRequest ) req ).getMethod().equals( "GET" ) ) {
            // handle the GET request here and call into plugin on renderContent
            super.service( req, resp );
        } else {
            // not a GET request, have the plugin handle it directly
            plugin.service( req, resp );
        }
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

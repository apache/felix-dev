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
package org.apache.felix.webconsole.servlet;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.felix.webconsole.internal.Util;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * This class can be used as a base class for a web console plugin.
 * The plugin (servlet) needs to be registered as a servlet service
 * with at least the label property.
 * <p>
 * Extending this servlet is one way of providing a plugin. The other
 * is to simply direclty implement the servlet interface. In both cases
 * requests to resources with a path of "LABEL/res/*" are automatically
 * handled.
 * <p>
 * For html (content) requests, the web console automatically renders the header,
 * footer and navigation.
 * <p>
 * Support for Jakarta servlets requires that the Jakarta Servlet API and the
 * Apache Felix Http Wrappers are available in the runtime.
 * <p>
 * If you are upgrading from {@link org.apache.felix.webconsole.AbstractWebConsolePlugin}
 * there are some changes to be aware of:
 * <ul>
 * <li>Resources are detected via the {@link #getResource(String)} method.</li>
 * <li>Regardless of the http method, if the corresponding doXXX method does not create
 *    a response, the {@link #renderContent(HttpServletRequest, HttpServletResponse)}
 *    method is invoked.</li>
 * <li>If a doXXX method other than doGet wants to create a response, it must not call
 *    doGet or renderContent directly. It rather just does not create any response and
 *    returns. In this case the web console will invoke the renderContent method
 *    afterwards.</li>
 * </ul>
 *
 * @see ServletConstants#PLUGIN_LABEL
 * @see ServletConstants#PLUGIN_TITLE
 * @see ServletConstants#PLUGIN_CATEGORY
 * @see ServletConstants#PLUGIN_CSS_REFERENCES
 */
public abstract class AbstractServlet extends HttpServlet {

    /**
     * Called to identify resources.
     * By default, if the path starts with "/res/" this is treated as a resource
     * and the URL to the resource is tried to be loaded via the class loader.
     * @param path the path
     * @return the URL of the resource or <code>null</code> if not found.
     */
    protected URL getResource( final String path ) {
        final int index = path.indexOf( '/', 1 );
        if (index != -1) {
            if (path.substring(index).startsWith("/res/") ) {
                return getClass().getResource( path.substring(index) );
            }
        }
        return null;
    }

    /**
     * Handle get requests. This method can be used to return resources, like JSON responses etc.
     * If the plugin is serving a resource, this method call {@link HttpServletResponse#setStatus(int)}.
     * This method is also allowed to send a redirect or an error.
     * If none of the three applies, the webconsole will call {@link #renderContent(HttpServletRequest, HttpServletResponse)}
     */
    @Override
    protected void doGet( final HttpServletRequest request, final HttpServletResponse response )
    throws ServletException, IOException {
        this.spoolResource( request, response );
    }

    /**
     * This method is used to render the main contents of the plugin
     * @param request The request
     * @param response The response
     * @throws ServletException If an error occurs
     * @throws IOException If writing the response fails
     */
    public void renderContent( final HttpServletRequest request, final HttpServletResponse response)
    throws ServletException, IOException {
        throw new ServletException("Render method not implemented");
    }

   /**
     * If the request addresses a resource , this method serves it
     * and returns <code>true</code>. Otherwise <code>false</code> is returned.
     * <p>
     * If <code>true</code> is returned, the request is considered complete and
     * request processing terminates. Otherwise request processing continues
     * with normal plugin rendering.
     *
     * @param request The request object
     * @param response The response object
     *
     * @throws IOException If an error occurs accessing or spooling the resource.
     */
    protected final void spoolResource(final HttpServletRequest request, final HttpServletResponse response)
    throws IOException {
        // check for a resource, fail if none
        final URL url = this.getResource(request.getPathInfo());
        if ( url == null ) {
            return;
        }

        // open the connection and the stream (we use the stream to be able
        // to at least hint to close the connection because there is no
        // method to explicitly close the conneciton, unfortunately)
        final URLConnection connection = url.openConnection();
        try ( final InputStream ins = connection.getInputStream()) {
            // FELIX-2017 Equinox may return an URL for a non-existing
            // resource but then (instead of throwing) return null on
            // getInputStream. We should account for this situation and
            // just assume a non-existing resource in this case.
            if (ins == null) {
                return;
            }

            // check whether we may return 304/UNMODIFIED
            long lastModified = connection.getLastModified();
            if ( lastModified > 0 ) {
                long ifModifiedSince = request.getDateHeader( "If-Modified-Since" );
                if ( ifModifiedSince >= ( lastModified / 1000 * 1000 ) ) {
                    // Round down to the nearest second for a proper compare
                    // A ifModifiedSince of -1 will always be less
                    response.setStatus( HttpServletResponse.SC_NOT_MODIFIED );

                    return;
                }

                // have to send, so set the last modified header now
                response.setDateHeader( "Last-Modified", lastModified );
            }

            // describe the contents
            final String contentType = getServletContext().getMimeType( request.getPathInfo());
            if ( contentType != null ) {
                response.setContentType( contentType );
            }
            if (connection.getContentLength() != -1) {
                response.setContentLength( connection.getContentLength() );
            }
            response.setStatus( HttpServletResponse.SC_OK);

            // spool the actual contents
            final OutputStream out = response.getOutputStream();
            final byte[] buf = new byte[2048];
            int rd;
            while ( ( rd = ins.read( buf ) ) >= 0 ) {
                out.write( buf, 0, rd );
            }
        }
    }

    /**
     * Reads the <code>templateFile</code> as a resource through the class
     * loader of this class converting the binary data into a string using
     * UTF-8 encoding.
     *
     * @param templateFile The absolute path to the template file to read.
     * @return The contents of the template file as a string
     *
     * @throws NullPointerException if <code>templateFile</code> is
     *      <code>null</code>
     * @throws FileNotFoundException If template file cannot be found
     * @throws IOException On any other error reading the template file
     */
    protected final String readTemplateFile( final String templateFile ) throws IOException {
        return Util.readTemplateFile( getClass(), templateFile );
    }

    protected RequestVariableResolver getVariableResolver(final HttpServletRequest request) {
        return (RequestVariableResolver) request.getAttribute(RequestVariableResolver.REQUEST_ATTRIBUTE);
    }

    /**
     * Sets response headers to force the client to not cache the response
     * sent back. This method must be called before the response is committed
     * otherwise it will have no effect.
     * <p>
     * This method sets the <code>Cache-Control</code>, <code>Expires</code>,
     * and <code>Pragma</code> headers.
     *
     * @param response The response for which to set the cache prevention
     */
    public static final void setNoCache(final HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Cache-Control", "must-revalidate");
        response.addHeader("Cache-Control", "max-age=0");
        response.setHeader("Expires", "Thu, 01 Jan 1970 01:00:00 GMT");
        response.setHeader("Pragma", "no-cache");
    }
}

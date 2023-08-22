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


import static org.apache.felix.webconsole.internal.servlet.BasicWebConsoleSecurityProvider.AUTHENTICATION_SCHEME_BASIC;
import static org.apache.felix.webconsole.internal.servlet.BasicWebConsoleSecurityProvider.HEADER_AUTHORIZATION;
import static org.apache.felix.webconsole.internal.servlet.BasicWebConsoleSecurityProvider.HEADER_WWW_AUTHENTICATE;

import java.io.IOException;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.webconsole.servlet.User;
import org.apache.felix.webconsole.WebConsoleSecurityProvider;
import org.apache.felix.webconsole.WebConsoleSecurityProvider2;
import org.osgi.framework.Bundle;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.util.tracker.ServiceTracker;


final class OsgiManagerHttpContext extends ServletContextHelper {

    private final ServiceTracker<WebConsoleSecurityProvider, WebConsoleSecurityProvider> tracker;

    private final String realm;

    private final Bundle bundle;

    OsgiManagerHttpContext(final Bundle webConsoleBundle,
            final ServiceTracker<WebConsoleSecurityProvider, WebConsoleSecurityProvider> tracker,
            final String realm) {
        super(webConsoleBundle);
        this.tracker = tracker;
        this.realm = realm;
        this.bundle = webConsoleBundle;
    }

    public URL getResource(final String name) {
        URL url = this.bundle.getResource( name );
        if ( url == null && name.endsWith( "/" ) ) {
            url = this.bundle.getResource( name.substring( 0, name.length() - 1 ) );
        }
        return url;
    }

    @Override
    public boolean handleSecurity( final HttpServletRequest r, final HttpServletResponse response ) {
        final WebConsoleSecurityProvider provider = tracker.getService();

        // for compatibility we have to adjust a few methods on the request
        final HttpServletRequest request = new HttpServletRequestWrapper(r) {

            @Override
            public String getContextPath() {
                return "";
            }

            @Override
            public String getServletPath() {
                return r.getContextPath();
            }

            @Override
            public String getPathInfo() {
                return r.getServletPath();
            }
        };

        // check whether the security provider can fully handle the request
        final boolean result;
        if ( provider instanceof WebConsoleSecurityProvider2 ) {
            result = ( ( WebConsoleSecurityProvider2 ) provider ).authenticate( request, response );
        } else {
            result = handleSecurity(provider, request, response);
        }

        if ( result ) {
            request.setAttribute(User.USER_ATTRIBUTE, new org.apache.felix.webconsole.User(){

				@Override
				public boolean authorize(String role) {
                    final Object user = this.getUserObject();
                    if ( user == null ) {
                        // no user object in request, deny
                        return false;
                    }
					if ( provider == null ) {
                        // no provider, allow (compatibility)
                        return true;
                    }
					return provider.authorize(user, role);
				}

				@Override
				public Object getUserObject() {
					return request.getAttribute(WebConsoleSecurityProvider2.USER_ATTRIBUTE);
				}
                
            });
            request.setAttribute(org.apache.felix.webconsole.User.USER_ATTRIBUTE, request.getAttribute(User.USER_ATTRIBUTE));
        }
        return result;
    }

    /**
     * Handle security with an optional web console security provider
     */
    private boolean handleSecurity( final WebConsoleSecurityProvider provider, 
        final HttpServletRequest request,
        final HttpServletResponse response) {
        // Return immediately if the header is missing
        String authHeader = request.getHeader( HEADER_AUTHORIZATION );
        if ( authHeader != null && authHeader.length() > 0 )
        {

            // Get the authType (Basic, Digest) and authInfo (user/password)
            // from
            // the header
            authHeader = authHeader.trim();
            int blank = authHeader.indexOf( ' ' );
            if ( blank > 0 )
            {
                String authType = authHeader.substring( 0, blank );
                String authInfo = authHeader.substring( blank ).trim();

                // Check whether authorization type matches
                if ( authType.equalsIgnoreCase( AUTHENTICATION_SCHEME_BASIC ) )
                {
                    try
                    {
                        byte[][] userPass = BasicWebConsoleSecurityProvider.base64Decode( authInfo );
                        final String username = BasicWebConsoleSecurityProvider.toString( userPass[0] );

                        // authenticate
                        if ( authenticate( provider, username, userPass[1] ) )
                        {
                            // as per the spec, set attributes
                            request.setAttribute( AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH );
                            request.setAttribute( REMOTE_USER, username );

                            // set web console user attribute
                            request.setAttribute( WebConsoleSecurityProvider2.USER_ATTRIBUTE, username );

                            // succeed
                            return true;
                        }
                    }
                    catch ( Exception e )
                    {
                        // Ignore
                    }
                }
            }
        }

        // request authentication
        try {
            response.setHeader( HEADER_WWW_AUTHENTICATE, AUTHENTICATION_SCHEME_BASIC + " realm=\"" + this.realm + "\"" );
            response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
            response.setContentLength( 0 );
            response.flushBuffer();
        } catch ( IOException ioe ) {
            // failed sending the response ... cannot do anything about it
        }

        // inform HttpService that authentication failed
        return false;
    }

    private boolean authenticate( WebConsoleSecurityProvider provider, String username, byte[] password ) {
        if ( provider != null )
        {
            return provider.authenticate( username, BasicWebConsoleSecurityProvider.toString( password ) ) != null;
        }
        return false;
    }
}

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.felix.webconsole.spi.SecurityProvider;
import org.osgi.framework.BundleContext;
import org.osgi.service.servlet.context.ServletContextHelper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Basic implementation of WebConsoleSecurityProvider to replace logic that
 * was previously in OsgiManagerHttpContext
 */
public class BasicWebConsoleSecurityProvider implements SecurityProvider {

    static final String HEADER_WWW_AUTHENTICATE = "WWW-Authenticate";

    static final String HEADER_AUTHORIZATION = "Authorization";

    static final String AUTHENTICATION_SCHEME_BASIC = "Basic";

    public static volatile String REALM;

    private final String username;

    private final Password password;

    private final BundleContext bundleContext;

    public BasicWebConsoleSecurityProvider(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        this.username = null;
        this.password = null;
    }

    public BasicWebConsoleSecurityProvider(final BundleContext bundleContext, final String username, final String password) {
        this.bundleContext = bundleContext;
        this.username = username;
        this.password = new Password(password);
    }

    public Object authenticate(final String username, final String password) {
        if ( this.username.equals( username ) && this.password.matches( password.getBytes() ) ) {
            if (bundleContext.getProperty(OsgiManager.FRAMEWORK_PROP_SECURITY_PROVIDERS) == null) {
                // Only allow username and password authentication if no mandatory security providers are registered
                return true;
            }
        }
        return null;
    }

    /**
     * All users authenticated with the repository are granted access for all roles in the Web Console.
     */
    @Override
    public boolean authorize(final Object user, final String role) {
        return true;
    }

    @Override
    public Object authenticate(final HttpServletRequest request, final HttpServletResponse response) {
        // Return immediately if the header is missing
        String authHeader = request.getHeader( HEADER_AUTHORIZATION );
        if ( authHeader != null && authHeader.length() > 0 ) {

            // Get the authType (Basic, Digest) and authInfo (user/password)
            // from
            // the header
            authHeader = authHeader.trim();
            final int blank = authHeader.indexOf( ' ' );
            if ( blank > 0 ) {
                final String authType = authHeader.substring( 0, blank );
                final String authInfo = authHeader.substring( blank ).trim();

                // Check whether authorization type matches
                if ( authType.equalsIgnoreCase( AUTHENTICATION_SCHEME_BASIC ) ) {
                    try {
                        final byte[][] userPass = base64Decode( authInfo );
                        final String username = toString( userPass[0] );

                        // authenticate
                        if ( authenticate( username, toString(userPass[1]) ) != null ) {
                            // as per the spec, set attributes
                            request.setAttribute( ServletContextHelper.AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH );
                            request.setAttribute( ServletContextHelper.REMOTE_USER, username );

                            // succeed
                            return username;
                        }
                    } catch (final  Exception e )  {
                        // Ignore
                    }
                }
            }
        }

        // request authentication
        try {
            response.setHeader( HEADER_WWW_AUTHENTICATE, AUTHENTICATION_SCHEME_BASIC + " realm=\"" + REALM + "\"" );
            response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
            response.setContentLength( 0 );
            response.flushBuffer();
        } catch (final  IOException ioe ) {
            // failed sending the response ... cannot do anything about it
        }

        // inform that authentication failed
        return null;
    }

    @Override
    public void logout(final HttpServletRequest request, final HttpServletResponse response) {
        // nothing to do
    }

    static byte[][] base64Decode(final String srcString ) {
        final byte[] transformed = Base64.decodeBase64( srcString );
        for ( int i = 0; i < transformed.length; i++ ) {
            if ( transformed[i] == ':' ) {
                final byte[] user = new byte[i];
                final byte[] pass = new byte[transformed.length - i - 1];
                System.arraycopy( transformed, 0, user, 0, user.length );
                System.arraycopy( transformed, i + 1, pass, 0, pass.length );
                return new byte[][] { user, pass };
            }
        }

        return new byte[][] { transformed, new byte[0] };
    }

    static String toString( final byte[] src ) {
        return new String(src, StandardCharsets.ISO_8859_1);
    }
}

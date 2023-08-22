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
package org.apache.felix.webconsole.spi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * The <code>SecurityProvider</code> is a service interface allowing
 * to use an external system to authenticate users before granting access to the
 * Web Console.
 * <p>
 * Support for Jakarta servlets requires that the Jakarta Servlet API and the
 * Apache Felix Http Wrappers are available in the runtime.
 *
 * @since 1.2.0
 */
@ConsumerType
public interface SecurityProvider {

    /**
     * Checks whether the authenticated user has the given role permission.
     *
     * @param user The object referring to the authenticated user. This is the
     *      object returned from the {@link #authenticate(HttpServletRequest, HttpServletResponse)}
     *      method and will never be <code>null</code>.
     * @param role The requested role
     * @return <code>true</code> if the user is given permission for the given
     *      role.
     */
    boolean authorize( Object user, String role );

    /**
     * Authenticates the given request or asks the client for credentials.
     * <p>
     * Implementations of this method are expected to respect and implement
     * the semantics of the <code>ServletContextHelper.handleSecurity</code> method
     * as specified in the OSGi HTTP Service specification.
     * <p>
     * If this method returns an object (non null) it is assumed the request
     * provided valid credentials identifying the user as accepted to access
     * the web console.
     * <p>
     * If this method returns {@code null} the request to the web console
     * is terminated without any more response sent back to the client. That is
     * the implementation is expected to have informed the client in case of
     * non-granted access.
     *
     * @param request The request object
     * @param response The response object
     * @return An object representing the user if the request provided valid credentials.
     *   Otherwise return {@code null}.
     */
    Object authenticate( HttpServletRequest request, HttpServletResponse response );

    /**
     * This method will be called by the web console when the user clicks the logout button. The security provider
     * shouldn't invalidate the session, it will be invalidated after this method exits.
     * 
     * However the security provider must delete any cookies or objects, that matters during the authorization process.
     * 
     * @param request the request
     * @param response the response
     */
    void logout(HttpServletRequest request, HttpServletResponse response);
}

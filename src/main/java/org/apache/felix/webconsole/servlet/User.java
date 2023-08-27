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

import org.osgi.annotation.versioning.ProviderType;

/**
 * Representation of a user.
 * The user object can be used by plugins to {@link #authorize(String)} the user.
 */
@ProviderType
public interface User {
    
    /**
     * The name of the request attribute providing an object of this class
     */
    String USER_ATTRIBUTE = User.class.getName();

    /**
     * Return the user object.
     * This method might return {@code null} if no web console security provider is configured and
     * access to the console is allowed without authentication.
     * @return The user object or {@code null}
     */
    Object getUserObject();


    /**
     * Checks whether the user has the given role permission.
     *
     * @param role The requested role
     * @return {@code true} if the user is given permission for the given role.
     */
    boolean authorize( String role );
}

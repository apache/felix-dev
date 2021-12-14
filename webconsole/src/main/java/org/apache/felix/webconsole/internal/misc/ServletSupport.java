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
package org.apache.felix.webconsole.internal.misc;

import org.osgi.framework.BundleContext;

public interface ServletSupport {

    /**
     * Log the message
     * @param msg a log message
     */
    void log(String msg);

    /**
     * Log the message
     * @param message a log message
     * @param t a throwable
     */
    void log(String message, Throwable t);

    /**
     * Get the bundle context
     * @return The bundle contect
     */
    BundleContext getBundleContext();

    /**
     * Gets the service with the specified class name. 
     *
     * @param serviceName the service name to obtain
     * @return the service or <code>null</code> if missing.
     */
    Object getService( String serviceName );
}

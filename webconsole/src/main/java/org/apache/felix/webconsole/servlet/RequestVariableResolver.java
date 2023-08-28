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

import java.util.HashMap;

/**
 * The <code>RequestVariableResolver</code> is a <code>HashMap</code> that
 * is used by the webconsole to process variables in the template.
 * The resolver is stored as a request attribute with the name
 * {@link #REQUEST_ATTRIBUTE}.
 */
public class RequestVariableResolver extends HashMap<String, Object> {

    /**
     * The name of the request attribute holding the {@link RequestVariableResolver}
     * for the request (value is "felix.webconsole.variable.resolver").
     * This attribute is guaaranteed to be set for plugins.
     */
    public static final String REQUEST_ATTRIBUTE = "felix.webconsole.variable.resolver";

    /**
     * The name of the key providing the absolute path of the Web Console root.
     * This key is guaaranteed to be set for plugins.
     * @see ServletConstants#ATTR_APP_ROOT
     */
    public static final String KEY_APP_ROOT = "appRoot";

    /**
     * The name of the key providing the absolute path of the current plugin.
     * This key is guaaranteed to be set for plugins.
     * @see ServletConstants#ATTR_PLUGIN_ROOT
     */
    public static final String KEY_PLUGIN_ROOT = "pluginRoot";

    /**
     * Creates a new variable resolver with default capacity.
     */
    public RequestVariableResolver() {
        super();
    }

    /**
     * Returns the string representation of the value stored under the variable
     * name in this map. If no value is stored under the variable name,
     * <code>null</code> is returned.
     *
     * @param variable The name of the variable whose value is to be returned.
     * @return The variable value or <code>null</code> if there is no entry
     *      with the given name in this map.
     */
    public String resolve( final String variable ) {
        final Object value = this.get(variable);
        if ( value != null ) {
            return value.toString();
        }
        return null;
    }
}

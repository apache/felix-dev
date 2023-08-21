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

/**
 * Constants for servlets registered with the web console.
 */
public abstract class ServletConstants {

    /**
     * The URI address label under which the servlet is called by
     * the web console (value is "felix.webconsole.label").
     * <p>
     * This service registration property must be set to a single non-empty
     * String value. Otherwise the Servlet services will
     * be ignored by the web console and not be used as a plugin.
     */
    public static final String PLUGIN_LABEL = "felix.webconsole.label";

    /**
     * The title under which the servlet is called by
     * the web console (value is "felix.webconsole.title").
     * <p>
     * This property is required for the service to be used as a plugin. 
     * Otherwise the service is just ignored by the web console.
     * <p>
     */
    public static final String PLUGIN_TITLE = "felix.webconsole.title";

    /**
     * The category under which the servlet is listed in the top
     * navigation by the web console (value is "felix.webconsole.category").
     * <p>
     * If not specified, the servlet is put into the default category.
     */
    public static final String PLUGIN_CATEGORY = "felix.webconsole.category";

    /**
     * The name of the service registration properties providing references
     * to addition CSS files that should be loaded when rendering the header
     * for a registered plugin.
     * <p>
     * This property is expected to be a single string value, array of string
     * values or a Collection (or Vector) of string values.
     */
    public static final String PLUGIN_CSS_REFERENCES = "felix.webconsole.css";

    /**
     * The name of the request attribute providing the absolute path of the
     * Web Console root (value is "felix.webconsole.appRoot"). This consists of
     * the servlet context path (from <code>HttpServletRequest.getContextPath()</code>)
     * and the Web Console servlet path (from
     * <code>HttpServletRequest.getServletPath()</code>,
     * <code>/system/console</code> by default).
     * <p>
     * The type of this request attribute is <code>String</code>.
     */
    public static final String ATTR_APP_ROOT = "felix.webconsole.appRoot";
}

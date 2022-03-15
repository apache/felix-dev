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
package org.apache.felix.http.base.internal.javaxwrappers;

import java.util.Enumeration;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.ServletConfig;

/**
 * Servlet config wrapper
 */
public class ServletConfigWrapper implements javax.servlet.ServletConfig {

    private final ServletConfig config;

    /**
     * Create new servlet config
     * @param config wrapped servlet config
     */
    public ServletConfigWrapper(@NotNull final ServletConfig config) {
        this.config = config;
    }

    @Override
    public String getServletName() {
        return config.getServletName();
    }

    @Override
    public javax.servlet.ServletContext getServletContext() {
        return new ServletContextWrapper(config.getServletContext());
    }

    @Override
    public String getInitParameter(final String name) {
        return config.getInitParameter(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return config.getInitParameterNames();
    }
}

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

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;

/**
 * Servlet wrapper
 */
public class ServletWrapper implements javax.servlet.Servlet {

    private final Servlet servlet;

    /**
     * Create new servlet
     * @param servlet wrapped servlet
     */
    public ServletWrapper(@NotNull final Servlet servlet) {
        this.servlet = servlet;
    }

    @Override
    public void init(final javax.servlet.ServletConfig config) throws javax.servlet.ServletException {
        try {
            servlet.init(new org.apache.felix.http.base.internal.jakartawrappers.ServletConfigWrapper(config));
        } catch (final ServletException e) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

    @Override
    public javax.servlet.ServletConfig getServletConfig() {
        return new ServletConfigWrapper(servlet.getServletConfig());
    }

    @Override
    public void service(final javax.servlet.ServletRequest req, final javax.servlet.ServletResponse res)
            throws javax.servlet.ServletException, IOException {
        try {
            servlet.service(org.apache.felix.http.base.internal.jakartawrappers.ServletRequestWrapper.getWrapper(req),
                    org.apache.felix.http.base.internal.jakartawrappers.ServletResponseWrapper.getWrapper(res));
        } catch (final ServletException e) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

    @Override
    public String getServletInfo() {
        return servlet.getServletInfo();
    }

    @Override
    public void destroy() {
        servlet.destroy();
    }

    /**
     * Get the wrapped servlet
     * @return The servlet
     */
    public @NotNull Servlet getServlet() {
        return this.servlet;
    }
}

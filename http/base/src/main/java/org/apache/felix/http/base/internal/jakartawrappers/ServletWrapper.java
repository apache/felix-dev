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
package org.apache.felix.http.base.internal.jakartawrappers;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Servlet wrapper
 */
public class ServletWrapper implements Servlet {

    private final javax.servlet.Servlet servlet;

    /**
     * Get the registered servlet
     * @param s The servlet
     * @return The registered servlet
     */
    public static Servlet getRegisteredServlet(final javax.servlet.Servlet s) {
        if ( s instanceof org.apache.felix.http.base.internal.javaxwrappers.ServletWrapper ) {
            return ((org.apache.felix.http.base.internal.javaxwrappers.ServletWrapper)s).getServlet();
        }
        return new ServletWrapper(s);
    }

    /**
     * Create new servlet
     * @param servlet wrapped servlet
     */
    public ServletWrapper(@NotNull final javax.servlet.Servlet servlet) {
        this.servlet = servlet;
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        try {
            servlet.init(new org.apache.felix.http.base.internal.javaxwrappers.ServletConfigWrapper(config));
        } catch (javax.servlet.ServletException e) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

    @Override
    public ServletConfig getServletConfig() {
        return new ServletConfigWrapper(servlet.getServletConfig());
    }

    @Override
    public void service(final ServletRequest req, final ServletResponse res)
            throws ServletException, IOException {
        try {
            servlet.service(org.apache.felix.http.base.internal.javaxwrappers.ServletRequestWrapper.getWrapper(req),
                    org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper.getWrapper(res));
        } catch (javax.servlet.ServletException e) {
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
    public @NotNull javax.servlet.Servlet getServlet() {
        return this.servlet;
    }
}

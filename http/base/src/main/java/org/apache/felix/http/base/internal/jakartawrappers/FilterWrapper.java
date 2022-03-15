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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Jakarta Filter based on a javax filter
 */
public class FilterWrapper implements Filter {

    private final javax.servlet.Filter filter;

    /**
     * Create new filter
     * @param filter wrapped filter
     */
    public FilterWrapper(@NotNull final javax.servlet.Filter filter) {
        this.filter = filter;
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        try {
            this.filter.init(new org.apache.felix.http.base.internal.javaxwrappers.FilterConfigWrapper(filterConfig));
        } catch (final javax.servlet.ServletException e) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            this.filter.doFilter(org.apache.felix.http.base.internal.javaxwrappers.ServletRequestWrapper.getWrapper(request),
                    org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper.getWrapper(response),
                    new org.apache.felix.http.base.internal.javaxwrappers.FilterChainWrapper(chain));
        } catch (final javax.servlet.ServletException e) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

    @Override
    public void destroy() {
        this.filter.destroy();
    }

    /**
     * Get the filter
     * @return The filter
     */
    public @NotNull javax.servlet.Filter getFilter() {
        return this.filter;
    }
}

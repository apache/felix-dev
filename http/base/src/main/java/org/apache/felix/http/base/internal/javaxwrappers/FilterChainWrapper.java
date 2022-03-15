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

import org.apache.felix.http.base.internal.jakartawrappers.ServletResponseWrapper;
import org.jetbrains.annotations.NotNull;

import jakarta.servlet.FilterChain;

/**
 * Javax filter chain based on a jakarta filter chain
 */
public class FilterChainWrapper implements javax.servlet.FilterChain {

    private final FilterChain filterChain;

    /**
     * Create new chain
     * @param chain Wrapped chain
     */
    public FilterChainWrapper(@NotNull final FilterChain chain) {
        this.filterChain = chain;
    }

    @Override
    public void doFilter(final javax.servlet.ServletRequest request, final javax.servlet.ServletResponse response)
            throws IOException, javax.servlet.ServletException {
        try {
            filterChain.doFilter(org.apache.felix.http.base.internal.jakartawrappers.ServletRequestWrapper.getWrapper(request),
                    ServletResponseWrapper.getWrapper(response));
        } catch (final jakarta.servlet.ServletException e) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }
}

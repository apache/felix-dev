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
package org.apache.felix.http.jakartawrappers;

import java.io.IOException;

import javax.servlet.FilterChain;

import org.apache.felix.http.javaxwrappers.ServletRequestWrapper;
import org.apache.felix.http.javaxwrappers.ServletResponseWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * Jakarta filter chain based on a javax filter chain
 */
public class FilterChainWrapper implements jakarta.servlet.FilterChain
{
    private final FilterChain filterChain;


    /**
     * Create new chain
     *
     * @param chain Wrapped chain
     */
    public FilterChainWrapper(@NotNull final FilterChain chain)
    {
        this.filterChain = chain;
    }


    @Override
    public void doFilter(final jakarta.servlet.ServletRequest request, final jakarta.servlet.ServletResponse response)
                    throws IOException, jakarta.servlet.ServletException
    {
        try
        {
            filterChain.doFilter(ServletRequestWrapper.getWrapper(request),
                                 ServletResponseWrapper.getWrapper(response));
        }
        catch (final javax.servlet.ServletException e)
        {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

}

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
package org.apache.felix.http.base.internal.handler;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.jakartawrappers.ServletWrapper;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.apache.felix.http.base.internal.service.HttpServiceFactory;

import jakarta.servlet.Servlet;

/**
 * Servlet handler for servlets registered through the http service.
 */
public final class HttpServiceServletHandler extends ServletHandler
{
    /**
     * New handler
     * @param context The context
     * @param servletInfo The servlet info
     * @param servlet The servlet
     */
    public HttpServiceServletHandler(final ExtServletContext context,
            final ServletInfo servletInfo,
            final javax.servlet.Servlet servlet)
    {
        this(HttpServiceFactory.HTTP_SERVICE_CONTEXT_SERVICE_ID, context, servletInfo, ServletWrapper.getRegisteredServlet(servlet));
    }

    /**
     * New handler
     * @param contextServiceId The context id
     * @param context The context
     * @param servletInfo The servlet info
     * @param servlet The servlet
     */
    public HttpServiceServletHandler(final long contextServiceId,
            final ExtServletContext context,
            final ServletInfo servletInfo,
            final Servlet servlet)
    {
        super(contextServiceId, context, servletInfo);
        this.setServlet(servlet);
    }
}

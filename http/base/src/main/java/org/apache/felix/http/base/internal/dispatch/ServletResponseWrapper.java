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
package org.apache.felix.http.base.internal.dispatch;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.apache.felix.http.base.internal.handler.FilterHandler;
import org.apache.felix.http.base.internal.handler.ServletHandler;
import org.apache.felix.http.base.internal.registry.PerContextHandlerRegistry;

final class ServletResponseWrapper extends HttpServletResponseWrapper
{

    private final HttpServletRequest request;

    private final AtomicInteger invocationCount = new AtomicInteger();

    private final PerContextHandlerRegistry errorRegistry;

    private final String servletName;

    public ServletResponseWrapper(@NotNull final HttpServletRequest req,
            @NotNull final HttpServletResponse res,
            @Nullable final String servletName,
            @Nullable final PerContextHandlerRegistry errorRegistry)
    {
        super(res);
        this.request = req;
        this.servletName = servletName;
        this.errorRegistry = errorRegistry;
    }

    @Override
    public void sendError(int sc) throws IOException
    {
        sendError(sc, null);
    }

    @Override
    public void sendError(final int code, final String message) throws IOException
    {
        resetBuffer();

        setStatus(code);

        boolean invokeSuper = true;

        if ( invocationCount.incrementAndGet() == 1 )
        {
            // If we are allowed to have a body
            if (code != SC_NO_CONTENT &&
                    code != SC_NOT_MODIFIED &&
                    code != SC_PARTIAL_CONTENT &&
                    code >= SC_OK)
            {
                final Throwable exception = (Throwable)request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                final ServletHandler errorResolution = (errorRegistry == null ? null :
                    errorRegistry.getErrorHandler(code, exception));

                if ( errorResolution != null )
                {
                    try
                    {
                        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, code);
                        if ( message != null )
                        {
                            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
                        }
                        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());
                        if ( this.servletName != null )
                        {
                            request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, this.servletName);
                        }

                        final String servletPath = null;
                        final String pathInfo = request.getRequestURI();
                        final String queryString = null;

                        final RequestInfo requestInfo = new RequestInfo(servletPath, pathInfo, queryString, pathInfo,
                                request.getHttpServletMapping().getServletName(),
                                request.getHttpServletMapping().getPattern(),
                                request.getHttpServletMapping().getMatchValue(),
                                request.getHttpServletMapping().getMappingMatch(),
                                false);

                        final FilterHandler[] filterHandlers = errorRegistry.getFilterHandlers(errorResolution, DispatcherType.ERROR, request.getRequestURI());

                        final ServletRequestWrapper reqWrapper = new ServletRequestWrapper(request,
                                errorResolution.getContext(),
                                requestInfo,
                                DispatcherType.ERROR,
                                false,
                                null,
                                null);
                        final FilterChain filterChain = new InvocationChain(errorResolution, filterHandlers);
                        filterChain.doFilter(reqWrapper, this);

                        invokeSuper = false;
                    }
                    catch (final ServletException e)
                    {
                        // ignore
                    }
                    finally
                    {
                        request.removeAttribute(RequestDispatcher.ERROR_STATUS_CODE);
                        request.removeAttribute(RequestDispatcher.ERROR_MESSAGE);
                        request.removeAttribute(RequestDispatcher.ERROR_REQUEST_URI);
                        request.removeAttribute(RequestDispatcher.ERROR_SERVLET_NAME);
                        request.removeAttribute(RequestDispatcher.ERROR_EXCEPTION);
                        request.removeAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE);
                    }
                }
            }
        }
        if ( invokeSuper )
        {
            super.sendError(code, message);
        }
    }
}

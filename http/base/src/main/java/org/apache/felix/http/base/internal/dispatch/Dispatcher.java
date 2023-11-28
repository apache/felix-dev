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
import java.util.Set;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.handler.FilterHandler;
import org.apache.felix.http.base.internal.handler.HttpSessionWrapper;
import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.apache.felix.http.base.internal.registry.HandlerRegistry;
import org.apache.felix.http.base.internal.registry.PathResolution;
import org.apache.felix.http.base.internal.registry.PerContextHandlerRegistry;
import org.apache.felix.http.base.internal.whiteboard.WhiteboardManager;
import org.apache.felix.http.jakartawrappers.ServletExceptionWrapper;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.servlet.whiteboard.Preprocessor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public final class Dispatcher
{
    private final HandlerRegistry handlerRegistry;

    private volatile WhiteboardManager whiteboardManager;

    public Dispatcher(final HandlerRegistry handlerRegistry)
    {
        this.handlerRegistry = handlerRegistry;
    }

    /**
     * Set or unset the whiteboard manager.
     * @param service The whiteboard manager or {@code null}
     */
    public void setWhiteboardManager(@Nullable final WhiteboardManager service)
    {
        this.whiteboardManager = service;
    }

    /**
     * Responsible for dispatching a given request to the actual applicable servlet and/or filters in the local registry.
     *
     * @param req the {@link ServletRequest} to dispatch;
     * @param res the {@link ServletResponse} to dispatch.
     * @throws ServletException in case of exceptions during the actual dispatching;
     * @throws IOException in case of I/O problems.
     */
    public void dispatch(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException
    {
        final WhiteboardManager mgr = this.whiteboardManager;
        if ( mgr == null )
        {
            // not active, always return 404
            if ( !res.isCommitted() )
            {
                res.sendError(404);
            }
            return;
        }

        // check for invalidating session(s) first
        final HttpSession session = req.getSession(false);
        if ( session != null )
        {
            final Set<String> names = HttpSessionWrapper.getExpiredSessionContextNames(session);
            mgr.sessionDestroyed(session, names);
        }

        // invoke preprocessors and then dispatching
        mgr.invokePreprocessors(req, res, new Preprocessor() {

			@Override
			public void init(final FilterConfig filterConfig) throws ServletException
			{
				// nothing to do
		    }

			@Override
			public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
			throws IOException, ServletException
			{
				final HttpServletRequest req = (HttpServletRequest)request;
				final HttpServletResponse res = (HttpServletResponse)response;
		        // get full decoded path for dispatching
		        // we can't use req.getRequestURI() or req.getRequestURL() as these are returning the encoded path
		        String path = req.getServletPath();
		        if ( path == null )
		        {
		            path = "";
		        }
		        if ( req.getPathInfo() != null )
		        {
		            path = path.concat(req.getPathInfo());
		        }
		        final String requestURI = path;

		        // Determine which servlet we should forward the request to...
		        final PathResolution pr = handlerRegistry.resolveServlet(requestURI);

		        final PerContextHandlerRegistry errorRegistry = (pr != null ? pr.handlerRegistry : handlerRegistry.getBestMatchingRegistry(requestURI));
		        final String servletName = (pr != null ? pr.handler.getName() : null);
		        final HttpServletResponse wrappedResponse = new ServletResponseWrapper(req, res, servletName, errorRegistry);
		        if ( pr == null )
		        {
                    if ( !wrappedResponse.isCommitted() )
                    {
                        wrappedResponse.sendError(404);
                    }
		            return;
		        }

		        final ExtServletContext servletContext = pr.handler.getContext();
		        final RequestInfo requestInfo = new RequestInfo(pr.servletPath, pr.pathInfo, null, req.getRequestURI(),
		                pr.handler.getName(), pr.matchedPattern, pr.matchValue, pr.match, false);
		        final HttpServletRequest wrappedRequest = new ServletRequestWrapper(req, servletContext, requestInfo, null,
		                pr.handler.getServletInfo().isAsyncSupported(),
		                pr.handler.getMultipartConfig(),
		                pr.handler.getMultipartSecurityContext());
		        final FilterHandler[] filterHandlers = handlerRegistry.getFilters(pr, req.getDispatcherType(), pr.requestURI);

		        try
		        {
		            if ( servletContext.getServletRequestListener() != null )
		            {
		                servletContext.getServletRequestListener().requestInitialized(new ServletRequestEvent(servletContext, wrappedRequest));
		            }
		            final FilterChain filterChain = new InvocationChain(pr.handler, filterHandlers);
		            filterChain.doFilter(wrappedRequest, wrappedResponse);

		        }
		        catch ( Exception e)
		        {
                    if ( e instanceof ServletExceptionWrapper ) {
                        e = ((ServletExceptionWrapper)e).getException();
                    }
		            SystemLogger.LOGGER.error("Exception while processing request to " + requestURI, e);
		            req.setAttribute(RequestDispatcher.ERROR_EXCEPTION, e);
		            req.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, e.getClass().getName());

                    if ( !wrappedResponse.isCommitted() )
                    {
                        wrappedResponse.sendError(500);
                    }
		        }
		        finally
		        {
		            if ( servletContext.getServletRequestListener() != null )
		            {
		                servletContext.getServletRequestListener().requestDestroyed(new ServletRequestEvent(servletContext, wrappedRequest));
		            }
		        }
		    }

			@Override
			public void destroy()
			{
				// nothing to do
			}
		});

    }
}

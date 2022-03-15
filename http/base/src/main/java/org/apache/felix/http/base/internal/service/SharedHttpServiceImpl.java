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
package org.apache.felix.http.base.internal.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.handler.HttpServiceServletHandler;
import org.apache.felix.http.base.internal.handler.ServletHandler;
import org.apache.felix.http.base.internal.jakartawrappers.ServletWrapper;
import org.apache.felix.http.base.internal.registry.HandlerRegistry;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.http.NamespaceException;

/**
 * Shared http service implementation
 * This implementation is shared by all bundles using the http service.
 */
public final class SharedHttpServiceImpl
{
    private final HandlerRegistry handlerRegistry;

    private final Map<String, ServletHandler> aliasMap = new HashMap<>();

    /**
     * Create a new implementation
     * @param handlerRegistry The handler registry
     */
    public SharedHttpServiceImpl(final HandlerRegistry handlerRegistry)
    {
        if (handlerRegistry == null)
        {
            throw new IllegalArgumentException("HandlerRegistry cannot be null!");
        }

        this.handlerRegistry = handlerRegistry;
    }

    /**
     * Register a servlet
     * @param alias The alias
     * @param httpContext The servlet context
     * @param servlet The servlet
     * @param servletInfo The info for the servlet
     * @throws javax.servlet.ServletException If registration fails
     * @throws NamespaceException If a servlet for the same alias already exists
     */
    public void registerServlet(@NotNull final String alias,
            @NotNull final ExtServletContext httpContext,
            @NotNull final javax.servlet.Servlet servlet,
            @NotNull final ServletInfo servletInfo) throws javax.servlet.ServletException, NamespaceException
    {
        final ServletHandler handler = new HttpServiceServletHandler(httpContext, servletInfo, servlet);

        synchronized (this.aliasMap)
        {
            if (this.aliasMap.containsKey(alias))
            {
                throw new NamespaceException("Alias " + alias + " is already in use.");
            }
            this.handlerRegistry.getRegistry(handler.getContextServiceId()).registerServlet(handler);

            this.aliasMap.put(alias, handler);
        }
    }

    /**
     * Unregister a servlet
     * @param alias The alias
     * @return The servlet or {@code null}
     * @see org.osgi.service.http.HttpService#unregister(java.lang.String)
     */
    public javax.servlet.Servlet unregister(final String alias)
    {
        synchronized (this.aliasMap)
        {
            final ServletHandler handler = this.aliasMap.remove(alias);
            if (handler == null)
            {
                throw new IllegalArgumentException("Nothing registered at " + alias);
            }

            final javax.servlet.Servlet s = getServlet(handler);
            if ( handler.getServlet() instanceof HttpResourceServlet ) {
                final HttpResourceServlet resource = (HttpResourceServlet)handler.getServlet();
                resource.setWrapper(null);
            }
            this.handlerRegistry.getRegistry(handler.getContextServiceId()).unregisterServlet(handler.getServletInfo(), true);
            return s;
        }
    }

    private javax.servlet.Servlet getServlet(final ServletHandler handler) {
        final javax.servlet.Servlet s;
        if ( handler.getServlet() instanceof HttpResourceServlet ) {
            final HttpResourceServlet resource = (HttpResourceServlet)handler.getServlet();
            s = resource.getWrapper();
            resource.setWrapper(null);
        } else {
            s = ((ServletWrapper)handler.getServlet()).getServlet();
        }
        return s;
    }

    /**
     * Unregister a servlet
     * @param servlet The servlet
     */
    public void unregisterServlet(final javax.servlet.Servlet servlet)
    {
        if (servlet != null)
        {
            synchronized (this.aliasMap)
            {
                final Iterator<Map.Entry<String, ServletHandler>> i = this.aliasMap.entrySet().iterator();
                while (i.hasNext())
                {
                    final Map.Entry<String, ServletHandler> entry = i.next();
                    final javax.servlet.Servlet s = getServlet(entry.getValue());
                    if (s == servlet)
                    {
                        this.handlerRegistry.getRegistry(entry.getValue().getContextServiceId()).unregisterServlet(entry.getValue().getServletInfo(), false);

                        i.remove();
                        break;
                    }

                }
            }
        }
    }

    /**
     * Get the handler registry
     * @return The registry
     */
	public @NotNull HandlerRegistry getHandlerRegistry()
	{
		return this.handlerRegistry;
	}
}

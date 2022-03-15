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

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.apache.felix.http.base.internal.util.PatternUtil;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.Bundle;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

import jakarta.servlet.ServletContext;

/**
 * This implementation of the {@link HttpService} implements the front end
 * used by client bundles. It performs the validity checks and passes the
 * real operation to the shared http service.
 */
public final class PerBundleHttpServiceImpl implements HttpService
{
    private final Bundle bundle;
    private final Set<javax.servlet.Servlet> localServlets = new HashSet<>();
    private final ServletContextManager contextManager;
    private final SharedHttpServiceImpl sharedHttpService;

    /**
     * Create a new http service front end
     * @param bundle The using bundle
     * @param sharedHttpService The shared service
     * @param context The context
     * @param sharedContextAttributes Shared context attributes?
     */
    public PerBundleHttpServiceImpl(final Bundle bundle,
            final SharedHttpServiceImpl sharedHttpService,
            final ServletContext context,
            final boolean sharedContextAttributes)
    {
        if (bundle == null)
        {
            throw new IllegalArgumentException("Bundle cannot be null!");
        }
        if (context == null)
        {
            throw new IllegalArgumentException("Context cannot be null!");
        }

        this.bundle = bundle;
        this.contextManager = new ServletContextManager(this.bundle,
        		context,
        		sharedContextAttributes,
        		sharedHttpService.getHandlerRegistry().getRegistry(HttpServiceFactory.HTTP_SERVICE_CONTEXT_SERVICE_ID));
        this.sharedHttpService = sharedHttpService;
    }

    @Override
    public HttpContext createDefaultHttpContext()
    {
        return new DefaultHttpContext(this.bundle);
    }

    /**
     * No need to sync this method, syncing is done via {@link #registerServlet(String, javax.servlet.Servlet, Dictionary, HttpContext)}
     * @see org.osgi.service.http.HttpService#registerResources(java.lang.String, java.lang.String, org.osgi.service.http.HttpContext)
     */
    @Override
    public void registerResources(final String alias, final String name, final HttpContext context) throws NamespaceException
    {
        if (!isNameValid(name))
        {
            throw new IllegalArgumentException("Malformed resource name [" + name + "]");
        }

        if (!PatternUtil.isValidPattern(alias) || !alias.startsWith("/") )
        {
            throw new IllegalArgumentException("Malformed resource alias [" + alias + "]");
        }
        try
        {
            final HttpResourceServlet servlet = new HttpResourceServlet(name);
            final javax.servlet.Servlet wrapper = new org.apache.felix.http.base.internal.javaxwrappers.ServletWrapper(servlet);
            servlet.setWrapper(wrapper);
            registerServlet(alias, wrapper, null, context);
        }
        catch (javax.servlet.ServletException e)
        {
            SystemLogger.error("Failed to register resources", e);
        }
    }

    /**
     * @see org.osgi.service.http.HttpService#registerServlet(java.lang.String, javax.servlet.Servlet, java.util.Dictionary, org.osgi.service.http.HttpContext)
     */
    @Override
    public void registerServlet(String alias, javax.servlet.Servlet servlet, @SuppressWarnings("rawtypes") Dictionary initParams, HttpContext context)
            throws javax.servlet.ServletException, NamespaceException
    {
        if (servlet == null)
        {
            throw new IllegalArgumentException("Servlet must not be null");
        }
        if (!PatternUtil.isValidPattern(alias) || !alias.startsWith("/") )
        {
            throw new IllegalArgumentException("Malformed servlet alias [" + alias + "]");
        }

        final Map<String, String> paramMap = new HashMap<>();
        if (initParams != null && initParams.size() > 0)
        {
            @SuppressWarnings("rawtypes")
            Enumeration e = initParams.keys();
            while (e.hasMoreElements())
            {
                Object key = e.nextElement();
                Object value = initParams.get(key);

                if ((key instanceof String) && (value instanceof String))
                {
                    paramMap.put((String) key, (String) value);
                }
            }
        }

        synchronized (this.localServlets)
        {
            if (this.localServlets.contains(servlet))
            {
                throw new javax.servlet.ServletException("Servlet instance " + servlet + " already registered");
            }
            this.localServlets.add(servlet);
        }

        final ServletInfo servletInfo = new ServletInfo(String.format("%s_%d", servlet.getClass(), this.hashCode()), alias, paramMap);
        final ExtServletContext httpContext = getServletContext(context);

        boolean success = false;
        try
        {
            this.sharedHttpService.registerServlet(alias, httpContext,  servlet,  servletInfo);
            success = true;
        }
        finally
        {
            if ( !success )
            {
                synchronized ( this.localServlets )
                {
                    this.localServlets.remove(servlet);
                }
            }
        }
    }

    /**
     * @see org.osgi.service.http.HttpService#unregister(java.lang.String)
     */
    @Override
    public void unregister(final String alias)
    {
        final javax.servlet.Servlet servlet = this.sharedHttpService.unregister(alias);
        if ( servlet != null )
        {
            synchronized ( this.localServlets )
            {
                this.localServlets.remove(servlet);
            }
        }
    }

    /**
     * Remove all registered servlets
     */
    public void unregisterAll()
    {
        final Set<javax.servlet.Servlet> servlets = new HashSet<>(this.localServlets);
        for (final javax.servlet.Servlet servlet : servlets)
        {
            unregisterServlet(servlet);
        }
    }

    private void unregisterServlet(final javax.servlet.Servlet servlet)
    {
        if (servlet != null)
        {
            synchronized ( this.localServlets )
            {
                this.localServlets.remove(servlet);
            }
            this.sharedHttpService.unregisterServlet(servlet);
        }
    }

    /**
     * Get a servlet context
     * @param context http context
     * @return servlet context
     */
    public @NotNull ExtServletContext getServletContext(HttpContext context)
    {
        if (context == null)
        {
            context = createDefaultHttpContext();
        }

        return this.contextManager.getServletContext(context);
    }

    private boolean isNameValid(final String name)
    {
        if (name == null)
        {
            return false;
        }

        if (!name.equals("/") && name.endsWith("/"))
        {
            return false;
        }

        return true;
    }
}

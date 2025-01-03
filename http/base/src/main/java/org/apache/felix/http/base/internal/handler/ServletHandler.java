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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.dispatch.MultipartConfig;
import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.apache.felix.http.jakartawrappers.ServletWrapper;
import org.osgi.framework.Bundle;
import org.osgi.service.servlet.runtime.dto.DTOConstants;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * The servlet handler handles the initialization and destruction of
 * a servlet.
 */
public abstract class ServletHandler implements Comparable<ServletHandler>
{
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    private static final String JAVA_SERVLET_TEMP_DIR_PROP = "jakarta.servlet.content.tempdir";

    // The Jetty class used for Jetty WebSocket servlets
    private static final String JETTY_WEB_SOCKET_SERVLET_CLASS = "JettyWebSocketServlet";

    private final long contextServiceId;

    private final ServletInfo servletInfo;

    private final ExtServletContext context;

    private volatile Servlet servlet;

    protected volatile int useCount;

    private final AtomicBoolean lazyFirstInitCall = new AtomicBoolean(true);
    private final CountDownLatch initBarrier = new CountDownLatch(1);

    private final MultipartConfig mpConfig;

    public ServletHandler(final long contextServiceId,
            final ExtServletContext context,
            final ServletInfo servletInfo)
    {
        this.contextServiceId = contextServiceId;
        this.context = context;
        this.servletInfo = servletInfo;
        final MultipartConfig origConfig = servletInfo.getMultipartConfig();
        if ( origConfig != null )
        {
            String location = origConfig.multipartLocation;
            if ( location != null && location.trim().length() == 0 ) {
                location = null;
            }
            if ( location == null ) {
                final Object obj = context == null ? null : context.getAttribute(JAVA_SERVLET_TEMP_DIR_PROP);
                if ( obj != null ) {
                    if ( obj instanceof File ) {
                        location = ((File)obj).getAbsolutePath();
                    } else {
                        location = obj.toString();
                    }
                }
            }
            if ( location == null ) {
                location = TEMP_DIR;
            }
            this.mpConfig = new MultipartConfig(origConfig.multipartThreshold,
                    location,
                    origConfig.multipartMaxFileSize,
                    origConfig.multipartMaxRequestSize,
                    origConfig.multipartMaxFileCount);
        }
        else
        {
            this.mpConfig = null;
        }
    }

    @Override
    public int compareTo(final ServletHandler other)
    {
        return this.servletInfo.compareTo(other.servletInfo);
    }

    public long getContextServiceId()
    {
        return this.contextServiceId;
    }

    public ExtServletContext getContext()
    {
        return this.context;
    }

    public Servlet getServlet()
    {
        return servlet;
    }

    protected void setServlet(final Servlet s)
    {
        this.servlet = s;
    }

    public void handle(final ServletRequest req, final ServletResponse res)
            throws ServletException, IOException
    {
        final Servlet local = this.servlet;
        if ( local != null )
        {
            // Lazy init if needed, thread-safe
            lazyInit();

            local.service(req, res);
        }
        else
        {
            throw new ServletException("Servlet has been unregistered");
        }
    }

    public ServletInfo getServletInfo()
    {
        return this.servletInfo;
    }

    public String getName()
    {
        String name = this.servletInfo.getName();
        if (name == null )
        {
            final Servlet local = this.servlet;
            if ( local != null )
            {
                if (local instanceof ServletWrapper ) {
                    name = ((ServletWrapper)local).getServlet().getClass().getName();
                } else {
                    name = local.getClass().getName();
                }
            }
        }
        return name;
    }

    /**
     * Initialize the object
     * @return {code -1} on success, a failure reason according to {@link DTOConstants} otherwise.
     */
    public int init()
    {
        if ( this.useCount > 0 )
        {
            this.useCount++;
            return -1;
        }

        if (this.servlet == null)
        {
            return DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE;
        }

        if (!isJettyWebSocketServlet(servlet)) {
            try {
                servlet.init(new ServletConfigImpl(getName(), getContext(), getServletInfo().getInitParameters()));
            } catch (final Exception e) {
                SystemLogger.LOGGER.error(SystemLogger.formatMessage(this.getServletInfo().getServiceReference(),
                                "Error during calling init() on servlet ".concat(this.servletInfo.getClassName(this.servlet))),
                        e);
                return DTOConstants.FAILURE_REASON_EXCEPTION_ON_INIT;
            } finally {
                initBarrier.countDown();
            }
        }
        this.useCount++;
        return -1;
    }

    public boolean destroy()
    {
        if (this.servlet == null)
        {
            return false;
        }

        this.useCount--;
        if ( this.useCount == 0 )
        {
            try
            {
                if (!isJettyWebSocketServlet(servlet) || !lazyFirstInitCall.get()) {
                    servlet.destroy();
                }
            }
            catch ( final Exception ignore )
            {
                // we ignore this
                SystemLogger.LOGGER.error(SystemLogger.formatMessage(this.getServletInfo().getServiceReference(),
                        "Error during calling destroy() on servlet ".concat(this.servletInfo.getClassName(this.servlet))), ignore);
            }

            servlet = null;
            return true;
        }
        return false;
    }

    public boolean dispose()
    {
        // fully destroy the servlet
        this.useCount = 1;
        return this.destroy();
    }

    @Override
    public int hashCode()
    {
        return 31 + servletInfo.hashCode();
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || getClass() != obj.getClass() )
        {
            return false;
        }
        final ServletHandler other = (ServletHandler) obj;
        return servletInfo.equals(other.servletInfo);
    }

    public MultipartConfig getMultipartConfig()
    {
        return mpConfig;
    }

    public Bundle getMultipartSecurityContext()
    {
        return null;
    }

    /**
     * Check if the servlet is a JettyWebSocketServlet.
     * JettyWebSocket classes are handled differently due to FELIX-6746.
     * @param servlet the servlet to check
     * @return true if the servlet is a JettyWebSocketServlet, false otherwise
     */
    private static boolean isJettyWebSocketServlet(Object servlet) {
        final Class<?> superClass = servlet.getClass().getSuperclass();
        SystemLogger.LOGGER.debug("Checking if the servlet is a JettyWebSocketServlet: '" + superClass.getSimpleName() + "'");

        // Now check if the servlet class extends 'JettyWebSocketServlet'
        boolean isJettyWebSocketServlet = superClass.getSimpleName().endsWith(JETTY_WEB_SOCKET_SERVLET_CLASS);
        if (!isJettyWebSocketServlet) {
            // Recurse through the wrapped servlets, in case of double-wrapping
            if (servlet instanceof org.apache.felix.http.jakartawrappers.ServletWrapper) {
                final javax.servlet.Servlet wrappedServlet = ((org.apache.felix.http.jakartawrappers.ServletWrapper) servlet).getServlet();
                return isJettyWebSocketServlet(wrappedServlet);
            } else if (servlet instanceof org.apache.felix.http.javaxwrappers.ServletWrapper) {
                final jakarta.servlet.Servlet wrappedServlet = ((org.apache.felix.http.javaxwrappers.ServletWrapper) servlet).getServlet();
                return isJettyWebSocketServlet(wrappedServlet);
            }
        }
        return isJettyWebSocketServlet;
    }

    /*
     * Lazy initialization of the servlet.
     * Will only be called once for each servlet instance and is thread-safe.
     */
    private void lazyInit() {
        if (lazyFirstInitCall.compareAndSet(true, false)) {
            try {
                servlet.init(new ServletConfigImpl(getName(), getContext(), getServletInfo().getInitParameters()));
            } catch (final Exception e) {
                SystemLogger.LOGGER.error(SystemLogger.formatMessage(this.getServletInfo().getServiceReference(),
                                "Error calling init() lazy on servlet ".concat(this.servletInfo.getClassName(this.servlet))), e);
            } finally {
                initBarrier.countDown();
            }
        } else {
            try {
                initBarrier.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

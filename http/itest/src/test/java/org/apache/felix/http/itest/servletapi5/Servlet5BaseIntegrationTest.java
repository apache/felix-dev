/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.http.itest.servletapi5;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.felix.http.itest.BaseIntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Base class for integration tests.
 */
public abstract class Servlet5BaseIntegrationTest extends BaseIntegrationTest {
    
    private CountDownLatch initLatch;
    private CountDownLatch destroyLatch;

    protected final List<ServiceRegistration<?>> registrations = new ArrayList<>();

    public void setupLatches(final int count) {
        this.initLatch = new CountDownLatch(count);
        this.destroyLatch = new CountDownLatch(count);
    }

    public void waitForInit() throws InterruptedException {
        assertTrue(initLatch.await(5, TimeUnit.SECONDS));
    }

    public void waitForDestroy() throws InterruptedException {
        assertTrue(destroyLatch.await(5, TimeUnit.SECONDS));
        destroyLatch = null;
    }

    protected class TestFilter implements Filter {

        @Override
        public void destroy() {
            if (destroyLatch != null) {
                destroyLatch.countDown();
            }
        }

        @Override
        public final void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
                throws IOException, ServletException {
            filter((HttpServletRequest) req, (HttpServletResponse) resp, chain);
        }

        @Override
        public void init(FilterConfig config) throws ServletException {
            if (initLatch != null) {
                initLatch.countDown();
            }
        }

        protected void filter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
                throws IOException, ServletException {
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }

    protected class TestServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        public void destroy() {
            super.destroy();
            if (destroyLatch != null) {
                destroyLatch.countDown();
            }
        }

        @Override
        public void init() throws ServletException {
            super.init();
            if (initLatch != null) {
                initLatch.countDown();
            }
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }

    public HttpServiceRuntime getHttpServiceRuntime() {
        final HttpServiceRuntime runtime = this.getService(HttpServiceRuntime.class);
        assertNotNull(runtime);
        return runtime;
    }

    private ServiceListener serviceListener;

    private final AtomicLong runtimeCounter = new AtomicLong();

    @Before
    public void setup() throws InvalidSyntaxException {
        this.serviceListener = new ServiceListener() {

            @Override
            public void serviceChanged(final ServiceEvent event) {
                runtimeCounter.set((Long) event.getServiceReference().getProperty("service.changecount"));
            }

        };
        this.m_context.addServiceListener(this.serviceListener,
                "(objectClass=" + HttpServiceRuntime.class.getName() + ")");
    }

    public long getRuntimeCounter() {
        return this.runtimeCounter.get();
    }

    public long waitForRuntime(final long oldCounter) {
        while (runtimeCounter.get() == oldCounter) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        return runtimeCounter.get();
    }

    @After
    public void unregisterServices() throws InterruptedException {
        if (this.serviceListener != null) {
            this.m_context.removeServiceListener(this.serviceListener);
            this.serviceListener = null;
        }
        for (final ServiceRegistration<?> serviceRegistration : registrations) {
            try {
                serviceRegistration.unregister();
            } catch (final IllegalStateException ignore) {
                // ignore
            }
        }
        if (destroyLatch != null) {
            waitForDestroy();
        }
    }

    protected ServiceRegistration<Servlet> registerAsyncServlet(final String path, final Servlet servlet) {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_ASYNC_SUPPORTED, true);
        return this.registerServlet(path, servlet, props);
    }

    protected ServiceRegistration<Servlet> registerServlet(final String path, final Servlet servlet) {
        return this.registerServlet(path, servlet, new Hashtable<>());
    }

    protected ServiceRegistration<Servlet> registerServlet(final String path, final Servlet servlet,
            final Dictionary<String, Object> props) {
        props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, path);
        final ServiceRegistration<Servlet> reg = this.m_context.registerService(Servlet.class, servlet, props);
        this.registrations.add(reg);
        return reg;
    }

    protected void unregister(final ServiceRegistration<Servlet> reg) {
        reg.unregister();
        this.registrations.remove(reg);
    }
}

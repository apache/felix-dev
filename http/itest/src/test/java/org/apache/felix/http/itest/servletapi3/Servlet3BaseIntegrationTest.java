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
package org.apache.felix.http.itest.servletapi3;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.http.itest.BaseIntegrationTest;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

/**
 * Base class for integration tests.
 */
public abstract class Servlet3BaseIntegrationTest extends BaseIntegrationTest {
    
    protected static class TestFilter implements Filter {
        private final CountDownLatch m_initLatch;
        private final CountDownLatch m_destroyLatch;

        public TestFilter() {
            this(null, null);
        }

        public TestFilter(CountDownLatch initLatch, CountDownLatch destroyLatch) {
            m_initLatch = initLatch;
            m_destroyLatch = destroyLatch;
        }

        @Override
        public void destroy() {
            if (m_destroyLatch != null) {
                m_destroyLatch.countDown();
            }
        }

        @Override
        public final void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
            filter((HttpServletRequest) req, (HttpServletResponse) resp, chain);
        }

        @Override
        public void init(FilterConfig config) throws ServletException {
            if (m_initLatch != null) {
                m_initLatch.countDown();
            }
        }

        protected void filter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }

    protected static class TestServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private final CountDownLatch m_initLatch;
        private final CountDownLatch m_destroyLatch;

        public TestServlet() {
            this(null, null);
        }

        public TestServlet(CountDownLatch initLatch, CountDownLatch destroyLatch) {
            m_initLatch = initLatch;
            m_destroyLatch = destroyLatch;
        }

        @Override
        public void destroy() {
            super.destroy();
            if (m_destroyLatch != null) {
                m_destroyLatch.countDown();
            }
        }

        @Override
        public void init() throws ServletException {
            super.init();
            if (m_initLatch != null) {
                m_initLatch.countDown();
            }
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }

    protected HttpService getHttpService() {
        return getService(HttpService.class);
    }

    protected void register(String alias, Servlet servlet) throws ServletException, NamespaceException {
        register(alias, servlet, null);
    }

    protected void register(String alias, Servlet servlet, HttpContext context) throws ServletException, NamespaceException {
        getHttpService().registerServlet(alias, servlet, null, context);
    }

    protected void register(String alias, String name) throws ServletException, NamespaceException {
        register(alias, name, null);
    }

    protected void register(String alias, String name, HttpContext context) throws ServletException, NamespaceException {
        getHttpService().registerResources(alias, name, context);
    }


    protected void unregister(String alias) throws ServletException, NamespaceException {
        getHttpService().unregister(alias);
    }
}

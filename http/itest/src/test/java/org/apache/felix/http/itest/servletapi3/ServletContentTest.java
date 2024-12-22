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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.http.javaxwrappers.ServletWrapper;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.ServiceRegistration;

import jakarta.servlet.http.HttpServlet;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class ServletContentTest extends Servlet3BaseIntegrationTest {

    private static final String CONTENT = "myservletcontent";

    private List<ServiceRegistration<?>> registrations = new ArrayList<>();

    private CountDownLatch initLatch;
    private CountDownLatch destroyLatch;

    public void setupLatches(int count) {
        initLatch = new CountDownLatch(count);
        destroyLatch = new CountDownLatch(count);
    }

    public void setupServlet(final String path) throws Exception {
        Dictionary<String, Object> servletProps = new Hashtable<>();
        servletProps.put(HTTP_WHITEBOARD_SERVLET_PATTERN, path);

        TestServlet servletWithErrorCode = new TestServlet(initLatch, destroyLatch) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
                resp.setContentType("text/plain");
                resp.setCharacterEncoding("utf-8");
                PrintWriter writer = resp.getWriter();
                resp.setCharacterEncoding("utf-16");
                writer.print(CONTENT);
            }
        };

        registrations.add(m_context.registerService(Servlet.class.getName(), servletWithErrorCode, servletProps));
    }

    @After
    public void unregisterServices() throws InterruptedException {
        for (ServiceRegistration<?> serviceRegistration : registrations) {
            serviceRegistration.unregister();
        }

        assertTrue(destroyLatch.await(5, TimeUnit.SECONDS));

        Thread.sleep(500);
    }

    protected void assertContent(final String path) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) createURL(path).openConnection();

        int rc = conn.getResponseCode();
        assertEquals("Unexpected response code,", 200, rc);

        assertEquals("text/plain;charset=utf-8", conn.getHeaderField("content-type"));
        try (InputStream is = conn.getInputStream()) {
            assertEquals(CONTENT, slurpAsString(is));
        } finally {
            conn.disconnect();
        }
    }

    @Test
    public void testContentAndHeaders() throws Exception {
        setupLatches(1);

        setupServlet("/myservlet");

        assertTrue(initLatch.await(5, TimeUnit.SECONDS));

        assertContent("/myservlet");
    }

    private class JakartaServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        public void init() throws jakarta.servlet.ServletException {
            super.init();
            initLatch.countDown();
        }

        @Override
        protected void doGet(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse resp)
            throws IOException {
            resp.getWriter().print("helloworld");
            resp.flushBuffer();
        }

        @Override
        public void destroy() {
            destroyLatch.countDown();
        }
    }

    @Test
    public void testRegisteringWrapperAsServlet() throws Exception  {
        this.setupLatches(1);

        final Dictionary<String, Object> servletProps = new Hashtable<>();
        servletProps.put(HTTP_WHITEBOARD_SERVLET_PATTERN, "/testjakarta");

        final ServiceRegistration<Servlet> reg = m_context.registerService(Servlet.class, new ServletWrapper(new JakartaServlet()), servletProps);

        assertTrue(initLatch.await(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS));

        assertContent("helloworld", createURL("/testjakarta"));

        reg.unregister();
        assertTrue(destroyLatch.await(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRegisteringCustomWrapperAsServlet() throws Exception  {
        this.setupLatches(1);

        final Dictionary<String, Object> servletProps = new Hashtable<>();
        servletProps.put(HTTP_WHITEBOARD_SERVLET_PATTERN, "/testjakarta");

        final ServiceRegistration<Servlet> reg = m_context.registerService(Servlet.class, new ServletWrapper(new JakartaServlet()) {
            @Override
            public void service(ServletRequest req, ServletResponse resp)
                throws IOException {
                resp.getWriter().print("helloworldwrapped");
                resp.flushBuffer();
            }
        }, servletProps);

        assertTrue(initLatch.await(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS));

        assertContent("helloworldwrapped", createURL("/testjakarta"));

        reg.unregister();
        assertTrue(destroyLatch.await(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS));
    }
}

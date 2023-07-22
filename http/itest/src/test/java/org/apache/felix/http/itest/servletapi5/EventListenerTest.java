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

import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * Test cases for all supported event listeners.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class EventListenerTest extends Servlet5BaseIntegrationTest {

    private Dictionary<String, Object> getListenerProps() {
        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER, "true");

        return props;
    }

    private Dictionary<String, Object> getServletProps(final String pattern) {
        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, pattern);

        return props;
    }

    /**
     * Tests that {@link HttpSessionListener}s are called whenever a session is created or destroyed.
     */
    @Test
    public void testHttpSessionListenerOk() throws Exception {
        final CountDownLatch createdLatch = new CountDownLatch(1);
        final CountDownLatch destroyedLatch = new CountDownLatch(1);

        HttpSessionListener listener = new HttpSessionListener() {
            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                destroyedLatch.countDown();
            }

            @Override
            public void sessionCreated(HttpSessionEvent se) {
                createdLatch.countDown();
            }
        };

        this.registrations.add(m_context.registerService(HttpSessionListener.class, listener, getListenerProps()));
        this.setupLatches(1);
        this.registrations.add(m_context.registerService(Servlet.class, new TestServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                HttpSession session = req.getSession();
                session.setMaxInactiveInterval(2);

                resp.setStatus(SC_OK);
                resp.flushBuffer();
            }
        }, getServletProps("/session")));
        this.waitForInit();

        assertContent(SC_OK, null, createURL("/session"));

        // Session should been created...
        assertTrue(createdLatch.await(50, TimeUnit.SECONDS));

        assertContent(SC_OK, null, createURL("/session"));

        // Session should timeout automatically...
        assertTrue(destroyedLatch.await(50, TimeUnit.SECONDS));
    }

    /**
     * Tests that {@link HttpSessionAttributeListener}s are called whenever a session attribute is added, changed or removed.
     */
    @Test
    public void testHttpSessionAttributeListenerOk() throws Exception {
        final CountDownLatch addedLatch = new CountDownLatch(1);
        final CountDownLatch removedLatch = new CountDownLatch(1);
        final CountDownLatch replacedLatch = new CountDownLatch(1);

        HttpSessionAttributeListener listener = new HttpSessionAttributeListener() {
            @Override
            public void attributeAdded(HttpSessionBindingEvent event) {
                addedLatch.countDown();
            }

            @Override
            public void attributeRemoved(HttpSessionBindingEvent event) {
                removedLatch.countDown();
            }

            @Override
            public void attributeReplaced(HttpSessionBindingEvent event) {
                replacedLatch.countDown();
            }
        };

        this.registrations
                .add(m_context.registerService(HttpSessionAttributeListener.class, listener, getListenerProps()));

        this.setupLatches(1);
        this.registrations.add(m_context.registerService(Servlet.class, new TestServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                try {
                    HttpSession session = req.getSession();

                    session.setAttribute("foo", "bar");

                    assertTrue(addedLatch.await(5, TimeUnit.SECONDS));

                    session.setAttribute("foo", "qux");

                    assertTrue(replacedLatch.await(5, TimeUnit.SECONDS));

                    session.removeAttribute("foo");

                    assertTrue(removedLatch.await(5, TimeUnit.SECONDS));

                    resp.setStatus(SC_OK);
                } catch (AssertionError ae) {
                    resp.sendError(SC_INTERNAL_SERVER_ERROR, ae.getMessage());
                    throw ae;
                } catch (InterruptedException e) {
                    resp.sendError(SC_SERVICE_UNAVAILABLE, e.getMessage());
                } finally {
                    resp.flushBuffer();
                }
            }
        }, getServletProps("/session")));

        this.waitForInit();
        assertContent(SC_OK, null, createURL("/session"));
    }

    /**
     * Tests {@link ServletContextListener}s
     */
    @Test
    public void testServletContextListener() throws Exception {

        final CountDownLatch initLatch = new CountDownLatch(1);
        final CountDownLatch destroyLatch = new CountDownLatch(1);

        final ServletContextListener listener = new ServletContextListener() {

            @Override
            public void contextInitialized(final ServletContextEvent sce) {
                initLatch.countDown();
            }

            @Override
            public void contextDestroyed(final ServletContextEvent sce) {
                destroyLatch.countDown();
            }
        };

        // register with default context
        final ServiceRegistration<ServletContextListener> reg = m_context.registerService(ServletContextListener.class,
                listener, getListenerProps());

        try {
            assertTrue(initLatch.await(5, TimeUnit.SECONDS));
        } finally {
            reg.unregister();
        }
        assertTrue(destroyLatch.await(5, TimeUnit.SECONDS));
    }

    /**
     * Tests {@link ServletRequestListener}s
     */
    @Test
    public void testServletRequestListener() throws Exception {
        final List<String> list = new ArrayList<>();

        final ServletRequestListener listener = new ServletRequestListener() {

            @Override
            public void requestDestroyed(ServletRequestEvent arg0) {
                list.add("DESTROY");
            }

            @Override
            public void requestInitialized(ServletRequestEvent arg0) {
                list.add("INIT");
            }
        };

        // register with default context
        this.registrations.add(m_context.registerService(ServletRequestListener.class, listener, getListenerProps()));
        // register proprietary listener
        this.registrations.add(m_context.registerService(ServletRequestListener.class, listener, null));

        // register test servlet with default context
        this.setupLatches(1);
        this.registrations.add(m_context.registerService(Servlet.class, new TestServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                resp.setStatus(SC_OK);
                resp.flushBuffer();
            }
        }, getServletProps("/test")));
        this.waitForInit();

        assertEquals(0, list.size());
        assertContent(SC_OK, null, createURL("/test"));
        assertEquals(2, list.size());
        assertEquals("INIT", list.get(0));
        assertEquals("DESTROY", list.get(1));
    }
}

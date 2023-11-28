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

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.servlet.context.ServletContextHelper;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class ServletTest extends Servlet5BaseIntegrationTest {

    @Test
    public void testCorrectPathInfoInHttpContextOk() throws Exception {
        this.setupLatches(1);
        TestServlet servlet = new TestServlet() {

            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse resp)
                    throws ServletException, IOException {
                assertEquals("", request.getContextPath());
                assertEquals("/foo", request.getServletPath());
                assertEquals("/bar", request.getPathInfo());
                assertEquals("/foo/bar", request.getRequestURI());
                assertEquals("qux=quu", request.getQueryString());
                super.doGet(request, resp);
            }
        };

        registerServlet("/foo/*", servlet);

        this.waitForInit();
        final URL testURL = createURL("/foo/bar?qux=quu");
        assertResponseCode(SC_OK, testURL);
    }

    /**
     * Tests that we can register a servlet with Jetty and that its lifecycle is correctly controlled.
     */
    @Test
    public void testRegisterServletLifecycleOk() throws Exception {
        this.setupLatches(1);
        TestServlet servlet = new TestServlet();

        final ServiceRegistration<Servlet> reg = registerServlet("/test", servlet);

        this.waitForInit();

        this.unregister(reg);

        this.waitForDestroy();
    }

    /**
     * Tests that initialization parameters are properly passed.
     */
    @Test
    public void testInitParametersOk() throws Exception {
        this.setupLatches(1);
        Servlet servlet = new TestServlet() {
            @Override
            public void init(ServletConfig config) throws ServletException {
                String value1 = config.getInitParameter("key1");
                String value2 = config.getInitParameter("key2");
                if (!"value1".equals(value1) || !"value2".equals(value2)) {
                    throw new ServletException("Init parameters wrong");
                }
                super.init(config);
            }
        };
        Dictionary<String, Object> params = new Hashtable<>();
        params.put("servlet.init.key1", "value1");
        params.put("servlet.init.key2", "value2");

        this.registerServlet("/initTest", servlet, params);
        this.waitForInit();
    }

    @Test
    public void testUseServletContextOk() throws Exception {
        this.setupLatches(1);
        TestServlet servlet = new TestServlet()  {
            private static final long serialVersionUID = 1L;

            @Override
            public void init(ServletConfig config) throws ServletException {
                ServletContext context = config.getServletContext();
                assertEquals("", context.getContextPath());

                super.init(config);
            }
        };

        final ServiceRegistration<Servlet> reg = registerServlet("/foo", servlet);

        this.waitForInit();

        final URL testURL = createURL("/foo");

        assertResponseCode(SC_OK, testURL);

        unregister(reg);

        this.waitForDestroy();

        assertResponseCode(SC_NOT_FOUND, testURL);
    }

    public void setupServlet(final String name, String[] path, int rank, String context) throws Exception {
        Dictionary<String, Object> servletProps = new Hashtable<>();
        servletProps.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME, name);
        servletProps.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, path);
        servletProps.put(Constants.SERVICE_RANKING, rank);
        if (context != null) {
            servletProps.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT, "(" + HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME + "=" + context + ")");
        }

        long counter = this.getRuntimeCounter();
        TestServlet servletWithErrorCode = new TestServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
                resp.getWriter().print(name);
                resp.flushBuffer();
            }
        };

        registrations.add(m_context.registerService(Servlet.class.getName(), servletWithErrorCode, servletProps));
        this.waitForRuntime(counter);
    }

    private void setupContext(String name, String path) throws InterruptedException {
        Dictionary<String, ?> properties = createDictionary(
            HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME, name,
            HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH, path);

        ServletContextHelper servletContextHelper = new ServletContextHelper(m_context.getBundle()){
            // test helper
        };
        long counter = this.getRuntimeCounter();
        registrations.add(m_context.registerService(ServletContextHelper.class.getName(), servletContextHelper, properties));
        this.waitForRuntime(counter);
    }

    @Test
    public void testHighRankReplaces() throws Exception {
        setupServlet("lowRankServlet", new String[] { "/foo", "/bar" }, 1, null);
        setupServlet("highRankServlet", new String[] { "/foo", "/baz" }, 2, null);

        assertContent("highRankServlet", createURL("/foo"));
        assertContent("lowRankServlet", createURL("/bar"));
        assertContent("highRankServlet", createURL("/baz"));
    }

    @Test
    public void testSameRankDoesNotReplace() throws Exception {
        setupServlet("servlet1", new String[]{ "/foo", "/bar" }, 2, null);
        setupServlet("servlet2", new String[]{ "/foo", "/baz" }, 2, null);

        assertContent("servlet1", createURL("/foo"));
        assertContent("servlet1", createURL("/bar"));
        assertContent("servlet2", createURL("/baz"));
    }

    @Test
    public void testHighRankResourceReplaces() throws Exception {
        setupServlet("lowRankServlet", new String[]{ "/foo" }, 1, null);

        assertContent("lowRankServlet", createURL("/foo"));

        Dictionary<String, Object> resourceProps = new Hashtable<>();
        String highRankPattern[] = { "/foo" };
        resourceProps.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PATTERN, highRankPattern);
        resourceProps.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PREFIX, "/resource/test.html");
        resourceProps.put(Constants.SERVICE_RANKING, 2);

        long counter = this.getRuntimeCounter();
        registrations.add(m_context.registerService(Object.class.getName(),
            new Object(), resourceProps));
        this.waitForRuntime(counter);

        assertContent(getTestHtmlContent(), createURL("/foo"));
    }

    private String getTestHtmlContent() throws IOException {
        InputStream resourceAsStream = this.getClass().getResourceAsStream("/resource/test.html");
        return slurpAsString(resourceAsStream);
    }

    @Test
    public void contextWithLongerPrefixIsChosen() throws Exception {
        setupContext("contextA", "/a");
        setupContext("contextB", "/a/b");

        setupServlet("servlet1", new String[]{ "/b/test" }, 1, "contextA");

        assertContent("servlet1", createURL("/a/b/test"));

        setupServlet("servlet2", new String[]{ "/test" }, 1, "contextB");

        assertContent("servlet2", createURL("/a/b/test"));
    }

    @Test
    public void contextWithLongerPrefixIsChosenWithWildcard() throws Exception {
        setupContext("contextA", "/a");
        setupContext("contextB", "/a/b");

        setupServlet("servlet1", new String[]{ "/b/test/servlet" }, 1, "contextA");

        assertContent("servlet1", createURL("/a/b/test/servlet"));

        setupServlet("servlet2", new String[]{ "/test/*" }, 1, "contextB");

        assertContent("servlet2", createURL("/a/b/test/servlet"));
    }

    @Test
    public void pathMatchingTest() throws Exception {
        setupContext("contextA", "/a");

        setupServlet("servlet1", new String[]{ "/servlet/*" }, 1, "contextA");

        assertContent("servlet1", createURL("/a/servlet/foo"));
        assertContent("servlet1", createURL("/a/servlet"));
    }
}

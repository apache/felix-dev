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

import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;

import java.io.IOException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class HttpWhiteboardTargetTest extends Servlet5BaseIntegrationTest {

	private static final String SERVICE_HTTP_PORT = "org.osgi.service.http.port";

	/**]
	 * Test that a servlet with the org.osgi.http.whiteboard.target property not set
	 * is registered with the whiteboard
	 */
	@Test
	public void testServletNoTargetProperty() throws Exception {
        long counter = this.getRuntimeCounter();
		TestServlet servlet = new TestServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
				resp.getWriter().print("It works!");
				resp.flushBuffer();
			}
		};

		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/servletAlias");
		props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_INIT_PARAM_PREFIX + ".myname", "servletName");

		this.registrations.add(m_context.registerService(Servlet.class.getName(), servlet, props));
        counter = this.waitForRuntime(counter);

        URL testURL = createURL("/servletAlias");
        assertContent("It works!", testURL);
	}

	/**
	 * Test that a servlet with the org.osgi.http.whiteboard.target property matching the
	 * HttpServiceRuntime properties is registered with the whiteboard.
	 *
	 * In the current implementation the HttpServiceRuntime properties are the same as the
	 * HttpService properties.
	 *
	 */
	@Test
	public void testServletTargetMatchPort() throws Exception {
        long counter = this.getRuntimeCounter();
		TestServlet servlet = new TestServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
				resp.getWriter().print("matchingServlet");
				resp.flushBuffer();
			}
		};

		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/servletAlias");
		props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_INIT_PARAM_PREFIX + ".myname", "servletName");
		props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, "(" + SERVICE_HTTP_PORT + "=8080" + ")");

		this.registrations.add(m_context.registerService(Servlet.class.getName(), servlet, props));
        counter = this.waitForRuntime(counter);

        URL testURL = createURL("/servletAlias");
        assertContent("matchingServlet", testURL);
	}

	/**
	 * Test that a servlet with the org.osgi.http.whiteboard.target property not matching
	 * the properties of the HttpServiceRuntime is not registered with the whiteboard.
	 *
	 */
	@Test
	public void testServletTargetNotMatchPort() throws Exception {
        long counter = this.getRuntimeCounter();
		TestServlet nonMatchingServlet = new TestServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
				resp.getWriter().print("nonMatchingServlet");
				resp.flushBuffer();
			}
		};

		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/servletAlias");
		props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_INIT_PARAM_PREFIX + ".myname", "servletName");
		props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, "(" + SERVICE_HTTP_PORT + "=8282" + ")");

		this.registrations.add(m_context.registerService(Servlet.class.getName(), nonMatchingServlet, props));
        counter = this.waitForRuntime(counter);

        // the servlet will not be registered
        URL testURL = createURL("/servletAlias");
        assertResponseCode(404, testURL);
	}

	/**
	 * Test that a filter with no target property set is correctly registered with the whiteboard
	 *
	 */
	@Test
	public void testFilterNoTargetProperty() throws Exception {
        long counter = this.getRuntimeCounter();
		TestServlet servlet1 = new TestServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
				resp.getWriter().print("servlet1");
				resp.flushBuffer();
			}
		};
		Dictionary<String, Object> props1 = new Hashtable<String, Object>();
		props1.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/servlet/1");
		props1.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_INIT_PARAM_PREFIX + ".myname", "servlet1");
		props1.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, "(" + SERVICE_HTTP_PORT + "=8080" + ")");

		TestServlet servlet2 = new TestServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
				resp.getWriter().print("servlet2");
				resp.flushBuffer();
			}
		};
		Dictionary<String, Object> props2 = new Hashtable<String, Object>();
		props2.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/servlet/2");
		props2.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_INIT_PARAM_PREFIX + ".myname", "servle2");
		props2.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, "(" + SERVICE_HTTP_PORT + "=8080" + ")");

		TestFilter filter = new TestFilter() {
			@Override
			protected void filter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
				String param = req.getParameter("param");
				if ("forbidden".equals(param)) {
					resp.reset();
					resp.sendError(SC_FORBIDDEN);
					resp.flushBuffer();
				} else {
					chain.doFilter(req, resp);
				}
			}
		};

		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_PATTERN, "/servlet/1");
		props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_INIT_PARAM_PREFIX + ".myname", "servletName");

		this.registrations.add(m_context.registerService(Servlet.class.getName(), servlet1, props1));
        counter = this.waitForRuntime(counter);
		this.registrations.add(m_context.registerService(Servlet.class.getName(), servlet2, props2));
        counter = this.waitForRuntime(counter);
		this.registrations.add(m_context.registerService(Filter.class.getName(), filter, props));
        counter = this.waitForRuntime(counter);

		assertResponseCode(SC_FORBIDDEN, createURL("/servlet/1?param=forbidden"));
		assertContent("servlet1", createURL("/servlet/1?param=any"));
		assertContent("servlet1", createURL("/servlet/1"));

		assertResponseCode(SC_OK, createURL("/servlet/2?param=forbidden"));
		assertContent("servlet2", createURL("/servlet/2?param=forbidden"));
	}

	@Test
	public void testFilterTargetMatchPort() throws Exception {
        long counter = this.getRuntimeCounter();
		TestServlet servlet = new TestServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
				resp.getWriter().print("servlet");
				resp.flushBuffer();
			}
		};
		Dictionary<String, Object> sprops = new Hashtable<String, Object>();
		sprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/servlet");
		sprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_INIT_PARAM_PREFIX + ".myname", "servlet1");
		sprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, "(" + SERVICE_HTTP_PORT + "=8080" + ")");

		TestFilter filter = new TestFilter() {
			@Override
			protected void filter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
				String param = req.getParameter("param");
				if("forbidden".equals(param)) {
					resp.reset();
					resp.sendError(SC_FORBIDDEN);
					resp.flushBuffer();
				} else {
					chain.doFilter(req, resp);
				}
			}
		};

		Dictionary<String, Object> fprops = new Hashtable<String, Object>();
		fprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_PATTERN, "/servlet");
		fprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_INIT_PARAM_PREFIX + ".myname", "servletName");
		fprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, "(" + SERVICE_HTTP_PORT + "=8080" + ")");

		this.registrations.add(m_context.registerService(Servlet.class.getName(), servlet, sprops));
        counter = this.waitForRuntime(counter);
		this.registrations.add(m_context.registerService(Filter.class.getName(), filter, fprops));
        counter = this.waitForRuntime(counter);

		assertResponseCode(SC_FORBIDDEN, createURL("/servlet?param=forbidden"));
		assertContent("servlet", createURL("/servlet?param=any"));
		assertContent("servlet", createURL("/servlet"));
	}

	@Test
	public void testFilterTargetNotMatchPort() throws Exception {
        long counter = this.getRuntimeCounter();
		TestServlet servlet = new TestServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
				resp.getWriter().print("servlet");
				resp.flushBuffer();
			}
		};
		Dictionary<String, Object> sprops = new Hashtable<String, Object>();
		sprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/servlet");
		sprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_INIT_PARAM_PREFIX + ".myname", "servlet1");
		sprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, "(" + SERVICE_HTTP_PORT + "=8080" + ")");

		TestFilter filter = new TestFilter() {
			@Override
			protected void filter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
				String param = req.getParameter("param");
				if("forbidden".equals(param)) {
					resp.reset();
					resp.sendError(SC_FORBIDDEN);
					resp.flushBuffer();
				} else {
					chain.doFilter(req, resp);
				}
			}
		};

		Dictionary<String, Object> fprops = new Hashtable<String, Object>();
		fprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_PATTERN, "/servlet");
		fprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_INIT_PARAM_PREFIX + ".myname", "servletName");
		fprops.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, "(" + SERVICE_HTTP_PORT + "=8181" + ")");

		this.registrations.add(m_context.registerService(Filter.class.getName(), filter, fprops));
		this.registrations.add(m_context.registerService(Servlet.class.getName(), servlet, sprops));
        counter = this.waitForRuntime(counter);

		// servlet is registered
		// fitler is not registered

		assertResponseCode(SC_OK, createURL("/servlet?param=forbidden"));
		assertContent("servlet", createURL("/servlet?param=forbidden"));
		assertContent("servlet", createURL("/servlet?param=any"));
		assertContent("servlet", createURL("/servlet"));
	}
}

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

import static org.junit.Assert.assertEquals;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;
 
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.util.Dictionary;
import java.util.Hashtable;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
 
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class ServletContentTest extends Servlet5BaseIntegrationTest {

    private static final String CONTENT = "myservletcontent";

    public void setupServlet(final String path) throws Exception {
        Dictionary<String, Object> servletProps = new Hashtable<>();
        servletProps.put(HTTP_WHITEBOARD_SERVLET_PATTERN, path);

        TestServlet servletWithErrorCode = new TestServlet() {
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

        this.waitForInit();

        assertContent("/myservlet");
    }
}
 
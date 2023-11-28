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
package org.apache.felix.http.jakartawrappers;

import jakarta.servlet.ServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletResponse;

import org.junit.Test;

public class ServletRequestTest {

    private javax.servlet.ServletRequest createRequest() {
        return new javax.servlet.ServletRequest() {

            final private Map<String, Object> attributes = new HashMap<>();

            @Override
            public AsyncContext getAsyncContext() {
                return null;
            }

            @Override
            public Object getAttribute(String name) {
                return attributes.get(name);
            }

            @Override
            public Enumeration<String> getAttributeNames() {
                return Collections.enumeration(attributes.keySet());
            }

            @Override
            public String getCharacterEncoding() {
                return null;
            }

            @Override
            public int getContentLength() {
                return 0;
            }

            @Override
            public long getContentLengthLong() {
                return 0;
            }

            @Override
            public String getContentType() {
                return null;
            }

            @Override
            public DispatcherType getDispatcherType() {
                return null;
            }

            @Override
            public ServletInputStream getInputStream() throws IOException {
                return null;
            }

            @Override
            public String getLocalAddr() {
                return null;
            }

            @Override
            public String getLocalName() {
                return null;
            }

            @Override
            public int getLocalPort() {
                return 0;
            }

            @Override
            public Locale getLocale() {
                return null;
            }

            @Override
            public Enumeration<Locale> getLocales() {
                return null;
            }

            @Override
            public String getParameter(String name) {
                return null;
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                return null;
            }

            @Override
            public Enumeration<String> getParameterNames() {
                return null;
            }

            @Override
            public String[] getParameterValues(String name) {
                return null;
            }

            @Override
            public String getProtocol() {
                return null;
            }

            @Override
            public BufferedReader getReader() throws IOException {
                return null;
            }

            @Override
            public String getRealPath(String path) {
                return null;
            }

            @Override
            public String getRemoteAddr() {
                return null;
            }

            @Override
            public String getRemoteHost() {
                return null;
            }

            @Override
            public int getRemotePort() {
                return 0;
            }

            @Override
            public RequestDispatcher getRequestDispatcher(String path) {
                return null;
            }

            @Override
            public String getScheme() {
                return null;
            }

            @Override
            public String getServerName() {
                return null;
            }

            @Override
            public int getServerPort() {
                return 0;
            }

            @Override
            public ServletContext getServletContext() {
                return null;
            }

            @Override
            public boolean isAsyncStarted() {
                return false;
            }

            @Override
            public boolean isAsyncSupported() {
                return false;
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public void removeAttribute(String name) {
                attributes.remove(name);
            }

            @Override
            public void setAttribute(String name, Object o) {
                this.attributes.put(name, o);                
            }

            @Override
            public void setCharacterEncoding(String env) throws UnsupportedEncodingException {                
            }

            @Override
            public AsyncContext startAsync() throws IllegalStateException {
                return null;
            }

            @Override
            public AsyncContext startAsync(javax.servlet.ServletRequest servletRequest, ServletResponse servletResponse)
                    throws IllegalStateException {
                return null;
            }            
        };
    }

    @Test public void testAttributeGetterSetter() {
        final javax.servlet.ServletRequest sr = createRequest();
        final ServletRequest req = ServletRequestWrapper.getWrapper(sr);
        req.setAttribute("foo", "bar");
        assertEquals("bar", req.getAttribute("foo"));
        req.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, "500");
        assertEquals("500", req.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE));

        final List<String> names = Collections.list(req.getAttributeNames());
        assertEquals(2, names.size());
        assertTrue(names.contains("foo"));
        assertTrue(names.contains(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE));

        req.removeAttribute("foo");
        assertNull(req.getAttribute("foo"));
        req.removeAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE);
        assertNull(req.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE));
        assertFalse(req.getAttributeNames().hasMoreElements());
    }

    @Test public void testProvidedAttributes() {
        final javax.servlet.ServletRequest sr = createRequest();
        sr.setAttribute(javax.servlet.RequestDispatcher.ERROR_STATUS_CODE, "500");
        sr.setAttribute("foo", "bar");

        final ServletRequest req = ServletRequestWrapper.getWrapper(sr);
        assertEquals("bar", req.getAttribute("foo"));
        assertEquals("500", req.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE));

        final List<String> names = Collections.list(req.getAttributeNames());
        assertEquals(2, names.size());
        assertTrue(names.contains("foo"));
        assertTrue(names.contains(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE));

        req.removeAttribute("foo");
        assertNull(req.getAttribute("foo"));
        req.removeAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE);
        assertNull(req.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE));
        assertFalse(req.getAttributeNames().hasMoreElements());
    }
}

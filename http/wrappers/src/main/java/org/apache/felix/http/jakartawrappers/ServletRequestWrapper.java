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

import static jakarta.servlet.AsyncContext.ASYNC_CONTEXT_PATH;
import static jakarta.servlet.AsyncContext.ASYNC_MAPPING;
import static jakarta.servlet.AsyncContext.ASYNC_PATH_INFO;
import static jakarta.servlet.AsyncContext.ASYNC_QUERY_STRING;
import static jakarta.servlet.AsyncContext.ASYNC_REQUEST_URI;
import static jakarta.servlet.AsyncContext.ASYNC_SERVLET_PATH;
import static jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION;
import static jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION_TYPE;
import static jakarta.servlet.RequestDispatcher.ERROR_MESSAGE;
import static jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI;
import static jakarta.servlet.RequestDispatcher.ERROR_SERVLET_NAME;
import static jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE;
import static jakarta.servlet.RequestDispatcher.FORWARD_CONTEXT_PATH;
import static jakarta.servlet.RequestDispatcher.FORWARD_MAPPING;
import static jakarta.servlet.RequestDispatcher.FORWARD_PATH_INFO;
import static jakarta.servlet.RequestDispatcher.FORWARD_QUERY_STRING;
import static jakarta.servlet.RequestDispatcher.FORWARD_REQUEST_URI;
import static jakarta.servlet.RequestDispatcher.FORWARD_SERVLET_PATH;
import static jakarta.servlet.RequestDispatcher.INCLUDE_CONTEXT_PATH;
import static jakarta.servlet.RequestDispatcher.INCLUDE_MAPPING;
import static jakarta.servlet.RequestDispatcher.INCLUDE_PATH_INFO;
import static jakarta.servlet.RequestDispatcher.INCLUDE_QUERY_STRING;
import static jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI;
import static jakarta.servlet.RequestDispatcher.INCLUDE_SERVLET_PATH;
import static jakarta.servlet.RequestDispatcher.ERROR_QUERY_STRING;
import static jakarta.servlet.RequestDispatcher.ERROR_METHOD;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Servlet request wrapper
 */
public class ServletRequestWrapper implements ServletRequest {

    private static final String JAVAX_ERROR_METHOD = ERROR_METHOD.replace("jakarta", "javax");
    private static final String JAVAX_ERROR_QUERY_STRING = ERROR_QUERY_STRING.replace("jakarta", "javax");

    private final javax.servlet.ServletRequest request;

    /**
     * Get the wrapper
     * @param r The request
     * @return The wrapped request
     */
    public static ServletRequest getWrapper(final javax.servlet.ServletRequest r) {
        if ( r instanceof org.apache.felix.http.javaxwrappers.ServletRequestWrapper) {
            return ((org.apache.felix.http.javaxwrappers.ServletRequestWrapper)r).getRequest();
        }
        if ( r instanceof javax.servlet.http.HttpServletRequest ) {
            return new HttpServletRequestWrapper((javax.servlet.http.HttpServletRequest)r);
        }
        return new ServletRequestWrapper(r);
    }

    /**
     * Create a wrapper
     * @param r The wrapped request
     */
    public ServletRequestWrapper(@NotNull final javax.servlet.ServletRequest r) {
        this.request = r;
    }


    /**
     * Get the request
     * @return The request
     */
    public javax.servlet.ServletRequest getRequest() {
        return this.request;
    }

    private Object wrapHttpServletMapping(final Object value) {
        if ( value instanceof org.apache.felix.http.javaxwrappers.HttpServletMappingWrapper ) {
            return ((org.apache.felix.http.javaxwrappers.HttpServletMappingWrapper)value).getMapping();
        }
        if ( value instanceof javax.servlet.http.HttpServletMapping ) {
            return new HttpServletMappingWrapper((javax.servlet.http.HttpServletMapping)value);
        }
        return value;
    }

    public static String getTranslatedAttributeName(final String name) {
        if ( FORWARD_CONTEXT_PATH.equals(name) ) {
            return javax.servlet.RequestDispatcher.FORWARD_CONTEXT_PATH;

        } else if ( FORWARD_MAPPING.equals(name) ) {
            return javax.servlet.RequestDispatcher.FORWARD_MAPPING;

        } else if ( FORWARD_PATH_INFO.equals(name) ) {
            return javax.servlet.RequestDispatcher.FORWARD_PATH_INFO;

        } else if ( FORWARD_QUERY_STRING.equals(name) ) {
            return javax.servlet.RequestDispatcher.FORWARD_QUERY_STRING;

        } else if ( FORWARD_REQUEST_URI.equals(name) ) {
            return javax.servlet.RequestDispatcher.FORWARD_REQUEST_URI;

        } else if ( FORWARD_SERVLET_PATH.equals(name) ) {
            return javax.servlet.RequestDispatcher.FORWARD_SERVLET_PATH;

        } else if ( INCLUDE_CONTEXT_PATH.equals(name) ) {
            return javax.servlet.RequestDispatcher.INCLUDE_CONTEXT_PATH;

        } else if ( INCLUDE_MAPPING.equals(name) ) {
            return javax.servlet.RequestDispatcher.INCLUDE_MAPPING;

        } else if ( INCLUDE_PATH_INFO.equals(name) ) {
            return javax.servlet.RequestDispatcher.INCLUDE_PATH_INFO;

        } else if ( INCLUDE_QUERY_STRING.equals(name) ) {
            return javax.servlet.RequestDispatcher.INCLUDE_QUERY_STRING;

        } else if ( INCLUDE_REQUEST_URI.equals(name) ) {
            return javax.servlet.RequestDispatcher.INCLUDE_REQUEST_URI;

        } else if ( INCLUDE_SERVLET_PATH.equals(name) ) {
            return javax.servlet.RequestDispatcher.INCLUDE_SERVLET_PATH;

        } else if ( ERROR_EXCEPTION.equals(name) ) {
            return javax.servlet.RequestDispatcher.ERROR_EXCEPTION;

        } else if ( ERROR_EXCEPTION_TYPE.equals(name) ) {
            return javax.servlet.RequestDispatcher.ERROR_EXCEPTION_TYPE;

        } else if ( ERROR_MESSAGE.equals(name) ) {
            return javax.servlet.RequestDispatcher.ERROR_MESSAGE;

        } else if ( ERROR_REQUEST_URI.equals(name) ) {
            return javax.servlet.RequestDispatcher.ERROR_REQUEST_URI;

        } else if ( ERROR_SERVLET_NAME.equals(name) ) {
            return javax.servlet.RequestDispatcher.ERROR_SERVLET_NAME;

        } else if ( ERROR_STATUS_CODE.equals(name) ) {
            return javax.servlet.RequestDispatcher.ERROR_STATUS_CODE;

        // new in 6.1
        } else if ( ERROR_METHOD.equals(name) ) {
            return JAVAX_ERROR_METHOD;

        // new in 6.1
        } else if ( ERROR_QUERY_STRING.equals(name) ) {
            return JAVAX_ERROR_QUERY_STRING;

        } else if ( ASYNC_CONTEXT_PATH.equals(name) ) {
            return javax.servlet.AsyncContext.ASYNC_CONTEXT_PATH;

        } else if ( ASYNC_MAPPING.equals(name) ) {
            return javax.servlet.AsyncContext.ASYNC_MAPPING;

        } else if ( ASYNC_PATH_INFO.equals(name) ) {
            return javax.servlet.AsyncContext.ASYNC_PATH_INFO;

        } else if ( ASYNC_QUERY_STRING.equals(name) ) {
            return javax.servlet.AsyncContext.ASYNC_QUERY_STRING;

        } else if ( ASYNC_REQUEST_URI.equals(name) ) {
            return javax.servlet.AsyncContext.ASYNC_REQUEST_URI;

        } else if ( ASYNC_SERVLET_PATH.equals(name) ) {
            return javax.servlet.AsyncContext.ASYNC_SERVLET_PATH;
        }
        return null;
    }

    @Override
    public Object getAttribute(final String name) {
        final String translatedName = getTranslatedAttributeName(name);
        if ( translatedName != null ) {
            final Object value = this.request.getAttribute(translatedName);
            if ( FORWARD_MAPPING.equals(name) ) {
                return wrapHttpServletMapping(value);
            } else if ( INCLUDE_MAPPING.equals(name) ) {
                return wrapHttpServletMapping(value);
            } else if ( ASYNC_MAPPING.equals(name) ) {
                return wrapHttpServletMapping(value);
            }
            return value;
        }
        return this.request.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        final List<String> names = Collections.list(this.request.getAttributeNames());
        final List<String> translatedNames = new ArrayList<>();
        for(final String name : names) {
            final String translatedName = org.apache.felix.http.javaxwrappers.ServletRequestWrapper.getTranslatedAttributeName(name);
            if ( translatedName != null ) {
                translatedNames.add(translatedName);
            } else {
                translatedNames.add(name);
            }
        }
        return Collections.enumeration(translatedNames);
    }

    @Override
    public String getCharacterEncoding() {
        return this.request.getCharacterEncoding();
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        this.request.setCharacterEncoding(env);
    }

    @Override
    public int getContentLength() {
        return this.request.getContentLength();
    }

    @Override
    public long getContentLengthLong() {
        return this.request.getContentLengthLong();
    }

    @Override
    public String getContentType() {
        return this.request.getContentType();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new ServletInputStreamWrapper(this.request.getInputStream());
    }

    @Override
    public String getParameter(String name) {
        return this.request.getParameter(name);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return this.request.getParameterNames();
    }

    @Override
    public String[] getParameterValues(final String name) {
        return this.request.getParameterValues(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return this.request.getParameterMap();
    }

    @Override
    public String getProtocol() {
        return this.request.getProtocol();
    }

    @Override
    public String getScheme() {
        return this.request.getScheme();
    }

    @Override
    public String getServerName() {
        return this.request.getServerName();
    }

    @Override
    public int getServerPort() {
        return this.request.getServerPort();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return this.request.getReader();
    }

    @Override
    public String getRemoteAddr() {
        return this.request.getRemoteAddr();
    }

    @Override
    public String getRemoteHost() {
        return this.request.getRemoteHost();
    }

    @Override
    public void setAttribute(final String name, final Object o) {
        final String translatedName = getTranslatedAttributeName(name);
        if (translatedName != null) {
            this.request.removeAttribute(name);
            this.request.setAttribute(translatedName, o);
        } else {
            this.request.setAttribute(name, o);
        }
    }

    @Override
    public void removeAttribute(final String name) {
        final String translatedName = getTranslatedAttributeName(name);
        this.request.removeAttribute(name);
        if (translatedName != null) {
            this.request.removeAttribute(translatedName);
        }
    }

    @Override
    public Locale getLocale() {
        return this.request.getLocale();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return this.request.getLocales();
    }

    @Override
    public boolean isSecure() {
        return this.request.isSecure();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {
        final javax.servlet.RequestDispatcher dispatcher = this.request.getRequestDispatcher(path);
        if ( dispatcher != null ) {
            return new RequestDispatcherWrapper(dispatcher);
        }
        return null;
    }

    @Override
    public int getRemotePort() {
        return this.request.getRemotePort();
    }

    @Override
    public String getLocalName() {
        return this.request.getLocalName();
    }

    @Override
    public String getLocalAddr() {
        return this.request.getLocalAddr();
    }

    @Override
    public int getLocalPort() {
        return this.request.getLocalPort();
    }

    @Override
    public ServletContext getServletContext() {
        return new ServletContextWrapper(this.request.getServletContext());
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        return new AsyncContextWrapper(this.request.startAsync());
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
            throws IllegalStateException {
        return new AsyncContextWrapper(this.request.startAsync(
                org.apache.felix.http.javaxwrappers.ServletRequestWrapper.getWrapper(servletRequest),
                org.apache.felix.http.javaxwrappers.ServletResponseWrapper.getWrapper(servletResponse)));
    }

    @Override
    public boolean isAsyncStarted() {
        return this.request.isAsyncStarted();
    }

    @Override
    public boolean isAsyncSupported() {
        return this.request.isAsyncSupported();
    }

    @Override
    public AsyncContext getAsyncContext() {
        return new AsyncContextWrapper(this.request.getAsyncContext());
    }

    @Override
    public DispatcherType getDispatcherType() {
        switch (this.request.getDispatcherType()) {
        case ASYNC : return DispatcherType.ASYNC;
        case ERROR : return DispatcherType.ERROR;
        case FORWARD : return DispatcherType.FORWARD;
        case INCLUDE : return DispatcherType.INCLUDE;
        case REQUEST : return DispatcherType.REQUEST;
        }
        return null;
    }

    @Override
    public String getProtocolRequestId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRequestId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletConnection getServletConnection() {
        throw new UnsupportedOperationException();
    }
}

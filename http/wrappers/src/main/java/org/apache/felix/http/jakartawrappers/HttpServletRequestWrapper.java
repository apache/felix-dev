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

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import jakarta.servlet.http.PushBuilder;

/**
 * Http servlet request wrapper
 */
public class HttpServletRequestWrapper extends ServletRequestWrapper
    implements HttpServletRequest {

    private final javax.servlet.http.HttpServletRequest request;

    /**
     * Create new wrapper
     * @param r Wrapped request
     */
    public HttpServletRequestWrapper(@NotNull final javax.servlet.http.HttpServletRequest r) {
        super(r);
        this.request = r;
    }

    @Override
    public String getAuthType() {
        return this.request.getAuthType();
    }

    @Override
    public Cookie[] getCookies() {
        return CookieWrapper.wrap(this.request.getCookies());
    }

    @Override
    public long getDateHeader(final String name) {
        return this.request.getDateHeader(name);
    }

    @Override
    public String getHeader(final String name) {
        return this.request.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(final String name) {
        return this.request.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return this.request.getHeaderNames();
    }

    @Override
    public int getIntHeader(final String name) {
        return this.request.getIntHeader(name);
    }

    @Override
    public String getMethod() {
        return this.request.getMethod();
    }

    @Override
    public String getPathInfo() {
        return this.request.getPathInfo();
    }

    @Override
    public String getPathTranslated() {
        return this.request.getPathTranslated();
    }

    @Override
    public String getContextPath() {
        return this.request.getContextPath();
    }

    @Override
    public String getQueryString() {
        return this.request.getQueryString();
    }

    @Override
    public String getRemoteUser() {
        return this.request.getRemoteUser();
    }

    @Override
    public boolean isUserInRole(final String role) {
        return this.request.isUserInRole(role);
    }

    @Override
    public Principal getUserPrincipal() {
        return this.request.getUserPrincipal();
    }

    @Override
    public String getRequestedSessionId() {
        return this.request.getRequestedSessionId();
    }

    @Override
    public String getRequestURI() {
        return this.request.getRequestURI();
    }

    @Override
    public StringBuffer getRequestURL() {
        return this.request.getRequestURL();
    }

    @Override
    public String getServletPath() {
        return this.request.getServletPath();
    }

    @Override
    public HttpSession getSession(final boolean create) {
        final javax.servlet.http.HttpSession session = this.request.getSession(create);
        if ( session != null ) {
            return new HttpSessionWrapper(session);
        }
        return null;
    }

    @Override
    public HttpSession getSession() {
        return new HttpSessionWrapper(this.request.getSession());
    }

    @Override
    public String changeSessionId() {
        return this.request.changeSessionId();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return this.request.isRequestedSessionIdValid();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return this.request.isRequestedSessionIdFromCookie();
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return this.request.isRequestedSessionIdFromURL();
    }

    @Override
    public boolean authenticate(final HttpServletResponse response) throws IOException, ServletException {
        try {
            return this.request.authenticate((javax.servlet.http.HttpServletResponse)org.apache.felix.http.javaxwrappers.ServletResponseWrapper.getWrapper(response));
        } catch ( final javax.servlet.ServletException e ) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

    @Override
    public void login(final String username, final String password) throws ServletException {
        try {
            this.request.login(username, password);
        } catch ( final javax.servlet.ServletException e ) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

    @Override
    public void logout() throws ServletException {
        try {
            this.request.logout();
        } catch ( final javax.servlet.ServletException e ) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        try {
            final List<Part> result = new ArrayList<>();
            for(final javax.servlet.http.Part p : this.request.getParts()) {
                result.add(new PartWrapper(p));
            }
            return result;
        } catch ( final javax.servlet.ServletException e ) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

    @Override
    public Part getPart(final String name) throws IOException, ServletException {
        try {
            final javax.servlet.http.Part part = this.request.getPart(name);
            if (part != null) {
                return new PartWrapper(part);
            }
            return null;
        } catch ( final javax.servlet.ServletException e ) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(final Class<T> handlerClass) throws IOException, ServletException {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpServletMapping getHttpServletMapping() {
        return new HttpServletMappingWrapper(this.request.getHttpServletMapping());
    }

    @Override
    public PushBuilder newPushBuilder() {
        final javax.servlet.http.PushBuilder builder = this.request.newPushBuilder();
        if ( builder != null ) {
            return new PushBuilderWrapper(builder);
        }
        return null;
    }

    @Override
    public Map<String, String> getTrailerFields() {
        return this.request.getTrailerFields();
    }

    @Override
    public boolean isTrailerFieldsReady() {
        return this.request.isTrailerFieldsReady();
    }
}

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
package org.apache.felix.http.base.internal.jakartawrappers;

import java.io.IOException;
import java.util.Collection;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Http servlet response wrapper
*/
public class HttpServletResponseWrapper extends ServletResponseWrapper
    implements HttpServletResponse {

    private final javax.servlet.http.HttpServletResponse response;

    /**
     * Create a new response
     * @param r Wrapped response
     */
    public HttpServletResponseWrapper(@NotNull final javax.servlet.http.HttpServletResponse r) {
        super(r);
        this.response = r;
    }

    @Override
    public void addCookie(final Cookie cookie) {
        this.response.addCookie(new org.apache.felix.http.base.internal.javaxwrappers.CookieWrapper(cookie));
    }

    @Override
    public boolean containsHeader(final String name) {
        return this.response.containsHeader(name);
    }

    @Override
    public String encodeURL(final String url) {
        return this.response.encodeURL(url);
    }

    @Override
    public String encodeRedirectURL(final String url) {
        return this.response.encodeRedirectURL(url);
    }

    @SuppressWarnings("deprecation")
    @Override
    public String encodeUrl(final String url) {
        return this.response.encodeUrl(url);
    }

    @SuppressWarnings("deprecation")
    @Override
    public String encodeRedirectUrl(final String url) {
        return this.response.encodeRedirectUrl(url);
    }

    @Override
    public void sendError(final int sc, final String msg) throws IOException {
        this.response.sendError(sc, msg);
    }

    @Override
    public void sendError(final int sc) throws IOException {
        this.response.sendError(sc);
    }

    @Override
    public void sendRedirect(final String location) throws IOException {
        this.response.sendRedirect(location);
    }

    @Override
    public void setDateHeader(final String name, final long date) {
        this.response.setDateHeader(name, date);
    }

    @Override
    public void addDateHeader(final String name, final long date) {
        this.response.addDateHeader(name, date);
    }

    @Override
    public void setHeader(final String name, final String value) {
        this.response.setHeader(name, value);
    }

    @Override
    public void addHeader(final String name, final String value) {
        this.response.addHeader(name, value);
    }

    @Override
    public void setIntHeader(final String name, final int value) {
        this.response.setIntHeader(name, value);
    }

    @Override
    public void addIntHeader(final String name, final int value) {
        this.response.addIntHeader(name, value);
    }

    @Override
    public void setStatus(int sc) {
        this.response.setStatus(sc);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setStatus(final int sc, final String sm) {
        this.response.setStatus(sc, sm);
    }

    @Override
    public int getStatus() {
        return this.response.getStatus();
    }

    @Override
    public String getHeader(final String name) {
        return this.response.getHeader(name);
    }

    @Override
    public Collection<String> getHeaders(final String name) {
        return this.response.getHeaders(name);
    }

    @Override
    public Collection<String> getHeaderNames() {
        return this.response.getHeaderNames();
    }
}

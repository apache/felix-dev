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
package org.apache.felix.http.base.internal.dispatch;

import java.io.IOException;
import java.util.Locale;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Wrapper to handle dispatching of includes where all changes to headers
 * are simply silently ignored (see section 9.3 of the servlet specification)
 */
public class IncludeResponseWrapper extends HttpServletResponseWrapper {

    public IncludeResponseWrapper(final HttpServletResponse response) {
        super(response);
    }

    @Override
    public void reset() {
        // ignore if not committed
        if (getResponse().isCommitted()) {
            getResponse().reset();
        }
    }

    @Override
    public void setContentLength(final int len) {
        // ignore
    }

    @Override
    public void setContentLengthLong(final long len) {
        // ignore
    }

    @Override
    public void setContentType(final String type) {
        // ignore
    }

    @Override
    public void setLocale(final Locale loc) {
        // ignore
    }

    @Override
    public void setBufferSize(final int size) {
        // ignore
    }

    @Override
    public void addCookie(final Cookie cookie) {
        // ignore
    }


    @Override
    public void addDateHeader(final String name, final long value) {
        // ignore
    }

    @Override
    public void addHeader(final String name, final String value) {
        // ignore
    }

    @Override
    public void addIntHeader(final String name, final int value) {
        // ignore
    }

    @Override
    public void sendError(final int sc) throws IOException {
        // ignore
    }

    @Override
    public void sendError(final int sc, final String msg) throws IOException {
        // ignore
    }

    @Override
    public void sendRedirect(final String location) throws IOException {
        // ignore
    }

    @Override
    public void setDateHeader(final String name, final long value) {
        // ignore
    }

    @Override
    public void setHeader(final String name, final String value) {
        // ignore
    }

    @Override
    public void setIntHeader(final String name, final int value) {
        // ignore
    }

    @Override
    public void setStatus(final int sc) {
        // ignore
    }

    @Override
    public void setStatus(final int sc, final String msg) {
        // ignore
    }
}

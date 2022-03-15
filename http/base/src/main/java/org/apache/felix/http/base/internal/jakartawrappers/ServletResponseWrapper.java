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
import java.io.PrintWriter;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;

/**
 * Servlet response wrapper
 */
public class ServletResponseWrapper implements ServletResponse {

    private final javax.servlet.ServletResponse response;

    /**
     * Get a wrapper
     * @param r Response
     * @return Wrapped response
     */
    public static ServletResponse getWrapper(final javax.servlet.ServletResponse r) {
        if ( r instanceof org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper) {
            return ((org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper)r).getResponse();
        }
        if ( r instanceof javax.servlet.http.HttpServletResponse ) {
            return new HttpServletResponseWrapper((javax.servlet.http.HttpServletResponse)r);
        }
        return new ServletResponseWrapper(r);
    }

    /**
     * Create new response
     * @param r Wrapped response
     */
    public ServletResponseWrapper(@NotNull final javax.servlet.ServletResponse r) {
        this.response = r;
    }

    /**
     * Get the wrapped response
     * @return The response
     */
    public javax.servlet.ServletResponse getResponse() {
        return this.response;
    }

    @Override
    public String getCharacterEncoding() {
        return this.response.getCharacterEncoding();
    }

    @Override
    public String getContentType() {
        return this.response.getContentType();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return new ServletOutputStreamWrapper(this.response.getOutputStream());
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return this.response.getWriter();
    }

    @Override
    public void setCharacterEncoding(final String charset) {
        this.response.setCharacterEncoding(charset);
    }

    @Override
    public void setContentLength(final int len) {
        this.response.setContentLength(len);
    }

    @Override
    public void setContentLengthLong(final long len) {
        this.response.setContentLengthLong(len);
    }

    @Override
    public void setContentType(final String type) {
        this.response.setContentType(type);
    }

    @Override
    public void setBufferSize(final int size) {
        this.response.setBufferSize(size);
    }

    @Override
    public int getBufferSize() {
        return this.response.getBufferSize();
    }

    @Override
    public void flushBuffer() throws IOException {
        this.response.flushBuffer();
    }

    @Override
    public void resetBuffer() {
        this.response.resetBuffer();
    }

    @Override
    public boolean isCommitted() {
        return this.response.isCommitted();
    }

    @Override
    public void reset() {
        this.response.reset();
    }

    @Override
    public void setLocale(final Locale loc) {
        this.response.setLocale(loc);
    }

    @Override
    public Locale getLocale() {
        return this.response.getLocale();
    }
}

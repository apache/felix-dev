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

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * async context wrapper
 */
public class AsyncContextWrapper implements AsyncContext {

    private final javax.servlet.AsyncContext context;

    /**
     * Create new context
     * @param c Wrapped context
     */
    public AsyncContextWrapper(@NotNull final javax.servlet.AsyncContext c) {
        this.context = c;
    }

    @Override
    public ServletRequest getRequest() {
        return ServletRequestWrapper.getWrapper(context.getRequest());
    }

    @Override
    public ServletResponse getResponse() {
        return ServletResponseWrapper.getWrapper(context.getResponse());
    }

    @Override
    public boolean hasOriginalRequestAndResponse() {
        return context.hasOriginalRequestAndResponse();
    }

    @Override
    public void dispatch() {
        context.dispatch();
    }

    @Override
    public void dispatch(final String path) {
        context.dispatch(path);
    }

    @Override
    public void dispatch(final ServletContext sc, final String path) {
        context.dispatch(new org.apache.felix.http.base.internal.javaxwrappers.ServletContextWrapper(sc), path);
    }

    @Override
    public void complete() {
        context.complete();
    }

    @Override
    public void start(final Runnable run) {
        context.start(run);
    }

    @Override
    public void addListener(final AsyncListener listener) {
        context.addListener(new org.apache.felix.http.base.internal.javaxwrappers.AsyncListenerWrapper(listener));
    }

    @Override
    public void addListener(final AsyncListener listener, final ServletRequest servletRequest, final ServletResponse servletResponse) {
        context.addListener(new org.apache.felix.http.base.internal.javaxwrappers.AsyncListenerWrapper(listener),
                org.apache.felix.http.base.internal.javaxwrappers.ServletRequestWrapper.getWrapper(servletRequest),
                org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper.getWrapper(servletResponse));
    }

    @Override
    public <T extends AsyncListener> T createListener(final Class<T> clazz) throws ServletException {
        throw new ServletException();
    }

    @Override
    public void setTimeout(final long timeout) {
        context.setTimeout(timeout);
    }

    @Override
    public long getTimeout() {
        return context.getTimeout();
    }
}

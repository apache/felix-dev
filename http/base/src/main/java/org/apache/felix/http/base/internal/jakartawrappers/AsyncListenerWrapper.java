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

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;

/**
 * Async listener wrapper
 */
public class AsyncListenerWrapper implements AsyncListener {

    private final javax.servlet.AsyncListener listener;

    /**
     * Create new listener
     * @param c Wrapped listener
     */
    public AsyncListenerWrapper(@NotNull final javax.servlet.AsyncListener c) {
        this.listener = c;
    }

    @Override
    public void onComplete(final AsyncEvent event) throws IOException {
        this.listener.onComplete(new javax.servlet.AsyncEvent(
                new org.apache.felix.http.base.internal.javaxwrappers.AsyncContextWrapper(event.getAsyncContext()),
                org.apache.felix.http.base.internal.javaxwrappers.ServletRequestWrapper.getWrapper(event.getSuppliedRequest()),
                org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper.getWrapper(event.getSuppliedResponse()),
                event.getThrowable()));
    }

    @Override
    public void onTimeout(final AsyncEvent event) throws IOException {
        this.listener.onTimeout(new javax.servlet.AsyncEvent(
                new org.apache.felix.http.base.internal.javaxwrappers.AsyncContextWrapper(event.getAsyncContext()),
                org.apache.felix.http.base.internal.javaxwrappers.ServletRequestWrapper.getWrapper(event.getSuppliedRequest()),
                org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper.getWrapper(event.getSuppliedResponse()),
                event.getThrowable()));
    }

    @Override
    public void onError(final AsyncEvent event) throws IOException {
        this.listener.onError(new javax.servlet.AsyncEvent(
                new org.apache.felix.http.base.internal.javaxwrappers.AsyncContextWrapper(event.getAsyncContext()),
                org.apache.felix.http.base.internal.javaxwrappers.ServletRequestWrapper.getWrapper(event.getSuppliedRequest()),
                org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper.getWrapper(event.getSuppliedResponse()),
                event.getThrowable()));
    }

    @Override
    public void onStartAsync(final AsyncEvent event) throws IOException {
        this.listener.onStartAsync(new javax.servlet.AsyncEvent(
                new org.apache.felix.http.base.internal.javaxwrappers.AsyncContextWrapper(event.getAsyncContext()),
                org.apache.felix.http.base.internal.javaxwrappers.ServletRequestWrapper.getWrapper(event.getSuppliedRequest()),
                org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper.getWrapper(event.getSuppliedResponse()),
                event.getThrowable()));
    }
}

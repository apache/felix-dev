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
package org.apache.felix.http.base.internal.javaxwrappers;

import java.io.IOException;

import org.apache.felix.http.base.internal.jakartawrappers.ServletRequestWrapper;
import org.apache.felix.http.base.internal.jakartawrappers.ServletResponseWrapper;
import org.jetbrains.annotations.NotNull;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;

/**
 * Async listener wrapper
 */
public class AsyncListenerWrapper implements javax.servlet.AsyncListener {

    private final AsyncListener listener;

    /**
     * Create new listener
     * @param c Wrapped listener
     */
    public AsyncListenerWrapper(@NotNull final AsyncListener c) {
        this.listener = c;
    }

    @Override
    public void onComplete(final javax.servlet.AsyncEvent event) throws IOException {
        this.listener.onComplete(new AsyncEvent(
                new org.apache.felix.http.base.internal.jakartawrappers.AsyncContextWrapper(event.getAsyncContext()),
                ServletRequestWrapper.getWrapper(event.getSuppliedRequest()),
                ServletResponseWrapper.getWrapper(event.getSuppliedResponse()),
                event.getThrowable()));
    }

    @Override
    public void onTimeout(final javax.servlet.AsyncEvent event) throws IOException {
        this.listener.onTimeout(new AsyncEvent(
                new org.apache.felix.http.base.internal.jakartawrappers.AsyncContextWrapper(event.getAsyncContext()),
                ServletRequestWrapper.getWrapper(event.getSuppliedRequest()),
                ServletResponseWrapper.getWrapper(event.getSuppliedResponse()),
                event.getThrowable()));
    }

    @Override
    public void onError(final javax.servlet.AsyncEvent event) throws IOException {
        this.listener.onError(new AsyncEvent(
                new org.apache.felix.http.base.internal.jakartawrappers.AsyncContextWrapper(event.getAsyncContext()),
                ServletRequestWrapper.getWrapper(event.getSuppliedRequest()),
                ServletResponseWrapper.getWrapper(event.getSuppliedResponse()),
                event.getThrowable()));
    }

    @Override
    public void onStartAsync(final javax.servlet.AsyncEvent event) throws IOException {
        this.listener.onStartAsync(new AsyncEvent(
                new org.apache.felix.http.base.internal.jakartawrappers.AsyncContextWrapper(event.getAsyncContext()),
                ServletRequestWrapper.getWrapper(event.getSuppliedRequest()),
                ServletResponseWrapper.getWrapper(event.getSuppliedResponse()),
                event.getThrowable()));
    }
}

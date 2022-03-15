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

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Dispatcher wrapper
 */
public class RequestDispatcherWrapper implements RequestDispatcher {

    private final javax.servlet.RequestDispatcher dispatcher;

    /**
     * Create new dispatcher
     * @param dispatcher Dispatcher
     */
    public RequestDispatcherWrapper(@NotNull javax.servlet.RequestDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void forward(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
        try {
            dispatcher.forward(org.apache.felix.http.base.internal.javaxwrappers.ServletRequestWrapper.getWrapper(request),
                    org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper.getWrapper(response));
        } catch (final javax.servlet.ServletException e) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }

    @Override
    public void include(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
        try {
            dispatcher.include(org.apache.felix.http.base.internal.javaxwrappers.ServletRequestWrapper.getWrapper(request),
                    org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper.getWrapper(response));
        } catch (final javax.servlet.ServletException e) {
            throw ServletExceptionUtil.getServletException(e);
        }
    }
}

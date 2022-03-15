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

import jakarta.servlet.ServletException;

/**
 * Wrapper for servlet exception
 */
public class ServletExceptionWrapper extends ServletException {

    private static final long serialVersionUID = 1L;

    private final javax.servlet.ServletException exception;

    /**
     * Create new wrapepr exception
     * @param e Original exception
     */
    public ServletExceptionWrapper(@NotNull final javax.servlet.ServletException e) {
        this.exception = e;
    }

    /**
     * Get the original exception
     * @return The original exception
     */
    @NotNull public javax.servlet.ServletException getException() {
        return this.exception;
    }
}

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

import java.util.Set;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.http.PushBuilder;

/**
 * Push builder wrapper
 */
public class PushBuilderWrapper implements PushBuilder {

    private final javax.servlet.http.PushBuilder builder;

    /**
     * Create new wrapper
     * @param c Wrapped builder
     */
    public PushBuilderWrapper(@NotNull final javax.servlet.http.PushBuilder c) {
        this.builder = c;
    }

    @Override
    public PushBuilder method(final String method) {
        this.builder.method(method);
        return this;
    }

    @Override
    public PushBuilder queryString(final String queryString) {
        this.builder.queryString(queryString);
        return this;
    }

    @Override
    public PushBuilder sessionId(final String sessionId) {
        this.builder.sessionId(sessionId);
        return this;
    }

    @Override
    public PushBuilder setHeader(final String name, final String value) {
        this.builder.setHeader(name, value);
        return this;
    }

    @Override
    public PushBuilder addHeader(final String name, final String value) {
        this.builder.addHeader(name, value);
        return this;
    }

    @Override
    public PushBuilder removeHeader(final String name) {
        this.builder.removeHeader(name);
        return this;
    }

    @Override
    public PushBuilder path(final String path) {
        this.builder.path(path);
        return this;
    }

    @Override
    public void push() {
        this.builder.push();
    }

    @Override
    public String getMethod() {
        return this.builder.getMethod();
    }

    @Override
    public String getQueryString() {
        return this.builder.getQueryString();
    }

    @Override
    public String getSessionId() {
        return this.builder.getSessionId();
    }

    @Override
    public Set<String> getHeaderNames() {
        return this.builder.getHeaderNames();
    }

    @Override
    public String getHeader(final String name) {
        return this.builder.getHeader(name);
    }

    @Override
    public String getPath() {
        return this.builder.getPath();
    }
}

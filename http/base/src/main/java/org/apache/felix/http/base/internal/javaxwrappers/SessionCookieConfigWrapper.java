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

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.SessionCookieConfig;

/**
 * Session cookie config wrapper
 */
public class SessionCookieConfigWrapper implements javax.servlet.SessionCookieConfig {

    private final SessionCookieConfig config;

    /**
     * Create new config
     * @param c Wrapped config
     */
    public SessionCookieConfigWrapper(@NotNull final SessionCookieConfig c) {
        this.config = c;
    }

    @Override
    public void setName(final String name) {
        config.setName(name);
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public void setDomain(final String domain) {
        config.setDomain(domain);
    }

    @Override
    public String getDomain() {
        return config.getDomain();
    }

    @Override
    public void setPath(final String path) {
        config.setPath(path);
    }

    @Override
    public String getPath() {
        return config.getPath();
    }

    @Override
    public void setComment(final String comment) {
        config.setComment(comment);
    }

    @Override
    public String getComment() {
        return config.getComment();
    }

    @Override
    public void setHttpOnly(final boolean httpOnly) {
        config.setHttpOnly(httpOnly);
    }

    @Override
    public boolean isHttpOnly() {
        return config.isHttpOnly();
    }

    @Override
    public void setSecure(final boolean secure) {
        config.setSecure(secure);
    }

    @Override
    public boolean isSecure() {
        return config.isSecure();
    }

    @Override
    public void setMaxAge(final int maxAge) {
        config.setMaxAge(maxAge);
    }

    @Override
    public int getMaxAge() {
        return config.getMaxAge();
    }
}
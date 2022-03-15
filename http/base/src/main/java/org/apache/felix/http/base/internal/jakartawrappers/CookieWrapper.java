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

import jakarta.servlet.http.Cookie;

/**
 * Cookie
 */
public class CookieWrapper extends Cookie {

    private static final long serialVersionUID = 5230437594501050941L;

    /**
     * Wrap an array of cookies
     * @param array The array
     * @return The result
     */
    public static Cookie[] wrap(final javax.servlet.http.Cookie[] array) {
        if ( array == null ) {
            return null;
        }
        final Cookie[] result = new Cookie[array.length];
        for(int i=0;i<array.length;i++) {
            result[i] = new CookieWrapper(array[i]);
        }
        return result;
    }

    private final javax.servlet.http.Cookie cookie;

    /**
     * Create new cookie
     * @param c Wrapped cookie
     */
    public CookieWrapper(@NotNull final javax.servlet.http.Cookie c) {
        super(c.getName(), c.getValue());
        this.cookie = c;
        super.setComment(c.getComment());
        super.setDomain(c.getDomain());
        super.setHttpOnly(c.isHttpOnly());
        super.setMaxAge(c.getMaxAge());
        super.setPath(c.getPath());
        super.setSecure(c.getSecure());
        super.setVersion(c.getVersion());
    }

    @Override
    public void setComment(final String purpose) {
        this.cookie.setComment(purpose);
        super.setComment(purpose);
    }

    @Override
    public void setDomain(final String domain) {
        this.cookie.setDomain(domain);
        super.setDomain(domain);
    }

    @Override
    public void setMaxAge(final int expiry) {
        this.cookie.setMaxAge(expiry);
        super.setMaxAge(expiry);
    }

    @Override
    public void setPath(final String uri) {
        this.cookie.setPath(uri);
        super.setPath(uri);
    }

    @Override
    public void setSecure(final boolean flag) {
        this.cookie.setSecure(flag);
        super.setSecure(flag);
    }

    @Override
    public void setValue(final String newValue) {
        this.cookie.setValue(newValue);
        super.setValue(newValue);
    }

    @Override
    public void setVersion(final int v) {
        this.cookie.setVersion(v);
        super.setVersion(v);
    }

    @Override
    public void setHttpOnly(final boolean isHttpOnly) {
        this.cookie.setHttpOnly(isHttpOnly);
        super.setHttpOnly(isHttpOnly);
    }
}

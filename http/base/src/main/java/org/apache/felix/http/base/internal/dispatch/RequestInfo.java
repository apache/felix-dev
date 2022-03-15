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

import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.MappingMatch;

/**
 * Information about the request
 */
public final class RequestInfo implements HttpServletMapping
{
    final String servletPath;
    final String pathInfo;
    final String queryString;
    final String requestURI;
    private final String matchServletName;
    private final String matchPattern;
    private final String matchValue;
    private final MappingMatch match;
    final boolean nameMatch;

    /**
     * Create a new request info
     * @param servletPath The servlet path
     * @param pathInfo The path info
     * @param queryString The query string
     * @param requestURI The request uri
     * @param matchServletName The servlet name
     * @param matchPattern The servlet pattern
     * @param matchValue The value matching
     * @param match The match type
     * @param nameMatch Is named dispatcher
     */
    public RequestInfo(final String servletPath,
            final String pathInfo,
            final String queryString,
            final String requestURI,
            final String matchServletName,
            final String matchPattern,
            final String matchValue,
            final MappingMatch match,
            final boolean nameMatch)
    {
        this.servletPath = servletPath;
        this.pathInfo = pathInfo;
        this.queryString = queryString;
        this.requestURI = requestURI;
        this.matchServletName = matchServletName;
        this.matchPattern = matchPattern;
        this.matchValue = matchValue;
        this.match = match;
        this.nameMatch = nameMatch;
    }

    @Override
    public String getMatchValue() {
        return this.matchValue;
    }

    @Override
    public String getPattern() {
        return this.matchPattern;
    }

    @Override
    public String getServletName() {
        return this.matchServletName;
    }

    @Override
    public MappingMatch getMappingMatch() {
        return this.match;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("RequestInfo[servletPath =");
        sb.append(this.servletPath).append(", pathInfo = ").append(this.pathInfo);
        sb.append(", queryString = ").append(this.queryString).append("]");
        return sb.toString();
    }
}

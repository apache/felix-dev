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

import jakarta.servlet.http.HttpServletMapping;

/**
 * Http Mapping wrapper
 */
public class HttpServletMappingWrapper implements javax.servlet.http.HttpServletMapping {

    private final HttpServletMapping mapping;

    /**
     * Create new wrapper
     * @param c Wrapped mapper
     */
    public HttpServletMappingWrapper(@NotNull final HttpServletMapping c) {
        this.mapping = c;
    }

    @Override
    public String getMatchValue() {
        return mapping.getMatchValue();
    }

    @Override
    public String getPattern() {
        return mapping.getPattern();
    }

    @Override
    public String getServletName() {
        return mapping.getServletName();
    }

    @Override
    public javax.servlet.http.MappingMatch getMappingMatch() {
        switch (mapping.getMappingMatch()) {
        case CONTEXT_ROOT : return javax.servlet.http.MappingMatch.CONTEXT_ROOT;
        case DEFAULT : return javax.servlet.http.MappingMatch.DEFAULT;
        case EXACT : return javax.servlet.http.MappingMatch.EXACT;
        case EXTENSION : return javax.servlet.http.MappingMatch.EXTENSION;
        case PATH : return javax.servlet.http.MappingMatch.PATH;
        }
        return null;
    }

    /**
     * Get the wrapped mapping
     * @return The mapping
     */
    public HttpServletMapping getMapping() {
        return this.mapping;
    }
}

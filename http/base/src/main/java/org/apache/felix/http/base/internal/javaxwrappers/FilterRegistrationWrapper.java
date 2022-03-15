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

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;

/**
 * Filter registration wrapper
 */
public class FilterRegistrationWrapper implements javax.servlet.FilterRegistration {

    private final FilterRegistration reg;

    /**
     * Create new wrapper
     * @param c Wrapped registration
     */
    public FilterRegistrationWrapper(@NotNull final FilterRegistration c) {
        this.reg = c;
    }

    private EnumSet<DispatcherType> wrap(final EnumSet<javax.servlet.DispatcherType> dispatcherTypes) {
        final EnumSet<DispatcherType> set = EnumSet.noneOf(DispatcherType.class);
        if ( dispatcherTypes.contains(javax.servlet.DispatcherType.ASYNC)) {
            set.add(DispatcherType.ASYNC);
        }
        if ( dispatcherTypes.contains(javax.servlet.DispatcherType.ERROR)) {
            set.add(DispatcherType.ERROR);
        }
        if ( dispatcherTypes.contains(javax.servlet.DispatcherType.FORWARD)) {
            set.add(DispatcherType.FORWARD);
        }
        if ( dispatcherTypes.contains(javax.servlet.DispatcherType.INCLUDE)) {
            set.add(DispatcherType.INCLUDE);
        }
        if ( dispatcherTypes.contains(javax.servlet.DispatcherType.REQUEST)) {
            set.add(DispatcherType.REQUEST);
        }
        return set;
    }

    @Override
    public void addMappingForServletNames(final EnumSet<javax.servlet.DispatcherType> dispatcherTypes, final boolean isMatchAfter,
            final String... servletNames) {
        reg.addMappingForServletNames(wrap(dispatcherTypes), isMatchAfter, servletNames);
    }

    @Override
    public String getName() {
        return reg.getName();
    }

    @Override
    public String getClassName() {
        return reg.getClassName();
    }

    @Override
    public boolean setInitParameter(final String name, final String value) {
        return reg.setInitParameter(name, value);
    }

    @Override
    public Collection<String> getServletNameMappings() {
        return reg.getServletNameMappings();
    }

    @Override
    public void addMappingForUrlPatterns(final EnumSet<javax.servlet.DispatcherType> dispatcherTypes, final boolean isMatchAfter,
            final String... urlPatterns) {
        reg.addMappingForUrlPatterns(wrap(dispatcherTypes), isMatchAfter, urlPatterns);
    }

    @Override
    public String getInitParameter(final String name) {
        return reg.getInitParameter(name);
    }

    @Override
    public Set<String> setInitParameters(final Map<String, String> initParameters) {
        return reg.setInitParameters(initParameters);
    }

    @Override
    public Collection<String> getUrlPatternMappings() {
        return reg.getUrlPatternMappings();
    }

    @Override
    public Map<String, String> getInitParameters() {
        return reg.getInitParameters();
    }
}

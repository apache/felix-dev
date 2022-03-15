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
public class FilterRegistrationWrapper implements FilterRegistration {

    private final javax.servlet.FilterRegistration reg;

    /**
     * Create new wrapper
     * @param c Wrapped registration
     */
    public FilterRegistrationWrapper(@NotNull final javax.servlet.FilterRegistration c) {
        this.reg = c;
    }

    private EnumSet<javax.servlet.DispatcherType> wrap(final EnumSet<DispatcherType> dispatcherTypes) {
        final EnumSet<javax.servlet.DispatcherType> set = EnumSet.noneOf(javax.servlet.DispatcherType.class);
        if ( dispatcherTypes.contains(DispatcherType.ASYNC)) {
            set.add(javax.servlet.DispatcherType.ASYNC);
        }
        if ( dispatcherTypes.contains(DispatcherType.ERROR)) {
            set.add(javax.servlet.DispatcherType.ERROR);
        }
        if ( dispatcherTypes.contains(DispatcherType.FORWARD)) {
            set.add(javax.servlet.DispatcherType.FORWARD);
        }
        if ( dispatcherTypes.contains(DispatcherType.INCLUDE)) {
            set.add(javax.servlet.DispatcherType.INCLUDE);
        }
        if ( dispatcherTypes.contains(DispatcherType.REQUEST)) {
            set.add(javax.servlet.DispatcherType.REQUEST);
        }
        return set;
    }

    @Override
    public void addMappingForServletNames(final EnumSet<DispatcherType> dispatcherTypes, final boolean isMatchAfter,
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
    public void addMappingForUrlPatterns(final EnumSet<DispatcherType> dispatcherTypes, final boolean isMatchAfter,
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

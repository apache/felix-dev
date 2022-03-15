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
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.ServletRegistration;

/**
 * servlet registration wrapper
 */
public class ServletRegistrationWrapper implements ServletRegistration {

    private final javax.servlet.ServletRegistration reg;

    /**
     * Create new wrapper
     * @param c Wrapped registration
     */
    public ServletRegistrationWrapper(@NotNull final javax.servlet.ServletRegistration c) {
        this.reg = c;
    }

    @Override
    public Set<String> addMapping(final String... urlPatterns) {
        return reg.addMapping(urlPatterns);
    }

    @Override
    public String getName() {
        return reg.getName();
    }

    @Override
    public Collection<String> getMappings() {
        return reg.getMappings();
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
    public String getRunAsRole() {
        return reg.getRunAsRole();
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
    public Map<String, String> getInitParameters() {
        return reg.getInitParameters();
    }
}

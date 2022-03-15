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
import java.net.URL;
import java.util.Set;

import org.apache.felix.http.base.internal.javaxwrappers.ServletRequestWrapper;
import org.apache.felix.http.base.internal.javaxwrappers.ServletResponseWrapper;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.servlet.whiteboard.ServletContextHelper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ServletContextHelper wrapper
 */
public class ServletContextHelperWrapper extends ServletContextHelper {

    private final org.osgi.service.http.context.ServletContextHelper helper;

    /**
     * Create new wrapper
     * @param helper Helper
     */
    public ServletContextHelperWrapper(@NotNull final org.osgi.service.http.context.ServletContextHelper helper) {
        this.helper = helper;
    }

    @Override
    public boolean handleSecurity(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        return helper.handleSecurity((javax.servlet.http.HttpServletRequest)ServletRequestWrapper.getWrapper(request),
                (javax.servlet.http.HttpServletResponse)ServletResponseWrapper.getWrapper(response));
    }

    @Override
    public void finishSecurity(final HttpServletRequest request, final HttpServletResponse response) {
        helper.finishSecurity((javax.servlet.http.HttpServletRequest)ServletRequestWrapper.getWrapper(request),
                (javax.servlet.http.HttpServletResponse)ServletResponseWrapper.getWrapper(response));
    }

    @Override
    public URL getResource(final String name) {
        return helper.getResource(name);
    }

    @Override
    public String getMimeType(final String name) {
        return helper.getMimeType(name);
    }

    @Override
    public Set<String> getResourcePaths(final String path) {
        return helper.getResourcePaths(path);
    }

    @Override
    public String getRealPath(final String path) {
        return helper.getRealPath(path);
    }

    /**
     * Get the helper
     * @return The helper
     */
    public @NotNull org.osgi.service.http.context.ServletContextHelper getHelper() {
        return this.helper;
    }
}

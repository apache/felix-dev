/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.http.base.internal.runtime;

import org.osgi.framework.BundleContext;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;
import org.osgi.service.servlet.context.ServletContextHelper;

/**
 * Info for the default context
 */
public class DefaultServletContextHelperInfo extends ServletContextHelperInfo {

    public DefaultServletContextHelperInfo() {
        super(Integer.MIN_VALUE, Integer.MIN_VALUE, HttpWhiteboardConstants.HTTP_WHITEBOARD_DEFAULT_CONTEXT_NAME, "/", null);
    }

    @Override
    public ServletContextHelper getService(final BundleContext bundleContext) {
        return new ServletContextHelper(bundleContext.getBundle()) {
            // nothing to override
        };
}

    @Override
    public void ungetService(final BundleContext bundleContext, final ServletContextHelper service) {
        // nothing to do
    }
}

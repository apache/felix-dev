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
package org.apache.felix.http.base.internal.whiteboard.tracker;

import org.apache.felix.http.base.internal.jakartawrappers.ServletWrapper;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.apache.felix.http.base.internal.runtime.WhiteboardServiceInfo;
import org.apache.felix.http.base.internal.util.ServiceUtils;
import org.apache.felix.http.base.internal.whiteboard.WhiteboardManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.Servlet;


/**
 * Tracker for http whiteboard servlets
 */
public final class JavaxServletTracker extends WhiteboardServiceTracker<Servlet> {

    /**
     * Create new tracker
     * @param context bundle context
     * @param manager whiteboard manager
     */
    public JavaxServletTracker(final BundleContext context, final WhiteboardManager manager) {
        super(manager, context, String.format("(&(objectClass=%s)(|(%s=*)(%s=*)(%s=*)))",
                javax.servlet.Servlet.class.getName(),
                HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME,
                HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN,
                HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_ERROR_PAGE));
    }

    @Override
    protected WhiteboardServiceInfo<Servlet> getServiceInfo(final ServiceReference<Servlet> ref) {
        return new JavaxServletInfo(ref);
    }

    /**
     * Servlet info for javax servlets
     */
    private static final class JavaxServletInfo extends ServletInfo {

        private final ServiceReference<javax.servlet.Servlet> reference;

        /**
         * Create new info
         * @param ref Reference to the servlet
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public JavaxServletInfo(final ServiceReference<Servlet> ref) {
            super(ref);
            this.reference = (ServiceReference)ref;
        }

        @Override
        public Servlet getService(final BundleContext bundleContext) {
            final javax.servlet.Servlet servlet = ServiceUtils.safeGetServiceObjects(bundleContext, this.reference);
            if ( servlet == null ) {
                return null;
            }
            return new ServletWrapper(servlet);
        }

        @Override
        public void ungetService(final BundleContext bundleContext, final Servlet service) {
            if ( service instanceof ServletWrapper ) {
                final javax.servlet.Servlet servlet = ((ServletWrapper)service).getServlet();
                ServiceUtils.safeUngetServiceObjects(bundleContext, this.reference, servlet);
            }
        }
    }
}

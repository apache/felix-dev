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

import org.apache.felix.http.base.internal.jakartawrappers.FilterWrapper;
import org.apache.felix.http.base.internal.runtime.FilterInfo;
import org.apache.felix.http.base.internal.runtime.WhiteboardServiceInfo;
import org.apache.felix.http.base.internal.util.ServiceUtils;
import org.apache.felix.http.base.internal.whiteboard.WhiteboardManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.Filter;


/**
 * Tracker for http whiteboard filters
 */
public final class JavaxFilterTracker extends WhiteboardServiceTracker<Filter> {

    /**
     * Create new tracker
     * @param context bundle context
     * @param manager whiteboard manager
     */
    public JavaxFilterTracker(final BundleContext context, final WhiteboardManager manager) {
        super(manager, context, String.format("(&(objectClass=%s)(|(%s=*)(%s=*)(%s=*)))",
                javax.servlet.Filter.class.getName(),
                HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_PATTERN,
                HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_REGEX,
                HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_SERVLET));
    }

    @Override
    protected WhiteboardServiceInfo<Filter> getServiceInfo(final ServiceReference<Filter> ref) {
        return new JavaxFilterInfo(ref);
    }

    /**
     * Filter info for javax filters
     */
    private static final class JavaxFilterInfo extends FilterInfo {

        private final ServiceReference<javax.servlet.Filter> reference;

        /**
         * Create new filter
         * @param ref Reference to the filter
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public JavaxFilterInfo(final ServiceReference<Filter> ref) {
            super(ref);
            this.reference = (ServiceReference)ref;
        }

        @Override
        public Filter getService(final BundleContext bundleContext) {
            final javax.servlet.Filter filter = ServiceUtils.safeGetServiceObjects(bundleContext, this.reference);
            if ( filter == null ) {
                return null;
            }
            return new FilterWrapper(filter);
        }

        @Override
        public void ungetService(final BundleContext bundleContext, final Filter service) {
            if ( service instanceof FilterWrapper ) {
                final javax.servlet.Filter filter = ((FilterWrapper)service).getFilter();
                ServiceUtils.safeUngetServiceObjects(bundleContext, this.reference, filter);
            }
        }
    }
}

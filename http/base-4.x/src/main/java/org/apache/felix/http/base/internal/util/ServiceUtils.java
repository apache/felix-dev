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
package org.apache.felix.http.base.internal.util;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;

/**
 * Utility methods to get/unget services, ignoring exceptions that might occur
 * on bundle stop/update
 */
public abstract class ServiceUtils {

    /**
     * Get the service
     * @return The service or {@code null}
     */
    public static <T> T safeGetService(final BundleContext ctx, final ServiceReference<T> ref) {
        try {
            return ctx.getService(ref);
        } catch ( final IllegalStateException | IllegalArgumentException | ServiceException ignore ) {
            // ignore this
        }
        return null;
    }

    /**
     * Unget the service
     */
    public static <T> void safeUngetService(final BundleContext ctx, final ServiceReference<T> ref) {
        try {
            ctx.ungetService(ref);
        } catch ( final IllegalStateException | IllegalArgumentException | ServiceException ignore ) {
            // ignore this
        }
    }

    /**
     * Get the service using {@code ServiceObjects}
     * @return The service or {@code null}
     */
    public static <T> T safeGetServiceObjects(final BundleContext ctx, final ServiceReference<T> ref) {
        if ( ctx != null ) {
            try {
                final ServiceObjects<T> so = ctx.getServiceObjects(ref);

                return so == null ? null : so.getService();
            } catch ( final IllegalStateException | IllegalArgumentException | ServiceException ignore ) {
                // ignore this
            }
        }
        return null;
    }

    /**
     * Unget the service using {@code ServiceObjects}
     */
    public static <T> void safeUngetServiceObjects(final BundleContext ctx, final ServiceReference<T> ref, final T service) {
        if ( ctx != null && service != null ) {
            try {
                final ServiceObjects<T> so = ctx.getServiceObjects(ref);

                if ( so != null ) {
                    so.ungetService(service);
                }
            } catch ( final IllegalStateException | IllegalArgumentException | ServiceException ignore ) {
                // ignore this
            }
        }
    }
}
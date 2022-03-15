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

import org.apache.felix.http.base.internal.jakartawrappers.PreprocessorWrapper;
import org.apache.felix.http.base.internal.runtime.PreprocessorInfo;
import org.apache.felix.http.base.internal.runtime.WhiteboardServiceInfo;
import org.apache.felix.http.base.internal.util.ServiceUtils;
import org.apache.felix.http.base.internal.whiteboard.WhiteboardManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.servlet.whiteboard.Preprocessor;

public final class JavaxPreprocessorTracker extends WhiteboardServiceTracker<Preprocessor>
{

   /**
     * Create a new tracker
     * @param bundleContext The bundle context.
     * @param contextManager The context manager
     */
    public JavaxPreprocessorTracker(final BundleContext bundleContext, final WhiteboardManager contextManager)
    {
        super(contextManager, bundleContext,
        		String.format("(objectClass=%s)", org.osgi.service.http.whiteboard.Preprocessor.class.getName()));
    }

    @Override
    protected WhiteboardServiceInfo<Preprocessor> getServiceInfo(final ServiceReference<Preprocessor> ref)
    {
        return new JavaxPreprocessorInfo(ref);
    }

    /**
     * Info for javax listeners
     */
    private static final class JavaxPreprocessorInfo extends PreprocessorInfo {

        private final ServiceReference<org.osgi.service.http.whiteboard.Preprocessor> reference;

        /**
         * Create new info
         * @param ref Reference to the preprocessor
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public JavaxPreprocessorInfo(final ServiceReference<Preprocessor> ref) {
            super(ref);
            this.reference = (ServiceReference)ref;
        }

        @Override
        public Preprocessor getService(final BundleContext bundleContext) {
            final org.osgi.service.http.whiteboard.Preprocessor p = ServiceUtils.safeGetServiceObjects(bundleContext, this.reference);
            if ( p == null ) {
                return null;
            }
            return new PreprocessorWrapper(p);
        }

        @Override
        public void ungetService(final BundleContext bundleContext, final Preprocessor service) {
            if ( service instanceof PreprocessorWrapper ) {
                final org.osgi.service.http.whiteboard.Preprocessor p = ((PreprocessorWrapper)service).getPreprocessor();
                ServiceUtils.safeUngetServiceObjects(bundleContext, this.reference, p);
            }
        }
    }
}

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
package org.apache.felix.webconsole.plugins.ds.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Activator is the main starting class.
 */
public class Activator 
    implements BundleActivator, ServiceTrackerCustomizer<ServiceComponentRuntime, ServiceRegistrations> {

    private volatile ServiceTracker<ServiceComponentRuntime, ServiceRegistrations> tracker;

    private volatile BundleContext bundleContext;

    @Override
    public final void start(final BundleContext context) throws Exception {
        this.bundleContext = context;
        this.tracker = new ServiceTracker<>(context, ServiceComponentRuntime.class, this);
        this.tracker.open();
    }

    @Override
    public final void stop(final BundleContext context) throws Exception {
        if (tracker != null) {
            tracker.close();
            tracker = null;
        }
        this.bundleContext = null;
    }

    // - begin tracker
    @Override
    public final void modifiedService(final ServiceReference<ServiceComponentRuntime> reference, final ServiceRegistrations service) {
        // nothing to do
    }

    @Override
    public final ServiceRegistrations addingService(final ServiceReference<ServiceComponentRuntime> reference) {
        final ServiceComponentRuntime service = this.bundleContext.getService(reference);
        if (service != null) {
            return new ServiceRegistrations(this.bundleContext, service);
        }
        return null;
    }

    @Override
    public final void removedService(final ServiceReference<ServiceComponentRuntime> reference, final ServiceRegistrations service) {
        service.destroy();
    }
}

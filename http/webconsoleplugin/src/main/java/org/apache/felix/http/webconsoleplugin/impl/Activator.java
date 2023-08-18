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
package org.apache.felix.http.webconsoleplugin.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import jakarta.servlet.Servlet;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.whiteboard.annotations.RequireHttpWhiteboard;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
@RequireHttpWhiteboard
public class Activator implements BundleActivator {

    private volatile ServiceTracker<HttpServiceRuntime, HttpServiceRuntime> tracker;

    private volatile ServiceRegistration<Servlet> serviceReg;

    private void register(final BundleContext context, final HttpServiceRuntime runtime) {
        final HttpServicePlugin plugin = new HttpServicePlugin(context, runtime);
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put("felix.webconsole.label", "httpservice");
        props.put("felix.webconsole.title", "HTTP Service");
        this.serviceReg = context.registerService(Servlet.class, plugin, props);
    }

    @Override
    public void start(final BundleContext context) throws Exception {
        final ServiceTracker<HttpServiceRuntime, HttpServiceRuntime> tracker = new ServiceTracker<>(context, HttpServiceRuntime.class, new ServiceTrackerCustomizer<HttpServiceRuntime,HttpServiceRuntime>() {

            @Override
            public HttpServiceRuntime addingService(final ServiceReference<HttpServiceRuntime> reference) {
                final HttpServiceRuntime runtime = context.getService(reference);
                if ( runtime != null ) {
                    register(context, runtime);
                }
                return runtime;
            }

            @Override
            public void modifiedService(final ServiceReference<HttpServiceRuntime> reference, final HttpServiceRuntime service) {
                // ignore
            }

            @Override
            public void removedService(final ServiceReference<HttpServiceRuntime> reference, final HttpServiceRuntime service) {
                if ( serviceReg != null ) {
                    try {
                        serviceReg.unregister();
                    } catch ( final IllegalStateException ignore) {
                        // ignore
                    }
                    serviceReg = null;
                }
            }
            
        });
        tracker.open();        
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        if ( tracker != null ) {
            tracker.close();
            tracker = null;
        }
    }
}

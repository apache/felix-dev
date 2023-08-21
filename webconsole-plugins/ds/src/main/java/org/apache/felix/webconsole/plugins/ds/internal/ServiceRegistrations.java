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

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.apache.felix.inventory.Format;
import org.apache.felix.inventory.InventoryPrinter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.runtime.ServiceComponentRuntime;

public class ServiceRegistrations {

    private final List<ServiceRegistration<?>> registrations = new ArrayList<>();

    private final List<Closeable> closeables = new ArrayList<>();

    public ServiceRegistrations(final BundleContext bundleContext, final ServiceComponentRuntime runtime) {
        final WebConsolePlugin plugin = new WebConsolePlugin(bundleContext, runtime);
        this.closeables.add(plugin);
        this.registrations.add(plugin.register(bundleContext));

        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        final String name = "Declarative Services Components";
        props.put(InventoryPrinter.NAME, "scr"); //$NON-NLS-1$
        props.put(InventoryPrinter.TITLE, name);
        props.put(InventoryPrinter.FORMAT, new String[] {
            Format.TEXT.toString(),
            Format.JSON.toString()
        });
        this.registrations.add(bundleContext.registerService(InventoryPrinter.class,
                new ComponentConfigurationPrinter(runtime, (WebConsolePlugin) plugin),
                props));

        this.registrations.add(new InfoProvider(bundleContext.getBundle(), runtime).register(bundleContext));
    }

    public void destroy() {
        for(final ServiceRegistration<?> reg : this.registrations) {
            try {
                reg.unregister();
            } catch (final IllegalStateException e) {
                // ignore - bundle is already stopped
            }
        }
        this.registrations.clear();
        for(final Closeable c : this.closeables) {
            try {
                c.close();
            } catch (final Exception e) {
                // ignore
            }
        }
        this.closeables.clear();
    }
}

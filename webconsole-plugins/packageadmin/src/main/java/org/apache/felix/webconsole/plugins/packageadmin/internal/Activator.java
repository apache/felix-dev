/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.webconsole.plugins.packageadmin.internal;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.inventory.Format;
import org.apache.felix.inventory.InventoryPrinter;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.packageadmin.PackageAdmin;

import jakarta.servlet.Servlet;

/**
 * This is the main starting class of the bundle.
 */
@SuppressWarnings("deprecation")
public class Activator implements BundleActivator {

    private ServiceRegistration<Servlet> plugin;
    private ServiceRegistration<InventoryPrinter> printerReg;

    /**
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    public void start(final BundleContext context) throws Exception {
        // package admin is always available
        final ServiceReference<PackageAdmin> packageAdmin = context.getServiceReference(PackageAdmin.class);
        if (packageAdmin != null ) {
            final PackageAdmin pa = context.getService(packageAdmin);
            if (pa != null) {
                // register configuration printer
                final Dictionary<String, Object> props = new Hashtable<String, Object>();
                props.put(InventoryPrinter.NAME, "duplicate_exports"); //$NON-NLS-1$
                props.put(InventoryPrinter.TITLE, "Duplicate Exports"); //$NON-NLS-1$
                props.put(InventoryPrinter.FORMAT, new String[] { Format.TEXT.toString() });

                this.printerReg = context.registerService(
                    InventoryPrinter.class,
                    new WebConsolePrinter(context, pa), props);
                this.plugin = new WebConsolePlugin(context, pa).register();

            }
        }
    }

    /**
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public void stop(final BundleContext context) throws Exception {
        if (printerReg != null) {
            printerReg.unregister();
            printerReg = null;
        }
        if (this.plugin != null) {
            this.plugin.unregister();
            this.plugin = null;
        }
    }
}

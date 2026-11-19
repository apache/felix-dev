/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.felix.logback.internal;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.slf4j.LoggerFactory;

/**
 * Starts and stops the services that make Logback the common backend for JUL
 * and the OSGi Log Service.
 */
@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator implements BundleActivator {

    private final JULBridge julBridge = new JULBridge();
    private volatile OSGiLogBridge logBridge;

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        LoggerFactory.getILoggerFactory();
        julBridge.install();

        try {
            logBridge = new OSGiLogBridge(bundleContext);

            logBridge.open();
        }
        catch (Exception | Error e) {
            closeTracker(e);
            julBridge.restore(e);
            throw e;
        }
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        try {
            closeTracker();
        }
        finally {
            julBridge.restore();
        }
    }

    private void closeTracker(Throwable failure) {
        try {
            closeTracker();
        }
        catch (RuntimeException | Error e) {
            failure.addSuppressed(e);
        }
    }

    private void closeTracker() {
        OSGiLogBridge bridge = logBridge;
        logBridge = null;

        if (bridge != null) {
            bridge.close();
        }
    }

}

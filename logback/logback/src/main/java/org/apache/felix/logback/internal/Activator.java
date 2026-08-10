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

import java.util.AbstractMap.SimpleEntry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.admin.LoggerAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.LoggerFactory;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator implements BundleActivator {

    private final JULBridge julBridge = new JULBridge();
    private volatile LoggerAdminServiceTracker lat;

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        LoggerFactory.getILoggerFactory();
        julBridge.install();

        try {
            lat = new LoggerAdminServiceTracker(bundleContext);

            lat.open();
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
        LoggerAdminServiceTracker tracker = lat;
        lat = null;

        if (tracker != null) {
            tracker.close();
        }
    }

    private static <R> R tccl(Supplier<R> action) {
        Thread currentThread = Thread.currentThread();
        ClassLoader original = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(Activator.class.getClassLoader());
            return action.get();
        }
        finally {
            currentThread.setContextClassLoader(original);
        }
    }

    static class LoggerAdminServiceTracker extends ServiceTracker<LoggerAdmin, LRST> {

        LoggerAdminServiceTracker(BundleContext context) {
            this(context, LRST::new);
        }

        LoggerAdminServiceTracker(
            BundleContext context,
            BiFunction<BundleContext, LoggerAdmin, LRST> trackerFactory) {

            super(context, LoggerAdmin.class, null);
            this.trackerFactory = trackerFactory;
        }

        @Override
        public LRST addingService(ServiceReference<LoggerAdmin> reference) {
            return tccl(() -> {
                LoggerAdmin loggerAdmin = context.getService(reference);

                if (loggerAdmin == null) {
                    return null;
                }

                try {
                    LRST lrst = trackerFactory.apply(context, loggerAdmin);
                    lrst.open();
                    return lrst;
                }
                catch (RuntimeException | Error e) {
                    context.ungetService(reference);
                    throw e;
                }
            });
        }

        @Override
        public void removedService(
            ServiceReference<LoggerAdmin> reference, LRST lrst) {

            try {
                tccl(() -> {
                    lrst.close();
                    return null;
                });
            }
            finally {
                context.ungetService(reference);
            }
        }

        private final BiFunction<BundleContext, LoggerAdmin, LRST> trackerFactory;

    }

    static class LRST extends ServiceTracker<LogReaderService, Pair> {

        public LRST(BundleContext context, LoggerAdmin loggerAdmin) {
            this(context, loggerAdmin, LogbackLogListener::new);
        }

        LRST(
            BundleContext context,
            LoggerAdmin loggerAdmin,
            Function<LoggerAdmin, LogbackLogListener> listenerFactory) {

            super(context, LogReaderService.class, null);

            this.loggerAdmin = loggerAdmin;
            this.listenerFactory = listenerFactory;
        }

        @Override
        public Pair addingService(
            ServiceReference<LogReaderService> reference) {

            return tccl(() -> {
                LogReaderService logReaderService = context.getService(reference);

                if (logReaderService == null) {
                    return null;
                }

                LogbackLogListener logbackLogListener = null;

                try {
                    logbackLogListener = listenerFactory.apply(loggerAdmin);
                    logReaderService.addLogListener(logbackLogListener);

                    return new Pair(logReaderService, logbackLogListener);
                }
                catch (RuntimeException | Error e) {
                    if (logbackLogListener != null) {
                        try {
                            logReaderService.removeLogListener(logbackLogListener);
                        }
                        catch (RuntimeException | Error cleanup) {
                            e.addSuppressed(cleanup);
                        }
                    }

                    context.ungetService(reference);
                    throw e;
                }
            });
        }

        @Override
        public void removedService(
            ServiceReference<LogReaderService> reference,
            Pair pair) {

            try {
                tccl(() -> {
                    pair.getKey().removeLogListener(pair.getValue());
                    return null;
                });
            }
            finally {
                context.ungetService(reference);
            }
        }

        private final LoggerAdmin loggerAdmin;
        private final Function<LoggerAdmin, LogbackLogListener> listenerFactory;

    }

    static class Pair extends SimpleEntry<LogReaderService, LogbackLogListener> {

        private static final long serialVersionUID = 1L;

        public Pair(LogReaderService logReaderService, LogbackLogListener logbackLogListener) {
            super(logReaderService, logbackLogListener);
        }

    }

}

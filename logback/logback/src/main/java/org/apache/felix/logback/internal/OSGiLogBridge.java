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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.admin.LoggerAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Maintains the active connection between Felix Logback and an available OSGi
 * Log Service provider.
 */
final class OSGiLogBridge implements AutoCloseable {

    OSGiLogBridge(BundleContext context) {
        this(context, LogbackLogListener::new);
    }

    OSGiLogBridge(
        BundleContext context,
        Function<LoggerAdmin, LogbackLogListener> listenerFactory) {

        this.listenerFactory = listenerFactory;
        loggerAdminTracker = new RankedServiceTracker<>(
            context, LoggerAdmin.class, new ServiceChangeHandler<LoggerAdmin>() {

                @Override
                public void added(
                    ServiceReference<LoggerAdmin> reference,
                    LoggerAdmin service) {

                    addLoggerAdmin(reference, service);
                }

                @Override
                public void modified(
                    ServiceReference<LoggerAdmin> reference,
                    LoggerAdmin service) {

                    rebind();
                }

                @Override
                public void removed(
                    ServiceReference<LoggerAdmin> reference,
                    LoggerAdmin service) {

                    removeLoggerAdmin(reference);
                }
            });
        logReaderTracker = new RankedServiceTracker<>(
            context, LogReaderService.class,
            new ServiceChangeHandler<LogReaderService>() {

                @Override
                public void added(
                    ServiceReference<LogReaderService> reference,
                    LogReaderService service) {

                    addLogReader(reference, service);
                }

                @Override
                public void modified(
                    ServiceReference<LogReaderService> reference,
                    LogReaderService service) {

                    rebind();
                }

                @Override
                public void removed(
                    ServiceReference<LogReaderService> reference,
                    LogReaderService service) {

                    removeLogReader(reference);
                }
            });
    }

    void open() {
        try {
            loggerAdminTracker.open();
            logReaderTracker.open();
        }
        catch (RuntimeException | Error e) {
            try {
                close();
            }
            catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    @Override
    public void close() {
        Binding current;

        synchronized (this) {
            closing = true;
            current = binding;
            binding = null;
            selectedLoggerAdmin = null;
            selectedLogReader = null;
        }

        Runnable closeCurrent = current == null ? null : () -> tccl(() -> {
            current.close();
            return null;
        });
        Throwable failure = runClose(closeCurrent, null);
        failure = runClose(logReaderTracker::close, failure);
        failure = runClose(loggerAdminTracker::close, failure);

        synchronized (this) {
            loggerAdmins.clear();
            logReaders.clear();
        }

        if (failure instanceof RuntimeException) {
            throw (RuntimeException)failure;
        }
        if (failure instanceof Error) {
            throw (Error)failure;
        }
    }

    private synchronized void addLoggerAdmin(
        ServiceReference<LoggerAdmin> reference, LoggerAdmin service) {

        loggerAdmins.put(reference, service);
        try {
            rebind();
        }
        catch (RuntimeException | Error e) {
            loggerAdmins.remove(reference);
            rebindAfterFailure(e);
            throw e;
        }
    }

    private synchronized void removeLoggerAdmin(
        ServiceReference<LoggerAdmin> reference) {

        loggerAdmins.remove(reference);
        rebind();
    }

    private synchronized void addLogReader(
        ServiceReference<LogReaderService> reference, LogReaderService service) {

        logReaders.put(reference, service);
        try {
            rebind();
        }
        catch (RuntimeException | Error e) {
            logReaders.remove(reference);
            rebindAfterFailure(e);
            throw e;
        }
    }

    private synchronized void removeLogReader(
        ServiceReference<LogReaderService> reference) {

        logReaders.remove(reference);
        rebind();
    }

    private void rebindAfterFailure(Throwable failure) {
        try {
            rebind();
        }
        catch (RuntimeException | Error cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private synchronized void rebind() {
        if (closing) {
            return;
        }

        ServiceReference<LogReaderService> logReaderReference =
            selectLogReader();
        ServiceReference<LoggerAdmin> loggerAdminReference =
            selectLoggerAdmin(logReaderReference);

        if (Objects.equals(selectedLoggerAdmin, loggerAdminReference) &&
            Objects.equals(selectedLogReader, logReaderReference)) {

            return;
        }

        Binding current = binding;
        binding = null;
        selectedLoggerAdmin = null;
        selectedLogReader = null;

        if (current != null) {
            current.close();
        }

        if (loggerAdminReference != null && logReaderReference != null) {
            binding = createBinding(
                loggerAdmins.get(loggerAdminReference),
                logReaders.get(logReaderReference));
            selectedLoggerAdmin = loggerAdminReference;
            selectedLogReader = logReaderReference;
        }
    }

    private Binding createBinding(
        LoggerAdmin loggerAdmin, LogReaderService logReaderService) {

        LogbackLogListener listener = null;

        try {
            listener = Objects.requireNonNull(listenerFactory.apply(loggerAdmin));
            logReaderService.addLogListener(listener);
            return new Binding(logReaderService, listener);
        }
        catch (RuntimeException | Error e) {
            if (listener != null) {
                try {
                    logReaderService.removeLogListener(listener);
                }
                catch (RuntimeException | Error cleanup) {
                    e.addSuppressed(cleanup);
                }
                addSuppressedClose(listener, e);
            }
            throw e;
        }
    }

    private ServiceReference<LogReaderService> selectLogReader() {
        return highestRanked(logReaders, reference -> {
            Bundle provider = reference.getBundle();

            return provider != null && highestRanked(
                loggerAdmins,
                adminReference -> provider.equals(adminReference.getBundle())) != null;
        });
    }

    private ServiceReference<LoggerAdmin> selectLoggerAdmin(
        ServiceReference<LogReaderService> logReaderReference) {

        if (logReaderReference == null) {
            return null;
        }

        Bundle provider = logReaderReference.getBundle();

        if (provider == null) {
            return null;
        }

        return highestRanked(
            loggerAdmins,
            reference -> provider.equals(reference.getBundle()));
    }

    private static <S> ServiceReference<S> highestRanked(
        Map<ServiceReference<S>, S> services,
        Predicate<ServiceReference<S>> selector) {

        return services.keySet().stream().filter(selector).max(
            ServiceReference::compareTo).orElse(null);
    }

    private static Throwable runClose(Runnable action, Throwable failure) {
        if (action == null) {
            return failure;
        }

        try {
            action.run();
        }
        catch (RuntimeException | Error e) {
            if (failure == null) {
                return e;
            }
            failure.addSuppressed(e);
        }

        return failure;
    }

    private static void addSuppressedClose(
        AutoCloseable closeable, Throwable failure) {
        try {
            closeable.close();
        }
        catch (RuntimeException | Error cleanup) {
            failure.addSuppressed(cleanup);
        }
        catch (Exception cleanup) {
            failure.addSuppressed(cleanup);
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

    /**
     * Represents an active OSGi-to-Logback connection whose resources share a
     * common lifetime.
     */
    private final class Binding implements AutoCloseable {

        private Binding(
            LogReaderService logReaderService, LogbackLogListener listener) {

            this.logReaderService = logReaderService;
            this.listener = listener;
        }

        @Override
        public void close() {
            try {
                logReaderService.removeLogListener(listener);
            }
            catch (RuntimeException | Error e) {
                OSGiLogBridge.addSuppressedClose(listener, e);
                throw e;
            }

            listener.close();
        }

        private final LogReaderService logReaderService;
        private final LogbackLogListener listener;

    }

    /**
     * Keeps the bridge's candidate set synchronized with the services
     * available from OSGi.
     *
     * @param <S> the tracked service type
     */
    static final class RankedServiceTracker<S>
        extends ServiceTracker<S, S> {

        private RankedServiceTracker(
            BundleContext context,
            Class<S> serviceClass,
            ServiceChangeHandler<S> changeHandler) {

            super(context, serviceClass, null);
            this.changeHandler = changeHandler;
        }

        @Override
        public S addingService(ServiceReference<S> reference) {
            return tccl(() -> {
                S service = super.addingService(reference);

                if (service == null) {
                    return null;
                }

                try {
                    changeHandler.added(reference, service);
                    return service;
                }
                catch (RuntimeException | Error e) {
                    super.removedService(reference, service);
                    throw e;
                }
            });
        }

        @Override
        public void modifiedService(ServiceReference<S> reference, S service) {
            tccl(() -> {
                changeHandler.modified(reference, service);
                return null;
            });
        }

        @Override
        public void removedService(ServiceReference<S> reference, S service) {
            try {
                tccl(() -> {
                    changeHandler.removed(reference, service);
                    return null;
                });
            }
            finally {
                super.removedService(reference, service);
            }
        }

        private final ServiceChangeHandler<S> changeHandler;

    }

    /**
     * Receives service lifecycle changes that may require the bridge to select
     * a new provider.
     *
     * @param <S> the changing service type
     */
    private interface ServiceChangeHandler<S> {

        void added(ServiceReference<S> reference, S service);

        void modified(ServiceReference<S> reference, S service);

        void removed(ServiceReference<S> reference, S service);

    }

    final RankedServiceTracker<LoggerAdmin> loggerAdminTracker;
    final RankedServiceTracker<LogReaderService> logReaderTracker;
    private final Function<LoggerAdmin, LogbackLogListener> listenerFactory;
    private final Map<ServiceReference<LoggerAdmin>, LoggerAdmin> loggerAdmins =
        new HashMap<>();
    private final Map<ServiceReference<LogReaderService>, LogReaderService> logReaders =
        new HashMap<>();
    private ServiceReference<LoggerAdmin> selectedLoggerAdmin;
    private ServiceReference<LogReaderService> selectedLogReader;
    private Binding binding;
    private boolean closing;

}

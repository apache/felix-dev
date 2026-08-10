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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.Bundle;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.admin.LoggerAdmin;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.CallerData;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;

public class LogbackLogListener implements AutoCloseable, LogListener, LoggerContextListener {

    private static final String EVENTS_BUNDLE = "Events.Bundle";
    private static final String EVENTS_FRAMEWORK = "Events.Framework";
    private static final String EVENTS_SERVICE = "Events.Service";
    private static final String LOG_SERVICE = "LogService";

    volatile LoggerContext loggerContext;
    volatile Logger rootLogger;
    volatile LoggerContextVO loggerContextVO;
    final org.osgi.service.log.admin.LoggerContext osgiLoggerContext;
    final AtomicBoolean closed = new AtomicBoolean();
    final Map<String, Optional<LogLevel>> originalLogLevels = new HashMap<>();
    final Map<String, Optional<LogLevel>> appliedLogLevels = new HashMap<>();

    public LogbackLogListener(LoggerAdmin loggerAdmin) {
        this(loggerAdmin, getLoggerContext());
    }

    LogbackLogListener(LoggerAdmin loggerAdmin, LoggerContext loggerContext) {
        osgiLoggerContext = loggerAdmin.getLoggerContext(null);
        this.loggerContext = loggerContext;

        try {
            onStart(loggerContext);
            loggerContext.addListener(this);
        }
        catch (RuntimeException | Error e) {
            try {
                restoreLogLevels();
            }
            catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    private static LoggerContext getLoggerContext() {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();

        if (loggerFactory instanceof LoggerContext) {
            return (LoggerContext)loggerFactory;
        }

        throw new IllegalStateException("This bundle only works with logback-classic");
    }

    @Override
    public boolean isResetResistant() {
        return true;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            loggerContext.removeListener(this);
            restoreLogLevels();
        }
    }

    @Override
    public void logged(final LogEntry entry) {
        String loggerName = entry.getLoggerName();
        String message = entry.getMessage();
        Object[] arguments = null;
        Level level = from(entry.getLogLevel());
        final AtomicBoolean avoidCallerData = new AtomicBoolean();

        if (EVENTS_BUNDLE.equals(loggerName) ||
            EVENTS_FRAMEWORK.equals(loggerName) ||
            LOG_SERVICE.equals(loggerName)) {

            loggerName = formatBundle(entry.getBundle(), loggerName);
            avoidCallerData.set(true);
        }
        else if (loggerName.startsWith(EVENTS_BUNDLE) ||
                 loggerName.startsWith(EVENTS_FRAMEWORK) ||
                 loggerName.startsWith(LOG_SERVICE)) {

            avoidCallerData.set(true);
        }
        else if (EVENTS_SERVICE.equals(loggerName)) {
            loggerName = formatBundle(entry.getBundle(), loggerName);
            message = message + " {}";
            arguments = new Object[] {entry.getServiceReference()};
            avoidCallerData.set(true);
        }
        else if (loggerName.startsWith(EVENTS_SERVICE)) {
            message = message + " {}";
            arguments = new Object[] {entry.getServiceReference()};
            avoidCallerData.set(true);
        }

        Logger logger = loggerContext.getLogger(loggerName);

        // Check to see if there's a logger defined in our configuration and
        // if there is, then make sure it's handled as an override for the
        // effective level.
        if (!logger.isEnabledFor(level)) {
            return;
        }

        LoggingEvent le = new LoggingEvent() {

            @Override
            public StackTraceElement[] getCallerData() {
                if (avoidCallerData.get() || callerData != null)
                    return callerData;
                return callerData = getCallerData0(entry.getLocation());
            }

            private volatile StackTraceElement[] callerData;

        };

        le.setArgumentArray(arguments);
        le.setMessage(message);
        le.setLevel(level);
        le.setLoggerContextRemoteView(loggerContextVO);
        le.setLoggerName(loggerName);
        le.setThreadName(entry.getThreadInfo());
        le.setThrowableProxy(getThrowableProxy(entry.getException()));
        le.setTimeStamp(entry.getTime());
        le.setSequenceNumber(entry.getSequence());

        logger.callAppenders(le);
    }

    @Override
    public void onLevelChange(Logger logger, Level level) {
        if (closed.get()) {
            return;
        }

        Level configuredLevel = logger.getLevel();

        updateLogLevel(
            logger.getName(),
            configuredLevel == null ?
                Optional.empty() : Optional.of(from(configuredLevel)));
    }

    @Override
    public void onStart(LoggerContext context) {
        if (closed.get()) {
            return;
        }

        loggerContext = context;
        rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        loggerContextVO = loggerContext.getLoggerContextRemoteView();

        configureLogLevels(loggerContext);
    }

    @Override
    public void onStop(LoggerContext context) {
        if (!closed.get()) {
            restoreLogLevels();
        }
    }

    @Override
    public void onReset(LoggerContext context) {
        if (!closed.get()) {
            onStart(context);
        }
    }

    String formatBundle(Bundle bundle, String loggerName) {
        String symbolicName = bundle == null ? null : bundle.getSymbolicName();

        if (symbolicName == null) {
            return loggerName;
        }

        return new StringBuilder().append(
            loggerName
        ).append(
            "."
        ).append(
            symbolicName
        ).toString();
    }

    LogLevel from(Level level) {
        if (Level.ALL.equals(level) || Level.OFF.equals(level)) {
            return LogLevel.TRACE;
        }
        else if (Level.DEBUG.equals(level)) {
            return LogLevel.DEBUG;
        }
        else if (Level.ERROR.equals(level)) {
            return LogLevel.ERROR;
        }
        else if (Level.INFO.equals(level)) {
            return LogLevel.INFO;
        }
        else if (Level.TRACE.equals(level)) {
            return LogLevel.TRACE;
        }
        else if (Level.WARN.equals(level)) {
            return LogLevel.WARN;
        }

        return LogLevel.WARN;
    }

    Level from(LogLevel logLevel) {
        switch (logLevel) {
            case AUDIT:
                return Level.TRACE;
            case DEBUG:
                return Level.DEBUG;
            case ERROR:
                return Level.ERROR;
            case INFO:
                return Level.INFO;
            case TRACE:
                return Level.TRACE;
            case WARN:
            default:
                return Level.WARN;
        }
    }

    StackTraceElement[] getCallerData0(StackTraceElement stackTraceElement) {
        StackTraceElement[] callerData = CallerData.extract(
            new Throwable(),
            org.osgi.service.log.Logger.class.getName(),
            loggerContext.getMaxCallerDataDepth(),
            loggerContext.getFrameworkPackages());

        if (stackTraceElement != null) {
            if (callerData.length == 0) {
                callerData = new StackTraceElement[] {stackTraceElement};
            }
            else {
                StackTraceElement[] copy = new StackTraceElement[callerData.length + 1];
                copy[0] = stackTraceElement;
                System.arraycopy(callerData, 0, copy, 1, callerData.length);
                callerData = copy;
            }
        }

        return callerData;
    }

    ThrowableProxy getThrowableProxy(Throwable t) {
        if (t == null)
            return null;

        ThrowableProxy throwableProxy = new ThrowableProxy(t);

        if (loggerContext.isPackagingDataEnabled()) {
            throwableProxy.calculatePackagingData();
        }

        return throwableProxy;
    }

    Map<String, LogLevel> updateLevels(LoggerContext loggerContext, Map<String, LogLevel> levels) {
        Map<String, LogLevel> copy = new HashMap<String, LogLevel>(levels);

        Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        LogLevel rootLevel = from(root.getLevel());
        copy.put(org.osgi.service.log.Logger.ROOT_LOGGER_NAME, rootLevel);
        copy.put(EVENTS_BUNDLE, LogLevel.TRACE);
        copy.put(EVENTS_FRAMEWORK, LogLevel.TRACE);
        copy.put(EVENTS_SERVICE, LogLevel.TRACE);
        copy.put(LOG_SERVICE, LogLevel.TRACE);

        for (Logger logger : loggerContext.getLoggerList()) {
            String name = logger.getName();
            Level level = logger.getLevel();

            if (level != null) {
                copy.remove(name);
                copy.put(name, from(level));
            }
        }

        return copy;
    }

    private synchronized void updateLogLevel(
        String name, Optional<LogLevel> level) {

        Map<String, LogLevel> currentLevels = osgiLoggerContext.getLogLevels();
        Map<String, LogLevel> updatedLevels = new HashMap<>(currentLevels);
        setLevel(updatedLevels, name, level);
        Map<String, Optional<LogLevel>> nextOriginalLogLevels =
            new HashMap<>(originalLogLevels);
        Map<String, Optional<LogLevel>> nextAppliedLogLevels =
            new HashMap<>(appliedLogLevels);

        updateOwnership(currentLevels, updatedLevels,
            nextOriginalLogLevels, nextAppliedLogLevels);
        osgiLoggerContext.setLogLevels(updatedLevels);
        replaceOwnership(nextOriginalLogLevels, nextAppliedLogLevels);
    }

    private synchronized void configureLogLevels(LoggerContext context) {
        Map<String, LogLevel> currentLevels = osgiLoggerContext.getLogLevels();
        Map<String, LogLevel> releasedLevels = getReleasedLogLevels(currentLevels);
        Map<String, LogLevel> updatedLevels = updateLevels(context, releasedLevels);
        Map<String, Optional<LogLevel>> nextOriginalLogLevels = new HashMap<>();
        Map<String, Optional<LogLevel>> nextAppliedLogLevels = new HashMap<>();

        updateOwnership(releasedLevels, updatedLevels,
            nextOriginalLogLevels, nextAppliedLogLevels);
        osgiLoggerContext.setLogLevels(updatedLevels);
        replaceOwnership(nextOriginalLogLevels, nextAppliedLogLevels);
    }

    private static void updateOwnership(
        Map<String, LogLevel> currentLevels,
        Map<String, LogLevel> updatedLevels,
        Map<String, Optional<LogLevel>> originalLevels,
        Map<String, Optional<LogLevel>> appliedLevels) {

        Set<String> names = new HashSet<>(currentLevels.keySet());
        names.addAll(updatedLevels.keySet());
        names.addAll(appliedLevels.keySet());

        for (String name : names) {
            Optional<LogLevel> current = getLevel(currentLevels, name);
            Optional<LogLevel> updated = getLevel(updatedLevels, name);
            Optional<LogLevel> applied = appliedLevels.get(name);

            if (applied != null && !current.equals(applied)) {
                originalLevels.remove(name);
                appliedLevels.remove(name);
            }

            if (!current.equals(updated)) {
                originalLevels.putIfAbsent(name, current);
                appliedLevels.put(name, updated);
            }
        }
    }

    private Map<String, LogLevel> getReleasedLogLevels(
        Map<String, LogLevel> currentLevels) {

        Map<String, LogLevel> releasedLevels = new HashMap<>(currentLevels);

        for (Map.Entry<String, Optional<LogLevel>> entry : appliedLogLevels.entrySet()) {
            String name = entry.getKey();

            if (getLevel(currentLevels, name).equals(entry.getValue())) {
                setLevel(releasedLevels, name, originalLogLevels.get(name));
            }
        }

        return releasedLevels;
    }

    private synchronized void restoreLogLevels() {
        Map<String, LogLevel> currentLevels = osgiLoggerContext.getLogLevels();
        Map<String, LogLevel> restoredLevels = getReleasedLogLevels(currentLevels);

        if (!currentLevels.equals(restoredLevels)) {
            osgiLoggerContext.setLogLevels(restoredLevels);
        }

        originalLogLevels.clear();
        appliedLogLevels.clear();
    }

    private void replaceOwnership(
        Map<String, Optional<LogLevel>> originalLevels,
        Map<String, Optional<LogLevel>> appliedLevels) {

        originalLogLevels.clear();
        originalLogLevels.putAll(originalLevels);
        appliedLogLevels.clear();
        appliedLogLevels.putAll(appliedLevels);
    }

    private static Optional<LogLevel> getLevel(
        Map<String, LogLevel> levels, String name) {

        return Optional.ofNullable(levels.get(name));
    }

    private static void setLevel(
        Map<String, LogLevel> levels, String name, Optional<LogLevel> level) {

        if (level.isPresent()) {
            levels.put(name, level.get());
        }
        else {
            levels.remove(name);
        }
    }

}

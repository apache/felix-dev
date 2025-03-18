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
package org.apache.felix.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicLong;

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogService;

/**
 * Implementation of the OSGi {@link LogEntry} interface.  See section 101
 * of the OSGi service compendium.
 * <p>
 * Provides methods to access the information contained in an individual Log
 * Service log entry.
 * <p>
 * A LogEntry object may be acquired from the
 * {@link org.osgi.service.log.LogReaderService#getLog()} method or by
 * registering a {@link org.osgi.service.log.LogListener} object.
 * @see org.osgi.service.log.LogReaderService#getLog()
 * @see org.osgi.service.log.LogListener
 */
final class LogEntryImpl implements LogEntry
{
    /** The sequence number generator. */
    private static final AtomicLong m_sequenceGenerator = new AtomicLong();

    /** The name of the logger used to create the LogEntry. */
    private final String m_name;
    /** The bundle that created the LogEntry object. */
    private final Bundle m_bundle;
    /** The exception associated with this LogEntry object. */
    private final Throwable m_exception;
    /** The severity level of this LogEntry object. */
    private final LogLevel m_level;
    private final int m_legacyLevel;
    /** The message associated with this LogEntry object. */
    private final String m_message;
    /** The service reference associated with this LogEntry object. */
    private final ServiceReference<?> m_serviceReference;
    /** The system time in milliseconds when this LogEntry object was created. */
    private final long m_time;
    /** The sequence of the LogEntry. */
    private final long m_sequence;
    /** The information about the Thread which logged the message. */
    private final String m_threadInfo;
    /** The StackTraceElement where the message was originally logged. */
    private final StackTraceElement m_stackTraceElement;

    private volatile String _toString;

    /**
     * Create a new instance.
     * @param name the name of the logger used to create the LogEntry
     * @param bundle the bundle that created the LogEntry object
     * @param sr the service reference to associate with this LogEntry object
     * @param level the severity level for this LogEntry object
     * @param message the message to associate with this LogEntry object
     * @param exception the exception to associate with this LogEntry object
     */
    LogEntryImpl(
        final String name,
        final Bundle bundle,
        final ServiceReference<?> sr,
        final LogLevel level,
        final String message,
        final Throwable exception,
        final StackTraceElement stackTraceElement)
    {
        this.m_name = name;
        this.m_bundle = bundle;
        this.m_exception = LogException.getException(exception);
        this.m_level = level;
        this.m_legacyLevel = level.ordinal();
        this.m_message = message;
        this.m_serviceReference = sr;
        this.m_time = System.currentTimeMillis();
        this.m_sequence = m_sequenceGenerator.getAndIncrement();
        this.m_threadInfo = Thread.currentThread().getName();
        this.m_stackTraceElement = stackTraceElement;
    }

    @SuppressWarnings("deprecation")
    LogEntryImpl(
        final String name,
        final Bundle bundle,
        final ServiceReference<?> sr,
        final int legacyLevel,
        final String message,
        final Throwable exception,
        final StackTraceElement stackTraceElement)
    {
        this.m_name = name;
        this.m_bundle = bundle;
        this.m_exception = LogException.getException(exception);
        LogLevel level = LogLevel.TRACE;
        switch (legacyLevel) {
            case LogService.LOG_ERROR:
                level = LogLevel.ERROR;
                break;
            case LogService.LOG_WARNING:
                level = LogLevel.WARN;
                break;
            case LogService.LOG_INFO:
                level = LogLevel.INFO;
                break;
            case LogService.LOG_DEBUG:
                level = LogLevel.DEBUG;
        }
        this.m_level = level;
        this.m_legacyLevel = ((level == LogLevel.TRACE) ? legacyLevel : level.ordinal());
        this.m_message = message;
        this.m_serviceReference = sr;
        this.m_time = System.currentTimeMillis();
        this.m_sequence = m_sequenceGenerator.getAndIncrement();
        this.m_threadInfo = Thread.currentThread().getName();
        this.m_stackTraceElement = stackTraceElement;
    }

    /**
     * Returns the bundle that created this LogEntry object.
     * @return the bundle that created this LogEntry object;<code>null</code> if no
     * bundle is associated with this LogEntry object
     */
    @Override
    public Bundle getBundle()
    {
        return m_bundle;
    }

    /**
     * Returns the {@link ServiceReference} object for the service associated with
     * this LogEntry object.
     * @return the {@link ServiceReference} object for the service associated with
     * this LogEntry object; <code>null</code> if no {@link ServiceReference} object
     * was provided
     */
    @Override
    public ServiceReference<?> getServiceReference()
    {
        return m_serviceReference;
    }

    /**
     * Returns the severity level of this LogEntry object.
     * <p>
     * This is one of the severity levels defined by the
     * {@link org.osgi.service.logLogService} interface.
     * @return severity level of this LogEntry object.
     * @see org.osgi.service.LogService#LOG_ERROR
     * @see org.osgi.service.LogService#LOG_WARNING
     * @see org.osgi.service.LogService#LOG_INFO
     * @see org.osgi.service.LogService#LOG_DEBUG
     */
    @Override
    public int getLevel()
    {
        if (m_legacyLevel != m_level.ordinal()) {
            return m_legacyLevel;
        }

        return m_level.ordinal();
    }

    /**
     * Returns the human readable message associated with this LogEntry object.
     * @return a string containing the message associated with this LogEntry object
     */
    @Override
    public String getMessage()
    {
        return m_message;
    }

    /**
     * Returns the exception object associated with this LogEntry object.
     * <p>
     * The returned exception may not be the original exception.  To avoid
     * references to a bundle defined exception class, thus preventing an
     * uninstalled bundle from being garbage collected, this LogService will
     * return an exception object of an implementation defined
     * {@link Throwable} sub-class.
     * This exception will maintain as much information as possible from the
     * original exception object such as the message and stack trace.
     * @return throwable object of the exception associated with this LogEntry;
     * <code>null</code> if no exception is associated with this LogEntry object
     */
    @Override
    public Throwable getException()
    {
        return m_exception;
    }

    /**
     * Returns the value of {@link System#currentTimeMillis()} at the time this
     * LogEntry object was created.
     * @return the system time in milliseconds when this LogEntry object was created
     * @see System#currentTimeMillis()
     */
    @Override
    public long getTime()
    {
        return m_time;
    }

    @Override
    public LogLevel getLogLevel() {
        return m_level;
    }

    @Override
    public String getLoggerName() {
        return m_name;
    }

    @Override
    public long getSequence() {
        return m_sequence;
    }

    @Override
    public String getThreadInfo() {
        return m_threadInfo;
    }

    @Override
    public StackTraceElement getLocation() {
        return m_stackTraceElement;
    }

    @Override
    public String toString() {
        if (_toString == null) {
            _toString = m_time + "#" + m_sequence + " [" + m_threadInfo + "] " + m_level +
                " (" + m_legacyLevel + ") [" + m_bundle.getBundleId() + ":" + m_name + "] " +
                    m_stackTraceElement.getClassName() + ":" +
                    m_stackTraceElement.getLineNumber() + " > " + m_message +
                        (m_exception != null ? "\n" + exceptionString(m_exception) : "");
        }
        return _toString;
    }

    String exceptionString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

}
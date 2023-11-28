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
package org.apache.felix.eventadmin.impl.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

/**
 * This class mimics the standard OSGi {@code LogService} interface. An
 * instance of this class will be used by the EventAdmin for all logging. The
 * implementation of this class sends log messages to standard output, if no
 * {@code LogService} is present; it uses a log service if one is
 * installed in the framework. To do that without creating a hard dependency on the
 * package it uses fully qualified class names and registers a listener with the
 * framework hence, it does not need access to the {@code LogService} class but will
 * use it if the listener is informed about an available service. By using a
 * DynamicImport-Package dependency we don't need the package but
 * use it if present. Additionally, all log methods prefix the log message with
 * {@code EventAdmin: }.
 *
 * There is one difference in behavior from the standard OSGi LogService.
 * This logger has a {@link #m_logLevel} property which decides what messages
 * get logged.
 *
 * @see org.osgi.service.log.LogService
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
**/
// TODO: At the moment we log a message to all currently available LogServices.
//       Maybe, we should only log to the one with the highest ranking instead?
//       What is the best practice in this case?
public class LogWrapper
{
    /**
     * ERROR LEVEL
     *
     * @see org.osgi.service.log.LogService#LOG_ERROR
     */
    public static final int LOG_ERROR = 1;

    /**
     * WARNING LEVEL
     *
     * @see org.osgi.service.log.LogService#LOG_WARNING
     */
    public static final int LOG_WARNING = 2;

    /**
     * INFO LEVEL
     *
     * @see org.osgi.service.log.LogService#LOG_INFO
     */
    public static final int LOG_INFO = 3;

    /**
     * DEBUG LEVEL
     *
     * @see org.osgi.service.log.LogService#LOG_DEBUG
     */
    public static final int LOG_DEBUG = 4;

    // A set containing the currently available LogServices. Furthermore used as lock
    private final Set<ServiceReference<?>> m_loggerRefs = new HashSet<>();

    // Only null while not set and m_loggerRefs is empty hence, only needs to be
    // checked in case m_loggerRefs is empty otherwise it will not be null.
    private BundleContext m_context;

    private ServiceListener m_logServiceListener;

    /**
     * Current log level. Message with log level less than or equal to
     * current log level will be logged.
     * The default value is {@link #LOG_WARNING}
     *
     * @see #setLogLevel(int)
     */
    private int m_logLevel = LOG_WARNING;
    /*
     * A thread save variant of the double checked locking singleton.
     */
    private static class LogWrapperLoader
    {
        static final LogWrapper m_singleton = new LogWrapper();
    }

    /**
     * Returns the singleton instance of this LogWrapper that can be used to send
     * log messages to all currently available LogServices or to standard output,
     * respectively.
     *
     * @return the singleton instance of this LogWrapper.
     */
    public static LogWrapper getLogger()
    {
        return LogWrapperLoader.m_singleton;
    }

    /**
     * Set the {@code BundleContext} of the bundle. This method registers a service
     * listener for LogServices with the framework that are subsequently used to
     * log messages.
     * <p>
     * If the bundle context is <code>null</code>, the service listener is
     * unregistered and all remaining references to LogServices dropped before
     * internally clearing the bundle context field.
     *
     *  @param context The context of the bundle.
     */
    public static void setContext( final BundleContext context )
    {
        LogWrapper logWrapper = LogWrapperLoader.m_singleton;

        // context is removed, unregister and drop references
        if ( context == null )
        {
            if ( logWrapper.m_logServiceListener != null )
            {
                logWrapper.m_context.removeServiceListener( logWrapper.m_logServiceListener );
                logWrapper.m_logServiceListener = null;
            }
            logWrapper.removeLoggerRefs();
        }

        // set field
        logWrapper.setBundleContext( context );

        // context is set, register and get existing services
        if ( context != null )
        {
            try
            {
                ServiceListener listener = new ServiceListener()
                {
                    // Add a newly available LogService reference to the singleton.
                    @Override
                    public void serviceChanged( final ServiceEvent event )
                    {
                        if ( ServiceEvent.REGISTERED == event.getType() )
                        {
                            LogWrapperLoader.m_singleton.addLoggerRef( event.getServiceReference() );
                        }
                        // unregistered services are handled in the next log operation.
                    }

                };
                context.addServiceListener( listener, "(" + Constants.OBJECTCLASS + "=org.osgi.service.log.LogService)" );
                logWrapper.m_logServiceListener = listener;

                // Add all available LogService references to the singleton.
                final ServiceReference<?>[] refs = context.getServiceReferences( "org.osgi.service.log.LogService", null );

                if ( null != refs )
                {
                    for ( int i = 0; i < refs.length; i++ )
                    {
                        logWrapper.addLoggerRef( refs[i] );
                    }
                }
            }
            catch ( InvalidSyntaxException e )
            {
                // this never happens
            }
        }
    }


    /*
     * The private singleton constructor.
     */
    LogWrapper()
    {
        // Singleton
    }

    /*
     * Removes all references to LogServices still kept
     */
    void removeLoggerRefs()
    {
        synchronized ( m_loggerRefs )
        {
            m_loggerRefs.clear();
        }
    }

    /*
     * Add a reference to a newly available LogService
     */
    void addLoggerRef( final ServiceReference<?> ref )
    {
        synchronized (m_loggerRefs)
        {
            m_loggerRefs.add(ref);
        }
    }

    /*
     * Set the context of the bundle in the singleton implementation.
     */
    private void setBundleContext(final BundleContext context)
    {
        synchronized(m_loggerRefs)
        {
            m_context = context;
        }
    }

    /**
     * Log a message with the given log level. Note that this will prefix the message
     * with {@code EventAdmin: }.
     *
     * @param level The log level with which to log the msg.
     * @param msg The message to log.
     */
    public void log(final int level, final String msg)
    {
        // The method will remove any unregistered service reference as well.
        synchronized(m_loggerRefs)
        {
            if (level > m_logLevel)
            {
                return; // don't log
            }

            final String logMsg = "EventAdmin: " + msg;

            if (!m_loggerRefs.isEmpty())
            {
                // There is at least one LogService available hence, we can use the
                // class as well.
                for (Iterator<ServiceReference<?>> iter = m_loggerRefs.iterator(); iter.hasNext();)
                {
                    final ServiceReference<?> next = iter.next();

                    org.osgi.service.log.LogService logger =
                        (org.osgi.service.log.LogService) m_context.getService(next);

                    if (null != logger)
                    {
                        logger.log(level, logMsg);

                        m_context.ungetService(next);
                    }
                    else
                    {
                        // The context returned null for the reference - it follows
                        // that the service is unregistered and we can remove it
                        iter.remove();
                    }
                }
            }
            else
            {
                _log(null, level, logMsg, null);
            }
        }
    }

    /**
     * Log a message with the given log level and the associated exception. Note that
     * this will prefix the message with {@code EventAdmin: }.
     *
     * @param level The log level with which to log the msg.
     * @param msg The message to log.
     * @param ex The exception associated with the message.
     */
    public void log(final int level, final String msg, final Throwable ex)
    {
        // The method will remove any unregistered service reference as well.
        synchronized(m_loggerRefs)
        {
            if (level > m_logLevel)
            {
                return; // don't log
            }

            final String logMsg = "EventAdmin: " + msg;

            if (!m_loggerRefs.isEmpty())
            {
                // There is at least one LogService available hence, we can use the
                // class as well.
                for (Iterator<ServiceReference<?>> iter = m_loggerRefs.iterator(); iter.hasNext();)
                {
                    final ServiceReference<?> next = iter.next();

                    org.osgi.service.log.LogService logger =
                        (org.osgi.service.log.LogService) m_context.getService(next);

                    if (null != logger)
                    {
                        logger.log(level, logMsg, ex);

                        m_context.ungetService(next);
                    }
                    else
                    {
                        // The context returned null for the reference - it follows
                        // that the service is unregistered and we can remove it
                        iter.remove();
                    }
                }
            }
            else
            {
                _log(null, level, logMsg, ex);
            }
        }
    }

    /**
     * Log a message with the given log level together with the associated service
     * reference. Note that this will prefix the message with {@code EventAdmin: }.
     *
     * @param sr The reference of the service associated with this message.
     * @param level The log level with which to log the msg.
     * @param msg The message to log.
     */
    public void log(final ServiceReference<?> sr, final int level, final String msg)
    {
        // The method will remove any unregistered service reference as well.
        synchronized(m_loggerRefs)
        {
            if (level > m_logLevel)
            {
                return; // don't log
            }

            final String logMsg = "EventAdmin: " + msg;

            if (!m_loggerRefs.isEmpty())
            {
                // There is at least one LogService available hence, we can use the
                // class as well.
                for (Iterator<ServiceReference<?>> iter = m_loggerRefs.iterator(); iter.hasNext();)
                {
                    final ServiceReference<?> next = iter.next();

                    org.osgi.service.log.LogService logger =
                        (org.osgi.service.log.LogService) m_context.getService(next);

                    if (null != logger)
                    {
                        logger.log(sr, level, logMsg);

                        m_context.ungetService(next);
                    }
                    else
                    {
                        // The context returned null for the reference - it follows
                        // that the service is unregistered and we can remove it
                        iter.remove();
                    }
                }
            }
            else
            {
                _log(sr, level, logMsg, null);
            }
        }
    }

    /**
     * Log a message with the given log level, the associated service reference and
     * exception. Note that this will prefix the message with {@code EventAdmin: }.
     *
     * @param sr The reference of the service associated with this message.
     * @param level The log level with which to log the msg.
     * @param msg The message to log.
     * @param ex The exception associated with the message.
     */
    public void log(final ServiceReference<?> sr, final int level, final String msg,
        final Throwable ex)
    {
        // The method will remove any unregistered service reference as well.
        synchronized(m_loggerRefs)
        {
            if (level > m_logLevel)
            {
                return; // don't log
            }

            final String logMsg = "EventAdmin: " + msg;

            if (!m_loggerRefs.isEmpty())
            {
                // There is at least one LogService available hence, we can use the
                // class as well.
                for (Iterator<ServiceReference<?>> iter = m_loggerRefs.iterator(); iter.hasNext();)
                {
                       final ServiceReference<?> next = iter.next();

                    org.osgi.service.log.LogService logger =
                        (org.osgi.service.log.LogService) m_context.getService(next);

                    if (null != logger)
                    {
                        logger.log(sr, level, logMsg, ex);

                        m_context.ungetService(next);
                    }
                    else
                    {
                        // The context returned null for the reference - it follows
                        // that the service is unregistered and we can remove it
                        iter.remove();
                    }
                }
            }
            else
            {
                _log(sr, level, logMsg, ex);
            }
        }
    }

    /*
     * Log the message to standard output. This appends the level to the message.
     * null values are handled appropriate.
     */
    private void _log(final ServiceReference<?> sr, final int level, final String msg,
        Throwable ex)
    {
        String s = (sr == null) ? null : "SvcRef " + sr;
        s = (s == null) ? msg : s + " " + msg;
        s = (ex == null) ? s : s + " (" + ex + ")";

        switch (level)
        {
            case LOG_DEBUG:
                System.out.println("DEBUG: " + s);
                break;
            case LOG_ERROR:
                System.out.println("ERROR: " + s);
                if (ex != null)
                {
                    if ((ex instanceof BundleException)
                        && (((BundleException) ex).getNestedException() != null))
                    {
                        ex = ((BundleException) ex).getNestedException();
                    }

                    ex.printStackTrace();
                }
                break;
            case LOG_INFO:
                System.out.println("INFO: " + s);
                break;
            case LOG_WARNING:
                System.out.println("WARNING: " + s);
                break;
            default:
                System.out.println("UNKNOWN[" + level + "]: " + s);
        }
    }

    /**
     * Change the current log level. Log level decides what messages gets
     * logged. Any message with a log level higher than the currently set
     * log level is not logged.
     *
     * @param logLevel new log level
     */
    public void setLogLevel(int logLevel)
    {
        synchronized (m_loggerRefs)
        {
            m_logLevel = logLevel;
        }
    }

    /**
     * @return current log level.
     */
    public int getLogLevel()
    {
        synchronized (m_loggerRefs)
        {
            return m_logLevel;
        }
    }
}

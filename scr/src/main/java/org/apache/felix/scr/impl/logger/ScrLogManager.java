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
package org.apache.felix.scr.impl.logger;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;

import org.apache.felix.scr.impl.logger.InternalLogger.Level;
import org.apache.felix.scr.impl.manager.ScrConfiguration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.Logger;

/**
 * Implements a SCR based log manager. This class was needed to not change the
 * whole codebase. It looks very similar to the old logging but leverages the
 * {@link LogManager}. This class implements the existing behavior and the
 * {@link ExtLogManager} implements the extension behavior. The {@link #scr()}
 * method makes this distinction based on the configuration.
 */
public class ScrLogManager extends LogManager
{

    private final Bundle bundle;
    private final ScrConfiguration config;

    /**
     * Get a new log manager based on the configuration.
     * 
     * @param context
     *                    the bundle context of the SCR bundle
     * @param config
     *                    the SCR configuration
     * @return a proper ScrLogManager
     */

    public static ScrLogger scr(BundleContext context, ScrConfiguration config)
    {
        ScrLogManager manager;
        if (config.isLogExtension())
            manager = new ExtLogManager(context, config);
        else
            manager = new ScrLogManager(context, config);
        manager.open();
        return manager.scr();
    }

    ScrLogManager(BundleContext context, ScrConfiguration config)
    {
        super(context);
        this.config = config;
        this.bundle = context.getBundle();
    }

    /**
     * This logger is used for the main code of SCR. This will use the SCR
     * bundle & the {@link Logger#ROOT_LOGGER_NAME}
     * 
     * @return an Scr Logger.
     */
    public ScrLogger scr()
    {
        ScrLoggerFacade scrl = super.getLogger(bundle, Logger.ROOT_LOGGER_NAME,
            ScrLoggerFacade.class);
        scrl.setPrefix(getBundleIdentifier(bundle));
        return scrl;
    }

    /**
     * This logger is used for the logging on a per bundle basis. This will use
     * the target bundle & the {@link Logger#ROOT_LOGGER_NAME}
     * 
     * @param bundle
     *                   the target bundle
     * @return a logger suitable to log bundle entries
     */
    public BundleLogger bundle(Bundle bundle)
    {
        ScrLoggerFacade logger = getLogger(bundle, Logger.ROOT_LOGGER_NAME,
            ScrLoggerFacade.class);
        logger.setPrefix(getBundleIdentifier(bundle));
        return logger;
    }

    /**
     * This logger is used for the logging on a per bundle basis. This will use
     * the target bundle & the implementation class as logger name.
     * 
     * @param bundle
     *                   the target bundle
     * @return a logger suitable to log bundle entries
     */
    public ComponentLogger component(Bundle bundle, String implementationClass,
        String name)
    {

        // assert bundle != null;
        // assert bundle.getSymbolicName() != null : "scr requires recent
        // bundles";
        // assert implementationClass != null;
        // assert name != null;

        ScrLoggerFacade facade = getLogger(bundle, implementationClass,
            ScrLoggerFacade.class);
        facade.setComponentId(-1);
        return (ComponentLogger) facade;
    }

    class ScrLoggerFacade extends LoggerFacade implements InternalLogger, ScrLogger, BundleLogger, ComponentLogger
    {
        ScrLoggerFacade(LogDomain logDomain, String name)
        {
            super(logDomain, name);
        }

        @Override
        public void setComponentId(long id)
        {
            setPrefix(componentPrefix(this, id));
        }

        public boolean isLogEnabled(Level level)
        {

            // assert !closed.get();

            Object checkLogger = getLogger();
            if (checkLogger != null)
            {
                Logger logger = (Logger) checkLogger;
                switch (level)
                {
                    case AUDIT:
                        return true;
                    case ERROR:
                        return logger.isErrorEnabled();
                    case WARN:
                        return logger.isWarnEnabled();
                    case INFO:
                        return logger.isInfoEnabled();
                    case TRACE:
                        return logger.isTraceEnabled();
                    case DEBUG:
                    default:
                        return logger.isDebugEnabled();
                }
            }
            else
            {
                return getLogLevel().implies(level);
            }
        }

        @Override
        public void log(Level level, String format, Throwable ex, Object... arguments)
        {
            if (isLogEnabled(level))
                log0(level, format(format, arguments), ex);
        }

        @Override
        public void log(Level level, String message, Throwable ex)
        {
            if (isLogEnabled(level))
                log0(level, message, ex);
        }

        void log0(Level level, String message, Throwable ex)
        {
            if (prefix != null && prefix.length() > 0)
            {
                message = prefix.concat(" ").concat(message);
            }
            Object checkLogger = getLogger();
            if (checkLogger != null)
            {
                Logger logger = (Logger) checkLogger;
                if (ex == null)
                {
                    switch (level)
                    {
                        case AUDIT:
                            logger.audit(message);
                            break;
                        case ERROR:
                            logger.error(message);
                            break;
                        case WARN:
                            logger.warn(message);
                            break;
                        case INFO:
                            logger.info(message);
                            break;
                        case TRACE:
                            logger.trace(message);
                            break;
                        case DEBUG:
                        default:
                            logger.debug(message);
                    }
                }
                else
                {
                    switch (level)
                    {
                        case AUDIT:
                            logger.audit(message, ex);
                            break;
                        case ERROR:
                            logger.error(message, ex);
                            break;
                        case WARN:
                            logger.warn(message, ex);
                            break;
                        case INFO:
                            logger.info(message, ex);
                            break;
                        case TRACE:
                            logger.trace(message, ex);
                            break;
                        case DEBUG:
                        default:
                            logger.debug(message, ex);
                    }
                }
            }
            else
            {
                StringWriter buf = new StringWriter();
                String l = String.format("%-5s", level);
                buf.append(l).append(" : ").append(message);
                if (ex != null)
                {
                    try (PrintWriter pw = new PrintWriter(buf))
                    {
                        pw.println();
                        ex.printStackTrace(pw);
                    }
                }

                @SuppressWarnings("resource")
                PrintStream out = level.err() ? System.err : System.out;
                out.println(buf);
            }
        }

        void setPrefix(String prefix)
        {
            this.prefix = prefix;
        }

        @Override
        public ComponentLogger component(Bundle bundle, String implementationClassName,
            String name)
        {
            // assert !closed.get();
            return ScrLogManager.this.component(bundle, implementationClassName, name);
        }

        @Override
        public BundleLogger bundle(Bundle bundle)
        {
            // assert !closed.get();
            return ScrLogManager.this.bundle(bundle);
        }

        @Override
        public void close()
        {
            // assert !closed.get();
            ScrLogManager.this.close();
        }
    };

    LoggerFacade createLoggerFacade(LogDomain logDomain, String name)
    {
        // assert !closed.get();
        return new ScrLoggerFacade(logDomain, name);
    }

    Level getLogLevel()
    {
        return config.getLogLevel();
    }

    String getBundleIdentifier(final Bundle bundle)
    {
        final StringBuilder sb = new StringBuilder("bundle ");

        if (bundle.getSymbolicName() != null)
        {
            sb.append(bundle.getSymbolicName());
            sb.append(':');
            sb.append(bundle.getVersion());
            sb.append(" (");
            sb.append(bundle.getBundleId());
            sb.append(")");
        }
        else
        {
            sb.append(bundle.getBundleId());
        }

        return sb.toString();
    }

    String componentPrefix(ScrLoggerFacade slf, long id)
    {
        if (id >= 0)
        {
            return getBundleIdentifier(slf.getBundle()) + "[" + slf.getName() + "(" + id
                + ")] :";
        }
        else
        {
            return getBundleIdentifier(slf.getBundle()) + "[" + slf.getName() + "] :";
        }
    }

    String format(final String pattern, final Object... arguments)
    {
        if (arguments == null || arguments.length == 0)
        {
            return pattern;
        }
        else
        {
            for (int i = 0; i < arguments.length; i++)
            {
                if (arguments[i] instanceof Bundle)
                {
                    arguments[i] = getBundleIdentifier((Bundle) arguments[i]);
                }
            }
            return MessageFormat.format(pattern, arguments);
        }
    }

}

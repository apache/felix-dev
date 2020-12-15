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

import java.text.MessageFormat;

import org.apache.felix.scr.impl.manager.ScrConfiguration;
import org.osgi.service.log.admin.LoggerAdmin;

/**
 * Base interface for the different SCR Loggers. Since this is not used outside
 * this package, it could be private. However, then Level should be standalone,
 * which would change most files. So minimize the code change, it is kept public.
 */
public interface InternalLogger
{
    /**
     * The level to log. This is aligned with the OSGi LogLevel
     */
    public enum Level
    {
        AUDIT
        {
            boolean err()
            {
                return true;
            }
        },
        ERROR
        {
            boolean err()
            {
                return true;
            }
        },
        WARN, INFO, DEBUG, TRACE;

        /**
         * Check if this log level is higher or the same of the other level.
         * 
         * @param other
         *                  the other level
         * @return true if the level other should be logged
         */
        boolean implies(Level other)
        {
            return ordinal() >= other.ordinal();
        }

        boolean err()
        {
            return false;
        }

    }

    /**
     * Logs the message to an appropriate OSGi logger. If not such logger can be
     * found then it will log to stderr for ERROR & AUDIT messages and stdout
     * for other messages
     * 
     * @param level
     *                    only log when this level is implied by the current log
     *                    level
     * @param message
     *                    the message to log
     * @param ex
     *                    a Throwable or null
     */
    void log(Level level, String message, Throwable ex);

    /**
     * Formats the message using the {@link MessageFormat} class, i.e. with {}
     * place holders for the args. It then calls
     * {@link #log(Level, String, Throwable)}.
     * 
     * @param level
     *                    only log when this level is implied by the current log
     *                    level
     * @param message
     *                    the message to log
     * @param ex
     *                    a Throwable or null
     * @param args
     *                    the arguments to the {@link MessageFormat} formatting
     */
    void log(Level level, String message, Throwable ex, Object... args);

    /**
     * Answer true if the current logging level is enabled for the given level.
     * For stdout/stderr fallback the logging level is defined by the
     * {@link ScrConfiguration#getLogLevel()}. If there is an OSGi logger
     * available then the logger name will define the log level via
     * {@link LoggerAdmin}.
     *
     * @param level
     *                  the level to check
     * @return true if the given log level is enabled
     */
    boolean isLogEnabled(Level level);

}

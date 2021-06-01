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

import org.apache.felix.scr.impl.logger.InternalLogger.Level;

/**
 * This is used to deal with the log configuration.
 * 
 * <p>
 * The log configuration comprises the following:
 * 
 * <p>
 * <ul>
 * <li>The associated log level</li>
 * <li>flag if the log is enabled</li>
 * <li>flag if the log extension is enabled</li>
 * </ul>
 * 
 * <p>
 * Note that, any consumer can decide if the logging in SCR is at all required.
 * By default, the SCR logging will be enabled. Consumer can decide to set the
 * following property to {@code false} to disable the SCR logging completely.
 * 
 * <p>
 * {@code ds.log.enabled}
 * 
 * <p>
 * Also note that, consumer can also decide to enable log extension by setting
 * the following property to {@code true}. This also implies that the logging is 
 * enabled.
 * 
 * <p>
 * {@code ds.log.extension}
 * 
 * <p>
 * Note that, by default SCR uses the log level of the bundle that contains the
 * SCR components to log the messages, but the log extension uses the log level
 * of the SCR bundle itself to log the messages.
 *
 */
public interface LogConfiguration
{
    /**
     * The property to retrieve the log level
     */
    String PROP_LOGLEVEL = "ds.loglevel";

    /**
     * The property to enable or disable the logging
     */
    String PROP_LOG_ENABLED = "ds.log.enabled";

    /**
     * The property to enable log extension
     */
    String PROP_LOG_EXTENSION = "ds.log.extension";

    /**
     * Returns the current log level
     * 
     * @return the log level (cannot be {@code null})
     */
    Level getLogLevel();

    /**
     * Checks if the logging is enabled. Disabling logging is incompatible
     * with the OSGi specification.
     * 
     * @return {@code true} if enabled otherwise {@code false}
     */
    boolean isLogEnabled();

    /**
     * Checks if the log extension is enabled. The extension is incompatible
     * with the OSGi specification.
     * 
     * @return {@code true} if enabled otherwise {@code false}
     */
    boolean isLogExtensionEnabled();
}
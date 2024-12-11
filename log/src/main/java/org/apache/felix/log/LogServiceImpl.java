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

import org.osgi.annotation.bundle.Capability;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.namespace.service.ServiceNamespace;
import org.osgi.service.log.LogService;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;
import org.osgi.service.log.admin.LoggerAdmin;

/**
 * Implementation of the OSGi {@link LogService}.
 */
@Capability(
        namespace = ServiceNamespace.SERVICE_NAMESPACE,
        attribute = { "objectClass:List<String>=\"org.osgi.service.log.LogService,org.osgi.service.log.LoggerFactory\"" },
        uses = { LogServiceImpl.class, LogService.class, LoggerFactory.class, LoggerAdmin.class }
)
final class LogServiceImpl implements LogService
{
    /** The bundle associated with this implementation. */
    private final Bundle m_bundle;
    /** The logger admin impl. */
    private final LoggerAdminImpl m_loggerAdminImpl;

    /**
     * Create a new instance.
     * @param bundle the bundle associated with this implementation
     * @param loggerAdminImpl
     */
    LogServiceImpl(final Bundle bundle, final LoggerAdminImpl loggerAdminImpl)
    {
        this.m_bundle = bundle;
        this.m_loggerAdminImpl = loggerAdminImpl;
    }

    /**
     * Log the specified message at the specified level.
     * @param level the level to log the message at
     * @param message the message to log
     */
    public void log(final int level, final String message)
    {
        log(null, level, message, null);
    }

    /**
     * Log the specified message along with the specified exception at the
     * specified level.
     * @param level the level to log the message and exception at
     * @param message the message to log
     * @param exception the exception to log
     */
    public void log(final int level,
        final String message,
        final Throwable exception)
    {
        log(null, level, message, exception);
    }

    /**
     * Log the specified message along with the speicified service reference
     * at the specified level.
     * @param sr the service reference of the service that produced the message
     * @param level the level to log the message at
     * @param message the message to log
     */
    public void log(final ServiceReference<?> sr,
        final int level,
        final String message)
    {
        log(sr, level, message, null);
    }

    /**
     * Log the specified message along with the specified exception and
     * service reference at the specified level.
     * @param sr the service reference of the service that produced the message
     * @param level the level to log the message at
     * @param message the message to log
     * @param exception the exception to log
     */
    @SuppressWarnings("deprecation")
    public void log(final ServiceReference<?> sr,
        final int level,
        final String message,
        final Throwable exception)
    {
        LoggerImpl logger = (LoggerImpl)m_loggerAdminImpl.getLogger(m_bundle, "LogService.".concat(m_bundle.getSymbolicName()), Logger.class);

        switch (level) {
            case LogService.LOG_DEBUG:
                logger.debug(message, sr, exception);
                break;
            case LogService.LOG_ERROR:
                logger.error(message, sr, exception);
                break;
            case LogService.LOG_INFO:
                logger.info(message, sr, exception);
                break;
            case LogService.LOG_WARNING:
                logger.warn(message, sr, exception);
                break;
            default:
                logger.log(level, message, sr, exception);
        }
    }

    @Override
    public Logger getLogger(String name) {
        return m_loggerAdminImpl.getLogger(m_bundle, name, Logger.class);
    }

    @Override
    public Logger getLogger(Class<?> clazz) {
        return m_loggerAdminImpl.getLogger(m_bundle, clazz.getName(), Logger.class);
    }

    @Override
    public <L extends Logger> L getLogger(String name, Class<L> loggerType) {
        return m_loggerAdminImpl.getLogger(m_bundle, name, loggerType);
    }

    @Override
    public <L extends Logger> L getLogger(Class<?> clazz, Class<L> loggerType) {
        return m_loggerAdminImpl.getLogger(m_bundle, clazz.getName(), loggerType);
    }

    @Override
    public <L extends Logger> L getLogger(Bundle bundle, String name, Class<L> loggerType) {
        return m_loggerAdminImpl.getLogger(bundle, name, loggerType);
    }

}

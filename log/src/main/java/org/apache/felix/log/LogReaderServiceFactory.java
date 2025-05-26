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

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogReaderService;

/**
 * {@link ServiceFactory} implementation for {@link LogReaderService}.  Associates
 * an individual {@link LogReaderService} with a {@link Bundle}.
 */
final class LogReaderServiceFactory implements ServiceFactory<LogReaderService>
{
    /** The log to associate the service implementations with. */
    private final Log m_log;

    /**
     * Create a new instance.
     * @param log the log to associate the service implementations with.,
     */
    LogReaderServiceFactory(final Log log)
    {
        m_log = log;
    }

    /**
     * Get the service to use for the specified bundle.
     * @param bundle the bundle requesting the service
     * @param registration the service registration
     * @return the log reader service implementation for the specified bundle
     */
    @Override
    public LogReaderService getService(final Bundle bundle,
        final ServiceRegistration<LogReaderService> registration)
    {
        return new LogReaderServiceImpl(m_log);
    }

    /**
     * Release the service previously obtained through
     * {@link #getService(Bundle, ServiceRegistration)}.
     * @param bundle the bundle that originally requested the service
     * @param registration the service registration
     * @param service the service to release
     */
    @Override
    public void ungetService(final Bundle bundle,
        final ServiceRegistration<LogReaderService> registration,
        final LogReaderService service)
    {
        ((LogReaderServiceImpl) service).removeAllLogListeners();
    }
}
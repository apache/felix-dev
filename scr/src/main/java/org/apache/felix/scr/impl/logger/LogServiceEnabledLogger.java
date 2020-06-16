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

import org.osgi.framework.Bundle;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;
import org.osgi.util.tracker.ServiceTracker;

/**
 * This abstract class adds support for using a LogService
 * (or LoggerFactory for R7+).
 */
abstract class LogServiceEnabledLogger extends AbstractLogger
{
    // the log service to log messages to
    protected final ServiceTracker<LoggerFactory, LoggerFactory> loggingFactoryTracker;

    protected final Bundle bundle;

    private volatile InternalLogger currentLogger;

    protected volatile int trackingCount = -2;

    public LogServiceEnabledLogger(final Bundle bundle, ServiceTracker<LoggerFactory, LoggerFactory> loggingFactoryTracker)
    {
        super(getBundleIdentifier(bundle));
        this.bundle = bundle;
        this.loggingFactoryTracker = loggingFactoryTracker;
    }

    @Override
    InternalLogger getLogger()
    {
        if (this.trackingCount < this.loggingFactoryTracker.getTrackingCount())
        {
            final LoggerFactory factory = this.loggingFactoryTracker.getService();
            if (factory == null)
            {
                this.currentLogger = this.getDefaultLogger();
            }
            else
            {
                this.currentLogger = new OSGiLogger(
                    factory.getLogger(bundle, null, Logger.class));
            }
            this.trackingCount = this.loggingFactoryTracker.getTrackingCount();
        }
        return currentLogger;
    }

    abstract InternalLogger getDefaultLogger();

    ServiceTracker<LoggerFactory, LoggerFactory> getLoggerFactoryTracker()
    {
        return loggingFactoryTracker;
    }
}
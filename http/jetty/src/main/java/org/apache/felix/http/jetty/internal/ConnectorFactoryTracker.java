/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.http.jetty.internal;

import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.apache.felix.http.base.internal.util.ServiceUtils;
import org.apache.felix.http.jetty.ConnectorFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

public class ConnectorFactoryTracker extends ServiceTracker<ConnectorFactory, Connector>
{
    private final Server server;

    public ConnectorFactoryTracker(final BundleContext context, final Server server)
    {
        super(context, ConnectorFactory.class, null);
        this.server = server;
    }

    @Override
    public void open()
    {
        if (!this.server.isStarted())
        {
            throw new IllegalStateException("Jetty Server must be started before looking for ConnectorFactory services");
        }

        super.open();
    }

    @Override
    public Connector addingService(ServiceReference<ConnectorFactory> reference)
    {
        ConnectorFactory factory = ServiceUtils.safeGetService(context, reference);
        if (factory != null) {
            Connector connector = null;
            try {
                connector = factory.createConnector(server);
                this.server.addConnector(connector);
                connector.start();
                return connector;
            } catch (Exception e) {
                SystemLogger.LOGGER.error("Failed starting connector '{}' provided by {}", connector, reference, e);
            }
            // connector failed to start, don't continue tracking
            ServiceUtils.safeUngetService(context, reference);
        }
        return null;
    }

    @Override
    public void removedService(ServiceReference<ConnectorFactory> reference, Connector service)
    {
        Connector connector = service;
        if (connector.isStarted())
        {
            try
            {
                connector.stop();
            }
            catch (Exception e)
            {
                SystemLogger.LOGGER.info("Failed stopping connector '{}' provided by {}", connector, reference, e);
            }
        }
        this.server.removeConnector(connector);
        ServiceUtils.safeUngetService(context, reference);
    }
}

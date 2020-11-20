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

import java.io.Closeable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;
import org.osgi.util.tracker.ServiceTracker;

/*
 * A class that tracks the LoggerFactory of a bundle and its associated Logger objects. 
 * When a bundle is stopped and/or the service is unregistered, the logger objects are cleaned.
 * 
 * The basic technique is to use a facade. Instead of returning a log object, we return a facade. The 
 * methods delegate to the actual logger object. If there is no logger object, we create one.
 * 
 * The LogDomain represents every bundle. Per LogDomain, we keep the facades. If the factory goes,
 * we reset the facades.
 */
class LogManager extends ServiceTracker<Object, Object> implements BundleListener
{

    private static final String LOGGER_FACTORY_CLASS_NAME = "org.osgi.service.log.LoggerFactory";

    final BundleContext scrContext;
    final AtomicBoolean closed = new AtomicBoolean(false);

    /*
     * Locks access to guarded fields
     */
    class Lock
    {
        final Map<Bundle, LogDomain> domains = new HashMap<>();
        int trackingCount;
        Object factory;
        int ranking = 0;

        synchronized LogDomain getLogDomain(Bundle bundle)
        {
            LogDomain domain = domains.get(bundle);
            if (domain == null)
            {
                domain = new LogDomain(bundle);
                domains.put(bundle, domain);
            }
            return domain;
        }

        synchronized void removedFactory(Object service)
        {
            if (this.factory == service)
            {
                this.factory = null;
                reset();
            }
        }

        synchronized void setFactory(int ranking, Object service)
        {
            if (this.factory == null)
            {
                this.factory = service;
                this.ranking = ranking;
            }
            else if (this.ranking < ranking)
            {
                this.factory = service;
                this.ranking = ranking;
                reset();
            }
        }

        synchronized void reset()
        {
            for (LogDomain domain : domains.values())
            {
                domain.reset();
            }
        }

        synchronized Object getLogger(LoggerFacade facade, Bundle bundle, String name)
        {
            if (factory == null)
                return facade.logger = null;
            else
                return facade.logger = ((LoggerFactory) factory).getLogger(bundle, name,
                    Logger.class);
        }

        synchronized LogDomain remove(Bundle bundle)
        {
            return domains.remove(bundle);
        }

        synchronized void close()
        {
            reset();
            domains.clear();
        }

    }

    final Lock lock = new Lock();

    LogManager(BundleContext context)
    {
        super(context, LOGGER_FACTORY_CLASS_NAME, null);
        this.scrContext = context;
        scrContext.addBundleListener(this);
    }

    @Override
    public Object addingService(ServiceReference<Object> reference)
    {
        Object service = super.addingService(reference);
        Integer ranking = (Integer) reference.getProperty(Constants.SERVICE_RANKING);
        if (ranking == null)
            ranking = 0;
        lock.setFactory(ranking, service);
        return service;
    }

    @Override
    public void removedService(ServiceReference<Object> reference, Object service)
    {
        super.removedService(reference, service);
        lock.removedFactory(service);
    }

    <T> T getLogger(Bundle bundle, String name, Class<T> type)
    {
        return type.cast(lock.getLogDomain(bundle).getLogger(name));
    }

    @SuppressWarnings("resource")
    @Override
    public void bundleChanged(BundleEvent event)
    {
        if (event.getType() == BundleEvent.STOPPED && !closed.get())
        {
            LogDomain domain = lock.remove(event.getBundle());
            if (domain != null)
            {
                domain.close();
            }
        }
    }

    /*
     * Tracks a bundle's LoggerFactory service
     */
    class LogDomain implements Closeable
    {

        private final Bundle bundle;
        private final Set<LoggerFacade> facades = new HashSet<>();

        LogDomain(Bundle bundle)
        {
            this.bundle = bundle;
        }

        private void reset()
        {
            synchronized (facades)
            {
                for (LoggerFacade facade : facades)
                {
                    facade.reset();
                }
            }
        }

        LoggerFacade getLogger(String name)
        {
            LoggerFacade facade = createLoggerFacade(this, name);
            synchronized (facades)
            {
                facades.add(facade);
            }
            return facade;
        }

        @Override
        public void close()
        {
            reset();
        }

    }

    class LoggerFacade
    {
        private final String name;
        private final LogDomain domain;
        volatile Object logger;
        volatile String prefix;

        LoggerFacade(LogDomain logDomain, String name)
        {
            this.domain = logDomain;
            this.name = name;
        }

        void reset()
        {
            logger = null;
        }

        Object getLogger()
        {
            Object l = this.logger;
            if (l == null)
            {
                l = lock.getLogger(this, domain.bundle, name);
            }
            return l;
        }

        Bundle getBundle()
        {
            return domain.bundle;
        }

        String getName()
        {
            return name;
        }

    }

    public void close()
    {
        if (closed.compareAndSet(false, true))
        {
            lock.close();
            super.close();
            this.context.removeBundleListener(this);
        }
    }

    LoggerFacade createLoggerFacade(LogDomain logDomain, String name)
    {
        assert !closed.get();
        return new LoggerFacade(logDomain, name);
    }

}

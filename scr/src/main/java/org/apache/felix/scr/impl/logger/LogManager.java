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
class LogManager extends ServiceTracker<LoggerFactory, LoggerFactory> {

	// Guard is itself. Don't replace it with a ConcurrentHashMap since the
	// LogDomain value is not stateless.

	final Map<Bundle, LogDomain>	domains	= new HashMap<>();
	final BundleContext				scrContext;
	final AtomicBoolean				closed	= new AtomicBoolean(false);
	volatile int					trackingCount;
	volatile LoggerFactory			factory;

	LogManager(BundleContext context) {
		super(context, LoggerFactory.class, null);
		this.scrContext = context;
	}

	private LogDomain getLogDomain(Bundle bundle) {
		synchronized (domains) {
			LogDomain domain = domains.get(bundle);
			if (domain == null) {
				domain = new LogDomain(bundle);
				domains.put(bundle, domain);
			}
			return domain;
		}
	}

	@Override
	public void removedService(ServiceReference<LoggerFactory> reference, LoggerFactory service) {
		if (!closed.get()) {
			reset();
		}
	}

	private void reset() {
		for (LogDomain domain : domains.values()) {
			domain.reset();
		}
	}

	<T> T getLogger(Bundle bundle, String name, Class<T> type) {
		return type.cast(getLogDomain(bundle).getLogger(name));
	}

	LoggerFactory getLoggerFactory() {
		int trackingCount = getTrackingCount();
		if (this.trackingCount < trackingCount) {
			this.trackingCount = trackingCount;
			factory = getService();
		}
		return factory;
	}

	/*
	 * Tracks a bundle's LoggerFactory service
	 */
	class LogDomain
			implements Closeable, BundleListener {

		private final Bundle			bundle;
		private final Set<LoggerFacade>	facades	= new HashSet<>();

		LogDomain(Bundle bundle) {
			this.bundle = bundle;
			scrContext.addBundleListener(this);
			open();
		}

		void reset() {
			for (LoggerFacade facade : facades) {
				facade.reset();
			}
		}

		@SuppressWarnings("resource")
		@Override
		public void bundleChanged(BundleEvent event) {
			if (event.getBundle() == bundle && event.getType() == BundleEvent.STOPPED && !closed.get()) {
				scrContext.removeBundleListener(this);
				LogDomain remove;
				synchronized (domains) {
					remove = domains.remove(bundle);
				}
				if (remove != null)
					remove.close();
			}
		}

		LoggerFacade getLogger(String name) {
			LoggerFacade facade = createLoggerFacade(this, name);
			facades.add(facade);
			return facade;
		}

		@Override
		public void close() {
			reset();
		}

	}

	class LoggerFacade {
		private final String	name;
		private final LogDomain	domain;
		volatile Logger			logger;
		volatile String			prefix;

		LoggerFacade(LogDomain logDomain, String name) {
			this.domain = logDomain;
			this.name = name;
		}

		void reset() {
			logger = null;
		}

		Logger getLogger() {
			Logger l = this.logger;
			if (l == null) {
				LoggerFactory lf = getLoggerFactory();
				if (lf == null)
					return null;

				l = this.logger = lf.getLogger(domain.bundle, name, Logger.class);
			}
			return l;
		}

		Bundle getBundle() {
			return domain.bundle;
		}

		String getName() {
			return name;
		}

	}

	public void close() {
		if (closed.compareAndSet(false, true)) {
			Set<LogDomain> domains;
			synchronized (this.domains) {
				domains = new HashSet<>(this.domains.values());
				this.domains.clear();
			}
			for (LogDomain domain : domains) {
				domain.close();
			}
		}
	}

	LoggerFacade createLoggerFacade(LogDomain logDomain, String name) {
		assert !closed.get();
		return new LoggerFacade(logDomain, name);
	}

}

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
package org.apache.felix.dm.impl.index;

import java.lang.reflect.Constructor;
import java.util.function.Consumer;

import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.FilterIndex;
import org.apache.felix.dm.impl.index.multiproperty.MultiPropertyFilterIndex;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;

/**
 * This class manages the service registry cache creation.
 */
public class ServiceRegistryCacheManager {
	
	/**
	 * The Service Registry cache, which is created if you specify the "org.apache.felix.dependencymanager.filterindex" system property
	 * or if you register as a service a FilterIndex in the service registry.
	 */
	private static volatile ServiceRegistryCache m_cache;
	
	/**
	 * Backdoor only used by tests: a Consumer<String> is added in system properties using the following key and 
	 * when tests invoke it, the cache is reintialized as if the JVM would have been restarted.
	 */
	private final static String RESET = "org.apache.felix.dependencymanager.filterindex.reset";
	
	/**
	 * the DependendencyManager bundle context used by the ServiceRegistryCache.
	 */
	private static volatile BundleContext m_context;

	/**
	 * Gets the service registry cache, if enabled. Before returning the cache, we try to make sure the DependencyManager
	 * bundle is started, because the cache is initialized by the DependencyManager bundle Activator.
	 *
	 * The cache is only an optimization: this method never throws, and if the DependencyManager bundle can't be started,
	 * the error is reported and the cache (possibly null) is returned.
	 *
	 * Never call this method from the DependencyManager Activator.
	 *
	 * @return the service registry cache, if enabled, or null
	 */
	public static ServiceRegistryCache getCache() {
		try {
			Bundle dm = FrameworkUtil.getBundle(ServiceRegistryCacheManager.class);
			if (dm != null && dm.getState() != Bundle.ACTIVE) {
				startDependencyManager(dm);
			}
		} catch (Exception e) {
			// we can't start the DependencyManager bundle: never prevent the user bundle from starting, just don't use the indices
			e.printStackTrace();
		}
		return m_cache;
	}

	/**
	 * Starts the DependencyManager bundle, so that its Activator has initialized the cache when we return it.
	 *
	 * We first try a transient start, because while the framework is raising its start level, a transient start is much
	 * more likely to activate the bundle immediately than a persistent start (this describes the Felix framework):
	 * <ul>
	 * <li>the framework raises its active start level one level at a time, and its start level thread starts the
	 * bundles from a queue, in start level order;</li>
	 * <li>a persistent start called from another thread is queued if the bundle start level is greater than or equal to
	 * the active start level: it returns without starting the bundle;</li>
	 * <li>a transient start is never queued: if the bundle start level is lower than or equal to the active start level,
	 * it activates the bundle immediately (or waits until the thread that is already starting it has completed), else
	 * it fails with START_TRANSIENT_ERROR.</li>
	 * </ul>
	 * For example, if all bundles have the start level 2, the active start level is 2 during almost the whole startup:
	 * a transient start activates the DependencyManager bundle immediately, whereas a persistent start only queues it,
	 * so the cache would not be initialized yet (this was the cause of FELIX-6874).
	 *
	 * If the transient start fails with START_TRANSIENT_ERROR, we fall back to a persistent start: it still activates
	 * the bundle immediately if the framework has just begun to raise its start level (no bundle is queued yet, and the
	 * active start level is still 0). Else, the bundle is started later, and our caller does not use the cache.
	 *
	 * @param dm the DependencyManager bundle
	 * @throws BundleException if the DependencyManager bundle can't be started
	 */
	private static void startDependencyManager(Bundle dm) throws BundleException {
		try {
			dm.start(Bundle.START_TRANSIENT);
		} catch (BundleException e) {
			if (dm.getState() == Bundle.ACTIVE) {
				return; // started meanwhile by another thread
			}
			if (e.getType() != BundleException.START_TRANSIENT_ERROR) {
				throw e;
			}
			// start level not met yet: fall back to a persistent start (see javadoc)
			dm.start();
		}
	}

	/**
	 * Registers a FilterIndex into the service registry cache. The cache is created if necessary.
	 * @param index a new FilterIndex to be registered in the cache
	 * @param context the bundle context used by the cache
	 */
	public static void registerFilterIndex(FilterIndex index, BundleContext context) {
		ServiceRegistryCache cache = createCache(context);
		cache.addFilterIndex(index);
	}
	
	/**
	 * Unregister a FilterIndex from the cache, and possibly close the cache in case it becomes empty.
	 * @param index the FilterIndex to unregister
	 */
	public static void unregisterFilterIndex(FilterIndex index) {
		ServiceRegistryCache cache = m_cache;
		if (cache != null) {
			cache.removeFilterIndex(index);
			boolean close = false;
			synchronized (ServiceRegistryCacheManager.class) {
				if (cache.getSize() == 0) {
					m_cache = null;
					close = true;
				}				
			}
			if (close) {
				cache.close();
			}
		}
	}
	
	/**
	 * Creates the cache if it does not exist.
	 * @param context the bundle context that will be used by the case
	 * @return the created cache (or the existing cache)
	 */
	private static ServiceRegistryCache createCache(BundleContext context) {
		ServiceRegistryCache cache = null;
		boolean open = false;
		synchronized (ServiceRegistryCacheManager.class) {
			if (m_cache == null) {
				m_cache = new ServiceRegistryCache(context);
				open = true;
			}
			cache = m_cache;
		}
		if (open) {
			cache.open();
		}
		return cache;
	}
	
	/**
	 * Initialize the service registry cache. Called by the DependencyManager Activator, with the DependencyManager bundle context
	 * (the framework serializes the Activator start, so we don't need to synchronize).
	 * Initialization is only done once, even if the DependencyManager bundle is restarted.
	 * @param context the DependencyManager bundle context
	 */
	public static void init(BundleContext context) {
		if (m_context != null) {
			return;
		}
		m_context = context;
		String index = m_context.getProperty(DependencyManager.SERVICEREGISTRY_CACHE_INDICES);
		if (index != null) {
			resetIndices(index);
		}
		Consumer<String> reset = ServiceRegistryCacheManager::reset;
		System.getProperties().put(RESET, reset);
	}
		
	private static void resetIndices(String index) {
		try {
			if (index != null) {
				ServiceRegistryCache cache = createCache(m_context); // may already exist, in case the Activator has called back our registerFilterIndex method
					
				String[] props = index.split(";");
				for (int i = 0; i < props.length; i++) {
					// Check if a classname is specified for the current index.
					// (syntax is "full_class_name:index")
					int colon = props[i].indexOf(":");
					if (colon != -1) {
						String className = props[i].substring(0, colon).trim();
						String indexDefinition = props[i].substring(colon + 1);
						cache.addFilterIndex(createCustomIndex(className, indexDefinition));
					} else if (props[i].equals("*aspect*")) {
						cache.addFilterIndex(new AspectFilterIndex());
					} else if (props[i].equals("*adapter*")) {
						cache.addFilterIndex(new AdapterFilterIndex());
					} else {
						cache.addFilterIndex(new MultiPropertyFilterIndex(props[i]));
					}
				}
			}			
		}

		catch (Exception e) {
			e.printStackTrace();
		}				
	}
		
	/**
	 * Creates a custom index using its classname, that has been specified in the org.apache.felix.dependencymanager.filterindex system property.
	 */
	private static FilterIndex createCustomIndex(String className, String indexDefinition) {
		try {
			Class<?> customIndexClass = Class.forName(className);
			Constructor<?> ctor = customIndexClass.getConstructor(String.class);
			FilterIndex index = (FilterIndex) ctor.newInstance(indexDefinition);
			return index;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Reinitialize the cache. Only called by tests. This method is registered in the system properties in the form of a Runnable,
	 * with key=ServiceRegistryCacheManager.RESET.
	 */
	private static void reset(String indices) {
		if (m_cache != null) {
			for (FilterIndex index : m_cache.getFilterIndices()) {
				index.close();
			}
			m_cache.close();
		}
		m_cache = null;
		resetIndices(indices);		
	}

}

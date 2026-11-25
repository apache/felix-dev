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
package org.apache.felix.dm.index.itest.refresh.tests;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;

import junit.framework.TestCase;

/**
 * Checks that the service registry cache works after a refresh of the DependencyManager bundle.
 *
 * The cache is kept in static fields of the DependencyManager bundle. A refresh creates a new
 * wiring of the bundle, with a new class loader, so these fields start from scratch: the cache is
 * created again with the new DependencyManager bundle context, and the bundles which use the
 * DependencyManager are refreshed too (stopped and started again), so their components use the new
 * cache.
 *
 * The cache is enabled with an "objectClass,dm.refresh.test" index (see the test.bndrun). The
 * refresh test bundle creates a component with a service dependency on the Runnable services having
 * the "dm.refresh.test" property, which goes through this index, and runs each Runnable it gets, and
 * each time its properties are updated. We check that a Runnable registered before the refresh, then
 * one registered after the refresh, are injected, and that the update of their service properties
 * is propagated.
 *
 * This test bundle doesn't import any package of the DependencyManager bundle (or of the refresh
 * test bundle), so it is not refreshed with it: it only uses the OSGi and JDK APIs.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class FELIX6874_DependencyManagerRefreshTest extends TestCase {

	private final static String DM_BSN = "org.apache.felix.dependencymanager";

	private final static String REFRESH_BUNDLE_BSN = "org.apache.felix.dependencymanager.index.itest.refresh.bundle";

	private final static long TIMEOUT = 10000; // ms

	private BundleContext m_context;

	/** The framework errors reported during the test. */
	private final List<FrameworkEvent> m_errors = new CopyOnWriteArrayList<>();

	private final FrameworkListener m_errorListener = event -> {
		if (event.getType() == FrameworkEvent.ERROR) {
			m_errors.add(event);
		}
	};

	@Override
	public void setUp() throws Exception {
		m_context = FrameworkUtil.getBundle(getClass()).getBundleContext();
		m_context.addFrameworkListener(m_errorListener);
	}

	@Override
	public void tearDown() throws Exception {
		m_context.removeFrameworkListener(m_errorListener);
	}

	public void testCacheWorksAfterDependencyManagerRefresh() throws Exception {
		Bundle dm = getBundle(DM_BSN);
		Bundle refreshBundle = getBundle(REFRESH_BUNDLE_BSN);
		assertEquals(Bundle.ACTIVE, dm.getState());
		assertEquals(Bundle.ACTIVE, refreshBundle.getState());

		// before the refresh
		checkRunnableInjected("before the refresh");

		// refresh the DependencyManager bundle, and wait until the refresh is complete (the refreshed
		// bundles have been started again)
		BundleWiring dmWiring = dm.adapt(BundleWiring.class);
		BundleWiring refreshBundleWiring = refreshBundle.adapt(BundleWiring.class);
		CountDownLatch refreshed = new CountDownLatch(1);
		FrameworkWiring wiring = m_context.getBundle(0).adapt(FrameworkWiring.class);
		wiring.refreshBundles(Collections.singleton(dm), event -> {
			if (event.getType() == FrameworkEvent.PACKAGES_REFRESHED) {
				refreshed.countDown();
			}
		});
		assertTrue("refresh not complete", refreshed.await(TIMEOUT, TimeUnit.MILLISECONDS));

		// both bundles have a new wiring (with a new class loader), and have been started again
		assertNotSame("DependencyManager bundle not refreshed", dmWiring, dm.adapt(BundleWiring.class));
		assertNotSame("refresh test bundle not refreshed", refreshBundleWiring, refreshBundle.adapt(BundleWiring.class));
		assertNotSame("DependencyManager class loader not recreated", dmWiring.getClassLoader(),
			dm.adapt(BundleWiring.class).getClassLoader());
		assertEquals(Bundle.ACTIVE, dm.getState());
		assertEquals(Bundle.ACTIVE, refreshBundle.getState());

		// after the refresh: the Runnable is registered (then updated) after the refresh, so it is only
		// seen if the index receives the service events of the new DependencyManager bundle context
		checkRunnableInjected("after the refresh");

		assertTrue("framework errors: " + m_errors, m_errors.isEmpty());
	}

	/**
	 * Registers a Runnable, and checks that the component of the refresh test bundle runs it (the
	 * service is injected), then updates the service properties, and checks that the component
	 * runs it again (the service event is propagated to the dependency).
	 */
	private void checkRunnableInjected(String when) throws InterruptedException {
		Semaphore runs = new Semaphore(0);
		Runnable runnable = runs::release;
		Dictionary<String, Object> props = new Hashtable<>();
		props.put("dm.refresh.test", "true");
		ServiceRegistration<Runnable> registration = m_context.registerService(Runnable.class, runnable, props);
		try {
			assertTrue("Runnable not injected " + when, runs.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));

			// update the service properties: the dependency must be called back
			props.put("version", "2");
			registration.setProperties(props);
			assertTrue("service update not propagated " + when, runs.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS));
		} finally {
			registration.unregister();
		}
	}

	private Bundle getBundle(String symbolicName) {
		for (Bundle b : m_context.getBundles()) {
			if (symbolicName.equals(b.getSymbolicName())) {
				return b;
			}
		}
		throw new IllegalStateException("bundle " + symbolicName + " not found");
	}

}

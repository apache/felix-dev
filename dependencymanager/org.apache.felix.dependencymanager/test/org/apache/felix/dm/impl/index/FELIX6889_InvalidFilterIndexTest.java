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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.FilterIndex;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

/**
 * Tests that a filter index configured in the org.apache.felix.dependencymanager.filterindex
 * property, but whose class can't be loaded, does not break the service registry cache: the
 * cache is an optional optimization, so the invalid index must be ignored.
 */
public class FELIX6889_InvalidFilterIndexTest {

    /**
     * A valid custom index, created by the cache manager using its (String) constructor.
     */
    public static class TestIndex implements FilterIndex {
        public TestIndex(String definition) {
        }

        @Override
        public void open(BundleContext context) {
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isApplicable(String clazz, String filter) {
            return "test.Applicable".equals(clazz);
        }

        @Override
        public List<ServiceReference> getAllServiceReferences(String clazz, String filter) {
            return Collections.emptyList();
        }

        @Override
        public void serviceChanged(ServiceEvent event) {
        }

        @Override
        public void addServiceListener(ServiceListener listener, String filter) {
        }

        @Override
        public void removeServiceListener(ServiceListener listener) {
        }
    }

    @Before
    public void setUp() throws Exception {
        resetManager();
    }

    @After
    public void tearDown() throws Exception {
        resetManager();
    }

    /**
     * The manager is static and only initialized once: reset it between the tests.
     */
    private static void resetManager() throws Exception {
        set("m_cache", null);
        set("m_context", null);
        set("m_activatingThread", null);
    }

    private static void set(String name, Object value) throws Exception {
        Field f = ServiceRegistryCacheManager.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static BundleContext context(String indices) {
        BundleContext context = mock(BundleContext.class);
        when(context.getProperty(DependencyManager.SERVICEREGISTRY_CACHE_INDICES)).thenReturn(indices);
        return context;
    }

    @Test
    public void testUnknownIndexClassIsIgnored() throws Exception {
        ServiceRegistryCacheManager.init(context("no.such.IndexClass:some.definition"));
        ServiceRegistryCacheManager.activated(); // like the Activator, once started
        {
            ServiceRegistryCache cache = ServiceRegistryCacheManager.getCache(null);
            assertNotNull(cache);

            // no null index must be in the cache
            for (FilterIndex index : cache.getFilterIndices()) {
                assertNotNull("a null index has been added to the cache", index);
            }

            // the lookups done on each dependency resolution must not fail
            assertNull(cache.hasFilterIndexFor("test.Applicable", null));
            cache.serviceChangedForFilterIndices(new ServiceEvent(ServiceEvent.REGISTERED, mock(ServiceReference.class)));
        }
    }

    @Test
    public void testIndexesAfterUnknownIndexClassAreStillCreated() throws Exception {
        String indices = "no.such.IndexClass:some.definition;" + TestIndex.class.getName() + ":def";
        ServiceRegistryCacheManager.init(context(indices));
        ServiceRegistryCacheManager.activated(); // like the Activator, once started
        {
            ServiceRegistryCache cache = ServiceRegistryCacheManager.getCache(null);
            assertNotNull(cache);

            assertEquals("only the valid index must be registered", 1, cache.getSize());
            FilterIndex index = cache.hasFilterIndexFor("test.Applicable", null);
            assertNotNull("the index following the invalid one has not been created", index);
            assertTrue(index instanceof TestIndex);
            assertFalse(cache.getFilterIndices().contains(null));
        }
    }
}

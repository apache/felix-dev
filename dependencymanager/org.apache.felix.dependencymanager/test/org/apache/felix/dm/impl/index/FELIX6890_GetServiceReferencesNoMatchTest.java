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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;

import org.apache.felix.dm.FilterIndex;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * BundleContext.getServiceReferences(Class, String) must return an empty collection (never throw)
 * when no service matches, even when the BundleContext is the one returned by the ServiceRegistryCache.
 */
public class FELIX6890_GetServiceReferencesNoMatchTest {

    @Test
    public void noMatchWithoutFilterIndexReturnsEmptyCollection() throws Exception {
        BundleContext context = mock(BundleContext.class);
        // the real framework returns null when no service matches
        when(context.getServiceReferences(anyString(), anyString())).thenReturn(null);

        ServiceRegistryCache cache = new ServiceRegistryCache(context);
        BundleContext interceptor = cache.createBundleContextInterceptor(context);

        Collection<ServiceReference<Runnable>> refs = interceptor.getServiceReferences(Runnable.class, "(x=y)");

        assertNotNull(refs);
        assertTrue(refs.isEmpty());
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void noMatchWithFilterIndexReturnsEmptyCollection() throws Exception {
        BundleContext context = mock(BundleContext.class);

        FilterIndex index = mock(FilterIndex.class);
        when(index.isApplicable(any(), any())).thenReturn(true);
        when(index.getAllServiceReferences(any(), any())).thenReturn(Collections.<ServiceReference>emptyList());

        ServiceRegistryCache cache = new ServiceRegistryCache(context);
        cache.addFilterIndex(index);
        BundleContext interceptor = cache.createBundleContextInterceptor(context);

        Collection<ServiceReference<Runnable>> refs = interceptor.getServiceReferences(Runnable.class, "(x=y)");

        assertNotNull(refs);
        assertTrue(refs.isEmpty());
    }
}

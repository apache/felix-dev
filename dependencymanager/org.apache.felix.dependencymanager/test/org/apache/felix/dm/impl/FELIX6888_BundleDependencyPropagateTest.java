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
package org.apache.felix.dm.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.felix.dm.context.ComponentContext;
import org.apache.felix.dm.context.DependencyContext;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * Tests that BundleDependency.setPropagate(instance, method) is honored.
 */
public class FELIX6888_BundleDependencyPropagateTest {
    public static class Callback {
        final AtomicInteger m_calls = new AtomicInteger();

        public Dictionary<String, Object> props(Bundle bundle) {
            m_calls.incrementAndGet();
            Dictionary<String, Object> d = new Hashtable<>();
            d.put("bundle.name", bundle.getSymbolicName());
            return d;
        }
    }

    private Bundle mockBundle() {
        Bundle bundle = mock(Bundle.class);
        when(bundle.getSymbolicName()).thenReturn("my.bundle");
        Hashtable<String, String> headers = new Hashtable<>();
        headers.put("Import-Package", "foo.bar");
        when(bundle.getHeaders()).thenReturn(headers);
        return bundle;
    }

    private BundleDependencyImpl createDependency(Bundle bundle, Callback callback) {
        BundleDependencyImpl dep = new BundleDependencyImpl();
        dep.setPropagate(callback, "props");
        ComponentContext component = mock(ComponentContext.class);
        when(component.getDependencyEvent(dep)).thenReturn(new BundleEventImpl(bundle, null));
        dep.setComponentContext(component);
        return dep;
    }

    @Test
    public void propagateCallbackIsInvoked() {
        Callback callback = new Callback();
        BundleDependencyImpl dep = createDependency(mockBundle(), callback);

        assertTrue(dep.isPropagated());
        Dictionary<String, Object> props = dep.getProperties();

        assertEquals("callback should be invoked once", 1, callback.m_calls.get());
        assertEquals("my.bundle", props.get("bundle.name"));
        assertNull("manifest headers must not be propagated", props.get("Import-Package"));
    }

    @Test
    public void propagateCallbackIsKeptInCopy() {
        Callback callback = new Callback();
        Bundle bundle = mockBundle();
        BundleDependencyImpl dep = createDependency(bundle, callback);

        DependencyContext copy = dep.createCopy();
        ComponentContext component = mock(ComponentContext.class);
        when(component.getDependencyEvent(copy)).thenReturn(new BundleEventImpl(bundle, null));
        copy.setComponentContext(component);

        Dictionary<String, Object> props = copy.getProperties();

        assertEquals("callback should be invoked once", 1, callback.m_calls.get());
        assertEquals("my.bundle", props.get("bundle.name"));
        assertNull("manifest headers must not be propagated", props.get("Import-Package"));
    }
}

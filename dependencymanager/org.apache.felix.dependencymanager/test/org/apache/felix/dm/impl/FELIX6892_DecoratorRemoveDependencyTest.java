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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.Dependency;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.context.ComponentContext;
import org.apache.felix.dm.context.DependencyContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

/**
 * Checks how a decorator manages the copies of the dependencies added to it after its
 * services have been instantiated: each service must get its own copy, a removed dependency
 * must be removed from each service, and the copies of a service which is no longer
 * instantiated must be forgotten.
 */
public class FELIX6892_DecoratorRemoveDependencyTest {

    static class Decorator extends AbstractDecorator {
        @Override
        public Component createService(Object[] properties) {
            return mock(Component.class);
        }
    }

    private Decorator m_decorator;

    @Before
    public void setUp() {
        ComponentContext decoratorComponent = mock(ComponentContext.class);
        when(decoratorComponent.getDependencyManager()).thenReturn(mock(DependencyManager.class));
        when(decoratorComponent.getExecutor()).thenReturn(Runnable::run);
        m_decorator = new Decorator();
        m_decorator.init(decoratorComponent);
    }

    @Test
    public void testRemoveDependencyRemovesOwnCopyFromEachInstance() {
        Component[] instances = { mock(Component.class), mock(Component.class), mock(Component.class) };
        for (int i = 0; i < instances.length; i++) {
            m_decorator.getServices().put("instance" + i, instances[i]);
        }

        DependencyContext original = mock(DependencyContext.class);
        when(original.createCopy()).thenReturn(
            mock(DependencyContext.class), mock(DependencyContext.class), mock(DependencyContext.class));

        m_decorator.addDependency(original);

        // Each instance must have received its own copy.
        Dependency[] added = new Dependency[instances.length];
        Set<Dependency> distinct = new HashSet<>();
        for (int i = 0; i < instances.length; i++) {
            ArgumentCaptor<Dependency> captor = ArgumentCaptor.forClass(Dependency.class);
            verify(instances[i]).add(captor.capture());
            added[i] = captor.getValue();
            distinct.add(added[i]);
        }
        assertEquals(instances.length, distinct.size());

        m_decorator.removeDependency(original);

        // Each instance must have lost exactly its own copy.
        for (int i = 0; i < instances.length; i++) {
            ArgumentCaptor<Dependency> captor = ArgumentCaptor.forClass(Dependency.class);
            verify(instances[i]).remove(captor.capture());
            List<Dependency> removed = captor.getAllValues();
            assertEquals(1, removed.size());
            assertEquals(added[i], removed.get(0));
        }
        assertTrue(dependencyCopies().isEmpty());
    }

    @Test
    public void testCopiesForgottenWhenAdapteeServiceRemoved() throws Exception {
        ServiceReference<?> ref1 = mock(ServiceReference.class);
        ServiceReference<?> ref2 = mock(ServiceReference.class);
        m_decorator.added(ref1, new Object());
        m_decorator.added(ref2, new Object());
        Component instance2 = m_decorator.getServices().get(ref2);
        DependencyContext original = addCopiedDependency();

        m_decorator.removed(ref1, null);

        assertEquals(Set.of(instance2), dependencyCopies().get(original).keySet());

        m_decorator.removed(ref2, null);

        assertTrue(dependencyCopies().isEmpty());
    }

    @Test
    public void testCopiesForgottenWhenAdapteeBundleRemoved() throws Exception {
        Bundle bundle = mock(Bundle.class);
        m_decorator.added(bundle);
        addCopiedDependency();

        m_decorator.removed(bundle);

        assertTrue(dependencyCopies().isEmpty());
    }

    @Test
    public void testCopiesForgottenWhenAdapteeResourceRemoved() throws Exception {
        URL resource = new URL("file:/resource");
        m_decorator.added(resource);
        addCopiedDependency();

        m_decorator.removed(resource);

        assertTrue(dependencyCopies().isEmpty());
    }

    @Test
    public void testCopiesForgottenWhenFactoryConfigurationDeleted() throws Exception {
        m_decorator.getServices().put("pid", mock(Component.class));
        addCopiedDependency();

        m_decorator.deleted("pid");

        assertTrue(dependencyCopies().isEmpty());
    }

    @Test
    public void testCopiesForgottenWhenDecoratorStopped() throws Exception {
        m_decorator.added(mock(ServiceReference.class), new Object());
        m_decorator.added(mock(ServiceReference.class), new Object());
        addCopiedDependency();

        m_decorator.stop();

        assertTrue(dependencyCopies().isEmpty());
    }

    private DependencyContext addCopiedDependency() {
        DependencyContext original = mock(DependencyContext.class);
        when(original.createCopy()).thenAnswer(invocation -> mock(DependencyContext.class));
        m_decorator.addDependency(original);
        assertEquals(m_decorator.getServices().size(), dependencyCopies().get(original).size());
        return original;
    }

    @SuppressWarnings("unchecked")
    private Map<Dependency, Map<Component, Dependency>> dependencyCopies() {
        try {
            Field field = AbstractDecorator.class.getDeclaredField("m_depclones");
            field.setAccessible(true);
            return (Map<Dependency, Map<Component, Dependency>>) field.get(m_decorator);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}

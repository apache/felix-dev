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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.ComponentExecutorFactory;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.BundleContext;

/**
 * FELIX-6895: when the "org.apache.felix.dependencymanager.parallel" property is set, a component
 * added before any ComponentExecutorFactory is available is kept pending, and is started when a
 * ComponentExecutorFactory is bound. A component removed while the ComponentExecutorFactory is
 * being bound must not stay started once remove() has returned.
 */
public class FELIX6895_RemovedComponentStartedTest {

    private static final String PARALLEL = "org.apache.felix.dependencymanager.parallel";

    public static class MyComponent {
        final AtomicBoolean m_running = new AtomicBoolean();

        void start() {
            m_running.set(true);
        }

        void stop() {
            m_running.set(false);
        }
    }

    /**
     * The component is removed by another thread (like a bundle being stopped) while bind() is
     * processing it: bind() has already taken the component from the pending components, and asks
     * the ComponentExecutorFactory for its executor.
     */
    @Test
    public void testComponentRemovedWhileExecutorFactoryIsBound() throws Exception {
        BundleContext context = mock(BundleContext.class);
        when(context.getProperty(PARALLEL)).thenReturn("*");
        DependencyManager dm = mock(DependencyManager.class);
        when(dm.getBundleContext()).thenReturn(context);

        ComponentScheduler scheduler = new ComponentScheduler();
        MyComponent impl = new MyComponent();
        ComponentImpl component = new ComponentImpl(null, dm, new Logger(null));
        component.setImplementation(impl);

        scheduler.add(component);
        Assert.assertFalse("the component must wait for a ComponentExecutorFactory", impl.m_running.get());

        AtomicReference<Thread> remover = new AtomicReference<>();
        AtomicReference<Throwable> removeFailure = new AtomicReference<>();
        ComponentExecutorFactory factory = new ComponentExecutorFactory() {
            @Override
            public Executor getExecutorFor(Component c) {
                Thread thread = new Thread(() -> {
                    try {
                        scheduler.remove(c);
                    } catch (Throwable e) {
                        removeFailure.set(e);
                    }
                }, "remover");
                remover.set(thread);
                thread.start();
                try {
                    // the removal may have to wait until bind() has completed
                    thread.join(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null; // no thread pool, the component is started synchronously
            }
        };

        scheduler.bind(factory);

        Assert.assertNotNull("bind() did not process the pending component", remover.get());
        remover.get().join(10000);
        Assert.assertFalse("remove() did not complete", remover.get().isAlive());
        Assert.assertNull("remove() failed", removeFailure.get());
        Assert.assertFalse("a removed component is still running", impl.m_running.get());
        Assert.assertFalse("a removed component is still active", component.isActive());
    }
}

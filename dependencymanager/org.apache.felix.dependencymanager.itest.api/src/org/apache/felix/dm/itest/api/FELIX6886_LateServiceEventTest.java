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
package org.apache.felix.dm.itest.api;

import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.context.ComponentContext;
import org.apache.felix.dm.itest.util.TestBase;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

/**
 * FELIX-6886: a service matching an instance-bound dependency shows up while the component instance is being
 * destroyed. The dependency tracker obtains the service and sends an event which is queued in the component executor,
 * and handled after the instance-bound dependency has been stopped and removed from the component.
 * <p>
 * When the dependency is stopped, closing its tracker sends a REMOVED event for every tracked service. These REMOVED
 * events are handled inline (the component executor is run by the current thread) while the dependency is still part
 * of the component, and they release the services. The queued events are then ignored: if handling them fails, the
 * error is logged and the test fails in {@link TestBase#tearDown()}.
 */
public class FELIX6886_LateServiceEventTest extends TestBase {

    public interface A {
    }

    public interface B {
    }

    /**
     * B service factory counting how many times the service is obtained and released.
     */
    static class CountingFactory implements ServiceFactory<B> {
        final AtomicInteger m_gets = new AtomicInteger();
        final AtomicInteger m_ungets = new AtomicInteger();
        volatile ServiceRegistration<?> m_registration;

        @Override
        public B getService(Bundle bundle, ServiceRegistration<B> registration) {
            m_gets.incrementAndGet();
            return new B() {
            };
        }

        @Override
        public void ungetService(Bundle bundle, ServiceRegistration<B> registration, B service) {
            m_ungets.incrementAndGet();
        }

        void unregister() {
            if (m_registration != null) {
                m_registration.unregister();
            }
        }
    }

    /**
     * Component with an instance-bound dependency on B, added from init(). The destroy callback runs the given action,
     * before the instance-bound dependency is stopped.
     */
    public static class Consumer {
        private final Runnable m_onDestroy;

        Consumer(Runnable onDestroy) {
            m_onDestroy = onDestroy;
        }

        void init(Component c) {
            DependencyManager dm = c.getDependencyManager();
            // The swap callback is required: without it, the tracker sends ADDED + REMOVED events instead of a
            // SWAPPED event when an aspect replaces the original service.
            c.add(dm.createServiceDependency().setService(B.class).setRequired(false)
                .setCallbacks("bind", null, "unbind", "swap"));
        }

        void destroy() {
            m_onDestroy.run();
        }

        void bind(B b) {
        }

        void unbind(B b) {
        }

        void swap(B oldB, B newB) {
        }
    }

    // Registration of the A service, required by the consumer.
    private ServiceRegistration<?> m_regA;

    /**
     * A B service is registered while the instance is being destroyed: the tracker sends an ADDED event, which is
     * queued and handled after the dependency has been removed from the component.
     */
    public void testAddedEventQueuedBeforeTrackerClose() {
        CountingFactory b = new CountingFactory();
        Component c = startConsumer(/* onDestroy */ () -> registerFromAnotherThread(b, new Hashtable<>()));
        try {
            destroyInstance(c);
            assertReleased(b);
        } finally {
            getDM().remove(c);
            b.unregister();
        }
    }

    /**
     * A B service is bound to the dependency, and an aspect of this service is registered while the instance is being
     * destroyed: the tracker replaces the original service by the aspect and sends a SWAPPED event, which is queued and
     * handled after the dependency has been removed from the component.
     */
    public void testSwappedEventQueuedBeforeTrackerClose() {
        CountingFactory b = new CountingFactory();
        CountingFactory aspect = new CountingFactory();
        register(b, new Hashtable<>());
        Component c = startConsumer(/* onDestroy */ () -> registerFromAnotherThread(aspect, aspectOf(b)));
        try {
            destroyInstance(c);
            assertReleased(b);
            assertReleased(aspect);
        } finally {
            getDM().remove(c);
            aspect.unregister();
            b.unregister();
        }
    }

    /**
     * Starts a consumer component, and makes it available by registering the A service it requires.
     */
    private Component startConsumer(Runnable onDestroy) {
        DependencyManager m = getDM();
        Component c = m.createComponent()
            .setImplementation(new Consumer(onDestroy))
            .add(m.createServiceDependency().setService(A.class).setRequired(true));
        m.add(c);
        m_regA = context.registerService(A.class.getName(), new A() {
        }, null);
        assertTrue(((ComponentContext) c).isAvailable());
        return c;
    }

    /**
     * Unregisters the A service: the consumer instance is destroyed and its instance-bound dependency is stopped.
     * All events are handled when this method returns: the component executor is run by the current thread, which also
     * handles the events queued by other threads in the meantime.
     */
    private void destroyInstance(Component c) {
        m_regA.unregister();
        assertFalse(((ComponentContext) c).isAvailable());
    }

    private void register(CountingFactory factory, Hashtable<String, Object> properties) {
        factory.m_registration = context.registerService(B.class.getName(), factory, properties);
    }

    private void registerFromAnotherThread(CountingFactory factory, Hashtable<String, Object> properties) {
        Thread t = new Thread(() -> register(factory, properties));
        t.start();
        try {
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while registering the service", e);
        }
        if (t.isAlive()) {
            throw new AssertionError("the service has not been registered");
        }
    }

    /**
     * Returns the service properties of an aspect of the given service, with a higher ranking.
     */
    private static Hashtable<String, Object> aspectOf(CountingFactory original) {
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(DependencyManager.ASPECT, original.m_registration.getReference().getProperty(Constants.SERVICE_ID));
        props.put(Constants.SERVICE_RANKING, 10);
        return props;
    }

    private static void assertReleased(CountingFactory factory) {
        assertTrue("the service has not been obtained by the dependency tracker", factory.m_gets.get() > 0);
        assertEquals("the service obtained by the removed dependency has not been released",
            factory.m_gets.get(), factory.m_ungets.get());
    }
}

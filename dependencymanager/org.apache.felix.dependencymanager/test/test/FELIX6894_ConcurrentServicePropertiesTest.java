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
package test;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.felix.dm.context.DependencyContext;
import org.apache.felix.dm.context.Event;
import org.apache.felix.dm.impl.ComponentImpl;
import org.junit.Assert;
import org.junit.Test;

/**
 * Checks that Component.getServiceProperties() can be called from a foreign thread while
 * a propagated dependency is concurrently removed from the component.
 */
public class FELIX6894_ConcurrentServicePropertiesTest {

    /**
     * A propagated dependency which, like the real service dependency, reads its current
     * dependency events from the component when computing its properties.
     * Once armed, it blocks inside getProperties() until the test has removed it from the component.
     */
    static class PropagatedDependency extends SimpleServiceDependency {
        volatile boolean m_armed;
        final CountDownLatch m_inGetProperties = new CountDownLatch(1);
        final CountDownLatch m_removed = new CountDownLatch(1);
        volatile Event m_event;
        volatile Set<Event> m_events;

        @Override
        public DependencyContext createCopy() {
            return new PropagatedDependency();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <K, V> Dictionary<K, V> getProperties() {
            if (m_armed) {
                m_inGetProperties.countDown();
                try {
                    m_removed.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                m_event = m_component.getDependencyEvent(this);
                m_events = m_component.getDependencyEvents(this);
            }
            return (Dictionary<K, V>) new Hashtable<String, Object>();
        }
    }

    @Test
    public void getServicePropertiesWhileDependencyIsRemoved() throws Exception {
        ComponentImpl c = new ComponentImpl();
        c.setImplementation(new Object());
        PropagatedDependency d = new PropagatedDependency();
        d.setRequired(false);
        d.setPropagate(true);
        c.add(d);
        c.start();
        d.add(new EventImpl(1)); // makes the dependency available, so its properties are propagated
        Assert.assertTrue(d.isAvailable());

        d.m_armed = true;
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                c.getServiceProperties();
            } catch (Throwable t) {
                error.set(t);
            }
        }, "getServiceProperties");
        reader.start();

        // The reader thread is now computing the service properties, and still sees the dependency.
        Assert.assertTrue(d.m_inGetProperties.await(5, TimeUnit.SECONDS));
        c.remove(d);
        d.m_removed.countDown();
        reader.join(5000);

        Assert.assertFalse("reader thread did not complete", reader.isAlive());
        if (error.get() != null) {
            throw new AssertionError("getServiceProperties() failed", error.get());
        }
        Assert.assertNull(d.m_event);
        Assert.assertNotNull(d.m_events);
        Assert.assertTrue(d.m_events.isEmpty());

        c.stop();
    }
}

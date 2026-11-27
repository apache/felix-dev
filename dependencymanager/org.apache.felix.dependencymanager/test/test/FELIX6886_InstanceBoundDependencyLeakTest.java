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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.impl.ComponentImpl;
import org.junit.Assert;
import org.junit.Test;

/**
 * FELIX-6886: dependencies added from the init() callback (instance-bound dependencies) are removed from the
 * component when its instance is destroyed. No reference to their events must be kept afterwards, as it is the
 * case when a dependency is removed with Component.remove(Dependency).
 */
public class FELIX6886_InstanceBoundDependencyLeakTest {
    private static final int CYCLES = 100;

    /**
     * Event which counts how many times it is closed.
     */
    static class ClosableEvent extends EventImpl {
        final AtomicInteger m_closed = new AtomicInteger();

        @Override
        public void close() {
            m_closed.incrementAndGet();
        }
    }

    @Test
    public void testInstanceBoundDependencyEventsAreReleasedOnDestroy() {
        final List<SimpleServiceDependency> instanceBoundDeps = new ArrayList<>();
        final List<ClosableEvent> instanceBoundEvents = new ArrayList<>();
        final SimpleServiceDependency required = new SimpleServiceDependency();
        required.setRequired(true);

        ComponentImpl c = new ComponentImpl();
        c.setImplementation(new Object() {
            @SuppressWarnings("unused")
            void init(Component comp) {
                SimpleServiceDependency dep = new SimpleServiceDependency();
                dep.setRequired(false);
                instanceBoundDeps.add(dep);
                comp.add(dep);
                ClosableEvent event = new ClosableEvent();
                instanceBoundEvents.add(event);
                dep.add(event);
            }
        });
        c.add(required);
        c.start();

        EventImpl requiredEvent = new EventImpl(1);
        for (int i = 0; i < CYCLES; i++) {
            // the required dependency comes up: the instance is created and init() adds a new dependency
            required.add(requiredEvent);
            Assert.assertTrue(c.isAvailable());
            // the required dependency goes away: the instance is destroyed with its instance-bound dependency
            required.remove(requiredEvent);
            Assert.assertFalse(c.isAvailable());
        }

        Assert.assertEquals(CYCLES, instanceBoundDeps.size());
        int leaked = 0;
        for (SimpleServiceDependency dep : instanceBoundDeps) {
            if (c.getDependencyEvents(dep) != null) {
                leaked++;
            }
        }
        int notClosed = 0;
        for (ClosableEvent event : instanceBoundEvents) {
            if (event.m_closed.get() == 0) {
                notClosed++;
            }
        }
        c.stop();
        Assert.assertEquals("events of removed instance-bound dependencies are still referenced", 0, leaked);
        Assert.assertEquals("events of removed instance-bound dependencies are not closed", 0, notClosed);
    }

    @Test
    public void testRemovedEventAfterInstanceBoundDependencyRemoval() {
        // Like a service tracker being closed, the dependency sends a REMOVED event when it is stopped. Since the
        // dependency is stopped from the component executor, this event is handled after the dependency has been
        // removed from the component.
        final List<SimpleServiceDependency> instanceBoundDeps = new ArrayList<>();
        final List<ClosableEvent> instanceBoundEvents = new ArrayList<>();
        final SimpleServiceDependency required = new SimpleServiceDependency();
        required.setRequired(true);

        ComponentImpl c = new ComponentImpl();
        c.setImplementation(new Object() {
            @SuppressWarnings("unused")
            void init(Component comp) {
                final ClosableEvent event = new ClosableEvent();
                SimpleServiceDependency dep = new SimpleServiceDependency() {
                    @Override
                    public void stop() {
                        super.stop();
                        remove(event);
                    }
                };
                dep.setRequired(false);
                instanceBoundDeps.add(dep);
                instanceBoundEvents.add(event);
                comp.add(dep);
                dep.add(event);
            }
        });
        c.add(required);
        c.start();

        EventImpl requiredEvent = new EventImpl(1);
        for (int i = 0; i < 2; i++) {
            required.add(requiredEvent);
            Assert.assertTrue(c.isAvailable());
            required.remove(requiredEvent);
            Assert.assertFalse(c.isAvailable());
        }

        for (int i = 0; i < instanceBoundDeps.size(); i++) {
            Assert.assertNull(c.getDependencyEvents(instanceBoundDeps.get(i)));
            Assert.assertTrue(instanceBoundEvents.get(i).m_closed.get() > 0);
        }
        c.stop();
    }

    @Test
    public void testDependencyEventsAreReleasedOnRemove() {
        // reference behavior: a dependency removed with Component.remove(Dependency) leaves no events behind
        SimpleServiceDependency dep = new SimpleServiceDependency();
        dep.setRequired(false);

        ComponentImpl c = new ComponentImpl();
        c.setImplementation(new Object());
        c.add(dep);
        c.start();
        dep.add(new EventImpl(1));
        Assert.assertNotNull(c.getDependencyEvents(dep));

        c.remove(dep);
        Assert.assertNull(c.getDependencyEvents(dep));
        c.stop();
    }
}

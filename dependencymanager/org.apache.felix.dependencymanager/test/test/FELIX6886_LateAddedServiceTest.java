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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.Logger;
import org.apache.felix.dm.context.ComponentContext;
import org.apache.felix.dm.impl.ComponentImpl;
import org.apache.felix.dm.impl.ServiceEventImpl;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * FELIX-6886: an ADDED event sent by an instance-bound dependency while it is being stopped is handled after the
 * dependency has been removed from the component. The service held by this event must be released.
 * <p>
 * The queued ADDED event is ignored, since the dependency has been removed from the component: handling it must not
 * fail. Like a service tracker, the dependency then sends a REMOVED event for the service it has added (same event
 * object), and this REMOVED event releases the service, even though the dependency has been removed from the
 * component.
 */
public class FELIX6886_LateAddedServiceTest {
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void lateAddedServiceQueuedDuringStopIsReleased() throws Exception {
        BundleContext bundleContext = mock(BundleContext.class);
        ComponentContext componentContext = mock(ComponentContext.class);
        ServiceReference reference = mock(ServiceReference.class);
        when(componentContext.getBundleContext()).thenReturn(bundleContext);
        ServiceEventImpl lateEvent = new ServiceEventImpl(componentContext, reference, new Object());

        // Errors thrown while the component executor handles an event are only logged: collect them.
        List<String> errors = new CopyOnWriteArrayList<>();
        Logger logger = new Logger(null) {
            @Override
            protected void doLog(ServiceReference sr, int level, String msg, Throwable throwable) {
                if (level == LOG_ERROR) {
                    errors.add(msg + (throwable != null ? " (" + throwable + ")" : ""));
                }
                super.doLog(sr, level, msg, throwable);
            }
        };

        SimpleServiceDependency required = new SimpleServiceDependency();
        required.setRequired(true);

        SimpleServiceDependency lateDependency = new SimpleServiceDependency() {
            @Override
            public void stop() {
                super.stop();
                // The late service is added from another thread while the dependency is being stopped: the ADDED
                // event is queued, since the component executor is currently run by the current thread.
                Thread sender = new Thread(() -> add(lateEvent));
                sender.start();
                try {
                    sender.join(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                if (sender.isAlive()) {
                    throw new AssertionError("late ADDED event was not queued");
                }
            }
        };
        lateDependency.setRequired(false);

        ComponentImpl component = new ComponentImpl(null, null, logger);
        component.setImplementation(new Object() {
            @SuppressWarnings("unused")
            void init(Component comp) {
                comp.add(lateDependency);
            }
        });
        component.add(required);
        component.start();

        EventImpl requiredEvent = new EventImpl(1);
        required.add(requiredEvent);
        // The instance is destroyed: the late ADDED event is queued while its dependency is being stopped, and ignored.
        required.remove(requiredEvent);

        // Like a service tracker, the dependency sends a REMOVED event for the service it has added.
        lateDependency.remove(lateEvent);

        Assert.assertEquals("errors logged while handling events", "[]", errors.toString());
        Assert.assertNull(component.getDependencyEvents(lateDependency));
        verify(bundleContext, times(1)).ungetService(reference);
        component.stop();
    }
}

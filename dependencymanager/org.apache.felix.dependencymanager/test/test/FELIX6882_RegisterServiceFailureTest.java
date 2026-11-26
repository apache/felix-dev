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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Dictionary;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.felix.dm.Logger;
import org.apache.felix.dm.impl.ComponentImpl;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * Reproducer for FELIX-6882: when an exception other than IllegalArgumentException is thrown
 * while the service properties are calculated in ComponentImpl.registerService() (here, a propagation
 * callback that fails), the ServiceRegistrationImpl wrapper is never filled in, and stopping the
 * component blocks forever in ServiceRegistrationImpl.ensureRegistration().
 * <p>
 * This test currently FAILS (stop() hangs); it passes once the problem is fixed.
 */
public class FELIX6882_RegisterServiceFailureTest {

    /** A dependency whose propagated properties can't be computed (what ServiceDependencyImpl does when the propagate callback throws). */
    static class FailingPropagateDependency extends SimpleServiceDependency {
        @Override
        public java.util.Dictionary<String, Object> getProperties() {
            throw new IllegalStateException("Could not invoke callback");
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void stopMustNotHangAfterFailedServiceRegistration() throws Exception {
        Bundle bundle = mock(Bundle.class);
        when(bundle.getState()).thenReturn(Bundle.ACTIVE);
        BundleContext ctx = mock(BundleContext.class);
        when(ctx.getBundle()).thenReturn(bundle);
        ServiceRegistration reg = mock(ServiceRegistration.class);
        when(ctx.registerService(anyString(), any(), any(Dictionary.class))).thenReturn(reg);

        ComponentImpl c = new ComponentImpl(ctx, null, new Logger(null));
        c.setImplementation(new Object());
        c.setInterface(Object.class.getName(), null);

        FailingPropagateDependency d = new FailingPropagateDependency();
        d.setRequired(true);
        d.setPropagate(true);
        c.add(d);

        c.start();
        try {
            // The dependency becomes available: the component goes to TRACKING_OPTIONAL and registers its service,
            // which fails in calculateServiceProperties() (the exception is caught and logged by the executor).
            d.add(new EventImpl());
        } catch (RuntimeException expected) {
            // depending on the executor, the failure may or may not propagate to the caller
        }

        final CountDownLatch stopped = new CountDownLatch(1);
        Thread stopper = new Thread(() -> {
            try {
                c.stop();
            } catch (RuntimeException ignored) {
                // an exception is acceptable; hanging is not
            } finally {
                stopped.countDown();
            }
        }, "FELIX6882-stopper");
        stopper.setDaemon(true);
        stopper.start();

        boolean done = stopped.await(5, TimeUnit.SECONDS);
        if (!done) {
            StringBuilder sb = new StringBuilder();
            for (StackTraceElement e : stopper.getStackTrace()) {
                sb.append("\n    at ").append(e);
            }
            Assert.fail("Component.stop() is blocked forever in ServiceRegistrationImpl.ensureRegistration():" + sb);
        }
    }
}

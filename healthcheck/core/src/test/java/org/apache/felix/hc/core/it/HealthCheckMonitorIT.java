/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.felix.hc.core.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.condition.Healthy;
import org.apache.felix.hc.api.condition.SystemReady;
import org.apache.felix.hc.api.condition.Unhealthy;
import org.apache.felix.hc.api.execution.HealthCheckExecutionOptions;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckExecutor;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.condition.Condition;

@RunWith(PaxExam.class)
public class HealthCheckMonitorIT {

    private static final String FACTORY_PID = "org.apache.felix.hc.core.impl.monitor.HealthCheckMonitor";

    @Inject
    private HealthCheckExecutor executor;

    @Inject
    private BundleContext bundleContext;

    @Configuration
    public Option[] config() {
        return U.config();
    }

    class TestHC implements HealthCheck {

        private final Optional<Boolean> result;

        public TestHC(final Optional<Boolean> result) {
            this.result = result;
        }

        @Override
        public Result execute() {
            return new Result(result.orElse(true) ? Result.Status.OK : Result.Status.CRITICAL, "TestHC result: " + result);
        }
    }

    private ServiceRegistration<HealthCheck> registerHc(final String tag, final Optional<Boolean> status) {
        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(HealthCheck.TAGS, tag);

        final ServiceRegistration<HealthCheck> result = bundleContext.registerService(HealthCheck.class, new TestHC(status), props);

        // Wait for HC to be registered
        U.expectHealthChecks(1, executor, tag);

        return result;
    }

    private void registerMonitor(final String tag) {
        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("registerUnhealthyMarkerService", true);
        props.put("tags", tag);
        props.put("intervalInSec", 1);
        final ServiceReference<ConfigurationAdmin> refCA = bundleContext.getServiceReference(ConfigurationAdmin.class);
        try {
            final ConfigurationAdmin ca = bundleContext.getService(refCA);
            final org.osgi.service.cm.Configuration config = ca.getFactoryConfiguration(FACTORY_PID, tag, null);
            config.update(props);
        } catch (IOException e) {
            throw new RuntimeException("Failed to register monitor", e);
        } finally {
            bundleContext.ungetService(refCA);
        }
        // sleep 2 seconds to wait for first monitor run
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void unregisterMonitor(final String tag) {
        final ServiceReference<ConfigurationAdmin> refCA = bundleContext.getServiceReference(ConfigurationAdmin.class);
        try {
            final ConfigurationAdmin ca = bundleContext.getService(refCA);
            final org.osgi.service.cm.Configuration config = ca.getFactoryConfiguration(FACTORY_PID, tag, null);
            config.delete();
        } catch (IOException e) {
            throw new RuntimeException("Failed to register monitor", e);
        } finally {
            bundleContext.ungetService(refCA);
        }
    }

    private void executeHC(final String tag, final Optional<Boolean> status) {
        final HealthCheckSelector selector = HealthCheckSelector.tags(tag);
        final List<HealthCheckExecutionResult> executionResult = executor.execute(selector, new HealthCheckExecutionOptions());
        assertEquals(1, executionResult.size());
        final Result.Status expectedStatus = status.orElse(true) ? Result.Status.OK : Result.Status.CRITICAL;
        assertEquals("Expected " + expectedStatus + " result", expectedStatus, executionResult.get(0).getHealthCheckResult().getStatus());
    }

    private void assertHealthy(final String tag) throws InvalidSyntaxException {
        // healthy service
        final Collection<ServiceReference<Healthy>> colH = bundleContext.getServiceReferences(Healthy.class, "(tag=" + tag + ")");
        assertEquals(1, colH.size());
        final ServiceReference<Healthy> refH = colH.iterator().next();
        final Healthy h = bundleContext.getService(refH);
        assertNotNull(h);
        bundleContext.ungetService(refH);

        // condition
        final Collection<ServiceReference<Condition>> colC = bundleContext.getServiceReferences(Condition.class, "(osgi.condition.id=felix.hc." + tag + ")");
        assertEquals(1, colC.size());
        final ServiceReference<Condition> refC = colC.iterator().next();
        final Condition c = bundleContext.getService(refC);
        assertNotNull(c);
        bundleContext.ungetService(refC);

        // no unhealthy service
        final Collection<ServiceReference<Unhealthy>> colU = bundleContext.getServiceReferences(Unhealthy.class, "(tag=" + tag + ")");
        assertTrue(colU.isEmpty());
    }

    private void assertNotHealthy(final String tag) throws InvalidSyntaxException {
        // no healthy service
        final Collection<ServiceReference<Healthy>> colH = bundleContext.getServiceReferences(Healthy.class, "(tag=" + tag + ")");
        assertTrue(colH.isEmpty());

        // no condition
        final Collection<ServiceReference<Condition>> colC = bundleContext.getServiceReferences(Condition.class, "(osgi.condition.id=felix.hc." + tag + ")");
        assertTrue(colC.isEmpty());

        // unhealthy service
        final Collection<ServiceReference<Unhealthy>> colU = bundleContext.getServiceReferences(Unhealthy.class, "(tag=" + tag + ")");
        assertFalse(colU.isEmpty());
        assertEquals(1, colU.size());
        final ServiceReference<Unhealthy> refU = colU.iterator().next();
        final Unhealthy u = bundleContext.getService(refU);
        assertNotNull(u);
        bundleContext.ungetService(refU);

        // no system ready service
        final Collection<ServiceReference<SystemReady>> colS = bundleContext.getServiceReferences(SystemReady.class, null);
        assertTrue(colS.isEmpty());
    }

    @Test
    public void testHealthy() throws InvalidSyntaxException {
        final String testTag = "testHealthy";
        this.registerMonitor(testTag);

        final ServiceRegistration<HealthCheck> reg = this.registerHc(testTag, Optional.of(true));
        try {
            this.executeHC(testTag, Optional.of(true));

            this.assertHealthy(testTag);

            // no system ready service
            final Collection<ServiceReference<SystemReady>> colS = bundleContext.getServiceReferences(SystemReady.class, null);
            assertTrue(colS.isEmpty());
        } finally {
            reg.unregister();
            this.unregisterMonitor(testTag);
        }
    }

    @Test
    public void testUnhealthy() throws InvalidSyntaxException, IOException {
        final String testTag = "testUnhealthy";
        this.registerMonitor(testTag);

        final ServiceRegistration<HealthCheck> reg = this.registerHc(testTag, Optional.of(false));
        try {
            this.executeHC(testTag, Optional.of(false));

            this.assertNotHealthy(testTag);

        } finally {
            reg.unregister();
            this.unregisterMonitor(testTag);
        }
    }

    @Test
    public void testSystemReady() throws InvalidSyntaxException, IOException {
        final String testTag = "systemready";
        this.registerMonitor(testTag);

        final ServiceRegistration<HealthCheck> reg = this.registerHc(testTag, Optional.of(true));
        try {
            this.executeHC(testTag, Optional.of(true));

            this.assertHealthy(testTag);

            // system ready service
            final Collection<ServiceReference<SystemReady>> colS = bundleContext.getServiceReferences(SystemReady.class, null);
            assertFalse(colS.isEmpty());
            assertEquals(1, colS.size());
            final ServiceReference<SystemReady> refS = colS.iterator().next();
            final SystemReady s = bundleContext.getService(refS);
            assertNotNull(s);
            bundleContext.ungetService(refS);
        } finally {
            reg.unregister();
            this.unregisterMonitor(testTag);
        }
    }
}

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Dictionary;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.felix.dm.Logger;
import org.apache.felix.dm.context.ComponentContext;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

/**
 * Checks that a dependency copy (used for adapters, aspects, factory components and
 * prototype clones) keeps the configuration of the original dependency.
 */
public class FELIX6887_DependencyCopyTest {

    @Test
    public void testServiceDependencyCopyKeepsSwapCallback() {
        ServiceDependencyImpl original = new ServiceDependencyImpl();
        original.setService(Runnable.class).setCallbacks("add", "change", "remove", "swap");

        ServiceDependencyImpl copy = (ServiceDependencyImpl) original.createCopy();

        assertEquals("swap", copy.m_swap);
    }

    @Test
    public void testServiceDependencyCopyKeepsTrackedServiceId() {
        ServiceReference<?> ref = mock(ServiceReference.class);
        when(ref.getProperty(Constants.SERVICE_ID)).thenReturn(42L);

        ServiceDependencyImpl original = new ServiceDependencyImpl();
        original.setService(Runnable.class, ref);

        ServiceDependencyImpl copy = (ServiceDependencyImpl) original.createCopy();

        assertEquals("(service.id=42)", copy.getFilter());
    }

    @Test
    public void testConfigurationDependencyCopyKeepsNeedsInstance() {
        ConfigurationDependencyImpl original = new ConfigurationDependencyImpl();
        original.setPid("my.pid").needsInstance(false);

        ConfigurationDependencyImpl copy = (ConfigurationDependencyImpl) original.createCopy();
        copy.setCallback(null, "updated");

        assertFalse(copy.needsInstance());
    }

    @Test
    public void testConfigurationDependencyCopyKeepsOptional() throws Exception {
        ConfigurationDependencyImpl original = new ConfigurationDependencyImpl(mock(BundleContext.class), mock(Logger.class));
        Service service = new Service();
        original.setCallback(service, "updated").setPid("my.pid").setRequired(false);

        ConfigurationDependencyImpl copy = (ConfigurationDependencyImpl) original.createCopy();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ComponentContext component = mock(ComponentContext.class);
            when(component.getExecutor()).thenReturn(executor);
            when(component.getLogger()).thenReturn(mock(Logger.class));
            copy.setComponentContext(component);
            copy.start();

            // No configuration: an optional dependency provides an empty default configuration.
            copy.updated(null);

            assertNotNull("optional configuration dependency copy did not provide a default configuration", service.m_config);
            assertEquals("my.pid", service.m_config.get(Constants.SERVICE_PID));
        } finally {
            executor.shutdownNow();
        }
    }

    public static class Service {
        volatile Dictionary<?, ?> m_config;

        public void updated(Dictionary<?, ?> config) {
            m_config = config;
        }
    }
}

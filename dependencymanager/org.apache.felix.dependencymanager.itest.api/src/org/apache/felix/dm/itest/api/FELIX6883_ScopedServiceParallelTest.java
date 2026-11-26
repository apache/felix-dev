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

import java.util.ArrayList;
import java.util.List;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.Component.ServiceScope;
import org.apache.felix.dm.DependencyManager;
import org.junit.Assert;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.apache.felix.dm.itest.util.TestBase;

/**
 * Validates that a prototype scoped service obtained from a parallel dependency manager is fully
 * initialized (dependencies injected, start callback invoked) when it is handed to the consumer.
 */
public class FELIX6883_ScopedServiceParallelTest extends TestBase {
    private static final int INSTANCES = 100;

    public FELIX6883_ScopedServiceParallelTest() {
        setParallel();
    }

    public void testPrototypeInstanceIsStartedWhenReturned() throws Exception {
        DependencyManager m = getDM();

        Component dependency = m.createComponent()
            .setInterface(Dependency.class.getName(), null)
            .setImplementation(new Dependency() {});

        Component provider = m.createComponent()
            .setScope(ServiceScope.PROTOTYPE)
            .setAutoConfig(Bundle.class, false)
            .setAutoConfig(ServiceRegistration.class, false)
            .setImplementation(ServiceImpl.class)
            .setInterface(Service.class.getName(), null)
            .add(m.createServiceDependency().setRequired(true).setService(Dependency.class));

        m.add(dependency);
        m.add(provider);

        ServiceReference<Service> ref = waitForReference();
        ServiceObjects<Service> objects = context.getServiceObjects(ref);
        Assert.assertNotNull(objects);

        List<Service> instances = new ArrayList<>();
        int notReady = 0;
        try {
            for (int i = 0; i < INSTANCES; i++) {
                ServiceImpl s = (ServiceImpl) objects.getService();
                instances.add(s);
                // Right after getService() returns, the instance must be injected and started.
                if (s.m_dependency == null || !s.m_started) {
                    notReady++;
                }
            }
        } finally {
            instances.forEach(objects::ungetService);
        }
        Assert.assertEquals("instances returned before injection/start: " + notReady + "/" + INSTANCES, 0, notReady);

        m.clear();
    }

    private ServiceReference<Service> waitForReference() throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        ServiceReference<Service> ref;
        while ((ref = context.getServiceReference(Service.class)) == null) {
            Assert.assertTrue("provider service not registered", System.currentTimeMillis() < end);
            Thread.sleep(10);
        }
        return ref;
    }

    public interface Service {
    }

    public interface Dependency {
    }

    public static class ServiceImpl implements Service {
        volatile Dependency m_dependency; // injected by DM before start()
        volatile boolean m_started;

        @SuppressWarnings("unused")
        void start() {
            m_started = true;
        }
    }
}

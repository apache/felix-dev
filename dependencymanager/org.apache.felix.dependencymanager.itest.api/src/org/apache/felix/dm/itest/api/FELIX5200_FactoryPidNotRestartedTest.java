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

import java.io.IOException;
import java.util.Dictionary;
import java.util.Properties;
import java.util.function.BooleanSupplier;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.itest.util.TestBase;
import org.junit.Assert;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Use case: a factory configuration adapter has a required dependency on a "Required" service.
 * The factory configuration is created, the "Required" service is registered, so a factory configuration adapter INSTANCE1 is then created.
 * Now the "Required" service is unregistered: the factory config adapter INSTANCE1 is then stopped.
 * And when the "Required" service comes up again, then a new factory config adapter INSTANCE2 should be re-created, updated and started.
 * When the factory configuration is removed, the factory config adapter instance is stopped.
 * <p>
 * Config Admin may deliver the configuration to the factory more than once (for example when the factory is
 * registered again), which makes the adapter instance be replaced by an other one. The test does not depend on
 * how often that happens: it follows the instances instead of counting the calls.
 * 
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class FELIX5200_FactoryPidNotRestartedTest extends TestBase {
    private static final int TIMEOUT = 10000;
    
    // the instances of the adapter, in the order they have been started
    static final java.util.List<FactoryPidComponent> m_instances = new java.util.concurrent.CopyOnWriteArrayList<>();
    
    public void testFactoryAdapterNotRestartedTest() throws InterruptedException {
        m_instances.clear();
        DependencyManager dm = getDM();
        
        Component adapter = dm.createFactoryConfigurationAdapterService("factory.pid", "updated", false)
            .setImplementation(FactoryPidComponent.class)
            .add(dm.createServiceDependency().setService(RequiredService.class).setRequired(true));
        
        Component required = dm.createComponent()
            .setImplementation(RequiredService.class)
            .setInterface(RequiredService.class.getName(), null);
        
        Component configurator = dm.createComponent()
            .setImplementation(new Configurator("factory.pid"))
            .add(dm.createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true));
        
        dm.add(configurator);
        dm.add(adapter);
        dm.add(required);        
        // adapter instance1 updated and started
        waitFor("the first adapter instance is started", () -> !m_instances.isEmpty());
        FactoryPidComponent instance1 = m_instances.get(0);
        
        dm.remove(required);        
        // adapter instance1 stopped
        waitFor("the first adapter instance is stopped", () -> instance1.m_stopped);
        int started = m_instances.size();
        
        dm.add(required);
        // a new adapter instance is created, updated and started
        waitFor("a new adapter instance is started", () -> m_instances.size() > started);
        FactoryPidComponent instance2 = m_instances.get(m_instances.size() - 1);
        Assert.assertNotSame(instance1, instance2);
        
        dm.remove(configurator);
        // the configuration is removed: the last adapter instance is stopped
        waitFor("the adapter instance is stopped when the configuration is removed", () -> instance2.m_stopped);
    }
    
    private void waitFor(String what, BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + TIMEOUT;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                Assert.fail("Timed out waiting until " + what);
            }
            Thread.sleep(10);
        }
    }
    
    public static class FactoryPidComponent {
        RequiredService m_required;
        volatile boolean m_stopped;
        
        void updated(Dictionary<String, Object> properties) {
        }
        
        void start() {
            m_instances.add(this);
        }
        
        void stop() {
            m_stopped = true;
        }
    }
    
    public static class RequiredService {
    }
    
    class Configurator {
        private volatile ConfigurationAdmin m_ca;
        Configuration m_conf;
        final String m_pid;
        
        public Configurator(String pid) {
            m_pid = pid;
        }

        public void init() {
            try {
                Assert.assertNotNull(m_ca);
                m_conf = m_ca.createFactoryConfiguration(m_pid, null);
                Properties props = new Properties();
                props.setProperty("some", "properties");
                m_conf.update(toR6Dictionary(props));              
            }
            catch (IOException e) {
                Assert.fail("Could not create configuration: " + e.getMessage());
            }
        }
        
        public void destroy() throws IOException {
            m_conf.delete();
        }
    }
}

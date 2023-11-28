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
package org.apache.felix.scr.impl.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.felix.scr.impl.inject.internal.ComponentMethodsImpl;
import org.apache.felix.scr.impl.logger.BundleLogger;
import org.apache.felix.scr.impl.logger.ComponentLogger;
import org.apache.felix.scr.impl.manager.AbstractComponentManager.State;
import org.apache.felix.scr.impl.metadata.ComponentMetadata;
import org.apache.felix.scr.impl.metadata.DSVersion;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;

public class SingleComponentManagerTest
{
    private ServiceRegistration<?> serviceRegistration = Mockito.mock(
        ServiceRegistration.class);

    private BundleLogger bundleLogger = Mockito.mock(BundleLogger.class);
    private ComponentLogger componentLogger = Mockito.mock(ComponentLogger.class);

    private ComponentActivator componentActivator = new ComponentActivator() {

        @Override
        public void addServiceListener(String serviceFilterString,
            ExtendedServiceListener<ExtendedServiceEvent> listener)
        {
            // TODO Auto-generated method stub

        }

        @Override
        public void removeServiceListener(String serviceFilterString,
            ExtendedServiceListener<ExtendedServiceEvent> listener)
        {
            // TODO Auto-generated method stub

        }

        @Override
        public BundleContext getBundleContext()
        {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean isActive()
        {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public ScrConfiguration getConfiguration()
        {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void schedule(Runnable runnable)
        {
            // TODO Auto-generated method stub

        }

        @Override
        public long registerComponentId(AbstractComponentManager<?> sAbstractComponentManager)
        {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public void unregisterComponentId(AbstractComponentManager<?> sAbstractComponentManager)
        {
            // TODO Auto-generated method stub

        }

        @Override
        public <T> boolean enterCreate(ServiceReference<T> reference)
        {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public <T> void leaveCreate(ServiceReference<T> reference)
        {
            // TODO Auto-generated method stub

        }

        @Override
        public <S, T> void registerMissingDependency(DependencyManager<S, T> dependencyManager,
            ServiceReference<T> serviceReference, int trackingCount)
        {
            // TODO Auto-generated method stub

        }

        @Override
        public <T> void missingServicePresent(ServiceReference<T> serviceReference)
        {
            // TODO Auto-generated method stub

        }

        @Override
        public void enableComponent(String name)
        {
            // TODO Auto-generated method stub
        }

        @Override
        public void disableComponent(String name)
        {
            // TODO Auto-generated method stub
        }

        @Override
        public RegionConfigurationSupport setRegionConfigurationSupport(ServiceReference<ConfigurationAdmin> reference)
        {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void unsetRegionConfigurationSupport(RegionConfigurationSupport rcs)
        {
            // TODO Auto-generated method stub
        }

        @Override
        public void updateChangeCount() {
            // TODO Auto-generated method stub
        }

        @Override
        public BundleLogger getLogger() {
            return bundleLogger;
        }

        @Override
        public ServiceReference<?> getTrueCondition()
        {
            // TODO Auto-generated method stub
            return null;
        }
    };

    @SuppressWarnings("unchecked")
    @Test
    public void testGetService() throws Exception {
        ComponentMetadata cm = new ComponentMetadata(DSVersion.DS13);
        cm.setImplementationClassName("foo.bar.SomeClass");
        cm.validate();

        ComponentContainer<Object> cc = Mockito.mock(ComponentContainer.class);
        Mockito.when(cc.getComponentMetadata()).thenReturn(cm);
        Mockito.when(cc.getActivator()).thenReturn(componentActivator);
        Mockito.when(cc.getLogger()).thenReturn(componentLogger);

        SingleComponentManager<Object> scm = new SingleComponentManager<Object>(cc,
            new ComponentMethodsImpl<>())
        {
            @Override
            boolean getServiceInternal(ServiceRegistration<Object> serviceRegistration)
            {
                return true;
            }
        };

        BundleContext bc = Mockito.mock(BundleContext.class);
        Bundle b = Mockito.mock(Bundle.class);
        Mockito.when(b.getBundleContext()).thenReturn(bc);

        ComponentContextImpl<Object> cci = new ComponentContextImpl<>(scm, b, null);
        Object implObj = new Object();
        cci.setImplementationObject(implObj);
        cci.setImplementationAccessible(true);

        Field f = SingleComponentManager.class.getDeclaredField("m_componentContext");
        f.setAccessible(true);
        f.set(scm, cci);

        scm.setState(scm.getState(), State.unsatisfiedReference);
        assertSame(implObj,
            scm.getService(b, (ServiceRegistration<Object>) serviceRegistration));

        Field u = SingleComponentManager.class.getDeclaredField("m_useCount");
        u.setAccessible(true);
        AtomicInteger use = (AtomicInteger) u.get(scm);
        assertEquals(1, use.get());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetServiceWithNullComponentContext() throws Exception
    {
        ComponentMetadata cm = new ComponentMetadata(DSVersion.DS13);
        cm.setImplementationClassName("foo.bar.SomeClass");
        cm.validate();

        ComponentContainer<Object> cc = Mockito.mock(ComponentContainer.class);
        Mockito.when(cc.getComponentMetadata()).thenReturn(cm);
        Mockito.when(cc.getActivator()).thenReturn(componentActivator);
        Mockito.when(cc.getLogger()).thenReturn(componentLogger);

        SingleComponentManager<Object> scm = new SingleComponentManager<Object>(cc,
            new ComponentMethodsImpl<>())
        {
            @Override
            boolean getServiceInternal(ServiceRegistration<Object> serviceRegistration)
            {
                return true;
            }
        };
        BundleContext bc = Mockito.mock(BundleContext.class);
        Bundle b = Mockito.mock(Bundle.class);
        Mockito.when(b.getBundleContext()).thenReturn(bc);

        scm.setState(scm.getState(), State.unsatisfiedReference);
        assertNull("m_componentContext is null, this should not cause an NPE",
            scm.getService(b, (ServiceRegistration<Object>) serviceRegistration));

        Field u = SingleComponentManager.class.getDeclaredField("m_useCount");
        u.setAccessible(true);
        AtomicInteger use = (AtomicInteger) u.get(scm);
        assertEquals(0, use.get());
    }
}

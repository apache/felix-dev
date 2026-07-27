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
package org.apache.felix.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.felix.framework.ServiceRegistrationImpl.ServiceReferenceImpl;
import org.apache.felix.framework.ServiceRegistry.ServiceHolder;
import org.apache.felix.framework.ServiceRegistry.UsageCount;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.PrototypeServiceFactory;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.EventHook;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.hooks.service.ListenerHook;

class ServiceRegistryTest
{
    @Test
    void registerEventHookService()
    {
        Bundle b = mock(Bundle.class);
        BundleContext c = mock(BundleContext.class);
        Mockito.when(c.getBundle()).thenReturn(b);

        ServiceRegistry sr = new ServiceRegistry(new Logger(), null);
        EventHook hook = new EventHook()
        {
            @Override
            public void event(ServiceEvent event, Collection<BundleContext> contexts)
            {
            }
        };

        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Precondition failed").isEqualTo(0);
        ServiceRegistration<?> reg = sr.registerService(c.getBundle(), new String [] {EventHook.class.getName()}, hook, new Hashtable());
        assertThat(sr.getHookRegistry().getHooks(EventHook.class)).hasSize(1);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).iterator().next() instanceof ServiceReference).isTrue();
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).iterator().next()).isSameAs(reg.getReference());
        assertThat(((ServiceRegistrationImpl) reg).getService()).isSameAs(hook);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Postcondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Postcondition failed").isEqualTo(0);

        sr.unregisterService(b, reg);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
    }

    @Test
    void registerEventHookServiceFactory()
    {
        Bundle b = mock(Bundle.class);
        BundleContext c = mock(BundleContext.class);
        Mockito.when(c.getBundle()).thenReturn(b);

        ServiceRegistry sr = new ServiceRegistry(new Logger(), null);
        ServiceFactory<?> sf = mock(ServiceFactory.class);

        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Precondition failed").isEqualTo(0);
        ServiceRegistration<?> reg = sr.registerService(c.getBundle(), new String [] {EventHook.class.getName()}, sf, new Hashtable());
        assertThat(sr.getHookRegistry().getHooks(EventHook.class)).hasSize(1);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).iterator().next()).isSameAs(reg.getReference());
        assertThat(((ServiceRegistrationImpl<?>) reg).getService()).isSameAs(sf);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Postcondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Postcondition failed").isEqualTo(0);

        sr.unregisterService(b, reg);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
    }

    @Test
    void registerFindHookService()
    {
        Bundle b = mock(Bundle.class);
        BundleContext c = mock(BundleContext.class);
        Mockito.when(c.getBundle()).thenReturn(b);



        ServiceRegistry sr = new ServiceRegistry(new Logger(), null);
        FindHook hook = new FindHook()
        {
            @Override
            public void find(BundleContext context, String name, String filter,
                boolean allServices, Collection<ServiceReference<?>> references)
            {
            }
        };

        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Precondition failed").isEqualTo(0);
        ServiceRegistration<?> reg = sr.registerService(c.getBundle(), new String [] {FindHook.class.getName()}, hook, new Hashtable());
        assertThat(sr.getHookRegistry().getHooks(FindHook.class)).hasSize(1);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).iterator().next()).isSameAs(reg.getReference());
        assertThat(((ServiceRegistrationImpl<?>) reg).getService()).isSameAs(hook);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Postcondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Postcondition failed").isEqualTo(0);

        sr.unregisterService(b, reg);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
    }

    @Test
    void registerFindHookServiceFactory()
    {
        Bundle b = mock(Bundle.class);
        BundleContext c = mock(BundleContext.class);
        Mockito.when(c.getBundle()).thenReturn(b);

        ServiceRegistry sr = new ServiceRegistry(new Logger(), null);
        ServiceFactory<?> sf = mock(ServiceFactory.class);

        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Precondition failed").isEqualTo(0);
        ServiceRegistration<?> reg = sr.registerService(c.getBundle(), new String [] {FindHook.class.getName()}, sf, new Hashtable());
        assertThat(sr.getHookRegistry().getHooks(FindHook.class)).hasSize(1);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).iterator().next()).isSameAs(reg.getReference());
        assertThat(((ServiceRegistrationImpl<?>) reg).getService()).isSameAs(sf);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Postcondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Postcondition failed").isEqualTo(0);

        sr.unregisterService(b, reg);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
    }

    @Test
    void registerListenerHookService()
    {
        Bundle b = mock(Bundle.class);
        BundleContext c = mock(BundleContext.class);
        Mockito.when(c.getBundle()).thenReturn(b);

        ServiceRegistry sr = new ServiceRegistry(new Logger(), null);

        ListenerHook hook = new ListenerHook()
        {
            @Override
            public void added(Collection<ListenerInfo> listeners)
            {
            }

            @Override
            public void removed(Collection<ListenerInfo> listener)
            {
            }
        };

        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Precondition failed").isEqualTo(0);
        ServiceRegistration<?> reg = sr.registerService(c.getBundle(), new String [] {ListenerHook.class.getName()}, hook, new Hashtable());
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class)).hasSize(1);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).iterator().next()).isSameAs(reg.getReference());
        assertThat(((ServiceRegistrationImpl<?>) reg).getService()).isSameAs(hook);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Postcondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Postcondition failed").isEqualTo(0);

        sr.unregisterService(b, reg);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
    }

    @Test
    void registerListenerHookServiceFactory()
    {
        Bundle b = mock(Bundle.class);
        BundleContext c = mock(BundleContext.class);
        Mockito.when(c.getBundle()).thenReturn(b);

        ServiceRegistry sr = new ServiceRegistry(new Logger(), null);
        ServiceFactory<?> sf = mock(ServiceFactory.class);

        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Precondition failed").isEqualTo(0);
        ServiceRegistration<?> reg = sr.registerService(c.getBundle(), new String [] {ListenerHook.class.getName()}, sf, new Hashtable());
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class)).hasSize(1);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).iterator().next()).isSameAs(reg.getReference());
        assertThat(((ServiceRegistrationImpl<?>) reg).getService()).isSameAs(sf);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Postcondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Postcondition failed").isEqualTo(0);

        sr.unregisterService(b, reg);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Should be no hooks left after unregistration").isEqualTo(0);
    }

    public void testRegisterCombinedService()
    {
        Bundle b = mock(Bundle.class);
        BundleContext c = mock(BundleContext.class);
        Mockito.when(c.getBundle()).thenReturn(b);


        ServiceRegistry sr = new ServiceRegistry(new Logger(), null);
        class CombinedService implements ListenerHook, FindHook, EventHook, Runnable
        {
            @Override
            public void added(Collection<ListenerInfo> listeners)
            {
            }

            @Override
            public void removed(Collection<ListenerInfo> listener)
            {
            }

            @Override
            public void find(BundleContext context, String name, String filter,
                    boolean allServices, Collection<ServiceReference<?>> references)
            {
            }

            @Override
            public void event(ServiceEvent event, Collection<BundleContext> contexts)
            {
            }

            @Override
            public void run()
            {
            }

        }
        CombinedService hook = new CombinedService();

        assertThat(sr.getHookRegistry().getHooks(EventHook.class)).as("Precondition failed").hasSize(0);
        assertThat( sr.getHookRegistry().getHooks(FindHook.class)).as("Precondition failed").hasSize(0);
        assertThat( sr.getHookRegistry().getHooks(ListenerHook.class)).as("Precondition failed").hasSize(0);
        ServiceRegistration<?> reg = sr.registerService(c.getBundle(), new String [] {
            Runnable.class.getName(),
            ListenerHook.class.getName(),
            FindHook.class.getName(),
            EventHook.class.getName()}, hook, new Hashtable<>());
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class)).hasSize(1);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).iterator().next()).isSameAs(reg.getReference());
        assertThat(((ServiceRegistrationImpl<?>) reg).getService()).isSameAs(hook);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class)).hasSize(1);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).iterator().next()).isSameAs(reg.getReference());
        assertThat(((ServiceRegistrationImpl<?>) reg).getService()).isSameAs(hook);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class)).hasSize(1);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).iterator().next()).isSameAs(reg.getReference());
        assertThat(((ServiceRegistrationImpl<?>) reg).getService()).isSameAs(hook);

        sr.unregisterService(b, reg);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class)).as("Should be no hooks left after unregistration").isEmpty();
        assertThat(sr.getHookRegistry().getHooks(FindHook.class)).as("Should be no hooks left after unregistration").isEmpty();
        assertThat( sr.getHookRegistry().getHooks(ListenerHook.class)).as("Should be no hooks left after unregistration").isEmpty();
    }

    @Test
    void registerPlainService()
    {
        Bundle b = mock(Bundle.class);
        BundleContext c = mock(BundleContext.class);
        Mockito.when(c.getBundle()).thenReturn(b);

        ServiceRegistry sr = new ServiceRegistry(new Logger(), null);
        String svcObj = "hello";
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Precondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Precondition failed").isEqualTo(0);
        ServiceRegistration<?> reg = sr.registerService(c.getBundle(), new String [] {String.class.getName()}, svcObj, new Hashtable());
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Postcondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Postcondition failed").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Postcondition failed").isEqualTo(0);

        sr.unregisterService(b, reg);
        assertThat(sr.getHookRegistry().getHooks(EventHook.class).size()).as("Unregistration should have no effect").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(FindHook.class).size()).as("Unregistration should have no effect").isEqualTo(0);
        assertThat(sr.getHookRegistry().getHooks(ListenerHook.class).size()).as("Unregistration should have no effect").isEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getService()
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        String svc = "foo";

        Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<?> reg = Mockito.mock(ServiceRegistrationImpl.class);
        Mockito.when(reg.isValid()).thenReturn(true);
        Mockito.when(reg.getService(b)).thenReturn(svc);

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        assertThat(sr.getService(b, ref, false)).isSameAs(svc);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getServiceHolderAwait() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        final String svc = "test";

        Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<?> reg = Mockito.mock(ServiceRegistrationImpl.class);
        Mockito.when(reg.isValid()).thenReturn(true);

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        UsageCount uc = sr.obtainUsageCount(b, ref, null, false);

        // Set an empty Service Holder so we can test that it waits.
        final ServiceHolder sh = new ServiceHolder();
        uc.m_svcHolderRef.set(sh);

        final StringBuilder sb = new StringBuilder();
        final AtomicBoolean threadException = new AtomicBoolean(false);
        Thread t = new Thread() {
            @Override
            public void run()
            {
                try { Thread.sleep(250); } catch (InterruptedException e) {}
                sh.m_service = svc;
                if (sb.length() > 0)
                {
                    // Should not have put anything in SB until countDown() was called...
                    threadException.set(true);
                }
                sh.m_latch.countDown();
            }
        };
        assertThat(t.isInterrupted()).isFalse();
        t.start();

        Object actualSvc = sr.getService(b, ref, false);
        sb.append(actualSvc);

        t.join();
        assertFalse(threadException.get(),
                "This thread did not wait until the latch was count down");

        assertThat(actualSvc).isSameAs(svc);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getServicePrototype() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        String svc = "xyz";

        Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<?> reg = Mockito.mock(ServiceRegistrationImpl.class);
        Mockito.when(reg.isValid()).thenReturn(true);
        Mockito.when(reg.getService(b)).thenReturn(svc);

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        assertThat(sr.getService(b, ref, true)).isSameAs(svc);

        final ConcurrentMap<Bundle, UsageCount[]> inUseMap =
                (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");
        UsageCount[] uca = inUseMap.get(b);
        assertThat(uca.length).isEqualTo(1);
        assertThat(uca[0].m_serviceObjectsCount.get()).isEqualTo(1);

        sr.getService(b, ref, true);
        assertThat(uca[0].m_serviceObjectsCount.get()).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getServiceThreadMarking() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<?> reg = Mockito.mock(ServiceRegistrationImpl.class);

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        sr.getService(b, ref, false);

        InOrder inOrder = Mockito.inOrder(reg);
        inOrder.verify(reg, Mockito.times(1)).currentThreadMarked();
        inOrder.verify(reg, Mockito.times(1)).markCurrentThread();
        inOrder.verify(reg, Mockito.times(1)).unmarkCurrentThread();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getServiceThreadMarking2() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        String svc = "bar";

        Bundle b = Mockito.mock(Bundle.class);

        ServiceRegistrationImpl<?> reg = (ServiceRegistrationImpl) sr.registerService(
                b, new String [] {String.class.getName()}, svc, null);

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        reg.markCurrentThread();
        try
        {
            sr.getService(b, ref, false);
            fail("Should have thrown an exception to signal reentrant behaviour");
        }
        catch (ServiceException se)
        {
            assertThat(se.getType()).isEqualTo(ServiceException.FACTORY_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void ungetService() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<?> reg = Mockito.mock(ServiceRegistrationImpl.class);

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        final ConcurrentMap<Bundle, UsageCount[]> inUseMap =
                (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        UsageCount uc = new UsageCount(ref, false);
        uc.m_svcHolderRef.set(new ServiceHolder());

        inUseMap.put(b, new UsageCount[] {uc});

        assertThat(sr.ungetService(b, ref, null)).isFalse();
        assertThat(uc.m_svcHolderRef.get()).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void ungetService2() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<Object> reg = Mockito.mock(ServiceRegistrationImpl.class);

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        final ConcurrentMap<Bundle, UsageCount[]> inUseMap =
                (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        UsageCount uc = new UsageCount(ref, false);
        ServiceHolder sh = new ServiceHolder();
        Object svc = new Object();
        sh.m_service = svc;
        uc.m_svcHolderRef.set(sh);
        uc.m_count.incrementAndGet();

        Mockito.verify(reg, Mockito.never()).
            ungetService(Mockito.isA(Bundle.class), Mockito.any());
        inUseMap.put(b, new UsageCount[] {uc});

        assertThat(sr.ungetService(b, ref, null)).isTrue();
        assertThat(uc.m_svcHolderRef.get()).isNull();

        Mockito.verify(reg).
            ungetService(Mockito.isA(Bundle.class), Mockito.eq(svc));
    }

    @SuppressWarnings("unchecked")
    @Test
    void ungetService3() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<?> reg = Mockito.mock(ServiceRegistrationImpl.class);
        Mockito.when(reg.isValid()).thenReturn(true);

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        final ConcurrentMap<Bundle, UsageCount[]> inUseMap =
                (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        UsageCount uc = new UsageCount(ref, false);
        uc.m_svcHolderRef.set(new ServiceHolder());
        uc.m_count.set(2);

        inUseMap.put(b, new UsageCount[] {uc});

        assertThat(sr.ungetService(b, ref, null)).isTrue();
        assertThat(uc.m_svcHolderRef.get()).isNotNull();
        assertThat(inUseMap.get(b)).isNotNull();

        Mockito.verify(reg, Mockito.never()).
            ungetService(Mockito.isA(Bundle.class), Mockito.any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void ungetService4() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<?> reg = Mockito.mock(ServiceRegistrationImpl.class);
        Mockito.when(reg.isValid()).thenReturn(false);

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        final ConcurrentMap<Bundle, UsageCount[]> inUseMap =
                (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        UsageCount uc = new UsageCount(ref, false);
        uc.m_svcHolderRef.set(new ServiceHolder());
        uc.m_count.set(2);

        inUseMap.put(b, new UsageCount[] {uc});

        assertThat(sr.ungetService(b, ref, null)).isTrue();
        assertThat(uc.m_svcHolderRef.get()).isNull();

        Mockito.verify(reg, Mockito.never()).
            ungetService(Mockito.isA(Bundle.class), Mockito.any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void ungetService5() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<Object> reg = Mockito.mock(ServiceRegistrationImpl.class);
        Mockito.doThrow(new RuntimeException("Test!")).when(reg).
            ungetService(Mockito.isA(Bundle.class), Mockito.any());

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        final ConcurrentMap<Bundle, UsageCount[]> inUseMap =
                (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        String svc = "myService";
        UsageCount uc = new UsageCount(ref, false);
        ServiceHolder sh = new ServiceHolder();
        sh.m_service = svc;
        sh.m_latch.countDown();
        uc.m_svcHolderRef.set(sh);
        uc.m_count.set(1);

        inUseMap.put(b, new UsageCount[] {uc});

        try
        {
            assertThat(sr.ungetService(b, ref, null)).isTrue();
            fail("Should have propagated the runtime exception");
        }
        catch (RuntimeException re)
        {
            assertThat(re.getMessage()).isEqualTo("Test!");
        }
        assertThat(uc.m_svcHolderRef.get()).isNull();

        Mockito.verify(reg).ungetService(b, svc);
    }

    @Test
    void ungetServiceThreadMarking()
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<?> reg = Mockito.mock(ServiceRegistrationImpl.class);

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        assertFalse(sr.ungetService(b, ref, null),
                "There is no usage count, so this method should return false");

        InOrder inOrder = Mockito.inOrder(reg);
        inOrder.verify(reg, Mockito.times(1)).currentThreadMarked();
        inOrder.verify(reg, Mockito.times(1)).markCurrentThread();
        inOrder.verify(reg, Mockito.times(1)).unmarkCurrentThread();
    }

    @Test
    void ungetServiceThreadMarking2()
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<?> reg = Mockito.mock(ServiceRegistrationImpl.class);
        Mockito.when(reg.currentThreadMarked()).thenReturn(true);

        ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        try
        {
            sr.ungetService(b, ref, null);
            fail("The thread should be observed as marked and hence throw an exception");
        }
        catch (IllegalStateException ise)
        {
            // good
        }
    }

    @Test
    void obtainUsageCount() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        @SuppressWarnings("unchecked")
        ConcurrentMap<Bundle, UsageCount[]> inUseMap = (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        assertThat(inUseMap.size()).as("Precondition").isEqualTo(0);

        Bundle b = Mockito.mock(Bundle.class);
        ServiceReference<?> ref = Mockito.mock(ServiceReference.class);
        UsageCount uc = sr.obtainUsageCount(b, ref, null, false);
        assertThat(inUseMap).hasSize(1);
        assertThat(inUseMap.get(b).length).isEqualTo(1);
        assertThat(inUseMap.get(b)[0]).isSameAs(uc);
        assertThat(uc.m_ref).isSameAs(ref);
        assertThat(uc.m_prototype).isFalse();

        UsageCount uc2 = sr.obtainUsageCount(b, ref, null, false);
        assertThat(uc2).isSameAs(uc);

        ServiceReference<?> ref2 = Mockito.mock(ServiceReference.class);
        UsageCount uc3 = sr.obtainUsageCount(b, ref2, null, false);
        assertNotSame(uc3, uc2);
        assertThat(uc3.m_ref).isSameAs(ref2);
    }

    @Test
    void obtainUsageCountPrototype() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        @SuppressWarnings("unchecked")
        ConcurrentMap<Bundle, UsageCount[]> inUseMap = (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        Bundle b = Mockito.mock(Bundle.class);
        ServiceReference<?> ref = Mockito.mock(ServiceReference.class);
        UsageCount uc = sr.obtainUsageCount(b, ref, null, true);
        assertThat(inUseMap).hasSize(1);
        assertThat(inUseMap.values().iterator().next().length).isEqualTo(1);

        ServiceReference<?> ref2 = Mockito.mock(ServiceReference.class);
        UsageCount uc2 = sr.obtainUsageCount(b, ref2, null, true);
        assertThat(inUseMap).hasSize(1);
        assertThat(inUseMap.values().iterator().next().length).isEqualTo(2);
        List<UsageCount> ucl = Arrays.asList(inUseMap.get(b));
        assertThat(ucl).contains(uc);
        assertThat(ucl).contains(uc2);
    }

    @Test
    void obtainUsageCountPrototypeUnknownLookup() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        @SuppressWarnings("unchecked")
        ConcurrentMap<Bundle, UsageCount[]> inUseMap = (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        Bundle b = Mockito.mock(Bundle.class);
        ServiceReference<?> ref = Mockito.mock(ServiceReference.class);

        UsageCount uc = new UsageCount(ref, true);
        ServiceHolder sh = new ServiceHolder();
        String svc = "foobar";
        sh.m_service = svc;
        uc.m_svcHolderRef.set(sh);
        inUseMap.put(b, new UsageCount[] {uc});

        assertThat(sr.obtainUsageCount(b, Mockito.mock(ServiceReference.class), null, null)).isNull();

        UsageCount uc2 = sr.obtainUsageCount(b, ref, svc, null);
        assertThat(uc2).isSameAs(uc);
    }

    @Test
    void obtainUsageCountPrototypeUnknownLookup2() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        @SuppressWarnings("unchecked")
        ConcurrentMap<Bundle, UsageCount[]> inUseMap = (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        Bundle b = Mockito.mock(Bundle.class);
        ServiceReference<?> ref = Mockito.mock(ServiceReference.class);

        UsageCount uc = new UsageCount(ref, false);
        inUseMap.put(b, new UsageCount[] {uc});

        assertThat(sr.obtainUsageCount(b, Mockito.mock(ServiceReference.class), null, null)).isNull();

        UsageCount uc2 = sr.obtainUsageCount(b, ref, null, null);
        assertThat(uc2).isSameAs(uc);
    }

    @SuppressWarnings("unchecked")
    @Test
    void obtainUsageCountRetry1() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        final Bundle b = Mockito.mock(Bundle.class);

        final ConcurrentMap<Bundle, UsageCount[]> orgInUseMap =
            (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        ConcurrentMap<Bundle, UsageCount[]> inUseMap =
            Mockito.mock(ConcurrentMap.class, AdditionalAnswers.delegatesTo(orgInUseMap));
        Mockito.doAnswer(new Answer<UsageCount[]>()
            {
                @Override
                public UsageCount[] answer(InvocationOnMock invocation) throws Throwable
                {
                    // This mimicks another thread putting another UsageCount in concurrently
                    // The putIfAbsent() will fail and it has to retry
                    UsageCount uc = new UsageCount(Mockito.mock(ServiceReference.class), false);
                    UsageCount[] uca = new UsageCount[] {uc};
                    orgInUseMap.put(b, uca);
                    return uca;
                }
            }).when(inUseMap).putIfAbsent(Mockito.any(Bundle.class), Mockito.any(UsageCount[].class));
        setPrivateField(sr, "m_inUseMap", inUseMap);

        ServiceReference<?> ref = Mockito.mock(ServiceReference.class);

        assertThat(orgInUseMap).hasSize(0);
        UsageCount uc = sr.obtainUsageCount(b, ref, null, false);
        assertThat(orgInUseMap).hasSize(1);
        assertThat(orgInUseMap.get(b).length).isEqualTo(2);
        assertThat(uc.m_ref).isSameAs(ref);
        assertThat(uc.m_prototype).isFalse();
        List<UsageCount> l = new ArrayList<>(Arrays.asList(orgInUseMap.get(b)));
        l.remove(uc);
        assertThat(l.size()).as("There should be one UsageCount left").isEqualTo(1);
        assertNotSame(ref, l.get(0).m_ref);
    }

    @SuppressWarnings("unchecked")
    @Test
    void obtainUsageCountRetry2() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        final Bundle b = Mockito.mock(Bundle.class);

        final ConcurrentMap<Bundle, UsageCount[]> orgInUseMap =
            (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");
        orgInUseMap.put(b, new UsageCount[] {new UsageCount(Mockito.mock(ServiceReference.class), false)});

        ConcurrentMap<Bundle, UsageCount[]> inUseMap =
            Mockito.mock(ConcurrentMap.class, AdditionalAnswers.delegatesTo(orgInUseMap));
        Mockito.doAnswer(new Answer<Boolean>()
            {
                @Override
                public Boolean answer(InvocationOnMock invocation) throws Throwable
                {
                    orgInUseMap.remove(b);
                    return false;
                }
            }).when(inUseMap).replace(Mockito.any(Bundle.class),
                    Mockito.any(UsageCount[].class), Mockito.any(UsageCount[].class));
        setPrivateField(sr, "m_inUseMap", inUseMap);

        ServiceReference<?> ref = Mockito.mock(ServiceReference.class);

        assertThat(inUseMap.size()).as("Precondition").isEqualTo(1);
        assertThat(inUseMap.values().iterator().next().length).as("Precondition").isEqualTo(1);
        assertNotSame(ref, inUseMap.get(b)[0].m_ref, "Precondition");
        sr.obtainUsageCount(b, ref, null, false);
        assertThat(inUseMap).hasSize(1);
        assertThat(inUseMap.values().iterator().next().length).isEqualTo(1);
        assertThat(inUseMap.get(b)[0].m_ref).as("The old usage count should have been removed by the mock and this one should have been added").isSameAs(ref);
    }

    @Test
    void flushUsageCount() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        @SuppressWarnings("unchecked")
        ConcurrentMap<Bundle, UsageCount[]> inUseMap = (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        Bundle b = Mockito.mock(Bundle.class);

        ServiceReference<?> ref = Mockito.mock(ServiceReference.class);
        UsageCount uc = new UsageCount(ref, false);
        ServiceReference<?> ref2 = Mockito.mock(ServiceReference.class);
        UsageCount uc2 = new UsageCount(ref2, true);

        inUseMap.put(b, new UsageCount[] {uc, uc2});

        assertThat(inUseMap.size()).as("Precondition").isEqualTo(1);
        assertThat(inUseMap.values().iterator().next().length).as("Precondition").isEqualTo(2);

        sr.flushUsageCount(b, ref, uc);
        assertThat(inUseMap).hasSize(1);
        assertThat(inUseMap.values().iterator().next().length).isEqualTo(1);
        assertThat(inUseMap.values().iterator().next()[0]).isSameAs(uc2);

        sr.flushUsageCount(b, ref2, uc2);
        assertThat(inUseMap).hasSize(0);
    }

    @Test
    void flushUsageCountNullRef() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        @SuppressWarnings("unchecked")
        ConcurrentMap<Bundle, UsageCount[]> inUseMap = (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        Bundle b = Mockito.mock(Bundle.class);
        Bundle b2 = Mockito.mock(Bundle.class);

        ServiceReference<?> ref = Mockito.mock(ServiceReference.class);
        UsageCount uc = new UsageCount(ref, false);
        ServiceReference<?> ref2 = Mockito.mock(ServiceReference.class);
        UsageCount uc2 = new UsageCount(ref2, true);
        ServiceReference<?> ref3 = Mockito.mock(ServiceReference.class);
        UsageCount uc3 = new UsageCount(ref3, true);

        inUseMap.put(b, new UsageCount[] {uc2, uc});
        inUseMap.put(b2, new UsageCount[] {uc3});

        assertThat(inUseMap.size()).as("Precondition").isEqualTo(2);

        sr.flushUsageCount(b, null, uc);
        assertThat(inUseMap).hasSize(2);

        sr.flushUsageCount(b, null, uc2);
        assertThat(inUseMap).hasSize(1);
    }

    @Test
    void flushUsageCountAlienObject() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        @SuppressWarnings("unchecked")
        ConcurrentMap<Bundle, UsageCount[]> inUseMap = (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        Bundle b = Mockito.mock(Bundle.class);

        ServiceReference<?> ref = Mockito.mock(ServiceReference.class);
        UsageCount uc = new UsageCount(ref, false);

        inUseMap.put(b, new UsageCount[] {uc});
        assertThat(inUseMap.size()).as("Precondition").isEqualTo(1);
        assertThat(inUseMap.values().iterator().next().length).as("Precondition").isEqualTo(1);

        UsageCount uc2 = new UsageCount(Mockito.mock(ServiceReference.class), false);
        sr.flushUsageCount(b, ref, uc2);
        assertThat(inUseMap.size()).as("Should be no changes").isEqualTo(1);
        assertThat(inUseMap.values().iterator().next().length).as("Should be no changes").isEqualTo(1);
    }

    @Test
    void flushUsageCountNull() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        @SuppressWarnings("unchecked")
        ConcurrentMap<Bundle, UsageCount[]> inUseMap = (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        Bundle b = Mockito.mock(Bundle.class);
        Bundle b2 = Mockito.mock(Bundle.class);

        ServiceReference<?> ref = Mockito.mock(ServiceReference.class);
        UsageCount uc = new UsageCount(ref, false);
        ServiceReference<?> ref2 = Mockito.mock(ServiceReference.class);
        UsageCount uc2 = new UsageCount(ref2, true);
        ServiceReference<?> ref3 = Mockito.mock(ServiceReference.class);
        UsageCount uc3 = new UsageCount(ref3, true);

        inUseMap.put(b, new UsageCount[] {uc2, uc});
        inUseMap.put(b2, new UsageCount[] {uc3});

        assertThat(inUseMap.size()).as("Precondition").isEqualTo(2);

        sr.flushUsageCount(b, ref, null);
        assertThat(inUseMap).hasSize(2);

        sr.flushUsageCount(b, ref2, null);
        assertThat(inUseMap).hasSize(1);

    }

    @SuppressWarnings("unchecked")
    @Test
    void flushUsageCountRetry() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        final Bundle b = Mockito.mock(Bundle.class);
        final ServiceReference<?> ref = Mockito.mock(ServiceReference.class);
        final UsageCount uc = new UsageCount(ref, false);
        final ServiceReference<?> ref2 = Mockito.mock(ServiceReference.class);
        final UsageCount uc2 = new UsageCount(ref2, false);

        final ConcurrentMap<Bundle, UsageCount[]> orgInUseMap =
            (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        final ConcurrentMap<Bundle, UsageCount[]> inUseMap =
            Mockito.mock(ConcurrentMap.class, AdditionalAnswers.delegatesTo(orgInUseMap));
        Mockito.doAnswer(new Answer<Boolean>()
            {
                @Override
                public Boolean answer(InvocationOnMock invocation) throws Throwable
                {
                    inUseMap.put(b, new UsageCount[] {uc});
                    return false;
                }
            }).when(inUseMap).replace(Mockito.isA(Bundle.class),
                    Mockito.isA(UsageCount[].class), Mockito.isA(UsageCount[].class));
        setPrivateField(sr, "m_inUseMap", inUseMap);

        inUseMap.put(b, new UsageCount[] {uc, uc2});

        sr.flushUsageCount(b, null, uc);

        assertThat(inUseMap.get(b)).as("A 'concurrent' process has removed uc2 as well, "
            + "so the entry for 'b' should have been removed").isNull();
    }

    @Test
    void flushUsageCountRetry2() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);

        final Bundle b = Mockito.mock(Bundle.class);
        final ServiceReference<?> ref = Mockito.mock(ServiceReference.class);
        final UsageCount uc = new UsageCount(ref, false);
        final ServiceReference<?> ref2 = Mockito.mock(ServiceReference.class);
        final UsageCount uc2 = new UsageCount(ref2, false);

        final ConcurrentMap<Bundle, UsageCount[]> orgInUseMap =
            (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        final ConcurrentMap<Bundle, UsageCount[]> inUseMap =
            Mockito.mock(ConcurrentMap.class, AdditionalAnswers.delegatesTo(orgInUseMap));
        Mockito.doAnswer(new Answer<Boolean>()
            {
                @Override
                public Boolean answer(InvocationOnMock invocation) throws Throwable
                {
                    inUseMap.put(b, new UsageCount[] {uc, uc2});
                    return false;
                }
            }).when(inUseMap).remove(Mockito.isA(Bundle.class), Mockito.isA(UsageCount[].class));
        setPrivateField(sr, "m_inUseMap", inUseMap);

        inUseMap.put(b, new UsageCount[] {uc});

        sr.flushUsageCount(b, null, uc);

        assertThat(inUseMap.get(b).length).isEqualTo(1);
        assertThat(inUseMap.get(b)[0]).isSameAs(uc2);
    }

    @Test
    void getUngetServiceFactory() throws Exception
    {
        final ServiceRegistry sr = new ServiceRegistry(null, null);
        final Bundle regBundle = Mockito.mock(Bundle.class);
        final ServiceRegistration<?> reg = sr.registerService(regBundle, new String[] {Observer.class.getName()},
                new ServiceFactory<Observer>()
                {

                    final class ObserverImpl implements Observer
                    {
                        private final AtomicInteger counter = new AtomicInteger();
                        public volatile boolean active = true;

                        @Override
                        public void update(Observable o, Object arg)
                        {
                            counter.incrementAndGet();
                            if ( !active )
                            {
                                throw new IllegalArgumentException("Iteration:" + counter.get());
                            }
                        }

                    }

                    @Override
                    public Observer getService(Bundle bundle, ServiceRegistration<Observer> registration)
                    {
                        return new ObserverImpl();
                    }

                    @Override
                    public void ungetService(Bundle bundle, ServiceRegistration<Observer> registration, Observer service)
                    {
                        ((ObserverImpl)service).active = false;
                    }
                }, null);

        final Bundle clientBundle = Mockito.mock(Bundle.class);
        Mockito.when(clientBundle.getBundleId()).thenReturn(42L);

        // check simple get/unget
        final Object obj = sr.getService(clientBundle, reg.getReference(), false);
        assertThat(obj).isNotNull();
        assertThat(obj instanceof Observer).isTrue();
        ((Observer)obj).update(null, null);
        sr.ungetService(clientBundle, reg.getReference(), null);
        try {
            ((Observer)obj).update(null, null);
            fail("");
        }
        catch ( final IllegalArgumentException iae)
        {
            // expected
        }

        // start three threads
        final int MAX_THREADS = 3;
        final int MAX_LOOPS = 50000;
        final CountDownLatch latch = new CountDownLatch(MAX_THREADS);
        final Thread[] threads = new Thread[MAX_THREADS];
        final List<Exception> exceptions = Collections.synchronizedList(new ArrayList<Exception>());
        for(int i=0; i<MAX_THREADS; i++)
        {
            threads[i] = new Thread(new Runnable()
            {

                @Override
                public void run()
                {
                    try
                    {
                        Thread.currentThread().sleep(50);
                    }
                    catch (InterruptedException e1)
                    {
                        // ignore
                    }
                    for(int i=0; i < MAX_LOOPS; i++)
                    {
                        try
                        {
                            final Object obj = sr.getService(clientBundle, reg.getReference(), false);
                            ((Observer)obj).update(null, null);
                            sr.ungetService(clientBundle, reg.getReference(), null);
                        }
                        catch ( final Exception e)
                        {
                            exceptions.add(e);
                        }
                    }
                    latch.countDown();
                }
            });
        }
        for(int i=0; i<MAX_THREADS; i++)
        {
            threads[i].start();
        }

        latch.await();

        List<String> counterValues = new ArrayList<>();
        for (Exception ex : exceptions)
        {
            counterValues.add(ex.getMessage());
        }

        assertTrue(exceptions.isEmpty(), "" + counterValues);
    }

    @Test
    void usageCountCleanup() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);
        Bundle regBundle = Mockito.mock(Bundle.class);

        ServiceRegistration<?> reg = sr.registerService(
                regBundle, new String [] {String.class.getName()}, "hi", null);

        final Bundle clientBundle = Mockito.mock(Bundle.class);
        Mockito.when(clientBundle.getBundleId()).thenReturn(327L);

        assertThat(sr.getService(clientBundle, reg.getReference(), false)).isEqualTo("hi");
        sr.ungetService(clientBundle, reg.getReference(), null);

        ConcurrentMap<Bundle, UsageCount[]> inUseMap =
                (ConcurrentMap<Bundle, UsageCount[]>) getPrivateField(sr, "m_inUseMap");

        sr.unregisterService(regBundle, reg);
        assertThat(inUseMap).hasSize(0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getServiceThrowsException() throws Exception
    {
        final ServiceRegistry sr = new ServiceRegistry(null, null);

        final Bundle b = Mockito.mock(Bundle.class);
        ServiceRegistrationImpl<?> reg = Mockito.mock(ServiceRegistrationImpl.class);
        Mockito.when(reg.isValid()).thenReturn(true);
        Mockito.when(reg.getService(b)).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable
            {
                Thread.sleep(500);
                throw new Exception("boo!");
            }
        });

        final ServiceReferenceImpl ref = Mockito.mock(ServiceReferenceImpl.class);
        Mockito.when(ref.getRegistration()).thenReturn(reg);

        final StringBuilder sb = new StringBuilder();
        Thread t = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    assertThat(sb.toString()).as("Should not yet have given the service to the other thread").isEqualTo("");
                    sr.getService(b, ref, false);
                }
                catch (Exception e)
                {
                    // We expect an exception here.
                }
            }
        };
        t.start();

        // Wait until the other thread has called getService();
        Thread.sleep(250);

        // This thread has waited long enough for the other thread to call getService()
        // however the actual getService() call blocks long enough for this one to then
        // concurrently call getService() while the other thread is in getService() of the
        // factory. This thread will then end up in m_latch.await().
        // The factory implementation of the other thread then throws an exception. This test
        // ultimately checks that this thread here is not stuck waiting forwever.
        assertThat(sr.getService(b, ref, false)).isNull();
        sb.append("Obtained service");
    }

    @Test
    void usingBundlesWithoutZeroCounts() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);
        Bundle regBundle = Mockito.mock(Bundle.class);

        ServiceReference<String> ref = registerService(sr, regBundle, "hi");

        final Bundle clientBundle = Mockito.mock(Bundle.class);
        Mockito.when(clientBundle.getBundleId()).thenReturn(42L);
        assertThat(sr.getService(clientBundle, ref, false)).isEqualTo("hi");

        final Bundle clientBundle2 = Mockito.mock(Bundle.class);
        Mockito.when(clientBundle.getBundleId()).thenReturn(327L);
        assertThat(sr.getService(clientBundle2, ref, false)).isEqualTo("hi");

        assertThat(sr.ungetService(clientBundle, ref, null)).isEqualTo(true);

        assertThat(sr.getUsingBundles(ref)).isEqualTo(new Bundle[]{clientBundle2});
    }

    @Test
    void servicesInUseWithoutZeroCounts() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);
        Bundle regBundle = Mockito.mock(Bundle.class);

        ServiceReference<String> refHi = registerService(sr, regBundle, "hi");
        ServiceReference<String> refBye = registerService(sr, regBundle, "bye");

        final Bundle clientBundle = Mockito.mock(Bundle.class);
        Mockito.when(clientBundle.getBundleId()).thenReturn(42L);

        sr.getService(clientBundle, refHi, false);
        sr.getService(clientBundle, refBye, false);
        assertThat(sr.getServicesInUse(clientBundle).length).isEqualTo(2);

        sr.ungetService(clientBundle, refBye, null);
        assertThat(sr.getServicesInUse(clientBundle)).isEqualTo(new ServiceReference[]{refHi});

        sr.ungetService(clientBundle, refHi, null);
        assertThat(sr.getServicesInUse(clientBundle)).isNull();
    }

    @Test
    void prototypeService() throws Exception
    {
        ServiceRegistry sr = new ServiceRegistry(null, null);
        Bundle regBundle = Mockito.mock(Bundle.class);

        final PrototypeServiceFactory<String> psv = new PrototypeServiceFactory<String>()
        {

            @Override
            public void ungetService(Bundle bundle, ServiceRegistration<String> registration, String service)
            {
            }

            @Override
            public String getService(Bundle bundle, ServiceRegistration<String> registration) {
                return "foo";
            }
        };

        ServiceRegistration reg = sr.registerService(
                regBundle, new String [] {String.class.getName()}, psv, null);

        @SuppressWarnings("unchecked")
        ServiceReference<String> ref =  reg.getReference();

        final String val = sr.getService(regBundle, ref, true);
        assertThat(val).isEqualTo("foo");

        // first unget is ok
        assertThat(sr.ungetService(regBundle, ref, val)).isTrue();
        // second unget of the same object, should be ok to
        // This sould return true, but current returns false, see FELIX-6429
        assertThat(sr.ungetService(regBundle, ref, val)).isFalse();
        // ungetting an unknown object must return false
        assertThat(sr.ungetService(regBundle, ref, "bar")).isFalse();

    }

    private ServiceReference<String> registerService(ServiceRegistry sr, Bundle regBundle, String svcObj) {
        ServiceRegistration reg = sr.registerService(
                regBundle, new String [] {String.class.getName()}, svcObj, null);

        @SuppressWarnings("unchecked")
        ServiceReference<String> ref =  reg.getReference();

        return ref;
    }

    private Object getPrivateField(Object obj, String fieldName) throws NoSuchFieldException,
            IllegalAccessException
    {
        Field f = ServiceRegistry.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(obj);
    }

    private void setPrivateField(ServiceRegistry obj, String fieldName, Object val) throws SecurityException,
            NoSuchFieldException, IllegalArgumentException, IllegalAccessException
    {
        Field f = ServiceRegistry.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, val);
    }
}

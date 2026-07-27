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
package org.apache.felix.webconsole.internal.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.felix.webconsole.spi.SecurityProvider;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.servlet.context.ServletContextHelper;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import jakarta.servlet.Servlet;

public class OsgiManagerTest {
    @Test
    public void testSplitCommaSeparatedString() {
        assertEquals(0, OsgiManager.splitCommaSeparatedString(null).size());
        assertEquals(0, OsgiManager.splitCommaSeparatedString("").size());
        assertEquals(0, OsgiManager.splitCommaSeparatedString(" ").size());
        assertEquals(Collections.singleton("foo.bar"),
                OsgiManager.splitCommaSeparatedString("foo.bar "));

        Set<String> expected = new HashSet<String>();
        expected.add("abc");
        expected.add("x.y.z");
        expected.add("123");
        assertEquals(expected,
                OsgiManager.splitCommaSeparatedString(" abc , x.y.z,123"));
    }

    @SuppressWarnings({ "unchecked", "rawtypes"})
    @Test
    public void testUpdateDependenciesCustomizerAdd() throws Exception {
        BundleContext bc = mockBundleContext();

        final List<Boolean> updateCalled = new ArrayList<Boolean>();
        OsgiManager mgr = new OsgiManager(bc) {
            @Override
            void updateRegistrationState() {
                updateCalled.add(true);
            }
        };

        ServiceTrackerCustomizer stc = mgr.new UpdateDependenciesStateCustomizer();

        ServiceReference sref = Mockito.mock(ServiceReference.class);
        stc.addingService(sref);
        assertEquals(1, updateCalled.size());

        ServiceReference sref2 = Mockito.mock(ServiceReference.class);
        Mockito.when(sref2.getProperty(SecurityProvider.PROPERTY_ID)).thenReturn("xyz");
        Mockito.when(sref2.getProperty(Constants.SERVICE_ID)).thenReturn(1L);
        Mockito.when(bc.getService(sref2)).thenReturn(Mockito.mock(SecurityProvider.class));
        stc.addingService(sref2);
        assertEquals(Collections.singleton("xyz"), mgr.registeredSecurityProviders);
        assertEquals(2, updateCalled.size());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testUpdateDependenciesCustomzerRemove() throws Exception {
        BundleContext bc = mockBundleContext();

        final List<Boolean> updateCalled = new ArrayList<Boolean>();
        OsgiManager mgr = new OsgiManager(bc) {
            @Override
            void updateRegistrationState() {
                updateCalled.add(true);
            }
        };
        ServiceReference sref1 = Mockito.mock(ServiceReference.class);
        Mockito.when(sref1.getProperty(SecurityProvider.PROPERTY_ID)).thenReturn("abc");
        Mockito.when(sref1.getProperty(Constants.SERVICE_ID)).thenReturn(1L);
        Mockito.when(bc.getService(sref1)).thenReturn(Mockito.mock(SecurityProvider.class));

        ServiceReference sref2 = Mockito.mock(ServiceReference.class);
        Mockito.when(sref2.getProperty(SecurityProvider.PROPERTY_ID)).thenReturn("xyz");
        Mockito.when(sref2.getProperty(Constants.SERVICE_ID)).thenReturn(2L);
        Mockito.when(bc.getService(sref2)).thenReturn(Mockito.mock(SecurityProvider.class));

        ServiceTrackerCustomizer stc = mgr.new UpdateDependenciesStateCustomizer();
        stc.addingService(sref1);
        stc.addingService(sref2);

        ServiceReference sref = Mockito.mock(ServiceReference.class);
        Mockito.when(sref.getProperty(Constants.SERVICE_ID)).thenReturn(3L);
        stc.removedService(sref, null);

        assertEquals(3, updateCalled.size());
        assertEquals(2, mgr.registeredSecurityProviders.size());
        assertTrue(mgr.registeredSecurityProviders.contains("abc"));
        assertTrue(mgr.registeredSecurityProviders.contains("xyz"));

        stc.removedService(sref2, null);
        assertEquals(Collections.singleton("abc"), mgr.registeredSecurityProviders);
        assertEquals(4, updateCalled.size());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testUpdateDependenciesCustomzerModified() throws Exception {
        BundleContext bc = mockBundleContext();

        OsgiManager mgr = new OsgiManager(bc);

        final List<String> invocations = new ArrayList<String>();
        ServiceTrackerCustomizer stc = mgr.new UpdateDependenciesStateCustomizer() {
            @Override
            public SecurityProvider addingService(ServiceReference<SecurityProvider> reference) {
                invocations.add("added:" + reference);
                return null;
            }

            @Override
            public void removedService(ServiceReference<SecurityProvider> reference, SecurityProvider service) {
                invocations.add("removed:" + reference);
            }
        };

        ServiceReference sref = Mockito.mock(ServiceReference.class);
        Mockito.when(sref.toString()).thenReturn("blah!");

        assertEquals("Precondition", 0, invocations.size());
        stc.modifiedService(sref, null);
        assertEquals(2, invocations.size());
        assertEquals("removed:blah!", invocations.get(0));
        assertEquals("added:blah!", invocations.get(1));
    }


    @Test
    public void testUpdateRegistrationStateNoRequiredProviders() throws Exception {
        BundleContext bc = mockBundleContext();

        final AtomicReference<String> invocation = new AtomicReference<String>();
        new OsgiManager(bc) {
            @Override
            protected synchronized void registerHttpWhiteboardServices() {
                invocation.set("register");
            }

            @Override
            protected synchronized void unregisterHttpWhiteboardServices() {
                invocation.set("unregister");
            }
        };

        // services are registered by default
        assertEquals("register", invocation.get());
    }

    @Test
    public void testUpdateRegistrationStateSomeRequiredProviders() throws Exception {
        BundleContext bc = mockBundleContext();
        Mockito.when(bc.getProperty(OsgiManager.FRAMEWORK_PROP_SECURITY_PROVIDERS)).
            thenReturn("foo,blah");

        final List<String> invocations = new ArrayList<String>();
        OsgiManager mgr = new OsgiManager(bc) {
            @Override
            protected synchronized void registerHttpWhiteboardServices() {
                invocations.add("register");
            }

            @Override
            protected synchronized void unregisterHttpWhiteboardServices() {
                invocations.add("unregister");
            }
        };

        // some required providers, no registered providers -> unregister
        invocations.clear();
        mgr.registeredSecurityProviders.clear();
        mgr.updateRegistrationState();
        assertEquals(Collections.singletonList("unregister"), invocations);

        // some required providers, more registered ones -> register
        invocations.clear();
        mgr.registeredSecurityProviders.addAll(Arrays.asList("foo", "bar", "blah"));
        mgr.updateRegistrationState();
        assertEquals(Collections.singletonList("register"), invocations);

        // some required providers, different registered ones -> unregister
        invocations.clear();
        mgr.registeredSecurityProviders.clear();
        mgr.registeredSecurityProviders.addAll(Arrays.asList("foo", "bar"));
        mgr.updateRegistrationState();
        assertEquals(Collections.singletonList("unregister"), invocations);

        // some required providers, more registered ones -> unregister
        invocations.clear();
        mgr.registeredSecurityProviders.addAll(Arrays.asList("foo", "bar", "blah"));
        mgr.updateRegistrationState();
        assertEquals(Collections.singletonList("register"), invocations);
    }


    @SuppressWarnings({ "unchecked" })
    @Test
    public void testRegisterHttpWhiteboardServices() throws Exception {
        final BundleContext bc = mockBundleContext();
        final OsgiManager mgr = new OsgiManager(bc);

        Mockito.verify(bc, Mockito.times(1))
            .registerService(Mockito.eq(SecurityProvider.class), Mockito.isA(SecurityProvider.class), Mockito.isA(Dictionary.class));
        Mockito.verify(bc, Mockito.times(1))
            .registerService(Mockito.eq(ServletContextHelper.class), Mockito.isA(ServletContextHelper.class), Mockito.isA(Dictionary.class));
        Mockito.verify(bc, Mockito.times(7))
            .registerService(Mockito.eq(Servlet.class), Mockito.isA(Servlet.class), Mockito.isA(Dictionary.class));

        mgr.registerHttpWhiteboardServices();

        // Should not re-register the services, as they were already registered
        Mockito.verify(bc, Mockito.times(1))
            .registerService(Mockito.eq(SecurityProvider.class), Mockito.isA(SecurityProvider.class), Mockito.isA(Dictionary.class));
        Mockito.verify(bc, Mockito.times(1))
            .registerService(Mockito.eq(ServletContextHelper.class), Mockito.isA(ServletContextHelper.class), Mockito.isA(Dictionary.class));
        Mockito.verify(bc, Mockito.times(7))
            .registerService(Mockito.eq(Servlet.class), Mockito.isA(Servlet.class), Mockito.isA(Dictionary.class));
    }

    @SuppressWarnings({ "rawtypes" })
    @Test
    public void testUnregisterHttpService() throws Exception {
        final BundleContext bc = mockBundleContext();
        final OsgiManager mgr = new OsgiManager(bc);

        final ServiceRegistration reg1 = Mockito.mock(ServiceRegistration.class);
        final ServiceRegistration reg2 = Mockito.mock(ServiceRegistration.class);

        setPrivateField(OsgiManager.class, mgr, "servletContextRegistration", reg1);
        setPrivateField(OsgiManager.class, mgr, "servletRegistration", reg2);

        mgr.unregisterHttpWhiteboardServices();
        assertNull(getPrivateField(OsgiManager.class, mgr, "servletContextRegistration"));
        assertNull(getPrivateField(OsgiManager.class, mgr, "servletRegistration"));

        Mockito.verify(reg1, Mockito.times(1)).unregister();
        Mockito.verify(reg2, Mockito.times(1)).unregister();

        mgr.unregisterHttpWhiteboardServices();
        assertNull(getPrivateField(OsgiManager.class, mgr, "servletContextRegistration"));
        assertNull(getPrivateField(OsgiManager.class, mgr, "servletRegistration"));

        Mockito.verify(reg1, Mockito.times(1)).unregister();
        Mockito.verify(reg2, Mockito.times(1)).unregister();
    }

    private Object getPrivateField(Class<?> cls, Object obj, String field) throws Exception {
        Field f = cls.getDeclaredField(field);
        f.setAccessible(true);
        return f.get(obj);
    }

    private void setPrivateField(Class<?> cls, Object obj, String field, Object value) throws Exception {
        Field f = cls.getDeclaredField(field);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private BundleContext mockBundleContext() throws InvalidSyntaxException {
        Bundle bundle = Mockito.mock(Bundle.class);
        BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.when(bc.getBundle()).thenReturn(bundle);
        Mockito.when(bundle.getBundleContext()).thenReturn(bc);
        Mockito.when(bc.createFilter(Mockito.anyString())).then(new Answer<Filter>() {
            @Override
            public Filter answer(InvocationOnMock invocation) throws Throwable {
                String fs = invocation.getArgument(0, String.class);
                return FrameworkUtil.createFilter(fs);
            }
        });
        // FELIX-6341 - mock the getHeaders to avoid a NPE during ResourceBundleCache#getResourceBundleEntries
        final Dictionary<String, String> headers = new Hashtable<>();
        Mockito.when(bundle.getHeaders()).thenReturn(headers);
        // FELIX-6341 - mock bundle#findEntries so ResourceBundleCache#getResourceBundleEntries will function
        URL rbUrl = getClass().getResource("/OSGI-INF/l10n/bundle.properties");
        Mockito.when(bundle.findEntries("OSGI-INF/l10n", "bundle*.properties", false)).thenAnswer(new Answer<Enumeration<URL>>() {
			@Override
			public Enumeration<URL> answer(InvocationOnMock invocation) throws Throwable {
				return Collections.enumeration(Collections.singleton(rbUrl));
			}
        });
        Mockito.when(bc.registerService((Class)Mockito.any(), (Object)Mockito.any(), (Dictionary)Mockito.any()))
            .thenReturn(Mockito.mock(ServiceRegistration.class));
        return bc;
    }
}

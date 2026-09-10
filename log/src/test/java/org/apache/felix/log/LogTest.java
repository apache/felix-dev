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
package org.apache.felix.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;

public class LogTest {
    @Test
    public void testWarnEventMappingAndConditionalProperties() throws Exception {
        final Bundle eventAdminBundle = mock(Bundle.class);
        doReturn(Event.class).when(eventAdminBundle).loadClass("org.osgi.service.event.Event");
        doReturn(EventAdmin.class).when(eventAdminBundle).loadClass("org.osgi.service.event.EventAdmin");

        @SuppressWarnings("unchecked")
        final ServiceReference<Object> eventAdminReference = mock(ServiceReference.class);
        when(eventAdminReference.getBundle()).thenReturn(eventAdminBundle);
        final EventAdmin eventAdmin = mock(EventAdmin.class);
        final Log.EAProxy proxy = new Log.EAProxy(eventAdminReference, eventAdmin);

        final Bundle sourceBundle = mock(Bundle.class);
        when(sourceBundle.getBundleId()).thenReturn(42L);
        when(sourceBundle.getSymbolicName()).thenReturn(null);
        final Throwable exception = new RuntimeException();
        final LogEntry entry = mock(LogEntry.class);
        when(entry.getBundle()).thenReturn(sourceBundle);
        when(entry.getLogLevel()).thenReturn(LogLevel.WARN);
        when(entry.getLevel()).thenReturn(47);
        when(entry.getLoggerName()).thenReturn("test.logger");
        when(entry.getThreadInfo()).thenReturn("test-thread");
        when(entry.getMessage()).thenReturn("test-message");
        when(entry.getTime()).thenReturn(1234L);
        when(entry.getException()).thenReturn(exception);

        proxy.postEvent(entry);

        final ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventAdmin).postEvent(eventCaptor.capture());
        final Event event = eventCaptor.getValue();
        assertEquals("org/osgi/service/log/LogEntry/LOG_WARNING", event.getTopic());
        assertEquals(Integer.valueOf(47), event.getProperty("log.level"));
        assertSame(LogLevel.WARN, event.getProperty("log.loglevel"));
        assertFalse(Arrays.asList(event.getPropertyNames()).contains(EventConstants.BUNDLE_SYMBOLICNAME));
        assertFalse(Arrays.asList(event.getPropertyNames()).contains(EventConstants.EXCEPTION_MESSAGE));
        assertNotNull(event.getProperty(EventConstants.EXCEPTION));
    }

    @Test
    public void testEventAdminServiceUseIsBalanced() throws Exception {
        final BundleContext context = mock(BundleContext.class);
        final Bundle eventAdminBundle = mock(Bundle.class);
        doReturn(Event.class).when(eventAdminBundle).loadClass("org.osgi.service.event.Event");
        doReturn(EventAdmin.class).when(eventAdminBundle).loadClass("org.osgi.service.event.EventAdmin");
        @SuppressWarnings("unchecked")
        final ServiceReference<Object> reference = mock(ServiceReference.class);
        when(reference.getBundle()).thenReturn(eventAdminBundle);
        final EventAdmin eventAdmin = mock(EventAdmin.class);
        when(context.getService(reference)).thenReturn(eventAdmin);
        final Log.EAProxyServiceTrackerCustomizer customizer =
                new Log.EAProxyServiceTrackerCustomizer(context);

        final Log.EAProxy proxy = customizer.addingService(reference);
        assertNotNull(proxy);
        verify(context, times(1)).getService(reference);

        customizer.modifiedService(reference, proxy);
        verify(context, times(1)).getService(reference);

        customizer.removedService(reference, proxy);
        assertNull(proxy.m_info.get());
        verify(context).ungetService(reference);
    }

    @Test
    public void testUnavailableEventAdminServiceIsNotTracked() {
        final BundleContext context = mock(BundleContext.class);
        @SuppressWarnings("unchecked")
        final ServiceReference<Object> reference = mock(ServiceReference.class);
        when(context.getService(reference)).thenReturn(null);
        final Log.EAProxyServiceTrackerCustomizer customizer =
                new Log.EAProxyServiceTrackerCustomizer(context);

        assertNull(customizer.addingService(reference));
        verify(context, never()).ungetService(reference);
    }
}

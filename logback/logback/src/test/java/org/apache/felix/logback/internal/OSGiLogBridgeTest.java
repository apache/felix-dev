/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.felix.logback.internal;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.junit.Test;
import org.mockito.InOrder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.admin.LoggerAdmin;

@SuppressWarnings("unchecked")
public class OSGiLogBridgeTest {

    @Test
    public void servicesAreReleasedWhenBindingIsRemoved() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LoggerAdmin> adminReference = mock(ServiceReference.class);
        ServiceReference<LogReaderService> readerReference = mock(ServiceReference.class);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        LogReaderService logReader = mock(LogReaderService.class);
        LogbackLogListener listener = mock(LogbackLogListener.class);
        Bundle provider = mock(Bundle.class);
        when(context.getService(adminReference)).thenReturn(loggerAdmin);
        when(context.getService(readerReference)).thenReturn(logReader);
        when(adminReference.getBundle()).thenReturn(provider);
        when(readerReference.getBundle()).thenReturn(provider);
        OSGiLogBridge bridge = new OSGiLogBridge(context, admin -> listener);

        bridge.loggerAdminTracker.addingService(adminReference);
        bridge.logReaderTracker.addingService(readerReference);
        bridge.logReaderTracker.removedService(readerReference, logReader);
        bridge.loggerAdminTracker.removedService(adminReference, loggerAdmin);

        verify(logReader).addLogListener(listener);
        verify(logReader).removeLogListener(listener);
        verify(listener).close();
        verify(context).ungetService(readerReference);
        verify(context).ungetService(adminReference);
    }

    @Test
    public void readerRankingSelectsCoherentProviderPairs() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LoggerAdmin> adminAReference = mock(ServiceReference.class);
        ServiceReference<LoggerAdmin> adminBReference = mock(ServiceReference.class);
        ServiceReference<LogReaderService> readerAReference = mock(ServiceReference.class);
        ServiceReference<LogReaderService> readerBReference = mock(ServiceReference.class);
        LoggerAdmin adminA = mock(LoggerAdmin.class);
        LoggerAdmin adminB = mock(LoggerAdmin.class);
        LogReaderService readerA = mock(LogReaderService.class);
        LogReaderService readerB = mock(LogReaderService.class);
        Bundle providerA = mock(Bundle.class);
        Bundle providerB = mock(Bundle.class);
        AtomicBoolean readerBIsHigher = new AtomicBoolean();
        List<LoggerAdmin> selectedAdmins = new ArrayList<>();
        List<LogbackLogListener> listeners = new ArrayList<>();
        when(context.getService(adminAReference)).thenReturn(adminA);
        when(context.getService(adminBReference)).thenReturn(adminB);
        when(context.getService(readerAReference)).thenReturn(readerA);
        when(context.getService(readerBReference)).thenReturn(readerB);
        when(adminAReference.getBundle()).thenReturn(providerA);
        when(readerAReference.getBundle()).thenReturn(providerA);
        when(adminBReference.getBundle()).thenReturn(providerB);
        when(readerBReference.getBundle()).thenReturn(providerB);
        makeHigher(adminBReference, adminAReference);
        when(readerAReference.compareTo(readerBReference)).thenAnswer(
            invocation -> readerBIsHigher.get() ? -1 : 1);
        when(readerBReference.compareTo(readerAReference)).thenAnswer(
            invocation -> readerBIsHigher.get() ? 1 : -1);
        OSGiLogBridge bridge = new OSGiLogBridge(context, admin -> {
            LogbackLogListener listener = mock(LogbackLogListener.class);
            selectedAdmins.add(admin);
            listeners.add(listener);
            return listener;
        });

        bridge.loggerAdminTracker.addingService(adminAReference);
        bridge.logReaderTracker.addingService(readerAReference);
        bridge.loggerAdminTracker.addingService(adminBReference);
        bridge.logReaderTracker.addingService(readerBReference);
        readerBIsHigher.set(true);
        bridge.logReaderTracker.modifiedService(readerBReference, readerB);
        bridge.logReaderTracker.removedService(readerBReference, readerB);

        InOrder order = inOrder(readerA, readerB, listeners.get(0),
            listeners.get(1), listeners.get(2));
        order.verify(readerA).addLogListener(listeners.get(0));
        order.verify(readerA).removeLogListener(listeners.get(0));
        order.verify(listeners.get(0)).close();
        order.verify(readerB).addLogListener(listeners.get(1));
        order.verify(readerB).removeLogListener(listeners.get(1));
        order.verify(listeners.get(1)).close();
        order.verify(readerA).addLogListener(listeners.get(2));
        assertSame(adminA, selectedAdmins.get(0));
        assertSame(adminB, selectedAdmins.get(1));
        assertSame(adminA, selectedAdmins.get(2));
        verify(readerA, never()).addLogListener(listeners.get(1));
        verify(readerB, never()).addLogListener(listeners.get(0));
        verify(readerB, never()).addLogListener(listeners.get(2));
        verify(context).ungetService(readerBReference);
    }

    @Test
    public void servicesFromDifferentProvidersAreNotBound() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LoggerAdmin> adminReference = mock(ServiceReference.class);
        ServiceReference<LogReaderService> readerReference = mock(ServiceReference.class);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        LogReaderService logReader = mock(LogReaderService.class);
        Function<LoggerAdmin, LogbackLogListener> listenerFactory = mock(Function.class);
        when(context.getService(adminReference)).thenReturn(loggerAdmin);
        when(context.getService(readerReference)).thenReturn(logReader);
        when(adminReference.getBundle()).thenReturn(mock(Bundle.class));
        when(readerReference.getBundle()).thenReturn(mock(Bundle.class));
        OSGiLogBridge bridge = new OSGiLogBridge(context, listenerFactory);

        bridge.loggerAdminTracker.addingService(adminReference);
        bridge.logReaderTracker.addingService(readerReference);

        verify(listenerFactory, never()).apply(loggerAdmin);
        verify(logReader, never()).addLogListener(
            org.mockito.ArgumentMatchers.any(LogbackLogListener.class));
    }

    @Test
    public void unavailableServicesAreNotTrackedOrReleased() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LoggerAdmin> adminReference = mock(ServiceReference.class);
        ServiceReference<LogReaderService> readerReference = mock(ServiceReference.class);
        OSGiLogBridge bridge = new OSGiLogBridge(
            context, admin -> mock(LogbackLogListener.class));

        assertNull(bridge.loggerAdminTracker.addingService(adminReference));
        assertNull(bridge.logReaderTracker.addingService(readerReference));

        verify(context, never()).ungetService(adminReference);
        verify(context, never()).ungetService(readerReference);
    }

    @Test
    public void logReaderIsReleasedWhenListenerRegistrationFails() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LoggerAdmin> adminReference = mock(ServiceReference.class);
        ServiceReference<LogReaderService> readerReference = mock(ServiceReference.class);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        LogReaderService logReader = mock(LogReaderService.class);
        LogbackLogListener listener = mock(LogbackLogListener.class);
        Bundle provider = mock(Bundle.class);
        when(context.getService(adminReference)).thenReturn(loggerAdmin);
        when(context.getService(readerReference)).thenReturn(logReader);
        when(adminReference.getBundle()).thenReturn(provider);
        when(readerReference.getBundle()).thenReturn(provider);
        doThrow(new IllegalStateException()).when(logReader).addLogListener(listener);
        OSGiLogBridge bridge = new OSGiLogBridge(context, admin -> listener);
        bridge.loggerAdminTracker.addingService(adminReference);

        assertThrows(IllegalStateException.class,
            () -> bridge.logReaderTracker.addingService(readerReference));

        verify(logReader).removeLogListener(listener);
        verify(listener).close();
        verify(context).ungetService(readerReference);
    }

    @Test
    public void logReaderIsReleasedWhenListenerRemovalFails() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LoggerAdmin> adminReference = mock(ServiceReference.class);
        ServiceReference<LogReaderService> readerReference = mock(ServiceReference.class);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        LogReaderService logReader = mock(LogReaderService.class);
        LogbackLogListener listener = mock(LogbackLogListener.class);
        Bundle provider = mock(Bundle.class);
        when(context.getService(adminReference)).thenReturn(loggerAdmin);
        when(context.getService(readerReference)).thenReturn(logReader);
        when(adminReference.getBundle()).thenReturn(provider);
        when(readerReference.getBundle()).thenReturn(provider);
        doThrow(new IllegalStateException()).when(logReader).removeLogListener(listener);
        OSGiLogBridge bridge = new OSGiLogBridge(context, admin -> listener);
        bridge.loggerAdminTracker.addingService(adminReference);
        bridge.logReaderTracker.addingService(readerReference);

        assertThrows(IllegalStateException.class,
            () -> bridge.logReaderTracker.removedService(readerReference, logReader));

        verify(listener).close();
        verify(context).ungetService(readerReference);
    }

    private static <S> void makeHigher(
        ServiceReference<S> high, ServiceReference<S> low) {

        when(high.compareTo(low)).thenReturn(1);
        when(low.compareTo(high)).thenReturn(-1);
    }

}

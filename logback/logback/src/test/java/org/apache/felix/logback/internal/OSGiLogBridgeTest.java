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

import org.junit.Test;
import org.mockito.InOrder;
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
        when(context.getService(adminReference)).thenReturn(loggerAdmin);
        when(context.getService(readerReference)).thenReturn(logReader);
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
    public void highestRankedServicesAreBoundWithFallbackOnRemoval() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LoggerAdmin> lowAdminReference = mock(ServiceReference.class);
        ServiceReference<LoggerAdmin> highAdminReference = mock(ServiceReference.class);
        ServiceReference<LogReaderService> lowReaderReference = mock(ServiceReference.class);
        ServiceReference<LogReaderService> highReaderReference = mock(ServiceReference.class);
        LoggerAdmin lowAdmin = mock(LoggerAdmin.class);
        LoggerAdmin highAdmin = mock(LoggerAdmin.class);
        LogReaderService lowReader = mock(LogReaderService.class);
        LogReaderService highReader = mock(LogReaderService.class);
        List<LoggerAdmin> selectedAdmins = new ArrayList<>();
        List<LogbackLogListener> listeners = new ArrayList<>();
        when(context.getService(lowAdminReference)).thenReturn(lowAdmin);
        when(context.getService(highAdminReference)).thenReturn(highAdmin);
        when(context.getService(lowReaderReference)).thenReturn(lowReader);
        when(context.getService(highReaderReference)).thenReturn(highReader);
        makeHigher(highAdminReference, lowAdminReference);
        makeHigher(highReaderReference, lowReaderReference);
        OSGiLogBridge bridge = new OSGiLogBridge(context, admin -> {
            LogbackLogListener listener = mock(LogbackLogListener.class);
            selectedAdmins.add(admin);
            listeners.add(listener);
            return listener;
        });

        bridge.loggerAdminTracker.addingService(lowAdminReference);
        bridge.logReaderTracker.addingService(lowReaderReference);
        bridge.loggerAdminTracker.addingService(highAdminReference);
        bridge.logReaderTracker.addingService(highReaderReference);
        bridge.logReaderTracker.removedService(highReaderReference, highReader);
        bridge.loggerAdminTracker.removedService(highAdminReference, highAdmin);

        InOrder order = inOrder(lowReader, highReader, listeners.get(0),
            listeners.get(1), listeners.get(2), listeners.get(3), listeners.get(4));
        order.verify(lowReader).addLogListener(listeners.get(0));
        order.verify(lowReader).removeLogListener(listeners.get(0));
        order.verify(listeners.get(0)).close();
        order.verify(lowReader).addLogListener(listeners.get(1));
        order.verify(lowReader).removeLogListener(listeners.get(1));
        order.verify(listeners.get(1)).close();
        order.verify(highReader).addLogListener(listeners.get(2));
        order.verify(highReader).removeLogListener(listeners.get(2));
        order.verify(listeners.get(2)).close();
        order.verify(lowReader).addLogListener(listeners.get(3));
        order.verify(lowReader).removeLogListener(listeners.get(3));
        order.verify(listeners.get(3)).close();
        order.verify(lowReader).addLogListener(listeners.get(4));
        assertSame(lowAdmin, selectedAdmins.get(0));
        assertSame(highAdmin, selectedAdmins.get(1));
        assertSame(highAdmin, selectedAdmins.get(2));
        assertSame(highAdmin, selectedAdmins.get(3));
        assertSame(lowAdmin, selectedAdmins.get(4));
        verify(lowReader, never()).addLogListener(listeners.get(2));
        verify(highReader, never()).addLogListener(listeners.get(0));
        verify(highReader, never()).addLogListener(listeners.get(1));
        verify(context).ungetService(highReaderReference);
        verify(context).ungetService(highAdminReference);
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
        when(context.getService(adminReference)).thenReturn(loggerAdmin);
        when(context.getService(readerReference)).thenReturn(logReader);
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
        when(context.getService(adminReference)).thenReturn(loggerAdmin);
        when(context.getService(readerReference)).thenReturn(logReader);
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

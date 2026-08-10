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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.admin.LoggerAdmin;

@SuppressWarnings("unchecked")
public class ActivatorTest {

    @Test
    public void loggerAdminIsReleased() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LoggerAdmin> reference = mock(ServiceReference.class);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        Activator.LRST lrst = mock(Activator.LRST.class);
        when(context.getService(reference)).thenReturn(loggerAdmin);

        Activator.LoggerAdminServiceTracker tracker =
            new Activator.LoggerAdminServiceTracker(context, (c, a) -> lrst);

        assertSame(lrst, tracker.addingService(reference));
        tracker.removedService(reference, lrst);

        verify(lrst).open();
        verify(lrst).close();
        verify(context).ungetService(reference);
    }

    @Test
    public void loggerAdminIsReleasedWhenOpeningItsTrackerFails() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LoggerAdmin> reference = mock(ServiceReference.class);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        Activator.LRST lrst = mock(Activator.LRST.class);
        when(context.getService(reference)).thenReturn(loggerAdmin);
        doThrow(new IllegalStateException()).when(lrst).open();

        Activator.LoggerAdminServiceTracker tracker =
            new Activator.LoggerAdminServiceTracker(context, (c, a) -> lrst);

        assertThrows(IllegalStateException.class, () -> tracker.addingService(reference));
        verify(context).ungetService(reference);
    }

    @Test
    public void loggerAdminIsReleasedWhenClosingItsTrackerFails() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LoggerAdmin> reference = mock(ServiceReference.class);
        Activator.LRST lrst = mock(Activator.LRST.class);
        doThrow(new IllegalStateException()).when(lrst).close();
        Activator.LoggerAdminServiceTracker tracker =
            new Activator.LoggerAdminServiceTracker(context, (c, a) -> lrst);

        assertThrows(
            IllegalStateException.class,
            () -> tracker.removedService(reference, lrst));
        verify(context).ungetService(reference);
    }

    @Test
    public void unavailableLoggerAdminIsNotTrackedOrReleased() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LoggerAdmin> reference = mock(ServiceReference.class);
        Activator.LoggerAdminServiceTracker tracker =
            new Activator.LoggerAdminServiceTracker(context, (c, a) -> mock(Activator.LRST.class));

        assertNull(tracker.addingService(reference));
        verify(context, never()).ungetService(reference);
    }

    @Test
    public void logReaderServiceIsReleased() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LogReaderService> reference = mock(ServiceReference.class);
        LogReaderService logReaderService = mock(LogReaderService.class);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        LogbackLogListener listener = mock(LogbackLogListener.class);
        when(context.getService(reference)).thenReturn(logReaderService);

        Activator.LRST tracker = new Activator.LRST(context, loggerAdmin, a -> listener);

        Activator.Pair pair = tracker.addingService(reference);
        assertSame(logReaderService, pair.getKey());
        assertSame(listener, pair.getValue());
        tracker.removedService(reference, pair);

        verify(logReaderService).addLogListener(listener);
        verify(logReaderService).removeLogListener(listener);
        verify(listener).close();
        verify(context).ungetService(reference);
    }

    @Test
    public void logReaderServiceIsReleasedWhenListenerRegistrationFails() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LogReaderService> reference = mock(ServiceReference.class);
        LogReaderService logReaderService = mock(LogReaderService.class);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        LogbackLogListener listener = mock(LogbackLogListener.class);
        when(context.getService(reference)).thenReturn(logReaderService);
        doThrow(new IllegalStateException()).when(logReaderService).addLogListener(listener);

        Activator.LRST tracker = new Activator.LRST(context, loggerAdmin, a -> listener);

        assertThrows(IllegalStateException.class, () -> tracker.addingService(reference));
        verify(logReaderService).removeLogListener(listener);
        verify(listener).close();
        verify(context).ungetService(reference);
    }

    @Test
    public void logReaderServiceIsReleasedWhenListenerRemovalFails() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LogReaderService> reference = mock(ServiceReference.class);
        LogReaderService logReaderService = mock(LogReaderService.class);
        LogbackLogListener listener = mock(LogbackLogListener.class);
        doThrow(new IllegalStateException()).when(logReaderService).removeLogListener(listener);
        Activator.LRST tracker = new Activator.LRST(
            context, mock(LoggerAdmin.class), a -> listener);
        Activator.Pair pair = new Activator.Pair(logReaderService, listener);

        assertThrows(
            IllegalStateException.class,
            () -> tracker.removedService(reference, pair));
        verify(listener).close();
        verify(context).ungetService(reference);
    }

    @Test
    public void unavailableLogReaderServiceIsNotTrackedOrReleased() {
        BundleContext context = mock(BundleContext.class);
        ServiceReference<LogReaderService> reference = mock(ServiceReference.class);
        Activator.LRST tracker = new Activator.LRST(
            context, mock(LoggerAdmin.class), a -> mock(LogbackLogListener.class));

        assertNull(tracker.addingService(reference));
        verify(context, never()).ungetService(reference);
    }

}

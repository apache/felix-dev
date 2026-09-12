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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.equinox.log.ExtendedLogEntry;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.service.log.LogEntry;
import org.slf4j.event.KeyValuePair;

import ch.qos.logback.classic.spi.LoggingEvent;

public class LogEntryEnricherTest {

    @Test
    public void equinoxEntriesExposeRawContextAndThreadId() {
        Object context = new Object();
        ExtendedLogEntry entry = mock(ExtendedLogEntry.class);
        when(entry.getContext()).thenReturn(context);
        when(entry.getThreadId()).thenReturn(47L);
        LoggingEvent event = new LoggingEvent();

        new EquinoxLogEntryEnricher().enrich(entry, event);

        List<KeyValuePair> values = event.getKeyValuePairs();
        assertEquals(2, values.size());
        assertEquals(EquinoxLogEntryEnricher.CONTEXT_KEY, values.get(0).key);
        assertSame(context, values.get(0).value);
        assertEquals(EquinoxLogEntryEnricher.THREAD_ID_KEY, values.get(1).key);
        assertEquals(Long.valueOf(47L), values.get(1).value);
    }

    @Test
    public void standardEntriesAreNotEnriched() {
        LoggingEvent event = new LoggingEvent();

        new EquinoxLogEntryEnricher().enrich(mock(LogEntry.class), event);

        assertNull(event.getKeyValuePairs());
    }

    @Test
    public void unavailableEquinoxApiSelectsNoOpEnricher() throws Exception {
        Bundle bundle = mock(Bundle.class);
        when(bundle.loadClass(LogEntryEnricher.EQUINOX_EXTENDED_LOG_ENTRY))
            .thenThrow(new ClassNotFoundException());

        assertSame(NoOpLogEntryEnricher.INSTANCE,
            LogEntryEnricher.create(bundle));
    }

    @Test
    public void availableEquinoxApiSelectsEquinoxEnricher() throws Exception {
        Bundle bundle = mock(Bundle.class);
        when(bundle.loadClass(LogEntryEnricher.EQUINOX_EXTENDED_LOG_ENTRY))
            .thenAnswer(invocation -> ExtendedLogEntry.class);

        assertEquals(EquinoxLogEntryEnricher.class,
            LogEntryEnricher.create(bundle).getClass());
    }

    @Test
    public void linkageFailuresRemainVisible() throws Exception {
        Bundle bundle = mock(Bundle.class);
        when(bundle.loadClass(LogEntryEnricher.EQUINOX_EXTENDED_LOG_ENTRY))
            .thenThrow(new NoClassDefFoundError("incompatible Equinox API"));

        assertThrows(NoClassDefFoundError.class,
            () -> LogEntryEnricher.create(bundle));
    }

}

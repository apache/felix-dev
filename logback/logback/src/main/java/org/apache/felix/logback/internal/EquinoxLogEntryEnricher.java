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

import ch.qos.logback.classic.spi.LoggingEvent;
import org.eclipse.equinox.log.ExtendedLogEntry;
import org.osgi.service.log.LogEntry;
import org.slf4j.event.KeyValuePair;

/**
 * Makes Equinox log context and thread identity available to Logback
 * appenders as structured data.
 */
final class EquinoxLogEntryEnricher implements LogEntryEnricher {

    static final String CONTEXT_KEY = "osgi.log.context";
    static final String THREAD_ID_KEY = "osgi.log.thread.id";

    @Override
    public void enrich(LogEntry entry, LoggingEvent event) {
        if (!(entry instanceof ExtendedLogEntry)) {
            return;
        }

        ExtendedLogEntry extendedEntry = (ExtendedLogEntry)entry;
        Object context = extendedEntry.getContext();

        if (context != null) {
            event.addKeyValuePair(new KeyValuePair(CONTEXT_KEY, context));
        }

        event.addKeyValuePair(new KeyValuePair(
            THREAD_ID_KEY, extendedEntry.getThreadId()));
    }

}

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.admin.LoggerAdmin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

public class LogbackLogListenerTest {

    @Test
    public void closeRemovesContextListenerAndRestoresLogLevelsAfterReset() {
        LoggerContext loggerContext = new LoggerContext();
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.INFO);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        org.osgi.service.log.admin.LoggerContext osgiLoggerContext =
            mock(org.osgi.service.log.admin.LoggerContext.class);
        Map<String, LogLevel> initialLevels = new HashMap<>();
        initialLevels.put("existing", LogLevel.ERROR);
        AtomicReference<Map<String, LogLevel>> levels = mockLogLevels(
            loggerAdmin, osgiLoggerContext, initialLevels);

        LogbackLogListener listener = new LogbackLogListener(loggerAdmin, loggerContext);

        assertTrue(loggerContext.getCopyOfListenerList().contains(listener));
        listener.onReset(loggerContext);
        listener.close();
        listener.close();

        assertFalse(loggerContext.getCopyOfListenerList().contains(listener));
        assertEquals(initialLevels, levels.get());
        verify(osgiLoggerContext, times(1)).setLogLevels(initialLevels);
    }

    @Test
    public void closePreservesLogLevelsChangedByAnotherOwner() {
        LoggerContext loggerContext = new LoggerContext();
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.INFO);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        org.osgi.service.log.admin.LoggerContext osgiLoggerContext =
            mock(org.osgi.service.log.admin.LoggerContext.class);
        Map<String, LogLevel> initialLevels = new HashMap<>();
        initialLevels.put(Logger.ROOT_LOGGER_NAME, LogLevel.ERROR);
        initialLevels.put("existing", LogLevel.WARN);
        AtomicReference<Map<String, LogLevel>> levels = mockLogLevels(
            loggerAdmin, osgiLoggerContext, initialLevels);

        LogbackLogListener listener = new LogbackLogListener(loggerAdmin, loggerContext);
        Map<String, LogLevel> externalLevels = new HashMap<>(levels.get());
        externalLevels.put("Events.Bundle", LogLevel.DEBUG);
        externalLevels.put("external", LogLevel.TRACE);
        levels.set(externalLevels);

        listener.close();

        Map<String, LogLevel> expected = new HashMap<>(initialLevels);
        expected.put("Events.Bundle", LogLevel.DEBUG);
        expected.put("external", LogLevel.TRACE);
        assertEquals(expected, levels.get());
    }

    @Test
    public void loggedUsesNamedLoggerAppenderRouting() {
        LoggerContext loggerContext = new LoggerContext();
        Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.TRACE);
        Logger named = loggerContext.getLogger("test.named");
        named.setLevel(Level.INFO);
        named.setAdditive(false);
        ListAppender<ILoggingEvent> rootAppender = new ListAppender<>();
        ListAppender<ILoggingEvent> namedAppender = new ListAppender<>();
        rootAppender.setContext(loggerContext);
        rootAppender.start();
        namedAppender.setContext(loggerContext);
        namedAppender.start();
        root.addAppender(rootAppender);
        named.addAppender(namedAppender);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        org.osgi.service.log.admin.LoggerContext osgiLoggerContext =
            mock(org.osgi.service.log.admin.LoggerContext.class);
        mockLogLevels(loggerAdmin, osgiLoggerContext, new HashMap<>());
        LogEntry entry = mock(LogEntry.class);
        when(entry.getLoggerName()).thenReturn(named.getName());
        when(entry.getMessage()).thenReturn("message");
        when(entry.getLogLevel()).thenReturn(LogLevel.INFO);

        LogbackLogListener listener = new LogbackLogListener(loggerAdmin, loggerContext);
        listener.logged(entry);

        assertEquals(1, namedAppender.list.size());
        assertTrue(rootAppender.list.isEmpty());
        listener.close();
    }

    private static AtomicReference<Map<String, LogLevel>> mockLogLevels(
        LoggerAdmin loggerAdmin,
        org.osgi.service.log.admin.LoggerContext osgiLoggerContext,
        Map<String, LogLevel> initialLevels) {

        AtomicReference<Map<String, LogLevel>> levels =
            new AtomicReference<>(new HashMap<>(initialLevels));
        when(loggerAdmin.getLoggerContext(null)).thenReturn(osgiLoggerContext);
        when(osgiLoggerContext.getLogLevels()).thenAnswer(
            invocation -> new HashMap<>(levels.get()));
        doAnswer(invocation -> {
            levels.set(new HashMap<>(invocation.getArgument(0)));
            return null;
        }).when(osgiLoggerContext).setLogLevels(anyMap());

        return levels;
    }

}

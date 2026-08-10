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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.admin.LoggerAdmin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

public class LogbackLogListenerTest {

    @Test
    public void closeRemovesContextListenerAndRestoresLogLevels() {
        LoggerContext loggerContext = new LoggerContext();
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.INFO);
        LoggerAdmin loggerAdmin = mock(LoggerAdmin.class);
        org.osgi.service.log.admin.LoggerContext osgiLoggerContext =
            mock(org.osgi.service.log.admin.LoggerContext.class);
        Map<String, LogLevel> initialLevels = new HashMap<>();
        initialLevels.put("existing", LogLevel.ERROR);
        when(loggerAdmin.getLoggerContext(null)).thenReturn(osgiLoggerContext);
        when(osgiLoggerContext.getLogLevels()).thenReturn(initialLevels);

        LogbackLogListener listener = new LogbackLogListener(loggerAdmin, loggerContext);

        assertTrue(loggerContext.getCopyOfListenerList().contains(listener));
        listener.close();
        listener.close();

        assertFalse(loggerContext.getCopyOfListenerList().contains(listener));
        verify(osgiLoggerContext, times(1)).setLogLevels(initialLevels);
    }

}

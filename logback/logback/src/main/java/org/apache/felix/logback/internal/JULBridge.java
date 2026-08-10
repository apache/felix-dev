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

import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.slf4j.bridge.SLF4JBridgeHandler;

final class JULBridge {

    private Logger rootLogger;
    private Handler[] rootHandlers;
    private SLF4JBridgeHandler bridgeHandler;

    void install() {
        rootLogger = LogManager.getLogManager().getLogger("");
        rootHandlers = rootLogger.getHandlers();
        bridgeHandler = new SLF4JBridgeHandler();

        try {
            for (Handler handler : rootHandlers) {
                rootLogger.removeHandler(handler);
            }
            rootLogger.addHandler(bridgeHandler);
        }
        catch (RuntimeException | Error e) {
            restore(e);
            throw e;
        }
    }

    void restore(Throwable failure) {
        try {
            restore();
        }
        catch (RuntimeException | Error e) {
            failure.addSuppressed(e);
        }
    }

    void restore() {
        if (rootLogger == null) {
            return;
        }

        if (bridgeHandler != null) {
            rootLogger.removeHandler(bridgeHandler);
        }

        for (Handler handler : rootHandlers) {
            if (!contains(rootLogger.getHandlers(), handler)) {
                rootLogger.addHandler(handler);
            }
        }

        bridgeHandler = null;
        rootHandlers = null;
        rootLogger = null;
    }

    private static boolean contains(Handler[] handlers, Handler target) {
        for (Handler handler : handlers) {
            if (handler == target) {
                return true;
            }
        }

        return false;
    }

}

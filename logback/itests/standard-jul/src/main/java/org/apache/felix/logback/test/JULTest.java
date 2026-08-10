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
package org.apache.felix.logback.test;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.felix.logback.test.helper.LogTestHelper;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

public class JULTest extends LogTestHelper {

    @Test
    public void test() {
        long time = System.nanoTime();
        Logger logger = Logger.getLogger(getClass().getName());
        if (logger.isLoggable(Level.INFO)) {
            logger.info(time + "");
        }
        assertLog("INFO", getClass().getName(), time);
    }

    @Test
    public void testRootHandlersAreRestored() throws Exception {
        Logger rootLogger = Logger.getLogger("");
        BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
        Bundle logbackBundle = getBundle(context, "org.apache.felix.logback");
        boolean restart = logbackBundle.getState() == Bundle.ACTIVE;
        Handler savedHandler = new NoOpHandler();
        Handler addedHandler = new NoOpHandler();

        try {
            if (restart) {
                logbackBundle.stop();
            }

            rootLogger.addHandler(savedHandler);
            logbackBundle.start();

            Handler[] activeHandlers = rootLogger.getHandlers();
            Assert.assertEquals(1, activeHandlers.length);
            Handler bridgeHandler = activeHandlers[0];
            Assert.assertFalse(contains(rootLogger.getHandlers(), savedHandler));

            rootLogger.addHandler(addedHandler);
            logbackBundle.stop();

            Assert.assertFalse(contains(rootLogger.getHandlers(), bridgeHandler));
            Assert.assertTrue(contains(rootLogger.getHandlers(), savedHandler));
            Assert.assertTrue(contains(rootLogger.getHandlers(), addedHandler));
        }
        finally {
            if (logbackBundle.getState() == Bundle.ACTIVE) {
                logbackBundle.stop();
            }

            rootLogger.removeHandler(savedHandler);
            rootLogger.removeHandler(addedHandler);

            if (restart) {
                logbackBundle.start();
            }
        }
    }

    private static Bundle getBundle(BundleContext context, String symbolicName) {
        for (Bundle bundle : context.getBundles()) {
            if (symbolicName.equals(bundle.getSymbolicName())) {
                return bundle;
            }
        }

        throw new AssertionError("Bundle not found: " + symbolicName);
    }

    private static boolean contains(Handler[] handlers, Handler target) {
        for (Handler handler : handlers) {
            if (handler == target) {
                return true;
            }
        }

        return false;
    }

    private static class NoOpHandler extends Handler {

        @Override
        public void publish(LogRecord record) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

    }

}

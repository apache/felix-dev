/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.http.samples.whiteboard;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;

/**
 * Abstract class that hides all Jetty Websocket specifics and provides a way for the developer to focus on the actual WebSocket implementation.
 */
public abstract class FelixJettyWebSocketServlet extends JettyWebSocketServlet {
    private final AtomicBoolean myFirstInitCall = new AtomicBoolean(true);
    private final CountDownLatch myInitBarrier = new CountDownLatch(1);

    public final void init() {
        // nothing, see delayed init below in service method
        // this is a workaround as stated in https://issues.apache.org/jira/browse/FELIX-5310
    }

    @Override
    public void service(final ServletRequest req, final ServletResponse res) throws ServletException, IOException {
        if (myFirstInitCall.compareAndSet(true, false)) {
            try {
                super.init();
            } finally {
                myInitBarrier.countDown();
            }
        } else {
            try {
                myInitBarrier.await();
            } catch (final InterruptedException e) {
                throw new ServletException("Timed out waiting for initialisation", e);
            }
        }

        super.service(req, res);
    }

    /**
     * Cleanup method.
     */
    @Override
    public final void destroy() {
        // only call destroy when the servlet has been initialized
        if (!myFirstInitCall.get()) {
            // This is required because WebSocketServlet needs to have it's destroy() method called as well
            // Causes NPE otherwise when calling an WS endpoint
            super.destroy();
        }
    }
}

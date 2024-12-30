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

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;

import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketOpen;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

/**
 * Example of a WebSocket servlet that uses the Jetty WebSocket API, and is registered by extending JettyWebSocketServlet.
 * It does respect the path this servlet is registered to.
 * Requires setting `org.apache.felix.jetty.websocket.enable=true`.
 */
public class TestWebSocketServletAlternative extends JettyWebSocketServlet {
    private final String name;

    public TestWebSocketServletAlternative(String name) {
        this.name = name;
    }

    private void doLog(String message) {
        System.out.println("## [" + this.name + "] " + message);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        doLog("Init with config [" + config + "]");
        super.init(config);
    }

    @Override
    protected void configure(JettyWebSocketServletFactory factory) {
        factory.register(TestWebSocket.class);
    }

    @WebSocket
    public static class TestWebSocket {
        @OnWebSocketMessage
        public void onText(final Session session, final String message) {
            doLog("Received message: " + message);
        }

        @OnWebSocketOpen
        public void onOpen(final Session session) {
            doLog("Opened session: " + session);

            // send a message to the client
            session.sendText("Hello from server", Callback.NOOP);
        }

        @OnWebSocketError
        public void onError(final Session session, final Throwable error) {
            doLog("Error on session: " + session + " - " + error);
        }

        @OnWebSocketClose
        public void onClose(final Session session, final int statusCode, final String reason) {
            doLog("Closed session: " + session + " - " + statusCode + " - " + reason);
        }

        private void doLog(String message) {
            System.out.println("## [" + this.getClass() + "] " + message);
        }
    }
}
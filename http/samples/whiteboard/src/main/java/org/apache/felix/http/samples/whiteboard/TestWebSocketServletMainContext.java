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
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;

import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServerContainer;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketOpen;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

/**
 * Example of a WebSocket servlet that uses the Jetty WebSocket API, and is registered on the main servlet context.
 * It does not respect the path this servlet is registered to, but requires no further workarounds.
 * Setting `org.apache.felix.jetty.ee10.websocket.enable=true` is enough.
 */
public class TestWebSocketServletMainContext extends HttpServlet {
    private final String name;

    public TestWebSocketServletMainContext(String name) {
        this.name = name;
    }

    private void doLog(String message) {
        System.out.println("## [" + this.name + "] " + message);
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // The websocket container is attached to the main servlet context, not the per-bundle one.
        //  Lookup the ServletContext for the context path where the websocket server is attached.
        ServletContext servletContext = config.getServletContext();
        ServletContext rootContext = servletContext.getContext("/");

        // Retrieve the JettyWebSocketServerContainer.
        JettyWebSocketServerContainer container = JettyWebSocketServerContainer.getContainer(rootContext);
        container.addMapping("/websocket/*", (upgradeRequest, upgradeResponse) -> new TestWebSocket());
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
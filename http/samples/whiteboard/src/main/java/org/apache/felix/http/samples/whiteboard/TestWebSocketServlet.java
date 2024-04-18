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
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServerContainer;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory;
import org.eclipse.jetty.ee10.websocket.server.internal.JettyServerFrameHandlerFactory;
import org.eclipse.jetty.ee10.websocket.servlet.WebSocketUpgradeFilter;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketOpen;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.core.server.WebSocketMappings;
import org.eclipse.jetty.websocket.core.server.WebSocketServerComponents;

public class TestWebSocketServlet extends JettyWebSocketServlet {
    private final String name;
    private final AtomicBoolean myFirstInitCall = new AtomicBoolean(true);
    private final CountDownLatch myInitBarrier = new CountDownLatch(1);
    private ServletContext myProxiedContext;
    private ServletContextHandler myServletContextHandler;

    public TestWebSocketServlet(String name) {
        this.name = name;
    }

    @Override
    public void init() throws ServletException {
        doLog("Init, delaying init call until service method is called...");
    }

    private void doLog(String message) {
        System.out.println("## [" + this.name + "] " + message);
    }

    @Override
    protected void configure(JettyWebSocketServletFactory jettyWebSocketServletFactory) {
        doLog("Configuring WebSocket factory");
        jettyWebSocketServletFactory.register(TestWebSocket.class);
    }

    @Override
    public void destroy() {
        doLog("Destroyed servlet");

        // only call destroy when the servlet has been initialized
        if (!myFirstInitCall.get()) {
            // This is required because WebSocketServlet needs to have it's destroy() method called as well
            // Causes NPE otherwise when calling an WS endpoint
            super.destroy();
        }
    }

    // This is a workaround required for WebSockets to work in Jetty12, see
    // https://www.eclipse.org/forums/index.php/t/1110140/
    @Override
    public synchronized ServletContext getServletContext() {
        if (myProxiedContext == null) {
            myProxiedContext = (ServletContext) Proxy.newProxyInstance(JettyWebSocketServlet.class.getClassLoader(),
                    new Class[]{ServletContext.class}, (proxy, method, methodArgs) -> {
                        final ServletContext osgiServletContext = super.getServletContext();
                        if (!"getAttribute".equals(method.getName())) {
                            return method.invoke(osgiServletContext, methodArgs);
                        }

                        final String name = (String) methodArgs[0];
                        Object value = osgiServletContext.getAttribute(name);
                        if (value == null && myProxiedContext != null) {
                            final ServletContext jettyServletContext = myServletContextHandler.getServletContext();
                            value = jettyServletContext.getAttribute(name);
                        }
                        return value;
                    });
        }

        return myProxiedContext;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (myFirstInitCall.compareAndSet(true, false)) {
            try {
                delayedInit();
            } catch (Exception e) {
                System.err.println("Error delayed init: " + e.getMessage());
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

        doLog("Upgrading to websocket request...");

        // Call JettyWebSocketServlet service method to handle upgrade requests
        super.service(req, resp);
    }

    private void delayedInit() throws ServletException {
        doLog("Delayed init...");

        // Make sure WebSockets are enabled in Jetty12
        ensureWebSocketsInitialized();

        // Overide the TCCL so that the internal factory can be found
        // Jetty tries to use ServiceLoader, and their fallback is to
        // use TCCL, it would be better if we could provide a loader...
        final Thread currentThread = Thread.currentThread();
        final ClassLoader tccl = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(JettyWebSocketServlet.class.getClassLoader());
        try {
            super.init();
        } finally {
            currentThread.setContextClassLoader(tccl);
        }
    }

    private void ensureWebSocketsInitialized() {
        doLog("Ensuring websockets");
        final ServletContext osgiServletContext = getServletContext();
        myServletContextHandler = ServletContextHandler.getServletContextHandler(osgiServletContext, "WebSockets");

        final JettyWebSocketServerContainer serverContainer = JettyWebSocketServerContainer
                .getContainer(osgiServletContext);
        if (serverContainer == null) {
            // Ensure WebSocket components are initialized in Jetty12
            final ServletContext jettyServletContext = myServletContextHandler.getServletContext();
            WebSocketServerComponents.ensureWebSocketComponents(myServletContextHandler.getServer(),
                    myServletContextHandler);
            WebSocketUpgradeFilter.ensureFilter(jettyServletContext);
            WebSocketMappings.ensureMappings(myServletContextHandler);
            JettyServerFrameHandlerFactory.getFactory(jettyServletContext);
            JettyWebSocketServerContainer.ensureContainer(jettyServletContext);
        }
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

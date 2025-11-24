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
package org.apache.felix.http.jetty.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.awaitility.Awaitility;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.server.JettyWebSocketServerContainer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;

/**
 *
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class JettySpecificWebsocketIT extends AbstractJettyTestSupport {

    @Inject
    protected BundleContext bundleContext;

    @Override
    protected Option[] additionalOptions() throws IOException {
        String jettyVersion = System.getProperty("jetty.version", JETTY_VERSION);
        return new Option[] {
                spifly(),

                // bundles for the server side
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-webapp").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("websocket-core-common").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("websocket-core-server").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("websocket-jetty-api").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("websocket-jetty-common").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("websocket-jetty-server").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("websocket-servlet").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-xml").version(jettyVersion),

                // additional bundles for the client side
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-alpn-client").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-client").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("websocket-core-client").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("websocket-jetty-client").version(jettyVersion)
        };
    }

    @Override
    protected Option felixHttpConfig(int httpPort) {
        return newConfiguration("org.apache.felix.http")
                .put("org.osgi.service.http.port", httpPort)
                .put("org.apache.felix.jetty.websocket.enable", true)
                .asOption();
    }


    @Test
    public void testWebSocketConversation() throws Exception {
        assertNotNull(bundleContext);
        bundleContext.registerService(Servlet.class, new MyWebSocketInitServlet(), new Hashtable<>(Map.of(
                HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/mywebsocket1"
                )));

        HttpClientTransportOverHTTP transport = new HttpClientTransportOverHTTP();
        HttpClient httpClient = new org.eclipse.jetty.client.HttpClient(transport);
        WebSocketClient webSocketClient = new WebSocketClient(httpClient);
        webSocketClient.start();

        Object value = bundleContext.getServiceReference(HttpService.class).getProperty("org.osgi.service.http.port");
        int httpPort = Integer.parseInt((String)value);
        URI destUri = new URI(String.format("ws://localhost:%d/mywebsocket1", httpPort));

        MyClientWebSocket clientWebSocket = new MyClientWebSocket();
        ClientUpgradeRequest request = new ClientUpgradeRequest();
        CompletableFuture<Session> future = webSocketClient.connect(clientWebSocket, destUri, request);
        Session session = future.get();
        assertNotNull(session);

        // send a message from the client to the server
        clientWebSocket.sendMessage("Hello WebSocket");

        // wait for the async response from the server
        Awaitility.await("waitForResponse")
            .atMost(Duration.ofSeconds(30))
            .pollDelay(Duration.ofMillis(200))
            .until(() -> clientWebSocket.getLastMessage() != null);
        assertEquals("Hello WebSocket", clientWebSocket.getLastMessage());
    }

    /**
     * A servlet that declares the websocket during init
     */
    private static final class MyWebSocketInitServlet extends HttpServlet {
        private static final long serialVersionUID = -6893620059263229183L;

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);

            // Retrieve the JettyWebSocketServerContainer.
            ServletContext servletContext = config.getServletContext();
            JettyWebSocketServerContainer container = JettyWebSocketServerContainer.getContainer(servletContext);
            assertNotNull(container);
            container.addMapping("/mywebsocket1", (upgradeRequest, upgradeResponse) -> new MyServerWebSocket());
        }
    }

    /**
     * WebSocket handler for the client side
     */
    @WebSocket(maxTextMessageSize = 64 * 1024)
    public static class MyClientWebSocket {
        private Session session;
        private String lastMessage;

        public String getLastMessage() {
            return lastMessage;
        }

        @OnWebSocketConnect
        public void onConnect(Session session) {
            this.session = session;
        }

        /**
         * Send a message to the server side
         * @param msg the message to send
         */
        public void sendMessage(String msg) {
            this.session.getRemote().sendString(msg, WriteCallback.NOOP);
        }

        /**
         * Receive a message from the server side
         * @param msg the message
         */
        @OnWebSocketMessage
        public void onMessage(String msg) {
            lastMessage = msg;
        }
    }

    /**
     * WebSocket handler for the server side
     */
    @WebSocket(maxTextMessageSize = 64 * 1024)
    public static class MyServerWebSocket {
        /**
         * Receive message sent from the client
         * 
         * @param session the session
         * @param message the message
         */
        @OnWebSocketMessage
        public void onText(Session session, String message) {
            // echo a response back to the client 
            session.getRemote().sendString(message, WriteCallback.NOOP);
        }
    }

}

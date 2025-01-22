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
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;

import org.apache.felix.http.javaxwrappers.ServletWrapper;
import org.awaitility.Awaitility;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServerContainer;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketOpen;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;

/**
 *
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class JettySpecificWebsocketIT extends AbstractJettyTestSupport {

    @Inject
    protected BundleContext bundleContext;

    @Inject
    protected HttpService httpService;

    @Override
    protected Option[] additionalOptions() throws IOException {
        String jettyVersion = System.getProperty("jetty.version", JETTY_VERSION);
        return new Option[] {
                spifly(),

                // bundles for the server side
                mavenBundle().groupId("org.eclipse.jetty.ee10").artifactId("jetty-ee10-webapp").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("jetty-websocket-core-common").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("jetty-websocket-core-server").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("jetty-websocket-jetty-api").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("jetty-websocket-jetty-common").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("jetty-websocket-jetty-server").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.ee10.websocket").artifactId("jetty-ee10-websocket-servlet").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.ee10.websocket").artifactId("jetty-ee10-websocket-jetty-server").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-xml").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-ee").version(jettyVersion),

                // additional bundles for the client side
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-alpn-client").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-client").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("jetty-websocket-core-client").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.websocket").artifactId("jetty-websocket-jetty-client").version(jettyVersion)
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
                HTTP_WHITEBOARD_SERVLET_PATTERN, "/mywebsocket1"
        )));

        assertWebSocketResponse("mywebsocket1");
    }

    @Test
    public void testWebSocketServletWhiteboard() throws Exception {
        final JettyWebSocketServlet webSocketServlet = new JettyWebSocketServlet() {
            @Override
            protected void configure(JettyWebSocketServletFactory jettyWebSocketServletFactory) {
                jettyWebSocketServletFactory.register(MyServerWebSocket.class);
            }
        };
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(HTTP_WHITEBOARD_SERVLET_PATTERN, "/websocketservletwhiteboard");
        bundleContext.registerService(Servlet.class, webSocketServlet, props);

        assertWebSocketResponse("websocketservletwhiteboard");
    }

    @Test
    public void testWebSocketServletHttpService() throws Exception {
        final JettyWebSocketServlet webSocketServlet = new JettyWebSocketServlet() {
            @Override
            protected void configure(JettyWebSocketServletFactory jettyWebSocketServletFactory) {
                jettyWebSocketServletFactory.register(MyServerWebSocket.class);
            }
        };

        httpService.registerServlet("/websocketservlethttpservice", new ServletWrapper(webSocketServlet), null, null);

        assertWebSocketResponse("websocketservlethttpservice");
    }

    private void assertWebSocketResponse(String servletPath) throws Exception {
        HttpClientTransportOverHTTP transport = new HttpClientTransportOverHTTP();
        HttpClient httpClient = new org.eclipse.jetty.client.HttpClient(transport);
        WebSocketClient webSocketClient = new WebSocketClient(httpClient);
        webSocketClient.start();

        Object value = bundleContext.getServiceReference(HttpService.class).getProperty("org.osgi.service.http.port");
        int httpPort = Integer.parseInt((String)value);
        URI destUri = new URI(String.format("ws://localhost:%d/%s", httpPort, servletPath));

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

            //  Lookup the ServletContext for the context path where the websocket server is attached.
            ServletContext servletContext = config.getServletContext();

            // Retrieve the JettyWebSocketServerContainer.
            JettyWebSocketServerContainer container = JettyWebSocketServerContainer.getContainer(servletContext);
            assertNotNull(container);
            container.addMapping("/mywebsocket1", (upgradeRequest, upgradeResponse) -> new MyServerWebSocket());
        }
    }

    /**
     * WebSocket handler for the client side
     */
    @WebSocket()
    public static class MyClientWebSocket {
        private Session session;
        private String lastMessage;

        public String getLastMessage() {
            return lastMessage;
        }

        @OnWebSocketOpen
        public void onConnect(Session session) {
            this.session = session;
        }

        /**
         * Send a message to the server side
         * @param msg the message to send
         */
        public void sendMessage(String msg) {
            this.session.sendText(msg, Callback.NOOP);
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
    @WebSocket()
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
            session.sendText(message, Callback.NOOP);
        }
    }

}
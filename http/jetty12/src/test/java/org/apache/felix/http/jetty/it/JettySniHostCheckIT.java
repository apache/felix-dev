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

import javax.inject.Inject;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.PathUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

/**
 * Integration test for org.apache.felix.https.ssl.sniHostCheck (FELIX-6846).
 *
 * With sniHostCheck=true (the default), Jetty checks that the certificate
 * presented to the client matches the Host header. A Host header that does not
 * match the certificate CN/SAN results in a 400 Bad Request.
 *
 * With sniHostCheck=false that check is skipped and every request is accepted.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class JettySniHostCheckIT extends AbstractJettyTestSupport {

    @Inject
    protected BundleContext bundleContext;

    @Override
    protected Option[] additionalOptions() throws IOException {
        String jettyVersion = System.getProperty("jetty.version", JETTY_VERSION);
        return new Option[] {
                spifly(),

                // bundles for the server side
                mavenBundle().groupId("org.eclipse.jetty.ee11").artifactId("jetty-ee11-webapp").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.ee11").artifactId("jetty-ee11-servlet").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-xml").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.compression").artifactId("jetty-compression-common").version(jettyVersion),

                // additional bundles for the client side
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-alpn-client").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-client").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.compression").artifactId("jetty-compression-gzip").version(jettyVersion)
        };
    }

    @Override
    protected Option felixHttpConfig(int httpPort) {
        String keystorePath = PathUtils.getBaseDir() + "/src/test/resources/test-keystore.p12";
        return newConfiguration("org.apache.felix.http")
                .put("org.osgi.service.http.port", httpPort)
                .put("org.osgi.service.http.port.secure", findFreePort())
                .put("org.apache.felix.https.enable", "true")
                .put("org.apache.felix.https.keystore", keystorePath)
                .put("org.apache.felix.https.keystore.password", "testpassword")
                .put("org.apache.felix.https.keystore.key.password", "testpassword")
                // sniHostCheck defaults to true — no explicit property needed
                .asOption();
    }

    @Before
    public void setup() {
        assertNotNull(bundleContext);
        bundleContext.registerService(Servlet.class, new OkServlet(), new Hashtable<>(Map.of(
                HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/*"
        )));
    }

    @Test
    public void testMatchingHostHeaderIsAccepted() throws Exception {
        try (HttpClient httpClient = newTrustAllHttpsClient()) {
            httpClient.start();
            // Host header matches the certificate CN/SAN — should be accepted
            ContentResponse response = httpClient.GET(new URI(String.format("https://localhost:%d/test", getHttpsPort())));
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    public void testMismatchedHostHeaderIsRejected() throws Exception {
        try (HttpClient httpClient = newTrustAllHttpsClient()) {
            httpClient.start();
            // Override Host header so it does not match the certificate (CN=localhost)
            ContentResponse response = httpClient.newRequest(new URI(String.format("https://localhost:%d/test", getHttpsPort())))
                    .headers(h -> h.put(HttpHeader.HOST, "wrong.example.org"))
                    .send();
            assertEquals(400, response.getStatus());
        }
    }

    private HttpClient newTrustAllHttpsClient() {
        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setTrustAll(true);
        ClientConnector clientConnector = new ClientConnector();
        clientConnector.setSslContextFactory(sslContextFactory);
        return new HttpClient(new HttpClientTransportOverHTTP(clientConnector));
    }

    private int getHttpsPort() {
        // HTTPS is enabled via ConfigAdmin after initial startup, which restarts Jetty
        // and briefly unregisters the HttpService. Wait for it to come back.
        Awaitility.await("httpServiceRegistered")
                .atMost(Duration.ofSeconds(30))
                .until(() -> bundleContext.getServiceReference(HttpService.class) != null);
        Object value = bundleContext.getServiceReference(HttpService.class).getProperty("org.osgi.service.http.port.secure");
        return Integer.parseInt((String) value);
    }

    static final class OkServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setStatus(200);
            resp.getWriter().write("OK");
        }
    }
}

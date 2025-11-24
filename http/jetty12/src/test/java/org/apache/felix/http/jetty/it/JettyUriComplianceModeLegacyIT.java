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

import static org.eclipse.jetty.http.UriCompliance.LEGACY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.io.IOException;
import java.net.URI;
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
import org.eclipse.jetty.http.UriCompliance;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class JettyUriComplianceModeLegacyIT extends JettyUriComplianceModeDefaultIT {

    @Override
    protected Option felixHttpConfig(int httpPort) {
        return newConfiguration("org.apache.felix.http")
                .put("org.osgi.service.http.port", httpPort)
                .put("org.eclipse.jetty.UriComplianceMode", LEGACY.getName())
                .asOption();
    }

    @Test
    public void testUriCompliance() throws Exception {
        HttpClientTransportOverHTTP transport = new HttpClientTransportOverHTTP();
        HttpClient httpClient = new HttpClient(transport);
        httpClient.start();

        Object value = bundleContext.getServiceReference(HttpService.class).getProperty("org.osgi.service.http.port");
        int httpPort = Integer.parseInt((String) value);

        URI destUriWorking = new URI(String.format("http://localhost:%d/endpoint/working", httpPort));
        URI destUriAmbigousPath = new URI("http://localhost:" + httpPort + "/endpoint/ambigousPathitem_0_http%3A%2F%2Fwww.test.com%2F0.html/abc");

        ContentResponse response = httpClient.GET(destUriWorking);
        assertEquals(200, response.getStatus());
        assertEquals("OK", response.getContentAsString());

        // no longer blocked due to LEGACY compliance mode
        ContentResponse response2 = httpClient.GET(destUriAmbigousPath);
        assertEquals(200, response2.getStatus());
        assertEquals("OK", response2.getContentAsString());

        httpClient.close();
    }
}
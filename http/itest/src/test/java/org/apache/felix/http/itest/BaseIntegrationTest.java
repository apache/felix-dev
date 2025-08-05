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
package org.apache.felix.http.itest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.Constants.START_LEVEL_SYSTEM_BUNDLES;
import static org.ops4j.pax.exam.Constants.START_LEVEL_TEST_BUNDLE;
import static org.ops4j.pax.exam.CoreOptions.frameworkStartLevel;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Scanner;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Base class for integration tests.
 */
public abstract class BaseIntegrationTest {

    protected static final int DEFAULT_TIMEOUT = 10000;

    protected static final String ORG_APACHE_FELIX_HTTP_JETTY = "org.apache.felix.http.jetty";

    protected void assertContent(int expectedRC, String expected, URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        int rc = conn.getResponseCode();
        assertEquals("Unexpected response code,", expectedRC, rc);

        if (rc >= 200 && rc < 500) {
            try (InputStream is = conn.getInputStream()) {
                assertEquals(expected, slurpAsString(is));
            } finally {
                conn.disconnect();
            }
        } else {
            try (InputStream is = conn.getErrorStream()) {
                assertEquals(expected, slurpAsString(is));
            } finally {
                conn.disconnect();
            }
        }
    }

    protected void assertContent(String expected, URL url) throws IOException {
        assertContent(200, expected, url);
    }

    protected void assertResponseCode(int expected, URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            assertEquals(expected, conn.getResponseCode());
        } finally {
            conn.disconnect();
        }
    }

    protected Dictionary<String, ?> createDictionary(Object... entries) {
        Dictionary<String, Object> props = new Hashtable<>();
        for (int i = 0; i < entries.length; i += 2) {
            String key = (String) entries[i];
            Object value = entries[i + 1];
            props.put(key, value);
        }
        return props;
    }

    protected URL createURL(String path) {
        if (path == null) {
            path = "";
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        int port = Integer.getInteger("org.osgi.service.http.port", 8080);
        try {
            return new URL(String.format("http://localhost:%d/%s", port, path));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    protected String slurpAsString(InputStream is) throws IOException {
        // See <weblogs.java.net/blog/pat/archive/2004/10/stupid_scanner_1.html>
        Scanner scanner = new Scanner(is, "UTF-8");
        try {
            scanner.useDelimiter("\\A");

            return scanner.hasNext() ? scanner.next() : null;
        } finally {
            try {
                scanner.close();
            } catch (Exception e) {
                // Ignore...
            }
        }
    }

    @Inject
    protected volatile BundleContext m_context;

    @Configuration
    public Option[] config() {
        final String localRepo = System.getProperty("maven.repo.local", "");

        return options(
                when( localRepo.length() > 0 ).useOptions(
                        systemProperty("org.ops4j.pax.url.mvn.localRepository").value(localRepo)
                        ),
                //            CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8787"),

                // scavenge sessions every 10 seconds (10 minutes is default in 9.4.x)
                systemProperty("org.eclipse.jetty.servlet.SessionScavengingInterval").value("10"),
                // update pax logging for SLF4J 2
                mavenBundle().groupId("org.ops4j.pax.logging").artifactId("pax-logging-api").version("2.3.0"),                mavenBundle("org.slf4j", "slf4j-api", "2.0.17"),
                mavenBundle("org.apache.sling", "org.apache.sling.commons.johnzon", "1.2.16").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                mavenBundle("commons-io", "commons-io", "2.19.0").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                mavenBundle("commons-fileupload", "commons-fileupload", "1.6.0").startLevel(START_LEVEL_SYSTEM_BUNDLES),

                mavenBundle("org.apache.felix", "org.apache.felix.configadmin").version("1.9.22").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                mavenBundle("org.apache.felix", "org.apache.felix.http.servlet-api", System.getProperty("http.servlet.api.version")).startLevel(START_LEVEL_SYSTEM_BUNDLES),
                mavenBundle("org.apache.felix", System.getProperty("http.jetty.id"), System.getProperty("http.jetty.version")).startLevel(START_LEVEL_SYSTEM_BUNDLES),
                mavenBundle("org.apache.felix", "org.apache.felix.http.whiteboard", "4.0.0").startLevel(START_LEVEL_SYSTEM_BUNDLES),

                mavenBundle("org.apache.httpcomponents", "httpcore-osgi", "4.4.6").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                mavenBundle("org.apache.httpcomponents", "httpclient-osgi", "4.5.3").startLevel(START_LEVEL_SYSTEM_BUNDLES),

                junitBundles(),
                frameworkStartLevel(START_LEVEL_TEST_BUNDLE));
    }

    private final Map<String, ServiceTracker<?, ?>> trackers = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        assertNotNull("No bundle context?!", m_context);
    }

    @After
    public void tearDown() throws Exception {
        synchronized ( trackers ) {
            for(final ServiceTracker<?, ?> entry : trackers.values()) {
                entry.close();
            }
            trackers.clear();
        }
        Bundle bundle = getHttpJettyBundle();
        // Restart the HTTP-service to clean all registrations...
        if (bundle.getState() == Bundle.ACTIVE) {
            bundle.stop();
            bundle.start();
        }
    }

    /**
     * Waits for a service to become available in certain time interval.
     * @param serviceName
     * @return
     * @throws Exception
     */
    protected <T> T awaitService(Class<T> clazz) throws Exception {
        ServiceTracker<T, T> tracker = null;
        tracker = getTracker(clazz);
        return tracker.waitForService(DEFAULT_TIMEOUT);
    }

    /**
     * Return an array of {@code ServiceReference}s for all services for the
     * given serviceName
     * @param serviceName
     * @return Array of {@code ServiceReference}s or {@code null} if no services
     *         are being tracked.
     */
    protected <T> ServiceReference<T>[] getServiceReferences(Class<T> clazz) {
        ServiceTracker<T, T> tracker = getTracker(clazz);
        return tracker.getServiceReferences();
    }

    private <T> ServiceTracker<T, T> getTracker(Class<T> clazz) {
        synchronized ( this.trackers ) {
            ServiceTracker<T, T> tracker = (ServiceTracker<T, T>) trackers.get(clazz.getName());
            if ( tracker == null ) {
                tracker = new ServiceTracker<>(m_context, clazz, null);
                trackers.put(clazz.getName(), tracker);
                tracker.open();
            }
            return tracker;
        }
    }

    protected void configureHttpService(Dictionary<String, ?> props) throws Exception {
        final String pid = "org.apache.felix.http";

        final Collection<ServiceReference<ManagedService>> serviceRefs = m_context.getServiceReferences(ManagedService.class, String.format("(%s=%s)", Constants.SERVICE_PID, pid));
        assertNotNull("Unable to obtain managed configuration for " + pid, serviceRefs);
        assertFalse("Unable to obtain managed configuration for " + pid, serviceRefs.isEmpty());

        for (final ServiceReference<ManagedService> serviceRef : serviceRefs) {
            ManagedService service = m_context.getService(serviceRef);
            try {
                service.updated(props);
            } catch (ConfigurationException ex) {
                fail("Invalid configuration provisioned: " + ex.getMessage());
            } finally {
                m_context.ungetService(serviceRef);
            }
        }
    }

    /**
     * @param bsn
     * @return
     */
    protected Bundle findBundle(String bsn) {
        for (Bundle bundle : m_context.getBundles()) {
            if (bsn.equals(bundle.getSymbolicName()) || (bundle.getSymbolicName() != null && bundle.getSymbolicName().startsWith(bsn))) {
                return bundle;
            }
        }
        return null;
    }

    protected Bundle getHttpJettyBundle() {
        Bundle b = findBundle(ORG_APACHE_FELIX_HTTP_JETTY);
        assertNotNull("Apache Felix Jetty bundle not found. Looking for symbolic name equal to/starting with " + ORG_APACHE_FELIX_HTTP_JETTY, b);
        return b;
    }

    /**
     * Obtains a service without waiting for it to become available.
     * @param serviceName
     * @return
     */
    protected <T> T getService(final Class<T> clazz) {
        final ServiceTracker<T, T> tracker = getTracker(clazz);
        return tracker.getService();
    }
}

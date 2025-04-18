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
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.UrlProvisionOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 *
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class LightClassifierIT extends AbstractJettyTestSupport {

    @Inject
    protected BundleContext bundleContext;

    @Override
    protected Option[] additionalOptions() throws IOException {
        String jettyVersion = System.getProperty("jetty.version", JETTY_VERSION);
        return new Option[] {
                spifly(),

                // Minimum additional jetty dependency bundles
                mavenBundle().groupId("commons-io").artifactId("commons-io").version("2.19.0"),
                mavenBundle().groupId("commons-fileupload").artifactId("commons-fileupload").version("1.5"),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-alpn-java-server").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-alpn-server").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-http").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.http2").artifactId("http2-common").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.http2").artifactId("http2-hpack").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty.http2").artifactId("http2-server").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-io").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-jmx").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-security").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-server").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-servlet").version(jettyVersion),
                mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-util").version(jettyVersion),

                // additional dependencies to verify FELIX-6700
                mavenBundle().groupId("org.owasp.encoder").artifactId("encoder").version("1.2.3"),
                mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.inventory").version("2.0.0"),
                mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.webconsole").version("5.0.2")
        };
    }

    /**
     * Override to use the "light" classifier variant of the test bundle
     */
    @Override
    protected UrlProvisionOption testBundle(String systemProperty) {
        String pathname = System.getProperty(systemProperty);
        pathname = pathname.replace(".jar", "-light.jar");
        final File file = new File(pathname);
        return bundle(file.toURI().toString());
    }

    /**
     * Verify FELIX-6700 by checking that the webconsole bundle was resolved and active
     */
    @Test
    public void testWebConsoleBundleIsActive() throws Exception {
        assertNotNull(bundleContext);
        Optional<Bundle> first = Stream.of(bundleContext.getBundles())
                .filter(b -> "org.apache.felix.webconsole".equals(b.getSymbolicName()))
                .findFirst();
        assertTrue(first.isPresent());
        assertEquals(Bundle.ACTIVE, first.get().getState());
    }

}

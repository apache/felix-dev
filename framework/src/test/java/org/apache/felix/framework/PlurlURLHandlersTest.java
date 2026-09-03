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
package org.apache.felix.framework;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.felix.framework.plurl.Plurl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Constants;

/**
 * Verifies that a running framework registers its URL handling with plurl
 * (FELIX-6759), rather than taking over the java.net.URL singletons by swapping a
 * private static field.
 */
class PlurlURLHandlersTest
{
    private Felix m_felix;
    private File m_cacheDir;

    @BeforeEach
    void setUp() throws Exception
    {
        m_cacheDir = File.createTempFile("felix-cache", ".dir");
        m_cacheDir.delete();
        m_cacheDir.mkdirs();

        Map<String, Object> params = new HashMap<>();
        params.put(Constants.FRAMEWORK_SYSTEMPACKAGES,
            "org.osgi.framework; version=1.4.0,"
            + "org.osgi.service.packageadmin; version=1.2.0,"
            + "org.osgi.service.startlevel; version=1.1.0,"
            + "org.osgi.util.tracker; version=1.3.3,"
            + "org.osgi.service.url; version=1.0.0");
        params.put(Constants.FRAMEWORK_STORAGE, m_cacheDir.getAbsolutePath());

        m_felix = new Felix(params);
        m_felix.init();
        m_felix.start();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (m_felix != null)
        {
            m_felix.stop();
            m_felix.waitForStop(10000);
        }
        deleteDir(m_cacheDir);
    }

    /**
     * The framework must have registered a factory with the plurl router while
     * starting. Before FELIX-6759 nothing called Plurl.install(..), so no registration
     * existed at all.
     */
    @Test
    void registersWithPlurlOnStart()
    {
        assertThat(URLHandlers.getPlurlHandlers(m_felix))
            .as("framework should have registered its URL handling with plurl")
            .isNotNull();
    }

    /**
     * Installing plurl is what makes the plurl: protocol resolvable, so being able to
     * construct such a URL proves the router really took over the JVM factory rather
     * than the registration silently failing.
     */
    @Test
    void plurlRouterIsInstalledInTheJvm() throws Exception
    {
        URL url = new URL("plurl", "op", "plurlForbidNothing");
        assertThat(url.getProtocol()).isEqualTo("plurl");
    }

    /**
     * shouldHandle must claim only classes belonging to this framework instance.
     * Claiming any Felix bundle would mean one framework answering lookups for a
     * bundle resolved in another framework in the same JVM.
     */
    @Test
    void shouldHandleOnlyClaimsThisFrameworksClasses()
    {
        PlurlURLHandlers handlers = URLHandlers.getPlurlHandlers(m_felix);
        assertThat(handlers).isNotNull();

        // Not loaded by any bundle class loader.
        assertThat(handlers.shouldHandle(String.class))
            .as("JDK classes are not owned by a framework").isFalse();
        assertThat(handlers.shouldHandle(getClass()))
            .as("test classes are not loaded from a bundle").isFalse();
        assertThat(handlers.shouldHandle((Class<?>) null))
            .as("null must not be claimed").isFalse();
    }

    /**
     * The router that routes in this JVM must be one that consults
     * shouldHandle(protocol, spec). Felix warns at startup when it is not, because
     * bundle: URLs are then routed to whichever factory registered first; this test
     * pins the capability so that re-vendoring an older plurl fails here rather than
     * silently turning that warning on for every user.
     */
    @Test
    void installedPlurlSupportsSelectionBySpec()
    {
        assertThat(Plurl.capabilities())
            .as("the installed plurl reports what it supports")
            .contains(Plurl.PLURL_CAPABILITY_SELECT_BY_SPEC);
    }

    /**
     * shouldHandle(protocol, spec) must claim only this framework's bundle: URLs.
     * The framework UUID is in the host, and it is the only thing that identifies an
     * owner when the URL is parsed by a caller that is in no bundle.
     */
    @Test
    void shouldHandleOnlyClaimsThisFrameworksBundleUrls()
    {
        PlurlURLHandlers handlers = URLHandlers.getPlurlHandlers(m_felix);
        assertThat(handlers).isNotNull();

        String uuid = m_felix._getProperty(Constants.FRAMEWORK_UUID);
        assertThat(handlers.shouldHandle("bundle", "bundle://" + uuid + "_1.0/resource"))
            .as("this framework's own URL").isTrue();
        assertThat(handlers.shouldHandle("bundle", "bundle://" + uuid + "_1.0:0/resource"))
            .as("host may carry a port").isTrue();
        assertThat(handlers.shouldHandle("bundle", "bundle://someotherframework_1.0/resource"))
            .as("another framework's URL must not be claimed").isFalse();
        assertThat(handlers.shouldHandle("http", "http://" + uuid + "_1.0/resource"))
            .as("only the bundle protocol is claimed").isFalse();
        assertThat(handlers.shouldHandle("bundle", "bundle:relative/resource"))
            .as("a spec with no host cannot be claimed").isFalse();
        assertThat(handlers.shouldHandle("bundle", null))
            .as("null must not be claimed").isFalse();
    }

    private static void deleteDir(File root)
    {
        if (root == null || !root.exists())
        {
            return;
        }
        File[] children = root.listFiles();
        if (children != null)
        {
            for (File child : children)
            {
                deleteDir(child);
            }
        }
        root.delete();
    }
}

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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.AfterEach;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Resource;

class RequirementsCapabilitiesTest
{
    private File tempDir;
    private Framework felix;
    private File cacheDir;

    @BeforeEach
    void setUp() throws Exception
    {
        tempDir = File.createTempFile("felix-temp", ".dir");
        assertThat(tempDir.delete()).as("precondition").isTrue();
        assertThat(tempDir.mkdirs()).as("precondition").isTrue();

        cacheDir = new File(tempDir, "felix-cache");
        assertThat(cacheDir.mkdir()).as("precondition").isTrue();

        String cache = cacheDir.getPath();

        Map<String,String> params = new HashMap<>();
        params.put("felix.cache.profiledir", cache);
        params.put("felix.cache.dir", cache);
        params.put(Constants.FRAMEWORK_STORAGE, cache);

        felix = new Felix(params);
        felix.init();
        felix.start();
    }

    @AfterEach
    void tearDown() throws Exception
    {

        felix.stop(); // Note that this method is async
        felix = null;

        deleteDir(tempDir);
        tempDir = null;
        cacheDir = null;
    }

    @Test
    void identityCapabilityBundleFragment() throws Exception
    {
        String bmf = "Bundle-SymbolicName: cap.bundle\n"
                + "Bundle-Version: 1.2.3.Blah\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Import-Package: org.osgi.framework\n";
        File bundleFile = createBundle(bmf);

        String fmf = "Bundle-SymbolicName: cap.frag\n"
                + "Bundle-Version: 1.0.0\n"
                + "Fragment-Host: cap.bundle\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Export-Package: org.foo.bar;version=\"2.0.0\"\n"
                + "Import-Package: org.osgi.util.tracker\n";
        File fragFile = createBundle(fmf);

        Bundle b = felix.getBundleContext().installBundle(bundleFile.toURI().toASCIIString());
        Bundle f = felix.getBundleContext().installBundle(fragFile.toURI().toASCIIString());

        // Check the bundle capabilities.
        // First check the capabilities on the Bundle Revision, which is available on installed bundles
        BundleRevision bbr = b.adapt(BundleRevision.class);
        List<Capability> bwbCaps = bbr.getCapabilities("osgi.wiring.bundle");
        assertThat(bwbCaps).hasSize(1);

        Map<String, Object> expectedBWBAttrs = new HashMap<>();
        expectedBWBAttrs.put("osgi.wiring.bundle", "cap.bundle");
        expectedBWBAttrs.put("bundle-version", Version.parseVersion("1.2.3.Blah"));
        Capability expectedBWBCap = new TestCapability("osgi.wiring.bundle",
                expectedBWBAttrs, Collections.<String, String>emptyMap());
        assertCapsEquals(expectedBWBCap, bwbCaps.get(0));

        List<Capability> bwhCaps = bbr.getCapabilities("osgi.wiring.host");
        assertThat(bwhCaps).hasSize(1);

        Map<String, Object> expectedBWHAttrs = new HashMap<>();
        expectedBWHAttrs.put("osgi.wiring.host", "cap.bundle");
        expectedBWHAttrs.put("bundle-version", Version.parseVersion("1.2.3.Blah"));
        Capability expectedBWHCap = new TestCapability("osgi.wiring.host",
                expectedBWHAttrs, Collections.<String, String>emptyMap());
        assertCapsEquals(expectedBWHCap, bwhCaps.get(0));

        List<Capability> bwiCaps = bbr.getCapabilities("osgi.identity");
        assertThat(bwiCaps).hasSize(1);

        Map<String, Object> expectedBWIAttrs = new HashMap<>();
        expectedBWIAttrs.put("osgi.identity", "cap.bundle");
        expectedBWIAttrs.put("type", "osgi.bundle");
        expectedBWIAttrs.put("version", Version.parseVersion("1.2.3.Blah"));
        Capability expectedBWICap = new TestCapability("osgi.identity",
                expectedBWIAttrs, Collections.<String, String>emptyMap());
        assertCapsEquals(expectedBWICap, bwiCaps.get(0));

        assertThat(bbr.getCapabilities("osgi.wiring.package").size()).as("The Bundle should not directly expose osgi.wiring.package").isEqualTo(0);

        // Check the fragment's capabilities.
        // First check the capabilities on the Bundle Revision, which is available on installed fragments
        BundleRevision fbr = f.adapt(BundleRevision.class);
        List<Capability> fwpCaps = fbr.getCapabilities("osgi.wiring.package");
        assertThat(fwpCaps).hasSize(1);

        Map<String, Object> expectedFWAttrs = new HashMap<>();
        expectedFWAttrs.put("osgi.wiring.package", "org.foo.bar");
        expectedFWAttrs.put("version", Version.parseVersion("2"));
        expectedFWAttrs.put("bundle-symbolic-name", "cap.frag");
        expectedFWAttrs.put("bundle-version", Version.parseVersion("1.0.0"));
        Capability expectedFWCap = new TestCapability("osgi.wiring.package",
                expectedFWAttrs, Collections.<String, String>emptyMap());
        assertCapsEquals(expectedFWCap, fwpCaps.get(0));

        List<Capability> fiCaps = fbr.getCapabilities("osgi.identity");
        assertThat(fiCaps).hasSize(1);
        Map<String, Object> expectedFIAttrs = new HashMap<>();
        expectedFIAttrs.put("osgi.identity", "cap.frag");
        expectedFIAttrs.put("type", "osgi.fragment");
        expectedFIAttrs.put("version", Version.parseVersion("1.0.0"));
        Capability expectedFICap = new TestCapability("osgi.identity",
                expectedFIAttrs, Collections.<String, String>emptyMap());
        assertCapsEquals(expectedFICap, fiCaps.get(0));

        // Start the bundle. This will make the BundleWiring available on both the bundle and the fragment
        b.start();

        // Check the Bundle Wiring on the fragment. It should only contain the osgi.identity capability
        // All the other capabilities should have migrated to the bundle's BundleWiring.
        BundleWiring fbw = f.adapt(BundleWiring.class);
        List<BundleCapability> fbwCaps = fbw.getCapabilities(null);
        assertThat(fbwCaps.size()).as("Fragment should only have 1 capability: it's osgi.identity").isEqualTo(1);
        assertCapsEquals(expectedFICap, fbwCaps.get(0));

        // Check the Bundle Wiring on the bundle. It should contain all the capabilities originally on the
        // bundle and also contain the osgi.wiring.package capability from the fragment.
        BundleWiring bbw = b.adapt(BundleWiring.class);
        List<BundleCapability> bwbCaps2 = bbw.getCapabilities("osgi.wiring.bundle");
        assertThat(bwbCaps2).hasSize(1);
        assertCapsEquals(expectedBWBCap, bwbCaps2.get(0));
        List<BundleCapability> bwhCaps2 = bbw.getCapabilities("osgi.wiring.host");
        assertThat(bwhCaps2).hasSize(1);
        assertCapsEquals(expectedBWHCap, bwhCaps2.get(0));
        List<BundleCapability> bwiCaps2 = bbw.getCapabilities("osgi.identity");
        assertThat(bwiCaps2).hasSize(1);
        assertCapsEquals(expectedBWICap, bwiCaps2.get(0));
        List<BundleCapability> bwpCaps2 = bbw.getCapabilities("osgi.wiring.package");
        assertThat(bwpCaps2.size()).as("Bundle should have inherited the osgi.wiring.package capability from the fragment").isEqualTo(1);
        assertCapsEquals(expectedFWCap, bwpCaps2.get(0));
    }

    @Test
    void identityCapabilityFrameworkExtension() throws Exception
    {
        String femf = "Bundle-SymbolicName: fram.ext\n"
                + "Bundle-Version: 1.2.3.test\n"
                + "Fragment-Host: system.bundle; extension:=framework\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Export-Package: org.foo.bar;version=\"2.0.0\"\n";
        File feFile = createBundle(femf);

        Bundle fe = felix.getBundleContext().installBundle(feFile.toURI().toASCIIString());

        BundleRevision fbr = fe.adapt(BundleRevision.class);

        List<Capability> feCaps = fbr.getCapabilities("osgi.identity");
        assertThat(feCaps).hasSize(1);
        Map<String, Object> expectedFEAttrs = new HashMap<>();
        expectedFEAttrs.put("osgi.identity", "fram.ext");
        expectedFEAttrs.put("type", "osgi.fragment");
        expectedFEAttrs.put("version", Version.parseVersion("1.2.3.test"));
        Capability expectedFICap = new TestCapability("osgi.identity",
                expectedFEAttrs, Collections.<String, String>emptyMap());
        assertCapsEquals(expectedFICap, feCaps.get(0));
    }

    private File createBundle(String manifest) throws IOException
    {
        File f = File.createTempFile("felix-bundle", ".jar", tempDir);

        Manifest mf = new Manifest(new ByteArrayInputStream(manifest.getBytes("utf-8")));
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        JarOutputStream os = new JarOutputStream(new FileOutputStream(f), mf);
        os.close();
        return f;
    }

    private static void assertCapsEquals(Capability expected, Capability actual)
    {
        assertThat(actual.getNamespace()).isEqualTo(expected.getNamespace());
        assertThat(actual.getAttributes()).containsAllEntriesOf(expected.getAttributes());
        assertThat(actual.getDirectives()).containsAllEntriesOf(expected.getDirectives());

        // We ignore the resource in the comparison
    }

    private static void deleteDir(File root) throws IOException
    {
        if (root.isDirectory())
        {
            for (File file : root.listFiles())
            {
                deleteDir(file);
            }
        }
        assertThat(root.delete()).isTrue();
    }

    static class TestCapability implements Capability
    {
        private final String namespace;
        private final Map<String, Object> attributes;
        private final Map<String, String> directives;

        TestCapability(String ns, Map<String,Object> attrs, Map<String,String> dirs)
        {
            namespace = ns;
            attributes = attrs;
            directives = dirs;
        }

        @Override
		public String getNamespace()
        {
            return namespace;
        }

        @Override
		public Map<String, Object> getAttributes()
        {
            return attributes;
        }

        @Override
		public Map<String, String> getDirectives()
        {
            return directives;
        }

        @Override
		public Resource getResource()
        {
            return null;
        }
    }
}

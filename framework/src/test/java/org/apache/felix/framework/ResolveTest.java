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
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;

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

class ResolveTest
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
    void resolveFragmentWithHost() throws Exception
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

        Bundle h = felix.getBundleContext().installBundle(bundleFile.toURI().toASCIIString());
        Bundle f = felix.getBundleContext().installBundle(fragFile.toURI().toASCIIString());

        assertThat(h.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(f.getState()).isEqualTo(Bundle.INSTALLED);

        felix.adapt(FrameworkWiring.class).resolveBundles(Collections.singletonList(h));

        assertThat(h.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(f.getState()).isEqualTo(Bundle.RESOLVED);
    }

    @Test
    void resolveOnlyMatchingFragmentWithHost() throws Exception
    {
        String bmf = "Bundle-SymbolicName: cap.bundle\n"
            + "Bundle-Version: 1.2.3.Blah\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Import-Package: org.osgi.framework\n";
        File bundleFile = createBundle(bmf);

        String bmfo = "Bundle-SymbolicName: cap.bundleo\n"
            + "Bundle-Version: 1.2.3.Blah\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Import-Package: org.osgi.framework\n";
        File bundleFileO = createBundle(bmfo);

        String fmf = "Bundle-SymbolicName: cap.frag\n"
            + "Bundle-Version: 1.0.0\n"
            + "Fragment-Host: cap.bundle\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Export-Package: org.foo.bar;version=\"2.0.0\"\n"
            + "Import-Package: org.osgi.util.tracker\n";
        File fragFile = createBundle(fmf);

        String fmfo = "Bundle-SymbolicName: cap.frago\n"
            + "Bundle-Version: 1.0.0\n"
            + "Fragment-Host: cap.bundleo\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Export-Package: org.foo.bar;version=\"2.0.0\"\n"
            + "Import-Package: org.osgi.util.tracker\n";
        File fragFileO = createBundle(fmfo);

        Bundle h = felix.getBundleContext().installBundle(bundleFile.toURI().toASCIIString());
        Bundle f = felix.getBundleContext().installBundle(fragFile.toURI().toASCIIString());

        Bundle ho = felix.getBundleContext().installBundle(bundleFileO.toURI().toASCIIString());
        Bundle fo = felix.getBundleContext().installBundle(fragFileO.toURI().toASCIIString());

        assertThat(h.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(f.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(ho.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(fo.getState()).isEqualTo(Bundle.INSTALLED);

        felix.adapt(FrameworkWiring.class).resolveBundles(Collections.singletonList(h));

        assertThat(h.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(f.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(ho.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(fo.getState()).isEqualTo(Bundle.INSTALLED);
    }

    @Test
    void resolveDynamicWithOnlyMatchingFragmentWithHost() throws Exception
    {
        String bmf = "Bundle-SymbolicName: cap.bundle\n"
            + "Bundle-Version: 1.2.3.Blah\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Import-Package: org.osgi.framework\n";
        File bundleFile = createBundle(bmf);

        String bmfo = "Bundle-SymbolicName: cap.bundleo\n"
            + "Bundle-Version: 1.2.3.Blah\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Import-Package: org.osgi.framework\n";
        File bundleFileO = createBundle(bmfo);

        String fmf = "Bundle-SymbolicName: cap.frag\n"
            + "Bundle-Version: 1.0.0\n"
            + "Fragment-Host: cap.bundle\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Export-Package: org.foo.bar;version=\"2.0.0\"\n"
            + "Import-Package: org.osgi.util.tracker,test.baz\n";
        File fragFile = createBundle(fmf);

        String fmfo = "Bundle-SymbolicName: cap.frago\n"
            + "Bundle-Version: 1.0.0\n"
            + "Fragment-Host: cap.bundleo\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Export-Package: org.foo.baz;version=\"2.0.0\"\n"
            + "Import-Package: org.osgi.util.tracker\n";
        File fragFileO = createBundle(fmfo);

        String dynm = "Bundle-SymbolicName: cap.dyn\n"
            + "Bundle-Version: 1.0.0\n"
            + "Bundle-ManifestVersion: 2\n"
            + "DynamicImport-Package: org.foo.*,org.osgi.*\n";
        File dynFile = createBundle(dynm);

        String reqm = "Bundle-SymbolicName: cap.req\n"
            + "Bundle-Version: 1.0.0\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Export-Package: test.baz\n";
        File reqFile = createBundle(reqm);

        String reqnm = "Bundle-SymbolicName: cap.reqn\n"
            + "Bundle-Version: 1.0.0\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Export-Package: test.bazz,org.foo.bar.blub\n";
        File reqnFile = createBundle(reqnm);

        Bundle h = felix.getBundleContext().installBundle(bundleFile.toURI().toASCIIString());
        Bundle f = felix.getBundleContext().installBundle(fragFile.toURI().toASCIIString());

        Bundle ho = felix.getBundleContext().installBundle(bundleFileO.toURI().toASCIIString());
        Bundle fo = felix.getBundleContext().installBundle(fragFileO.toURI().toASCIIString());

        Bundle dyn = felix.getBundleContext().installBundle(dynFile.toURI().toASCIIString());
        Bundle req = felix.getBundleContext().installBundle(reqFile.toURI().toASCIIString());
        Bundle reqn = felix.getBundleContext().installBundle(reqnFile.toURI().toASCIIString());

        assertThat(h.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(f.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(ho.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(fo.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(dyn.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(req.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(reqn.getState()).isEqualTo(Bundle.INSTALLED);

        felix.adapt(FrameworkWiring.class).resolveBundles(Collections.singletonList(dyn));

        assertThat(h.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(f.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(ho.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(fo.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(dyn.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(req.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(reqn.getState()).isEqualTo(Bundle.INSTALLED);

        try
        {
            dyn.loadClass("org.foo.bar.Bar");
            fail("");
        }
        catch (Exception ex)
        {
            // Expected
        }
        assertThat(h.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(f.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(ho.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(fo.getState()).isEqualTo(Bundle.INSTALLED);
        assertThat(dyn.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(req.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(reqn.getState()).isEqualTo(Bundle.INSTALLED);
        List<BundleWire> requiredWires = dyn.adapt(BundleWiring.class).getRequiredWires(BundleRevision.PACKAGE_NAMESPACE);
        assertThat(requiredWires).hasSize(1);
        assertThat(h).isEqualTo(requiredWires.get(0).getProvider().getBundle());

        try
        {
            dyn.loadClass("org.foo.baz.Bar");
            fail("");
        }
        catch (Exception ex)
        {
            // Expected
        }
        assertThat(h.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(f.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(ho.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(fo.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(dyn.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(req.getState()).isEqualTo(Bundle.RESOLVED);
        assertThat(reqn.getState()).isEqualTo(Bundle.INSTALLED);
        requiredWires = dyn.adapt(BundleWiring.class).getRequiredWires(BundleRevision.PACKAGE_NAMESPACE);
        assertThat(requiredWires).hasSize(2);
        assertThat(h).isEqualTo(requiredWires.get(0).getProvider().getBundle());
        assertThat(ho).isEqualTo(requiredWires.get(1).getProvider().getBundle());
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
}

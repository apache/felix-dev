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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.AfterEach;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.service.packageadmin.ExportedPackage;

class PackageAdminImplTest
{
    private File tempDir;
    private Felix felix;
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
    void exportedPackages() throws Exception
    {
        String bmf = "Bundle-SymbolicName: pkg.bundle\n"
                + "Bundle-Version: 1\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Export-Package: org.foo.bundle\n";
        File bundleFile = createBundle(bmf);

        String fmf = "Bundle-SymbolicName: pkg.frag\n"
                + "Bundle-Version: 1\n"
                + "Fragment-Host: pkg.bundle\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Export-Package: org.foo.fragment;version=\"2.0.0\"\n";
        File fragFile = createBundle(fmf);

        Bundle b = felix.getBundleContext().installBundle(bundleFile.toURI().toASCIIString());
        Bundle f = felix.getBundleContext().installBundle(fragFile.toURI().toASCIIString());
        b.start();

        try
        {
            PackageAdminImpl pa = new PackageAdminImpl(felix);
            assertThat(pa.getExportedPackage("org.foo.bundle").getExportingBundle()).isEqualTo(b);
            assertThat(pa.getExportedPackage("org.foo.fragment").getExportingBundle()).isEqualTo(b);

            Set<String> expected = new HashSet<>();
            expected.addAll(Arrays.asList("org.foo.bundle", "org.foo.fragment"));

            Set<String> actual = new HashSet<>();
            for (ExportedPackage ep : pa.getExportedPackages(b))
            {
                actual.add(ep.getName());
                assertThat(ep.getExportingBundle()).isEqualTo(b);
            }
            assertThat(actual).isEqualTo(expected);

            ExportedPackage[] bundlePkgs = pa.getExportedPackages("org.foo.bundle");
            assertThat(bundlePkgs.length).isEqualTo(1);
            assertThat(bundlePkgs[0].getExportingBundle()).isEqualTo(b);
            assertThat(bundlePkgs[0].getVersion()).isEqualTo(new Version("0"));

            ExportedPackage[] fragPkgs = pa.getExportedPackages("org.foo.fragment");
            assertThat(fragPkgs.length).isEqualTo(1);
            assertThat(fragPkgs[0].getExportingBundle()).as("The fragment package should be exposed through the bundle").isEqualTo(b);
            assertThat(fragPkgs[0].getVersion()).isEqualTo(new Version("2"));
        }
        finally
        {
            b.stop();
            b.uninstall();
            f.uninstall();
        }
    }

    private File createBundle(String manifest) throws IOException
    {
        File f = File.createTempFile("felix-bundle", ".jar", tempDir);

        Manifest mf = new Manifest(new ByteArrayInputStream(manifest.getBytes("UTF-8")));
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        JarOutputStream os = new JarOutputStream(new FileOutputStream(f), mf);
        os.close();
        return f;
    }

    private static void deleteDir(File root) throws IOException
    {
        if (root.isDirectory())
        {
            File[] files = root.listFiles();
            if (files != null)
            {
                for (File file : files)
                {
                    deleteDir(file);
                }
            }
        }
        root.delete();
    }
}

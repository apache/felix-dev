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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.AfterEach;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleWiring;

class ResourceLoadingTest
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
    void resourceLoadingWithHash() throws Exception
    {
        String bmf = "Bundle-SymbolicName: cap.bundle\n"
            + "Bundle-Version: 1.2.3.Blah\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Import-Package: org.osgi.framework\n";
        File bundleFile = File.createTempFile("felix-bundle", ".jar", tempDir);

        Manifest mf = new Manifest(new ByteArrayInputStream(bmf.getBytes("utf-8")));
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        JarOutputStream os = new JarOutputStream(new FileOutputStream(bundleFile), mf);

        String name = "bla/ bli/@@€ ß&&????ßß &&$$\" \'##&&/ äöüß/ @@ foo#bar#baz ?a=a.txt?d=ä#dlksl";
        os.putNextEntry(new ZipEntry(name));
        os.write("This is a Test".getBytes());
        os.close();

        Bundle testBundle = felix.getBundleContext().installBundle(bundleFile.toURI().toASCIIString());

        testBundle.start();

        assertThat(testBundle.getState()).isEqualTo(Bundle.ACTIVE);
        assertThat(testBundle.getResource(name)).isNotNull();
        assertThat(testBundle.getEntry(name)).isNotNull();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testBundle.getResource(name).openStream())))
        {
            assertThat(reader.readLine()).isEqualTo("This is a Test");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testBundle.getEntry(name).openStream())))
        {
            assertThat(reader.readLine()).isEqualTo("This is a Test");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testBundle.adapt(BundleWiring.class).getClassLoader().getResourceAsStream(name))))
        {
            assertThat(reader.readLine()).isEqualTo("This is a Test");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testBundle.adapt(BundleWiring.class).getClassLoader().getResource(name).openStream())))
        {
            assertThat(reader.readLine()).isEqualTo("This is a Test");
        }

        URL url = testBundle.adapt(BundleWiring.class).getClassLoader().getResource(name);

        URL testURL = new URL(url.getProtocol() + "://" +  url.getHost() + ":" +  url.getPort() + "/" + name);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testURL.openStream())))
        {
            assertThat(reader.readLine()).isEqualTo("This is a Test");
        }
    }

    @Test
    void resourceLoadingWithDirectory() throws Exception
    {
        String bmf = "Bundle-SymbolicName: cap.bundle\n"
                + "Bundle-Version: 1.2.3.Blah\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Import-Package: org.osgi.framework\n";
        File bundleFile = File.createTempFile("felix-bundle", ".jar", tempDir);

        Manifest mf = new Manifest(new ByteArrayInputStream(bmf.getBytes("utf-8")));
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        JarOutputStream os = new JarOutputStream(new FileOutputStream(bundleFile), mf);

        String name = "bla/bli/blub";
        os.putNextEntry(new ZipEntry("bla/"));
        os.putNextEntry(new ZipEntry("bla/bli/"));
        os.putNextEntry(new ZipEntry(name));
        os.write("This is a Test".getBytes());
        os.close();

        Bundle testBundle = felix.getBundleContext().installBundle(bundleFile.toURI().toASCIIString());

        testBundle.start();

        assertThat(testBundle.getState()).isEqualTo(Bundle.ACTIVE);
        assertThat(testBundle.getResource("bla").toExternalForm()).endsWith("/");
        assertThat(testBundle.getEntry("bla").toExternalForm()).endsWith("/");
        assertThat(testBundle.adapt(BundleWiring.class).getClassLoader().getResource("bla").toExternalForm()).endsWith("/");
        assertThat(testBundle.getResource("bla/").toExternalForm()).endsWith("/");
        assertThat(testBundle.getEntry("bla/").toExternalForm()).endsWith("/");
        assertThat(testBundle.adapt(BundleWiring.class).getClassLoader().getResource("bla/").toExternalForm()).endsWith("/");
        assertThat(testBundle.getResource("bla/bli").toExternalForm()).endsWith("/");
        assertThat(testBundle.getEntry("bla/bli").toExternalForm()).endsWith("/");
        assertThat(testBundle.adapt(BundleWiring.class).getClassLoader().getResource("bla/bli").toExternalForm()).endsWith("/");
        assertThat(testBundle.getResource("bla/bli/").toExternalForm()).endsWith("/");
        assertThat(testBundle.getEntry("bla/bli/").toExternalForm()).endsWith("/");
        assertThat(testBundle.adapt(BundleWiring.class).getClassLoader().getResource("bla/bli/").toExternalForm()).endsWith("/");
        assertThat(testBundle.getResource("bla/bli/blub").toExternalForm()).endsWith("/blub");
        assertThat(testBundle.getEntry("bla/bli/blub").toExternalForm()).endsWith("/blub");
        assertThat(testBundle.adapt(BundleWiring.class).getClassLoader().getResource("bla/bli/blub").toExternalForm()).endsWith("/blub");
    }

    @Test
    void resourceLoadingUsingURLClassLoaderJDK9() throws Exception {
        String bmf = "Bundle-SymbolicName: cap.bundle\n"
            + "Bundle-Version: 1.2.3.Blah\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Import-Package: org.osgi.framework\n";
        File bundleFile = File.createTempFile("felix-bundle", ".jar", tempDir);
        ByteArrayOutputStream embeddedJar1 = new ByteArrayOutputStream();
        ByteArrayOutputStream embeddedJar2 = new ByteArrayOutputStream();

        Manifest mf = new Manifest(new ByteArrayInputStream(bmf.getBytes("utf-8")));
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        JarOutputStream bundle1 = new JarOutputStream(new FileOutputStream(bundleFile), mf);
        JarOutputStream ej1 = new JarOutputStream(embeddedJar1, mf);
        JarOutputStream ej2 = new JarOutputStream(embeddedJar2, mf);

        String ej1Entry = "ej1.txt";
        ej1.putNextEntry(new ZipEntry(ej1Entry));
        ej1.write("This is a Test".getBytes());
        ej1.close();

        String ej2Entry = "ej2.txt";
        ej2.putNextEntry(new ZipEntry(ej2Entry));
        ej2.write("This is a Test".getBytes());
        ej2.close();

        String bundleResource = "b1.txt";
        bundle1.putNextEntry(new ZipEntry(bundleResource));
        bundle1.write("This is a Test".getBytes());
        bundle1.putNextEntry(new ZipEntry("ej1.jar"));
        bundle1.write(embeddedJar1.toByteArray());
        bundle1.putNextEntry(new ZipEntry("ej2.jar"));
        bundle1.write(embeddedJar2.toByteArray());
        bundle1.close();

        Bundle testBundle = felix.getBundleContext().installBundle(bundleFile.toURI().toASCIIString());

        testBundle.start();

        ClassLoader urlClassLoader = createClassLoader(testBundle);

        assertThat(urlClassLoader.getResource("ej2.txt")).isNotNull();
    }

    ClassLoader createClassLoader(Bundle bundle) {
        List<URL> urls = new ArrayList<>();
        Collection<String> resources = bundle.adapt(BundleWiring.class).listResources("/", "*.jar", BundleWiring.LISTRESOURCES_LOCAL);
        for (String resource : resources) {
            urls.add(bundle.getResource(resource));
        }
        // Create the classloader
        return new URLClassLoader(urls.toArray(new URL[urls.size()]), getClass().getClassLoader());
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

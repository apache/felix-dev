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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import junit.framework.TestCase;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleWiring;

public class ResourceLoadingTest extends TestCase
{
    private File tempDir;
    private Framework felix;
    private File cacheDir;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        tempDir = File.createTempFile("felix-temp", ".dir");
        assertTrue("precondition", tempDir.delete());
        assertTrue("precondition", tempDir.mkdirs());

        cacheDir = new File(tempDir, "felix-cache");
        assertTrue("precondition", cacheDir.mkdir());

        String cache = cacheDir.getPath();

        Map<String,String> params = new HashMap<String, String>();
        params.put("felix.cache.profiledir", cache);
        params.put("felix.cache.dir", cache);
        params.put(Constants.FRAMEWORK_STORAGE, cache);

        felix = new Felix(params);
        felix.init();
        felix.start();
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();

        felix.stop(); // Note that this method is async
        felix = null;

        deleteDir(tempDir);
        tempDir = null;
        cacheDir = null;
    }

    public void testResourceLoadingWithHash() throws Exception
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

        assertEquals(Bundle.ACTIVE, testBundle.getState());
        assertNotNull(testBundle.getResource(name));
        assertNotNull(testBundle.getEntry(name));

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testBundle.getResource(name).openStream())))
        {
            assertEquals("This is a Test", reader.readLine());
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testBundle.getEntry(name).openStream())))
        {
            assertEquals("This is a Test", reader.readLine());
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testBundle.adapt(BundleWiring.class).getClassLoader().getResourceAsStream(name))))
        {
            assertEquals("This is a Test", reader.readLine());
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testBundle.adapt(BundleWiring.class).getClassLoader().getResource(name).openStream())))
        {
            assertEquals("This is a Test", reader.readLine());
        }

        URL url = testBundle.adapt(BundleWiring.class).getClassLoader().getResource(name);

        URL testURL = new URL(url.getProtocol() + "://" +  url.getHost() + ":" +  url.getPort() + "/" + name);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testURL.openStream())))
        {
            assertEquals("This is a Test", reader.readLine());
        }
    }

    public void testResourceLoadingWithDirectory() throws Exception
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

        assertEquals(Bundle.ACTIVE, testBundle.getState());
        assertTrue(testBundle.getResource("bla").toExternalForm().endsWith("/"));
        assertTrue(testBundle.getEntry("bla").toExternalForm().endsWith("/"));
        assertTrue(testBundle.adapt(BundleWiring.class).getClassLoader().getResource("bla").toExternalForm().endsWith("/"));
        assertTrue(testBundle.getResource("bla/").toExternalForm().endsWith("/"));
        assertTrue(testBundle.getEntry("bla/").toExternalForm().endsWith("/"));
        assertTrue(testBundle.adapt(BundleWiring.class).getClassLoader().getResource("bla/").toExternalForm().endsWith("/"));
        assertTrue(testBundle.getResource("bla/bli").toExternalForm().endsWith("/"));
        assertTrue(testBundle.getEntry("bla/bli").toExternalForm().endsWith("/"));
        assertTrue(testBundle.adapt(BundleWiring.class).getClassLoader().getResource("bla/bli").toExternalForm().endsWith("/"));
        assertTrue(testBundle.getResource("bla/bli/").toExternalForm().endsWith("/"));
        assertTrue(testBundle.getEntry("bla/bli/").toExternalForm().endsWith("/"));
        assertTrue(testBundle.adapt(BundleWiring.class).getClassLoader().getResource("bla/bli/").toExternalForm().endsWith("/"));
        assertTrue(testBundle.getResource("bla/bli/blub").toExternalForm().endsWith("/blub"));
        assertTrue(testBundle.getEntry("bla/bli/blub").toExternalForm().endsWith("/blub"));
        assertTrue(testBundle.adapt(BundleWiring.class).getClassLoader().getResource("bla/bli/blub").toExternalForm().endsWith("/blub"));
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
        assertTrue(root.delete());
    }
}

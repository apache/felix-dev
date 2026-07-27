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

import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.manifestparser.NativeLibraryClause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.namespace.NativeNamespace;
import org.osgi.framework.wiring.BundleCapability;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * Test Classes for the ExtentionManager
 *
 */
class ExtensionManagerTest {
    private int counter;
    private File testDir;

    @BeforeEach
    void setUp() throws Exception {
        testDir = File.createTempFile("felix-temp", ".dir");
        assertThat(testDir.delete()).as("precondition").isTrue();
        assertThat(testDir.mkdirs()).as("precondition").isTrue();

    }

    @AfterEach
    void tearDown() throws Exception
    {
        deleteDir(testDir);
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

    /**
     *
     *
     * Ensure Native Bundle Capabilities are properly formed based on
     * Framework properties.
     *
     */
    @Test
    void buildNativeCapabilities() {
        Logger logger = new Logger();
        Map<String, String> configMap = new HashMap<>();
        configMap.put(FelixConstants.FELIX_VERSION_PROPERTY, "1.0");
        configMap.put(FelixConstants.FRAMEWORK_LANGUAGE, "en");
        configMap.put(FelixConstants.FRAMEWORK_PROCESSOR, "x86_64");
        configMap.put(FelixConstants.FRAMEWORK_OS_NAME, "windows8");
        configMap.put(FelixConstants.FRAMEWORK_OS_VERSION, "6.3");
        configMap.put(FelixConstants.NATIVE_OS_NAME_ALIAS_PREFIX + ".windows8", "windows 8,win32");
        configMap.put(FelixConstants.NATIVE_PROC_NAME_ALIAS_PREFIX + ".x86-64", "amd64,em64t,x86_64");
        configMap.put(FelixConstants.FRAMEWORK_SYSTEMPACKAGES, "foo");
        NativeLibraryClause.initializeNativeAliases(configMap);
        ExtensionManager extensionManager = new ExtensionManager(logger, configMap, null);

        BundleCapability nativeBundleCapability = extensionManager
                .buildNativeCapabilites(extensionManager.getRevision(), configMap);
        assertThat(nativeBundleCapability.getAttributes().get(
            NativeNamespace.CAPABILITY_LANGUAGE_ATTRIBUTE)).as("Native Language should be same as framework Language").isEqualTo("en");
        assertThat(Arrays.asList("x86-64", "amd64", "em64t", "x86_64").containsAll((List<?>)
            nativeBundleCapability.getAttributes().get(
                NativeNamespace.CAPABILITY_PROCESSOR_ATTRIBUTE))).as("Native Processor should be same as framework Processor").isTrue();
        assertThat(Arrays.asList("windows8", "windows 8", "win32").containsAll((List<?>)
            nativeBundleCapability.getAttributes().get(
                NativeNamespace.CAPABILITY_OSNAME_ATTRIBUTE))).as("Native OS Name should be the same as the framework os name").isTrue();
        assertThat(nativeBundleCapability.getAttributes().get(
            NativeNamespace.CAPABILITY_OSVERSION_ATTRIBUTE)).as("Native OS Version should be the same as the framework OS Version").isEqualTo(new Version("6.3"));
    }

    @Test
    void extensionBundleActivator() throws Exception {
        File cacheDir = new File(testDir, "cache");
        cacheDir.mkdirs();
        String cache = cacheDir.getAbsolutePath();

        Map<String, Object> params = new HashMap<>();
        params.put("felix.cache.profiledir", cache);
        params.put("felix.cache.dir", cache);
        params.put(Constants.FRAMEWORK_STORAGE, cache);

        Framework framework = new Felix(params);
        framework.init();
        framework.start();

        try {
            File ebf = createExtensionBundle();

            assertThat(activatorCalls.length()).as("Precondition").isEqualTo(0);
            framework.getBundleContext().installBundle(
                    ebf.toURI().toURL().toExternalForm());

            assertThat(activatorCalls).hasToString("start");
        } finally {
            framework.stop();
        }

        framework.waitForStop(10000);
        assertThat(activatorCalls).hasToString("startstop");
    }

    @Test
    void extensionBundleEntries() throws Exception {
        File cacheDir = new File(testDir, "cache");
        cacheDir.mkdirs();
        String cache = cacheDir.getAbsolutePath();

        Map<String, Object> params = new HashMap<>();
        params.put("felix.cache.profiledir", cache);
        params.put("felix.cache.dir", cache);
        params.put(Constants.FRAMEWORK_STORAGE, cache);

        Framework framework = new Felix(params);
        framework.init();
        framework.start();

        try {
            File ebf = createExtensionBundle();

            assertThat(framework.getBundleContext().getBundle(0).getEntry("/META-INF/MANIFEST.MF")).as("Precondition").isNull();
            assertThat(framework.getBundleContext().getBundle(0).findEntries("/", "MANIFEST.MF", true)).as("Precondition").isNull();

            framework.getBundleContext().installBundle(
                    ebf.toURI().toURL().toExternalForm());
            assertThat(framework.getBundleContext().getBundle(0).getEntry("/META-INF/MANIFEST.MF")).isNull();
            assertThat(framework.getBundleContext().getBundle(0).findEntries("/", "MANIFEST.MF", true)).isNull();

        } finally {
            framework.stop();
        }

        framework.waitForStop(10000);
    }

    @Test
    void systemBundleHeaders() throws Exception
    {
        File cacheDir = new File(testDir, "cache");
        cacheDir.mkdirs();
        String cache = cacheDir.getAbsolutePath();

        Map<String, Object> params = new HashMap<>();
        params.put("felix.cache.profiledir", cache);
        params.put("felix.cache.dir", cache);
        params.put(Constants.FRAMEWORK_STORAGE, cache);

        Framework framework = new Felix(params);
        framework.init();
        framework.start();

        Version version = new Version(System.getProperty("java.specification.version"));
        String versionString;
        if (version.getMajor() < 9)
        {
            versionString = String.format("0.0.0.JavaSE_001_%03d", version.getMinor() > 6 ? version.getMinor() : 6);
        }
        else
        {
            versionString = String.format("0.0.0.JavaSE_%03d", version.getMajor());
        }
        assertThat(framework.getHeaders().get(Constants.EXPORT_PACKAGE)).contains("java.lang; version=\"" + versionString + "\"");
    }

    private File createExtensionBundle() throws IOException {
        File f = File.createTempFile("felix-bundle" + counter++, ".jar", testDir);

        Manifest mf = new Manifest();
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        mf.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, "extension-bundle");
        mf.getMainAttributes().putValue(Constants.BUNDLE_VERSION, "3.2.1");
        mf.getMainAttributes().putValue(Constants.FRAGMENT_HOST, "system.bundle;extension:=framework");
        mf.getMainAttributes().putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        mf.getMainAttributes().putValue(Constants.EXTENSION_BUNDLE_ACTIVATOR, TestActivator.class.getName());
        JarOutputStream os = new JarOutputStream(new FileOutputStream(f), mf);

        String path = TestActivator.class.getName().replace('.', '/') + ".class";
        os.putNextEntry(new ZipEntry(path));

        InputStream is = TestActivator.class.getClassLoader().getResourceAsStream(path);
        pumpStreams(is, os);

        is.close();
        os.close();
        return f;
    }

    static void pumpStreams(InputStream is, OutputStream os) throws IOException {
        byte[] bytes = new byte[16384];

        int length = 0;
        int offset = 0;

        while ((length = is.read(bytes, offset, bytes.length - offset)) != -1) {
            offset += length;

            if (offset == bytes.length) {
                os.write(bytes, 0, bytes.length);
                offset = 0;
            }
        }
        if (offset != 0) {
            os.write(bytes, 0, offset);
        }
    }

    private static StringBuilder activatorCalls = new StringBuilder();

    public static class TestActivator implements BundleActivator {
        @Override
		public void start(BundleContext context) throws Exception {
            activatorCalls.append("start");
        }

        @Override
		public void stop(BundleContext context) throws Exception {
            activatorCalls.append("stop");
        }
    }
}

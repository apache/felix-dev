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
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import junit.framework.TestCase;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;

import static org.osgi.framework.Constants.SYSTEM_BUNDLE_ID;

public class FrameworkStartLevelImplTest extends TestCase {

    /**
     * This test will install 3 bundles A, B, C. They have to be started due to
     * start
     * levels in order of C, B, A.
     */
    public void testStartLevelStraight() throws Exception {
        File tmpDir = createTmpDir("generated-bundles");
        File cacheDir = createTmpDir("felix-cache");
        File fileA = createBundle("A", tmpDir, TestNoisyBundleActivator.class);
        File fileB = createBundle("B", tmpDir, TestNoisyBundleActivator.class);
        File fileC = createBundle("C", tmpDir, TestNoisyBundleActivator.class);

        Framework framework = createFramework(cacheDir);
        framework.init();
        framework.start();
        FrameworkStartLevel frameworkStartLevel = framework.adapt(FrameworkStartLevel.class);
        frameworkStartLevel.setStartLevel(1);

        Bundle bundleA = framework.getBundleContext().installBundle(fileA.toURI().toString());
        BundleStartLevel bundleAStartLevel = bundleA.adapt(BundleStartLevel.class);
        bundleAStartLevel.setStartLevel(40);
        bundleA.start();

        Bundle bundleB = framework.getBundleContext().installBundle(fileB.toURI().toString());
        BundleStartLevel bundleBStartLevel = bundleB.adapt(BundleStartLevel.class);
        bundleBStartLevel.setStartLevel(30);
        bundleB.start();

        Bundle bundleC = framework.getBundleContext().installBundle(fileC.toURI().toString());
        BundleStartLevel bundleCStartLevel = bundleC.adapt(BundleStartLevel.class);
        bundleCStartLevel.setStartLevel(20);
        bundleC.start();

        frameworkStartLevel.setStartLevel(100);

        Thread.sleep(1000);

        framework.stop();
        deleteTmpDir(cacheDir);
        deleteTmpDir(tmpDir);

        Thread.sleep(1000);
    }

    /**
     * This test will install 4 bundles A, B, C, M. Bundles A, B, C will have an
     * initial start level of 11.
     * Bundle M (start level 10) manipulates start levels in activator for Bundles
     * A, B, C that
     * start order should be C, B, A.
     */
    public void testStartLevelManipulatedByBundle() throws Exception {
        int initialBundleStartLevel = 12; // 12, 25, 37 does fail, >40 does work

        File tmpDir = createTmpDir("generated-bundles");
        File cacheDir = createTmpDir("felix-cache");
        File fileA = createBundle("A", tmpDir, TestNoisyBundleActivator.class);
        File fileB = createBundle("B", tmpDir, TestNoisyBundleActivator.class);
        File fileC = createBundle("C", tmpDir, TestNoisyBundleActivator.class);
        File fileM = createBundle("M", tmpDir, TestManipulatingBundleActivator.class);

        Framework framework = createFramework(cacheDir);
        framework.init();
        framework.start();
        FrameworkStartLevel frameworkStartLevel = framework.adapt(FrameworkStartLevel.class);
        frameworkStartLevel.setStartLevel(1);

        Bundle bundleA = framework.getBundleContext().installBundle(fileA.toURI().toString());
        BundleStartLevel bundleAStartLevel = bundleA.adapt(BundleStartLevel.class);
        bundleAStartLevel.setStartLevel(initialBundleStartLevel);
        bundleA.start();

        Bundle bundleB = framework.getBundleContext().installBundle(fileB.toURI().toString());
        BundleStartLevel bundleBStartLevel = bundleB.adapt(BundleStartLevel.class);
        bundleBStartLevel.setStartLevel(initialBundleStartLevel);
        bundleB.start();

        Bundle bundleC = framework.getBundleContext().installBundle(fileC.toURI().toString());
        BundleStartLevel bundleCStartLevel = bundleC.adapt(BundleStartLevel.class);
        bundleCStartLevel.setStartLevel(initialBundleStartLevel);
        bundleC.start();

        Bundle bundleM = framework.getBundleContext().installBundle(fileM.toURI().toString());
        BundleStartLevel bundleMStartLevel = bundleM.adapt(BundleStartLevel.class);
        bundleMStartLevel.setStartLevel(10);
        bundleM.start();

        frameworkStartLevel.setStartLevel(100);

        Thread.sleep(1000);

        framework.stop();
        deleteTmpDir(cacheDir);
        deleteTmpDir(tmpDir);

        Thread.sleep(1000);
    }

    // helper methods

    private File createTmpDir(String prefix) throws IOException {
        File tmpDir = File.createTempFile(prefix, ".dir");
        // System.out.println("tmpDir=" + tmpDir);

        // File tmpDir = new File("./" + prefix); // use to check bundle content locally
        // in workspace

        deleteTmpDir(tmpDir);
        tmpDir.mkdirs();
        return tmpDir;
    }

    private void deleteTmpDir(File tmpDir) throws IOException {
        if (tmpDir.isDirectory()) {
            for (File file : tmpDir.listFiles()) {
                deleteTmpDir(file);
            }
        }
        tmpDir.delete();
        assertFalse(tmpDir.exists());
    }

    private Framework createFramework(File cacheDir) {
        final String cacheDirPath = cacheDir.getPath();
        final Map<String, String> params = new HashMap<String, String>();
        // OSGi R8 + StartLevels
        params.put(Constants.FRAMEWORK_SYSTEMPACKAGES,
                "org.osgi.framework; version=1.10.0,"
                        + "org.osgi.framework.startlevel; version=1.0.0");
        params.put("felix.cache.profiledir", cacheDirPath);
        params.put("felix.cache.dir", cacheDirPath);
        params.put(Constants.FRAMEWORK_STORAGE, cacheDirPath);

        Framework framework = new Felix(params);
        return framework;
    }

    private File createBundle(String bundleName, File dir, Class<?> activatorClass) throws IOException {
        String mf = "Bundle-SymbolicName: " + bundleName + "\n"
                + "Bundle-Version: 1.0.0\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Import-Package: org.osgi.framework; version=\"[1.10,2.0)\","
                + "org.osgi.framework.startlevel; version=\"[1.0,2.0)\"\n";
        File bundleFile = createBundle(bundleName, mf, dir, activatorClass);
        return bundleFile;
    }

    private File createBundle(String bundleName, String manifest, File tmpDir, Class<?> activatorClass)
            throws IOException {
        File f = File.createTempFile("bundle" + bundleName + "-", ".jar", tmpDir);

        Manifest mf = new Manifest(new ByteArrayInputStream(manifest.getBytes("utf-8")));
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        mf.getMainAttributes().putValue(Constants.BUNDLE_ACTIVATOR, activatorClass.getName());
        JarOutputStream os = new JarOutputStream(new FileOutputStream(f), mf);

        String path = activatorClass.getName().replace('.', '/') + ".class";
        os.putNextEntry(new ZipEntry(path));

        InputStream is = activatorClass.getClassLoader().getResourceAsStream(path);
        byte[] b = new byte[is.available()];
        is.read(b);
        is.close();
        os.write(b);

        os.close();
        return f;
    }

    public static class TestManipulatingBundleActivator implements BundleActivator {
        public void start(BundleContext context) throws Exception {
            dumpState(context, "start");
            manipulateStartLevel(context, "A", 40);
            manipulateStartLevel(context, "B", 30);
            manipulateStartLevel(context, "C", 20);
        }

        public void stop(BundleContext context) throws Exception {
            dumpState(context, "stop");
        }

        private void manipulateStartLevel(BundleContext context, String bsn, int newStartLevel) {
            Bundle framework = context.getBundle(SYSTEM_BUNDLE_ID);

            for (Bundle b : context.getBundles()) {
                if (bsn.equals(b.getSymbolicName())) {
                    BundleStartLevel bundleStartLevel = b.adapt(BundleStartLevel.class);
                    int oldStartLevel = bundleStartLevel.getStartLevel();
                    bundleStartLevel.setStartLevel(newStartLevel);
                    System.out.print("Bundle " + b.toString() + ": manipulateStartLevel() ==> ");
                    System.out.print("bundleStartLevel: old: "
                            + oldStartLevel + ", new: " + newStartLevel + ", ");

                    FrameworkStartLevel frameworkStartLevel = framework.adapt(FrameworkStartLevel.class);
                    System.out.print("frameworkStartLevel: "
                            + frameworkStartLevel.getStartLevel() + ", ");
                    System.out.print("initialBundleStartLevel: "
                            + frameworkStartLevel.getInitialBundleStartLevel() + ", ");
                    System.out.println();
                }
            }
        }

        private void dumpState(BundleContext context, String method) {
            Bundle bundle = context.getBundle();
            System.out.print("Bundle " + bundle.toString() + ": " + method + "() ==> ");
            BundleStartLevel bundleStartLevel = bundle.adapt(BundleStartLevel.class);
            System.out.print("bundleStartLevel: "
                    + bundleStartLevel.getStartLevel() + ", ");

            Bundle framework = context.getBundle(SYSTEM_BUNDLE_ID);
            FrameworkStartLevel frameworkStartLevel = framework.adapt(FrameworkStartLevel.class);
            System.out.print("frameworkStartLevel: "
                    + frameworkStartLevel.getStartLevel() + ", ");
            System.out.print("initialBundleStartLevel: "
                    + frameworkStartLevel.getInitialBundleStartLevel() + ", ");
            System.out.println();
        }

    }

    public static class TestNoisyBundleActivator implements BundleActivator {
        public void start(BundleContext context) throws Exception {
            dumpState(context, "start");
        }

        public void stop(BundleContext context) throws Exception {
            dumpState(context, "stop");
        }

        private void dumpState(BundleContext context, String method) {
            Bundle bundle = context.getBundle();
            System.out.print("Bundle " + bundle.toString() + ": " + method + "() ==> ");
            BundleStartLevel bundleStartLevel = bundle.adapt(BundleStartLevel.class);
            System.out.print("bundleStartLevel: "
                    + bundleStartLevel.getStartLevel() + ", ");

            Bundle framework = context.getBundle(SYSTEM_BUNDLE_ID);
            FrameworkStartLevel frameworkStartLevel = framework.adapt(FrameworkStartLevel.class);
            System.out.print("frameworkStartLevel: "
                    + frameworkStartLevel.getStartLevel() + ", ");
            System.out.print("initialBundleStartLevel: "
                    + frameworkStartLevel.getInitialBundleStartLevel() + ", ");
            System.out.println();
        }
    }
}

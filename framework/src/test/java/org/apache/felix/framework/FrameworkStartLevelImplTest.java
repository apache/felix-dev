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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
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

/*
How to test:

git clone -b feature/test-startlevel-impl https://github.com/JochenHiller/felix-dev
cd felix-dev
cd framework
mvn clean compile
mvn test -Dtest=FrameworkStartLevelImplTest

See as well:
* https://issues.apache.org/jira/browse/FELIX-6586
*/
public class FrameworkStartLevelImplTest extends TestCase {

    /**
     * This test will install 3 bundles A, B, C. They have to be started due to
     * start
     * levels in order of C, B, A.
     */
    public void _testStartLevelStraight() throws Exception {
        redirectSystemOut();

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

        restoreSystemOut();

        System.out.println(getSystemOut()); // enable on demand

        String[] lines = getSystemOutAsArray();

        assertTrue(lines[0].startsWith("Bundle C [3]: start() ==> bundleStartLevel: 20, frameworkStartLevel: 20"));
        assertTrue(lines[1].startsWith("Bundle B [2]: start() ==> bundleStartLevel: 30, frameworkStartLevel: 30"));
        assertTrue(lines[2].startsWith("Bundle A [1]: start() ==> bundleStartLevel: 40, frameworkStartLevel: 40"));
        assertTrue(lines[3].startsWith("Bundle A [1]: stop() ==> bundleStartLevel: 40, frameworkStartLevel: 40"));
        assertTrue(lines[4].startsWith("Bundle B [2]: stop() ==> bundleStartLevel: 30, frameworkStartLevel: 30"));
        assertTrue(lines[5].startsWith("Bundle C [3]: stop() ==> bundleStartLevel: 20, frameworkStartLevel: 20"));
    }

    /**
     * This test will install 4 bundles A, B, C, M. Bundles A, B, C will have an
     * initial start level of 15, M of 10.
     * The framework will be started to start level 12, Bundle M starts and manipulates
     * start levels in activator for Bundles A, B, C that start order should be C, B, A.
     * When start level 12 has been reached, the framework will re-calculate now the start order.
     * When going to start level 100, bundle will be started in correct order C, B, A.
     */

    public void testStartLevelManipulatedByBundle() throws Exception {
        redirectSystemOut();

        int initialBundleStartLevel = 15;

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

        // if we go to startlevel 12, no bundle needs to be started
        // but bundle A-C which have been manipulated are re-calculated when start level 12 
        // has been reached
        frameworkStartLevel.setStartLevel(12);
        Thread.sleep(100);  // give chance to startup

        // now go to final start level, bundles C, B, A will be started
        frameworkStartLevel.setStartLevel(100);

        Thread.sleep(100);  // give chance to startup

        framework.stop();
        deleteTmpDir(cacheDir);
        deleteTmpDir(tmpDir);

        restoreSystemOut();

        System.out.println(getSystemOut()); // enable on demand

        String[] lines = getSystemOutAsArray();
        assertTrue(lines[0].startsWith("Bundle M [4]: start() ==> bundleStartLevel: 10"));

        // @formatter:off
        // Bundle A [1]: manipulateStartLevel() ==> bundleStartLevel: old: 12, new: 40, frameworkStartLevel: 10,
        // Bundle B [2]: manipulateStartLevel() ==> bundleStartLevel: old: 12, new: 30, frameworkStartLevel: 10,
        // Bundle C [3]: manipulateStartLevel() ==> bundleStartLevel: old: 12, new: 20, frameworkStartLevel: 10,
        // @formatter:on 

        assertTrue(lines[4].startsWith("Bundle C [3]: start() ==> bundleStartLevel: 20, frameworkStartLevel: 20"));
        assertTrue(lines[5].startsWith("Bundle B [2]: start() ==> bundleStartLevel: 30, frameworkStartLevel: 30"));
        assertTrue(lines[6].startsWith("Bundle A [1]: start() ==> bundleStartLevel: 40, frameworkStartLevel: 40"));
        assertTrue(lines[7].startsWith("Bundle A [1]: stop() ==> bundleStartLevel: 40, frameworkStartLevel: 40"));
        assertTrue(lines[8].startsWith("Bundle B [2]: stop() ==> bundleStartLevel: 30, frameworkStartLevel: 30"));
        assertTrue(lines[9].startsWith("Bundle C [3]: stop() ==> bundleStartLevel: 20, frameworkStartLevel: 20"));
        assertTrue(lines[10].startsWith("Bundle M [4]: stop() ==> bundleStartLevel: 10, frameworkStartLevel: 10"));
    }

    // helper methods

    PrintStream oldSystemOut = null;
    PrintStream oldSystemErr = null;
    ByteArrayOutputStream redirectedSystemOut = null;
    private void redirectSystemOut() {
        // Create a stream to hold the output
        redirectedSystemOut = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(redirectedSystemOut);
        oldSystemOut = System.out;
        oldSystemErr = System.err;
        System.setOut(ps);
        System.setErr(ps);
    }

    private void restoreSystemOut() {
        System.setErr(oldSystemErr);
        System.setOut(oldSystemOut);
    }

    private String getSystemOut() {
        String s = redirectedSystemOut.toString();
        return s;
    }

    private String[] getSystemOutAsArray() {
        String s = redirectedSystemOut.toString();
        String[] lines = s.split("\\R");
        return lines;
    }

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
            System.out.println();
        }
    }
}

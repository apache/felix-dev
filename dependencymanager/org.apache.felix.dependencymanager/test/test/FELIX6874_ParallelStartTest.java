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
package test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.apache.felix.dm.DependencyManager;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import test.felix6874.UserActivator;

/**
 * FELIX-6874: user bundles started in parallel with the DependencyManager bundle, like the AutoProcessor of
 * Felix PR #236 does: a pool of 8 threads calls start() once on every bundle, while the framework starts.
 *
 * All bundles use the default start level (1). If a pool thread starts a user bundle before the DM bundle
 * is started, the service registry cache initialization must not fail (in DM 5.0.0,
 * ServiceRegistryCacheManager.<clinit> throws a NullPointerException, and the class becomes unusable).
 *
 * This is a race: the scenario is run again with a new embedded Felix framework, at most "felix6874.runs" times
 * (system property, default 200).
 */
public class FELIX6874_ParallelStartTest {

    private static final int RUNS = Integer.getInteger("felix6874.runs", 200);
    private static final int USER_BUNDLES = 100;

    @Rule
    public TemporaryFolder m_tmp = new TemporaryFolder();

    @Test
    public void testUserBundlesStartedInParallelWithDependencyManager() throws Exception {
        test(null);
    }

    @Test
    public void testUserBundlesStartedInParallelWithDependencyManagerAndFilterIndex() throws Exception {
        test("objectClass,user.bundle");
    }

    /**
     * Runs the scenario RUNS times.
     * @param indices the filter indices (org.apache.felix.dependencymanager.filterindex property), or null for no service registry cache
     */
    private void test(String indices) throws Exception {
        File dmJar = createDependencyManagerBundle(m_tmp.newFile("dependencymanager.jar"));
        File userJar = createUserBundle(m_tmp.newFile("user-bundle.jar"));
        File storage = m_tmp.newFolder("felix-cache");

        for (int run = 1; run <= RUNS; run++) {
            Throwable failure = runOnce(run, dmJar, userJar, storage, indices);
            if (failure != null) {
                Assert.fail("FELIX-6874 reproduced (run " + run + "):\n" + toString(failure));
            }
        }
    }

    /** Starts a new framework once. Returns the FELIX-6874 exception thrown by a bundle start, or null. */
    private Throwable runOnce(int run, File dmJar, File userJar, File storage, String indices) throws Exception {
        Map<String, String> config = new HashMap<>();
        if (indices != null) {
            config.put(DependencyManager.SERVICEREGISTRY_CACHE_INDICES, indices);
            // the user bundles add components using the filter index (without index, keep the user activators
            // as light as possible, the race is easier to reproduce)
            config.put(UserActivator.COMPONENTS, "true");
        }
        config.put(Constants.FRAMEWORK_STORAGE, storage.getAbsolutePath());
        config.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        // allow several copies of the user bundle (same symbolic name and version)
        config.put(Constants.FRAMEWORK_BSNVERSION, Constants.FRAMEWORK_BSNVERSION_MULTIPLE);
        // packages imported by DM, provided by the test classpath (osgi.cmpn)
        config.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, "org.osgi.service.cm,org.osgi.service.metatype");
        // errors are collected by the framework listener below
        config.put("felix.log.level", "0");
        Framework framework = ServiceLoader.load(FrameworkFactory.class).iterator().next().newFramework(config);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService pool = null;
        FrameworkEvent stopped;
        try {
            framework.init();
            BundleContext context = framework.getBundleContext();

            // the bundles started by the FelixStartLevel thread report their start errors as framework events
            context.addFrameworkListener(event -> {
                if (event.getType() == FrameworkEvent.ERROR && event.getThrowable() != null) {
                    record(failure, event.getThrowable());
                }
            });

            List<Bundle> bundles = new ArrayList<>();
            for (int i = 1; i <= USER_BUNDLES; i++) {
                bundles.add(install(context, userJar, "user-bundle-" + i));
            }
            bundles.add(install(context, dmJar, "dependencymanager"));

            // AutoProcessor of PR #236: a pool of 8 threads starts the bundles, the launcher does not wait.
            pool = Executors.newFixedThreadPool(8);
            for (Bundle bundle : bundles) {
                pool.execute(() -> {
                    try {
                        bundle.start();
                    } catch (Throwable e) {
                        record(failure, e); // other start errors are ignored, like PR #236 does
                    }
                });
            }
            pool.shutdown();

            framework.start();

            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                Assert.fail("run " + run + ": the bundle starts did not complete");
            }
            if (failure.get() == null) {
                // make sure the scenario really ran: all bundles must be started
                for (Bundle bundle : bundles) {
                    Assert.assertEquals("run " + run + ": bundle " + bundle.getLocation() + " not active",
                        Bundle.ACTIVE, bundle.getState());
                }
                // each user bundle must use the service registry cache (if enabled), and its client component must be started
                for (Bundle bundle : bundles.subList(0, USER_BUNDLES)) {
                    Class<?> activator = bundle.loadClass(UserActivator.class.getName());
                    Assert.assertEquals("run " + run + ": bundle " + bundle.getLocation() + ": service registry cache usage",
                        indices != null, activator.getField("s_cacheUsed").get(null));
                    if (indices != null) {
                        Assert.assertEquals("run " + run + ": bundle " + bundle.getLocation() + ": client component not started",
                            true, activator.getField("s_clientStarted").get(null));
                    }
                }
            }
        } finally {
            if (pool != null) {
                pool.shutdownNow();
            }
            framework.stop();
            stopped = framework.waitForStop(30000);
        }
        // checked after the finally block, so that it does not hide an exception thrown by the try block
        Assert.assertNotEquals("run " + run + ": the framework did not stop", FrameworkEvent.WAIT_TIMEDOUT, stopped.getType());
        return failure.get();
    }

    private static void record(AtomicReference<Throwable> failure, Throwable e) {
        if (toString(e).contains("ServiceRegistryCacheManager")) {
            failure.compareAndSet(null, e);
        }
    }

    private static Bundle install(BundleContext context, File jar, String location) throws Exception {
        try (FileInputStream in = new FileInputStream(jar)) {
            return context.installBundle(location, in);
        }
    }

    /** Creates the DependencyManager bundle from the compiled classes of this module. */
    private static File createDependencyManagerBundle(File jar) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        attrs.putValue(Constants.BUNDLE_SYMBOLICNAME, "org.apache.felix.dependencymanager");
        attrs.putValue(Constants.BUNDLE_ACTIVATOR, "org.apache.felix.dm.impl.Activator");
        attrs.putValue(Constants.EXPORT_PACKAGE, "org.apache.felix.dm,org.apache.felix.dm.compat,org.apache.felix.dm.context,"
            + "org.apache.felix.dm.diagnostics,org.apache.felix.dm.tracker");
        attrs.putValue(Constants.IMPORT_PACKAGE, "org.osgi.framework,org.osgi.util.tracker,org.osgi.service.cm,org.osgi.service.metatype");
        Path classes = classesDir(DependencyManager.class);
        return createBundle(jar, manifest, classes, classes.resolve("org/apache/felix/dm"));
    }

    /** Creates a user bundle, which only contains the UserActivator class. */
    private static File createUserBundle(File jar) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        attrs.putValue(Constants.BUNDLE_SYMBOLICNAME, "felix6874.user");
        attrs.putValue(Constants.BUNDLE_ACTIVATOR, UserActivator.class.getName());
        attrs.putValue(Constants.IMPORT_PACKAGE, "org.apache.felix.dm,org.osgi.framework");
        Path classes = classesDir(UserActivator.class);
        return createBundle(jar, manifest, classes, classes.resolve("test/felix6874"));
    }

    /** Returns the classes directory (target/classes or target/test-classes) containing the given class. */
    private static Path classesDir(Class<?> clazz) throws Exception {
        return new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath();
    }

    /** Creates a jar with the given manifest, and all the files found under root/dir. */
    private static File createBundle(File jar, Manifest manifest, Path root, Path dir) throws IOException {
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar), manifest);
             Stream<Path> files = Files.walk(dir)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                out.putNextEntry(new JarEntry(root.relativize(file).toString().replace(File.separatorChar, '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        return jar;
    }

    private static String toString(Throwable e) {
        StringWriter out = new StringWriter();
        e.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}

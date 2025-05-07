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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.felix.framework.util.StringMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.connect.ModuleConnector;
import org.osgi.framework.connect.ConnectModule;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;

class ConnectTest
{
    @Test
    void simpleConnect() throws Exception
    {
        File cacheDir = File.createTempFile("felix-cache", ".dir");
        cacheDir.delete();
        cacheDir.mkdirs();
        String cache = cacheDir.getPath();

        Map<String, String> params = new HashMap<>();
        params.put("felix.cache.profiledir", cache);
        params.put("felix.cache.dir", cache);
        params.put(Constants.FRAMEWORK_STORAGE, cache);


        FrameworkFactory factory = new FrameworkFactory();
        Framework framework = null;
        try
        {
            final AtomicReference<String> version = new AtomicReference<>("1.0.0");
            ModuleConnector connectFactory = new ModuleConnector()
            {
                @Override
                public void initialize(File storage, Map<String, String> configuration)
                {

                }

                @Override
                public Optional<ConnectModule> connect(String location) throws IllegalStateException
                {
                    return location.startsWith("connect:foo") ? Optional.of(new ConnectModule()
                    {
                        @Override
                        public ConnectContent getContent() throws IOException
                        {
                            return new ConnectContent()
                            {
                                @Override
                                public Optional<ConnectEntry> getEntry(String name)
                                {

                                    return "foo.txt".equals(name) ? Optional.of(
                                        new ConnectEntry()
                                        {
                                            @Override
                                            public String getName()
                                            {
                                                return name;
                                            }

                                            @Override
                                            public long getContentLength()
                                            {
                                                return 0;
                                            }

                                            @Override
                                            public long getLastModified()
                                            {
                                                return 0;
                                            }

                                            @Override
                                            public InputStream getInputStream() throws IOException
                                            {
                                                return null;
                                            }
                                        }
                                    ) : Optional.empty();
                                }

                                @Override
                                public Iterable<String> getEntries()
                                {
                                    return Arrays.asList("foo.txt");
                                }

                                @Override
                                public Optional<ClassLoader> getClassLoader()
                                {
                                    return Optional.of(getClass().getClassLoader());
                                }

                                @Override
                                public void open() throws IOException
                                {
                                }

                                @Override
                                public void close() throws IOException
                                {
                                }

                                @Override
                                public Optional<Map<String, String>> getHeaders()
                                {
                                    Map<String, String> headers = new HashMap<>();
                                    headers.put(Constants.BUNDLE_MANIFESTVERSION, "2");
                                    headers.put(Constants.BUNDLE_SYMBOLICNAME, "connect.foo");
                                    headers.put(Constants.BUNDLE_VERSION, version.get());
                                    headers.put(Constants.EXPORT_PACKAGE, ConnectTest.class.getPackage().getName());
                                    return Optional.of(headers);
                                }
                            };
                        }


                    }) : location.startsWith("connect:extension") ? Optional.of(new ConnectModule()
                    {
                        @Override
                        public ConnectContent getContent() throws IOException
                        {
                            return new ConnectContent()
                            {
                                @Override
                                public Optional<ConnectEntry> getEntry(String name)
                                {

                                    return "foo.txt".equals(name) ? Optional.of(
                                        new ConnectEntry()
                                        {
                                            @Override
                                            public String getName()
                                            {
                                                return name;
                                            }

                                            @Override
                                            public long getContentLength()
                                            {
                                                return 0;
                                            }

                                            @Override
                                            public long getLastModified()
                                            {
                                                return 0;
                                            }

                                            @Override
                                            public InputStream getInputStream() throws IOException
                                            {
                                                return null;
                                            }
                                        }
                                    ) : Optional.empty();
                                }

                                @Override
                                public Iterable<String> getEntries()
                                {
                                    return Arrays.asList("foo.txt");
                                }

                                @Override
                                public Optional<ClassLoader> getClassLoader()
                                {
                                    return Optional.of(getClass().getClassLoader());
                                }

                                @Override
                                public void open() throws IOException
                                {

                                }

                                @Override
                                public void close() throws IOException
                                {

                                }

                                @Override
                                public Optional<Map<String, String>> getHeaders()
                                {
                                    Map<String, String> headers = new HashMap<>();
                                    headers.put(Constants.BUNDLE_MANIFESTVERSION, "2");
                                    headers.put(Constants.BUNDLE_SYMBOLICNAME, "connect.extension");
                                    headers.put(Constants.BUNDLE_VERSION, "1.0.0");
                                    headers.put(Constants.FRAGMENT_HOST, "system.bundle;extension:=framework");
                                    return Optional.of(headers);
                                }
                            };
                        }


                    })
                        : Optional.empty();
                }

                @Override
                public Optional<BundleActivator> newBundleActivator()
                {
                    return Optional.empty();
                }
            };

            framework = factory.newFramework(params, connectFactory);

            framework.start();
            Bundle b = framework.getBundleContext().installBundle("connect:foo");

            assertThat(b).isNotNull();
            assertThat(b.getSymbolicName()).isEqualTo("connect.foo");
            assertThat(framework.getBundleContext().getBundle("connect:foo")).isEqualTo(b);

            assertThat(b.getEntry("foo.txt")).isNotNull();
            assertThat(b.getEntry("bar.txt")).isNull();

            Bundle extension = framework.getBundleContext().installBundle("connect:extension");

            assertThat(extension.getState()).isEqualTo(Bundle.RESOLVED);

            framework.stop();
            framework.waitForStop(1000);
            framework = factory.newFramework(params, connectFactory);
            framework.start();

            b = framework.getBundleContext().getBundle("connect:foo");
            assertThat(b).isNotNull();
            assertThat(b.getSymbolicName()).isEqualTo("connect.foo");
            assertThat(b.getEntry("foo.txt")).isNotNull();
            assertThat(b.getEntry("bar.txt")).isNull();

            assertThat(b.loadClass(ConnectTest.class.getName())).isEqualTo(ConnectTest.class);

            String mf = "Bundle-SymbolicName: connect.test\n"
                + "Bundle-Version: 1.0.0\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Import-Package: " + ConnectTest.class.getPackage().getName()
                + "\n";

            File bundleFile = createBundle(mf, cacheDir, StringMap.class);

            Bundle b2 = framework.getBundleContext().installBundle(bundleFile.toURI().toURL().toString());
            b2.start();
            assertThat(b2.getState()).isEqualTo(Bundle.ACTIVE);
            assertThat(b2.loadClass(ConnectTest.class.getName())).isEqualTo(ConnectTest.class);
            assertNotSame(StringMap.class, b2.loadClass(StringMap.class.getName()));
            assertThat(b.getVersion()).isEqualTo(Version.parseVersion("1.0.0"));
            Version revVersion = b.adapt(BundleRevision.class).getVersion();

            assertThat(b2.adapt(BundleWiring.class).getRequiredWires(PackageNamespace.PACKAGE_NAMESPACE).get(0).getProvider()).isEqualTo(b.adapt(BundleRevision.class));

            version.set("2.0.0");

            b.update();

            final CountDownLatch latch = new CountDownLatch(1);

            framework.adapt(FrameworkWiring.class).refreshBundles(Arrays.asList(b), new FrameworkListener()
            {
                @Override
                public void frameworkEvent(FrameworkEvent event)
                {
                    latch.countDown();
                }
            });

            latch.await(1, TimeUnit.SECONDS);


            assertThat(b.getVersion()).isEqualTo(Version.parseVersion("2.0.0"));

            assertThat(b2.getState()).isEqualTo(Bundle.ACTIVE);
            assertThat(b2.loadClass(ConnectTest.class.getName())).isEqualTo(ConnectTest.class);
            assertNotSame(StringMap.class, b2.loadClass(StringMap.class.getName()));
            assertThat(b2.adapt(BundleWiring.class).getRequiredWires(PackageNamespace.PACKAGE_NAMESPACE).get(0).getProvider()).isEqualTo(b.adapt(BundleRevision.class));
            assertNotSame(revVersion, b2.adapt(BundleWiring.class).getRequiredWires(PackageNamespace.PACKAGE_NAMESPACE).get(0).getProvider());

        }
        finally
        {
            try
            {
                if (framework != null)
                {
                    framework.stop();
                    framework.waitForStop(1000);
                }
            }
            finally {
                MultiReleaseVersionTest.deleteDir(cacheDir);
            }
        }
    }

    private static File createBundle(String manifest, File tempDir, Class<?>... classes) throws IOException
    {
        File f = File.createTempFile("felix-bundle", ".jar", tempDir);

        Manifest mf = new Manifest(new ByteArrayInputStream(manifest.getBytes("utf-8")));
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");

        JarOutputStream os = new JarOutputStream(new FileOutputStream(f), mf);

        for (Class<?> c : classes)
        {
            String path = c.getName().replace('.', '/') + ".class";
            os.putNextEntry(new ZipEntry(path));

            InputStream is = c.getClassLoader().getResourceAsStream(path);
            byte[] b = new byte[is.available()];
            is.read(b);
            is.close();
            os.write(b);
        }

        os.close();
        return f;
    }
}

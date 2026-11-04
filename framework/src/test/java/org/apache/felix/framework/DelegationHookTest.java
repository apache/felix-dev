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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.hooks.delegation.DelegationHook;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;

class DelegationHookTest
{
    private static final String PROVIDED = DelegationHookTest.class.getName() + "$Provided";

    private File tempDir;
    private Framework felix;

    @BeforeEach
    void setUp() throws Exception
    {
        tempDir = Files.createTempDirectory("felix-delegation").toFile();
        Map<String, String> params = new HashMap<>();
        params.put(Constants.FRAMEWORK_STORAGE, new File(tempDir, "cache").getPath());
        params.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        felix = new Felix(params);
        felix.start();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        felix.stop();
        felix.waitForStop(10000);
        deleteRecursive(tempDir);
    }

    @Test
    void classIsFoundAndAskingBundleDependsOnProvider() throws Exception
    {
        Bundle provider = install("provider", true, "data/ignored.txt");
        Bundle asker = install("asker", false);
        provider.start();
        asker.start();

        TestHook hook = new TestHook();
        hook.clazz = provider.loadClass(PROVIDED);
        felix.getBundleContext().registerService(DelegationHook.class, hook, null);

        assertThat(asker.loadClass(PROVIDED)).isSameAs(hook.clazz);
        assertThat(hook.classRequests).containsExactly(PROVIDED);

        Collection<Bundle> closure = felix.adapt(FrameworkWiring.class)
            .getDependencyClosure(Collections.singleton(provider));
        assertThat(closure).contains(provider, asker);
    }

    @Test
    void hookIsNotCalledForJavaPackages() throws Exception
    {
        Bundle asker = install("asker", false);
        asker.start();
        TestHook hook = new TestHook();
        felix.getBundleContext().registerService(DelegationHook.class, hook, null);

        assertThat(asker.loadClass("java.lang.String")).isSameAs(String.class);
        try
        {
            asker.loadClass("java.lang.DoesNotExist");
        }
        catch (ClassNotFoundException ex)
        {
            // expected
        }
        assertThat(hook.classRequests).isEmpty();
    }

    @Test
    void resourcesAreAddedAndSingleResourceOnlyIfNothingFound() throws Exception
    {
        Bundle asker = install("asker", false, "data/own.txt");
        asker.start();
        URL extra = new URL("file:/extra");
        TestHook hook = new TestHook();
        hook.resource = extra;
        hook.resources = Collections.singletonList(extra);
        felix.getBundleContext().registerService(DelegationHook.class, hook, null);

        assertThat(asker.getResource("data/missing.txt")).isEqualTo(extra);

        URL own = asker.getResource("data/own.txt");
        assertThat(own).isNotNull().isNotEqualTo(extra);
        assertThat(hook.resourceRequests).containsExactly("data/missing.txt");

        List<URL> all = Collections.list(asker.adapt(BundleWiring.class)
            .getClassLoader().getResources("data/own.txt"));
        assertThat(all).containsExactly(own, extra);
    }

    @Test
    void throwingHookIsReportedAndIgnored() throws Exception
    {
        Bundle asker = install("asker", false);
        asker.start();
        List<FrameworkEvent> events = new CopyOnWriteArrayList<>();
        felix.getBundleContext().addFrameworkListener((FrameworkListener) events::add);

        TestHook hook = new TestHook();
        hook.failure = new IllegalStateException("boom");
        felix.getBundleContext().registerService(DelegationHook.class, hook, null);

        try
        {
            asker.loadClass("does.not.Exist");
        }
        catch (ClassNotFoundException ex)
        {
            // expected
        }
        assertThat(events).anyMatch(e -> e.getType() == FrameworkEvent.ERROR
            && e.getThrowable() == hook.failure);

        hook.classRequests.clear();
        try
        {
            asker.loadClass("does.not.Exist2");
        }
        catch (ClassNotFoundException ex)
        {
            // expected
        }
        assertThat(hook.classRequests).isEmpty();
    }

    private Bundle install(String name, boolean withProvided, String... resources) throws Exception
    {
        Manifest mf = new Manifest();
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        mf.getMainAttributes().putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        mf.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, name);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bout, mf))
        {
            for (String resource : resources)
            {
                jar.putNextEntry(new ZipEntry(resource));
                jar.write(1);
                jar.closeEntry();
            }
            if (withProvided)
            {
                String path = PROVIDED.replace('.', '/') + ".class";
                jar.putNextEntry(new ZipEntry(path));
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(path))
                {
                    in.transferTo(jar);
                }
                jar.closeEntry();
            }
        }
        return felix.getBundleContext().installBundle(name, new ByteArrayInputStream(bout.toByteArray()));
    }

    private static void deleteRecursive(File f)
    {
        File[] children = f.listFiles();
        if (children != null)
        {
            for (File c : children)
            {
                deleteRecursive(c);
            }
        }
        f.delete();
    }

    public static class Provided
    {
    }

    static class TestHook implements DelegationHook
    {
        final List<String> classRequests = new CopyOnWriteArrayList<>();
        final List<String> resourceRequests = new CopyOnWriteArrayList<>();
        volatile Class<?> clazz;
        volatile URL resource;
        volatile List<URL> resources = new ArrayList<>();
        volatile RuntimeException failure;

        @Override
        public Class<?> findClass(BundleWiring wiring, String name) throws ClassNotFoundException
        {
            classRequests.add(name);
            if (failure != null)
            {
                throw failure;
            }
            return PROVIDED.equals(name) ? clazz : null;
        }

        @Override
        public URL findResource(BundleWiring wiring, String name)
        {
            resourceRequests.add(name);
            return resource;
        }

        @Override
        public Collection<URL> findResources(BundleWiring wiring, String name)
        {
            return resources;
        }
    }
}

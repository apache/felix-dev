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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.dto.BundleStartLevelDTO;
import org.osgi.framework.wiring.dto.BundleRevisionDTO;
import org.osgi.framework.wiring.dto.BundleWiringDTO;
import org.osgi.resource.dto.CapabilityDTO;

class DTOFactoryTest
{
    private int counter;
    private Framework framework;
    private File testDir;

    @BeforeEach
    void setUp() throws Exception
    {
        String path = "/" + getClass().getName().replace('.', '/') + ".class";
        String url = getClass().getResource(path).getFile();
        String baseDir = url.substring(0, url.length() - path.length());
        String rndStr = Long.toString(System.nanoTime(), Character.MAX_RADIX);
        rndStr = rndStr.substring(rndStr.length() - 6, rndStr.length() - 1);
        testDir = new File(baseDir, getClass().getSimpleName() + "_" + rndStr);

        File cacheDir = new File(testDir, "cache");
        cacheDir.mkdirs();
        String cache = cacheDir.getAbsolutePath();

        Map<String, Object> params = new HashMap<>();
        params.put("felix.cache.profiledir", cache);
        params.put("felix.cache.dir", cache);
        params.put(Constants.FRAMEWORK_STORAGE, cache);

        framework = new Felix(params);
        framework.init();
        framework.start();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        framework.stop();
    }

    @Test
    void bundleStartLevelDTO() throws Exception
    {
        String mf = "Bundle-SymbolicName: tb1\n"
                + "Bundle-Version: 1.0.0\n"
                + "Bundle-ManifestVersion: 2\n";
        File bf = createBundle(mf);
        Bundle bundle = framework.getBundleContext().installBundle(bf.toURI().toURL().toExternalForm());

        BundleStartLevel sl = bundle.adapt(BundleStartLevel.class);
        sl.setStartLevel(7);

        BundleStartLevelDTO dto = bundle.adapt(BundleStartLevelDTO.class);
        assertThat(dto.bundle).isEqualTo(bundle.getBundleId());
        assertThat(dto.startLevel).isEqualTo(7);
    }

    @Test
    void serviceReferenceDTOArray() throws Exception
    {
        ServiceRegistration<String> reg = framework.getBundleContext().registerService(String.class, "hi", null);
        Long sid = (Long) reg.getReference().getProperty(Constants.SERVICE_ID);

        ServiceReferenceDTO[] dtos = framework.adapt(ServiceReferenceDTO[].class);
        assertThat(dtos.length > 0).isTrue();

        boolean found = false;
        for (ServiceReferenceDTO dto : dtos)
        {
            if (dto.id == sid)
            {
                found = true;
                assertThat(dto.bundle).isEqualTo(0L);
                assertThat(dto.properties)
                        .containsEntry(Constants.SERVICE_ID, sid)
                        .containsEntry(Constants.OBJECTCLASS, new String []{String.class.getName()});
                assertThat(dto.properties)
                        .containsEntry(Constants.SERVICE_BUNDLEID, 0L)
                        .containsEntry(Constants.SERVICE_SCOPE, Constants.SCOPE_SINGLETON);
                assertThat(dto.usingBundles.length).isEqualTo(0);
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void serviceReferenceDTOArrayStoppedBundle() throws Exception
    {
        String mf = "Bundle-SymbolicName: tb2\n"
                + "Bundle-Version: 1.2.3\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Import-Package: org.osgi.framework;version=\"[1.1,2)\"";
        File bf = createBundle(mf);
        Bundle bundle = framework.getBundleContext().installBundle(bf.toURI().toURL().toExternalForm());

        assertThat(bundle.getBundleContext()).as("Precondition").isNull();
        ServiceReferenceDTO[] dtos = bundle.adapt(ServiceReferenceDTO[].class);

        // Note this is incorrectly tested by the Core Framework R6 CT, which expects an
        // empty array. However this is not correct and recorded as a deviation.
        assertThat(dtos).as("As the bundle is not started, the dtos should be null").isNull();
    }

    @Test
    void bundleRevisionDTO() throws Exception
    {
        String mf = "Bundle-SymbolicName: tb2\n"
                + "Bundle-Version: 1.2.3\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Import-Package: org.osgi.framework;version=\"[1.1,2)\"";
        File bf = createBundle(mf);
        Bundle bundle = framework.getBundleContext().installBundle(bf.toURI().toURL().toExternalForm());
        bundle.start();
        assertThat(bundle.getState()).as("Precondition").isEqualTo(Bundle.ACTIVE);

        BundleRevisionDTO dto = bundle.adapt(BundleRevisionDTO.class);
        assertThat(dto.bundle).isEqualTo(bundle.getBundleId());
        assertThat(dto.id != 0).isTrue();
        assertThat(dto.symbolicName).isEqualTo("tb2");
        assertThat(dto.version).isEqualTo("1.2.3");
        assertThat(dto.type).isEqualTo(0);

        boolean foundBundle = false;
        boolean foundHost = false;
        boolean foundIdentity = false;
        int resource = 0;
        for (CapabilityDTO cap : dto.capabilities)
        {
            assertThat(cap.id != 0).isTrue();
            if (resource == 0)
                resource = cap.resource;
            else
                assertThat(cap.resource).isEqualTo(resource);

            if (BundleNamespace.BUNDLE_NAMESPACE.equals(cap.namespace))
            {
                foundBundle = true;
                assertThat(cap.attributes)
                        .containsEntry(BundleNamespace.BUNDLE_NAMESPACE, "tb2")
                        .containsEntry(BundleNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE, "1.2.3");
            }
            else if (HostNamespace.HOST_NAMESPACE.equals(cap.namespace))
            {
                foundHost = true;
                assertThat(cap.attributes)
                        .containsEntry(HostNamespace.HOST_NAMESPACE, "tb2")
                        .containsEntry(HostNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE, "1.2.3");
            }
            else if (IdentityNamespace.IDENTITY_NAMESPACE.equals(cap.namespace))
            {
                foundIdentity = true;
                assertThat(cap.attributes)
                        .containsEntry(IdentityNamespace.IDENTITY_NAMESPACE, "tb2")
                        .containsEntry(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE, "1.2.3")
                        .containsEntry(IdentityNamespace.CAPABILITY_TYPE_ATTRIBUTE, IdentityNamespace.TYPE_BUNDLE);
            }
        }
        assertThat(foundBundle).isTrue();
        assertThat(foundHost).isTrue();
        assertThat(foundIdentity).isTrue();
    }

    @Test
    void bundleRevisionDTOArray() throws Exception {
        String mf = "Bundle-SymbolicName: tb2\n"
                + "Bundle-Version: 1.2.3\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Import-Package: org.osgi.framework;version=\"[1.1,2)\"";
        File bf = createBundle(mf);
        Bundle bundle = framework.getBundleContext().installBundle(bf.toURI().toURL().toExternalForm());
        bundle.start();
        assertThat(bundle.getState()).as("Precondition").isEqualTo(Bundle.ACTIVE);

        BundleRevisionDTO[] dtos = bundle.adapt(BundleRevisionDTO[].class);
        assertThat(dtos.length).isEqualTo(1);
        BundleRevisionDTO dto = dtos[0];

        assertThat(dto.bundle).isEqualTo(bundle.getBundleId());
        assertThat(dto.id != 0).isTrue();
        assertThat(dto.symbolicName).isEqualTo("tb2");
        assertThat(dto.version).isEqualTo("1.2.3");
        assertThat(dto.type).isEqualTo(0);

        boolean foundBundle = false;
        boolean foundHost = false;
        boolean foundIdentity = false;
        int resource = 0;
        for (CapabilityDTO cap : dto.capabilities)
        {
            assertThat(cap.id != 0).isTrue();
            if (resource == 0)
                resource = cap.resource;
            else
                assertThat(cap.resource).isEqualTo(resource);

            if (BundleNamespace.BUNDLE_NAMESPACE.equals(cap.namespace))
            {
                foundBundle = true;
                assertThat(cap.attributes)
                        .containsEntry(BundleNamespace.BUNDLE_NAMESPACE, "tb2")
                        .containsEntry(BundleNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE, "1.2.3");
            }
            else if (HostNamespace.HOST_NAMESPACE.equals(cap.namespace))
            {
                foundHost = true;
                assertThat(cap.attributes)
                        .containsEntry(HostNamespace.HOST_NAMESPACE, "tb2")
                        .containsEntry(HostNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE, "1.2.3");
            }
            else if (IdentityNamespace.IDENTITY_NAMESPACE.equals(cap.namespace))
            {
                foundIdentity = true;
                assertThat(cap.attributes)
                        .containsEntry(IdentityNamespace.IDENTITY_NAMESPACE, "tb2")
                        .containsEntry(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE, "1.2.3")
                        .containsEntry(IdentityNamespace.CAPABILITY_TYPE_ATTRIBUTE, IdentityNamespace.TYPE_BUNDLE);
            }
        }
        assertThat(foundBundle).isTrue();
        assertThat(foundHost).isTrue();
        assertThat(foundIdentity).isTrue();
    }

    @Test
    void bundleWiringDTO() throws Exception {
        String mf = "Bundle-SymbolicName: tb2\n"
                + "Bundle-Version: 1.2.3\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Import-Package: org.osgi.framework;version=\"[1.1,2)\"";
        File bf = createBundle(mf);
        Bundle bundle = framework.getBundleContext().installBundle(bf.toURI().toURL().toExternalForm());
        bundle.start();
        assertThat(bundle.getState()).as("Precondition").isEqualTo(Bundle.ACTIVE);

        BundleWiringDTO dto = bundle.adapt(BundleWiringDTO.class);
        assertThat(dto.bundle).isEqualTo(bundle.getBundleId());
    }

    private File createBundle(String manifest) throws IOException
    {
        File f = File.createTempFile("felix-bundle" + counter++, ".jar", testDir);

        Manifest mf = new Manifest(new ByteArrayInputStream(manifest.getBytes("utf-8")));
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        JarOutputStream os = new JarOutputStream(new FileOutputStream(f), mf);

        os.close();
        return f;
    }
}

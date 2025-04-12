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
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.felix.framework.cache.BundleArchive;
import org.apache.felix.framework.cache.BundleArchiveRevision;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.bundle.CollisionHook;

class CollisionHookTest
{
    @Test
    void collisionHookInstall() throws Exception
    {
        BundleImpl identicalBundle = mockBundleImpl(1L, "foo", "1.2.1.a");
        BundleImpl differentBundle = mockBundleImpl(2L, "bar", "1.2.1.a");
        BundleImpl originatingBundle = mockBundleImpl(4L, "xyz", "1.0.0");

        CollisionHook testCollisionHook = new CollisionHook()
        {
            @Override
            public void filterCollisions(int operationType, Bundle target, Collection<Bundle> collisionCandidates)
            {
                if ((target.getBundleId() == 4L) && (operationType == CollisionHook.INSTALLING))
                {
                    collisionCandidates.clear();
                }
            }
        };

        @SuppressWarnings("unchecked")
        ServiceReference<CollisionHook> chRef = Mockito.mock(ServiceReference.class);

        // Mock the framework
        StatefulResolver mockResolver = Mockito.mock(StatefulResolver.class);
        Felix felixMock = Mockito.mock(Felix.class);
        HookRegistry hReg = mock(HookRegistry.class);
        when(hReg.getHooks(CollisionHook.class)).thenReturn(Collections.singleton(chRef));
        when(felixMock.getHookRegistry()).thenReturn(hReg);
        Mockito.when(felixMock.getResolver()).thenReturn(mockResolver);
        Mockito.when(felixMock.getBundles()).thenReturn(new Bundle[]
        {
            differentBundle, identicalBundle
        });
        Mockito.when(felixMock.getService(felixMock, chRef, false)).thenReturn(testCollisionHook);

        // Mock the archive of the bundle being installed
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put(Constants.BUNDLE_SYMBOLICNAME, "foo");
        headerMap.put(Constants.BUNDLE_VERSION, "1.2.1.a");
        headerMap.put(Constants.BUNDLE_MANIFESTVERSION, "2");

        BundleArchiveRevision archiveRevision = Mockito.mock(BundleArchiveRevision.class);
        Mockito.when(archiveRevision.getManifestHeader()).thenReturn(headerMap);

        BundleArchive archive = Mockito.mock(BundleArchive.class);
        Mockito.when(archive.getCurrentRevision()).thenReturn(archiveRevision);
        Mockito.when(archive.getId()).thenReturn(3L);

        BundleImpl bi = new BundleImpl(felixMock, originatingBundle, archive);
        assertThat(bi.getBundleId()).isEqualTo(3L);

        // Do the revise operation.
        try
        {
            bi.revise(null, null);
            fail("Should have thrown a BundleException because the installed bundle is not unique");
        }
        catch (BundleException be)
        {
            // good
            assertThat(be.getMessage()).contains("not unique");
        }
    }

    @Test
    void collisionHookUpdate() throws Exception
    {
        BundleImpl identicalBundle = mockBundleImpl(1L, "foo", "1.2.1.a");
        BundleImpl differentBundle = mockBundleImpl(2L, "foo", "1.2.1");

        CollisionHook testCollisionHook = new CollisionHook()
        {
            @Override
            public void filterCollisions(int operationType, Bundle target, Collection<Bundle> collisionCandidates)
            {
                if ((target.getBundleId() == 3L) && (operationType == CollisionHook.UPDATING))
                {
                    collisionCandidates.clear();
                }
            }
        };

        @SuppressWarnings("unchecked")
        ServiceReference<CollisionHook> chRef = Mockito.mock(ServiceReference.class);

        Map<String, Object> config = new HashMap<>();
        config.put(Constants.FRAMEWORK_BSNVERSION, Constants.FRAMEWORK_BSNVERSION_MANAGED);

        // Mock the framework
        StatefulResolver mockResolver = Mockito.mock(StatefulResolver.class);
        Felix felixMock = Mockito.mock(Felix.class);
        Mockito.when(felixMock.getConfig()).thenReturn(config);
        HookRegistry hReg = mock(HookRegistry.class);
        when(hReg.getHooks(CollisionHook.class)).thenReturn(Collections.singleton(chRef));
        when(felixMock.getHookRegistry()).thenReturn(hReg);
        Mockito.when(felixMock.getResolver()).thenReturn(mockResolver);
        Mockito.when(felixMock.getBundles()).thenReturn(new Bundle[]
        {
            differentBundle, identicalBundle
        });
        Mockito.when(felixMock.getService(felixMock, chRef, false)).thenReturn(testCollisionHook);

        // Mock the archive of the bundle being installed
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put(Constants.BUNDLE_SYMBOLICNAME, "zar");
        headerMap.put(Constants.BUNDLE_VERSION, "1.2.1.a");
        headerMap.put(Constants.BUNDLE_MANIFESTVERSION, "2");

        BundleArchiveRevision archiveRevision = Mockito.mock(BundleArchiveRevision.class);
        Mockito.when(archiveRevision.getManifestHeader()).thenReturn(headerMap);

        BundleArchive archive = Mockito.mock(BundleArchive.class);
        Mockito.when(archive.getCurrentRevision()).thenReturn(archiveRevision);
        Mockito.when(archive.getId()).thenReturn(3L);

        BundleImpl bi = new BundleImpl(felixMock, null, archive);
        assertThat(bi.getSymbolicName()).isEqualTo("zar");

        // Do the revise operation, change the bsn to foo
        headerMap.put(Constants.BUNDLE_SYMBOLICNAME, "foo");
        bi.revise(null, null);
        assertThat(bi.getSymbolicName()).isEqualTo("foo");
    }

    @Test
    void collisionNotEnabled() throws Exception
    {
        BundleImpl identicalBundle = mockBundleImpl(1L, "foo", "1.2.1.a");
        BundleImpl differentBundle = mockBundleImpl(2L, "bar", "1.2.1.a");

        CollisionHook testCollisionHook = new CollisionHook()
        {
            @Override
            public void filterCollisions(int operationType, Bundle target, Collection<Bundle> collisionCandidates)
            {
                if ((target.getBundleId() == 3L) && (operationType == CollisionHook.INSTALLING))
                {
                    collisionCandidates.clear();
                }
            }
        };

        @SuppressWarnings("unchecked")
        ServiceReference<CollisionHook> chRef = Mockito.mock(ServiceReference.class);

        Map<String, Object> config = new HashMap<>();
        config.put(Constants.FRAMEWORK_BSNVERSION, Constants.FRAMEWORK_BSNVERSION_SINGLE);

        // Mock the framework
        StatefulResolver mockResolver = Mockito.mock(StatefulResolver.class);
        Felix felixMock = Mockito.mock(Felix.class);
        Mockito.when(felixMock.getConfig()).thenReturn(config);
        HookRegistry hReg = mock(HookRegistry.class);
        when(hReg.getHooks(CollisionHook.class)).thenReturn(Collections.singleton(chRef));
        when(felixMock.getHookRegistry()).thenReturn(hReg);
        Mockito.when(felixMock.getResolver()).thenReturn(mockResolver);
        Mockito.when(felixMock.getBundles()).thenReturn(new Bundle[]
        {
            differentBundle, identicalBundle
        });
        Mockito.when(felixMock.getService(felixMock, chRef, false)).thenReturn(testCollisionHook);

        // Mock the archive of the bundle being installed
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put(Constants.BUNDLE_SYMBOLICNAME, "foo");
        headerMap.put(Constants.BUNDLE_VERSION, "1.2.1.a");
        headerMap.put(Constants.BUNDLE_MANIFESTVERSION, "2");

        BundleArchiveRevision archiveRevision = Mockito.mock(BundleArchiveRevision.class);
        Mockito.when(archiveRevision.getManifestHeader()).thenReturn(headerMap);

        BundleArchive archive = Mockito.mock(BundleArchive.class);
        Mockito.when(archive.getCurrentRevision()).thenReturn(archiveRevision);
        Mockito.when(archive.getId()).thenReturn(3L);

        try
        {
            new BundleImpl(felixMock, new BundleImpl(), archive);
            fail("Should have thrown a BundleException because the collision hook is not enabled");
        }
        catch (BundleException be)
        {
            // good
            assertThat(be.getMessage()).contains("not unique");
        }
    }

    @Test
    void allowMultiple() throws Exception
    {
        BundleImpl identicalBundle = mockBundleImpl(1L, "foo", "1.2.1.a");
        BundleImpl differentBundle = mockBundleImpl(2L, "bar", "1.2.1.a");

        Map<String, Object> config = new HashMap<>();
        config.put(Constants.FRAMEWORK_BSNVERSION, Constants.FRAMEWORK_BSNVERSION_MULTIPLE);

        // Mock the framework
        StatefulResolver mockResolver = Mockito.mock(StatefulResolver.class);
        Felix felixMock = Mockito.mock(Felix.class);
        Mockito.when(felixMock.getConfig()).thenReturn(config);
        Mockito.when(felixMock.getResolver()).thenReturn(mockResolver);
        Mockito.when(felixMock.getBundles()).thenReturn(new Bundle[]
        {
            differentBundle, identicalBundle
        });

        // Mock the archive of the bundle being installed
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put(Constants.BUNDLE_SYMBOLICNAME, "foo");
        headerMap.put(Constants.BUNDLE_VERSION, "1.2.1.a");
        headerMap.put(Constants.BUNDLE_MANIFESTVERSION, "2");

        BundleArchiveRevision archiveRevision = Mockito.mock(BundleArchiveRevision.class);
        Mockito.when(archiveRevision.getManifestHeader()).thenReturn(headerMap);

        BundleArchive archive = Mockito.mock(BundleArchive.class);
        Mockito.when(archive.getCurrentRevision()).thenReturn(archiveRevision);
        Mockito.when(archive.getId()).thenReturn(3L);

        BundleImpl bi = new BundleImpl(felixMock, null, archive);
        assertThat(bi.getBundleId()).isEqualTo(3L);
    }

    @Test
    void noCollisionHook() throws Exception
    {
        BundleImpl identicalBundle = mockBundleImpl(1L, "foo", "1.2.1.a");
        BundleImpl differentBundle = mockBundleImpl(2L, "bar", "1.2.1.a");

        // Mock the framework
        StatefulResolver mockResolver = Mockito.mock(StatefulResolver.class);
        Felix felixMock = Mockito.mock(Felix.class);
        HookRegistry hReg = Mockito.mock(HookRegistry.class);
        Mockito.when(felixMock.getHookRegistry()).thenReturn(hReg);
        Mockito.when(felixMock.getResolver()).thenReturn(mockResolver);
        Mockito.when(felixMock.getBundles()).thenReturn(new Bundle[]
        {
            differentBundle, identicalBundle
        });

        // Mock the archive of the bundle being installed
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put(Constants.BUNDLE_SYMBOLICNAME, "foo");
        headerMap.put(Constants.BUNDLE_VERSION, "1.2.1.a");
        headerMap.put(Constants.BUNDLE_MANIFESTVERSION, "2");

        BundleArchiveRevision archiveRevision = Mockito.mock(BundleArchiveRevision.class);
        Mockito.when(archiveRevision.getManifestHeader()).thenReturn(headerMap);

        BundleArchive archive = Mockito.mock(BundleArchive.class);
        Mockito.when(archive.getCurrentRevision()).thenReturn(archiveRevision);
        Mockito.when(archive.getId()).thenReturn(3L);

        try
        {
            new BundleImpl(felixMock, new BundleImpl(), archive);
            fail("Should have thrown a BundleException because the installed bundle is not unique");
        }
        catch (BundleException be)
        {
            // good
            assertThat(be.getMessage()).contains("not unique");
        }
    }

    private BundleImpl mockBundleImpl(long id, String bsn, String version)
    {
        BundleImpl identicalBundle = Mockito.mock(BundleImpl.class);
        Mockito.when(identicalBundle.getSymbolicName()).thenReturn(bsn);
        Mockito.when(identicalBundle.getVersion()).thenReturn(Version.parseVersion(version));
        Mockito.when(identicalBundle.getBundleId()).thenReturn(id);
        return identicalBundle;
    }
}

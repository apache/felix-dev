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
package org.apache.felix.bundlerepository.impl;

import junit.framework.TestCase;
import org.apache.felix.utils.log.Logger;
import org.easymock.EasyMock;
import org.osgi.framework.*;

import java.util.Dictionary;
import java.util.Hashtable;

public class LazyLocalResourceImplTest extends TestCase
{
    public void testEquals() {
        // Create mock Bundles using the helper
        Bundle bundle1a = createMockBundle("test.bundle", "1.0.0", 1L);
        Bundle bundle1b = createMockBundle("test.bundle", "1.0.0", 2L); // Different ID, same identity
        Bundle bundle1c = createMockBundle("test.bundle", "1.0.0", 5L); // For transitivity test
        Bundle bundle2 = createMockBundle("test.bundle", "1.1.0", 3L); // Different version
        Bundle bundle3 = createMockBundle("another.bundle", "1.0.0", 4L); // Different BSN

        // Replay all mocks created by the helper
        EasyMock.replay(bundle1a, bundle1b, bundle1c, bundle2, bundle3);

        // Create LazyLocalResourceImpl instances
        LazyLocalResourceImpl res1a = new LazyLocalResourceImpl(bundle1a, new Logger(bundle1a.getBundleContext()));
        // Reference to the same object
        LazyLocalResourceImpl res1b = new LazyLocalResourceImpl(bundle1b, new Logger(bundle1b.getBundleContext()));
        LazyLocalResourceImpl res1c = new LazyLocalResourceImpl(bundle1c, new Logger(bundle1c.getBundleContext()));
        LazyLocalResourceImpl res2 = new LazyLocalResourceImpl(bundle2, new Logger(bundle2.getBundleContext()));
        LazyLocalResourceImpl res3 = new LazyLocalResourceImpl(bundle3, new Logger(bundle3.getBundleContext()));

        // 1. Reflexive
        assertEquals("A resource must be equal to itself.", res1a, res1a);

        // 2. Symmetric
        assertEquals("Resources with same BSN/Version should be equal (Symmetry Part 1).", res1a, res1b);
        assertEquals("Resources with same BSN/Version should be equal (Symmetry Part 2).", res1b, res1a);

        // 3. Transitive
        assertEquals("res1b should be equal to res1c (Transitive premise 1).", res1b, res1c);
        assertEquals("res1a should be equal to res1c (Transitive conclusion).", res1a, res1c);

        // 4. Consistency (Implicitly tested by repeated calls)
        assertEquals("Consistency check", res1a, res1b);

        // 5. Null comparison
        assertFalse("Resource should not be equal to null.", res1a.equals(null));

        // 6. Different type comparison
        assertFalse("Resource should not be equal to an object of a different type.", res1a.equals(new Object()));

        // 7. Inequality cases
        assertFalse("Resources with different versions should not be equal.", res1a.equals(res2));
        assertFalse("Resources with different symbolic names should not be equal.", res1a.equals(res3));

        // 8. Test with lazy initialization interaction
        Bundle bundleLazyA = createMockBundle("lazy.test", "1.0", 6L);
        Bundle bundleLazyB = createMockBundle("lazy.test", "1.0", 7L);
        EasyMock.replay(bundleLazyA, bundleLazyB); // Replay the lazy mocks

        LazyLocalResourceImpl resLazyA = new LazyLocalResourceImpl(bundleLazyA, new Logger(bundleLazyA.getBundleContext()));
        LazyLocalResourceImpl resLazyB = new LazyLocalResourceImpl(bundleLazyB, new Logger(bundleLazyB.getBundleContext()));

        // Check equality before internal Resource is initialized
        assertEquals("Equality should hold even before explicit initialization.", resLazyA, resLazyB);

        // Force initialization of resLazyA's internal resource
        assertNotNull("Getting symbolic name should work", resLazyA.getSymbolicName());
        assertEquals("Equality should hold after one resource is initialized.", resLazyA, resLazyB);
        assertEquals("Symmetric equality should hold after one resource is initialized.", resLazyB, resLazyA);

        // Force initialization of resLazyB's internal resource
        assertNotNull("Getting version should work", resLazyB.getVersion());
        assertEquals("Equality should hold after both resources are initialized.", resLazyA, resLazyB);

        // Verify mocks - confirms expected methods were called on bundles
        EasyMock.verify(bundle1a, bundle1b, bundle1c, bundle2, bundle3, bundleLazyA, bundleLazyB);
    }

    public void testHashCode() {
        // Create mock Bundles
        Bundle bundle1a = createMockBundle("hash.bundle", "1.0.0", 10L);
        Bundle bundle1b = createMockBundle("hash.bundle", "1.0.0", 11L); // Should be equal to 1a
        Bundle bundle2 = createMockBundle("hash.bundle", "2.0.0", 12L); // Different version
        Bundle bundle3 = createMockBundle("another.hash.bundle", "1.0.0", 13L); // Different BSN

        // Replay mocks
        EasyMock.replay(bundle1a, bundle1b, bundle2, bundle3);

        LazyLocalResourceImpl res1a = new LazyLocalResourceImpl(bundle1a, new Logger(bundle1a.getBundleContext()));
        LazyLocalResourceImpl res1b = new LazyLocalResourceImpl(bundle1b, new Logger(bundle1b.getBundleContext()));
        LazyLocalResourceImpl res2 = new LazyLocalResourceImpl(bundle2, new Logger(bundle2.getBundleContext()));
        LazyLocalResourceImpl res3 = new LazyLocalResourceImpl(bundle3, new Logger(bundle3.getBundleContext()));

        // 1. Consistency
        int hash1a_call1 = res1a.hashCode();
        assertNotNull("Getting properties should work", res1a.getProperties()); // Access a property to potentially trigger initialization
        int hash1a_call2 = res1a.hashCode();
        assertEquals("HashCode must be consistent across multiple invocations.", hash1a_call1, hash1a_call2);

        // 2. Equality implies equal hash codes
        assertEquals("Precondition failed: res1a should be equal to res1b.", res1a, res1b);
        assertEquals("Equal objects must have equal hash codes.", res1a.hashCode(), res1b.hashCode());

        // Check inequality cases (hash codes *might* collide)
        assertFalse("Precondition failed: res1a should not be equal to res2.", res1a.equals(res2));
        assertFalse("Precondition failed: res1a should not be equal to res3.", res1a.equals(res3));

        // 3. Test with lazy initialization interaction
        Bundle bundleLazyHash = createMockBundle("lazy.hash", "1.0", 14L);
        EasyMock.replay(bundleLazyHash); // Replay the lazy mock

        LazyLocalResourceImpl resLazyHash = new LazyLocalResourceImpl(bundleLazyHash, new Logger(bundleLazyHash.getBundleContext()));

        int hashBeforeInit = resLazyHash.hashCode();
        // Force initialization by accessing a property that delegates
        assertNotNull("Getting capabilities should work", resLazyHash.getCapabilities());
        int hashAfterInit = resLazyHash.hashCode();

        assertEquals("HashCode should be consistent before and after lazy initialization.", hashBeforeInit, hashAfterInit);

        // Verify mocks
        EasyMock.verify(bundle1a, bundle1b, bundle2, bundle3, bundleLazyHash);
    }

    private Bundle createMockBundle(String symbolicName, String versionString, long bundleId) {
        Bundle mockBundle = EasyMock.createMock(Bundle.class);
        BundleContext mockContext = EasyMock.createNiceMock(BundleContext.class);
        Version version = (versionString != null) ? Version.parseVersion(versionString) : Version.emptyVersion;
        Dictionary<String, String> headers = new Hashtable<>();

        if (symbolicName != null) {
            headers.put(Constants.BUNDLE_SYMBOLICNAME, symbolicName);
            // Add BSN directive if needed, assuming default visibility
            EasyMock.expect(mockBundle.getSymbolicName()).andReturn(symbolicName).anyTimes();
        } else {
            EasyMock.expect(mockBundle.getSymbolicName()).andReturn(null).anyTimes();
        }
        headers.put(Constants.BUNDLE_VERSION, version.toString());
        headers.put(Constants.BUNDLE_MANIFESTVERSION, "2"); // Often required by resource parsers

        EasyMock.expect(mockBundle.getHeaders()).andReturn(headers).anyTimes();
        EasyMock.expect(mockBundle.getHeaders(EasyMock.anyString())).andReturn(headers).anyTimes(); // Handle locale variant
        EasyMock.expect(mockBundle.getVersion()).andReturn(version).anyTimes();
        EasyMock.expect(mockBundle.getBundleId()).andReturn(bundleId).anyTimes();
        EasyMock.expect(mockBundle.getLocation()).andReturn("mock:/" + bundleId).anyTimes(); // For Resource ID
        EasyMock.expect(mockBundle.getBundleContext()).andReturn(mockContext).anyTimes();
        EasyMock.expect(mockBundle.getRegisteredServices()).andReturn(new ServiceReference[0]).anyTimes();

        return mockBundle;
    }
}
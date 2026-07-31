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
package org.apache.felix.utils.resource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import junit.framework.TestCase;

import org.osgi.framework.Version;
import org.osgi.resource.Capability;
import org.osgi.resource.Resource;

public class ResourceImplTest extends TestCase {

    private static int expectedHash(ResourceImpl r) {
        // getCapabilities(null)/getRequirements(null) return the live internal lists,
        // so this is exactly the value the uncached implementation would compute.
        return Objects.hash(r.getCapabilities(null), r.getRequirements(null));
    }

    private static CapabilityImpl newCapability(ResourceImpl r) {
        return new CapabilityImpl(r, "osgi.wiring.package",
                new HashMap<String, String>(), new HashMap<String, Object>());
    }

    private static RequirementImpl newRequirement(ResourceImpl r) {
        return new RequirementImpl(r, "osgi.wiring.package",
                new HashMap<String, String>(), new HashMap<String, Object>());
    }

    public void testHashCodeMatchesContentAndIsStable() {
        ResourceImpl r = new ResourceImpl("res-a", "bundle", Version.parseVersion("1.0.0"));
        int h1 = r.hashCode();
        assertEquals(expectedHash(r), h1);
        assertEquals(h1, r.hashCode()); // memoized value stays consistent
    }

    public void testCacheInvalidatedByAddCapability() {
        ResourceImpl r = new ResourceImpl("res-b", "bundle", Version.parseVersion("1.0.0"));
        r.hashCode(); // prime the cache
        r.addCapability(newCapability(r));
        assertEquals(expectedHash(r), r.hashCode());
    }

    public void testCacheInvalidatedByAddCapabilities() {
        ResourceImpl r = new ResourceImpl("res-c", "bundle", Version.parseVersion("1.0.0"));
        r.hashCode(); // prime the cache
        r.addCapabilities(Arrays.asList(newCapability(r), newCapability(r)));
        assertEquals(expectedHash(r), r.hashCode());
    }

    public void testCacheInvalidatedByAddRequirement() {
        ResourceImpl r = new ResourceImpl("res-d", "bundle", Version.parseVersion("1.0.0"));
        r.hashCode(); // prime the cache
        r.addRequirement(newRequirement(r));
        assertEquals(expectedHash(r), r.hashCode());
    }

    public void testCacheInvalidatedByAddRequirements() {
        ResourceImpl r = new ResourceImpl("res-e", "bundle", Version.parseVersion("1.0.0"));
        r.hashCode(); // prime the cache
        r.addRequirements(Arrays.asList(newRequirement(r), newRequirement(r)));
        assertEquals(expectedHash(r), r.hashCode());
    }

    public void testEqualsHashCodeContract() {
        ResourceImpl r = new ResourceImpl();
        ResourceImpl s = new ResourceImpl();
        assertEquals(r, s);                       // both empty -> equal
        assertEquals(r.hashCode(), s.hashCode()); // contract holds with memoization
    }

    public void testHashCodeIsActuallyMemoizedAndInvalidated() throws Exception {
        // White-box: the behavioural tests above would also pass with no cache at all,
        // so verify the cache field itself transitions 0 -> computed -> 0 on mutation.
        Field hashField = ResourceImpl.class.getDeclaredField("hash");
        hashField.setAccessible(true);

        ResourceImpl r = new ResourceImpl("res-g", "bundle", Version.parseVersion("1.0.0"));
        assertEquals(0, hashField.getInt(r));

        int h = r.hashCode();
        assertEquals(h, hashField.getInt(r));

        r.addCapability(newCapability(r));
        assertEquals(0, hashField.getInt(r));

        r.hashCode();
        r.addRequirement(newRequirement(r));
        assertEquals(0, hashField.getInt(r));
    }

    public void testGenuineZeroHashIsReturnedAndCached() throws Exception {
        // With caps = [stub], reqs = [], the content hash is
        // Objects.hash(caps, reqs) = 31 * (31 * 1 + (31 + e)) + 1 = 1923 + 31 * e
        // where e is the stub capability's hashCode. e below solves
        // 1923 + 31 * e == 0 in int arithmetic, producing a genuine hash of 0.
        ResourceImpl r = new ResourceImpl();
        r.addCapability(new FixedHashCapability(r, 1108378595));
        assertEquals("test setup: content hash must be 0", 0, expectedHash(r));

        assertEquals(0, r.hashCode());
        assertEquals(0, r.hashCode()); // stays 0, contract intact

        // White-box: the zero result is cached via the hashIsZero flag,
        // not recomputed on every call, and mutation clears the flag.
        Field hashIsZeroField = ResourceImpl.class.getDeclaredField("hashIsZero");
        hashIsZeroField.setAccessible(true);
        assertTrue(hashIsZeroField.getBoolean(r));

        r.addCapability(newCapability(r));
        assertFalse(hashIsZeroField.getBoolean(r));
        assertEquals(expectedHash(r), r.hashCode());
    }

    /** Capability stub with a controllable hashCode, to hit the genuine-zero-hash path. */
    private static final class FixedHashCapability implements Capability {
        private final Resource resource;
        private final int hash;

        FixedHashCapability(Resource resource, int hash) {
            this.resource = resource;
            this.hash = hash;
        }

        public String getNamespace() {
            return "test";
        }

        public Map<String, String> getDirectives() {
            return Collections.emptyMap();
        }

        public Map<String, Object> getAttributes() {
            return Collections.emptyMap();
        }

        public Resource getResource() {
            return resource;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
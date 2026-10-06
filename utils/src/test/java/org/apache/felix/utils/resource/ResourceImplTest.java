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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

public class ResourceImplTest {

    private static CapabilityImpl newCapability(ResourceImpl res, String ns) {
        return new CapabilityImpl(res, ns, new HashMap<>(), new HashMap<>());
    }

    private static RequirementImpl newRequirement(ResourceImpl res, String ns) {
        return new RequirementImpl(res, ns, new HashMap<>(), new HashMap<>());
    }

    /**
     * Requirement whose hashCode() returns the given value and counts how often it is called
     */
    private static RequirementImpl newCountingRequirement(ResourceImpl res, String ns, int hash, AtomicInteger hashCodeCalls) {
        return new RequirementImpl(res, ns, new HashMap<>(), new HashMap<>()) {
            @Override
            public int hashCode() {
                hashCodeCalls.incrementAndGet();
                return hash;
            }
        };
    }

    /**
     * Wrapper method to avoid direct field reference
     */
    private static List<Capability> getCaps(ResourceImpl res) {
        return res.getCapabilities(null);
    }

    /**
     * Wrapper method to avoid direct field reference
     */
    private static List<Requirement> getReqs(ResourceImpl res) {
        return res.getRequirements(null);
    }

    private static int expectedHash(ResourceImpl res) {
        return Objects.hash(getCaps(res), getReqs(res));
    }

    @Test
    public void testAddCapability() {
        ResourceImpl res = new ResourceImpl();

        // before
        int hashBefore = res.hashCode();
        assertTrue(getCaps(res).isEmpty());
        assertEquals(expectedHash(res), hashBefore);

        Capability cap1 = newCapability(res, "ns1");
        res.addCapability(cap1);
        int hashWithCap1 = res.hashCode();
        assertEquals(1, getCaps(res).size());
        assertTrue(getCaps(res).contains(cap1));
        assertNotEquals(hashBefore, hashWithCap1);
        assertEquals(expectedHash(res), hashWithCap1);

        Capability cap2 = newCapability(res, "ns2");
        res.addCapability(cap2);
        int hashWithCap2 = res.hashCode();
        assertEquals(2, getCaps(res).size());
        assertTrue(getCaps(res).contains(cap1));
        assertTrue(getCaps(res).contains(cap2));
        assertNotEquals(hashBefore, hashWithCap2);
        assertNotEquals(hashWithCap1, hashWithCap2);
        assertEquals(expectedHash(res), hashWithCap2);
    }

    @Test
    public void testAddCapabilities() {
        ResourceImpl res = new ResourceImpl();

        // before
        int hashBefore = res.hashCode();
        assertTrue(getCaps(res).isEmpty());
        assertEquals(expectedHash(res), hashBefore);

        // adding an empty list leaves the resource unchanged
        res.addCapabilities(Collections.emptyList());
        assertTrue(getCaps(res).isEmpty());
        assertEquals(hashBefore, res.hashCode());

        Capability cap1 = newCapability(res, "ns1");
        Capability cap2 = newCapability(res, "ns2");
        res.addCapabilities(Arrays.asList(cap1, cap2));
        int hashWithCap1AndCap2 = res.hashCode();
        assertEquals(2, getCaps(res).size());
        assertTrue(getCaps(res).contains(cap1));
        assertTrue(getCaps(res).contains(cap2));
        assertNotEquals(hashBefore, hashWithCap1AndCap2);
        assertEquals(expectedHash(res), hashWithCap1AndCap2);

        Capability cap3 = newCapability(res, "ns3");
        res.addCapabilities(Collections.singletonList(cap3));
        int hashWithCap3 = res.hashCode();
        assertEquals(3, getCaps(res).size());
        assertTrue(getCaps(res).contains(cap1));
        assertTrue(getCaps(res).contains(cap2));
        assertTrue(getCaps(res).contains(cap3));
        assertNotEquals(hashBefore, hashWithCap3);
        assertNotEquals(hashWithCap1AndCap2, hashWithCap3);
        assertEquals(expectedHash(res), hashWithCap3);
    }

    @Test
    public void testAddRequirement() {
        ResourceImpl res = new ResourceImpl();

        // before
        int hashBefore = res.hashCode();
        assertTrue(getReqs(res).isEmpty());
        assertEquals(expectedHash(res), hashBefore);

        Requirement req1 = newRequirement(res, "ns1");
        res.addRequirement(req1);
        int hashWithReq1 = res.hashCode();
        assertEquals(1, getReqs(res).size());
        assertTrue(getReqs(res).contains(req1));
        assertNotEquals(hashBefore, hashWithReq1);
        assertEquals(expectedHash(res), hashWithReq1);

        Requirement req2 = newRequirement(res, "ns2");
        res.addRequirement(req2);
        int hashWithReq2 = res.hashCode();
        assertEquals(2, getReqs(res).size());
        assertTrue(getReqs(res).contains(req1));
        assertTrue(getReqs(res).contains(req2));
        assertNotEquals(hashBefore, hashWithReq2);
        assertNotEquals(hashWithReq1, hashWithReq2);
        assertEquals(expectedHash(res), hashWithReq2);
    }

    @Test
    public void testAddRequirements() {
        ResourceImpl res = new ResourceImpl();

        // before
        int hashBefore = res.hashCode();
        assertTrue(getReqs(res).isEmpty());
        assertEquals(expectedHash(res), hashBefore);

        // adding an empty list leaves the resource unchanged
        res.addRequirements(Collections.emptyList());
        assertTrue(getReqs(res).isEmpty());
        assertEquals(hashBefore, res.hashCode());

        Requirement req1 = newRequirement(res, "ns1");
        Requirement req2 = newRequirement(res, "ns2");
        res.addRequirements(Arrays.asList(req1, req2));
        int hashWithReq1AndReq2 = res.hashCode();
        assertEquals(2, getReqs(res).size());
        assertTrue(getReqs(res).contains(req1));
        assertTrue(getReqs(res).contains(req2));
        assertNotEquals(hashBefore, hashWithReq1AndReq2);
        assertEquals(expectedHash(res), hashWithReq1AndReq2);

        Requirement req3 = newRequirement(res, "ns3");
        res.addRequirements(Collections.singletonList(req3));
        int hashWithReq3 = res.hashCode();
        assertEquals(3, getReqs(res).size());
        assertTrue(getReqs(res).contains(req1));
        assertTrue(getReqs(res).contains(req2));
        assertTrue(getReqs(res).contains(req3));
        assertNotEquals(hashBefore, hashWithReq3);
        assertNotEquals(hashWithReq1AndReq2, hashWithReq3);
        assertEquals(expectedHash(res), hashWithReq3);
    }

    @Test
    public void testHashCode() {
        ResourceImpl res = new ResourceImpl("host",  IdentityNamespace.TYPE_BUNDLE, Version.parseVersion("3.3.3"));

        AtomicInteger reqHashCodeCalls = new AtomicInteger();
        res.addRequirement(newCountingRequirement(res, "ns1", 3, reqHashCodeCalls));
        int expectedHash = expectedHash(res);
        // ignore the direct call to req.hashCode() from above
        reqHashCodeCalls.set(0);
        assertNotEquals(0, expectedHash);

        // the hash is computed once and then served from the cache
        assertEquals(expectedHash, res.hashCode());
        assertEquals(expectedHash, res.hashCode());
        assertEquals(expectedHash, res.hashCode());
        assertEquals(1, reqHashCodeCalls.get());
    }

    @Test
    public void testHashCodeZero() {
        ResourceImpl res = new ResourceImpl();

        // Objects.hash(caps, reqs) is
        // 31 * (31 * 1 + caps.hashCode()) + reqs.hashCode(),
        // if caps is an empty ArrayList, caps.hashCode() is 1 and the above becomes
        // 31 * (31 * 1 + 1) + reqs.hashCode() = 31 * 32 + reqs.hashCode() = 992 + reqs.hashCode(),
        // if reqs has only 1 element, reqs.hashCode() is
        // 31 * 1 + element.hashCode() = 31 + element.hashCode(),
        // if element.hashCode() is -1023, then reqs.hashCode() is -992, and the overall hash code is 0
        AtomicInteger reqHashCodeCalls = new AtomicInteger();
        res.addRequirement(newCountingRequirement(res, "ns1", -1023, reqHashCodeCalls));
        assertEquals(0, expectedHash(res));
        // ignore the direct call to req.hashCode() from above
        reqHashCodeCalls.set(0);

        // a zero hash is computed once and then served from the cache
        assertEquals(0, res.hashCode());
        assertEquals(0, res.hashCode());
        assertEquals(0, res.hashCode());
        assertEquals(1, reqHashCodeCalls.get());

        // modifying the resource clears the cached zero hash
        res.addRequirement(newRequirement(res, "ns2"));
        int hashWithReq2 = res.hashCode();
        assertNotEquals(0, hashWithReq2);
        assertEquals(expectedHash(res), hashWithReq2);
    }

}

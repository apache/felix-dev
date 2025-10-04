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

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Hashtable;

import junit.framework.TestCase;

import org.apache.felix.bundlerepository.*;
import org.apache.felix.utils.filter.FilterImpl;
import org.apache.felix.utils.log.Logger;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.internal.matchers.Captures;
import org.osgi.framework.*;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.resource.Capability;

public class ResolverImplTest extends TestCase
{
    public void testReferral1() throws Exception
    {

        URL url = getClass().getResource("/repo_for_resolvertest.xml");
        RepositoryAdminImpl repoAdmin = createRepositoryAdmin();
        RepositoryImpl repo = (RepositoryImpl) repoAdmin.addRepository(url);

        Resolver resolver = repoAdmin.resolver();

        Resource[] discoverResources = repoAdmin.discoverResources("(symbolicname=org.apache.felix.test*)");
        assertNotNull(discoverResources);
        assertEquals(1, discoverResources.length);

        resolver.add(discoverResources[0]);
        assertTrue(resolver.resolve());
    }

    public void testSpec() throws Exception
    {
        URL url = getClass().getResource("/spec_repository.xml");
        RepositoryAdminImpl repoAdmin = createRepositoryAdmin();
        repoAdmin.addRepository(url);

        Resolver resolver = repoAdmin.resolver();

        RequirementImpl requirement = new RequirementImpl("foo");
        requirement.setFilter("(bar=toast)");

        Requirement[] requirements = { requirement };

        Resource[] discoverResources = repoAdmin.discoverResources(requirements);
        assertNotNull(discoverResources);
        assertEquals(1, discoverResources.length);

        resolver.add(discoverResources[0]);
        assertTrue("Resolver could not resolve", resolver.resolve());
    }

    public void testSpec2() throws Exception
    {
        URL url = getClass().getResource("/spec_repository.xml");
        RepositoryAdminImpl repoAdmin = createRepositoryAdmin();
        repoAdmin.addRepository(url);

        Resolver resolver = repoAdmin.resolver();

        // Create a Local Resource with an extender capability
        CapabilityImpl capability = new CapabilityImpl("osgi.extender");
        capability.addProperty("osgi.extender", "osgi.component");
        capability.addProperty("version", "Version", "1.3");

        org.apache.felix.bundlerepository.Capability[] capabilities = { capability };

        Resource resource = EasyMock.createMock(Resource.class);
        EasyMock.expect(resource.getSymbolicName()).andReturn("com.test.bundleA").anyTimes();
        EasyMock.expect(resource.getRequirements()).andReturn(null).anyTimes();
        EasyMock.expect(resource.getCapabilities()).andReturn(capabilities).anyTimes();
        EasyMock.expect(resource.getURI()).andReturn("http://test.com").anyTimes();
        EasyMock.expect(resource.isLocal()).andReturn(true).anyTimes();

        // Create a Local Resource with a service capability
        CapabilityImpl capability2 = new CapabilityImpl("service");
        capability2.addProperty("objectClass", "org.some.other.interface");
        capability2.addProperty("effective", "active");

        org.apache.felix.bundlerepository.Capability[] capabilities2 = { capability2 };

        Resource resource2 = EasyMock.createMock(Resource.class);
        EasyMock.expect(resource2.getSymbolicName()).andReturn("com.test.bundleB").anyTimes();
        EasyMock.expect(resource2.getRequirements()).andReturn(null).anyTimes();
        EasyMock.expect(resource2.getCapabilities()).andReturn(capabilities2).anyTimes();
        EasyMock.expect(resource2.getURI()).andReturn("http://test2.com").anyTimes();
        EasyMock.expect(resource2.isLocal()).andReturn(true).anyTimes();

        EasyMock.replay(resource, resource2);

        resolver.add(resource);
        resolver.add(resource2);

        // Set the requirements to get the bundle
        RequirementImpl requirement = new RequirementImpl("foo");
        requirement.setFilter("(bar=bread)");

        Requirement[] requirements = { requirement };

        Resource[] discoverResources = repoAdmin.discoverResources(requirements);
        assertNotNull(discoverResources);
        assertEquals(1, discoverResources.length);

        resolver.add(discoverResources[0]);
        assertTrue("Resolver could not resolve", resolver.resolve());

        EasyMock.verify(resource, resource2);
    }

    public void testSpecBundleNamespace() throws Exception
    {
        URL url = getClass().getResource("/spec_repository.xml");
        RepositoryAdminImpl repoAdmin = createRepositoryAdmin();
        RepositoryImpl repo = (RepositoryImpl) repoAdmin.addRepository(url);

        Resolver resolver = repoAdmin.resolver();

        Resource[] discoverResources = repoAdmin.discoverResources("(symbolicname=org.apache.felix.bundlerepository.test_file_6*)");
        assertNotNull(discoverResources);
        assertEquals(1, discoverResources.length);

        resolver.add(discoverResources[0]);
        assertTrue(resolver.resolve());
        
    }

    public void testMatchingReq() throws Exception
    {
        RepositoryAdminImpl repoAdmin = createRepositoryAdmin();
        repoAdmin.addRepository(getClass().getResource("/repo_for_resolvertest.xml"));

        Resource[] res = repoAdmin.discoverResources(
            new Requirement[] { repoAdmin.getHelper().requirement(
                "package", "(package=org.apache.felix.test.osgi)") });
        assertNotNull(res);
        assertEquals(1, res.length);
    }

    public void testResolveReq() throws Exception
    {
        RepositoryAdminImpl repoAdmin = createRepositoryAdmin();
        repoAdmin.addRepository(getClass().getResource("/repo_for_resolvertest.xml"));

        Resolver resolver = repoAdmin.resolver();
        resolver.add(repoAdmin.getHelper().requirement("package", "(package=org.apache.felix.test.osgi)"));
        assertTrue(resolver.resolve());
    }

    public void testResolveInterrupt() throws Exception
    {
        RepositoryAdminImpl repoAdmin = createRepositoryAdmin();
        repoAdmin.addRepository(getClass().getResource("/repo_for_resolvertest.xml"));

        Resolver resolver = repoAdmin.resolver();
        resolver.add(repoAdmin.getHelper().requirement("package", "(package=org.apache.felix.test.osgi)"));

        Thread.currentThread().interrupt();
        try
        {
            resolver.resolve();
            fail("An excepiton should have been thrown");
        }
        catch (org.apache.felix.bundlerepository.InterruptedResolutionException e)
        {
            // ok
        }
    }

    public void testOptionalResolution() throws Exception
    {
        RepositoryAdminImpl repoAdmin = createRepositoryAdmin();
        repoAdmin.addRepository(getClass().getResource("/repo_for_optional_resources.xml"));

        Resolver resolver = repoAdmin.resolver();
        resolver.add(repoAdmin.getHelper().requirement("bundle", "(symbolicname=res1)"));

        assertTrue(resolver.resolve());
        assertEquals(1, resolver.getRequiredResources().length);
        assertEquals(2, resolver.getOptionalResources().length);
    }

    public void testMandatoryPackages() throws Exception
    {
        RepositoryAdminImpl repoAdmin = createRepositoryAdmin();
        repoAdmin.addRepository(getClass().getResource("/repo_for_mandatory.xml"));

        Resolver resolver = repoAdmin.resolver();
        resolver.add(repoAdmin.getHelper().requirement("bundle", "(symbolicname=res2)"));
        assertFalse(resolver.resolve());

        resolver = repoAdmin.resolver();
        resolver.add(repoAdmin.getHelper().requirement("bundle", "(symbolicname=res3)"));
        assertTrue(resolver.resolve());

        resolver = repoAdmin.resolver();
        resolver.add(repoAdmin.getHelper().requirement("bundle", "(symbolicname=res4)"));
        assertFalse(resolver.resolve());

    }

    public void testFindUpdatableLocalResource() throws Exception {
        Bundle mockBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(mockBundle.getState()).andReturn(Bundle.ACTIVE).anyTimes();
        EasyMock.expect(mockBundle.getHeaders()).andReturn(new Hashtable<>()).anyTimes();

        mockBundle.stop();
        EasyMock.expectLastCall().once();
        mockBundle.update(EasyMock.anyObject());
        EasyMock.expectLastCall().once();
        mockBundle.start();
        EasyMock.expectLastCall().once();

        // In the implementation of ResolverImpl the static method FileUtil.openURL is used.
        // EasyMock doesn't have static method testing, Mockito doesn't have it in the currently used version.
        // So for now reference a local file which we know exists to be loaded.
        String uri = getClass().getResource("ResolverImplTest.class").toURI().toString();

        LocalResource resource = EasyMock.createMock(LocalResource.class);
        EasyMock.expect(resource.getSymbolicName()).andReturn("com.test.bundleA").anyTimes();
        EasyMock.expect(resource.getRequirements()).andReturn(null).anyTimes();
        EasyMock.expect(resource.getCapabilities()).andReturn(null).anyTimes();
        EasyMock.expect(resource.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(resource.getURI()).andReturn(uri).anyTimes();
        EasyMock.expect(resource.isLocal()).andReturn(true).anyTimes();

        // Ensure the resource to deploy is different that the local resource, thus triggering an update
        LocalResource resourceToDeploy = EasyMock.createMock(LocalResource.class);
        EasyMock.expect(resourceToDeploy.getSymbolicName()).andReturn("com.test.bundleA").anyTimes();
        EasyMock.expect(resourceToDeploy.getRequirements()).andReturn(null).anyTimes();
        EasyMock.expect(resourceToDeploy.getCapabilities()).andReturn(null).anyTimes();
        EasyMock.expect(resourceToDeploy.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(resourceToDeploy.getURI()).andReturn(uri).anyTimes();
        EasyMock.expect(resourceToDeploy.isLocal()).andReturn(true).anyTimes();

        Repository localRepo = EasyMock.createMock(Repository.class);

        Repository[] localRepos = { localRepo };
        final LocalResource[] localResources = { resource };

        EasyMock.expect(localRepo.getResources()).andReturn(localResources).anyTimes();
        EasyMock.expect(localRepo.getURI()).andReturn(Repository.LOCAL).anyTimes();
        EasyMock.expect(localRepo.getLastModified()).andReturn(System.currentTimeMillis()).anyTimes();

        BundleContext bundleContext = EasyMock.createMock(BundleContext.class);

        EasyMock.replay(resource, resourceToDeploy, mockBundle, localRepo);

        ResolverImpl resolver = new ResolverImpl(bundleContext, localRepos, new Logger(bundleContext)) {
            @Override
            public LocalResource[] getLocalResources() {
                return localResources;
            }
        };

        resolver.add(resourceToDeploy);

        boolean exceptionThrown = false;
        try {
            resolver.resolve();
            resolver.deploy(Resolver.START);
        } catch (Exception e) {
            e.printStackTrace();
            exceptionThrown = true;
        }
        assertFalse(exceptionThrown);

        EasyMock.verify(resource, resourceToDeploy, mockBundle, localRepo);
    }

    public void testDeployFragmentBundle() throws Exception {
        Bundle mockBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(mockBundle.getState()).andReturn(Bundle.ACTIVE).anyTimes();
        Hashtable<String, String> bundleHeaders = new Hashtable<>();
        bundleHeaders.put(Constants.FRAGMENT_HOST, "com.test.bundleA");
        EasyMock.expect(mockBundle.getHeaders()).andReturn(bundleHeaders).anyTimes();

        LocalResource resource = EasyMock.createMock(LocalResource.class);
        EasyMock.expect(resource.getSymbolicName()).andReturn("com.test.bundleA").anyTimes();
        EasyMock.expect(resource.getRequirements()).andReturn(null).anyTimes();
        EasyMock.expect(resource.getCapabilities()).andReturn(null).anyTimes();
        EasyMock.expect(resource.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(resource.getURI()).andReturn("http://test.com").anyTimes();
        EasyMock.expect(resource.isLocal()).andReturn(true).anyTimes();

        Repository localRepo = EasyMock.createMock(Repository.class);

        Repository[] localRepos = { localRepo };
        final LocalResource[] localResources = { resource };

        EasyMock.expect(localRepo.getResources()).andReturn(localResources).anyTimes();
        EasyMock.expect(localRepo.getURI()).andReturn(Repository.LOCAL).anyTimes();
        EasyMock.expect(localRepo.getLastModified()).andReturn(System.currentTimeMillis()).anyTimes();

        BundleContext bundleContext = EasyMock.createMock(BundleContext.class);

        EasyMock.replay(resource, mockBundle, localRepo);

        ResolverImpl resolver = new ResolverImpl(bundleContext, localRepos, new Logger(bundleContext)) {
            @Override
            public LocalResource[] getLocalResources() {
                return localResources;
            }
        };

        resolver.add(resource);

        boolean exceptionThrown = false;
        try {
            resolver.resolve();
            resolver.deploy(Resolver.START);
        } catch (Exception e) {
            e.printStackTrace();
            exceptionThrown = true;
        }
        assertFalse(exceptionThrown);

        EasyMock.verify(resource, mockBundle, localRepo);
    }

    public static void main(String[] args) throws Exception
    {
        new ResolverImplTest().testReferral1();
    }

    private RepositoryAdminImpl createRepositoryAdmin() throws Exception
    {
        BundleContext bundleContext = EasyMock.createMock(BundleContext.class);
        Bundle systemBundle = EasyMock.createMock(Bundle.class);
        BundleRevision systemBundleRevision = EasyMock.createMock(BundleRevision.class);

        Activator.setContext(bundleContext);
        EasyMock.expect(bundleContext.getProperty(RepositoryAdminImpl.REPOSITORY_URL_PROP))
                    .andReturn(getClass().getResource("/referred.xml").toExternalForm());
        EasyMock.expect(bundleContext.getProperty((String) EasyMock.anyObject())).andReturn(null).anyTimes();
        EasyMock.expect(bundleContext.getBundle(0)).andReturn(systemBundle);
        EasyMock.expect(bundleContext.installBundle((String) EasyMock.anyObject(), (InputStream) EasyMock.anyObject())).andReturn(systemBundle);
        EasyMock.expect(systemBundle.getHeaders()).andReturn(new Hashtable()).anyTimes();
        systemBundle.start();
        EasyMock.expectLastCall().anyTimes();
        EasyMock.expect(systemBundle.getRegisteredServices()).andReturn(null);
        EasyMock.expect(new Long(systemBundle.getBundleId())).andReturn(new Long(0)).anyTimes();
        EasyMock.expect(systemBundle.getBundleContext()).andReturn(bundleContext);
        EasyMock.expect(systemBundleRevision.getCapabilities(null)).andReturn(Collections.<Capability>emptyList());
        EasyMock.expect(systemBundle.adapt(BundleRevision.class)).andReturn(systemBundleRevision);
        bundleContext.addBundleListener((BundleListener) EasyMock.anyObject());
        bundleContext.addServiceListener((ServiceListener) EasyMock.anyObject());
        EasyMock.expect(bundleContext.getBundles()).andReturn(new Bundle[] { systemBundle });
        final Capture c = new Capture();
        EasyMock.expect(bundleContext.createFilter((String) capture(c))).andAnswer(new IAnswer() {
            public Object answer() throws Throwable {
                return FilterImpl.newInstance((String) c.getValue());
            }
        }).anyTimes();
        EasyMock.replay(new Object[] { bundleContext, systemBundle, systemBundleRevision });

        RepositoryAdminImpl repoAdmin = new RepositoryAdminImpl(bundleContext, new Logger(bundleContext));

        // force initialization && remove all initial repositories
        Repository[] repos = repoAdmin.listRepositories();
        for (int i = 0; repos != null && i < repos.length; i++)
        {
            repoAdmin.removeRepository(repos[i].getURI());
        }

        return repoAdmin;
    }

    static Object capture(Capture capture) {
        EasyMock.reportMatcher(new Captures(capture));
        return null;
    }

}
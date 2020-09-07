/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.scr.integration;

import static org.junit.Assert.assertNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.FrameworkWiring;

@RunWith(PaxExam.class)
public class Felix6325OptionalLogTest extends ComponentTestBase
{
    static
    {
        // This test creates its own component bundles
        descriptorFile = null;
        DS_LOGLEVEL = "debug";
        //paxRunnerVmOption = DEBUG_VM_OPTION;
    }

    final ResolverHookFactory resolverHookFactory = new ResolverHookFactoryImpl();

    class ResolverHookFactoryImpl implements ResolverHookFactory
    {
        final ResolverHook resolverHook = new ResolverHookImpl();

        @Override
        public ResolverHook begin(Collection<BundleRevision> triggers)
        {
            return resolverHook;
        }
    }

    class ResolverHookImpl implements ResolverHook
    {
        @Override
        public void filterSingletonCollisions(BundleCapability singleton,
            Collection<BundleCapability> collisionCandidates)
        {
            // nothing to do
        }

        @Override
        public void filterResolvable(Collection<BundleRevision> candidates)
        {
            // nothing to do
        }

        @Override
        public void filterMatches(BundleRequirement requirement,
            Collection<BundleCapability> candidates)
        {
            if (!candidates.isEmpty()
                && PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace()))
            {
                String packageName = (String) candidates.iterator().next().getAttributes().get(
                    PackageNamespace.PACKAGE_NAMESPACE);
                if (packageName != null && packageName.startsWith("org.osgi.service.log"))
                {
                    candidates.clear();
                }
            }
            return;
        }

        @Override
        public void end()
        {
            // nothing to do
        }
    }

    private ServiceRegistration<ResolverHookFactory> hookReg;
    private Bundle scrBundle;
    private FrameworkWiring fwkWiring;

    @Before
    public void registerResolverHook() throws BundleException
    {
        scrBundle = null;
        for (Bundle b : bundleContext.getBundles())
        {
            if ("org.apache.felix.scr".equals(b.getSymbolicName()))
            {
                scrBundle = b;
                break;
            }
        }
        assertNotNull("No SCR bundle found!", scrBundle);

        Bundle systemBundle = bundleContext.getBundle(Constants.SYSTEM_BUNDLE_LOCATION);
        BundleContext systemContext = systemBundle.getBundleContext();
        fwkWiring = systemContext.getBundle().adapt(FrameworkWiring.class);

        // Use a resolver hook to make sure scr does not import the log packages
        hookReg = systemContext.registerService(ResolverHookFactory.class,
            resolverHookFactory, null);
    }
    
    @After
    public void unregisterResolverHook() throws InterruptedException
    {
        if (hookReg != null)
        {
            try
            {
                hookReg.unregister();
            }
            catch (IllegalStateException e)
            {
                // ignore
            }
            hookReg = null;
        }
        refresh(scrBundle);
    }

    private void refresh(Bundle b) throws InterruptedException
    {
        final CountDownLatch refreshWait = new CountDownLatch(1);
        fwkWiring.refreshBundles(Collections.singleton(b), new FrameworkListener()
        {
            @Override
            public void frameworkEvent(FrameworkEvent event)
            {
                refreshWait.countDown();
            }
        });
        refreshWait.await(10, TimeUnit.SECONDS);
    }

    @Test
    public void testNoLogPackage() throws Exception
    {
        scrBundle.stop();
        refresh(scrBundle);
        scrBundle.start();
    }
}

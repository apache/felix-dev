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
package org.apache.felix.scr.integration.components.felix6274_hook;

import java.util.Collection;
import java.util.Iterator;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

public class Activator implements BundleActivator, ResolverHookFactory, ResolverHook
{

    @Override
    public void start(BundleContext context) throws Exception
    {
        context.registerService(ResolverHookFactory.class, this, null);
    }

    @Override
    public void stop(BundleContext context) throws Exception
    {
    }

    @Override
    public ResolverHook begin(Collection<BundleRevision> triggers)
    {
        return this;
    }

    @Override
    public void filterSingletonCollisions(BundleCapability singleton,
        Collection<BundleCapability> collisionCandidates)
    {
    }

    @Override
    public void filterResolvable(Collection<BundleRevision> candidates)
    {

    }

    @Override
    public void filterMatches(BundleRequirement requirement,
        Collection<BundleCapability> candidates)
    {
        Bundle b = requirement.getResource().getBundle();

        if (PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())
            && "org.osgi.service.log".equals(
                candidates.iterator().next().getAttributes().get(
                    PackageNamespace.PACKAGE_NAMESPACE))
            && "org.apache.felix.log".equals(b.getSymbolicName())
            && Version.valueOf("1.0.1").equals(b.getVersion()))
        {
            // Felix log 1.0.1 incorrectly allows import of R7 log
            Iterator<BundleCapability> iCaps = candidates.iterator();
            while (iCaps.hasNext())
            {
                if (!iCaps.next().getRevision().getBundle().equals(
                    requirement.getRevision().getBundle()))
                {
                    System.out.println("TJW - removed");
                    iCaps.remove();
                }
            }
        }
    }

    @Override
    public void end()
    {
    }
}

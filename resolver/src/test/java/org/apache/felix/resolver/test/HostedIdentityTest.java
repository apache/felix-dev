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
package org.apache.felix.resolver.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.felix.resolver.Logger;
import org.apache.felix.resolver.ResolverImpl;
import org.apache.felix.resolver.test.util.FilteringResolveContext;
import org.apache.felix.resolver.test.util.GenericRequirement;
import org.apache.felix.resolver.test.util.PackageCapability;
import org.apache.felix.resolver.test.util.ResourceImpl;
import org.junit.Test;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Namespace;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;

/**
 * Core specification 3.6.5 (Export-Package): the bundle-symbolic-name and bundle-version attributes of an
 * export are the ones of the exporting bundle, "in the case of a fragment bundle, this is the host bundle's
 * symbolic name" (and version). Tools like bnd create imports with these attributes for packages that
 * are exported by fragments, e.g. the SWT fragments, see
 * https://github.com/eclipse-platform/eclipse.platform.swt/pull/1032.
 */
public class HostedIdentityTest
{
    private final ResourceImpl host = new ResourceImpl("host", IdentityNamespace.TYPE_BUNDLE, Version.parseVersion("1.0.0"));
    private final ResourceImpl fragment = new ResourceImpl("fragment", IdentityNamespace.TYPE_FRAGMENT, Version.parseVersion("5.0.0"));

    public HostedIdentityTest()
    {
        GenericRequirement hostReq = new GenericRequirement(fragment, HostNamespace.HOST_NAMESPACE);
        hostReq.addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(" + HostNamespace.HOST_NAMESPACE + "=host)");
        fragment.addRequirement(hostReq);

        org.apache.felix.resolver.test.util.GenericCapability hostCap =
            new org.apache.felix.resolver.test.util.GenericCapability(host, HostNamespace.HOST_NAMESPACE);
        hostCap.addAttribute(HostNamespace.HOST_NAMESPACE, "host");
        host.addCapability(hostCap);

        // as created by bnd: the identity of the declaring bundle, that is the fragment
        PackageCapability export = new PackageCapability(fragment, "foo");
        export.addAttribute("bundle-symbolic-name", "fragment");
        export.addAttribute("bundle-version", Version.parseVersion("5.0.0"));
        fragment.addCapability(export);
    }

    private ResourceImpl importer(String filter)
    {
        ResourceImpl importer = new ResourceImpl("importer", IdentityNamespace.TYPE_BUNDLE, Version.parseVersion("1.0.0"));
        GenericRequirement req = new GenericRequirement(importer, PackageNamespace.PACKAGE_NAMESPACE);
        req.addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, filter);
        importer.addRequirement(req);
        return importer;
    }

    private Map<Resource, List<Wire>> resolve(ResourceImpl importer) throws ResolutionException
    {
        List<Resource> all = Arrays.<Resource> asList(importer, host, fragment);
        return new ResolverImpl(new Logger(Logger.LOG_DEBUG), 1)
            .resolve(new FilteringResolveContext(Arrays.<Resource> asList(importer, fragment), all));
    }

    private void assertWiredToHost(ResourceImpl importer, Map<Resource, List<Wire>> wires)
    {
        List<Wire> importerWires = wires.get(importer);
        assertNotNull(importerWires);
        assertEquals(1, importerWires.size());
        Wire wire = importerWires.get(0);
        assertEquals(PackageNamespace.PACKAGE_NAMESPACE, wire.getCapability().getNamespace());
        assertTrue("provided by " + wire.getProvider(), wire.getProvider() == host);
    }

    @Test
    public void testPackageOfFragmentIsImportedByNameOfHost() throws Exception
    {
        ResourceImpl importer = importer("(&(osgi.wiring.package=foo)(bundle-symbolic-name=host))");
        assertWiredToHost(importer, resolve(importer));
    }

    @Test
    public void testPackageOfFragmentHasVersionOfHost() throws Exception
    {
        ResourceImpl importer = importer("(&(osgi.wiring.package=foo)(bundle-version>=1.0.0)(!(bundle-version>=2.0.0)))");
        assertWiredToHost(importer, resolve(importer));
    }

    @Test
    public void testPackageOfFragmentIsNotImportedByNameOfFragment() throws Exception
    {
        try
        {
            resolve(importer("(&(osgi.wiring.package=foo)(bundle-symbolic-name=fragment))"));
            fail("The package is exported by the host, not by the fragment");
        }
        catch (ResolutionException e)
        {
            // expected
        }
    }

    @Test
    public void testPackageOfFragmentIsNotImportedByVersionOfFragment() throws Exception
    {
        try
        {
            resolve(importer("(&(osgi.wiring.package=foo)(bundle-version>=5.0.0))"));
            fail("The version of the package export is the one of the host");
        }
        catch (ResolutionException e)
        {
            // expected
        }
    }

    @Test
    public void testPackageOfFragmentIsStillImportedWithoutIdentity() throws Exception
    {
        ResourceImpl importer = importer("(osgi.wiring.package=foo)");
        assertWiredToHost(importer, resolve(importer));
    }
}

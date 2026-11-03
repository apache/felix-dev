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
package org.apache.felix.resolver.test.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * A resolve context that finds providers by matching the filter of a requirement against the
 * attributes of all capabilities of the given resources, like a repository would do.
 */
public class FilteringResolveContext extends ResolveContextImpl
{
    private final Collection<Resource> m_available;

    public FilteringResolveContext(Collection<Resource> mandatory, Collection<Resource> available)
    {
        super(Collections.<Resource, org.osgi.resource.Wiring> emptyMap(),
            Collections.<Requirement, List<Capability>> emptyMap(), mandatory,
            Collections.<Resource> emptyList());
        m_available = available;
    }

    @Override
    public List<Capability> findProviders(Requirement requirement)
    {
        List<Capability> result = new ArrayList<Capability>();
        String filter = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        try
        {
            for (Resource resource : m_available)
            {
                for (Capability cap : resource.getCapabilities(requirement.getNamespace()))
                {
                    if (filter == null || FrameworkUtil.createFilter(filter).matches(
                        new Hashtable<String, Object>(cap.getAttributes())))
                    {
                        result.add(cap);
                    }
                }
            }
        }
        catch (InvalidSyntaxException e)
        {
            throw new IllegalArgumentException(e);
        }
        return result;
    }
}

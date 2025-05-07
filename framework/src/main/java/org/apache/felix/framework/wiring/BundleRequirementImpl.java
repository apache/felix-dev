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
package org.apache.felix.framework.wiring;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.felix.framework.capabilityset.CapabilitySet;
import org.apache.felix.framework.capabilityset.SimpleFilter;
import org.apache.felix.framework.util.Util;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

public class BundleRequirementImpl implements BundleRequirement
{
    private final BundleRevision m_revision;
    private final String m_namespace;
    private final SimpleFilter m_filter;
    private final boolean m_optional;
    private final Map<String, String> m_dirs;
    private final Map<String, Object> m_attrs;

    public static BundleRequirementImpl createFrom(BundleRequirementImpl requirement, Function<Object, Object> cache)
    {
        String namespaceI = (String) cache.apply(requirement.m_namespace);
        Map<String, String> dirsI = new HashMap<>();
        for (Map.Entry<String, String> entry : requirement.m_dirs.entrySet())
        {
            dirsI.put((String) cache.apply(entry.getKey()), (String) cache.apply(entry.getValue()));
        }
        dirsI = (Map<String, String>) cache.apply(dirsI);

        Map<String, Object> attrsI = new HashMap<>();
        for (Map.Entry<String, Object> entry : requirement.m_attrs.entrySet())
        {
            attrsI.put((String) cache.apply(entry.getKey()), cache.apply(entry.getValue()));
        }
        attrsI = (Map<String, Object>) cache.apply(attrsI);
        SimpleFilter filterI = (SimpleFilter) cache.apply(requirement.m_filter);
        return new BundleRequirementImpl(requirement.m_revision, namespaceI, dirsI, attrsI, filterI);
    }

    public BundleRequirementImpl(
        BundleRevision revision, String namespace,
        Map<String, String> dirs, Map<String, Object> attrs, SimpleFilter filter)
    {
        m_revision = revision;
        m_namespace = namespace;
        m_dirs =  Util.newImmutableMap(dirs);
        m_attrs =  Util.newImmutableMap(attrs);
        m_filter = filter;

        // Find resolution import directives.
        boolean optional = false;
        if (m_dirs.containsKey(Constants.RESOLUTION_DIRECTIVE)
            && m_dirs.get(Constants.RESOLUTION_DIRECTIVE).equals(Constants.RESOLUTION_OPTIONAL))
        {
            optional = true;
        }
        m_optional = optional;
    }

    public BundleRequirementImpl(
        BundleRevision revision, String namespace,
        Map<String, String> dirs, Map<String, Object> attrs)
    {
        this(revision, namespace, dirs, Collections.emptyMap(), SimpleFilter.convert(attrs));
    }

    @Override
	public String getNamespace()
    {
        return m_namespace;
    }

    @Override
	public Map<String, String> getDirectives()
    {
        return m_dirs;
    }

    @Override
	public Map<String, Object> getAttributes()
    {
        return m_attrs;
    }

    @Override
	public BundleRevision getResource()
    {
        return m_revision;
    }

    @Override
	public BundleRevision getRevision()
    {
        return m_revision;
    }

    @Override
	public boolean matches(BundleCapability cap)
    {
        return CapabilitySet.matches(cap, getFilter());
    }

    public boolean isOptional()
    {
        return m_optional;
    }

    public SimpleFilter getFilter()
    {
        return m_filter;
    }

    @Override
    public String toString()
    {
        return "[" + m_revision + "] " + m_namespace + "; " + getFilter().toString();
    }
}
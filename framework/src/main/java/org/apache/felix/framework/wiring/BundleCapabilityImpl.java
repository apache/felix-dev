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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Function;

import org.apache.felix.framework.util.Util;
import org.apache.felix.framework.util.manifestparser.ManifestParser;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;

public class BundleCapabilityImpl implements BundleCapability
{
    public static final String VERSION_ATTR = "version";

    private final BundleRevision m_revision;
    private final String m_namespace;
    private final Map<String, String> m_dirs;
    private final Map<String, Object> m_attrs;
    private final List<String> m_uses;
    private final Set<String> m_mandatory;

    public static BundleCapabilityImpl createFrom(BundleCapabilityImpl capability, Function<Object, Object> cache)
    {
        String namespaceI = (String) cache.apply(capability.m_namespace);
        Map<String, String> dirsI = new HashMap<>();
        for (Map.Entry<String, String> entry : capability.m_dirs.entrySet())
        {
            dirsI.put((String) cache.apply(entry.getKey()), (String) cache.apply(entry.getValue()));
        }
        dirsI = (Map<String, String>) cache.apply(dirsI);

        Map<String, Object> attrsI = new HashMap<>();
        for (Map.Entry<String, Object> entry : capability.m_attrs.entrySet())
        {
            attrsI.put((String) cache.apply(entry.getKey()), cache.apply(entry.getValue()));
        }
        attrsI = (Map<String, Object>) cache.apply(attrsI);

        return new BundleCapabilityImpl(capability.m_revision, namespaceI, dirsI, attrsI);
    }

    public BundleCapabilityImpl(BundleRevision revision, String namespace,
        Map<String, String> dirs, Map<String, Object> attrs)
    {
        m_namespace = namespace;
        m_revision = revision;
        m_dirs = Util.newImmutableMap(dirs);
        m_attrs = Util.newImmutableMap(attrs);

        // Find all export directives: uses, mandatory, include, and exclude.

        List<String> uses = Collections.emptyList();
        String value = m_dirs.get(Constants.USES_DIRECTIVE);
        if (value != null)
        {
            // Parse these uses directive.
            StringTokenizer tok = new StringTokenizer(value, ",");
            uses = new ArrayList<>(tok.countTokens());
            while (tok.hasMoreTokens())
            {
                uses.add(tok.nextToken().trim().intern());
            }
        }
        m_uses = uses;

        Set<String> mandatory = Collections.emptySet();
        value = m_dirs.get(Constants.MANDATORY_DIRECTIVE);
        if (value != null)
        {
            List<String> names = ManifestParser.parseDelimitedString(value, ",");
            mandatory = new HashSet<>(names.size());
            for (String name : names)
            {
                // If attribute exists, then record it as mandatory.
                if (m_attrs.containsKey(name))
                {
                    mandatory.add(name.intern());
                }
                // Otherwise, report an error.
                else
                {
                    throw new IllegalArgumentException(
                        "Mandatory attribute '" + name + "' does not exist.");
                }
            }
        }
        m_mandatory = mandatory;
    }

    public BundleRevision getResource()
    {
        return m_revision;
    }

    public BundleRevision getRevision()
    {
        return m_revision;
    }

    public String getNamespace()
    {
        return m_namespace;
    }

    public Map<String, String> getDirectives()
    {
        return m_dirs;
    }

    public Map<String, Object> getAttributes()
    {
        return m_attrs;
    }

    public boolean isAttributeMandatory(String name)
    {
        return !m_mandatory.isEmpty() && m_mandatory.contains(name);
    }

    public List<String> getUses()
    {
        return m_uses;
    }

    @Override
    public String toString()
    {
        if (m_revision == null)
        {
            return m_attrs.toString();
        }
        return "[" + m_revision + "] " + m_namespace + "; " + m_attrs;
    }
}
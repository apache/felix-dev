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
package org.apache.felix.resolver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * The core specification (Export-Package) defines the <code>bundle-symbolic-name</code> and
 * <code>bundle-version</code> of an export as the ones of the exporting bundle, "in the case of a fragment bundle,
 * this is the host bundle's symbolic name" (and version).
 * The resolve context matches the capability as declared by the fragment though, so requirements that ask for the
 * identity of a bundle are relaxed when the context is asked for the providers and are matched again
 * once the capability is hosted.
 */
final class HostIdentity
{
    private static final String SYMBOLIC_NAME = "bundle-symbolic-name";
    private static final String VERSION = "bundle-version";

    private HostIdentity()
    {
    }

    static boolean isIdentityRequested(Requirement requirement)
    {
        if (!PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace()))
        {
            return false;
        }
        String filter = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        return filter != null && (filter.contains(SYMBOLIC_NAME) || filter.contains(VERSION));
    }

    /** The requirement without the terms for the identity of the bundle. */
    static Requirement relax(Requirement requirement)
    {
        String filter = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        String relaxed = relax(filter, new int[] { 0 });
        final Map<String, String> directives = new HashMap<String, String>(requirement.getDirectives());
        directives.put(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
            relaxed == null ? "(" + requirement.getNamespace() + "=*)" : relaxed);
        final Requirement wrapped = requirement;
        return new Requirement()
        {
            public String getNamespace()
            {
                return wrapped.getNamespace();
            }

            public Map<String, String> getDirectives()
            {
                return directives;
            }

            public Map<String, Object> getAttributes()
            {
                return wrapped.getAttributes();
            }

            public Resource getResource()
            {
                return wrapped.getResource();
            }

            public String toString()
            {
                return wrapped.toString();
            }
        };
    }

    /** @return the relaxed term or <code>null</code> if it is always true */
    private static String relax(String filter, int[] position)
    {
        int start = position[0];
        position[0]++;
        char operator = filter.charAt(position[0]);
        String result;
        if (operator == '&' || operator == '|' || operator == '!')
        {
            position[0]++;
            StringBuilder terms = new StringBuilder();
            boolean changed = false;
            boolean always = false;
            int count = 0;
            while (filter.charAt(position[0]) == '(')
            {
                int termStart = position[0];
                String term = relax(filter, position);
                if (term == null)
                {
                    changed = true;
                    always |= operator == '|';
                }
                else
                {
                    changed |= !term.equals(filter.substring(termStart, position[0]));
                    terms.append(term);
                    count++;
                }
            }
            if (operator == '!')
            {
                result = changed ? null : "(!" + terms + ")";
            }
            else if (always || count == 0)
            {
                result = null;
            }
            else
            {
                result = "(" + operator + terms + ")";
            }
        }
        else
        {
            int end = position[0];
            while (filter.charAt(end) != ')')
            {
                end += filter.charAt(end) == '\\' ? 2 : 1;
            }
            String attribute = filter.substring(start + 1, end).split("[=<>~]", 2)[0].trim();
            result = SYMBOLIC_NAME.equalsIgnoreCase(attribute) || VERSION.equalsIgnoreCase(attribute)
                ? null : filter.substring(start, end + 1);
            position[0] = end;
        }
        // the closing parenthesis
        position[0]++;
        return result;
    }

    /** @return true if the capability, as provided by its host, matches the filter of the requirement */
    static boolean matches(Requirement requirement, Capability hosted)
    {
        try
        {
            return FrameworkUtil.createFilter(requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE))
                .matches(new Hashtable<String, Object>(hosted.getAttributes()));
        }
        catch (InvalidSyntaxException e)
        {
            return true;
        }
    }

    /** The attributes of a capability of a fragment as they are provided by the host. */
    static Map<String, Object> attributes(Resource host, Capability declared)
    {
        Map<String, Object> attributes = declared.getAttributes();
        if (!PackageNamespace.PACKAGE_NAMESPACE.equals(declared.getNamespace())
            || (!attributes.containsKey(SYMBOLIC_NAME) && !attributes.containsKey(VERSION)))
        {
            return attributes;
        }
        Map<String, Object> hosted = new HashMap<String, Object>(attributes);
        if (attributes.containsKey(SYMBOLIC_NAME))
        {
            hosted.put(SYMBOLIC_NAME, Util.getSymbolicName(host));
        }
        if (attributes.containsKey(VERSION))
        {
            hosted.put(VERSION, Util.getVersion(host));
        }
        return Collections.unmodifiableMap(hosted);
    }
}

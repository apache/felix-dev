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
package org.apache.felix.framework;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.framework.Version;

/**
 * Converts generic version identifiers to the {@link Version} instances.
 */
public class VersionConverter
{

    private static final Pattern FUZZY_VERSION = Pattern.compile("(\\d+)(\\.(\\d+)(\\.(\\d+))?)?([^a-zA-Z0-9](.*))?",
        Pattern.DOTALL);

    /**
     * Converts generic version id to the {@link Version} instance. Examples:
     *
     * <pre>
     * 0.0 -> 0.0.0
     * 2.3.4-SNAPSHOT -> 2.3.4.SNAPSHOT
     * 8.7.6-special-edition -> 8.7.6.special-edition
     * </pre>
     *
     * @param value any usual version id parseable by the {@link Version} class constructor after
     *            adding missing implicit values.
     * @return {@link Version}
     * @throws IllegalArgumentException If the numerical components are negative
     *         or the qualifier string is invalid.
     */
    public static Version toOsgiVersion(String value) throws IllegalArgumentException
    {
        return new Version(cleanupVersion(value));
    }

    private static String cleanupVersion(String version)
    {
        StringBuilder result = new StringBuilder();
        Matcher m = FUZZY_VERSION.matcher(version);
        if (m.matches())
        {
            String major = m.group(1);
            String minor = m.group(3);
            String micro = m.group(5);
            String qualifier = m.group(7);

            if (major != null)
            {
                result.append(major);
                if (minor != null)
                {
                    result.append(".");
                    result.append(minor);
                    if (micro != null)
                    {
                        result.append(".");
                        result.append(micro);
                        if (qualifier != null && !qualifier.isEmpty())
                        {
                            result.append(".");
                            cleanupModifier(result, qualifier);
                        }
                    }
                    else if (qualifier != null && !qualifier.isEmpty())
                    {
                        result.append(".0.");
                        cleanupModifier(result, qualifier);
                    }
                    else
                    {
                        result.append(".0");
                    }
                }
                else if (qualifier != null && !qualifier.isEmpty())
                {
                    result.append(".0.0.");
                    cleanupModifier(result, qualifier);
                }
                else
                {
                    result.append(".0.0");
                }
            }
        }
        else
        {
            result.append("0.0.0.");
            cleanupModifier(result, version);
        }
        return result.toString();
    }


    private static void cleanupModifier(StringBuilder result, String modifier)
    {
        for (int i = 0; i < modifier.length(); i++)
        {
            char c = modifier.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '-')
            {
                result.append(c);
            }
            else
            {
                result.append('_');
            }
        }
    }
}

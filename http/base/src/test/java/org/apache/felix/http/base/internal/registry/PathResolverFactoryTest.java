/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.http.base.internal.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PathResolverFactoryTest {

    private void assertResult(final PathResolver resolver,
            final String path,
            final String expectedServletPath,
            final String expectedPathInfo,
            final MappingType match,
            final String matchPattern,
            final String matchValue)
    {
        final PathResolution pr = resolver.resolve(path);
        assertNotNull(pr);
        assertEquals(path, pr.requestURI);
        assertEquals(expectedServletPath, pr.servletPath);
        if ( expectedPathInfo == null )
        {
            assertNull(pr.pathInfo);
        }
        else
        {
            assertEquals(expectedPathInfo, pr.pathInfo);
        }
        assertEquals(match, pr.mappingType);
        assertEquals(matchPattern, pr.matchedPattern);
        assertEquals(matchValue, pr.matchValue);
    }

    @Test public void testRootMatching()
    {
        final PathResolver pr = PathResolverFactory.createPatternMatcher(null, "");
        assertNotNull(pr);

        assertResult(pr, "/", "", "/", MappingType.CONTEXT_ROOT, "", "");
        assertResult(pr, "", "", "/", MappingType.CONTEXT_ROOT, "", "");

        assertNull(pr.resolve("/foo"));
    }

    @Test public void testDefaultMatcher()
    {
        final PathResolver pr = PathResolverFactory.createPatternMatcher(null, "/");
        assertNotNull(pr);

        assertResult(pr, "/foo/bar", "/foo/bar", null, MappingType.DEFAULT, "/", "");
        assertResult(pr, "/foo", "/foo", null, MappingType.DEFAULT, "/", "");
    }

    @Test public void testPathMatcherRoot()
    {
        final PathResolver pr = PathResolverFactory.createPatternMatcher(null, "/*");
        assertNotNull(pr);

        assertResult(pr, "/foo", "", "/foo", MappingType.PATH, "/*", "foo");
        assertResult(pr, "/foo/bar", "", "/foo/bar", MappingType.PATH, "/*", "foo/bar");

        assertResult(pr, "/", "", "/", MappingType.PATH, "/*", "");

        assertResult(pr, "", "", null, MappingType.PATH, "/*", "");
    }

    @Test public void testPathMatcherSub()
    {
        final PathResolver pr = PathResolverFactory.createPatternMatcher(null, "/path/*");
        assertNotNull(pr);

        assertResult(pr, "/path/foo", "/path", "/foo", MappingType.PATH, "/path/*", "foo");
    }

    @Test public void testExactMatcher()
    {
        final PathResolver pr = PathResolverFactory.createPatternMatcher(null, "/MyServlet");
        assertNotNull(pr);

        assertResult(pr, "/MyServlet", "/MyServlet", null, MappingType.EXACT, "/MyServlet", "MyServlet");
    }

    @Test public void testExtensionMatcher()
    {
        final PathResolver pr = PathResolverFactory.createPatternMatcher(null, "*.extension");
        assertNotNull(pr);

        assertResult(pr, "/foo.extension", "/foo.extension", null, MappingType.EXTENSION, "*.extension", "foo");
    }
}

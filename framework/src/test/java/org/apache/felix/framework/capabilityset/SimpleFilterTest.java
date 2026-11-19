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
package org.apache.felix.framework.capabilityset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.osgi.framework.VersionRange;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class SimpleFilterTest
{
    @Test
    void convertListAttribute()
    {
        SimpleFilter sf = SimpleFilter.convert(
            Collections.singletonMap("colors", Arrays.asList("red", "blue")));
        assertThat(sf).hasToString("(&(colors=red)(colors=blue))");
    }

    @Test
    void convertEmptyListAttribute()
    {
        SimpleFilter sf = SimpleFilter.convert(
            Collections.singletonMap("colors", Collections.emptyList()));
        assertThat(sf).hasToString("(colors=*)").isEqualTo(SimpleFilter.parse("(colors=*)"));

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("osgi.wiring.package", "org.example");
        attrs.put("colors", Collections.emptyList());
        assertThat(SimpleFilter.convert(attrs)).hasToString("(&(osgi.wiring.package=org.example)(colors=*))");
    }

    @Test
    void convertSingleBoundedVersionRange()
    {
        SimpleFilter sf = SimpleFilter.convert(
            Collections.singletonMap("version", new VersionRange("[1.0,2.0)")));
        assertThat(sf).hasToString("(&(version>=1.0.0)(!(version>=2.0.0)))");
    }

    @Test
    void substringMatching()
    {
        List<String> pieces;

        pieces = SimpleFilter.parseSubstring("*");
        assertThat(SimpleFilter.compareSubstring(pieces, "")).as("Should match!").isTrue();

        pieces = SimpleFilter.parseSubstring("foo");
        assertThat(SimpleFilter.compareSubstring(pieces, "")).as("Should not match!").isFalse();

        pieces = SimpleFilter.parseSubstring("");
        assertThat(SimpleFilter.compareSubstring(pieces, "")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "foo")).as("Should not match!").isFalse();

        pieces = SimpleFilter.parseSubstring("foo");
        assertThat(SimpleFilter.compareSubstring(pieces, "foo")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "barfoo")).as("Should not match!").isFalse();
        assertThat(SimpleFilter.compareSubstring(pieces, "foobar")).as("Should not match!").isFalse();

        pieces = SimpleFilter.parseSubstring("foo*");
        assertThat(SimpleFilter.compareSubstring(pieces, "foo")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "barfoo")).as("Should not match!").isFalse();
        assertThat(SimpleFilter.compareSubstring(pieces, "foobar")).as("Should match!").isTrue();

        pieces = SimpleFilter.parseSubstring("*foo");
        assertThat(SimpleFilter.compareSubstring(pieces, "foo")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "barfoo")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "foobar")).as("Should match!").isFalse();

        pieces = SimpleFilter.parseSubstring("foo*bar");
        assertThat(SimpleFilter.compareSubstring(pieces, "foobar")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "barfoo")).as("Should not match!").isFalse();
        assertThat(SimpleFilter.compareSubstring(pieces, "foosldfjbar")).as("Should match!").isTrue();

        pieces = SimpleFilter.parseSubstring("*foo*bar");
        assertThat(SimpleFilter.compareSubstring(pieces, "foobar")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "foobarfoo")).as("Should not match!").isFalse();
        assertThat(SimpleFilter.compareSubstring(pieces, "barfoobar")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "sdffoobsdfbar")).as("Should match!").isTrue();

        pieces = SimpleFilter.parseSubstring("*foo*bar*");
        assertThat(SimpleFilter.compareSubstring(pieces, "foobar")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "foobarfoo")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "barfoobar")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "sdffoobsdfbar")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "sdffoobsdfbarlj")).as("Should match!").isTrue();
        assertThat(SimpleFilter.compareSubstring(pieces, "sdffobsdfbarlj")).as("Should not match!").isFalse();

        pieces = SimpleFilter.parseSubstring("*foo(*bar*");
        assertThat(SimpleFilter.compareSubstring(pieces, "foo()bar")).as("Should match!").isTrue();

        pieces = SimpleFilter.parseSubstring("*foo*bar*bar");
        assertThat(SimpleFilter.compareSubstring(pieces, "foobar")).as("Should not match!").isFalse();

        pieces = SimpleFilter.parseSubstring("aaaa*aaaa");
        assertThat(SimpleFilter.compareSubstring(pieces, "aaaaaaa")).as("Should not match!").isFalse();

        pieces = SimpleFilter.parseSubstring("aaa**aaa");
        assertThat(SimpleFilter.compareSubstring(pieces, "aaaaaa")).as("Should match!").isTrue();
    }
}

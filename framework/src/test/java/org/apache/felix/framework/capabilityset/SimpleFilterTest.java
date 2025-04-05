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

import java.util.List;

class SimpleFilterTest
{
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

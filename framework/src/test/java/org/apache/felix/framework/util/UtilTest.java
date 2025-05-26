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
package org.apache.felix.framework.util;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtilTest
{
    @Test
    void variableSubstitution()
    {
        Properties props = new Properties();
        props.setProperty("one", "${two}");
        props.setProperty("two", "2");
        String v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("2");

        props.clear();
        props.setProperty("one", "${two}${three}");
        props.setProperty("two", "2");
        props.setProperty("three", "3");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("23");

        props.clear();
        props.setProperty("one", "${two${three}}");
        props.setProperty("two3", "2");
        props.setProperty("three", "3");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("2");

        props.clear();
        props.setProperty("one", "${two${three}}");
        props.setProperty("two3", "2");
        System.setProperty("three", "3");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        System.getProperties().remove("three");
        assertThat(v).isEqualTo("2");

        props.clear();
        props.setProperty("one", "${two}");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEmpty();

        props.clear();
        props.setProperty("one", "{two");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("{two");

        props.clear();
        props.setProperty("one", "{two}");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("{two}");

        props.clear();
        props.setProperty("one", "${two");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("${two");

        props.clear();
        props.setProperty("one", "${two${two}");
        props.setProperty("two", "2");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("${two2");

        props.clear();
        props.setProperty("one", "{two${two}}");
        props.setProperty("two", "2");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("{two2}");

        props.clear();
        props.setProperty("one", "{two}${two");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("{two}${two");

        props.clear();
        props.setProperty("one", "leading text ${two}");
        props.setProperty("two", "2");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("leading text 2");

        props.clear();
        props.setProperty("one", "${two} trailing text");
        props.setProperty("two", "2");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("2 trailing text");

        props.clear();
        props.setProperty("one", "${two} middle text ${three}");
        props.setProperty("two", "2");
        props.setProperty("three", "3");
        v = Util.substVars(props.getProperty("one"), "one", null, props);
        assertThat(v).isEqualTo("2 middle text 3");
    }
}
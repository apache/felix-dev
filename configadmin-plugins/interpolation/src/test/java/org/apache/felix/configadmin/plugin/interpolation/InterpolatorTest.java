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
package org.apache.felix.configadmin.plugin.interpolation;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.felix.configadmin.plugin.interpolation.Interpolator.Provider;
import org.junit.Test;

public class InterpolatorTest {

    @Test
    public void testNoValue() {
        assertEquals("$[foo:hello]", Interpolator.replace("$[foo:hello]", (type, name, dir) -> null));
    }

    @Test
    public void testValue() {
        assertEquals("hello world", Interpolator.replace("$[foo:hello]", (type, name, dir) -> {
            if ("foo".equals(type) && "hello".equals(name)) {
                return "hello world";
            }
            return null;
        }));
    }

    @Test
    public void testValueAndConstantText() {
        assertEquals("beforehello worldafter", Interpolator.replace("before$[foo:hello]after", (type, name, dir) -> {
            if ("foo".equals(type) && "hello".equals(name)) {
                return "hello world";
            }
            return null;
        }));
    }

    @Test
    public void testRecursion() {
        assertEquals("beforehello worldafter",
                Interpolator.replace("before$[foo:$[foo:inner]]after", (type, name, dir) -> {
                    if ("foo".equals(type) && "hello".equals(name)) {
                        return "hello world";
                    } else if ("foo".equals(type) && "inner".equals(name)) {
                        return "hello";
                    }
                    return null;
                }));
    }

    @Test
    public void testEscaping() {
        final Provider p = new Provider() {

            @Override
            public Object provide(String type, String name, Map<String, String> directives) {
                return "value";
            }
        };
        assertEquals("$[no:replacement]", Interpolator.replace("\\$[no:replacement]", p));
    }

    @Test
    public void testParseDirectives() {
        final Map<String, String> directives = Interpolator.parseDirectives("a=1;b=2");
        assertEquals(2, directives.size());
        assertEquals("1", directives.get("a"));
        assertEquals("2", directives.get("b"));
    }

    @Test
    public void testParseDirectivesWithEscaping() {
        final Map<String, String> directives = Interpolator.parseDirectives("a=1\\;b=2");
        assertEquals(1, directives.size());
        assertEquals("1;b=2", directives.get("a"));
    }


    @Test
    public void testNestedDefaults() {
        final AtomicInteger usecase = new AtomicInteger();
        final Provider p = new Provider() {

            @Override
            public Object provide(String type, String name, Map<String, String> directives) {
                if ( usecase.get() == 1 && "env".equals(type) ) {
                    return "env.host";
                }
                if ( usecase.get() == 2 && "prop".equals(type) ) {
                    return "prop.host";
                }
                return directives.getOrDefault("default", null);
            }
        };

        final String test = "$[env:MQTT_WRITE_HOST;default=$[prop:MQTT_WRITE_HOST;default=some.host]]";
        // usecase 1 : env provides the value
        usecase.set(1);
        assertEquals("env.host", Interpolator.replace(test, p));
        // usecase 2 : prop provides the value
        usecase.set(2);
        assertEquals("prop.host", Interpolator.replace(test, p));
        // usecase 3 : default is used
        usecase.set(3);
        assertEquals("some.host", Interpolator.replace(test, p));
    }
}

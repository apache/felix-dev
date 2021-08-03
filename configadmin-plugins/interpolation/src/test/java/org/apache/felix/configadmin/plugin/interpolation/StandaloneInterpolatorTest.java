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
import static org.junit.Assert.assertNotEquals;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.apache.felix.configadmin.plugin.interpolation.StandaloneInterpolator;
import org.junit.Test;
import org.osgi.framework.Constants;

public class StandaloneInterpolatorTest {
    @Test
    public void testFrameworkPropertyInterpolation() {
        Map<String, String> fprops = new HashMap<>();
        fprops.put("my.prop", "12345");
        fprops.put("my.other.prop", "ABCDE");

        StandaloneInterpolator interpolator = new StandaloneInterpolator(fprops);

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("foo", "bar");
        dict.put("nonsubst", "$[yeah:yeah]");
        dict.put("prop", "$[prop:my.prop]");
        interpolator.interpolate("org.foo.bar", dict);

        assertEquals(3, Collections.list(dict.keys()).size());
        assertEquals("bar", dict.get("foo"));
        assertEquals("$[yeah:yeah]", dict.get("nonsubst"));
        assertEquals("12345", dict.get("prop"));
    }

    @Test
    public void testEnvVarInterpolation() {
        String envUser = System.getenv("USER");
        String userVar;
        if (envUser == null) {
            envUser = System.getenv("USERNAME"); // maybe we're on Windows
            userVar = "USERNAME";
        } else {
            userVar = "USER";
        }

        StandaloneInterpolator interpolator = new StandaloneInterpolator(Collections.emptyMap());

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("someuser", "$[env:" + userVar + "]");
        dict.put(Constants.SERVICE_PID, "org.foo.bar");
        interpolator.interpolate("org.foo.bar", dict);

        assertEquals(envUser, dict.get("someuser"));
    }

    @Test
    public void testSecretInterpolation() {
        URL resUrl = getClass().getResource("/res1");
        File res1Dir = new File(resUrl.getFile());
        File res0Dir = new File(res1Dir.getParentFile(), "res0");

        StandaloneInterpolator interpolator = new StandaloneInterpolator(Collections.emptyMap(), res0Dir, res1Dir);

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("name", "$[secret:my.db]");
        dict.put("pass", "$[secret:my.pwd]");
        interpolator.interpolate("my.pid", dict);

        assertEquals(2, dict.size());
        assertEquals("tiger", dict.get("name"));
        assertEquals("$[secret:my.pwd]", dict.get("pass"));
    }

    @Test
    public void testSecretInterpolationEncoding() {
        URL resUrl = getClass().getResource("/res1");
        File res1Dir = new File(resUrl.getFile());
        File res0Dir = new File(res1Dir.getParentFile(), "res0");

        StandaloneInterpolator interpolator = new StandaloneInterpolator(Collections.emptyMap(), "UTF-8", res1Dir, res0Dir);

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("name", "$[secret:my.db]");
        dict.put("pass", "$[secret:my.pwd]");
        interpolator.interpolate("my.pid", dict);

        assertEquals("tiger", dict.get("name"));
    }

    @Test
    public void testSecretInterpolationEncoding2() {
        URL resUrl = getClass().getResource("/res1");
        File res1Dir = new File(resUrl.getFile());

        StandaloneInterpolator interpolator = new StandaloneInterpolator(Collections.emptyMap(), "UTF-16", res1Dir);

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("name", "$[secret:my.db]");
        dict.put("pass", "$[secret:my.pwd]");
        interpolator.interpolate("my.pid", dict);

        assertNotEquals("tiger", dict.get("name"));
        assertNotEquals("$[secret:my.db]", dict.get("name"));
    }
}

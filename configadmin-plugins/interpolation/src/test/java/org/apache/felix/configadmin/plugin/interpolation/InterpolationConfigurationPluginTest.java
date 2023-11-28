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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

public class InterpolationConfigurationPluginTest {
    @Test
    public void testSelfReferenceConfiguration() throws Exception {
        BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.when(bc.getProperty("foo.bar")).thenReturn("hello there");

        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(bc::getProperty, null, null);

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("someprop", "$[prop:foo.bar]");
        dict.put("nope", "$[blah:blah]");
        dict.put("self.reference", "$[conf:someprop] foo");
        dict.put("long.array", new long[] {3L, 250000L});
        dict.put("self.long.array", "$[conf:long.array]");
        dict.put("string.array", new String[] {"foo", "bar"});
        dict.put("self.string.array", "$[conf:string.array]");
        dict.put("char.array", new char[] {'c', '香'});
        dict.put("self.char.array", "$[conf:char.array]");
        dict.put("self.circular.reference", "$[conf:self.circular.reference]");
        dict.put("self.circular.reference.a", "$[conf:self.circular.reference.b]");
        dict.put("self.circular.reference.b", "$[conf:self.circular.reference.a]");

        plugin.modifyConfiguration(null, dict);
        assertEquals("hello there", dict.get("someprop"));
        assertEquals("$[blah:blah]", dict.get("nope"));
        assertEquals("hello there foo", dict.get("self.reference"));
        assertEquals("[3, 250000]", dict.get("self.long.array"));
        assertEquals("[foo, bar]", dict.get("self.string.array"));
        assertEquals("[c, 香]", dict.get("self.char.array"));
        assertEquals("self.circular.reference", dict.get("self.circular.reference"));
        assertEquals("self.circular.reference.a", dict.get("self.circular.reference.a"));
        assertEquals("self.circular.reference.a", dict.get("self.circular.reference.b"));
    }

    @Test
    public void testModifyConfiguration() throws Exception {
        String envUser = System.getenv("USER");
        String userVar;
        if (envUser == null) {
            envUser = System.getenv("USERNAME"); // maybe we're on Windows
            userVar = "USERNAME";
        } else {
            userVar = "USER";
        }

        String rf = getClass().getResource("/testfile").getFile();

        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(null,
                new File(rf).getParent(), null);

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("foo", "bar");
        dict.put("replaced", "$[secret:testfile]");
        dict.put("defaulted", "$[secret:not_there;default=defval123]");
        dict.put("cur.user", "$[env:" + userVar + "]");
        dict.put("intval", 999);
        dict.put(Constants.SERVICE_PID, "my.service");
        plugin.modifyConfiguration(null, dict);

        assertEquals(6, dict.size());
        assertEquals("bar", dict.get("foo"));
        assertEquals("line1\nline2", dict.get("replaced"));
        assertEquals("defval123", dict.get("defaulted"));
        assertEquals(envUser, dict.get("cur.user"));
        assertEquals("my.service", dict.get(Constants.SERVICE_PID));
        assertEquals(999, dict.get("intval"));
    }

    @Test
    public void testModifyConfigurationNoDirConfig() throws Exception {
        BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.when(bc.getProperty("foo.bar")).thenReturn("hello there");

        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(bc::getProperty, null, null);

        String envUser = System.getenv("USER");
        String userVar;
        if (envUser == null) {
            envUser = System.getenv("USERNAME"); // maybe we're on Windows
            userVar = "USERNAME";
        } else {
            userVar = "USER";
        }

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("cur.user", "YY$[env:" + userVar + "]X$[env:" + userVar + "]YY");
        dict.put("someprop", "$[prop:foo.bar]");
        dict.put("nope", "$[blah:blah]");
        dict.put("left.as.is", "$[env:boo]");

        plugin.modifyConfiguration(null, dict);
        assertEquals("YY" + envUser + "X" + envUser + "YY", dict.get("cur.user"));
        assertEquals("hello there", dict.get("someprop"));
        assertEquals("$[blah:blah]", dict.get("nope"));
        assertEquals("$[env:boo]", dict.get("left.as.is"));
    }

    @Test
    public void testSubdirReplacement() throws Exception {
        String rf = getClass().getResource("/sub/sub2/testfile2").getFile();

        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(null,
                new File(rf).getParentFile().getParent(), null);

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("substed", "$[secret:sub2/testfile2]");
        dict.put("not1", "$[secret:../testfile]");
        dict.put("not2", "$[secret:sub2/../../testfile.txt]");
        plugin.modifyConfiguration(null, dict);

        assertEquals(3, dict.size());
        assertEquals("the_content", dict.get("substed"));
        assertEquals("$[secret:../testfile]", dict.get("not1"));
        assertEquals("$[secret:sub2/../../testfile.txt]", dict.get("not2"));
    }

    @Test
    public void testReplacement() throws Exception {
        String rf = getClass().getResource("/other/testfile.txt").getFile();
        File file = new File(rf);
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(null,
                file.getParent() + "," + file.getParentFile().getParent(), null);

        assertEquals("xxla la layy", plugin.replace("akey", "xx$[secret:testfile.txt]yy", "apid", new Hashtable<>()));
        String doesNotReplace = "xx$[" + rf + "]yy";
        assertEquals(doesNotReplace, plugin.replace("akey", doesNotReplace, "apid", new Hashtable<>()));
    }

    @Test
    public void testNoReplacement() throws IOException {
        String rf = getClass().getResource("/other/testfile.txt").getFile();
        File file = new File(rf);
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(null,
                file.getParent() + "," + file.getParentFile().getParent(), null);

        assertEquals("foo", plugin.replace("akey", "foo", "apid", new Hashtable<>()));
    }

    @Test
    public void testDefault() throws IOException {
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(null, null, null);

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("defaulted", "$[env:notset;default=foo]");
        dict.put("defaulted2", "$[env:notset;default=]");
        dict.put("defaulted3", "$[env:notset;default=foo=bar]");
        dict.put("defaulted4", "$[env:notset;default=foo;=bar]");
        dict.put("defaulted5", "$[env:notset;default= ]");
        dict.put("defaulted6", "$[env:notset;default");
        dict.put("defaulted7", "$[env:notset;default=/()^$]");
        dict.put("defaulted8", "$[env:notset;default=[8080]]");
        dict.put("defaulted9", "$[env:notset;default=[aabb]cc]");
        dict.put("defaulted10", "$[env:notset;default=\\[aabb]cc]");
        dict.put("defaulted11", "$[env:notset;default=\\[aabb\\]cc]");
        dict.put("defaulted12", "$[env:notset;default=\\[aabb\\]cc\\]]");

        plugin.modifyConfiguration(null, dict);

        assertEquals("foo", dict.get("defaulted"));
        assertEquals("", dict.get("defaulted2"));
        assertEquals("foo=bar", dict.get("defaulted3"));
        assertEquals("foo", dict.get("defaulted4")); // semicolon is not supported in values
        assertEquals(" ", dict.get("defaulted5"));
        assertEquals("$[env:notset;default", dict.get("defaulted6"));
        assertEquals("/()^$", dict.get("defaulted7"));
        assertEquals("[8080]", dict.get("defaulted8"));
        assertEquals("[aabb]cc", dict.get("defaulted9"));
        assertEquals("[aabbcc]", dict.get("defaulted10"));
        assertEquals("[aabb]cc", dict.get("defaulted11"));
        assertEquals("[aabb]cc]", dict.get("defaulted12"));
    }

    @Test
    public void testTypeConversion() throws IOException {
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(null, null, null);

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("defaulted", "$[env:notset;default=123;type=Integer]");

        plugin.modifyConfiguration(null, dict);

        assertEquals(Integer.valueOf(123), dict.get("defaulted"));
    }

    @Test
    public void testReplacementInStringArray() throws IOException {
        BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.when(bc.getProperty("foo.bar")).thenReturn("hello there");
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(bc::getProperty, null, null);

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("array", new String[] { "1", "$[prop:foo.bar]", "3" });

        plugin.modifyConfiguration(null, dict);

        String[] array = (String[]) dict.get("array");
        assertEquals(3, array.length);
        assertEquals("1", array[0]);
        assertEquals("hello there", array[1]);
        assertEquals("3", array[2]);
    }

    @Test
    public void testMultiplePlaceholders() throws Exception {
        BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.when(bc.getProperty("foo.bar")).thenReturn("hello there");
        String rf = getClass().getResource("/other/testfile.txt").getFile();
        File file = new File(rf);
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(bc::getProperty,
                file.getParent() + "," + file.getParentFile().getParent(), null);

        assertEquals("xxhello thereyyhello therezz",
                plugin.replace("akey", "xx$[prop:foo.bar]yy$[prop:foo.bar]zz", "apid", new Hashtable<>()));

        assertEquals("xxla la layyhello therezz",
                plugin.replace("akey", "xx$[secret:testfile.txt]yy$[prop:foo.bar]zz", "apid", new Hashtable<>()));
    }

    @Test
    public void testMultipleDirectories() throws Exception {
        BundleContext bc = Mockito.mock(BundleContext.class);
        String rf = getClass().getResource("/other/testfile.txt").getFile();
        File file = new File(rf);
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(bc::getProperty,
                file.getParent() + "," + file.getParentFile().getParent(), null);

        assertEquals("xxhello thereyyhello therezz",
                plugin.replace("akey", "xx$[secret:foo.bar]yy$[secret:foo.bar]zz", "apid", new Hashtable<>()));

        assertEquals("xxla la layyhello therezz",
                plugin.replace("akey", "xx$[secret:testfile.txt]yy$[secret:foo.bar]zz", "apid", new Hashtable<>()));
    }

    @Test
    public void testNestedPlaceholders() throws Exception {
        BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.when(bc.getProperty("foo.bar")).thenReturn("hello there");
        Mockito.when(bc.getProperty("key")).thenReturn("foo.bar");
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(bc::getProperty, null, null);

        assertEquals("hello there", plugin.replace("akey", "$[prop:$[prop:key]]", "apid", new Hashtable<>()));
    }

    @Test
    public void testArraySplit() throws Exception {
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(null, null, null);

        assertArrayEquals(new String[] { "a,b,c" }, plugin.split("a,b,c", "."));
        assertArrayEquals(new String[] { "a", "b", "c" }, plugin.split("a,b,c", ","));
        assertArrayEquals(new String[] { "a,b", "c" }, plugin.split("a\\,b,c", ","));
        assertArrayEquals(new String[] { "a\\", "b", "c" }, plugin.split("a\\\\,b,c", ","));
    }

    @Test
    public void testArrayTypeConversion() throws Exception {
        BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.when(bc.getProperty("foo")).thenReturn("2000,3000");
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(bc::getProperty, null, null);

        assertArrayEquals(new Integer[] { 2000, 3000 },
                (Integer[]) plugin.replace("key", "$[prop:foo;type=Integer[];delimiter=,]", "somepid", new Hashtable<>()));
        assertArrayEquals(new Integer[] { 1, 2 },
                (Integer[]) plugin.replace("key", "$[prop:bar;type=Integer[];delimiter=,;default=1,2]", "somepid", new Hashtable<>()));
    }

    @Test
    public void testConvertTypeDelimiterWithoutType() throws Exception {
        BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.when(bc.getProperty("foo")).thenReturn("2000,3000");
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(bc::getProperty, null, null);

        Object obj = plugin.convertType(null, "a,b", ",");
        assertArrayEquals(new String[] {"a", "b"}, (Object[])obj);

        obj = plugin.convertType("String", "a,b", ",");
        assertEquals("a,b", obj);
    }

    @Test
    public void testModifyConfigurationWithArrayInArray() throws Exception {
        BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.when(bc.getProperty("foo")).thenReturn("2000,3000");
        InterpolationConfigurationPlugin plugin = new InterpolationConfigurationPlugin(bc::getProperty, null, null);

        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("array", new String[] {"1000", "$[prop:foo;type=String[];delimiter=,]", "4000"});
        plugin.modifyConfiguration(null, dict);

        assertEquals(1, dict.size());
        assertArrayEquals(new String[] {"1000", "2000", "3000", "4000"}, (String[])dict.get("array"));
    }
}

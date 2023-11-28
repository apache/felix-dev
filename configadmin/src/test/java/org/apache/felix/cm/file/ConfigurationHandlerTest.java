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
package org.apache.felix.cm.file;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class ConfigurationHandlerTest {

    private static final String SERVICE_PID = "service.pid";

    private static final String PAR_1 = "mongouri";
    private static final String VAL_1 = "127.0.0.1:27017";
    private static final String PAR_2 = "customBlobStore";
    private static final String VAL_2 = "true";

    private static final String CONFIG =
        "#mongodb URI\n" +
        PAR_1 + "=\"" + VAL_1 + "\"\n" +
        "\n" +
        "  # custom datastore\n" +
        PAR_2 + "=B\"" + VAL_2 + "\"\n";

    private static final String MULTI_LINE_CONFIG = "# Licensed to the Apache Software Foundation (ASF) under one or more\n"
    + "# contributor license agreements. See the NOTICE file distributed with this\n"
    + "# work for additional information regarding copyright ownership. The ASF\n"
    + "# licenses this file to You under the Apache License, Version 2.0 (the\n"
    + "# \"License\"); you may not use this file except in compliance with the License.\n"
    + "# You may obtain a copy of the License at\n"
    + "#\n"
    + "# http://www.apache.org/licenses/LICENSE-2.0\n"
    + "#\n"
    + "# Unless required by applicable law or agreed to in writing, software\n"
    + "# distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT\n"
    + "# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the\n"
    + "# License for the specific language governing permissions and limitations under\n"
    + "# the License.\n"
    + "\n"
    + "scripts=[\\\n"
    + "    \"create service user test-user\n"
    + "    set ACL for test-user\n"
    + "        allow    jcr:read    on /conf\n"
    + "    end\",\\\n"
    + "    \"create service user test-user2\n"
    + "    set ACL for test-user2\n"
    + "        allow    jcr:read    on /conf\n"
    + "    end\",\\\n"
    + "    \"create path /test\n"
    + "    set properties on /test\n"
    + "        set testprop to \\\"one\\=two\\\"\n"
    + "    end\"\\\n"
    + "]";
    
    @Test
    public void testComments() throws IOException
    {
        @SuppressWarnings("unchecked")
        final Dictionary<String, Object> dict = ConfigurationHandler.read(new ByteArrayInputStream(CONFIG.getBytes("UTF-8")));
        Assert.assertEquals(2, dict.size());
        Assert.assertEquals(VAL_1, dict.get(PAR_1));
        Assert.assertEquals(VAL_2, dict.get(PAR_2).toString());
    }

    @Test
    public void testMultiLineConfig() throws IOException
    {
        @SuppressWarnings("unchecked")
        final Dictionary<String, Object> dict = ConfigurationHandler.read(new ByteArrayInputStream(MULTI_LINE_CONFIG.getBytes("UTF-8")));
        final String[] scripts = (String[]) dict.get("scripts");
        Assert.assertNotNull(scripts);
        Assert.assertEquals(3, scripts.length);
    }

    @Test
    public void test_writeArray() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, Object> properties = new Hashtable<>();
        properties.put(SERVICE_PID , new String [] {"foo", "bar"});
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=[ \\\r\n  \"foo\", \\\r\n  \"bar\", \\\r\n  ]\r\n", entry);
    }

    @Test
    public void test_writeEmptyCollection() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, Object> properties = new Hashtable<>();
        properties.put(SERVICE_PID , new ArrayList<String>());
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=( \\\r\n)\r\n", entry);
    }

    @Test
    public void test_writeCollection() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, Object> properties = new Hashtable<>();
        List<String> list = new ArrayList<>();
        list.add("foo");
        list.add("bar");

        properties.put(SERVICE_PID , list);
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=( \\\r\n  \"foo\", \\\r\n  \"bar\", \\\r\n)\r\n", entry);
    }

    @Test
    public void test_writeSimpleString() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, String> properties = new Hashtable<>();
        properties.put(SERVICE_PID, "com.adobe.granite.foo.Bar");
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=\"com.adobe.granite.foo.Bar\"\r\n", entry);
    }

    @Test
    public void test_writeStringWithSpaces() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, String> properties = new Hashtable<>();
        properties.put("prop", "Hello World");
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("prop=\"Hello\\ World\"\r\n", entry);
    }

    @Test
    public void test_writeInteger() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, Integer> properties = new Hashtable<>();
        properties.put(SERVICE_PID, 1000);
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=I\"1000\"\r\n", entry);
    }

    @Test
    public void test_writeLong() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, Long> properties = new Hashtable<>();
        properties.put(SERVICE_PID, 1000L);
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=L\"1000\"\r\n", entry);
    }

    @Test
    public void test_writeFloat() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, Float> properties = new Hashtable<>();
        properties.put(SERVICE_PID, 3.6f);
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=F\"1080452710\"\r\n", entry);
    }

    @Test
    public void test_writeDouble() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, Double> properties = new Hashtable<>();
        properties.put(SERVICE_PID, 3.6d);
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=D\"4615288898129284301\"\r\n", entry);
    }

    @Test
    public void test_writeByte() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, Byte> properties = new Hashtable<>();
        properties.put(SERVICE_PID, Byte.parseByte("10"));
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=X\"10\"\r\n", entry);
    }

    @Test
    public void test_writeShort() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, Short> properties = new Hashtable<>();
        properties.put(SERVICE_PID, (short)10);
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=S\"10\"\r\n", entry);
    }

    @Test
    public void test_writeChar() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, Character> properties = new Hashtable<>();
        properties.put(SERVICE_PID, 'c');
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=C\"c\"\r\n", entry);
    }

    @Test
    public void test_writeBoolean() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, Boolean> properties = new Hashtable<>();
        properties.put(SERVICE_PID, true);
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("service.pid=B\"true\"\r\n", entry);
    }

    @Test
    public void test_writeSimpleStringWithError() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        Dictionary< String, String> properties = new Hashtable<>();
        properties.put("foo.bar", "com.adobe.granite.foo.Bar");
        ConfigurationHandler.write(out, properties);
        String entry = new String(((ByteArrayOutputStream)out).toByteArray(),"UTF-8");
        Assert.assertEquals("foo.bar=\"com.adobe.granite.foo.Bar\"\r\n", entry);
    }

    @Test
    public void test_readArray() throws IOException {
        String entry = "service.pid=[ \\\r\n  \"foo\", \\\r\n  \"bar\", \\\r\n  ]\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertArrayEquals(new String [] {"foo", "bar"}, (String [])dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readEmptyCollection() throws IOException {
        String entry = "service.pid=( \\\r\n)\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals(new ArrayList<String>(), dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readCollection() throws IOException {
        String entry = "service.pid=( \\\r\n  \"foo\", \\\r\n  \"bar\", \\\r\n)\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        List<String> list = new ArrayList<>();
        list.add("foo");
        list.add("bar");
        Assert.assertEquals(list, dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readSimpleString() throws IOException {
        String entry = "service.pid=\"com.adobe.granite.foo.Bar\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals( "com.adobe.granite.foo.Bar", dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readStringWithSpaces() throws IOException {
        String entry = "prop=\"Hello\\ World\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals( "Hello World", dictionary.get("prop"));
    }

    @Test
    public void test_readSimpleStrings() throws IOException {
        String entry = "service.pid=\"com.adobe.granite.foo.Bar\"\r\nfoo.bar=\"com.adobe.granite.foo.Baz\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(2, dictionary.size());
        Assert.assertEquals( "com.adobe.granite.foo.Bar", dictionary.get(SERVICE_PID));
        Assert.assertNotNull(dictionary.get("foo.bar"));
    }

    @Test
    public void test_readInteger() throws IOException {
        String entry = "service.pid=I\"1000\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals( 1000, dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readLong() throws IOException {
        String entry = "service.pid=L\"1000\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals( 1000L, dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readFloat() throws IOException {
        String entry = "service.pid=F\"1080452710\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals( 3.6f, dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readDouble() throws IOException {
        String entry = "service.pid=D\"4615288898129284301\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals( 3.6d, dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readByte() throws IOException {
        String entry = "service.pid=X\"10\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals((byte)10 , dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readShort() throws IOException {
        String entry = "service.pid=S\"10\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals((short)10 , dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readChar() throws IOException {
        String entry = "service.pid=C\"c\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals('c' , dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readBoolean() throws IOException {
        String entry = "service.pid=B\"true\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals(true , dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_backslash() throws IOException {
        final String VALUE = "val\\ue\\\\";
        final Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("key", VALUE);
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ConfigurationHandler.write(out, dict);

            try (final ByteArrayInputStream ins = new ByteArrayInputStream(out.toByteArray())) {
                @SuppressWarnings("unchecked")
                final Dictionary<String, Object> read = ConfigurationHandler.read(ins);

                Assert.assertNotNull(read.get("key"));
                Assert.assertEquals(VALUE, read.get("key"));
            }
        }
    }

    @Test
    public void test_readElementWithWhitespaceBeforeEQ() throws IOException {
        String entry = "service.pid  =\n\"com.adobe.granite.foo.Bar\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals( "com.adobe.granite.foo.Bar", dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readElementWithWhitespaceAfterEQ() throws IOException {
        String entry = "service.pid=  \"com.adobe.granite.foo.Bar\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals("com.adobe.granite.foo.Bar", dictionary.get(SERVICE_PID));
    }

    @Test
    public void test_readElementWithWhitespaceBeforeKey() throws IOException {
        String entry = "  service.pid=\"com.adobe.granite.foo.Bar\"\r\n";
        InputStream stream = new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> dictionary = ConfigurationHandler.read(stream);
        Assert.assertEquals(1, dictionary.size());
        Assert.assertEquals("com.adobe.granite.foo.Bar", dictionary.get(SERVICE_PID));
    }
}


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
package org.apache.felix.cm.json.io.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.apache.felix.cm.json.io.Configurations;
import org.junit.Test;

/**
 * This class tests the {@code JsonSupport} class, however it calls the api
 * through the public {@code Configurations} api.
 */
public class JsonSupportTest {

    @Test
    public void testConvertNullToObject() {
        assertNull(Configurations.convertToObject(null));
        assertNull(Configurations.convertToObject(JsonValue.NULL));
    }

    @Test
    public void testConvertBooleanToObject() {
        assertEquals(Boolean.TRUE, Configurations.convertToObject(JsonValue.TRUE));
        assertEquals(Boolean.FALSE, Configurations.convertToObject(JsonValue.FALSE));
    }

    @Test
    public void testConvertNumberToObject() {
        assertEquals(7L, Configurations.convertToObject(Json.createValue(7)));
        assertEquals(7L, Configurations.convertToObject(Json.createValue((long) 7)));
        assertEquals(3.1d, Configurations.convertToObject(Json.createValue(3.1)));
    }

    public void testConvertStringToObject() {
        assertEquals("hello world", Configurations.convertToObject(Json.createValue("hello world")));
    }

    public void testConvertObjectToObject() {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("hello", "world");

        assertEquals("{\"hello\":\"world\"}", Configurations.convertToObject(builder.build()));
    }

    @Test
    public void testConvertBooleanArrayToObject() {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        builder.add(true);
        builder.add(false);

        assertArrayEquals(new Boolean[] { true, false }, (Boolean[]) Configurations.convertToObject(builder.build()));
    }

    @Test
    public void testConvertNumberArrayToObject() {
        final JsonArrayBuilder lBuilder = Json.createArrayBuilder();
        lBuilder.add(5);
        lBuilder.add(3);

        final JsonArrayBuilder dBuilder = Json.createArrayBuilder();
        dBuilder.add(5.7);
        dBuilder.add(3.7);

        final JsonArrayBuilder mBuilder = Json.createArrayBuilder();
        mBuilder.add(5.7);
        mBuilder.add(3);

        assertArrayEquals(new Long[] { 5L, 3L }, (Long[]) Configurations.convertToObject(lBuilder.build()));
        assertArrayEquals(new Double[] { 5.7, 3.7 }, (Double[]) Configurations.convertToObject(dBuilder.build()));
        assertArrayEquals(new Double[] { 5.7, 3d }, (Double[]) Configurations.convertToObject(mBuilder.build()));
    }

    @Test
    public void testConvertStringArrayToObject() {
        final JsonArrayBuilder sBuilder = Json.createArrayBuilder();
        sBuilder.add("hello");
        sBuilder.add("world");

        assertArrayEquals(new String[] { "hello", "world" },
                (String[]) Configurations.convertToObject(sBuilder.build()));

        final JsonArrayBuilder mBuilder = Json.createArrayBuilder();
        mBuilder.add("hello");
        mBuilder.add("3");

        assertArrayEquals(new String[] { "hello", "3" }, (String[]) Configurations.convertToObject(mBuilder.build()));
    }

    @Test
    public void testConvertObjectArrayToObject() {
        final JsonArrayBuilder sBuilder = Json.createArrayBuilder();
        final JsonObjectBuilder o1 = Json.createObjectBuilder();
        o1.add("a", "1");
        final JsonObjectBuilder o2 = Json.createObjectBuilder();
        o2.add("b", 2);
        sBuilder.add(o1);
        sBuilder.add(o2);

        assertArrayEquals(new String[] { "{\"a\":\"1\"}", "{\"b\":2}" },
                (String[]) Configurations.convertToObject(sBuilder.build()));
    }

    private String parse(final String input) throws IOException {
        try ( final Reader reader = Configurations.jsonCommentAwareReader(new StringReader(input))) {
            final StringWriter w = new StringWriter();
            int l = 0;
            char[] buf = new char[4096];
            while ( (l = reader.read(buf)) > 0 ) {
                w.write(buf, 0, l);
            }
            w.flush();

            return w.toString();
        }
    }

    @Test
    public void testLineCommentTop() throws IOException {
        final String input = "// Some comment\n" +
                             "{\n"
                             + "  \"a\" : 1,\n"
                             + "  \"b\" : 2\n"
                             + "}\n";

        assertEquals("\n" +
                "{\n"
                + "  \"a\" : 1,\n"
                + "  \"b\" : 2\n"
                + "}\n", parse(input));
    }

    @Test
    public void testLineComment() throws IOException {
        final String input = "{\n"
                             + "  \"a\" : 1,\n"
                             + "  // another comment\n"
                             + "  \"b\" : 2\n"
                             + "}\n";

        assertEquals("{\n"
                + "  \"a\" : 1,\n"
                + "  \n"
                + "  \"b\" : 2\n"
                + "}\n", parse(input));
    }

    @Test
    public void testSeveralComments() throws IOException {
        final String input = "// Some comment\n" +
                             "{\n"
                             + "  \"a\" : 1,\n"
                             + "  // another comment\n"
                             + "  /** And more\n"
                             + "   * comments\n"
                             + "   */\n"
                             + "  \"b\" : 2\n"
                             + "}\n";

        assertEquals("\n" +
                "{\n"
                + "  \"a\" : 1,\n"
                + "  \n"
                + "  \n"
                + "  \"b\" : 2\n"
                + "}\n", parse(input));
    }
}

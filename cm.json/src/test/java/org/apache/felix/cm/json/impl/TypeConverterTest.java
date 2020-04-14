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
package org.apache.felix.cm.json.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.junit.Test;

public class TypeConverterTest {

    @Test
    public void testInvalidTypeInf() {
        assertEquals(TypeConverter.CONVERSION_FAILED, TypeConverter.convertObjectToType(null, "foo"));
    }

    @Test
    public void testConvertNullToObject() {
        assertNull(TypeConverter.convertObjectToType(null, null));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, null));

        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "String"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "int"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "Integer"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "long"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "Long"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "float"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "Float"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "double"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "Double"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "byte"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "Byte"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "short"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "Short"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "char"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "Character"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "boolean"));
        assertNull(TypeConverter.convertObjectToType(JsonValue.NULL, "Boolean"));

        assertArrayEquals(new String[0], (String[]) TypeConverter.convertObjectToType(JsonValue.NULL, "String[]"));
        assertArrayEquals(new int[0], (int[]) TypeConverter.convertObjectToType(JsonValue.NULL, "int[]"));
        assertArrayEquals(new Integer[0], (Integer[]) TypeConverter.convertObjectToType(JsonValue.NULL, "Integer[]"));
        assertArrayEquals(new long[0], (long[]) TypeConverter.convertObjectToType(JsonValue.NULL, "long[]"));
        assertArrayEquals(new Long[0], (Long[]) TypeConverter.convertObjectToType(JsonValue.NULL, "Long[]"));
        assertArrayEquals(new float[0], (float[]) TypeConverter.convertObjectToType(JsonValue.NULL, "float[]"), 0.1f);
        assertArrayEquals(new Float[0], (Float[]) TypeConverter.convertObjectToType(JsonValue.NULL, "Float[]"));
        assertArrayEquals(new double[0], (double[]) TypeConverter.convertObjectToType(JsonValue.NULL, "double[]"), 0.1);
        assertArrayEquals(new Double[0], (Double[]) TypeConverter.convertObjectToType(JsonValue.NULL, "Double[]"));
        assertArrayEquals(new byte[0], (byte[]) TypeConverter.convertObjectToType(JsonValue.NULL, "byte[]"));
        assertArrayEquals(new Byte[0], (Byte[]) TypeConverter.convertObjectToType(JsonValue.NULL, "Byte[]"));
        assertArrayEquals(new short[0], (short[]) TypeConverter.convertObjectToType(JsonValue.NULL, "short[]"));
        assertArrayEquals(new Short[0], (Short[]) TypeConverter.convertObjectToType(JsonValue.NULL, "Short[]"));
        assertArrayEquals(new char[0], (char[]) TypeConverter.convertObjectToType(JsonValue.NULL, "char[]"));
        assertArrayEquals(new Character[0],
                (Character[]) TypeConverter.convertObjectToType(JsonValue.NULL, "Character[]"));
        assertArrayEquals(new boolean[0], (boolean[]) TypeConverter.convertObjectToType(JsonValue.NULL, "boolean[]"));
        assertArrayEquals(new Boolean[0], (Boolean[]) TypeConverter.convertObjectToType(JsonValue.NULL, "Boolean[]"));

        assertTrue(((Collection<?>) TypeConverter.convertObjectToType(JsonValue.NULL, "Collection")).isEmpty());
        assertTrue(((Collection<?>) TypeConverter.convertObjectToType(JsonValue.NULL, "Collection<String>")).isEmpty());
        assertTrue(
                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.NULL, "Collection<Integer>")).isEmpty());
        assertTrue(((Collection<?>) TypeConverter.convertObjectToType(JsonValue.NULL, "Collection<Long>")).isEmpty());
        assertTrue(((Collection<?>) TypeConverter.convertObjectToType(JsonValue.NULL, "Collection<Float>")).isEmpty());
        assertTrue(((Collection<?>) TypeConverter.convertObjectToType(JsonValue.NULL, "Collection<Double>")).isEmpty());
        assertTrue(((Collection<?>) TypeConverter.convertObjectToType(JsonValue.NULL, "Collection<Byte>")).isEmpty());
        assertTrue(((Collection<?>) TypeConverter.convertObjectToType(JsonValue.NULL, "Collection<Short>")).isEmpty());
        assertTrue(
                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.NULL, "Collection<Character>")).isEmpty());
        assertTrue(
                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.NULL, "Collection<Boolean>")).isEmpty());
    }

    @Test
    public void testConvertBooleanToObjectNoTypeInfo() {
        assertEquals(Boolean.TRUE, TypeConverter.convertObjectToType(JsonValue.TRUE, null));
        assertEquals(Boolean.FALSE, TypeConverter.convertObjectToType(JsonValue.FALSE, null));
    }

    @Test
    public void testConvertNumberToObjectNoTypeInfo() {
        assertEquals(7L, TypeConverter.convertObjectToType(Json.createValue(7), null));
        assertEquals(7L, TypeConverter.convertObjectToType(Json.createValue((long) 7), null));
        assertEquals(3.1, TypeConverter.convertObjectToType(Json.createValue(3.1), null));
    }

    @Test
    public void testConvertStringToObjectNoTypeInfo() {
        assertEquals("hello world", TypeConverter.convertObjectToType(Json.createValue("hello world"), null));
    }

    @Test
    public void testConvertObjectToObjectNoTypeInfo() {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("hello", "world");

        assertEquals("{\"hello\":\"world\"}", TypeConverter.convertObjectToType(builder.build(), null));
    }

    @Test
    public void testConvertBooleanArrayToObjectNoTypeInfo() {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        builder.add(true);
        builder.add(false);

        assertArrayEquals(new Boolean[] { true, false },
                (Boolean[]) TypeConverter.convertObjectToType(builder.build(), null));
    }

    @Test
    public void testConvertNumberArrayToObjectNoTypeInfo() {
        final JsonArrayBuilder lBuilder = Json.createArrayBuilder();
        lBuilder.add(5);
        lBuilder.add(3);

        final JsonArrayBuilder dBuilder = Json.createArrayBuilder();
        dBuilder.add(5.7);
        dBuilder.add(3.7);

        final JsonArrayBuilder mBuilder = Json.createArrayBuilder();
        mBuilder.add(5.7);
        mBuilder.add(3);

        assertArrayEquals(new Long[] { 5L, 3L }, (Long[]) TypeConverter.convertObjectToType(lBuilder.build(), null));
        assertArrayEquals(new Double[] { 5.7, 3.7 },
                (Double[]) TypeConverter.convertObjectToType(dBuilder.build(), null));
        assertArrayEquals(new Double[] { 5.7, 3d },
                (Double[]) TypeConverter.convertObjectToType(mBuilder.build(), null));
    }

    @Test
    public void testConvertStringArrayToObjectNoTypeInfo() {
        final JsonArrayBuilder sBuilder = Json.createArrayBuilder();
        sBuilder.add("hello");
        sBuilder.add("world");

        assertArrayEquals(new String[] { "hello", "world" },
                (String[]) TypeConverter.convertObjectToType(sBuilder.build(), null));

        final JsonArrayBuilder mBuilder = Json.createArrayBuilder();
        mBuilder.add("hello");
        mBuilder.add("3");

        assertArrayEquals(new String[] { "hello", "3" }, (String[]) TypeConverter.convertObjectToType(mBuilder.build(), null));
    }

    @Test
    public void testConvertObjectArrayToObjectNoTypeInfo() {
        final JsonArrayBuilder sBuilder = Json.createArrayBuilder();
        final JsonObjectBuilder o1 = Json.createObjectBuilder();
        o1.add("a", "1");
        final JsonObjectBuilder o2 = Json.createObjectBuilder();
        o2.add("b", 2);
        sBuilder.add(o1);
        sBuilder.add(o2);

        assertArrayEquals(new String[] { "{\"a\":\"1\"}", "{\"b\":2}" },
                (String[]) TypeConverter.convertObjectToType(sBuilder.build(), null));
    }

    @Test
    public void testConvertStringToObjectWithType() {
        assertEquals("hello", TypeConverter.convertObjectToType(Json.createValue("hello"), "String"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "int"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Integer"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "long"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Long"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "float"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Float"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "double"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Double"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "byte"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Byte"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "short"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Short"));
        assertEquals('h', TypeConverter.convertObjectToType(Json.createValue("hello"), "char"));
        assertEquals('h', TypeConverter.convertObjectToType(Json.createValue("hello"), "Character"));
        assertEquals(false, TypeConverter.convertObjectToType(Json.createValue("hello"), "boolean"));
        assertEquals(false, TypeConverter.convertObjectToType(Json.createValue("hello"), "Boolean"));

        assertArrayEquals(new String[] { "hello" },
                (String[]) TypeConverter.convertObjectToType(Json.createValue("hello"), "String[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "int[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Integer[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "long[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Long[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "float[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Float[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "double[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Double[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "byte[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Byte[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "short[]"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Short[]"));
        assertArrayEquals(new char[] { 'h', 'e', 'l', 'l', 'o' },
                (char[]) TypeConverter.convertObjectToType(Json.createValue("hello"), "char[]"));
        assertArrayEquals(new Character[] { 'h', 'e', 'l', 'l', 'o' },
                (Character[]) TypeConverter.convertObjectToType(Json.createValue("hello"), "Character[]"));
        assertArrayEquals(new boolean[] { false },
                (boolean[]) TypeConverter.convertObjectToType(Json.createValue("hello"), "boolean[]"));
        assertArrayEquals(new Boolean[] { false },
                (Boolean[]) TypeConverter.convertObjectToType(Json.createValue("hello"), "Boolean[]"));

        assertArrayEquals(Collections.singletonList("hello").toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("hello"), "Collection")).toArray());
        assertArrayEquals(Collections.singletonList("hello").toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("hello"), "Collection<String>"))
                        .toArray());
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Collection<Integer>"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Collection<Long>"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Collection<Float>"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Collection<Double>"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Collection<Byte>"));
        assertEquals(TypeConverter.CONVERSION_FAILED,
                TypeConverter.convertObjectToType(Json.createValue("hello"), "Collection<Short>"));

        assertArrayEquals(Arrays.asList('h').toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("hello"), "Collection<Character>"))
                        .toArray());
        assertArrayEquals(Collections.singletonList(Boolean.FALSE).toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("hello"), "Collection<Boolean>"))
                        .toArray());
    }

    @Test
    public void testConvertStringWithNumberToObjectWithType() {
        assertEquals("1", TypeConverter.convertObjectToType(Json.createValue("1"), "String"));
        assertEquals(1, TypeConverter.convertObjectToType(Json.createValue("1"), "int"));
        assertEquals(1, TypeConverter.convertObjectToType(Json.createValue("1"), "Integer"));
        assertEquals(1L, TypeConverter.convertObjectToType(Json.createValue("1"), "long"));
        assertEquals(1L, TypeConverter.convertObjectToType(Json.createValue("1"), "Long"));
        assertEquals(1.0f, TypeConverter.convertObjectToType(Json.createValue("1"), "float"));
        assertEquals(1.0f, TypeConverter.convertObjectToType(Json.createValue("1"), "Float"));
        assertEquals(1.0, TypeConverter.convertObjectToType(Json.createValue("1"), "double"));
        assertEquals(1.0, TypeConverter.convertObjectToType(Json.createValue("1"), "Double"));
        assertEquals((byte) 1, TypeConverter.convertObjectToType(Json.createValue("1"), "byte"));
        assertEquals((byte) 1, TypeConverter.convertObjectToType(Json.createValue("1"), "Byte"));
        assertEquals((short) 1, TypeConverter.convertObjectToType(Json.createValue("1"), "short"));
        assertEquals((short) 1, TypeConverter.convertObjectToType(Json.createValue("1"), "Short"));
        assertEquals('1', TypeConverter.convertObjectToType(Json.createValue("1"), "char"));
        assertEquals('1', TypeConverter.convertObjectToType(Json.createValue("1"), "Character"));
        assertEquals(false, TypeConverter.convertObjectToType(Json.createValue("1"), "boolean"));
        assertEquals(false, TypeConverter.convertObjectToType(Json.createValue("1"), "Boolean"));

        assertArrayEquals(new String[] { "1" },
                (String[]) TypeConverter.convertObjectToType(Json.createValue("1"), "String[]"));
        assertArrayEquals(new int[] { 1 }, (int[]) TypeConverter.convertObjectToType(Json.createValue("1"), "int[]"));
        assertArrayEquals(new Integer[] { 1 },
                (Integer[]) TypeConverter.convertObjectToType(Json.createValue("1"), "Integer[]"));
        assertArrayEquals(new long[] { 1 },
                (long[]) TypeConverter.convertObjectToType(Json.createValue("1"), "long[]"));
        assertArrayEquals(new Long[] { 1L },
                (Long[]) TypeConverter.convertObjectToType(Json.createValue("1"), "Long[]"));
        assertArrayEquals(new float[] { 1f },
                (float[]) TypeConverter.convertObjectToType(Json.createValue("1"), "float[]"), 0.1f);
        assertArrayEquals(new Float[] { 1f },
                (Float[]) TypeConverter.convertObjectToType(Json.createValue("1"), "Float[]"));
        assertArrayEquals(new double[] { 1 },
                (double[]) TypeConverter.convertObjectToType(Json.createValue("1"), "double[]"), 0.1);
        assertArrayEquals(new Double[] { 1.0 },
                (Double[]) TypeConverter.convertObjectToType(Json.createValue("1"), "Double[]"));
        assertArrayEquals(new byte[] { 1 },
                (byte[]) TypeConverter.convertObjectToType(Json.createValue("1"), "byte[]"));
        assertArrayEquals(new Byte[] { 1 },
                (Byte[]) TypeConverter.convertObjectToType(Json.createValue("1"), "Byte[]"));
        assertArrayEquals(new short[] { 1 },
                (short[]) TypeConverter.convertObjectToType(Json.createValue("1"), "short[]"));
        assertArrayEquals(new Short[] { 1 },
                (Short[]) TypeConverter.convertObjectToType(Json.createValue("1"), "Short[]"));
        assertArrayEquals(new char[] { '1' },
                (char[]) TypeConverter.convertObjectToType(Json.createValue("1"), "char[]"));
        assertArrayEquals(new Character[] { '1' },
                (Character[]) TypeConverter.convertObjectToType(Json.createValue("1"), "Character[]"));
        assertArrayEquals(new boolean[] { false },
                (boolean[]) TypeConverter.convertObjectToType(Json.createValue("1"), "boolean[]"));
        assertArrayEquals(new Boolean[] { false },
                (Boolean[]) TypeConverter.convertObjectToType(Json.createValue("1"), "Boolean[]"));

        assertArrayEquals(Collections.singletonList("1").toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("1"), "Collection")).toArray());
        assertArrayEquals(Collections.singletonList("1").toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("1"), "Collection<String>"))
                        .toArray());
        assertArrayEquals(new Integer[] { 1 },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("1"), "Collection<Integer>"))
                        .toArray());
        assertArrayEquals(new Long[] { 1L },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("1"), "Collection<Long>"))
                        .toArray());
        assertArrayEquals(new Float[] { 1f },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("1"), "Collection<Float>"))
                        .toArray());
        assertArrayEquals(new Double[] { 1.0 },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("1"), "Collection<Double>"))
                        .toArray());
        assertArrayEquals(new Byte[] { 1 },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("1"), "Collection<Byte>"))
                        .toArray());
        assertArrayEquals(new Short[] { 1 },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("1"), "Collection<Short>"))
                        .toArray());
        assertArrayEquals(Arrays.asList('1').toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("1"), "Collection<Character>"))
                        .toArray());
        assertArrayEquals(Collections.singletonList(Boolean.FALSE).toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue("1"), "Collection<Boolean>"))
                        .toArray());
    }

    @Test
    public void testConvertNumberToObjectWithType() {
        assertEquals("1", TypeConverter.convertObjectToType(Json.createValue(1), "String"));
        assertEquals(1, TypeConverter.convertObjectToType(Json.createValue(1), "int"));
        assertEquals(1, TypeConverter.convertObjectToType(Json.createValue(1), "Integer"));
        assertEquals(1L, TypeConverter.convertObjectToType(Json.createValue(1), "long"));
        assertEquals(1L, TypeConverter.convertObjectToType(Json.createValue(1), "Long"));
        assertEquals(1.0f, TypeConverter.convertObjectToType(Json.createValue(1), "float"));
        assertEquals(1.0f, TypeConverter.convertObjectToType(Json.createValue(1), "Float"));
        assertEquals(1.0, TypeConverter.convertObjectToType(Json.createValue(1), "double"));
        assertEquals(1.0, TypeConverter.convertObjectToType(Json.createValue(1), "Double"));
        assertEquals((byte) 1, TypeConverter.convertObjectToType(Json.createValue(1), "byte"));
        assertEquals((byte) 1, TypeConverter.convertObjectToType(Json.createValue(1), "Byte"));
        assertEquals((short) 1, TypeConverter.convertObjectToType(Json.createValue(1), "short"));
        assertEquals((short) 1, TypeConverter.convertObjectToType(Json.createValue(1), "Short"));
        assertEquals((char)1, TypeConverter.convertObjectToType(Json.createValue(1), "char"));
        assertEquals(Character.valueOf((char)1), TypeConverter.convertObjectToType(Json.createValue(1), "Character"));
        assertEquals(true, TypeConverter.convertObjectToType(Json.createValue(1), "boolean"));
        assertEquals(true, TypeConverter.convertObjectToType(Json.createValue(1), "Boolean"));

        assertArrayEquals(new String[] { "1" },
                (String[]) TypeConverter.convertObjectToType(Json.createValue(1), "String[]"));
        assertArrayEquals(new int[] { 1 }, (int[]) TypeConverter.convertObjectToType(Json.createValue(1), "int[]"));
        assertArrayEquals(new Integer[] { 1 },
                (Integer[]) TypeConverter.convertObjectToType(Json.createValue(1), "Integer[]"));
        assertArrayEquals(new long[] { 1 },
                (long[]) TypeConverter.convertObjectToType(Json.createValue(1), "long[]"));
        assertArrayEquals(new Long[] { 1L },
                (Long[]) TypeConverter.convertObjectToType(Json.createValue(1), "Long[]"));
        assertArrayEquals(new float[] { 1f },
                (float[]) TypeConverter.convertObjectToType(Json.createValue(1), "float[]"), 0.1f);
        assertArrayEquals(new Float[] { 1f },
                (Float[]) TypeConverter.convertObjectToType(Json.createValue(1), "Float[]"));
        assertArrayEquals(new double[] { 1 },
                (double[]) TypeConverter.convertObjectToType(Json.createValue(1), "double[]"), 0.1);
        assertArrayEquals(new Double[] { 1.0 },
                (Double[]) TypeConverter.convertObjectToType(Json.createValue(1), "Double[]"));
        assertArrayEquals(new byte[] { 1 },
                (byte[]) TypeConverter.convertObjectToType(Json.createValue(1), "byte[]"));
        assertArrayEquals(new Byte[] { 1 },
                (Byte[]) TypeConverter.convertObjectToType(Json.createValue(1), "Byte[]"));
        assertArrayEquals(new short[] { 1 },
                (short[]) TypeConverter.convertObjectToType(Json.createValue(1), "short[]"));
        assertArrayEquals(new Short[] { 1 },
                (Short[]) TypeConverter.convertObjectToType(Json.createValue(1), "Short[]"));
        assertArrayEquals(new char[] { (char)1 },
                (char[]) TypeConverter.convertObjectToType(Json.createValue(1), "char[]"));
        assertArrayEquals(new Character[] { Character.valueOf((char)1) },
                (Character[]) TypeConverter.convertObjectToType(Json.createValue(1), "Character[]"));
        assertArrayEquals(new boolean[] { true },
                (boolean[]) TypeConverter.convertObjectToType(Json.createValue(1), "boolean[]"));
        assertArrayEquals(new Boolean[] { true },
                (Boolean[]) TypeConverter.convertObjectToType(Json.createValue(1), "Boolean[]"));

        assertArrayEquals(Collections.singletonList(1L).toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue(1), "Collection")).toArray());
        assertArrayEquals(Collections.singletonList("1").toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue(1), "Collection<String>"))
                        .toArray());
        assertArrayEquals(new Integer[] { 1 },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue(1), "Collection<Integer>"))
                        .toArray());
        assertArrayEquals(new Long[] { 1L },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue(1), "Collection<Long>"))
                        .toArray());
        assertArrayEquals(new Float[] { 1f },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue(1), "Collection<Float>"))
                        .toArray());
        assertArrayEquals(new Double[] { 1.0 },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue(1), "Collection<Double>"))
                        .toArray());
        assertArrayEquals(new Byte[] { 1 },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue(1), "Collection<Byte>"))
                        .toArray());
        assertArrayEquals(new Short[] { 1 },
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue(1), "Collection<Short>"))
                        .toArray());
        assertArrayEquals(Arrays.asList(Character.valueOf((char)1)).toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue(1), "Collection<Character>"))
                        .toArray());
        assertArrayEquals(Collections.singletonList(Boolean.TRUE).toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(Json.createValue(1), "Collection<Boolean>"))
                        .toArray());
    }

    @Test
    public void testConvertBooleanToObjectWithType() {
        assertEquals("true", TypeConverter.convertObjectToType(JsonValue.TRUE, "String"));
        assertEquals(1, TypeConverter.convertObjectToType(JsonValue.TRUE, "int"));
        assertEquals(1, TypeConverter.convertObjectToType(JsonValue.TRUE, "Integer"));
        // uncomment below tests due to bug in Apache Felix Converter - FELIX-6242
//        assertEquals(1L, TypeConverter.convertObjectToType(JsonValue.TRUE, "long"));
//        assertEquals(1L, TypeConverter.convertObjectToType(JsonValue.TRUE, "Long"));
//        assertEquals(1.0f, TypeConverter.convertObjectToType(JsonValue.TRUE, "float"));
//        assertEquals(1.0f, TypeConverter.convertObjectToType(JsonValue.TRUE, "Float"));
//        assertEquals(1.0, TypeConverter.convertObjectToType(JsonValue.TRUE, "double"));
//        assertEquals(1.0, TypeConverter.convertObjectToType(JsonValue.TRUE, "Double"));
//        assertEquals((byte) 1, TypeConverter.convertObjectToType(JsonValue.TRUE, "byte"));
//        assertEquals((byte) 1, TypeConverter.convertObjectToType(JsonValue.TRUE, "Byte"));
//        assertEquals((short) 1, TypeConverter.convertObjectToType(JsonValue.TRUE, "short"));
//        assertEquals((short) 1, TypeConverter.convertObjectToType(JsonValue.TRUE, "Short"));
        assertEquals((char)1, TypeConverter.convertObjectToType(JsonValue.TRUE, "char"));
        assertEquals(Character.valueOf((char)1), TypeConverter.convertObjectToType(JsonValue.TRUE, "Character"));
        assertEquals(true, TypeConverter.convertObjectToType(JsonValue.TRUE, "boolean"));
        assertEquals(true, TypeConverter.convertObjectToType(JsonValue.TRUE, "Boolean"));

        assertArrayEquals(new String[] { "true" },
                (String[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "String[]"));
        assertArrayEquals(new int[] { 1 }, (int[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "int[]"));
        assertArrayEquals(new Integer[] { 1 },
                (Integer[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "Integer[]"));
        assertArrayEquals(new long[] { 1 },
                (long[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "long[]"));
//        assertArrayEquals(new Long[] { 1L },
//                (Long[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "Long[]"));
//        assertArrayEquals(new float[] { 1f },
//                (float[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "float[]"), 0.1f);
//        assertArrayEquals(new Float[] { 1f },
//                (Float[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "Float[]"));
//        assertArrayEquals(new double[] { 1 },
//                (double[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "double[]"), 0.1);
//        assertArrayEquals(new Double[] { 1.0 },
//                (Double[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "Double[]"));
//        assertArrayEquals(new byte[] { 1 },
//                (byte[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "byte[]"));
//        assertArrayEquals(new Byte[] { 1 },
//                (Byte[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "Byte[]"));
//        assertArrayEquals(new short[] { 1 },
//                (short[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "short[]"));
//        assertArrayEquals(new Short[] { 1 },
//                (Short[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "Short[]"));
        assertArrayEquals(new char[] { (char)1 },
                (char[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "char[]"));
        assertArrayEquals(new Character[] { Character.valueOf((char)1) },
                (Character[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "Character[]"));
        assertArrayEquals(new boolean[] { true },
                (boolean[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "boolean[]"));
        assertArrayEquals(new Boolean[] { true },
                (Boolean[]) TypeConverter.convertObjectToType(JsonValue.TRUE, "Boolean[]"));

        assertArrayEquals(Collections.singletonList(Boolean.TRUE).toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.TRUE, "Collection")).toArray());
        assertArrayEquals(Collections.singletonList("true").toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.TRUE, "Collection<String>"))
                        .toArray());
        assertArrayEquals(new Integer[] { 1 },
                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.TRUE, "Collection<Integer>"))
                        .toArray());
//        assertArrayEquals(new Long[] { 1L },
//                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.TRUE, "Collection<Long>"))
//                        .toArray());
//        assertArrayEquals(new Float[] { 1f },
//                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.TRUE, "Collection<Float>"))
//                        .toArray());
//        assertArrayEquals(new Double[] { 1.0 },
//                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.TRUE, "Collection<Double>"))
//                        .toArray());
//        assertArrayEquals(new Byte[] { 1 },
//                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.TRUE, "Collection<Byte>"))
//                        .toArray());
//        assertArrayEquals(new Short[] { 1 },
//                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.TRUE, "Collection<Short>"))
//                        .toArray());
        assertArrayEquals(Arrays.asList(Character.valueOf((char)1)).toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.TRUE, "Collection<Character>"))
                        .toArray());
        assertArrayEquals(Collections.singletonList(Boolean.TRUE).toArray(),
                ((Collection<?>) TypeConverter.convertObjectToType(JsonValue.TRUE, "Collection<Boolean>"))
                        .toArray());
    }

    private void assertEntry(final String typeInfo, final JsonValue jsonValue,
            final Map.Entry<String, JsonValue> entry) {
        assertEquals(typeInfo, entry.getKey());
        assertNotNull(entry.getValue());
        assertEquals(jsonValue, entry.getValue());
    }

    @Test
    public void testConvertScalarsToJson() throws Exception {
         // null
        assertEntry(TypeConverter.NO_TYPE_INFO, JsonValue.NULL,
                TypeConverter.convertObjectToTypedJsonValue(null));

        // String
        assertEntry(TypeConverter.NO_TYPE_INFO, Json.createValue("hello"),
                TypeConverter.convertObjectToTypedJsonValue("hello"));

        // boolean and Boolean
        assertEntry(TypeConverter.NO_TYPE_INFO,
                JsonValue.TRUE, TypeConverter.convertObjectToTypedJsonValue(true));
        assertEntry(TypeConverter.NO_TYPE_INFO, JsonValue.FALSE, TypeConverter.convertObjectToTypedJsonValue(false));
        assertEntry(TypeConverter.NO_TYPE_INFO, JsonValue.TRUE,
                TypeConverter.convertObjectToTypedJsonValue(Boolean.TRUE));
        assertEntry(TypeConverter.NO_TYPE_INFO, JsonValue.FALSE,
                TypeConverter.convertObjectToTypedJsonValue(Boolean.FALSE));

        // double and Double
        assertEntry(TypeConverter.NO_TYPE_INFO,
                Json.createValue(1.0), TypeConverter.convertObjectToTypedJsonValue(1.0));
        assertEntry(TypeConverter.NO_TYPE_INFO, Json.createValue(3.0),
                TypeConverter.convertObjectToTypedJsonValue(Double.valueOf(3.0)));
        // long and Long
        assertEntry(TypeConverter.NO_TYPE_INFO,
                Json.createValue((long) 5), TypeConverter.convertObjectToTypedJsonValue((long) 5));
        assertEntry(TypeConverter.NO_TYPE_INFO, Json.createValue((long) 5),
                TypeConverter.convertObjectToTypedJsonValue(Long.valueOf(5)));

        // int and Integer
        assertEntry("Integer", Json.createValue(5), TypeConverter.convertObjectToTypedJsonValue(5));
        assertEntry("Integer", Json.createValue(5), TypeConverter.convertObjectToTypedJsonValue(Integer.valueOf(5)));

        // float and Float
        assertEntry("Float", Json.createValue(1.0),
                TypeConverter.convertObjectToTypedJsonValue((float) 1.0));
        assertEntry("Float", Json.createValue(3.0), TypeConverter.convertObjectToTypedJsonValue(Float.valueOf(3.0f)));

        // byte and Byte
        assertEntry("Byte", Json.createValue(5), TypeConverter.convertObjectToTypedJsonValue((byte) 5));
        assertEntry("Byte", Json.createValue(5), TypeConverter.convertObjectToTypedJsonValue(Byte.valueOf((byte) 5)));

        // short and Short
        assertEntry("Short", Json.createValue(5),
                TypeConverter.convertObjectToTypedJsonValue((short) 5));
        assertEntry("Short", Json.createValue(5), TypeConverter.convertObjectToTypedJsonValue(Short.valueOf((short) 5)));

        // char and Character
        assertEntry("Character", Json.createValue("a"),
                TypeConverter.convertObjectToTypedJsonValue('a'));
        assertEntry("Character", Json.createValue("a"),
                TypeConverter.convertObjectToTypedJsonValue(Character.valueOf('a')));
    }

    @Test
    public void testConvertScalarArraysToJson() throws Exception {
        // String[]
        assertEntry(TypeConverter.NO_TYPE_INFO, Json.createArrayBuilder().add("hello").add("you").build(),
                TypeConverter.convertObjectToTypedJsonValue(new String[] { "hello", "you" }));

        // boolean[] and Boolean[]
        assertEntry("boolean[]", Json.createArrayBuilder().add(true).add(false).build(),
                TypeConverter.convertObjectToTypedJsonValue(new boolean[] { true, false }));
        assertEntry(TypeConverter.NO_TYPE_INFO, Json.createArrayBuilder().add(true).add(false).build(),
                TypeConverter.convertObjectToTypedJsonValue(new Boolean[] { true, false }));

        // double[] and Double[]
        assertEntry("double[]", Json.createArrayBuilder().add(2.0).add(1.0).build(),
                TypeConverter.convertObjectToTypedJsonValue(new double[] { 2.0, 1.0 }));
        assertEntry(TypeConverter.NO_TYPE_INFO, Json.createArrayBuilder().add(2.0).add(1.0).build(),
                TypeConverter.convertObjectToTypedJsonValue(new Double[] { 2.0, 1.0 }));

        // long[] and Long[]
        assertEntry("long[]", Json.createArrayBuilder().add(2).add(1).build(),
                TypeConverter.convertObjectToTypedJsonValue(new long[] { 2, 1 }));
        assertEntry(TypeConverter.NO_TYPE_INFO, Json.createArrayBuilder().add(2).add(1).build(),
                TypeConverter.convertObjectToTypedJsonValue(new Long[] { (long) 2, (long) 1 }));

        // int[] and Integer[]
        assertEntry("int[]", Json.createArrayBuilder().add(2).add(1).build(),
                TypeConverter.convertObjectToTypedJsonValue(new int[] { 2, 1 }));
        assertEntry("Integer[]", Json.createArrayBuilder().add(2).add(1).build(),
                TypeConverter.convertObjectToTypedJsonValue(new Integer[] { 2, 1 }));

        // float[] and Float[]
        assertEntry("float[]", Json.createArrayBuilder().add(2.0).add(1.0).build(),
                TypeConverter.convertObjectToTypedJsonValue(new float[] { (float) 2.0, (float) 1.0 }));
        assertEntry("Float[]", Json.createArrayBuilder().add(2.0).add(1.0).build(),
                TypeConverter.convertObjectToTypedJsonValue(new Float[] { (float) 2.0, (float) 1.0 }));

        // byte[] and Byte[]
        assertEntry("byte[]", Json.createArrayBuilder().add(2).add(1).build(),
                TypeConverter.convertObjectToTypedJsonValue(new byte[] { (byte) 2, (byte) 1 }));
        assertEntry("Byte[]", Json.createArrayBuilder().add(2).add(1).build(),
                TypeConverter.convertObjectToTypedJsonValue(new Byte[] { (byte) 2, (byte) 1 }));

        // short[] and Short[]
        assertEntry("short[]", Json.createArrayBuilder().add(2).add(1).build(),
                TypeConverter.convertObjectToTypedJsonValue(new short[] { (short) 2, (short) 1 }));
        assertEntry("Short[]", Json.createArrayBuilder().add(2).add(1).build(),
                TypeConverter.convertObjectToTypedJsonValue(new Short[] { (short) 2, (short) 1 }));

        // char[] and Character[]
        assertEntry("char[]", Json.createArrayBuilder().add("a").add("b").build(),
                TypeConverter.convertObjectToTypedJsonValue(new char[] { 'a', 'b' }));
        assertEntry("Character[]", Json.createArrayBuilder().add("a").add("b").build(),
                TypeConverter.convertObjectToTypedJsonValue(new Character[] { 'a', 'b' }));
    }

}

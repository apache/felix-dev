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
package org.apache.felix.scr.impl.inject.internal;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.Mockito;
import org.osgi.framework.Bundle;

import junit.framework.TestCase;

public class AnnotationTest extends TestCase
{

    public void testMapIdentifierToKey() throws Exception
    {
        assertEquals("foo", Annotations.mapIdentifierToKey("foo"));
        assertEquals("foo", Annotations.mapIdentifierToKey("$foo"));
        assertEquals("foo", Annotations.mapIdentifierToKey("foo$"));
        assertEquals("$foo", Annotations.mapIdentifierToKey("$$foo"));
        assertEquals("foobar", Annotations.mapIdentifierToKey("foo$bar"));
        assertEquals("foo$bar", Annotations.mapIdentifierToKey("foo$$bar"));
        assertEquals("foo.", Annotations.mapIdentifierToKey("foo_"));
        assertEquals("foo_", Annotations.mapIdentifierToKey("foo__"));
        assertEquals(".foo", Annotations.mapIdentifierToKey("_foo"));
        assertEquals("_foo", Annotations.mapIdentifierToKey("__foo"));
        assertEquals("foo.bar", Annotations.mapIdentifierToKey("foo_bar"));
        assertEquals("foo_bar", Annotations.mapIdentifierToKey("foo__bar"));
        assertEquals("foo$", Annotations.mapIdentifierToKey("foo$$$"));
        assertEquals("foo_.", Annotations.mapIdentifierToKey("foo___"));
        assertEquals("foo-.bar", Annotations.mapIdentifierToKey("foo$_$_bar"));
        assertEquals("six-prop", Annotations.mapIdentifierToKey("six$_$prop"));
        assertEquals("seven$.prop", Annotations.mapIdentifierToKey("seven$$_$prop"));
    }

    public void testMapTypeNameToKey() throws Exception
    {
        assertEquals("service.ranking", Annotations.mapTypeNameToKey("ServiceRanking"));
        assertEquals("some_value", Annotations.mapTypeNameToKey("Some_Value"));
        assertEquals("osgi.property", Annotations.mapTypeNameToKey("OSGiProperty"));
    }

    public enum E1 {a, b, c}
    public @interface A1 {
        boolean bool();
        byte byt();
        char cha();
        Class<?> clas();
        E1 e1();
        double doubl();
        float floa();
        int integer();
        long lon();
        short shor();
        String string();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Bundle mockBundle() throws ClassNotFoundException
    {
        Bundle b = Mockito.mock(Bundle.class);
        Mockito.when(b.loadClass(String.class.getName())).thenReturn((Class) String.class);
        Mockito.when(b.loadClass(Integer.class.getName())).thenReturn((Class) Integer.class);
        return b;
    }

    public void testA1() throws Exception
    {
        Map<String, Object> values = allValues();

        Object o = Annotations.toObject( A1.class, values, mockBundle(), false);
        assertTrue("expected an A1", o instanceof A1);

        A1 a = (A1) o;
        checkA1(a);
    }

    private void checkA1(A1 a)
    {
        assertEquals(true, a.bool());
        assertEquals((byte)12, a.byt());
        assertEquals('c', a.cha());
        assertEquals(String.class, a.clas());
        assertEquals(E1.a, a.e1());
        assertEquals(3.14d, a.doubl());
        assertEquals(500f, a.floa());
        assertEquals(3, a.integer());
        assertEquals(12345678l,  a.lon());
        assertEquals((short)3, a.shor());
        assertEquals("3", a.string());
    }

    public void testA1FromArray() throws Exception
    {
        Map<String, Object> values = arrayValues();

        Object o = Annotations.toObject( A1.class, values, mockBundle(), false);
        assertTrue("expected an A1", o instanceof A1);

        A1 a = (A1) o;
        assertEquals(true, a.bool());
        assertEquals((byte)12, a.byt());
        assertEquals(String.class, a.clas());
        assertEquals(E1.a, a.e1());
        assertEquals(3.14d, a.doubl());
        assertEquals(500f, a.floa());
        assertEquals(3, a.integer());
        assertEquals(12345678l,  a.lon());
        assertEquals((short)3, a.shor());
        assertEquals(null, a.string());
    }

    private Map<String, Object> allValues()
    {
        Map<String, Object> values = new HashMap<>();
        values.put("bool", "true");
        values.put("byt", 12l);
        values.put("cha", 'c');
        values.put("clas", String.class.getName());
        values.put("e1", E1.a.toString());
        values.put("doubl", "3.14");
        values.put("floa", 500l);
        values.put("integer", 3.0d);
        values.put("lon", "12345678");
        values.put("shor", 3l);
        values.put("string", 3);
        values.put("array", new String[]{"foo", "bar"});
        return values;
    }

    public void testA1NoValues() throws Exception
    {
        Map<String, Object> values = new HashMap<>();

        Object o = Annotations.toObject( A1.class, values, mockBundle(), false);
        assertTrue("expected an A1", o instanceof A1);

        A1 a = (A1) o;
        assertEquals(false, a.bool());
        assertEquals((byte)0, a.byt());
        assertEquals((char)0, a.cha());
        assertEquals(null, a.clas());
        assertEquals(null, a.e1());
        assertEquals(0d, a.doubl());
        assertEquals(0f, a.floa());
        assertEquals(0, a.integer());
        assertEquals(0l,  a.lon());
        assertEquals((short)0, a.shor());
        assertEquals(null, a.string());
    }

    public @interface A2 {
        boolean bool() default true;
        byte byt() default 5;
        char cha() default 'a';
        Class<?> clas() default Integer.class;
        E1 e1() default E1.b;
        double doubl() default -2;
        float floa() default -4;
        int integer() default -5;
        long lon() default Long.MIN_VALUE;
        short shor() default -8;
        String string() default "default";
        String[] array() default {};
    }

    public void testA2AllValues() throws Exception
    {
        Map<String, Object> values = allValues();

        Object o = Annotations.toObject( A2.class, values, mockBundle(), false);
        assertTrue("expected an A2", o instanceof A2);

        A2 a = (A2) o;
        assertEquals(true, a.bool());
        assertEquals((byte)12, a.byt());
        assertEquals('c', a.cha());
        assertEquals(String.class, a.clas());
        assertEquals(E1.a, a.e1());
        assertEquals(3.14d, a.doubl());
        assertEquals(500f, a.floa());
        assertEquals(3, a.integer());
        assertEquals(12345678l,  a.lon());
        assertEquals((short)3, a.shor());
        assertEquals("3", a.string());
        assertArrayEquals(new String[]{"foo", "bar"}, a.array());
    }

    public void testA2DefaultValues() throws Exception
    {
        Map<String, Object> values = Collections.emptyMap();

        Object o = Annotations.toObject( A2.class, values, mockBundle(), false);
        assertTrue("expected an A2", o instanceof A2);

        A2 a = (A2) o;
        assertEquals(false, a.bool());
        assertEquals((byte)0, a.byt());
        assertEquals((char)0, a.cha());
        assertEquals(null, a.clas());
        assertEquals(null, a.e1());
        assertEquals(0.0, a.doubl());
        assertEquals(0.0f, a.floa());
        assertEquals(0, a.integer());
        assertEquals(0, a.lon());
        assertEquals((short)0, a.shor());
        assertEquals(null, a.string());
        assertEquals(0, a.array().length);
    }

    public @interface A1Arrays {
        boolean[] bool();
        byte[] byt();
        char[] cha();
        Class<?>[] clas();
        E1[] e1();
        double[] doubl();
        float[] floa();
        int[] integer();
        long[] lon();
        short[] shor();
        String[] string();
    }

    public void testA1ArraysNoValues() throws Exception
    {
        Map<String, Object> values = new HashMap<>();

        Object o = Annotations.toObject( A1Arrays.class, values, mockBundle(), false);
        assertTrue("expected an A1Arrays", o instanceof A1Arrays);

        A1Arrays a = (A1Arrays) o;
        assertEquals(0, a.bool().length);
        assertEquals(0, a.byt().length);
        assertEquals(0, a.cha().length);
        assertEquals(0, a.clas().length);
        assertEquals(0, a.e1().length);
        assertEquals(0, a.doubl().length);
        assertEquals(0, a.floa().length);
        assertEquals(0, a.integer().length);
        assertEquals(0,  a.lon().length);
        assertEquals(0, a.shor().length);
        assertEquals(0, a.string().length);
    }

    public void testA1Array() throws Exception
    {
        Map<String, Object> values = allValues();

        Object o = Annotations.toObject( A1Arrays.class, values, mockBundle(), false);
        assertTrue("expected an A1Arrays", o instanceof A1Arrays);

        A1Arrays a = (A1Arrays) o;
        assertArrayEquals(new boolean[] {true}, a.bool());
        assertArrayEquals(new byte[] {(byte)12}, a.byt());
        assertArrayEquals(new char[] {'c'}, a.cha());
        assertArrayEquals(new Class<?>[] {String.class}, a.clas());
        assertArrayEquals(new E1[] {E1.a}, a.e1());
        assertArrayEquals(new double[] {3.14d}, a.doubl());
        assertArrayEquals(new float[] {500f}, a.floa());
        assertArrayEquals(new int[] {3}, a.integer());
        assertArrayEquals(new long[] {12345678l},  a.lon());
        assertArrayEquals(new short[] {(short)3}, a.shor());
        assertArrayEquals(new String[] {"3"}, a.string());
    }

    private void assertArrayEquals(Object a, Object b)
    {
        assertTrue(a.getClass().isArray());
        assertTrue(b.getClass().isArray());
        assertEquals("wrong length", Array.getLength(a), Array.getLength(b));
        assertEquals("wrong type", a.getClass().getComponentType(), b.getClass().getComponentType());
        for (int i = 0; i < Array.getLength(a); i++)
        {
            assertEquals("different value at " + i, Array.get(a, i), Array.get(b, i));
        }

    }

    private Map<String, Object> arrayValues()
    {
        Map<String, Object> values = new HashMap<>();
        values.put("bool", new boolean[] {true, false});
        values.put("byt", new byte[] {12, 3});
        values.put("cha", new char[] {'c', 'h', 'a', 'r'});
        values.put("clas", new String[] {String.class.getName(), Integer.class.getName()});
        values.put("e1", new String[] {E1.a.name(), E1.b.name()});
        values.put("doubl", new double[] {3.14, 2.78, 9});
        values.put("floa", new float[] {500, 37.44f});
        values.put("integer", new int[] {3, 6, 9});
        values.put("lon", new long[] {12345678l, -1});
        values.put("shor", new short[] {3, 88});
        values.put("string", new String[] {});
        return values;
    }

    public void testA1ArrayFromArray() throws Exception
    {
        Map<String, Object> values = arrayValues();

        doA1ArrayTest(values);
    }

    public void testA1ArrayFromCollection() throws Exception
    {
        Map<String, Object> values = arrayValues();
        Map<String, Object> collectionValues = new HashMap<>();
        for (Map.Entry<String, Object> entry: values.entrySet())
        {
            collectionValues.put(entry.getKey(), toList(entry.getValue()));
        }

        doA1ArrayTest(collectionValues);
    }

    private List<?> toList(Object value)
    {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < Array.getLength(value); i++)
        {
            result.add(Array.get(value, i));
        }
        return result;
    }

    private void doA1ArrayTest(Map<String, Object> values) throws ClassNotFoundException
    {
        Object o = Annotations.toObject( A1Arrays.class, values, mockBundle(), false);
        assertTrue("expected an A1Arrays", o instanceof A1Arrays);

        A1Arrays a = (A1Arrays) o;
        assertArrayEquals(new boolean[] {true, false}, a.bool());
        assertArrayEquals(new byte[] {12, 3}, a.byt());
        assertArrayEquals(new char[] {'c', 'h', 'a', 'r'}, a.cha());
        assertArrayEquals(new Class<?>[] {String.class, Integer.class}, a.clas());
        assertArrayEquals(new E1[] {E1.a, E1.b}, a.e1());
        assertArrayEquals(new double[] {3.14, 2.78, 9}, a.doubl());
        assertArrayEquals(new float[] {500f, 37.44f}, a.floa());
        assertArrayEquals(new int[] {3, 6, 9}, a.integer());
        assertArrayEquals(new long[] {12345678l, -1},  a.lon());
        assertArrayEquals(new short[] {(short)3, 88}, a.shor());
        assertArrayEquals(new String[] {}, a.string());
    }

    public @interface B1 {
        boolean bool();
        byte byt();
        Class<?> clas();
        E1 e1();
        double doubl();
        float floa();
        int integer();
        long lon();
        short shor();
        String string();
        A1 a1();
        A1[] a1array();
    }

    public void testB1() throws Exception
    {
        Map<String, Object> values = b1Values();

        Object o = Annotations.toObject( B1.class, values, mockBundle(), true);
        assertTrue("expected an B1 " + o, o instanceof B1);
        B1 b = (B1) o;
        checkB1(b);
    }

    private void checkB1(B1 b)
    {
        checkA1(b.a1());
        assertEquals(3, b.a1array().length);
        checkA1(b.a1array()[0]);
        checkA1(b.a1array()[1]);
        checkA1(b.a1array()[2]);
    }

    private Map<String, Object> b1Values()
    {
        Map<String, Object> a1Values = allValues();
        Map<String, Object> values = allValues();
        nest(values, "a1", 0, a1Values);
        nest(values, "a1array", 0, a1Values);
        nest(values, "a1array", 1, a1Values);
        nest(values, "a1array", 2, a1Values);
        return values;
    }

    private void nest(Map<String, Object> values, String key, int i,
        Map<String, Object> a1Values)
    {
        for (Map.Entry<String, Object> entry: a1Values.entrySet())
        {
            values.put(key + "." + i + "." + entry.getKey(), entry.getValue());
        }
    }

    public @interface C1 {
        boolean bool();
        byte byt();
        Class<?> clas();
        E1 e1();
        double doubl();
        float floa();
        int integer();
        long lon();
        short shor();
        String string();
        B1 b1();
        B1[] b1array();
    }

    public void testC1() throws Exception
    {
        Map<String, Object> values = c1Values();

        Object o = Annotations.toObject( C1.class, values, mockBundle(), true);
        assertTrue("expected an B1 " + o, o instanceof C1);
        C1 c = (C1) o;
        checkB1(c.b1());
        assertEquals(3, c.b1array().length);
        checkB1(c.b1array()[0]);
        checkB1(c.b1array()[1]);
        checkB1(c.b1array()[2]);

    }

    private Map<String, Object> c1Values()
    {
        Map<String, Object> b1Values = b1Values();
        Map<String, Object> values = allValues();
        nest(values, "b1", 0, b1Values);
        nest(values, "b1array", 0, b1Values);
        nest(values, "b1array", 1, b1Values);
        nest(values, "b1array", 2, b1Values);
        return values;
    }

    public interface I0 {
        boolean bool();
        byte byt();
        Class<?> clas();
        E1 e1();
    }
    public interface AI1 extends I0 {
        double doubl();
        float floa();
        int integer();
        long lon();
        short shor();
        String string();
    }

    public interface BI1 extends I0 {
        double doubl();
        float floa();
        int integer();
        long lon();
        short shor();
        String string();
        AI1 a1();
        AI1[] a1array();
    }

    public interface CI1 extends I0 {
        double doubl();
        float floa();
        int integer();
        long lon();
        short shor();
        String string();
        BI1 b1();
        BI1[] b1array();
    }

    public void testAI1() throws Exception
    {
        Map<String, Object> values = allValues();

        Object o = Annotations.toObject( AI1.class, values, mockBundle(), true);
        assertTrue("expected an AI1", o instanceof AI1);

        AI1 a = (AI1) o;
        checkAI1(a);
    }

    private void checkAI1(AI1 a)
    {
        assertEquals(true, a.bool());
        assertEquals((byte)12, a.byt());
        assertEquals(String.class, a.clas());
        assertEquals(E1.a, a.e1());
        assertEquals(3.14d, a.doubl());
        assertEquals(500f, a.floa());
        assertEquals(3, a.integer());
        assertEquals(12345678l,  a.lon());
        assertEquals((short)3, a.shor());
        assertEquals("3", a.string());
    }

    public void testBI1() throws Exception
    {
        Map<String, Object> values = b1Values();

        Object o = Annotations.toObject( BI1.class, values, mockBundle(), true);
        assertTrue("expected an B1 " + o, o instanceof BI1);
        BI1 b = (BI1) o;
        checkBI1(b);
    }

    private void checkBI1(BI1 b)
    {
        checkAI1(b.a1());
        assertEquals(3, b.a1array().length);
        checkAI1(b.a1array()[0]);
        checkAI1(b.a1array()[1]);
        checkAI1(b.a1array()[2]);
    }

    public void testCI1() throws Exception
    {
        Map<String, Object> values = c1Values();

        Object o = Annotations.toObject( CI1.class, values, mockBundle(), true);
        assertTrue("expected an B1 " + o, o instanceof CI1);
        CI1 c = (CI1) o;
        checkBI1(c.b1());
        assertEquals(3, c.b1array().length);
        checkBI1(c.b1array()[0]);
        checkBI1(c.b1array()[1]);
        checkBI1(c.b1array()[2]);

    }

    class Odd
    {
        private final String content;

        public Odd(String content)
        {
            this.content = content;
        }

        public String getContent()
        {
            return content;
        }
    }

    class Odder extends Odd
    {

        public Odder(String content)
        {
            super(content);
        }

    }

    interface OddTest
    {
        Odd odd1();

        Odd odd2();

        Odd odd3();

        Odd odder1();

        Odd odder2();

        Odd odder3();
    }

    public void testOddClasses() throws Exception
    {
        Map<String, Object> values = new HashMap<>();
        values.put("odd1", new Odd("one"));
        values.put("odd2", Collections.singletonList(new Odd("two")));
        values.put("odd3", new Odd[] {new Odd("three"), new Odd("four")});
        values.put("odder1", new Odder("one"));
        values.put("odder2", Collections.singletonList(new Odder("two")));
        values.put("odder3", new Odder[] {new Odder("three"), new Odder("four")});

        Object o = Annotations.toObject(OddTest.class, values, mockBundle(), true);
        assertTrue("expected an OddTest", o instanceof OddTest);
        OddTest ot = (OddTest)o;
        assertOdd("one", ot.odd1());
        assertOdd("two", ot.odd2());
        assertOdd("three", ot.odd3());
        assertOdder("one", ot.odder1());
        assertOdder("two", ot.odder2());
        assertOdder("three", ot.odder3());
    }

    public @interface SETest1 {
        String value();
    }

    public @interface SETest2 {
        boolean foo() default true;
        String value();
    }

    public @interface SETest3 {
        boolean foo();
        String value();
    }

    public @interface SETest4 {
        boolean foo();
        String values();
    }

    public @interface PrefixTest {
        String PREFIX_ = "org.apache.";

        boolean foo() default true;
        String[] values() default {"a"};
        String value();
    }

    public @interface SingleElementTest {
        String PREFIX_ = "org.apache.";

        String value();
    }

    public void testSingleElementAnnotation() throws Exception
    {
        assertFalse(Annotations.isSingleElementAnnotation(Map.class));
        assertTrue(Annotations.isSingleElementAnnotation(SETest1.class));
        assertTrue(Annotations.isSingleElementAnnotation(SETest2.class));
        assertFalse(Annotations.isSingleElementAnnotation(SETest3.class));
        assertFalse(Annotations.isSingleElementAnnotation(SETest4.class));
        assertTrue(Annotations.isSingleElementAnnotation(SingleElementTest.class));
    }

    public void testGetPrefix() throws Exception
    {
        assertNull(Annotations.getPrefix(Map.class));
        assertNull(Annotations.getPrefix(SETest1.class));
        assertNull(Annotations.getPrefix(SETest2.class));
        assertNull(Annotations.getPrefix(SETest3.class));
        assertNull(Annotations.getPrefix(SETest4.class));
        assertNull(Annotations.getPrefix(A1.class));
        assertEquals("org.apache.", Annotations.getPrefix(PrefixTest.class));
    }

    public void testMappingWithPrefix() throws Exception
    {
        final Map<String, Object> values = new HashMap<>();
        values.put("foo", false);
        values.put("org.apache.foo", true);
        values.put("values", "false-values");
        values.put("org.apache.values", "true-values");
        values.put("value", "false-value");
        values.put("org.apache.prefix.test", "true-value");

        final PrefixTest o = Annotations.toObject( PrefixTest.class, values, mockBundle(), true);
        assertEquals("true-value", o.value());
        assertEquals(1, o.values().length);
        assertEquals("true-values", o.values()[0]);
        assertEquals(true, o.foo());
    }

    public void testMappingWithPrefixSingleValue() throws Exception
    {
        final Map<String, Object> values = new HashMap<>();
        values.put("value", "false-value");
        values.put("org.apache.single.element.test", "true-value");

        final SingleElementTest o = Annotations.toObject( SingleElementTest.class, values, mockBundle(), true);
        assertEquals("true-value", o.value());
    }

    private void assertOdd(String expectedContent, Object actual) {
        assertTrue("expected an Odd", actual instanceof Odd);
        assertEquals("Expected Odd contents", expectedContent, ((Odd)actual).getContent());
    }
    private void assertOdder(String expectedContent, Object actual) {
        assertTrue("expected an Odder", actual instanceof Odder);
        assertEquals("Expected Odd contents", expectedContent, ((Odder)actual).getContent());
    }
}

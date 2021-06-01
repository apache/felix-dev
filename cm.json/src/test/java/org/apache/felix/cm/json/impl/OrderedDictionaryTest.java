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
package org.apache.felix.cm.json.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

public class OrderedDictionaryTest {

    @Test public void testKeys() {
        final Map<String, Object> map = new OrderedDictionary();
        assertNull(map.put("hello", "helloV"));
        assertNull(map.put("world", "worldV"));

        assertEquals(2, map.size());
        assertEquals(2, map.values().size());
        assertTrue(map.values().contains("helloV"));
        assertTrue(map.values().contains("worldV"));

        assertEquals(2, map.entrySet().size());
        final Map<String, Object> m = map.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        assertEquals("helloV", m.get("hello"));
        assertEquals("worldV", m.get("world"));

        Set<String> keys = new HashSet<>(map.keySet());
        assertEquals(2, keys.size());
        assertTrue(keys.contains("hello"));
        assertTrue(keys.contains("world"));

        assertEquals("helloV", map.get("hello"));
        assertEquals("worldV", map.get("world"));
        assertNull(map.get("foo"));

        assertEquals("helloV", map.get("HELLO"));
        assertEquals("worldV", map.get("WORLD"));

        assertEquals("helloV", map.put("heLLo", "bar"));
        assertEquals(2, map.size());

        keys = new HashSet<>(map.keySet());
        assertEquals(2, keys.size());
        assertTrue(keys.contains("heLLo"));
        assertTrue(keys.contains("world"));

        assertEquals("bar", map.get("hello"));
        assertEquals("worldV", map.get("world"));

        assertEquals("bar", map.remove("HellO"));
        assertEquals("worldV", map.remove("WoRlD"));
        assertTrue(map.isEmpty());
    }

    @Test public void testClear() {
        final Map<String, Object> map = new OrderedDictionary();
        map.put("hello", "hello");
        map.put("world", "world");

        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertTrue(map.values().isEmpty());
        assertTrue(map.keySet().isEmpty());
        assertTrue(map.entrySet().isEmpty());
    }

    @Test public void testContains() {
        final Map<String, Object> map = new OrderedDictionary();
        map.put("hello", "helloV");

        assertTrue(map.containsKey("hello"));
        assertTrue(map.containsKey("heLLo"));
        assertFalse(map.containsKey("hell o"));

        assertTrue(map.containsValue("helloV"));
        assertFalse(map.containsValue("heLLoV"));
        assertFalse(map.containsValue("hello"));
    }

    @Test public void testSerialization() throws IOException, ClassNotFoundException {
        final Map<String, Object> map = new OrderedDictionary();
        map.put("hello", "helloV");

        // write to file
        final File f = Files.createTempFile("dictionary", "ser").toFile();
        try {

            try (final ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f)) ) {
                oos.writeObject(map);
            }

            // read from file
            try(final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> readMap = (Map<String, Object>) ois.readObject();
                assertEquals(1, readMap.size());
                assertEquals("helloV", readMap.get("hello"));
            }
        } finally {
            if ( f.exists() ) {
                f.delete();
            }
        }
    }
}
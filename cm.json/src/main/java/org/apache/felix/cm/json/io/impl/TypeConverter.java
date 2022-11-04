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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonValue;

import org.osgi.util.converter.ConversionException;
import org.osgi.util.converter.Converters;
import org.osgi.util.converter.TypeReference;

public class TypeConverter {

    public static final String TYPE_BINARY = "binary";

    public static final String TYPE_BINARIES = "binary[]";

    private static final String TYPE_COLLECTION = "Collection";

    public static final String NO_TYPE_INFO = "";

    public static final Object CONVERSION_FAILED = new Object();

    private static final Map<String, Class<?>> TYPE_MAP = new LinkedHashMap<>();
    private static final Map<String, TypeReference<?>> TYPE_COLLECTION_MAP = new LinkedHashMap<>();
    static {
        // scalar types and primitive types
        TYPE_MAP.put("String", String.class);
        TYPE_MAP.put("Integer", Integer.class);
        TYPE_MAP.put("int", Integer.class);
        TYPE_MAP.put("Long", Long.class);
        TYPE_MAP.put("long", Long.class);
        TYPE_MAP.put("Float", Float.class);
        TYPE_MAP.put("float", Float.class);
        TYPE_MAP.put("Double", Double.class);
        TYPE_MAP.put("double", Double.class);
        TYPE_MAP.put("Byte", Byte.class);
        TYPE_MAP.put("byte", Byte.class);
        TYPE_MAP.put("Short", Short.class);
        TYPE_MAP.put("short", Short.class);
        TYPE_MAP.put("Character", Character.class);
        TYPE_MAP.put("char", Character.class);
        TYPE_MAP.put("Boolean", Boolean.class);
        TYPE_MAP.put("boolean", Boolean.class);
         // array of scalar types and primitive types
        TYPE_MAP.put("String[]", String[].class);
        TYPE_MAP.put("int[]", int[].class);
        TYPE_MAP.put("Integer[]", Integer[].class);
        TYPE_MAP.put("long[]", long[].class);
        TYPE_MAP.put("Long[]", Long[].class);
        TYPE_MAP.put("float[]", float[].class);
        TYPE_MAP.put("Float[]", Float[].class);
        TYPE_MAP.put("double[]", double[].class);
        TYPE_MAP.put("Double[]", Double[].class);
        TYPE_MAP.put("byte[]", byte[].class);
        TYPE_MAP.put("Byte[]", Byte[].class);
        TYPE_MAP.put("short[]", short[].class);
        TYPE_MAP.put("Short[]", Short[].class);
        TYPE_MAP.put("boolean[]", boolean[].class);
        TYPE_MAP.put("Boolean[]", Boolean[].class);
        TYPE_MAP.put("char[]", char[].class);
        TYPE_MAP.put("Character[]", Character[].class);

        // binaries
        TYPE_MAP.put(TYPE_BINARY, String.class);
        TYPE_MAP.put(TYPE_BINARIES, String[].class);

        // Collections of scalar types
        TYPE_COLLECTION_MAP.put("Collection<String>", new TypeReference<ArrayList<String>>() {
        });
        TYPE_COLLECTION_MAP.put("Collection<Integer>", new TypeReference<ArrayList<Integer>>() {
        });
        TYPE_COLLECTION_MAP.put("Collection<Long>", new TypeReference<ArrayList<Long>>() {
        });
        TYPE_COLLECTION_MAP.put("Collection<Float>", new TypeReference<ArrayList<Float>>() {
        });
        TYPE_COLLECTION_MAP.put("Collection<Double>", new TypeReference<ArrayList<Double>>() {
        });
        TYPE_COLLECTION_MAP.put("Collection<Byte>", new TypeReference<ArrayList<Byte>>() {
        });
        TYPE_COLLECTION_MAP.put("Collection<Short>", new TypeReference<ArrayList<Short>>() {
        });
        TYPE_COLLECTION_MAP.put("Collection<Character>", new TypeReference<ArrayList<Character>>() {
        });
        TYPE_COLLECTION_MAP.put("Collection<Boolean>", new TypeReference<ArrayList<Boolean>>() {
        });
    }

    /**
     * Convert a value to the given type
     *
     * @param value    The json value
     * @param typeInfo Optional type info
     * @return The converted value or {@code CONVERSION_FAILED} if the conversion failed.
     * @throws IllegalArgumentException If the type is unknown
     */
    public static Object convertObjectToType(
            final JsonValue jsonValue,
            final String typeInfo) throws IllegalArgumentException {
        final Object value = JsonSupport.convertToObject(jsonValue);
        if (typeInfo == null) {
            return value;
        }
        final Class<?> typeClass = TYPE_MAP.get(typeInfo);
        if ( typeClass != null ) {
            Object result = null;
            try {
                result = Converters.standardConverter()
                    .convert(value).defaultValue(null).to(typeClass);
            } catch ( final ConversionException ce) {
                // ignore
            }
            if (result == null && value != null) {
                result = CONVERSION_FAILED;
            }
            return result;
        }
        final TypeReference<?> typeReference = TYPE_COLLECTION_MAP.get(typeInfo);
        if (typeReference != null) {
            if (value == null) {
                return Collections.EMPTY_LIST;
            }
            Object result = null;
            try {
                result = Converters.standardConverter()
                    .convert(value).defaultValue(null).to(typeReference);
            } catch ( final ConversionException ce) {
                // ignore
            }
            if (result == null) {
                result = CONVERSION_FAILED;
            }
            return result;
        }

        // raw collection
        if (TYPE_COLLECTION.equals(typeInfo)) {
            if (value == null) {
                return Collections.EMPTY_LIST;
            } else if (value instanceof String || value instanceof Boolean || value instanceof Long
                    || value instanceof Double) {
                return Collections.singletonList(value);
            }
            final Collection<Object> c = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                c.add(Array.get(value, i));
            }
            return Collections.unmodifiableCollection(c);
        }

        // unknown type
        return CONVERSION_FAILED;
    }

    private static String findType(final Class<?> clazz) {
        for (final Map.Entry<String, Class<?>> entry : TYPE_MAP.entrySet()) {
            if (clazz.isAssignableFrom(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Convert an object to a {@code JsonValue}.
     *
     * @param value The object to convert
     * @return A map entry where the key contains the type info and the value the
     *         converted JsonValue.
     */
    public static Map.Entry<String, JsonValue> convertObjectToTypedJsonValue(final Object value) {
        // native types
        if (value == null || value instanceof Long || value instanceof Double || value instanceof String
                || value instanceof Boolean) {
            return new Entry(NO_TYPE_INFO, JsonSupport.convertToJson(value));
        }

        if (value.getClass().isArray()) {
            // arrays
            String typeInfo = findType(value.getClass());
            if (typeInfo != null) {
                if ("String[]".equals(typeInfo) || "Boolean[]".equals(typeInfo) || "Long[]".equals(typeInfo)
                        || "Double[]".equals(typeInfo)) {
                    typeInfo = NO_TYPE_INFO;
                }
                final JsonArrayBuilder jab = Json.createArrayBuilder();
                for (int i = 0; i < Array.getLength(value); i++) {
                    jab.add(convertObjectToTypedJsonValue(Array.get(value, i)).getValue());
                }

                return new Entry(typeInfo, jab.build());
            }

        } else if (Collection.class.isAssignableFrom(value.getClass())) {
            // collections
            final Collection<?> collection = (Collection<?>) value;
            // get first object to get the type
            String typeInfo = TypeConverter.TYPE_COLLECTION;
            final Iterator<?> i = collection.iterator();
            if (i.hasNext()) {
                final String colType = findType(i.next().getClass());
                if (colType != null) {
                    typeInfo = typeInfo.concat("<").concat(colType).concat(">");
                }
            }
            final JsonArrayBuilder jab = Json.createArrayBuilder();
            for (final Object obj : collection) {
                jab.add(JsonSupport.convertToJson(obj));
            }
            return new Entry(typeInfo, jab.build());
        }

        // scalar types - start with special cases for numbers
        if (value instanceof Integer) {
            return new Entry("Integer", Json.createValue((Integer) value));
        } else if (value instanceof Float) {
            return new Entry("Float", Json.createValue((Float) value));
        } else if (value instanceof Short) {
            return new Entry("Short", Json.createValue((Short) value));
        } else if (value instanceof Byte) {
            return new Entry("Byte", Json.createValue((Byte) value));
        } else if (value instanceof Character) {
            return new Entry("Character", Json.createValue(value.toString()));
        }
        final String typeInfo = findType(value.getClass());
        if (typeInfo != null) {
            return new Entry(typeInfo, JsonSupport.convertToJson(value.toString()));
        }

        return new Entry(NO_TYPE_INFO, JsonSupport.convertToJson(value));
    }

    private static final class Entry implements Map.Entry<String, JsonValue> {

        private final JsonValue value;
        private final String type;

        public Entry(final String type, final JsonValue value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String getKey() {
            return this.type;
        }

        @Override
        public JsonValue getValue() {
            return this.value;
        }

        @Override
        public JsonValue setValue(JsonValue value) {
            return null;
        }
    }
}

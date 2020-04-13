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

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.felix.cm.json.Configurations;

public class JsonSupport {

    /**
     * Convert a json value into an object
     *
     * @param value The json value
     * @return The object or {@code null} if the provided value is {@code null} or
     *         {@code ValueType#NULL}
     */
    public static Object convertToObject(final JsonValue value) {
        if (value == null) {
            return null;
        }
        switch (value.getValueType()) {
        // type NULL -> return null
        case NULL:
            return null;
        // type TRUE or FALSE -> return boolean
        case FALSE:
            return false;
        case TRUE:
            return true;
        // type String -> return String
        case STRING:
            return ((JsonString) value).getString();
        // type Number -> return long or double
        case NUMBER:
            final JsonNumber num = (JsonNumber) value;
            if (num.isIntegral()) {
                return num.longValue();
            }
            return num.doubleValue();
        // type ARRAY -> return list and call this method for each value
        case ARRAY:
            final JsonArray array = value.asJsonArray();
            final ValueType arrayType = detectArrayType(array);
            final Object[] objArray;
            if (arrayType == ValueType.FALSE || arrayType == ValueType.TRUE) {
                objArray = new Boolean[array.size()];
            } else if (arrayType == ValueType.NUMBER) {
                final boolean isLong = ((JsonNumber) array.get(0)).isIntegral();
                objArray = isLong ? new Long[array.size()] : new Double[array.size()];
            } else if (arrayType == ValueType.STRING) {
                objArray = new String[array.size()];
            } else {
                objArray = null;
            }
            if (objArray != null) {
                for (int i = 0; i < array.size(); i++) {
                    objArray[i] = convertToObject(array.get(i));
                }
                return objArray;
            }
            return array.toString();

        // type OBJECT -> return map
        default:
            return value.asJsonObject().toString();
        }
    }

    /**
     * Detect the value type of a json array. If all elements in the array have the
     * same type, this type is returned. Otherwise {@code ValueType#OBJECT} is
     * returned. For an empty array {@code ValueType#STRING} is returned.
     *
     * @param array The array
     * @return The detected type
     */
    private static ValueType detectArrayType(final JsonArray array) {
        if (array.size() == 0) {
            return ValueType.STRING;
        }
        final ValueType vt = array.get(0).getValueType();
        final boolean isLong = vt == ValueType.NUMBER && ((JsonNumber) array.get(0)).isIntegral();
        for (int i = 1; i < array.size(); i++) {
            final ValueType ct = array.get(i).getValueType();
            boolean isSame = false;
            if (ct == vt) {
                isSame = true;
                if (vt == ValueType.NUMBER) {
                    if (isLong != ((JsonNumber) array.get(i)).isIntegral()) {
                        isSame = false;
                    }
                }
            } else if (vt == ValueType.TRUE && ct == ValueType.FALSE) {
                isSame = true;
            } else if (vt == ValueType.FALSE && ct == ValueType.TRUE) {
                isSame = true;
            }
            if (!isSame) {
                return ValueType.OBJECT;
            }
        }
        return vt;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static JsonValue convertToJson(final Object object) {
        if (object == null) {
            return JsonValue.NULL;
        } else if (object instanceof String) {
            return Json.createValue((String) object);
        } else if (object instanceof Long) {
            return Json.createValue((Long) object);
        } else if (object instanceof Integer) {
            return Json.createValue((Integer) object);
        } else if (object instanceof Short) {
            return Json.createValue((Short) object);
        } else if (object instanceof Byte) {
            return Json.createValue((Byte) object);
        } else if (object instanceof Float) {
            return Json.createValue((Float) object);
        } else if (object instanceof Double) {
            return Json.createValue((Double) object);
        } else if (object instanceof Boolean) {
            final Boolean value = (Boolean) object;
            return value ? JsonValue.TRUE : JsonValue.FALSE;
        } else if (object instanceof Map) {
            boolean valid = true;
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            for (final Map.Entry<Object, Object> entry : ((Map<Object, Object>) object).entrySet()) {
                final JsonValue value = convertToJson(entry.getValue());
                if (entry.getKey() instanceof String) {
                    builder.add(entry.getKey().toString(), value);
                } else {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return builder.build();
            }
        } else if (object instanceof Collection) {
            final JsonArrayBuilder builder = Json.createArrayBuilder();
            for (final Object obj : (Collection) object) {
                builder.add(convertToJson(obj));
            }
            return builder.build();
        } else if (object.getClass().isArray()) {
            final JsonArrayBuilder builder = Json.createArrayBuilder();
            for (int i = 0; i < Array.getLength(object); i++) {
                builder.add(convertToJson(Array.get(object, i)));
            }
            return builder.build();
        }
        return Json.createValue(object.toString());
    }

    /**
     * Parse a JSON content
     *
     * @param identifier     The identifier of the JSON contents (optional)
     *
     * @param contentsReader The reader for the contents
     *
     * @return The parsed JSON object. throws IOException on failure parsing
     */
    public static JsonObject parseJson(final String identifier, final Reader contentsReader) throws IOException {
        try (final JsonReader reader = Json.createReader(Configurations.jsonCommentAwareReader(new FilterReader(contentsReader) {

            @Override
            public void close() throws IOException {
                // do not close reader
            }
        }))) {
            final JsonStructure obj = reader.read();
            if (obj != null && obj.getValueType() == ValueType.OBJECT) {
                return (JsonObject) obj;
            }
            final String msg = "Invalid JSON, no root object";
            if (identifier != null) {
                throw new IOException(identifier.concat(" : ").concat(msg));
            }
            throw new IOException(msg);
        }
    }

    /**
     * Create a reader which removes comments from the input
     * @param reader The input reader
     * @return The output reader
     * @throws IOException If something fails
     */
    public static Reader createCommentRemovingReader(final Reader reader) throws IOException {
        final String contents;
        try (final StringWriter writer = new StringWriter() ){
            final char[] buf = new char[2048];
            int l;
            while ( (l = reader.read(buf)) > 0 ) {
                writer.write(buf, 0, l);
            }
            writer.flush();
            contents = writer.toString();
        }
        final StringReader stringReader = new StringReader(removeComments(contents));
        return new FilterReader(stringReader) {

            boolean closed = false;
            @Override
            public void close() throws IOException {
                if (!closed) {
                    closed = true;
                    reader.close();
                    super.close();
                }
            }
        };
    }

    /**
     * Helper method to remove comments from JSON
     * @param comments The JSON with comments
     * @return The JSON without comments
     */
    private static String removeComments(final String comments) {
        final StringBuilder sb = new StringBuilder(comments);
        int index = 0;
        boolean insideQuote = false;
        while ( index < sb.length()) {
            switch ( sb.charAt(index) ) {
            case '"' : if ( index == 0 || sb.charAt(index - 1) != '\\') {
                           insideQuote = !insideQuote;
                       }
                       index++;
                       break;
            case '/' : if ( !insideQuote && index + 1 < sb.length()) {
                           if ( sb.charAt(index + 1) == '/') {
                               // line comment
                               int m = index + 2;
                               while ( m < sb.length() && sb.charAt(m) != '\n' ) {
                                   m++;
                               }
                               sb.delete(index, m);
                           } else if ( sb.charAt(index + 1 ) == '*') {
                               // block comment
                               int m = index + 2;
                               int newlines = 0;
                               while ( m < sb.length() && (sb.charAt(m) != '/' || sb.charAt(m - 1) != '*')) {
                                   if ( sb.charAt(m) == '\n') {
                                       newlines++;
                                   }
                                   m++;
                               }
                               if ( m == sb.length() ) {
                                   index = m; // invalid - just go to the end
                               } else {
                                   sb.delete(index,  m+1);
                                   for(int x = 0; x<newlines; x++) {
                                       sb.insert(index, '\n');
                                   }
                               }
                           }
                       }
                       index++;
                       break;
            default: index++;
            }
        }
        return sb.toString();
    }
}

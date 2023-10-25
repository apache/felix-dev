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

import java.io.FilterReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

import org.apache.felix.cm.json.io.Configurations;
import org.osgi.util.converter.Converters;

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
                objArray = new Object[array.size()];
            } else if (arrayType == ValueType.STRING || arrayType == ValueType.OBJECT) {
                objArray = new String[array.size()];
            } else {
            	objArray = null;
            }
            if (objArray != null) {
                boolean isLong = true;
                for (int i = 0; i < array.size(); i++) {
                    objArray[i] = convertToObject(array.get(i));
                    if ( arrayType == ValueType.NUMBER && !(objArray[i] instanceof Long) ) {
                        isLong = false;
                    }
                }
                if ( arrayType == ValueType.NUMBER ) {
                    if ( isLong ) {
                        return Converters.standardConverter().convert(objArray).to(Long[].class);
                    } else {
                        return Converters.standardConverter().convert(objArray).to(Double[].class);
                    }
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
     * same type, this type is returned. Otherwise {@code null} is
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
        for (int i = 1; i < array.size(); i++) {
            final ValueType ct = array.get(i).getValueType();
            boolean isSame = false;
            if (ct == vt) {
                isSame = true;
            } else if (vt == ValueType.TRUE && ct == ValueType.FALSE) {
                isSame = true;
            } else if (vt == ValueType.FALSE && ct == ValueType.TRUE) {
                isSame = true;
            }
            if (!isSame) {
                return null;
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
        return new CommentRemovingReader(reader);
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

     /**
     * Helper class to create a BufferedReader that implicitly removes inline and blockcomments from the input
     */
    private static class CommentRemovingReader extends FilterReader {

        private boolean closed = false;
            
        public CommentRemovingReader(Reader reader) {
            super(new BufferedReader(reader));
        }

        @Override
        public int read() throws IOException {
            return 0;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            int charsRead = super.read(cbuf, off, len);
            if (charsRead > 0) {
                String input = new String(cbuf, off, charsRead);
                String filteredContent = removeComments(input);
                char[] filteredChars = filteredContent.toCharArray();
                int filteredLen = Math.min(filteredChars.length, len);
                System.arraycopy(filteredChars, 0, cbuf, off, filteredLen);
                return filteredLen;
            }
            return charsRead;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                in.close();
                super.close();
            }
        }
    }
}

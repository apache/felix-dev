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
package org.apache.felix.cm.configuration;

import java.io.IOException;
import java.io.Reader;
import java.util.Hashtable;

import org.apache.felix.cm.configuration.impl.ConfigurationReaderImpl;
import org.apache.felix.cm.configuration.impl.ConfigurationWriterImpl;
import org.apache.felix.cm.configuration.impl.JsonSupport;
import org.apache.felix.cm.configuration.impl.OrderedDictionary;

import jakarta.json.JsonValue;

/**
 * Factory class for JSON and configuration support
 */
public class Configurations {


    private Configurations() {
        // Do not instantiate this factory class
    }

    /**
     * Get a builder to create a configuration reader
     *
     * @return A configuration reader builder
     */
    public static ConfigurationReader.Builder buildReader() {
        return new ConfigurationReaderImpl();
    }

    /**
     * Get a builder to create a configuration writer
     *
     * @return A configuration writer builder
     */
    public static ConfigurationWriter.Builder buildWriter() {
        return new ConfigurationWriterImpl();
    }

    /**
     * Create a reader handling comments in JSON. The reader tries to leave the original
     * structure of the document untouched, meaning comments will be replace with spaces.
     * Closing this reader will close the underlying reader.
     *
     * @param reader Reader for the JSON
     * @return Reader for the pure JSON
     * @throws IOException If reading fails.
     */
    public static Reader jsonCommentAwareReader(final Reader reader) throws IOException {
        return JsonSupport.createCommentRemovingReader(reader);
    }

    /**
     * Create a new map for configuration properties The returned map keeps the
     * order of properties added and is using case-insensitive keys.
     *
     * @return A new map
     */
    public static Hashtable<String, Object> newConfiguration() {
        return new OrderedDictionary();
    }

    /**
     * Convert a json value into an object. The resulting object is
     * <ul>
     * <li>{@code null} : if the provided value is {@code null} or
     * {@code ValueType#NULL}
     * <li>A Boolean : if the json value is a boolean
     * <li>A Long : if the json value contains an integral number
     * <li>A Double : if the json value contains a non integral number
     * <li>A String : for any other provided non array value
     * <li>A Boolean[] : if the provided value is an array of booleans
     * <li>A Long[] : if the provided value is an array of integral numbers
     * <li>A Double[] : if the provided value is an array of non integral numbers
     * <li>A String[] : for any other provided array value
     * </ul>
     *
     * @param value The json value
     * @return The object or {@code null} if the provided value is {@code null} or
     *         {@code ValueType#NULL}
     */
    public static Object convertToObject(final JsonValue value) {
        return JsonSupport.convertToObject(value);
    }

    /**
     * Convert an object to a JSON value. The resulting value is:
     * <ul>
     * <li>{@code JsonValue.NULL} : if the provided value is {@code null}
     * <li>{@code JsonValue.TRUE} or {@code JsonValue.FALSE} : if the provided value is Boolean.
     * <li>A JSON value of type {@code JsonValue.ValueType#NUMBER} : if the value is a Integer, Long, Float, Double, Byte, Short
     * <li>{@code JsonValue.ValueType#OBJECT} : if the value is a map
     * <li>{@code JsonValue.ValueType#ARRAY} : if the value is an array or a collection
     * <li>{@code JsonValue.ValueType#STRING} : otherwise, calling {@code toString()} on the value.
     * <li>
     * </ul>
     * @param value The object
     * @return The JSON value.
     */
    public static JsonValue convertToJsonValue(final Object value) {
        return JsonSupport.convertToJson(value);
    }
}

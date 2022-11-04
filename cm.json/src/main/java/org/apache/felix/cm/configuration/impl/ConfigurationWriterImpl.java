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
package org.apache.felix.cm.configuration.impl;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;

import org.apache.felix.cm.configuration.ConfigurationResource;
import org.apache.felix.cm.configuration.ConfigurationWriter;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;

public class ConfigurationWriterImpl
        implements ConfigurationWriter, ConfigurationWriter.Builder {

    private boolean closed = false;

    private JsonGenerator generator;

    private boolean closeGenerator;

    private void checkClosed() throws IOException {
        if (this.closed) {
            throw new IOException("Writer already closed");
        }
        closed = true;
    }

    @Override
    public ConfigurationWriter build(final Writer writer) {
        this.generator = Json.createGeneratorFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true))
                .createGenerator(new FilterWriter(writer) {

                    @Override
                    public void close() throws IOException {
                        // do not close provided writer, just flush
                        super.flush();
                    }

                });
        this.closeGenerator = true;
        return this;
    }

    @Override
    public ConfigurationWriter build(final JsonGenerator generator) {
        this.generator = generator;
        this.closeGenerator = false;
        return this;
    }

    @Override
    public void writeConfiguration(final Dictionary<String, Object> properties) throws IOException {
        checkClosed();
        try {
            writeConfigurationInternal(properties);
            if ( this.closeGenerator) this.generator.close();
        } catch ( final JsonException je) {
            throw new IOException(je.getMessage(), je);
        }
    }

    private void writeConfigurationInternal(final Dictionary<String, Object> properties) throws IOException {
        generator.writeStartObject();
        final Enumeration<String> e = properties.keys();
        while (e.hasMoreElements()) {
            final String name = e.nextElement();
            final Object value = properties.get(name);

            final Map.Entry<String, JsonValue> entry = TypeConverter.convertObjectToTypedJsonValue(value);
            final String key;
            if (TypeConverter.NO_TYPE_INFO.equals(entry.getKey())) {
                key = name;
            } else {
                key = name.concat(":").concat(entry.getKey());
            }
            generator.write(key, entry.getValue());
        }
        generator.writeEnd();
    }

    @Override
    public void writeConfigurationResource(final ConfigurationResource resource) throws IOException {
        checkClosed();
        try {
            generator.writeStartObject();
            for (final Map.Entry<String, Object> entry : resource.getProperties().entrySet()) {
                generator.write(entry.getKey(), JsonSupport.convertToJson(entry.getValue()));
            }
            for (final Map.Entry<String, Hashtable<String, Object>> entry : resource.getConfigurations().entrySet()) {
                generator.writeKey(entry.getKey());
                writeConfigurationInternal(entry.getValue());
            }
            generator.writeEnd();
            if ( this.closeGenerator) this.generator.close();
        } catch ( final JsonException je) {
            throw new IOException(je.getMessage(), je);
        }
    }
}

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

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

import org.apache.felix.cm.json.io.ConfigurationReader;
import org.apache.felix.cm.json.io.ConfigurationResource;
import org.apache.felix.cm.json.io.Configurations;
import org.osgi.service.configurator.ConfiguratorConstants;
import org.osgi.util.converter.ConversionException;
import org.osgi.util.converter.Converters;

public class ConfigurationReaderImpl
        implements ConfigurationReader, ConfigurationReader.Builder {

    private boolean closed = false;

    private Reader reader;

    private JsonObject jsonObject;

    private boolean verifyAsBundleResource = false;

    private String identifier;

    private final List<String> errors = new ArrayList<>();

    private BinaryHandler binaryHandler;

    private ConfiguratorPropertyHandler propertyHandler;

    @Override
    public Builder verifyAsBundleResource(final boolean flag) {
        this.verifyAsBundleResource = flag;
        return this;
    }

    @Override
    public Builder withIdentifier(final String value) {
        this.identifier = value;
        return this;
    }

    @Override
    public Builder withBinaryHandler(final BinaryHandler handler) {
        this.binaryHandler = handler;
        return this;
    }

    @Override
    public Builder withConfiguratorPropertyHandler(final ConfiguratorPropertyHandler handler) {
        this.propertyHandler = handler;
        return this;
    }

    @Override
    public ConfigurationReader build(final Reader reader) {
        this.reader = reader;
        return this;
    }

    @Override
    public ConfigurationReader build(final JsonObject object) {
        this.jsonObject = object;
        return this;
    }

    @Override
    public List<String> getIgnoredErrors() {
        return this.errors;
    }

    private void checkClosed() throws IOException {
        if (this.closed) {
            throwIOException("Reader already closed");
        }
        closed = true;
    }

    @Override
    public Hashtable<String, Object> readConfiguration() throws IOException {
        checkClosed();
        if (this.reader != null) {
            try {
                this.jsonObject = JsonSupport.parseJson(this.identifier, this.reader);
            } catch ( final JsonException jpe) {
                throw new IOException("Invalid JSON " + jpe.getMessage(), jpe);
            }
        }
        return readSingleConfiguration("<configuration>", this.jsonObject);
    }

    @Override
    public ConfigurationResource readConfigurationResource() throws IOException {
        checkClosed();
        if (this.reader != null) {
            try {
                this.jsonObject = JsonSupport.parseJson(this.identifier, this.reader);
            } catch ( final JsonException jpe) {
                throw new IOException("Invalid JSON " + jpe.getMessage(), jpe);
            }
        }
        verifyJsonResource();
        final ConfigurationResource resource = new ConfigurationResource();

        for (final Map.Entry<String, JsonValue> entry : this.jsonObject.entrySet()) {
            if (entry.getKey().startsWith(ConfigurationResource.CONFIGURATOR_PROPERTY_PREFIX)) {
                // internal property
                resource.getProperties().put(entry.getKey(), JsonSupport.convertToObject(entry.getValue()));
            } else if (entry.getValue().getValueType() != ValueType.OBJECT) {
                addError("Ignoring property (not a configuration) : ".concat(entry.getKey()));
                resource.getProperties().put(entry.getKey(), JsonSupport.convertToObject(entry.getValue()));
            } else {
                final Hashtable<String, Object> properties = readSingleConfiguration(entry.getKey(),
                        entry.getValue().asJsonObject());
                if (properties != null) {
                    resource.getConfigurations().put(entry.getKey(), properties);
                }
            }
        }
        return resource;
    }

    private void addError(final String msg) {
        if (this.identifier == null) {
            this.errors.add(msg);
        } else {
            this.errors.add(identifier.concat(" : ").concat(msg));
        }
    }

    private void throwIOException(final String msg) throws IOException {
        if (this.identifier == null) {
            throw new IOException(msg);
        }
        throw new IOException(this.identifier.concat(" : ").concat(msg));
    }

    /**
     * Verify the JSON according to the rules
     *
     * @param root The JSON root object.
     */
    private void verifyJsonResource() throws IOException {
        final Object version = JsonSupport
                .convertToObject(this.jsonObject.get(ConfiguratorConstants.PROPERTY_RESOURCE_VERSION));
        if (version != null) {
            int v = -1;
            try {
                v = Converters.standardConverter().convert(version).defaultValue(-1).to(Integer.class);
            } catch ( final ConversionException ce ) {
                // ignore
            }
            if (v == -1) {
                throwIOException("Invalid resource version information : ".concat(version.toString()));
            }
            // we only support version 1
            if (v != 1) {
                throwIOException("Unknown resource version : ".concat(version.toString()));
            }
        }
        if (!verifyAsBundleResource) {
            // if this is not a bundle resource
            // then version and symbolic name must be set
            final Object rsrcVersion = JsonSupport
                    .convertToObject(this.jsonObject.get(ConfiguratorConstants.PROPERTY_VERSION));
            if (rsrcVersion == null) {
                throwIOException("Missing version information");
            }
            if (!(rsrcVersion instanceof String)) {
                throwIOException("Invalid version information : ".concat(rsrcVersion.toString()));
            }
            final Object rsrcName = JsonSupport
                    .convertToObject(this.jsonObject.get(ConfiguratorConstants.PROPERTY_SYMBOLIC_NAME));
            if (rsrcName == null) {
                throwIOException("Missing symbolic name information");
            }
            if (!(rsrcName instanceof String)) {
                throwIOException("Invalid symbolic name information : ".concat(rsrcVersion.toString()));
            }
        }
    }

    public static final class KeyInfo {

        public final String propertyKey;

        public final String typeInfo;

        public final boolean isInternal;

        public final boolean isBinary;

        public KeyInfo(final String mapKey) {
            this.isInternal = mapKey.startsWith(ConfigurationResource.CONFIGURATOR_PROPERTY_PREFIX);
            String key = mapKey;
            if (isInternal) {
                key = key.substring(ConfigurationResource.CONFIGURATOR_PROPERTY_PREFIX.length());
            }
            final int pos = key.indexOf(':');
            String typeInfo = null;
            if (pos != -1) {
                typeInfo = key.substring(pos + 1);
                key = key.substring(0, pos);
            }
            this.propertyKey = key;
            this.typeInfo = typeInfo;
            this.isBinary = TypeConverter.TYPE_BINARY.equals(typeInfo) || TypeConverter.TYPE_BINARIES.equals(typeInfo);
        }
    }

    /**
     * Read a single configuration
     *
     * @param pid The configuration pid
     * @param propertyMap The configuration map
     * @return A valid configuration dictionary or {@code null}
     * @throws IOException If reading fails
     */
    private Hashtable<String, Object> readSingleConfiguration(final String pid,
            final JsonObject propertyMap) throws IOException {

        final Hashtable<String, Object> properties = Configurations.newConfiguration();
        boolean valid = true;
        for (final Map.Entry<String, JsonValue> propEntry : propertyMap.entrySet()) {
            final String mapKey = propEntry.getKey();

            final KeyInfo keyInfo = new KeyInfo(mapKey);

            if ( keyInfo.isBinary ) {
                if ( !this.verifyAsBundleResource) {
                    throwIOException("PID ".concat(pid)
                            .concat(" : Properties of type binary not allowed for non bundle resource : ")
                            .concat(keyInfo.propertyKey));
                }
                if ( this.binaryHandler == null ) {
                    throwIOException("PID ".concat(pid)
                        .concat(" : No handler configured for binary property : ")
                        .concat(keyInfo.propertyKey));
                }
                if ( keyInfo.isInternal ) {
                    throwIOException("PID ".concat(pid)
                            .concat(" : Binary values are not allowed for configurator properties : ")
                            .concat(keyInfo.propertyKey));
                }
            }

            if ( keyInfo.isInternal ) {
                final Object value = JsonSupport.convertToObject(propEntry.getValue());
                if ( propertyHandler == null ) {
                    if ( properties.put(keyInfo.propertyKey, value) != null ) {
                        throwIOException("PID ".concat(pid)
                        .concat(" : Duplicate property (properties are case-insensitive) : ")
                        .concat(keyInfo.propertyKey));
                    }
                } else {
                    propertyHandler.handleConfiguratorProperty(pid, keyInfo.propertyKey, value);
                }
            } else {
                // convert value
                Object convertedVal = TypeConverter.convertObjectToType(propEntry.getValue(), keyInfo.typeInfo);

                if ( convertedVal == TypeConverter.CONVERSION_FAILED ) {
                    final String msg = "PID ".concat(pid).concat(" : Invalid value/type for configuration : ")
                            .concat(mapKey).concat(" : ").concat(propEntry.getValue().toString());
                    if (!this.verifyAsBundleResource) {
                        throwIOException(msg);
                    }
                    addError(msg);
                    valid = false;
                    break;
                }

                // special handling for binary and binary[]
                if (keyInfo.isBinary) {
                    // String or String[] ?
                    if ( convertedVal instanceof String ) {
                        final String path = (String)convertedVal;
                        convertedVal = this.binaryHandler.handleBinaryValue(pid, keyInfo.propertyKey, path);
                    } else {
                        final String[] paths = (String[])convertedVal;
                        final String[] array = new String[paths.length];
                        convertedVal = array;
                        for(int i=0;i<paths.length;i++) {
                            array[i] =  this.binaryHandler.handleBinaryValue(pid, keyInfo.propertyKey, paths[i]);
                            if ( array[i] == null ) {
                                convertedVal = null;
                            }
                        }
                    }
                }
                // if ( convertedVal is null) this configuration is invalid
                if ( convertedVal == null ) {
                    valid = false;
                } else {
                    if ( properties.put(keyInfo.propertyKey, convertedVal) != null ) {
                        throwIOException("PID ".concat(pid)
                        .concat(" : Duplicate property (properties are case-insensitive) : ")
                        .concat(keyInfo.propertyKey));
                    }
                }
            }
        }
        return valid ? properties : null;
    }
}

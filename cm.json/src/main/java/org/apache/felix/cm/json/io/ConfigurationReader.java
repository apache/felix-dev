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
package org.apache.felix.cm.json.io;

import java.io.IOException;
import java.io.Reader;
import java.util.Hashtable;
import java.util.List;

import jakarta.json.JsonObject;

import org.osgi.annotation.versioning.ProviderType;

/**
 * A configuration reader can read configuration resources as defined in
 * <a href="https://osgi.org/specification/osgi.cmpn/7.0.0/service.configurator.html">OSGi Configurator specificiation</a>.
 * A reader can be obtained through a {@link Builder}.
 */
@ProviderType
public interface ConfigurationReader {

    /**
     * Read a single configuration from the provided reader. The reader is not
     * closed. {@link #getIgnoredErrors()} can be used after reading if any ignored
     * errors occurred during processing the configuration.
     *
     * @return The read configuration.
     * @throws IOException If reading fails
     */
    Hashtable<String, Object> readConfiguration() throws IOException;

    /**
     * Read a configuration resource from the provided reader. The reader is not
     * closed. {@link #getIgnoredErrors()} can be used after reading if any ignored
     * errors occurred during processing the resource.
     *
     * @return The read configurations.
     * @throws IOException If reading fails
     */
    ConfigurationResource readConfigurationResource() throws IOException;

    /**
     * After reading a configuration (resource) this method will return a list of
     * ignored errors.
     *
     * @return List of ignored errors, might be empty.
     */
    List<String> getIgnoredErrors();

    /**
     * Builder to create a reader. A builder can be obtained using
     * {@link Configurations#buildReader()}
     */
    public interface Builder {

        /**
         * By default a configuration resource is not treated as a bundle resource.
         * Additional rules apply for a resource outside of a bundle.
         *
         * @param flag Enable or disable verification as bundle resource
         * @return This builder
         */
        Builder verifyAsBundleResource(boolean flag);

        /**
         * Set an identifier. If set all ignored error messages and most exceptions will
         * contain this identifier.
         *
         * @param identifier An identifier for the resource
         * @return This builder
         */
        Builder withIdentifier(String identifier);

        /**
         * Set the binary handler.
         * If no binary handler is set and a binary property is encountered
         * the reading of the configuration resource fails.
         *
         * @param handler The binary handler
         * @return This builder
         */
        Builder withBinaryHandler(BinaryHandler handler);

        /**
         * Set the configurator property handler.
         * If no handler is set and a configurator property is encountered
         * it is added as is to the returned configuration dictionary.
         *
         * @param handler The binary handler
         * @return This builder
         */
        Builder withConfiguratorPropertyHandler(ConfiguratorPropertyHandler handler);

        /**
         * Build the configuration reader for the provided reader.
         *
         * @param reader The reader for the JSON
         * @return The configuration reader
         */
        ConfigurationReader build(Reader reader);

        /**
         * Build the configuration reader for the JSON object.
         *
         * @param object The JSON object
         * @return The configuration reader
         */
        ConfigurationReader build(JsonObject object);
    }

    /**
     * Handler for binary properties
     */
    @FunctionalInterface
    public interface BinaryHandler {

        /**
         * Provide the actual value for a binary property
         * @param pid The pid of the configuration
         * @param key The key of the binary property
         * @param value The binary property value
         * @return The actual value or {@code null} on failure
         */
        String handleBinaryValue(String pid, String key, String value);
    }

    /**
     * Handler for configurator properties
     */
    @FunctionalInterface
    public interface ConfiguratorPropertyHandler {

        void handleConfiguratorProperty(String pid, String key, Object value);
    }
}

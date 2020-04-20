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
package org.apache.felix.cm.json;

import java.io.IOException;
import java.io.Writer;
import java.util.Dictionary;

import javax.json.stream.JsonGenerator;

import org.osgi.annotation.versioning.ProviderType;

/**
 * A configuration writer can write configuration resources as defined in
 * <a href="https://osgi.org/specification/osgi.cmpn/7.0.0/service.configurator.html">OSGi Configurator specificiation</a>.
 * A writer can be obtained through a {@link Builder}.
 */
@ProviderType
public interface ConfigurationWriter {

    /**
     * Write a single configuration to the provided writer. The writer/generator is not
     * closed.
     *
     * @param properties The configuration
     * @throws IOException If writing fails
     */
    void writeConfiguration(Dictionary<String, Object> properties) throws IOException;

    /**
     * Write a configuration resource to the provided writer. The writer/generator is not
     * closed.
     *
     * @param resource The configuration resource
     * @throws IOException If writing fails
     */
    void writeConfigurationResource(ConfigurationResource resource) throws IOException;

    /**
     * Builder to create a writer. A builder can be obtained using
     * {@link Configurations#buildWriter()}
     */
    public interface Builder {

        /**
         * Build the configuration writer for the provided writer.
         * The writer is not closed when configuration(s) are written
         *
         * @param writer The writer for the JSON
         * @return The configuration writer
         */
        ConfigurationWriter build(Writer writer);

        /**
         * Build the configuration writer for the provided JSON generator.
         * The generator is not closed when configuration(s) are written
         *
         * @param generator The JSON generator
         * @return The configuration writer
         */
        ConfigurationWriter build(JsonGenerator generator);
    }
}

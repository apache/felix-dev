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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

import jakarta.json.JsonException;

import org.junit.Test;

public class ConfigurationReaderImplTest {

    @Test
    public void testReadSingleConfiguration() throws IOException {
        final ConfigurationReaderImpl cfgReader = new ConfigurationReaderImpl();
        final Dictionary<String, Object> config;
        try (final InputStream jsonStream = this.getClass().getResourceAsStream("/configs/single.json");
                final Reader jsonReader = new InputStreamReader(jsonStream, StandardCharsets.UTF_8)) {
            config = cfgReader.build(jsonReader).readConfiguration();
        }
        assertEquals(1, config.size());
        assertEquals("Hello World", config.get("text"));
    }

    @Test
    public void testReadConfigurationResource() throws IOException {
        final ConfigurationReaderImpl cfgReader = new ConfigurationReaderImpl();
        final Map<String, Hashtable<String, Object>> configs;
        try (final InputStream jsonStream = this.getClass().getResourceAsStream("/configs/resource.json");
                final Reader jsonReader = new InputStreamReader(jsonStream, StandardCharsets.UTF_8)) {
            configs = cfgReader.build(jsonReader).readConfigurationResource().getConfigurations();
        }
        assertEquals(2, configs.size());
        assertNotNull(configs.get("config.a"));
        assertNotNull(configs.get("config.b"));
        assertEquals("Hello World", configs.get("config.a").get("text"));
        assertEquals(8080, configs.get("config.b").get("port"));
    }

    @Test
    public void testReadConfigurationResourceMissingProperties() throws IOException {
        final ConfigurationReaderImpl cfgReader = new ConfigurationReaderImpl();
        try (final InputStream jsonStream = this.getClass().getResourceAsStream("/configs/bundle.json");
                final Reader jsonReader = new InputStreamReader(jsonStream, StandardCharsets.UTF_8)) {
            cfgReader.build(jsonReader).readConfigurationResource();
            fail();
        } catch (final IOException ioe) {
            // expected
        }
    }

    @Test
    public void testReadBundleConfigurationResource() throws IOException {
        final ConfigurationReaderImpl cfgReader = new ConfigurationReaderImpl();
        cfgReader.verifyAsBundleResource(true);
        final Map<String, Hashtable<String, Object>> configs;
        try (final InputStream jsonStream = this.getClass().getResourceAsStream("/configs/bundle.json");
                final Reader jsonReader = new InputStreamReader(jsonStream, StandardCharsets.UTF_8)) {
            configs = cfgReader.build(jsonReader).readConfigurationResource().getConfigurations();
        }
        assertEquals(2, configs.size());
        assertNotNull(configs.get("config.a"));
        assertNotNull(configs.get("config.b"));
        assertEquals("Hello World", configs.get("config.a").get("text"));
        assertEquals(8080, configs.get("config.b").get("port"));
    }

    @Test
    public void testReadInvalidJson() throws IOException {
        final String json = "{\n \"a\" : 5 \n \"b\" : 2\n}";

        final ConfigurationReaderImpl cfgReader = new ConfigurationReaderImpl();
        try {
            cfgReader.build(new StringReader(json)).readConfigurationResource();
            fail();
        } catch ( final IOException ioe) {
            assertTrue(ioe.getCause() instanceof JsonException);
        }
    }

    @Test(expected = IOException.class)
    public void testReadSingleConfigurationDuplicateKeys() throws IOException {
        final ConfigurationReaderImpl cfgReader = new ConfigurationReaderImpl();
        try (final InputStream jsonStream = this.getClass().getResourceAsStream("/configs/single-duplicatekeys.json");
                final Reader jsonReader = new InputStreamReader(jsonStream, StandardCharsets.UTF_8)) {
            cfgReader.build(jsonReader).readConfiguration();
        }
    }
}

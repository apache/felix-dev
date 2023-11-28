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

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A configuration resource holds a set of configurations
 */
public final class ConfigurationResource {

    public static final String CONFIGURATOR_PROPERTY_PREFIX = ":configurator:";

    private final Map<String, Hashtable<String, Object>> configurations = new LinkedHashMap<>();

    private final Map<String, Object> properties = new LinkedHashMap<>();

    /**
     * The set of configurations mapped by identifier.
     * @return The configurations.
     */
    public Map<String, Hashtable<String, Object>> getConfigurations() {
        return this.configurations;
    }

    /**
     * The properties of the resource
     * @return A map of properties
     */
    public Map<String, Object> getProperties() {
        return properties;
    }
}

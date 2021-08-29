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
package org.apache.felix.webconsole.spi;

import java.io.IOException;
import java.util.Dictionary;

/**
 * A configuration handler allows to hook into the processing of configurations for
 * the webconsole plugin.
 * A handler can decide to hide configurations and properties but also implement
 * additional validation.
 * All configuration handlers are called in order of their service ranking, highest first.
 */
public interface ConfigurationHandler {

    /**
     * A new configuration with that pid should be created
     * @param pid The pid
     * @throws IOException For an error or {@link ValidationException} if creation is not allowed
     */
    void createConfiguration(String pid) throws IOException;

    /**
     * A new factory configuration with that pid should be created
     * @param factoryPid The factory pid
     * @param name Optional name, might be {@code null} if unknown
     * @throws IOException For an error or {@link ValidationException} if creation is not allowed
     */
    void createFactoryConfiguration(String factoryPid, String name) throws IOException;

    /**
     * A configuration should be deleted
     * @param factoryPid Optional factory pid
     * @param pid The pid
     * @throws IOException For an error or {@link ValidationException} if deletion is not allowed
     */
    void deleteConfiguration(String factoryPid, String pid) throws IOException;

    /**
     * A configuration should be updated
     * @param factoryPid Optional factory pid
     * @param pid The pid
     * @param props Mutable dictionary
     * @throws IOException For an error or {@link ValidationException} if deletion is not allowed
     */
    void updateConfiguration(String factoryPid, String pid, Dictionary<String, Object> props) throws IOException;
}
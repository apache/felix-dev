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
package org.apache.felix.webconsole.internal.configuration;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.webconsole.spi.ConfigurationHandler;
import org.apache.felix.webconsole.spi.ValidationException;
import org.junit.Test;

public class ConfigJsonSupportTest {

    @Test public void testGetPropertyNamesForFormNoHandler() throws IOException {
        final ConfigJsonSupport support = new ConfigJsonSupport(null, null, null, Collections.emptyList());

        final Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("a", "1");
        dict.put("b", "2");

        support.filterConfigurationProperties("f", "p", dict);

        assertEquals(2, dict.size());
        assertEquals("1", dict.get("a"));
        assertEquals("2", dict.get("b"));
    }

    @Test public void testGetPropertyNamesForFormHandlerNoFiltering() throws IOException {
        final ConfigurationHandler handler = new ConfigurationHandler() {

            @Override
            public void createConfiguration(String pid) throws ValidationException, IOException {                
            }

            @Override
            public void createFactoryConfiguration(String factoryPid, String name)
                    throws ValidationException, IOException {                
            }

            @Override
            public void deleteConfiguration(String factoryPid, String pid) throws ValidationException, IOException {                
            }

            @Override
            public void updateConfiguration(String factoryPid, String pid, Dictionary<String, Object> props)
                    throws ValidationException, IOException {                
            }
            
        };
        final ConfigJsonSupport support = new ConfigJsonSupport(null, null, null, Collections.singletonList(handler));

        final Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("a", "1");
        dict.put("b", "2");

        support.filterConfigurationProperties("f", "p", dict);

        assertEquals(2, dict.size());
        assertEquals("1", dict.get("a"));
        assertEquals("2", dict.get("b"));
    }

    @Test public void testGetPropertyNamesForFormHandlerFiltering() throws IOException {
        final ConfigurationHandler handler = new ConfigurationHandler() {

            @Override
            public void createConfiguration(String pid) throws ValidationException, IOException {                
            }

            @Override
            public void createFactoryConfiguration(String factoryPid, String name)
                    throws ValidationException, IOException {                
            }

            @Override
            public void deleteConfiguration(String factoryPid, String pid) throws ValidationException, IOException {                
            }

            @Override
            public void updateConfiguration(String factoryPid, String pid, Dictionary<String, Object> props)
                    throws ValidationException, IOException {                
            }

            @Override
            public void filterProperties(String factoryPid, String pid, Collection<String> propertyNames)
                    throws IOException {
                propertyNames.remove("b");
                propertyNames.remove("c");
            }
            
        };
        final ConfigJsonSupport support = new ConfigJsonSupport(null, null, null, Collections.singletonList(handler));

        final Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("a", "1");
        dict.put("b", "2");

        support.filterConfigurationProperties("f", "p", dict);

        assertEquals(1, dict.size());
        assertEquals("1", dict.get("a"));
    }
}

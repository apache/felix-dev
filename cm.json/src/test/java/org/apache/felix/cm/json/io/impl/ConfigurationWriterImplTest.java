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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Dictionary;

import org.junit.Test;

public class ConfigurationWriterImplTest {

    @Test
    public void testWriteInAlphabeticalOrder() throws IOException {
        final ConfigurationWriterImpl cfgWriter = new ConfigurationWriterImpl();
        StringWriter writer = new StringWriter();
        Dictionary<String, Object> properties = new OrderedDictionary();
        properties.put("Z", "Z");
        properties.put("A", Integer.valueOf(1));
        properties.put("b", Long.valueOf(2000l));
        cfgWriter.build(writer).writeConfiguration(properties);
        assertEquals("{\n"
                + "  \"A:Integer\":1,\n"
                + "  \"Z\":\"Z\",\n"
                + "  \"b\":2000\n"
                + "}", writer.toString());
    }

}

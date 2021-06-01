/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.inventory.impl.helper;

import java.io.StringWriter;

import org.junit.Assert;
import org.junit.Test;

public class JSONConfigurationWriterTest {

    @Test public void test_escaping() {
        final StringWriter out = new StringWriter();
        final JSONConfigurationWriter w = new JSONConfigurationWriter(out);
        w.startJSONWrapper();
        w.write("abcd\\\n1\t2\f3\b4\"5end");
        w.endJSONWrapper();
        w.close();

        final String expected = "[" + System.lineSeparator()
                + "    \"abcd\\\\\"," + System.lineSeparator()
                + "    \"1234\\\"5end\"]" + System.lineSeparator();

        Assert.assertEquals("Escaped JSON", expected, out.toString());

    }
}

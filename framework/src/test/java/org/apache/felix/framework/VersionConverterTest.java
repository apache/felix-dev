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
package org.apache.felix.framework;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Version;

class VersionConverterTest {

    @Test
    void conversions() throws Exception {
        assertValid("1.0.0", "1");
        assertValid("2.3.0", "2.3");
        assertValid("1.0.0", "1.0.0");
        assertValid("5.0.0.SNAPSHOT", "5-SNAPSHOT");
        assertValid("1.0.0.SNAPSHOT", "1.0-SNAPSHOT");
        assertValid("1.2.3.SNAPSHOT", "1.2.3-SNAPSHOT");
        assertValid("1.2.3.foo-123", "1.2.3.foo-123");
        assertValid("1.2.3.foo-123-hello", "1.2.3.foo-123-hello");
        assertValid("1.2.3.4_5_6", "1.2.3.4.5.6");
        assertValid("1.2.3.classifier-M1", "1.2.3-classifier-M1");
        assertValid("1.2.3.classifier-M1", "1.2.3.classifier-M1");
        assertValid("1.2.3.classifier_M1", "1.2.3.classifier.M1");
    }


    private void assertValid(String expectedVersion, String input) throws Exception {
        assertThat(VersionConverter.toOsgiVersion(input)).isEqualTo(new Version(expectedVersion));
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.http.javaxwrappers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.mockito.Mockito;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

public class HttpServletRequestWrapperTest {

    @Test public void testGetPart() throws Exception {
        final HttpServletRequest orig = Mockito.mock(HttpServletRequest.class);
        final HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(orig);
        assertNull(wrapper.getPart("test"));

        Mockito.when(orig.getPart("foo")).thenReturn(Mockito.mock(Part.class));
        assertNotNull(wrapper.getPart("foo"));
    }
}

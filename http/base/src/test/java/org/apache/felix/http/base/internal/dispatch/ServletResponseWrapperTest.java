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
package org.apache.felix.http.base.internal.dispatch;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ServletResponseWrapperTest
{
    @Test public void testErrorServletPaths()
    {
        assertArrayEquals(new String[0], ServletResponseWrapper.getErrorServletPaths(null));
        assertArrayEquals(new String[0], ServletResponseWrapper.getErrorServletPaths(new String[0]));
        assertArrayEquals(new String[] {"/errors/page"}, ServletResponseWrapper.getErrorServletPaths(new String[] {"/errors/page"}));
        assertArrayEquals(new String[] {"/errors"}, ServletResponseWrapper.getErrorServletPaths(new String[] {"/errors/*"}));
        assertArrayEquals(new String[] {"/", "/"}, ServletResponseWrapper.getErrorServletPaths(new String[] {"", "/"}));
        assertArrayEquals(new String[] {"/a", "/b"}, ServletResponseWrapper.getErrorServletPaths(new String[] {"/a", "*.html", "/b"}));
    }
}

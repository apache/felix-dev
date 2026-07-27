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

import jakarta.servlet.http.Cookie;

import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class CookieWrapperTest {
    @Test
    public void testReservedCookieNames() {
        testCookie("Path");
        testCookie("MaxAge");
        testCookie("Comment");
    }

    private static void testCookie(String cookieName) {
        Cookie pathCookie = Mockito.mock(Cookie.class);
        when(pathCookie.getName()).thenReturn(cookieName);

        // Threw `java.lang.IllegalArgumentException: Cookie name "Path" is a reserved token` before
        CookieWrapper cookieWrapper = new CookieWrapper(pathCookie);

        assertEquals(cookieName, cookieWrapper.getName());
    }
}

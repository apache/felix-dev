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

import static org.mockito.Mockito.times;

import java.io.IOException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.mockito.Mockito;

public class IncludeResponseWrapperTest {

    @Test public void testReset() {
        final HttpServletResponse orig = Mockito.mock(HttpServletResponse.class);
        final HttpServletResponse include = new IncludeResponseWrapper(orig);
        
        Mockito.when(orig.isCommitted()).thenReturn(false);
        include.reset();
        Mockito.verify(orig, times(1)).isCommitted();
        Mockito.verifyNoMoreInteractions(orig);

        Mockito.when(orig.isCommitted()).thenReturn(true);
        include.reset();
        Mockito.verify(orig, times(2)).isCommitted();
        Mockito.verify(orig, times(1)).reset();
        Mockito.verifyNoMoreInteractions(orig);
    }

    @Test public void testContentMethods() {
        final HttpServletResponse orig = Mockito.mock(HttpServletResponse.class);
        final HttpServletResponse include = new IncludeResponseWrapper(orig);

        include.setContentLength(54);
        include.setContentLengthLong(33L);
        include.setContentType("text/plain");
        include.setLocale(null);
        include.setBufferSize(4500);

        Mockito.verifyNoInteractions(orig);
    }

    @Test public void testCookies() {
        final HttpServletResponse orig = Mockito.mock(HttpServletResponse.class);
        final HttpServletResponse include = new IncludeResponseWrapper(orig);

        include.addCookie(new Cookie("foo", "bar"));

        Mockito.verifyNoInteractions(orig);
    }

    @Test public void testSendError() throws IOException {
        final HttpServletResponse orig = Mockito.mock(HttpServletResponse.class);
        final HttpServletResponse include = new IncludeResponseWrapper(orig);

        include.sendError(500);
        include.sendError(500, "Error");

        Mockito.verifyNoInteractions(orig);
    }

    @Deprecated
    @Test public void testSetStatus() {
        final HttpServletResponse orig = Mockito.mock(HttpServletResponse.class);
        final HttpServletResponse include = new IncludeResponseWrapper(orig);

        include.setStatus(500);
        include.setStatus(500, "Error");

        Mockito.verifyNoInteractions(orig);
    }

    @Test public void testHeaders() {
        final HttpServletResponse orig = Mockito.mock(HttpServletResponse.class);
        final HttpServletResponse include = new IncludeResponseWrapper(orig);

        include.setDateHeader("foo-d", 2000L);
        include.addDateHeader("bar-d", 3000L);
        include.setIntHeader("foo-i", 1);
        include.addIntHeader("bar-i", 2);
        include.setHeader("foo", "value");
        include.addHeader("bar", "another");

        Mockito.verifyNoInteractions(orig);
    }
}

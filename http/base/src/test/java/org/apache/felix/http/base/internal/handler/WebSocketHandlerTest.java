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
package org.apache.felix.http.base.internal.handler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.apache.felix.http.javaxwrappers.ServletWrapper;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.junit.Before;
import org.junit.Test;

public class WebSocketHandlerTest {
    private javax.servlet.Servlet javaxServlet;
    private jakarta.servlet.Servlet jakartaServlet;
    private JettyWebSocketServlet jakartaWebSocketServlet;

    @Before
    public void setUp()
    {
        this.javaxServlet = mock(javax.servlet.Servlet.class);
        this.jakartaServlet = mock(jakarta.servlet.Servlet.class);
        this.jakartaWebSocketServlet = mock(JettyWebSocketServlet.class);
    }

    @Test
    public void isJettyWebSocketServlet(){
        assertFalse(WebSocketHandler.isJettyWebSocketServlet(this.javaxServlet));
        assertFalse(WebSocketHandler.isJettyWebSocketServlet(this.jakartaServlet));

        // See test scope dependency in pom.xml
        assertTrue(WebSocketHandler.isJettyWebSocketServlet(this.jakartaWebSocketServlet));

        // Also works with the wrapper
        assertTrue(WebSocketHandler.isJettyWebSocketServlet(new ServletWrapper(this.jakartaWebSocketServlet)));
    }
}

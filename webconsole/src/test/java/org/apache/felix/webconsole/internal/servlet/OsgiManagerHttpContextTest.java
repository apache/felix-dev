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
package org.apache.felix.webconsole.internal.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.felix.webconsole.spi.SecurityProvider;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.util.tracker.ServiceTracker;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicReference;

public class OsgiManagerHttpContextTest {

    @Test
    public void testPathsInHandleSecurity() throws Exception {

        Bundle bundle = Mockito.mock(Bundle.class);
        SecurityProvider provider = Mockito.mock(SecurityProvider.class);
        ServiceTracker<SecurityProvider, SecurityProvider> tracker = Mockito.mock(ServiceTracker.class);
        Mockito.when(tracker.getService()).thenReturn(provider);

        OsgiManagerHttpContext ctx = new OsgiManagerHttpContext(bundle, new AtomicReference<>(tracker), "/system/console");

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        Mockito.when(request.getContextPath()).thenReturn("/ctx/path/system/console");
        Mockito.when(request.getServletPath()).thenReturn("/bin/servlet");


        ctx.handleSecurity(request, response);

        ArgumentCaptor<HttpServletRequest> authenticationRequest = ArgumentCaptor.forClass(HttpServletRequest.class);
        ArgumentCaptor<HttpServletResponse> authenticationResponse = ArgumentCaptor.forClass(HttpServletResponse.class);
        Mockito.verify(provider, Mockito.times(1)).authenticate(authenticationRequest.capture(), authenticationResponse.capture());

        assertEquals("/ctx/path", authenticationRequest.getValue().getContextPath());
        assertEquals("/system/console", authenticationRequest.getValue().getServletPath());
        assertEquals("/bin/servlet", authenticationRequest.getValue().getPathInfo());
        assertEquals(response, authenticationResponse.getValue());
    }

}

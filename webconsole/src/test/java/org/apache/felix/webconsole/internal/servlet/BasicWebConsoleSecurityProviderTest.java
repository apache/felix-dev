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

import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BasicWebConsoleSecurityProviderTest {

    @Test
    public void testAuthenticate() throws Exception {
        final BundleContext bc = Mockito.mock(BundleContext.class);

        final BasicWebConsoleSecurityProvider provider = new BasicWebConsoleSecurityProvider(bc, "foo", "bar");
        assertNotNull(provider.authenticate("foo", "bar"));
        assertNull(provider.authenticate("foo", "blah"));
    }

    @Test
    public void testAuthenticatePwdDisabledWithRequiredSecurityProvider() throws Exception {
        final BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.when(bc.getProperty(OsgiManager.FRAMEWORK_PROP_SECURITY_PROVIDERS)).thenReturn("a");

        final BasicWebConsoleSecurityProvider provider = new BasicWebConsoleSecurityProvider(bc, "foo", "bar");
        assertNull(provider.authenticate("foo", "bar"));
        assertNull(provider.authenticate("foo", "blah"));
    }
}

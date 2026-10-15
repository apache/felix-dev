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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.apache.felix.framework.util.SecureAction;
import org.junit.jupiter.api.Test;

/**
 * An {@link IOException} thrown by a stream handler invoked through reflection is propagated
 * unchanged (FELIX-6861).
 */
class URLHandlersStreamHandlerProxyTest
{
    private static final FileNotFoundException FAILURE = new FileNotFoundException("no such resource");

    private static final URLStreamHandler FAILING_HANDLER = new URLStreamHandler()
    {
        @Override
        protected URLConnection openConnection(URL url) throws IOException
        {
            throw FAILURE;
        }

        @Override
        protected URLConnection openConnection(URL url, Proxy proxy) throws IOException
        {
            throw FAILURE;
        }
    };

    private static final URLStreamHandler BROKEN_HANDLER = new URLStreamHandler()
    {
        @Override
        protected URLConnection openConnection(URL url)
        {
            throw new UnsupportedOperationException("broken");
        }
    };

    @Test
    void openConnectionPropagatesIOExceptionOfHandler() throws Exception
    {
        URLHandlersStreamHandlerProxy proxy = new URLHandlersStreamHandlerProxy(
            "felixtest", new SecureAction(), FAILING_HANDLER, null);
        URL url = new URL(null, "felixtest://host/path", FAILING_HANDLER);

        assertThatThrownBy(() -> proxy.openConnection(url)).isSameAs(FAILURE);
    }

    @Test
    void openConnectionWithProxyPropagatesIOExceptionOfHandler() throws Exception
    {
        URLHandlersStreamHandlerProxy proxy = new URLHandlersStreamHandlerProxy(
            "felixtest", new SecureAction(), FAILING_HANDLER, null);
        URL url = new URL(null, "felixtest://host/path", FAILING_HANDLER);

        assertThatThrownBy(() -> proxy.openConnection(url, Proxy.NO_PROXY)).isSameAs(FAILURE);
    }

    @Test
    void openConnectionStillWrapsOtherFailuresOfHandler() throws Exception
    {
        URLHandlersStreamHandlerProxy proxy = new URLHandlersStreamHandlerProxy(
            "felixtest", new SecureAction(), BROKEN_HANDLER, null);
        URL url = new URL(null, "felixtest://host/path", BROKEN_HANDLER);

        assertThatThrownBy(() -> proxy.openConnection(url))
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseInstanceOf(UnsupportedOperationException.class);
    }
}

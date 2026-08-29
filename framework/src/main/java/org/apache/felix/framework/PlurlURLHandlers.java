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

import java.io.IOException;
import java.net.ContentHandler;
import java.net.URLStreamHandler;

import org.apache.felix.framework.plurl.Plurl;
import org.apache.felix.framework.plurl.PlurlContentHandlerFactory;
import org.apache.felix.framework.plurl.PlurlStreamHandlerFactory;

/**
 * <p>
 * PROTOTYPE (FELIX-6759): adapts Felix' {@link URLHandlers} onto the plurl
 * multiplexing URL factories.
 * </p>
 * <p>
 * {@code URLHandlers} currently claims the JVM-wide {@code java.net.URL} stream
 * handler factory by reflectively swapping a private static field
 * ({@code SecureAction.swapStaticFieldIfNotClass}). Obtaining a
 * {@code MethodHandles.Lookup} trusted enough to do that is the sole reason the
 * framework still uses {@code sun.misc.Unsafe}, and it means whichever framework
 * installs itself last wins the singleton - so Felix and Equinox cannot coexist in
 * one JVM without clobbering each other.
 * </p>
 * <p>
 * Plurl instead installs one cooperative router through the supported
 * {@code URL.setURLStreamHandlerFactory} API and lets any number of parties
 * register with it. Each registered factory answers {@link #shouldHandle(Class)}
 * to say whether a given calling class belongs to it; plurl walks the call stack
 * and routes accordingly. That maps directly onto what
 * {@link URLHandlers#getFrameworkFromContext()} already does.
 * </p>
 * <p>
 * See {@code org/apache/felix/framework/plurl/README.md} for the provenance of the
 * vendored plurl sources and the <b>unresolved licensing question</b> that currently
 * blocks this approach.
 * </p>
 */
class PlurlURLHandlers implements PlurlStreamHandlerFactory, PlurlContentHandlerFactory
{
    private final URLHandlers m_delegate;

    PlurlURLHandlers(URLHandlers delegate)
    {
        m_delegate = delegate;
    }

    /**
     * Registers this framework's handlers with the plurl router, installing the
     * router first if nobody has yet.
     */
    static PlurlURLHandlers install(URLHandlers delegate) throws IOException
    {
        PlurlURLHandlers handlers = new PlurlURLHandlers(delegate);
        Plurl.add((PlurlStreamHandlerFactory) handlers);
        Plurl.add((PlurlContentHandlerFactory) handlers);
        return handlers;
    }

    /**
     * Unregisters this framework's handlers, leaving the router in place for any
     * other framework instance still using it.
     */
    void uninstall() throws IOException
    {
        Plurl.remove((PlurlStreamHandlerFactory) this);
        Plurl.remove((PlurlContentHandlerFactory) this);
    }

    /**
     * Tells plurl whether the given calling class belongs to this framework.
     * <p>
     * This replaces the call stack walking URLHandlers does today: rather than
     * inspecting the stack itself to work out which framework owns the caller, the
     * router asks each registered factory about a single candidate class.
     */
    @Override
    public boolean shouldHandle(Class<?> clazz)
    {
        if (clazz == null)
        {
            return false;
        }
        ClassLoader loader = clazz.getClassLoader();
        if (loader == null)
        {
            return false;
        }
        String name = loader.getClass().getName();
        return name.startsWith("org.apache.felix.framework.BundleWiringImpl$BundleClassLoader")
            || name.startsWith("org.apache.felix.framework.ModuleImpl$ModuleClassLoader")
            || name.equals("org.apache.felix.framework.searchpolicy.ContentClassLoader");
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol)
    {
        return m_delegate.createURLStreamHandler(protocol);
    }

    @Override
    public ContentHandler createContentHandler(String mimeType)
    {
        return m_delegate.createContentHandler(mimeType);
    }
}

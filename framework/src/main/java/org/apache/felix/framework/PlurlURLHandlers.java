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

import java.net.ContentHandler;
import java.net.URLStreamHandler;

import org.apache.felix.framework.plurl.Plurl;
import org.apache.felix.framework.plurl.PlurlContentHandlerFactory;
import org.apache.felix.framework.plurl.PlurlStreamHandlerFactory;
import org.apache.felix.framework.plurl.impl.PlurlImpl;
import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.SecureAction;
import org.apache.felix.framework.util.Util;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.BundleReference;

/**
 * <p>
 * Registers this framework's URL handling with the plurl
 * multiplexing factories instead of taking over the JVM singletons directly.
 * </p>
 * <p>
 * {@link URLHandlers} used to claim the JVM-wide {@code URLStreamHandlerFactory} by
 * reflectively clearing a private static field on {@code java.net.URL}, which needed
 * a {@code MethodHandles.Lookup} trusted enough to write it and meant the last
 * framework to install itself won the singleton.
 * </p>
 * <p>
 * Plurl installs one cooperative router through the supported
 * {@code URL.setURLStreamHandlerFactory} API and asks each registered factory
 * {@link #shouldHandle(Class)} to claim a calling class, or
 * {@link #shouldHandle(String, String)} to claim the URL being parsed. That removes
 * the need for {@code URLHandlers.getFrameworkFromContext()} to walk the call stack:
 * by the time this factory is consulted, plurl has already established that the
 * caller belongs to this framework instance, so
 * {@link #createURLStreamHandler(String)} can use {@code m_felix} directly.
 * </p>
 * <p>
 * See {@code org/apache/felix/framework/plurl/README.md} for the provenance of the
 * vendored plurl sources.
 * </p>
 */
class PlurlURLHandlers implements PlurlStreamHandlerFactory, PlurlContentHandlerFactory
{
    private final Felix m_felix;
    private final SecureAction m_secureAction;

    private PlurlURLHandlers(Felix felix, SecureAction secureAction)
    {
        m_felix = felix;
        m_secureAction = secureAction;
    }

    /**
     * The plurl router is a JVM wide singleton, so it is installed once and shared by
     * every framework instance in this JVM. Only the per framework factories are
     * added and removed as frameworks come and go; tearing the router down while
     * another framework is still registered would break that framework's URLs.
     */
    private static Plurl m_router;
    private static int m_routerUsers;

    /**
     * Installs the plurl router if this is the first framework to need it and
     * registers the given framework's factories with it.
     * <p>
     * Failing to register must not prevent the framework from starting; in that case
     * this framework simply contributes no URL handlers, which the caller sees as a
     * {@code null} return.
     */
    static PlurlURLHandlers install(Felix felix, SecureAction secureAction)
    {
        PlurlURLHandlers handlers = new PlurlURLHandlers(felix, secureAction);
        try
        {
            synchronized (PlurlURLHandlers.class)
            {
                if (m_router == null)
                {
                    // Installing is what makes the plurl: protocol resolvable, which
                    // the static Plurl.add(..) calls below go through. Without it they
                    // fail with "unknown protocol: plurl".
                    Plurl router = new PlurlImpl();
                    router.install();
                    m_router = router;
                }
                m_routerUsers++;
            }

            Plurl.add((PlurlStreamHandlerFactory) handlers);
            Plurl.add((PlurlContentHandlerFactory) handlers);
            warnIfSelectionBySpecUnsupported(felix);
            return handlers;
        }
        catch (Throwable ex)
        {
            felix.getLogger().log(Logger.LOG_ERROR,
                "Unable to register this framework with plurl.", ex);
            releaseRouter();
            return null;
        }
    }

    /**
     * Unregisters this framework's factories, and uninstalls the router once the last
     * framework using it has gone.
     */
    void uninstall()
    {
        try
        {
            Plurl.remove((PlurlStreamHandlerFactory) this);
            Plurl.remove((PlurlContentHandlerFactory) this);
        }
        catch (Throwable ex)
        {
            m_felix.getLogger().log(Logger.LOG_ERROR,
                "Unable to unregister this framework from plurl.", ex);
        }
        finally
        {
            releaseRouter();
        }
    }

    private static void releaseRouter()
    {
        synchronized (PlurlURLHandlers.class)
        {
            if (m_routerUsers > 0)
            {
                m_routerUsers--;
            }
            if ((m_routerUsers == 0) && (m_router != null))
            {
                Plurl router = m_router;
                m_router = null;
                try
                {
                    router.uninstall();
                }
                catch (Throwable ex)
                {
                    // Nothing useful to do; the JVM factories stay as they are.
                }
            }
        }
    }

    /**
     * Tells plurl whether the given calling class belongs to this framework instance.
     * <p>
     * It is not enough for the class to come from some Felix bundle: two framework
     * instances in the same JVM both load classes through a
     * {@code BundleWiringImpl.BundleClassLoader}, so the owning framework has to
     * match as well. Otherwise one framework would answer lookups for a bundle
     * resolved in another.
     */
    @Override
    public boolean shouldHandle(Class<?> clazz)
    {
        if (clazz == null)
        {
            return false;
        }
        ClassLoader loader = clazz.getClassLoader();
        if (!(loader instanceof BundleReference))
        {
            return false;
        }
        Bundle bundle = ((BundleReference) loader).getBundle();
        return (bundle instanceof BundleImpl)
            && (((BundleImpl) bundle).getFramework() == m_felix);
    }

    /**
     * Warns when the plurl that won the install in this JVM cannot route by the URL.
     * <p>
     * The router is not necessarily the copy this framework brought: it may belong to
     * another framework instance, or to an application embedding its own, and it may
     * be older than this one. A bundle: URL can only be attributed to a framework by
     * the UUID it carries, so where {@link Plurl#PLURL_CAPABILITY_SELECT_BY_SPEC} is
     * not supported such a URL is handed to whichever factory registered first, which
     * then cannot resolve it. Nothing here can fix that, so say it plainly at startup
     * rather than leave it to surface later as a failed resource lookup.
     */
    private static void warnIfSelectionBySpecUnsupported(Felix felix)
    {
        if (!Plurl.capabilities().contains(Plurl.PLURL_CAPABILITY_SELECT_BY_SPEC))
        {
            felix.getLogger().log(Logger.LOG_WARNING,
                "The plurl implementation installed in this JVM does not support"
                    + " selecting a factory by the URL being parsed ("
                    + Plurl.PLURL_CAPABILITY_SELECT_BY_SPEC
                    + "). A bundle: URL parsed by a caller outside of any bundle may be"
                    + " routed to a different framework instance in this JVM and fail to"
                    + " resolve.");
        }
    }

    /**
     * Claims bundle: URLs belonging to this framework instance.
     * <p>
     * Every Felix framework in the JVM uses the same bundle: protocol, so the
     * protocol alone does not identify an owner; the framework UUID in the URL host
     * does. This matters when a URL is re-parsed by a caller that is in no bundle,
     * where there is nothing on the call stack for plurl to attribute.
     */
    @Override
    public boolean shouldHandle(String protocol, String spec)
    {
        if (!FelixConstants.BUNDLE_URL_PROTOCOL.equals(protocol) || (spec == null))
        {
            return false;
        }
        String uuid = Util.getFrameworkUUIDFromURL(getHost(protocol, spec));
        return (uuid != null)
            && uuid.equals(m_felix._getProperty(Constants.FRAMEWORK_UUID));
    }

    /**
     * Returns the host of the spec being parsed, or <tt>null</tt> if it has none.
     * The URL itself cannot be asked, because plurl has to pick a factory before the
     * URL has been parsed.
     */
    private static String getHost(String protocol, String spec)
    {
        int start = 0;
        if (spec.regionMatches(true, 0, protocol, 0, protocol.length())
            && (spec.length() > protocol.length())
            && (spec.charAt(protocol.length()) == ':'))
        {
            start = protocol.length() + 1;
        }
        if (!spec.startsWith("//", start))
        {
            return null;
        }
        start += 2;
        int end = start;
        while ((end < spec.length()) && ("/?#".indexOf(spec.charAt(end)) < 0))
        {
            end++;
        }
        return spec.substring(start, end);
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol)
    {
        if (FelixConstants.BUNDLE_URL_PROTOCOL.equals(protocol))
        {
            // Deliberately not bound to m_felix. The JVM caches one handler per
            // protocol for the whole JVM, so a handler pinned to this framework would
            // also be used for bundle: URLs belonging to another framework instance,
            // which then fail to resolve. The framework is instead resolved per call
            // from the UUID in the URL's host, which is what URLHandlers did.
            return new URLHandlersBundleStreamHandler(m_secureAction);
        }

        // Otherwise serve a URLStreamHandlerService registered in this framework. The
        // protocol based proxy resolves the service lazily on each use, which is how
        // URLHandlers built these: a service can come and go while a URL object using
        // this protocol is still around. Only claim the protocol if a service exists
        // now, so that unrelated protocols fall through to plurl's other factories.
        if (m_felix.getStreamHandlerService(protocol) != null)
        {
            return new URLHandlersStreamHandlerProxy(protocol, m_secureAction, null, null);
        }

        // Not ours. Returning null lets plurl ask the other registered factories and
        // fall back to the JVM built-ins.
        return null;
    }

    @Override
    public ContentHandler createContentHandler(String mimeType)
    {
        if (m_felix.getContentHandlerService(mimeType) != null)
        {
            return new URLHandlersContentHandlerProxy(mimeType, m_secureAction, null);
        }
        return null;
    }
}

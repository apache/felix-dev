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

import java.lang.reflect.Method;
import java.net.ContentHandler;
import java.net.ContentHandlerFactory;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.apache.felix.framework.util.Util.putIfAbsentAndReturn;

import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.SecureAction;
import org.apache.felix.framework.util.SecurityManagerEx;
import org.osgi.framework.Constants;
import org.osgi.service.url.URLStreamHandlerService;

/**
 * <p>
 * This class is a singleton and implements the stream and content handler
 * factories for all framework instances executing within the JVM. Any
 * calls to retrieve stream or content handlers is routed through this class
 * and it acts as a multiplexer for all framework instances. To achieve this,
 * all framework instances register with this class when they are created so
 * that it can maintain a centralized registry of instances.
 * </p>
 * <p>
 * When this class receives a request for a stream or content handler, it
 * always returns a proxy handler instead of only returning a proxy if a
 * handler currently exists. This approach is used for three reasons:
 * </p>
 * <ol>
 *   <li>Potential caching behavior by the JVM of stream handlers does not give
 *       you a second chance to provide a handler.
 *   </li>
 *   <li>Due to the dynamic nature of OSGi services, handlers may appear at
 *       any time, so always creating a proxy makes sense.
 *   </li>
 *   <li>Since these handler factories service all framework instances,
 *       some instances may have handlers and others may not, so returning
 *       a proxy is the only answer that makes sense.
 *   </li>
 * </ol>
 * <p>
 * It is possible to disable the URL Handlers service by setting the
 * <tt>framework.service.urlhandlers</tt> configuration property to <tt>false</tt>.
 * When multiple framework instances are in use, if no framework instances enable
 * the URL Handlers service, then the singleton stream and content factories will
 * never be set (i.e., <tt>URL.setURLStreamHandlerFactory()</tt> and
 * <tt>URLConnection.setContentHandlerFactory()</tt>). However, if one instance
 * enables URL Handlers service, then the factory methods will be invoked. In
 * that case, framework instances that disable the URL Handlers service will
 * simply not provide that services to their contained bundles, while framework
 * instances with the service enabled will.
 * </p>
**/
class URLHandlers implements URLStreamHandlerFactory, ContentHandlerFactory
{
    private static final Class[] CLASS_TYPE = new Class[]{Class.class};

    private static final Class URLHANDLERS_CLASS = URLHandlers.class;

    private static final SecureAction m_secureAction = new SecureAction();

    private static volatile SecurityManagerEx m_sm = null;
    private static volatile URLHandlers m_handler = null;

    // This maps classloaders of URLHandlers in other classloaders to lists of
    // their frameworks.
    private final static ConcurrentHashMap<ClassLoader, List<Object>> m_classloaderToFrameworkLists = new ConcurrentHashMap<ClassLoader, List<Object>>();

    // The list to hold all enabled frameworks registered with this handlers
    private static final CopyOnWriteArrayList<Felix> m_frameworks = new CopyOnWriteArrayList<Felix>();
    private static volatile int m_counter = 0;

    private static final ConcurrentHashMap<String, ContentHandler> m_contentHandlerCache = new ConcurrentHashMap<String, ContentHandler>();
    private static final ConcurrentHashMap<String, URLStreamHandler> m_streamHandlerCache = new ConcurrentHashMap<String, URLStreamHandler>();
    private static final ConcurrentHashMap<String, URL> m_protocolToURL = new ConcurrentHashMap<String, URL>();

    private static volatile URLStreamHandlerFactory m_streamHandlerFactory;
    private static volatile ContentHandlerFactory m_contentHandlerFactory;
    private static final String STREAM_HANDLER_PACKAGE_PROP = "java.protocol.handler.pkgs";
    private static final String DEFAULT_STREAM_HANDLER_PACKAGE = "sun.net.www.protocol|com.ibm.oti.net.www.protocol|gnu.java.net.protocol|wonka.net|com.acunia.wonka.net|org.apache.harmony.luni.internal.net.www.protocol|weblogic.utils|weblogic.net|javax.net.ssl|COM.newmonics.www.protocols";
    private static volatile Object m_rootURLHandlers;

    private static final String m_streamPkgs;
    private static final ConcurrentHashMap<String, URLStreamHandler> m_builtIn = new ConcurrentHashMap<String, URLStreamHandler>();
    private static final boolean m_loaded;

    static
    {
        String pkgs = new SecureAction().getSystemProperty(STREAM_HANDLER_PACKAGE_PROP, "");
        m_streamPkgs = (pkgs.equals(""))
            ? DEFAULT_STREAM_HANDLER_PACKAGE
            : pkgs + "|" + DEFAULT_STREAM_HANDLER_PACKAGE;
        boolean loaded;
        try
        {
            loaded = (null != URLHandlersStreamHandlerProxy.class) &&
                    (null != URLHandlersContentHandlerProxy.class) && (null != URLStreamHandlerService.class) && new URLHandlersStreamHandlerProxy(null, null) != null;
        }
        catch (Throwable e) {
            loaded = false;
        }
        m_loaded = loaded;
    }

    private void init(String protocol, URLStreamHandlerFactory factory)
    {
        try
        {
            // Try to get it directly from the URL class to if possible
            Method getURLStreamHandler = m_secureAction.getDeclaredMethod(URL.class,"getURLStreamHandler", new Class[]{String.class});
            URLStreamHandler handler = (URLStreamHandler) m_secureAction.invoke(getURLStreamHandler, null, new Object[]{protocol});
            addToCache(m_builtIn, protocol, handler);
        }
        catch (Throwable ex)
        {
            // Ignore, this is a best effort
            try
            {
                URLStreamHandler handler = getBuiltInStreamHandler(protocol, factory);
                if (handler != null)
                {
                    URL url = new URL(protocol, null, -1, "", handler);
                    addToCache(m_protocolToURL, protocol, url);
                }
            }
            catch (Throwable ex2)
            {
                // Ignore, this is a best effort (maybe log it or something).
            }
        }
    }

    /**
     * <p>
     * Only one instance of this class is created per classloader
     * and that one instance is registered as the stream and content handler
     * factories for the JVM. Unless, we already register one from a different
     * classloader. In this case we attach to this root.
     * </p>
    **/
    private URLHandlers()
    {
        m_sm = new SecurityManagerEx();
        synchronized (URL.class)
        {
            URLStreamHandlerFactory currentFactory = null;
            try
            {
                currentFactory = (URLStreamHandlerFactory) m_secureAction.swapStaticFieldIfNotClass(URL.class,
                    URLStreamHandlerFactory.class, URLHANDLERS_CLASS, "streamHandlerLock");
            }
            catch (Throwable ex)
            {
                // Ignore, this is a best effort (maybe log it or something)
            }

            init("file", currentFactory);
            init("ftp", currentFactory);
            init("http", currentFactory);
            init("https", currentFactory);


            // Try to preload the jrt handler as we need it from the jvm on java > 8
            if (getFromCache(m_builtIn, "jrt") == null)
            {
                try
                {
                    // Try to get it directly from the URL class to if possible
                    Method getURLStreamHandler = m_secureAction.getDeclaredMethod(URL.class,"getURLStreamHandler", new Class[]{String.class});
                    URLStreamHandler handler = (URLStreamHandler) m_secureAction.invoke(getURLStreamHandler, null, new Object[]{"jrt"});
                    addToCache(m_builtIn, "jrt", handler);
                }
                catch (Throwable ex)
                {
                    // Ignore, this is a best effort and try to load the normal way
                    try
                    {
                        getBuiltInStreamHandler("jrt", currentFactory);
                    }
                    catch (Throwable ex2)
                    {
                        // Ignore, this is a best efforts
                    }
                }
            }

            // Try to preload the jar handler as we need it from the jvm on java > 8
            if (getFromCache(m_builtIn, "jar") == null)
            {
                try
                {
                    // Try to get it directly from the URL class to if possible
                    Method getURLStreamHandler = m_secureAction.getDeclaredMethod(URL.class,"getURLStreamHandler", new Class[]{String.class});
                    URLStreamHandler handler = (URLStreamHandler) m_secureAction.invoke(getURLStreamHandler, null, new Object[]{"jar"});
                    addToCache(m_builtIn, "jar", handler);
                }
                catch (Throwable ex)
                {
                    // Ignore, this is a best effort
                    try
                    {
                        getBuiltInStreamHandler("jar", currentFactory);
                    }
                    catch (Throwable ex2)
                    {
                        // Ignore, this is a best effort (maybe log it or something)
                    }
                }
            }

            if (currentFactory != null)
            {
                try
                {
                    URL.setURLStreamHandlerFactory(currentFactory);
                }
                catch (Throwable ex)
                {
                    // Ignore, this is a best effort (maybe log it or something)
                }
            }

            try
            {
                URL.setURLStreamHandlerFactory(this);
                m_streamHandlerFactory = this;
                m_rootURLHandlers = this;
                // try to flush the cache (gnu/classpath doesn't do it itself)
                try
                {
                    m_secureAction.flush(URL.class, URL.class);
                }
                catch (Throwable t)
                {
                    // Not much we can do
                }
            }
            catch (Error err)
            {
                try
                {
                    // there already is a factory set so try to swap it with ours.
                    m_streamHandlerFactory = (URLStreamHandlerFactory)
                        m_secureAction.swapStaticFieldIfNotClass(URL.class,
                        URLStreamHandlerFactory.class, URLHANDLERS_CLASS, "streamHandlerLock");

                    if (m_streamHandlerFactory == null)
                    {
                        throw err;
                    }
                    if (!m_streamHandlerFactory.getClass().getName().equals(URLHANDLERS_CLASS.getName()))
                    {
                        URL.setURLStreamHandlerFactory(this);
                        m_rootURLHandlers = this;
                    }
                    else if (URLHANDLERS_CLASS != m_streamHandlerFactory.getClass())
                    {
                        try
                        {
                            m_secureAction.invoke(
                                m_secureAction.getDeclaredMethod(m_streamHandlerFactory.getClass(),
                                "registerFrameworkListsForContextSearch",
                                new Class[]{ClassLoader.class, List.class}),
                                m_streamHandlerFactory, new Object[]{ URLHANDLERS_CLASS.getClassLoader(),
                                    m_frameworks });
                            m_rootURLHandlers = m_streamHandlerFactory;
                        }
                        catch (Exception ex)
                        {
                            throw new RuntimeException(ex.getMessage());
                        }
                    }
                }
                catch (Exception e)
                {
                    throw err;
                }
            }

            try
            {
                URLConnection.setContentHandlerFactory(this);
                m_contentHandlerFactory = this;
                // try to flush the cache (gnu/classpath doesn't do it itself)
                try
                {
                    m_secureAction.flush(URLConnection.class, URLConnection.class);
                }
                catch (Throwable t)
                {
                    // Not much we can do
                }
            }
            catch (Error err)
            {
                // there already is a factory set so try to swap it with ours.
                try
                {
                    m_contentHandlerFactory = (ContentHandlerFactory)
                        m_secureAction.swapStaticFieldIfNotClass(
                            URLConnection.class, ContentHandlerFactory.class,
                            URLHANDLERS_CLASS, null);
                    if (m_contentHandlerFactory == null)
                    {
                        throw err;
                    }
                    if (!m_contentHandlerFactory.getClass().getName().equals(
                        URLHANDLERS_CLASS.getName()))
                    {
                        URLConnection.setContentHandlerFactory(this);
                    }
                }
                catch (Exception ex)
                {
                    throw err;
                }
            }
        }
        // are we not the new root?
        if (!((m_streamHandlerFactory == this) || !URLHANDLERS_CLASS.getName().equals(
            m_streamHandlerFactory.getClass().getName())))
        {
            m_sm = null;
            m_protocolToURL.clear();
            m_builtIn.clear();
        }
    }

    static void registerFrameworkListsForContextSearch(ClassLoader index,
        List frameworkLists)
    {
        synchronized (URL.class)
        {
            synchronized (m_classloaderToFrameworkLists)
            {
                m_classloaderToFrameworkLists.put(index, frameworkLists);
            }
        }
    }

    static void unregisterFrameworkListsForContextSearch(ClassLoader index)
    {
        synchronized (URL.class)
        {
            synchronized (m_classloaderToFrameworkLists)
            {
                m_classloaderToFrameworkLists.remove(index);
                if (m_classloaderToFrameworkLists.isEmpty() )
                {
                    synchronized (m_frameworks)
                    {
                        if (m_frameworks.isEmpty())
                        {
                            try
                            {
                                m_secureAction.swapStaticFieldIfNotClass(URL.class,
                                    URLStreamHandlerFactory.class, null, "streamHandlerLock");
                            }
                            catch (Exception ex)
                            {
                                // TODO log this
                                ex.printStackTrace();
                            }

                            if (m_streamHandlerFactory.getClass() != URLHANDLERS_CLASS)
                            {
                                URL.setURLStreamHandlerFactory(m_streamHandlerFactory);
                            }
                            try
                            {
                                m_secureAction.swapStaticFieldIfNotClass(
                                    URLConnection.class, ContentHandlerFactory.class,
                                    null, null);
                            }
                            catch (Exception ex)
                            {
                                // TODO log this
                                ex.printStackTrace();
                            }

                            if (m_contentHandlerFactory.getClass() != URLHANDLERS_CLASS)
                            {
                                URLConnection.setContentHandlerFactory(m_contentHandlerFactory);
                            }
                        }
                    }
                }
            }
        }
    }

    private URLStreamHandler getBuiltInStreamHandler(String protocol, URLStreamHandlerFactory factory)
    {
        URLStreamHandler handler = getFromCache(m_builtIn, protocol);

        if (handler != null)
        {
            return handler;
        }

        if (factory != null)
        {
            handler = factory.createURLStreamHandler(protocol);
        }

        if (handler == null)
        {
            // Check for built-in handlers for the mime type.
            // Iterate over built-in packages.
            handler = loadBuiltInStreamHandler(protocol, null);
        }

        if (handler == null)
        {
            handler = loadBuiltInStreamHandler(protocol, ClassLoader.getSystemClassLoader());
        }

        return addToCache(m_builtIn, protocol, handler);
    }

    private URLStreamHandler loadBuiltInStreamHandler(String protocol, ClassLoader classLoader) {
        StringTokenizer pkgTok = new StringTokenizer(m_streamPkgs, "| ");
        while (pkgTok.hasMoreTokens())
        {
            String pkg = pkgTok.nextToken().trim();
            String className = pkg + "." + protocol + ".Handler";
            try
            {
                // If a built-in handler is found then cache and return it
                Class handler = m_secureAction.forName(className, classLoader);
                if (handler != null)
                {
                    return (URLStreamHandler) handler.newInstance();
                }
            }
            catch (Throwable ex)
            {
                // This could be a class not found exception or an
                // instantiation exception, not much we can do in either
                // case other than ignore it.
            }
        }
        // This is a workaround for android - Starting with 4.1 the built-in core handler
        // are not following the normal naming package schema :-(
        String androidHandler = null;
        if ("file".equalsIgnoreCase(protocol))
        {
            androidHandler = "libcore.net.url.FileHandler";
        }
        else if ("ftp".equalsIgnoreCase(protocol))
        {
            androidHandler = "libcore.net.url.FtpHandler";
        }
        else if ("http".equalsIgnoreCase(protocol))
        {
            androidHandler = "libcore.net.http.HttpHandler";
        }
        else if ("https".equalsIgnoreCase(protocol))
        {
            androidHandler = "libcore.net.http.HttpsHandler";
        }
        else if ("jar".equalsIgnoreCase(protocol))
        {
            androidHandler = "libcore.net.url.JarHandler";
        }
        if (androidHandler != null)
        {
            try
            {
                // If a built-in handler is found then cache and return it
                Class handler = m_secureAction.forName(androidHandler, classLoader);
                if (handler != null)
                {
                    return (URLStreamHandler) handler.newInstance();
                }
            }
            catch (Throwable ex)
            {
                // This could be a class not found exception or an
                // instantiation exception, not much we can do in either
                // case other than ignore it.
            }
        }
        return null;
    }

    /**
     * <p>
     * This is a method implementation for the <tt>URLStreamHandlerFactory</tt>
     * interface. It simply creates a stream handler proxy object for the
     * specified protocol. It caches the returned proxy; therefore, subsequent
     * requests for the same protocol will receive the same handler proxy.
     * </p>
     * @param protocol the protocol for which a stream handler should be returned.
     * @return a stream handler proxy for the specified protocol.
    **/
    public URLStreamHandler createURLStreamHandler(String protocol)
    {
        // See if there is a cached stream handler.
        // IMPLEMENTATION NOTE: Caching is not strictly necessary for
        // stream handlers since the Java runtime caches them. Caching is
        // performed for code consistency between stream and content
        // handlers and also because caching behavior may not be guaranteed
        // across different JRE implementations.
        URLStreamHandler handler = getFromCache(m_streamHandlerCache, protocol);

        if (handler != null)
        {
            return handler;
        }
        // If this is the framework's "bundle:" protocol, then return
        // a handler for that immediately, since no one else can be
        // allowed to deal with it.
        if (protocol.equals(FelixConstants.BUNDLE_URL_PROTOCOL))
        {
            return new URLHandlersBundleStreamHandler(getFrameworkFromContext(), m_secureAction);
        }

        handler = getBuiltInStreamHandler(protocol,
            (m_streamHandlerFactory != this) ? m_streamHandlerFactory : null);

        if (handler == null && isJVM(protocol))
        {
            return null;
        }
        // If built-in content handler, then create a proxy handler.
        return addToCache(m_streamHandlerCache, protocol,
            URLHandlersStreamHandlerProxy.wrap(protocol, m_secureAction,
                handler, getFromCache(m_protocolToURL, protocol)));
    }

    private boolean isJVM(String protocol)
    {
        return protocol.equals("file") ||
                protocol.equals("ftp") ||
                protocol.equals("http") ||
                protocol.equals("https") ||
                protocol.equals("jar") ||
                protocol.equals("jmod") ||
                protocol.equals("mailto") ||
                protocol.equals("jrt");
    }

    /**
     * <p>
     * This is a method implementation for the <tt>ContentHandlerFactory</tt>
     * interface. It simply creates a content handler proxy object for the
     * specified mime type. It caches the returned proxy; therefore, subsequent
     * requests for the same content type will receive the same handler proxy.
     * </p>
     * @param mimeType the mime type for which a content handler should be returned.
     * @return a content handler proxy for the specified mime type.
    **/
    public ContentHandler createContentHandler(String mimeType)
    {
        // See if there is a cached stream handler.
        // IMPLEMENTATION NOTE: Caching is not strictly necessary for
        // stream handlers since the Java runtime caches them. Caching is
        // performed for code consistency between stream and content
        // handlers and also because caching behavior may not be guaranteed
        // across different JRE implementations.
        ContentHandler handler = getFromCache(m_contentHandlerCache, mimeType);

        if (handler != null)
        {
            return handler;
        }

        return addToCache(m_contentHandlerCache, mimeType,
            new URLHandlersContentHandlerProxy(mimeType, m_secureAction,
            (m_contentHandlerFactory != this) ? m_contentHandlerFactory : null));
    }

    private static <K,V> V addToCache(ConcurrentHashMap<K,V> cache, K key, V value)
    {
        return key != null && value != null ? putIfAbsentAndReturn(cache, key, value) : null;
    }

    private static <K,V> V getFromCache(ConcurrentHashMap<K,V> cache, K key)
    {
        return key != null ? cache.get(key) : null;
    }

    /**
     * <p>
     * Static method that adds a framework instance to the centralized
     * instance registry.
     * </p>
     * @param framework the framework instance to be added to the instance
     *        registry.
     * @param enable a flag indicating whether or not the framework wants to
     *        enable the URL Handlers service.
    **/
    public static void registerFrameworkInstance(Felix framework, boolean enable)
    {
        boolean register = false;
        synchronized (m_frameworks)
        {
            // If the URL Handlers service is not going to be enabled,
            // then return immediately.
            if (enable)
            {
                // We need to create an instance if this is the first
                // time this method is called, which will set the handler
                // factories.
                if (m_handler == null )
                {
                    register = true;
                }
                else
                {
                    m_frameworks.add(framework);
                    m_counter++;
                }
            }
            else
            {
                m_counter++;
            }
        }
        if (register)
        {
            synchronized (URL.class)
            {
                synchronized (m_classloaderToFrameworkLists)
                {
                    synchronized (m_frameworks)
                    {
                        if (m_handler == null )
                        {
                            m_handler = new URLHandlers();
                        }
                        m_frameworks.add(framework);
                        m_counter++;
                    }
                }
            }
        }
    }

    /**
     * <p>
     * Static method that removes a framework instance from the centralized
     * instance registry.
     * </p>
     * @param framework the framework instance to be removed from the instance
     *        registry.
    **/
    public static void unregisterFrameworkInstance(Object framework)
    {
        boolean unregister = false;
        synchronized (m_frameworks)
        {
            if (m_frameworks.contains(framework))
            {
                if (m_frameworks.size() == 1 && m_handler != null)
                {
                    unregister = true;
                }
                else
                {
                    m_frameworks.remove(framework);
                    m_counter--;
                }
            }
            else
            {
                m_counter--;
            }
        }
        if (unregister)
        {
            synchronized (URL.class)
            {
                synchronized (m_classloaderToFrameworkLists)
                {
                    synchronized (m_frameworks)
                    {
                        m_frameworks.remove(framework);
                        m_counter--;
                        if (m_frameworks.isEmpty() && m_handler != null)
                        {

                            m_handler = null;
                            try
                            {
                                m_secureAction.invoke(m_secureAction.getDeclaredMethod(
                                    m_rootURLHandlers.getClass(),
                                    "unregisterFrameworkListsForContextSearch",
                                    new Class[]{ ClassLoader.class}),
                                    m_rootURLHandlers,
                                    new Object[] {URLHANDLERS_CLASS.getClassLoader()});
                            }
                            catch (Exception e)
                            {
                                // This should not happen
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * <p>
     * This method returns the system bundle context for the caller.
     * It determines the appropriate system bundle by retrieving the
     * class call stack and find the first class that is loaded from
     * a bundle. It then checks to see which of the registered framework
     * instances owns the class and returns its system bundle context.
     * </p>
     * @return the system bundle context associated with the caller or
     *         <tt>null</tt> if no associated framework was found.
    **/
    public static Object getFrameworkFromContext()
    {
        // This is a hack. The idea is to return the only registered framework quickly
        int attempts = 0;
        while (m_classloaderToFrameworkLists.isEmpty() && (m_counter == 1) && (m_frameworks.size() == 1))
        {
            Object framework = m_frameworks.get(0);

            if (framework != null)
            {
                return framework;
            }
            else if (attempts++ > 3)
            {
                break;
            }
        }

        // get the current class call stack.
        Class[] stack = m_sm.getClassContext();
        // Find the first class that is loaded from a bundle.
        Class targetClass = null;
        ClassLoader targetClassLoader = null;
        for (int i = 0; i < stack.length; i++)
        {
            ClassLoader classLoader = m_secureAction.getClassLoader(stack[i]);
			if (classLoader != null)
            {
                String name = classLoader.getClass().getName();
                if (name.startsWith("org.apache.felix.framework.ModuleImpl$ModuleClassLoader")
                    || name.equals("org.apache.felix.framework.searchpolicy.ContentClassLoader")
                    || name.startsWith("org.apache.felix.framework.BundleWiringImpl$BundleClassLoader"))
                {
                    targetClass = stack[i];
                    targetClassLoader = classLoader;
                    break;
                }
            }
        }

        // If we found a class loaded from a bundle, then iterate
        // over the framework instances and see which framework owns
        // the bundle that loaded the class.
        if (targetClass != null)
        {
            ClassLoader index = m_secureAction.getClassLoader(targetClassLoader.getClass());

            List frameworks = (List) m_classloaderToFrameworkLists.get(index);

            if ((frameworks == null) && (index == URLHANDLERS_CLASS.getClassLoader()))
            {
                frameworks = m_frameworks;
            }
            if (frameworks != null)
            {
                // Check the registry of framework instances
                for (Object framework : frameworks)
                {
                    try
                    {
                        if (m_secureAction.invoke(
                            m_secureAction.getDeclaredMethod(framework.getClass(),
                            "getBundle", CLASS_TYPE),
                            framework, new Object[]{targetClass}) != null)
                        {
                            return framework;
                        }
                    }
                    catch (Exception ex)
                    {
                        // This should not happen but if it does there is
                        // not much we can do other then ignore it.
                        // Maybe log this or something.
                        ex.printStackTrace();
                    }
                }
            }
        }
        return null;
    }

    public static Object getFrameworkFromContext(String uuid)
    {
        if (uuid != null)
        {
            for (Felix framework : m_frameworks)
            {
                if (uuid.equals(framework._getProperty(Constants.FRAMEWORK_UUID)))
                {
                    return framework;
                }
            }
            for (List frameworks : m_classloaderToFrameworkLists.values())
            {
                for (Object framework : frameworks)
                {
                    try
                    {
                        if (uuid.equals(
                                m_secureAction.invoke(
                                        m_secureAction.getDeclaredMethod(framework.getClass(),"getProperty", new Class[]{String.class}),
                                        framework, new Object[]{Constants.FRAMEWORK_UUID})))
                        {
                            return framework;
                        }
                    }
                    catch (Exception ex)
                    {
                        // This should not happen but if it does there is
                        // not much we can do other then ignore it.
                        // Maybe log this or something.
                        ex.printStackTrace();
                    }
                }
            }
        }
        return getFrameworkFromContext();
    }
}

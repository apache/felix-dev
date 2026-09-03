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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


import org.apache.felix.framework.util.SecureAction;
import org.apache.felix.framework.util.SecurityManagerEx;
import org.osgi.framework.Constants;

/**
 * <p>
 * Keeps the registry of framework instances running in this JVM, and answers which
 * of them a caller belongs to.
 * </p>
 * <p>
 * This class used to be the singleton stream and content handler factory for every
 * framework instance in the JVM, installing itself into <tt>java.net.URL</tt> and
 * <tt>java.net.URLConnection</tt> and multiplexing between instances. Since
 * FELIX-6759 that role belongs to the plurl router: each framework registers its own
 * {@link PlurlURLHandlers} with plurl, which asks each registered factory whether a
 * calling class or a URL belongs to it. Installing the JVM factory ourselves required
 * reflectively clearing private static fields of <tt>java.net.URL</tt>, which no
 * longer works without <tt>--add-opens</tt>, and it left the factories occupied
 * before plurl could take them. What remains here is the instance registry, which
 * {@code URLHandlersBundleStreamHandler} still uses to find the framework that owns a
 * caller when a handler is not bound to one.
 * </p>
 * <p>
 * It is possible to disable the URL Handlers service by setting the
 * <tt>framework.service.urlhandlers</tt> configuration property to <tt>false</tt>.
 * A framework instance that disables it simply contributes no URL handlers, while
 * instances with the service enabled still provide them to their own bundles.
 * </p>
**/
class URLHandlers
{
    private static final Class<?>[] CLASS_TYPE = new Class[]{Class.class};

    private static final Class<?> URLHANDLERS_CLASS = URLHandlers.class;

    private static final SecureAction m_secureAction = new SecureAction();

    // Initialised eagerly: the constructor that used to set this up is no longer
    // invoked, since plurl installs the JVM factories instead of this class.
    private static volatile SecurityManagerEx m_sm = new SecurityManagerEx();

    // The list to hold all enabled frameworks registered with this handlers
    private static final CopyOnWriteArrayList<Felix> m_frameworks = new CopyOnWriteArrayList<>();
    private static volatile int m_counter = 0;

    // The plurl registration per framework instance, so it can be uninstalled again
    // when the framework stops.
    private static final Map<Felix, PlurlURLHandlers> m_plurlHandlers =
        new ConcurrentHashMap<>();














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
        synchronized (m_frameworks)
        {
            if (enable)
            {
                m_frameworks.add(framework);
            }
            m_counter++;
        }

        if (enable)
        {
            // FELIX-6759: this class no longer installs itself as the JVM stream and
            // content handler factory. Doing so meant reflectively clearing the
            // java.net.URL static fields, and it left the JVM factories occupied by
            // the time plurl ran, forcing plurl into deep reflection into java.net
            // and failing without --add-opens. Each framework registers its own
            // factory with the plurl router instead, on a clean JVM, through the
            // supported URL.setURLStreamHandlerFactory API.
            registerWithPlurl(framework);
        }
    }

    /**
     * Registers the given framework's URL handling with the plurl router.
     * <p>
     * Failing to register must not prevent the framework from starting. As in
     * Equinox, the consequence is simply that this framework instance contributes no
     * URL handlers; there is deliberately no fallback to swapping the java.net.URL
     * singleton fields, since removing that is the point of using plurl.
     */
    private static void registerWithPlurl(Felix framework)
    {
        PlurlURLHandlers handlers =
            PlurlURLHandlers.install(framework, m_secureAction);
        if (handlers != null)
        {
            m_plurlHandlers.put(framework, handlers);
        }
    }

    /**
     * The plurl registration for the given framework, or <tt>null</tt> if it is not
     * registered. Package private for testing.
     */
    static PlurlURLHandlers getPlurlHandlers(Felix framework)
    {
        return m_plurlHandlers.get(framework);
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
        if (framework instanceof Felix)
        {
            PlurlURLHandlers handlers = m_plurlHandlers.remove(framework);
            if (handlers != null)
            {
                handlers.uninstall();
            }
        }

        synchronized (m_frameworks)
        {
            m_frameworks.remove(framework);
            m_counter--;
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
        while ((m_counter == 1) && (m_frameworks.size() == 1))
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
        Class<?>[] stack = m_sm.getClassContext();
        // Find the first class that is loaded from a bundle.
        Class<?> targetClass = null;
        ClassLoader targetClassLoader = null;
        for (Class<?> element : stack) {
            ClassLoader classLoader = m_secureAction.getClassLoader(element);
			if (classLoader != null)
            {
                String name = classLoader.getClass().getName();
                if (name.startsWith("org.apache.felix.framework.ModuleImpl$ModuleClassLoader")
                    || name.equals("org.apache.felix.framework.searchpolicy.ContentClassLoader")
                    || name.startsWith("org.apache.felix.framework.BundleWiringImpl$BundleClassLoader"))
                {
                    targetClass = element;
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

            // Only classes loaded by a bundle of a framework from this copy of the
            // framework can be ours; another copy in the JVM routes through its own
            // plurl factory.
            List<?> frameworks =
                (index == URLHANDLERS_CLASS.getClassLoader()) ? m_frameworks : null;

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
        }
        return getFrameworkFromContext();
    }
}

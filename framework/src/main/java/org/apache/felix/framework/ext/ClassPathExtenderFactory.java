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
package org.apache.felix.framework.ext;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.felix.framework.util.SecureAction;

public interface ClassPathExtenderFactory
{
    interface ClassPathExtender
    {
        void add(File file) throws Exception;
    }

    ClassPathExtender getExtender(ClassLoader loader);

    final class DefaultClassLoaderExtender implements ClassPathExtenderFactory, ClassPathExtenderFactory.ClassPathExtender
    {
        private static final Method m_append;
        private static final ClassLoader m_app;
        private static final Method m_addURL;
        private final ClassLoader m_loader;

        // These are only looked up here, not made accessible. Looking a method up
        // needs no access, while setAccessible on a java.base member does, and on a
        // JVM where the package is not open that is served by defining an accessor
        // inside java.base with sun.misc.Unsafe -- which JDK 25 reports as a
        // terminally deprecated call. Since this runs whenever a framework instance
        // is created, doing it here made every start pay for it, and print that
        // warning, even when no extension bundle is ever installed. The access is
        // requested in add(File) instead, at the point it is actually used.
        static
        {
            ClassLoader app = ClassLoader.getSystemClassLoader();

            Method append = null;

            while (app != null)
            {
                try
                {
                    append = app.getClass().getDeclaredMethod("appendToClassPathForInstrumentation", String.class);
                    break;
                }
                catch (Throwable e)
                {
                    append = null;
                    try
                    {
                        app = app.getParent();
                    }
                    catch (Exception ex)
                    {
                        app = null;
                    }
                }
            }
            m_append = append;
            m_app = app;

            Method addURL;

            try
            {
                addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            }
            catch (Throwable e)
            {
                addURL = null;
            }

            m_addURL = addURL;
        }

        public DefaultClassLoaderExtender()
        {
            this(null);
        }

        private DefaultClassLoaderExtender(ClassLoader loader)
        {
            m_loader = loader;
        }

        @Override
        public ClassPathExtender getExtender(ClassLoader loader)
        {
            if (m_append != null)
            {
                ClassLoader current = ClassLoader.getSystemClassLoader();

                while (current != null)
                {
                    if (loader == current)
                    {
                        return this;
                    }
                    current = current.getParent();
                }
            }
            if (m_addURL != null && loader instanceof URLClassLoader)
            {
                return new DefaultClassLoaderExtender(loader);
            }
            return null;
        }

        @Override
        public void add(final File file) throws Exception
        {
            ClassLoader loader;
            if (m_loader != null)
            {
                loader = m_loader;
                synchronized (m_loader)
                {
                    new SecureAction().setAccesssible(m_addURL);
                    m_addURL.invoke(m_loader, file.getCanonicalFile().toURI().toURL());
                }
            }
            else
            {
                loader = m_app;
                synchronized (m_app)
                {
                    new SecureAction().setAccesssible(m_append);
                    m_append.invoke(m_app, file.getCanonicalFile().getPath());
                }
            }

            try
            {
                for (int i = 0; i < 1000; i++)
                {
                    loader.loadClass("flushFelixExtensionSubsystem" + i + ".class");
                }
            }
            catch (Exception ex) {
                // This is expected, we need to init the url subsystem
            }
        }
    }
}

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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.delegation.DelegationHook;
import org.osgi.framework.wiring.BundleWiring;

/**
 * Calls the registered {@link DelegationHook} services on behalf of a
 * {@link BundleWiringImpl}. Hooks are called in service ranking order without
 * holding any framework lock. A hook that throws is reported as a
 * {@link FrameworkEvent#ERROR} and is not called again.
 */
class DelegationHookSupport
{
    private final Felix m_felix;

    DelegationHookSupport(Felix felix)
    {
        m_felix = felix;
    }

    boolean hasHooks()
    {
        return !hooks().isEmpty();
    }

    Class<?> findClass(BundleWiringImpl wiring, String name)
    {
        for (ServiceReference<DelegationHook> sr : hooks())
        {
            DelegationHook hook = m_felix.getService(m_felix, sr, false);
            if (hook == null)
            {
                continue;
            }
            try
            {
                Class<?> clazz = hook.findClass(wiring, name);
                if (clazz != null)
                {
                    ClassLoader cl = clazz.getClassLoader();
                    if (cl instanceof BundleWiringImpl.BundleClassLoader)
                    {
                        BundleWiringImpl provider =
                            ((BundleWiringImpl.BundleClassLoader) cl).getBundleWiring();
                        m_felix.getDependencies().addDelegationDependent(
                            provider.getRevision(), wiring.getRevision());
                    }
                    return clazz;
                }
            }
            catch (ClassNotFoundException ex)
            {
                // The hook knows the class but cannot load it, treat as not provided.
            }
            catch (Throwable th)
            {
                failed(sr, th);
            }
            finally
            {
                m_felix.ungetService(m_felix, sr, null);
            }
        }
        return null;
    }

    URL findResource(BundleWiringImpl wiring, String name)
    {
        for (ServiceReference<DelegationHook> sr : hooks())
        {
            DelegationHook hook = m_felix.getService(m_felix, sr, false);
            if (hook == null)
            {
                continue;
            }
            try
            {
                URL url = hook.findResource(wiring, name);
                if (url != null)
                {
                    return url;
                }
            }
            catch (Throwable th)
            {
                failed(sr, th);
            }
            finally
            {
                m_felix.ungetService(m_felix, sr, null);
            }
        }
        return null;
    }

    List<URL> findResources(BundleWiring wiring, String name)
    {
        List<URL> result = new ArrayList<>();
        for (ServiceReference<DelegationHook> sr : hooks())
        {
            DelegationHook hook = m_felix.getService(m_felix, sr, false);
            if (hook == null)
            {
                continue;
            }
            try
            {
                Collection<URL> urls = hook.findResources(wiring, name);
                if (urls != null)
                {
                    result.addAll(urls);
                }
            }
            catch (Throwable th)
            {
                failed(sr, th);
            }
            finally
            {
                m_felix.ungetService(m_felix, sr, null);
            }
        }
        return result;
    }

    private List<ServiceReference<DelegationHook>> hooks()
    {
        List<ServiceReference<DelegationHook>> result = new ArrayList<>();
        for (ServiceReference<DelegationHook> sr :
            m_felix.getHookRegistry().getHooks(DelegationHook.class))
        {
            if (!m_felix.getHookRegistry().isHookBlackListed(sr))
            {
                result.add(sr);
            }
        }
        return result;
    }

    private void failed(ServiceReference<DelegationHook> sr, Throwable th)
    {
        m_felix.getHookRegistry().blackListHook(sr);
        m_felix.fireFrameworkEvent(FrameworkEvent.ERROR, sr.getBundle(), th);
    }
}

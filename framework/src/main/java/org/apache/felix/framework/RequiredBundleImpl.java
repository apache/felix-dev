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

import java.util.Set;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.service.packageadmin.RequiredBundle;

class RequiredBundleImpl implements RequiredBundle
{
    private final Felix m_felix;
    private final BundleImpl m_bundle;
    private volatile String m_toString = null;
    private volatile String m_versionString = null;

    public RequiredBundleImpl(Felix felix, BundleImpl bundle)
    {
        m_felix = felix;
        m_bundle = bundle;
    }

    @Override
	public String getSymbolicName()
    {
        return m_bundle.getSymbolicName();
    }

    @Override
	public Bundle getBundle()
    {
        return m_bundle;
    }

    @Override
	public Bundle[] getRequiringBundles()
    {
        // If the package is stale, then return null per the spec.
        if (m_bundle.isStale())
        {
            return null;
        }
        Set<Bundle> set = m_felix.getRequiringBundles(m_bundle);
        return set.toArray(new Bundle[set.size()]);
    }

    @Override
	public Version getVersion()
    {
        return m_bundle.getVersion();
    }

    @Override
	public boolean isRemovalPending()
    {
        return m_bundle.isRemovalPending();
    }

    @Override
	public String toString()
    {
        if (m_toString == null)
        {
            m_toString = m_bundle.getSymbolicName()
                + "; version=" + m_bundle.getVersion();
        }
        return m_toString;
    }
}
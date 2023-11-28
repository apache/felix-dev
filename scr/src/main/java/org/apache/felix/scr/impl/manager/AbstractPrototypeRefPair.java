/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.felix.scr.impl.manager;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.felix.scr.impl.inject.RefPair;
import org.apache.felix.scr.impl.inject.ScrComponentContext;
import org.apache.felix.scr.impl.logger.InternalLogger.Level;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * @version $Rev$ $Date$
 */
public abstract class AbstractPrototypeRefPair<S, T> extends RefPair<S, T>
{
    public AbstractPrototypeRefPair( ServiceReference<T> ref )
    {
        super(ref);
    }

    @Override
    public abstract T getServiceObject(ScrComponentContext key);

    @Override
    public abstract boolean setServiceObject(ScrComponentContext key, T serviceObject);

    protected abstract T remove(ScrComponentContext key);

    protected abstract Collection<Entry<ScrComponentContext, T>> clearEntries();

    @Override
    public final T ungetServiceObject(ScrComponentContext key)
    {
        if ( key == null )
        {
            Collection<Map.Entry<ScrComponentContext, T>> keys = clearEntries();
            for (Map.Entry<ScrComponentContext, T> e : keys)
            {
                doUngetService( e.getKey(), e.getValue() );
            }
            return null ;
        }
        T service = remove( key );
        if(service != null) {
        	doUngetService( key, service );
        }
		return service;
    }

    @Override
    public final void ungetServiceObjects(BundleContext bundleContext) {
        ungetServiceObject(null);
    }

    @Override
    public abstract String toString();

    @Override
    public final boolean getServiceObject(ScrComponentContext key, BundleContext context)
    {
        final T service = key.getComponentServiceObjectsHelper().getPrototypeRefInstance(this.getRef());
        if ( service == null )
        {
            markFailed();
            key.getLogger().log(
                Level.WARN,
                 "Could not get service from serviceobjects for ref {0}", null, getRef() );
            return false;
        }
        if (!setServiceObject(key, service))
        {
            // Another thread got the service before, so unget our
        	doUngetService( key, service );
        }
        return true;
    }

    private void doUngetService(ScrComponentContext key, final T service) {
		try
		{
			key.getComponentServiceObjectsHelper().getServiceObjects(getRef()).ungetService( service );
		}
		catch ( final IllegalStateException ise )
		{
			// ignore
		}
	}
}

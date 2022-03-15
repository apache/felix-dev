/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.http.base.internal.handler;

import java.util.EventListener;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.runtime.ListenerInfo;
import org.osgi.framework.BundleContext;
import org.osgi.service.servlet.whiteboard.runtime.dto.DTOConstants;

/**
 * The listener handler handles the initialization and destruction of listener
 * objects.
 */
public class ListenerHandler implements Comparable<ListenerHandler>
{
    private final long contextServiceId;

    private final ListenerInfo listenerInfo;

    private final ExtServletContext context;

    private final BundleContext bundleContext;

    private volatile EventListener listener;

    protected volatile int useCount;

    public ListenerHandler(final long contextServiceId,
            final ExtServletContext context,
            final ListenerInfo listenerInfo,
            final BundleContext bundleContext)
    {
        this.contextServiceId = contextServiceId;
        this.context = context;
        this.listenerInfo = listenerInfo;
        this.bundleContext = bundleContext;
    }

    @Override
    public int compareTo(final ListenerHandler other)
    {
        return this.listenerInfo.compareTo(other.listenerInfo);
    }

    public ExtServletContext getContext()
    {
        return this.context;
    }

    public long getContextServiceId()
    {
        return this.contextServiceId;
    }

    public EventListener getListener()
    {
        return listener;
    }

    public ListenerInfo getListenerInfo()
    {
        return this.listenerInfo;
    }

    public boolean destroy()
    {
        final EventListener l = this.listener;
        if ( l != null )
        {
            this.useCount--;
            if ( this.useCount == 0 )
            {
                this.listener = null;

                this.getListenerInfo().ungetService(this.bundleContext, l);

                return true;
            }
        }
        return false;
    }

    public boolean dispose()
    {
        // fully destroy the listener
        this.useCount = 1;
        return this.destroy();
    }

    /**
     * Initialize the object
     * @return {code -1} on success, a failure reason according to {@link DTOConstants} otherwise.
     */
    public int init()
    {
        if ( this.useCount > 0 )
        {
            this.useCount++;
            return -1;
        }

        this.listener = getListenerInfo().getService(this.bundleContext);

        final int reason;
        if (this.listener == null)
        {
            reason = DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE;
        }
        else
        {
            this.useCount++;
            reason = -1;
        }

        return reason;
    }

    @Override
    public int hashCode()
    {
        return 31 + listenerInfo.hashCode();
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || getClass() != obj.getClass() )
        {
            return false;
        }
        final ListenerHandler other = (ListenerHandler) obj;
        return listenerInfo.equals(other.listenerInfo);
    }
}

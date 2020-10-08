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
import org.apache.felix.http.base.internal.util.ServiceUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Listener handler for listeners registered through the http whiteboard.
 */
public final class WhiteboardListenerHandler extends ListenerHandler
{
    private final BundleContext bundleContext;

    public WhiteboardListenerHandler(final long contextServiceId,
            final ExtServletContext context,
            final ListenerInfo listenerInfo,
            final BundleContext bundleContext)
    {
        super(contextServiceId, context, listenerInfo);
        this.bundleContext = bundleContext;
    }

    @Override
    public int init()
    {
        if ( this.useCount > 0 )
        {
            this.useCount++;
            return -1;
        }

        final ServiceReference<EventListener> serviceReference = getListenerInfo().getServiceReference();
        this.setListener(ServiceUtils.safeGetServiceObjects(this.bundleContext, serviceReference));

        final int reason = super.init();
        if ( reason != -1 )
        {
            ServiceUtils.safeUngetServiceObjects(this.bundleContext, serviceReference, this.getListener());
            this.setListener(null);
        }
        return reason;
    }

    @Override
    public boolean destroy()
    {
        final EventListener l = this.getListener();
        if ( l != null )
        {
            if ( super.destroy() )
            {
                ServiceUtils.safeUngetServiceObjects(this.bundleContext,
                        getListenerInfo().getServiceReference(), l);

                return true;
            }
        }
        return false;
    }
}

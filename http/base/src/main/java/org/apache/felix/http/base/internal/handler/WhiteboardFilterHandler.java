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

import javax.servlet.Filter;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.runtime.FilterInfo;
import org.apache.felix.http.base.internal.util.ServiceUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Filter holder for filters registered through the http whiteboard.
 */
public final class WhiteboardFilterHandler extends FilterHandler
{
    private final BundleContext bundleContext;

    public WhiteboardFilterHandler(final long contextServiceId,
            final ExtServletContext context,
            final FilterInfo filterInfo,
            final BundleContext bundleContext)
    {
        super(contextServiceId, context, filterInfo);
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

        final ServiceReference<Filter> serviceReference = getFilterInfo().getServiceReference();
        this.setFilter(ServiceUtils.safeGetServiceObjects(this.bundleContext, serviceReference));

        final int reason = super.init();
        if ( reason != -1 )
        {
            ServiceUtils.safeUngetServiceObjects(this.bundleContext, serviceReference, this.getFilter());
            this.setFilter(null);
        }
        return reason;
    }

    @Override
    public boolean destroy()
    {
        final Filter f = this.getFilter();
        if ( f != null )
        {
            if ( super.destroy() )
            {
                ServiceUtils.safeUngetServiceObjects(this.bundleContext,
                        getFilterInfo().getServiceReference(), f);

                return true;
            }
        }
        return false;
    }
}

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
package org.apache.felix.http.proxy;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.http.HttpServlet;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

/**
 * @deprecated
 */
@Deprecated
public final class DispatcherTracker
    extends ServiceTracker
{
    final static String DEFAULT_FILTER = "(http.felix.dispatcher=*)";

    private final ServletConfig config;
    private HttpServlet dispatcher;

    public DispatcherTracker(BundleContext context, String filter, ServletConfig config)
        throws Exception
    {
        super(context, createFilter(context, filter), null);
        this.config = config;
    }

    public HttpServlet getDispatcher()
    {
        return this.dispatcher;
    }

    @Override
    public Object addingService(ServiceReference ref)
    {
        Object service = super.addingService(ref);
        if (service instanceof HttpServlet) {
            setDispatcher((HttpServlet)service);
        }

        return service;
    }

    @Override
    public void removedService(ServiceReference ref, Object service)
    {
        if (service instanceof HttpServlet) {
            setDispatcher(null);
        }

        super.removedService(ref, service);
    }

    private void log(String message, Throwable cause)
    {
        this.config.getServletContext().log(message, cause);
    }

    private void setDispatcher(HttpServlet dispatcher)
    {
        destroyDispatcher();
        this.dispatcher = dispatcher;
        initDispatcher();
    }

    private void destroyDispatcher()
    {
        if (this.dispatcher == null) {
            return;
        }

        this.dispatcher.destroy();
        this.dispatcher = null;
    }

    private void initDispatcher()
    {
        if (this.dispatcher == null) {
            return;
        }

        try {
            this.dispatcher.init(this.config);
        } catch (Exception e) {
            log("Failed to initialize dispatcher", e);
        }
    }

    private static Filter createFilter(BundleContext context, String filter)
        throws Exception
    {
        StringBuffer str = new StringBuffer();
        str.append("(&(").append(Constants.OBJECTCLASS).append("=");
        str.append(HttpServlet.class.getName()).append(")");
        str.append(filter != null ? filter : DEFAULT_FILTER).append(")");
        return context.createFilter(str.toString());
    }
}

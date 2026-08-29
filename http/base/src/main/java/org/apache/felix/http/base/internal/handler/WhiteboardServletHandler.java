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

import java.io.IOException;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.osgi.framework.BundleContext;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Servlet handler for servlets registered through the http whiteboard.
 */
public class WhiteboardServletHandler extends ServletHandler
{
    private final BundleContext bundleContext;

    private volatile WebSocketHandler webSocketHandler;

    public WhiteboardServletHandler(final long contextServiceId,
            final ExtServletContext context,
            final ServletInfo servletInfo,
            final BundleContext contextBundleContext)
    {
        super(contextServiceId, context, servletInfo);
        this.bundleContext = contextBundleContext;
    }

    @Override
    public int init()
    {
        if ( this.useCount > 0 )
        {
            this.useCount++;
            return -1;
        }

        this.setServlet(this.getServletInfo().getService(this.bundleContext));

        if (WebSocketHandler.isJettyWebSocketServlet(this.getServlet())) {
            if (this.webSocketHandler == null) {
                this.webSocketHandler = new WebSocketHandler(this);
            }
            if (!webSocketHandler.shouldInit()) {
                // do nothing, delay init until first service call
                return -1;
            }
        }

        final int reason = super.init();
        if ( reason != -1 )
        {
            this.getServletInfo().ungetService(this.bundleContext, this.getServlet());
            this.setServlet(null);
        }
        return reason;
    }

    @Override
    public boolean destroy()
    {
        final Servlet s = this.getServlet();
        if ( s != null )
        {
            if ( this.webSocketHandler != null && !this.webSocketHandler.shouldDestroy() ) {
                return false;
            }
            this.webSocketHandler = null;
            if ( super.destroy() )
            {
                this.getServletInfo().ungetService(this.bundleContext, this.getServlet());

                return true;
            }
        }
        return false;
    }

    @Override
    public void handle(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        if ( this.webSocketHandler != null ) {
            this.webSocketHandler.lazyInit();
        }
        super.handle(req, res);
    }
}

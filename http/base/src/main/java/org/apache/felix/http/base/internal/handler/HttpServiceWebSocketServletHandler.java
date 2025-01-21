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

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.runtime.ServletInfo;

/**
 * Servlet handler for servlets extending JettyWebSocketServlet registered through the http service.
 */
public final class HttpServiceWebSocketServletHandler extends HttpServiceServletHandler
{
    private final WebSocketHandler webSocketHandler;

    public HttpServiceWebSocketServletHandler(final ExtServletContext context,
                                     final ServletInfo servletInfo,
                                     final javax.servlet.Servlet servlet)
    {
        super(context, servletInfo, servlet);
        this.webSocketHandler = new WebSocketHandler(this);
    }

    @Override
    public int init() {
        if (webSocketHandler.shouldInit()) {
            return super.init();
        }
        // do nothing, delay init until first service call
        return -1;
    }

    @Override
    public void handle(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        this.webSocketHandler.lazyInit();
        super.handle(req, res);
    }

    @Override
    public boolean destroy() {
        if (webSocketHandler.shouldDestroy()) {
            return super.destroy();
        }
        return false;
    }
}

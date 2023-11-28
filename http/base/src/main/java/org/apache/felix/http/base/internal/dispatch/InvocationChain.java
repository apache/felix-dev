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
package org.apache.felix.http.base.internal.dispatch;

import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.felix.http.base.internal.handler.FilterHandler;
import org.apache.felix.http.base.internal.handler.ServletHandler;

public class InvocationChain implements FilterChain
{
    private final ServletHandler servletHandler;
    private final FilterHandler[] filterHandlers;

    private int index = -1;

    public InvocationChain(@NotNull final ServletHandler servletHandler, @NotNull final FilterHandler[] filterHandlers)
    {
        this.filterHandlers = filterHandlers;
        this.servletHandler = servletHandler;
    }

    @Override
    public final void doFilter(@NotNull final ServletRequest req, @NotNull final  ServletResponse res) throws IOException, ServletException
    {
        boolean callFinish = false;
        if ( this.index == -1 )
        {
            final HttpServletRequest hReq = (HttpServletRequest) req;
            final HttpServletResponse hRes = (HttpServletResponse) res;

            // invoke security
            if ( !servletHandler.getContext().handleSecurity(hReq, hRes))
            {
                // FELIX-3988: If the response is not yet committed and still has the default
                // status, we're going to override this and send an error instead.
                if (!res.isCommitted() && (hRes.getStatus() == SC_OK || hRes.getStatus() == 0))
                {
                    hRes.sendError(SC_FORBIDDEN);
                }

                // we're done
                return;
            }
            else
            {
                callFinish = true;
            }
        }
        this.index++;

        try
        {
            if (this.index < this.filterHandlers.length)
            {
                this.filterHandlers[this.index].handle(req, res, this);
            }
            else
            {
                // Last entry in the chain...
                this.servletHandler.handle(req, res);
            }
        }
        finally {
            if ( callFinish )
            {
                final HttpServletRequest hReq = (HttpServletRequest) req;
                final HttpServletResponse hRes = (HttpServletResponse) res;

                servletHandler.getContext().finishSecurity(hReq, hRes);
            }
        }
    }
}

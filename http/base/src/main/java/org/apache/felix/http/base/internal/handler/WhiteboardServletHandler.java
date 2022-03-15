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

import java.io.FilePermission;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.servlet.whiteboard.runtime.dto.DTOConstants;

import jakarta.servlet.Servlet;

/**
 * Servlet handler for servlets registered through the http whiteboard.
 */
public final class WhiteboardServletHandler extends ServletHandler
{
    private final BundleContext bundleContext;

    private final int multipartErrorCode;

    private final Bundle multipartSecurityContext;

    public WhiteboardServletHandler(final long contextServiceId,
            final ExtServletContext context,
            final ServletInfo servletInfo,
            final BundleContext contextBundleContext,
            final Bundle registeringBundle,
            final Bundle httpWhiteboardBundle)
    {
        super(contextServiceId, context, servletInfo);
        this.bundleContext = contextBundleContext;
        int errorCode = -1;
        // if multipart upload is enabled and a security manager is active
        // we need to check permissions
        if ( this.getMultipartConfig() != null && System.getSecurityManager() != null )
        {
            final FilePermission writePerm = new FilePermission(this.getMultipartConfig().multipartLocation, "read,write,delete");
            if ( servletInfo.getMultipartConfig().multipartLocation == null )
            {
                // Default location, whiteboard need writePerm, using bundle read perm
                multipartSecurityContext = httpWhiteboardBundle;
                if ( !httpWhiteboardBundle.hasPermission(writePerm))
                {
                    errorCode = DTOConstants.FAILURE_REASON_WHITEBOARD_WRITE_TO_DEFAULT_DENIED;
                }
                else
                {
                    final FilePermission readPerm = new FilePermission(this.getMultipartConfig().multipartLocation, "read");
                    if ( !registeringBundle.hasPermission(readPerm) )
                    {
                        errorCode = DTOConstants.FAILURE_REASON_SERVLET_READ_FROM_DEFAULT_DENIED;
                    }
                }
            }
            else
            {
                multipartSecurityContext = registeringBundle;
                // Provided location, whiteboard and using bundle need write perm
                if ( !registeringBundle.hasPermission(writePerm) )
                {
                    errorCode = DTOConstants.FAILURE_REASON_SERVLET_WRITE_TO_LOCATION_DENIED;
                }
                if ( !httpWhiteboardBundle.hasPermission(writePerm) )
                {
                    errorCode = DTOConstants.FAILURE_REASON_WHITEBOARD_WRITE_TO_LOCATION_DENIED;
                }
            }
        }
        else
        {
            multipartSecurityContext = null;
        }
        multipartErrorCode = errorCode;
    }

    @Override
    public int init()
    {
        if ( this.multipartErrorCode != -1 )
        {
            return this.multipartErrorCode;
        }
        if ( this.useCount > 0 )
        {
            this.useCount++;
            return -1;
        }

        this.setServlet(this.getServletInfo().getService(this.bundleContext));

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
            if ( super.destroy() )
            {
                this.getServletInfo().ungetService(this.bundleContext, this.getServlet());

                return true;
            }
        }
        return false;
    }

    @Override
    public Bundle getMultipartSecurityContext()
    {
        return multipartSecurityContext;
    }
}

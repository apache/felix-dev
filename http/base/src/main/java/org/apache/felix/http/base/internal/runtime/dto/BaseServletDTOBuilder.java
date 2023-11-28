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
package org.apache.felix.http.base.internal.runtime.dto;

import org.apache.felix.http.base.internal.handler.ServletHandler;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.osgi.service.servlet.runtime.dto.BaseServletDTO;

abstract class BaseServletDTOBuilder
{
    /**
     * Build a servlet DTO from a servlet info
     * @param info The servlet info
     * @return A servlet DTO
     */
    public static void fill(final BaseServletDTO dto, final ServletHandler handler)
    {
        dto.name = handler.getName();
        if ( handler.getServlet() != null )
        {
            dto.servletInfo = handler.getServlet().getServletInfo();
        }
        dto.servletContextId = handler.getContextServiceId();
    }

    /**
     * Build a servlet DTO from a servlet info
     * @param info The servlet info
     * @return A servlet DTO
     */
    public static void fill(final BaseServletDTO dto, final ServletInfo info)
    {
        dto.asyncSupported = info.isAsyncSupported();
        dto.initParams = info.getInitParameters();
        dto.name = info.getName();
        dto.serviceId = info.getServiceId();
    }
}

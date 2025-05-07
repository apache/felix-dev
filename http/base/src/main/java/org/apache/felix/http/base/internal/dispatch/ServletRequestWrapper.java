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

import static jakarta.servlet.RequestDispatcher.FORWARD_CONTEXT_PATH;
import static jakarta.servlet.RequestDispatcher.FORWARD_MAPPING;
import static jakarta.servlet.RequestDispatcher.FORWARD_PATH_INFO;
import static jakarta.servlet.RequestDispatcher.FORWARD_QUERY_STRING;
import static jakarta.servlet.RequestDispatcher.FORWARD_REQUEST_URI;
import static jakarta.servlet.RequestDispatcher.FORWARD_SERVLET_PATH;
import static jakarta.servlet.RequestDispatcher.INCLUDE_CONTEXT_PATH;
import static jakarta.servlet.RequestDispatcher.INCLUDE_MAPPING;
import static jakarta.servlet.RequestDispatcher.INCLUDE_PATH_INFO;
import static jakarta.servlet.RequestDispatcher.INCLUDE_QUERY_STRING;
import static jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI;
import static jakarta.servlet.RequestDispatcher.INCLUDE_SERVLET_PATH;
import static org.apache.felix.http.base.internal.util.UriUtils.concat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.handler.HttpSessionWrapper;
import org.osgi.service.servlet.context.ServletContextHelper;
import org.osgi.service.useradmin.Authorization;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestAttributeEvent;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

class ServletRequestWrapper extends HttpServletRequestWrapper
{
	private static final List<String> FORWARD_ATTRIBUTES = Arrays.asList(FORWARD_CONTEXT_PATH,
        FORWARD_MAPPING, FORWARD_PATH_INFO, FORWARD_QUERY_STRING, FORWARD_REQUEST_URI, FORWARD_SERVLET_PATH);

	private static final List<String> INCLUDE_ATTRIBUTES = Arrays.asList(INCLUDE_CONTEXT_PATH,
        INCLUDE_MAPPING, INCLUDE_PATH_INFO, INCLUDE_QUERY_STRING, INCLUDE_REQUEST_URI, INCLUDE_SERVLET_PATH);

	private final DispatcherType type;
	private final RequestInfo requestInfo;
    private final ExtServletContext servletContext;
    private final boolean asyncSupported;

    public ServletRequestWrapper(final HttpServletRequest req,
            final ExtServletContext servletContext,
            final RequestInfo requestInfo,
            final DispatcherType type,
            final boolean asyncSupported)
    {
        super(req);

        this.asyncSupported = asyncSupported;
        this.servletContext = servletContext;
        this.requestInfo = requestInfo;
        this.type = type;
    }

    @Override
    public Object getAttribute(String name)
    {
        HttpServletRequest request = (HttpServletRequest) getRequest();
        if (isInclusionDispatcher() && !this.requestInfo.nameMatch)
        {
            // The jakarta.servlet.include.* attributes refer to the information of the *included* request,
            // meaning that the request information comes from the *original* request...
            if (INCLUDE_REQUEST_URI.equals(name))
            {
                return this.requestInfo.requestURI;
            }
            else if (INCLUDE_CONTEXT_PATH.equals(name))
            {
                return request.getContextPath();
            }
            else if (INCLUDE_SERVLET_PATH.equals(name))
            {
                return this.requestInfo.servletPath;
            }
            else if (INCLUDE_PATH_INFO.equals(name))
            {
                return this.requestInfo.pathInfo;
            }
            else if (INCLUDE_QUERY_STRING.equals(name))
            {
                return this.requestInfo.queryString;
            }
            else if (INCLUDE_MAPPING.equals(name))
            {
                return this.requestInfo;
            }
            // include might be contained within a forward, allow forward attributes
            else if (FORWARD_ATTRIBUTES.contains(name) ) {
                return super.getAttribute(name);
            }
        }
        else if (isForwardingDispatcher() && !this.requestInfo.nameMatch)
        {
            // The jakarta.servlet.forward.* attributes refer to the information of the *original* request,
            // meaning that the request information comes from the *forwarded* request...
            if (FORWARD_REQUEST_URI.equals(name))
            {
                return super.getRequestURI();
            }
            else if (FORWARD_CONTEXT_PATH.equals(name))
            {
                return request.getContextPath();
            }
            else if (FORWARD_SERVLET_PATH.equals(name))
            {
                return super.getServletPath();
            }
            else if (FORWARD_PATH_INFO.equals(name))
            {
                return super.getPathInfo();
            }
            else if (FORWARD_QUERY_STRING.equals(name))
            {
                return super.getQueryString();
            }
            else if (FORWARD_MAPPING.equals(name))
            {
                return super.getHttpServletMapping();
            }
        }
        // block all special attributes
        if (INCLUDE_ATTRIBUTES.contains(name) || FORWARD_ATTRIBUTES.contains(name))
        {
            return null;
        }
        return super.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        if ( isForwardingDispatcher() || isInclusionDispatcher() ) {
            final Set<String> allNames = new HashSet<>(Collections.list(super.getAttributeNames()));
            if ( isForwardingDispatcher() ) {
                allNames.addAll(FORWARD_ATTRIBUTES);
            } else {
                allNames.addAll(INCLUDE_ATTRIBUTES);
            }
            return Collections.enumeration(allNames);
        }
        return super.getAttributeNames();
    }

    @Override
    public String getAuthType()
    {
        String authType = (String) getAttribute(ServletContextHelper.AUTHENTICATION_TYPE);
        if (authType == null)
        {
            authType = super.getAuthType();
        }
        return authType;
    }

    @Override
    public String getContextPath()
    {
        return this.getServletContext().getContextPath();
    }

    @Override
    public DispatcherType getDispatcherType()
    {
        return (this.type == null) ? super.getDispatcherType() : this.type;
    }

    @Override
    public String getPathInfo()
    {
        if ( this.isInclusionDispatcher() )
        {
            return super.getPathInfo();
        }
        return this.requestInfo.pathInfo;
    }

    @Override
    public String getPathTranslated()
    {
        final String info = getPathInfo();
        return (null == info) ? null : getServletContext().getRealPath(info);
    }

    @Override
    public String getRemoteUser()
    {
        String remoteUser = (String) getAttribute(ServletContextHelper.REMOTE_USER);
        if (remoteUser != null)
        {
            return remoteUser;
        }

        return super.getRemoteUser();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path)
    {
        // See section 9.1 of Servlet 3.0 specification...
        if (path == null)
        {
            return null;
        }
        // Handle relative paths, see Servlet 3.0 spec, section 9.1 last paragraph.
        boolean relPath = !path.startsWith("/") && !"".equals(path);
        if (relPath)
        {
            path = concat(getServletPath(), path);
        }
        return this.servletContext.getRequestDispatcher(path);
    }

    @Override
    public String getRequestURI()
    {
        if ( isInclusionDispatcher() )
        {
            return super.getRequestURI();
        }
        return this.requestInfo.requestURI;
    }

    @Override
    public ServletContext getServletContext()
    {
        return this.servletContext;
    }

    @Override
    public String getServletPath()
    {
        if ( isInclusionDispatcher() )
        {
            return super.getServletPath();
        }
        return this.requestInfo.servletPath;
    }

    @Override
    public HttpSession getSession() {
        return this.getSession(true);
    }

    @Override
    public HttpSession getSession(boolean create)
    {
        // FELIX-2797: wrap the original HttpSession to provide access to the correct ServletContext...
        final HttpSession session = super.getSession(create);
        if (session == null)
        {
            return null;
        }
        // check if internal session is available
        if ( !create && !HttpSessionWrapper.hasSession(this.servletContext.getServletContextName(), session) )
        {
            return null;
        }
        return new HttpSessionWrapper(session, this.servletContext, this.servletContext.getConfig(), false);
    }

    @Override
    public boolean isUserInRole(String role)
    {
        final Object authorization = getAttribute(ServletContextHelper.AUTHORIZATION);
        if (authorization instanceof Authorization )
        {
            return ((Authorization)authorization).hasRole(role);
        }

        return super.isUserInRole(role);
    }

    @Override
    public void setAttribute(final String name, final Object value)
    {
        if ( value == null )
        {
            this.removeAttribute(name);
        }
        final Object oldValue = this.getAttribute(name);
        super.setAttribute(name, value);
        if ( this.servletContext.getServletRequestAttributeListener() != null )
        {
            if ( oldValue == null )
            {
                this.servletContext.getServletRequestAttributeListener().attributeAdded(new ServletRequestAttributeEvent(this.servletContext, this, name, value));
            }
            else
            {
                this.servletContext.getServletRequestAttributeListener().attributeReplaced(new ServletRequestAttributeEvent(this.servletContext, this, name, oldValue));
            }
        }
    }

    @Override
    public void removeAttribute(final String name) {
        final Object oldValue = this.getAttribute(name);
        if ( oldValue != null )
        {
            super.removeAttribute(name);
            if ( this.servletContext.getServletRequestAttributeListener() != null )
            {
                this.servletContext.getServletRequestAttributeListener().attributeRemoved(new ServletRequestAttributeEvent(this.servletContext, this, name, oldValue));
            }
        }
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "->" + super.getRequest();
    }

    private boolean isForwardingDispatcher()
    {
        return DispatcherType.FORWARD == this.type;
    }

    private boolean isInclusionDispatcher()
    {
        return DispatcherType.INCLUDE == this.type;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException
    {
        if ( !this.asyncSupported )
        {
            throw new IllegalStateException();
        }
        return super.startAsync();
    }

    @Override
    public AsyncContext startAsync(final ServletRequest servletRequest,
            final ServletResponse servletResponse) throws IllegalStateException
    {
        if ( !this.asyncSupported )
        {
            throw new IllegalStateException();
        }
        return super.startAsync(servletRequest, servletResponse);
    }

    @Override
    public boolean isAsyncSupported()
    {
        return this.asyncSupported;
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        throw new ServletException("No Multipart-Support available");
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        throw new ServletException("No Multipart-Support available");
    }

    @Override
    public String getParameter(final String name) {
        final String[] values = this.getParameterValues(name);
        if (values != null && values.length > 0) {
            return values[0];
        }
        return null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
         return super.getParameterMap();
    }

    @Override
    public Enumeration<String> getParameterNames() {
        final Map<String, String[]> params = this.getParameterMap();
        return Collections.enumeration(params.keySet());
    }

    @Override
    public String[] getParameterValues(final String name) {
        final Map<String, String[]> params = this.getParameterMap();
        return params.get(name);
    }

    @Override
    public HttpServletMapping getHttpServletMapping() {
        return this.requestInfo;
    }

}

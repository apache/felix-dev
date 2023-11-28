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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.handler.HttpSessionWrapper;
import org.osgi.framework.Bundle;
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

final class ServletRequestWrapper extends HttpServletRequestWrapper
{
    private static final List<String> FORWARD_ATTRIBUTES = Arrays.asList(FORWARD_CONTEXT_PATH,
        FORWARD_MAPPING, FORWARD_PATH_INFO, FORWARD_QUERY_STRING, FORWARD_REQUEST_URI, FORWARD_SERVLET_PATH);

    private static final List<String> INCLUDE_ATTRIBUTES = Arrays.asList(INCLUDE_CONTEXT_PATH, 
        INCLUDE_MAPPING, INCLUDE_PATH_INFO, INCLUDE_QUERY_STRING, INCLUDE_REQUEST_URI, INCLUDE_SERVLET_PATH);

    /**
     * Constant for HTTP POST method.
     */
    private static final String POST_METHOD = "POST";

    private final DispatcherType type;
    private final RequestInfo requestInfo;
    private final ExtServletContext servletContext;
    private final boolean asyncSupported;
    private final MultipartConfig multipartConfig;
    private final Bundle bundleForSecurityCheck;

    private Collection<PartImpl> parts;

    private Map<String, String[]> partsParameterMap;

    public ServletRequestWrapper(final HttpServletRequest req,
            final ExtServletContext servletContext,
            final RequestInfo requestInfo,
            final DispatcherType type,
            final boolean asyncSupported,
            final MultipartConfig multipartConfig,
            final Bundle bundleForSecurityCheck)
    {
        super(req);

        this.asyncSupported = asyncSupported;
        this.multipartConfig = multipartConfig;
        this.servletContext = servletContext;
        this.requestInfo = requestInfo;
        this.type = type;
        this.bundleForSecurityCheck = bundleForSecurityCheck;
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

    private RequestContext getMultipartContext() {
        final RequestContext multipartContext;
        if (!POST_METHOD.equalsIgnoreCase(this.getMethod())) {
            multipartContext = null;
        } else {
            multipartContext = new RequestContext() {

                @Override
                public InputStream getInputStream() throws IOException {
                    return ServletRequestWrapper.this.getInputStream();
                }

                @Override
                public String getContentType() {
                    return ServletRequestWrapper.this.getContentType();
                }

                @Override
                public int getContentLength() {
                    return ServletRequestWrapper.this.getContentLength();
                }

                @Override
                public String getCharacterEncoding() {
                    return ServletRequestWrapper.this.getCharacterEncoding();
                }
            };
        }
        return multipartContext;
    }

    private Collection<PartImpl> checkMultipart() throws IOException, ServletException {
        if ( parts == null ) {
            final RequestContext multipartContext = getMultipartContext();
            if ( multipartContext != null && FileUploadBase.isMultipartContent(multipartContext) ) {
                if ( this.multipartConfig == null) {
                    throw new IllegalStateException("Multipart not enabled for servlet.");
                }

                if ( System.getSecurityManager() == null ) {
                    handleMultipart(multipartContext);
                } else {
                    final AccessControlContext ctx = bundleForSecurityCheck.adapt(AccessControlContext.class);
                    final IOException ioe = AccessController.doPrivileged(new PrivilegedAction<IOException>() {

                        @Override
                        public IOException run() {
                            try {
                                handleMultipart(multipartContext);
                            } catch ( final IOException ioe) {
                                return ioe;
                            }
                            return null;
                        }
                    }, ctx);
                    if ( ioe != null ) {
                        throw ioe;
                    }
                }

            } else {
                throw new ServletException("Not a multipart request");
            }
        }
        return parts;
    }

    private void handleMultipart(final RequestContext multipartContext) throws IOException {
        // Create a new file upload handler
        final FileUpload upload = new FileUpload();
        upload.setSizeMax(this.multipartConfig.multipartMaxRequestSize);
        upload.setFileSizeMax(this.multipartConfig.multipartMaxFileSize);
        upload.setFileItemFactory(new DiskFileItemFactory(this.multipartConfig.multipartThreshold,
                new File(this.multipartConfig.multipartLocation)));
        upload.setFileCountMax(this.multipartConfig.multipartMaxFileCount);
        // Parse the request
        List<FileItem> items = null;
        try {
            items = upload.parseRequest(multipartContext);
        } catch (final FileUploadException fue) {
            throw new IOException("Error parsing multipart request", fue);
        }
        this.parts = new ArrayList<>();
        for(final FileItem item : items) {
            this.parts.add(new PartImpl(item));
        }
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Collection<Part> getParts() throws IOException, ServletException {
        return (Collection)checkMultipart();

    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        Collection<PartImpl> parts = this.checkMultipart();
        for(final Part p : parts) {
            if ( p.getName().equals(name) ) {
                return p;
            }
        }
        return null;
    }

    private Map<String, String[]> getPartsParameterMap() {
        if ( this.partsParameterMap == null ) {
            try {
                final Collection<PartImpl> parts = this.checkMultipart();
                final Map<String, String[]> params = new HashMap<>();
                for(final PartImpl p : parts) {
                    if (p.getFileItem().isFormField()) {
                        String[] current = params.get(p.getName());
                        if (current == null) {
                            current = new String[] {p.getFileItem().getString()};
                        } else {
                            String[] newCurrent = new String[current.length + 1];
                            System.arraycopy( current, 0, newCurrent, 0, current.length );
                            newCurrent[current.length] = p.getFileItem().getString();
                            current = newCurrent;
                        }
                        params.put(p.getName(), current);
                    }
                }
                this.partsParameterMap = params;
            } catch (final IOException | ServletException ignore) {
                // ignore all exceptions and use default
            }
            if ( this.partsParameterMap == null ) {
                // use map from container implementation as default
                this.partsParameterMap = super.getParameterMap();
            }
        }
        return this.partsParameterMap;
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
        final RequestContext multipartContext = getMultipartContext();
        if ( multipartContext != null && FileUploadBase.isMultipartContent(multipartContext) && this.multipartConfig != null) {
            return this.getPartsParameterMap();
        }
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

    private static final class PartImpl implements Part {

        private final FileItem item;

        public PartImpl(final FileItem item) {
            this.item = item;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return item.getInputStream();
        }

        @Override
        public String getContentType() {
            return item.getContentType();
        }

        @Override
        public String getName() {
            return item.getFieldName();
        }

        @Override
        public String getSubmittedFileName() {
            return item.getName();
        }

        @Override
        public long getSize() {
            return item.getSize();
        }

        @Override
        public void write(final String fileName) throws IOException {
            try {
                item.write(new File(fileName));
            } catch (final IOException e) {
                throw e;
            } catch (final Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public void delete() throws IOException {
            item.delete();
        }

        @Override
        public String getHeader(final String name) {
            return item.getHeaders().getHeader(name);
        }

        @Override
        public Collection<String> getHeaders(final String name) {
            final List<String> values = new ArrayList<>();
            final Iterator<String> iter = item.getHeaders().getHeaders(name);
            while ( iter.hasNext() ) {
                values.add(iter.next());
            }
            return values;
        }

        @Override
        public Collection<String> getHeaderNames() {
            final List<String> names = new ArrayList<>();
            final Iterator<String> iter = item.getHeaders().getHeaderNames();
            while ( iter.hasNext() ) {
                names.add(iter.next());
            }
            return names;
        }

        public FileItem getFileItem() {
            return this.item;
        }
    }
}

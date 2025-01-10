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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.osgi.framework.Bundle;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

final class ServletRequestMultipartWrapper extends ServletRequestWrapper
{
    /**
     * Constant for HTTP POST method.
     */
    private static final String POST_METHOD = "POST";
	
    private final MultipartConfig multipartConfig;

    private Collection<PartImpl> parts;

    private Map<String, String[]> partsParameterMap;
	private Bundle bundleForSecurityCheck;

    public ServletRequestMultipartWrapper(final HttpServletRequest req,
            final ExtServletContext servletContext,
            final RequestInfo requestInfo,
            final DispatcherType type,
            final boolean asyncSupported,
            final MultipartConfig multipartConfig,
            final Bundle bundleForSecurityCheck)
    {
		super(req, servletContext, requestInfo, type, asyncSupported);

        this.multipartConfig = multipartConfig;
        this.bundleForSecurityCheck = bundleForSecurityCheck;

    }

    private RequestContext getMultipartContext() {
        final RequestContext multipartContext;
        if (!POST_METHOD.equalsIgnoreCase(this.getMethod())) {
            multipartContext = null;
        } else {
            multipartContext = new RequestContext() {

                @Override
                public InputStream getInputStream() throws IOException {
                    return ServletRequestMultipartWrapper.this.getInputStream();
                }

                @Override
                public String getContentType() {
                    return ServletRequestMultipartWrapper.this.getContentType();
                }

                @Override
                public int getContentLength() {
                    return ServletRequestMultipartWrapper.this.getContentLength();
                }

                @Override
                public String getCharacterEncoding() {
                    return ServletRequestMultipartWrapper.this.getCharacterEncoding();
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

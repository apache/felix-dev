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

import java.io.IOException;
import java.util.Collection;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.osgi.framework.Bundle;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

final class ServletRequestMultipartWrapper extends ServletRequestWrapper
{

    private long maxFileCount;

    public ServletRequestMultipartWrapper(final HttpServletRequest req,
            final ExtServletContext servletContext,
            final RequestInfo requestInfo,
            final DispatcherType type,
            final boolean asyncSupported,
            final MultipartConfig multipartConfig,
            final Bundle bundleForSecurityCheck)
    {
        super(req, servletContext, requestInfo, type, asyncSupported);

        // adapt the multipart configuration for jetty
        MultipartConfigElement mce = new MultipartConfigElement(
            multipartConfig.multipartLocation,
            multipartConfig.multipartMaxFileSize,
            multipartConfig.multipartMaxRequestSize,
            multipartConfig.multipartThreshold
        );

        // Override the multipart configuration for the current request
        setAttribute("org.eclipse.jetty.multipartConfig", mce);

        this.maxFileCount = multipartConfig.multipartMaxFileCount;
    }

    /**
     * Enforces the non-standard "maxFileCount" configuration
     * 
     * @return the parts the collection that was checked
     */
    private Collection<Part> checkMultipart() throws IOException, ServletException {
        Collection<Part> parts = getOriginalParts();
        long filePartCount = parts.stream().filter(p -> p.getSubmittedFileName() != null).count();
        if (filePartCount > maxFileCount) {
            throw new FileCountLimitExceededException("Request exceeds maximum file part count", maxFileCount);
        }
        return parts;
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        // enforce the non-standard "maxFileCount" condition
        return checkMultipart();
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        // enforce the non-standard "maxFileCount" condition
        checkMultipart();
        return getOriginalPart(name);
    }

}

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
package org.apache.felix.webconsole.internal.servlet;

import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.felix.webconsole.servlet.User;
import org.apache.felix.webconsole.spi.SecurityProvider;
import org.osgi.framework.Bundle;
import org.osgi.service.servlet.context.ServletContextHelper;
import org.osgi.util.tracker.ServiceTracker;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;


final class OsgiManagerHttpContext extends ServletContextHelper {

    private final AtomicReference<ServiceTracker<SecurityProvider, SecurityProvider>> holder;

    private final Bundle bundle;

    private final String webManagerRoot;

    OsgiManagerHttpContext(final Bundle webConsoleBundle,
            final AtomicReference<ServiceTracker<SecurityProvider, SecurityProvider>> holder,
            final String webManagerRoot) {
        super(webConsoleBundle);
        this.holder = holder;
        this.bundle = webConsoleBundle;
        this.webManagerRoot = webManagerRoot;
    }

    @Override
    public URL getResource(final String name) {
        URL url = this.bundle.getResource( name );
        if ( url == null && name.endsWith( "/" ) ) {
            url = this.bundle.getResource( name.substring( 0, name.length() - 1 ) );
        }
        return url;
    }

    @Override
    public String getMimeType(final String name) {
        return MimeTypes.getByFile(name);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean handleSecurity( final HttpServletRequest r, final HttpServletResponse response ) {
        final ServiceTracker<SecurityProvider, SecurityProvider> tracker = holder.get();
        final SecurityProvider provider = tracker != null ? tracker.getService() : null;

        // for compatibility we have to adjust a few methods on the request
        final HttpServletRequest request = new HttpServletRequestWrapper(r) {

            @Override
            public String getContextPath() {
                int managerRootIndex = r.getContextPath().lastIndexOf(webManagerRoot);
                return r.getContextPath().substring(0, managerRootIndex);
            }

            @Override
            public String getServletPath() {
                int managerRootIndex = r.getContextPath().lastIndexOf(webManagerRoot);
                return r.getContextPath().substring(managerRootIndex);
            }

            @Override
            public String getPathInfo() {
                return r.getServletPath();
            }
        };

        // check whether the security provider can fully handle the request
        final Object result = provider == null ? null : provider.authenticate( request, response );

        if ( result != null ) {
            request.setAttribute(User.USER_ATTRIBUTE, new org.apache.felix.webconsole.User(){

				@Override
				public boolean authorize(String role) {
                    final Object user = this.getUserObject();
                    if ( user == null ) {
                        // no user object in request, deny
                        return false;
                    }
					if ( provider == null ) {
                        // no provider, allow (compatibility)
                        return true;
                    }
					return provider.authorize(user, role);
				}

				@Override
				public Object getUserObject() {
					return result;
				}

            });
            request.setAttribute(org.apache.felix.webconsole.User.USER_ATTRIBUTE, request.getAttribute(User.USER_ATTRIBUTE));
        }
        return result != null;
    }
}

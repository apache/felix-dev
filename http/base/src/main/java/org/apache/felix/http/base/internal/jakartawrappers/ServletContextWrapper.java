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
package org.apache.felix.http.base.internal.jakartawrappers;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRegistration.Dynamic;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;

/**
 * Servlet context wrapper
 */
public class ServletContextWrapper implements ServletContext {

    private final javax.servlet.ServletContext context;

    /**
     * Create new context
     * @param c Wrapped context
     */
    public ServletContextWrapper(@NotNull final javax.servlet.ServletContext c) {
        this.context = c;
    }

    @Override
    public String getContextPath() {
        return this.context.getContextPath();
    }

    @Override
    public ServletContext getContext(final String uripath) {
        final javax.servlet.ServletContext c = this.context.getContext(uripath);
        if ( c != null ) {
            return new ServletContextWrapper(c);
        }
        return null;
    }

    @Override
    public int getMajorVersion() {
        return this.context.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return this.context.getMinorVersion();
    }

    @Override
    public int getEffectiveMajorVersion() {
        return this.context.getEffectiveMajorVersion();
    }

    @Override
    public int getEffectiveMinorVersion() {
        return this.context.getEffectiveMinorVersion();
    }

    @Override
    public String getMimeType(final String file) {
        return this.context.getMimeType(file);
    }

    @Override
    public Set<String> getResourcePaths(final String path) {
        return this.context.getResourcePaths(path);
    }

    @Override
    public URL getResource(final String path) throws MalformedURLException {
        return this.context.getResource(path);
    }

    @Override
    public InputStream getResourceAsStream(final String path) {
        return this.context.getResourceAsStream(path);
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {
        final javax.servlet.RequestDispatcher dispatcher = this.context.getRequestDispatcher(path);
        if ( dispatcher != null ) {
            return new RequestDispatcherWrapper(dispatcher);
        }
        return null;
    }

    @Override
    public RequestDispatcher getNamedDispatcher(final String name) {
        final javax.servlet.RequestDispatcher dispatcher = this.context.getNamedDispatcher(name);
        if ( dispatcher != null ) {
            return new RequestDispatcherWrapper(dispatcher);
        }
        return null;
    }

    @Override
    public Servlet getServlet(final String name) throws ServletException {
        return null;
    }

    @Override
    public Enumeration<Servlet> getServlets() {
        return Collections.emptyEnumeration();
    }

    @SuppressWarnings("deprecation")
    @Override
    public Enumeration<String> getServletNames() {
        return this.context.getServletNames();
    }

    @Override
    public void log(final String msg) {
        this.context.log(msg);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void log(final Exception exception, final String msg) {
        this.context.log(exception, msg);
    }

    @Override
    public void log(final String message, final Throwable throwable) {
        this.context.log(message, throwable);
    }

    @Override
    public String getRealPath(final String path) {
        return this.context.getRealPath(path);
    }

    @Override
    public String getServerInfo() {
        return this.context.getServerInfo();
    }

    @Override
    public String getInitParameter(final String name) {
        return this.context.getInitParameter(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return this.context.getInitParameterNames();
    }

    @Override
    public boolean setInitParameter(final String name, final String value) {
        return this.context.setInitParameter(name, value);
    }

    @Override
    public Object getAttribute(final String name) {
        return this.context.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return this.context.getAttributeNames();
    }

    @Override
    public void setAttribute(final String name, final Object object) {
        this.context.setAttribute(name, object);
    }

    @Override
    public void removeAttribute(final String name) {
        this.context.removeAttribute(name);
    }

    @Override
    public String getServletContextName() {
        return this.context.getServletContextName();
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final String className) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final Servlet servlet) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletRegistration.Dynamic addServlet(final String servletName, final Class<? extends Servlet> servletClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Servlet> T createServlet(final Class<T> clazz) throws ServletException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletRegistration getServletRegistration(final String servletName) {
        final javax.servlet.ServletRegistration reg = this.context.getServletRegistration(servletName);
        if ( reg != null ) {
            return new ServletRegistrationWrapper(reg);
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        final Map result = new HashMap<>();
        for(final Map.Entry<String, ? extends javax.servlet.ServletRegistration> e : this.context.getServletRegistrations().entrySet()) {
            result.put(e.getKey(), new ServletRegistrationWrapper(e.getValue()));
        }
        return result;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final String className) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final Filter filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilterRegistration.Dynamic addFilter(final String filterName, final Class<? extends Filter> filterClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Filter> T createFilter(final Class<T> clazz) throws ServletException {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilterRegistration getFilterRegistration(final String filterName) {
        return new FilterRegistrationWrapper(this.context.getFilterRegistration(filterName));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        final Map result = new HashMap<>();
        for(final Map.Entry<String, ? extends javax.servlet.FilterRegistration> e : this.context.getFilterRegistrations().entrySet()) {
            result.put(e.getKey(), new FilterRegistrationWrapper(e.getValue()));
        }
        return result;
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return new SessionCookieConfigWrapper(this.context.getSessionCookieConfig());
    }

    @Override
    public void setSessionTrackingModes(final Set<SessionTrackingMode> sessionTrackingModes) {
        if ( sessionTrackingModes == null ) {
            this.context.setSessionTrackingModes(null);
        } else {
            final Set<javax.servlet.SessionTrackingMode> modes = new HashSet<>();
            if ( sessionTrackingModes.contains(SessionTrackingMode.COOKIE)) {
                modes.add(javax.servlet.SessionTrackingMode.COOKIE);
            }
            if ( sessionTrackingModes.contains(SessionTrackingMode.SSL)) {
                modes.add(javax.servlet.SessionTrackingMode.SSL);
            }
            if ( sessionTrackingModes.contains(SessionTrackingMode.URL)) {
                modes.add(javax.servlet.SessionTrackingMode.URL);
            }
            this.context.setSessionTrackingModes(modes);
        }
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        final Set<javax.servlet.SessionTrackingMode> sessionTrackingModes = this.context.getDefaultSessionTrackingModes();
        final Set<SessionTrackingMode> modes = new HashSet<>();
        if ( sessionTrackingModes.contains(javax.servlet.SessionTrackingMode.COOKIE)) {
            modes.add(SessionTrackingMode.COOKIE);
        }
        if ( sessionTrackingModes.contains(javax.servlet.SessionTrackingMode.SSL)) {
            modes.add(SessionTrackingMode.SSL);
        }
        if ( sessionTrackingModes.contains(javax.servlet.SessionTrackingMode.URL)) {
            modes.add(SessionTrackingMode.URL);
        }
        return modes;
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        final Set<javax.servlet.SessionTrackingMode> sessionTrackingModes = this.context.getEffectiveSessionTrackingModes();
        final Set<SessionTrackingMode> modes = new HashSet<>();
        if ( sessionTrackingModes.contains(javax.servlet.SessionTrackingMode.COOKIE)) {
            modes.add(SessionTrackingMode.COOKIE);
        }
        if ( sessionTrackingModes.contains(javax.servlet.SessionTrackingMode.SSL)) {
            modes.add(SessionTrackingMode.SSL);
        }
        if ( sessionTrackingModes.contains(javax.servlet.SessionTrackingMode.URL)) {
            modes.add(SessionTrackingMode.URL);
        }
        return modes;
    }

    @Override
    public void addListener(String className) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends EventListener> void addListener(final T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addListener(final Class<? extends EventListener> listenerClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends EventListener> T createListener(final Class<T> clazz) throws ServletException {
        throw new UnsupportedOperationException();
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return this.context.getClassLoader();
    }

    @Override
    public void declareRoles(final String... roleNames) {
        this.context.declareRoles(roleNames);
    }

    @Override
    public String getVirtualServerName() {
        return this.context.getVirtualServerName();
    }

    @Override
    public Dynamic addJspFile(final String servletName, final String jspFile) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSessionTimeout() {
        return this.context.getSessionTimeout();
    }

    @Override
    public void setSessionTimeout(int sessionTimeout) {
        this.context.setSessionTimeout(sessionTimeout);
    }

    @Override
    public String getRequestCharacterEncoding() {
        return this.context.getRequestCharacterEncoding();
    }

    @Override
    public void setRequestCharacterEncoding(final String encoding) {
        this.context.setRequestCharacterEncoding(encoding);
    }

    @Override
    public String getResponseCharacterEncoding() {
        return this.context.getResponseCharacterEncoding();
    }

    @Override
    public void setResponseCharacterEncoding(final String encoding) {
        this.context.setResponseCharacterEncoding(encoding);
    }
}

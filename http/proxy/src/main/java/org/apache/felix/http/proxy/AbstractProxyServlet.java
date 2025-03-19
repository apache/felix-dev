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
package org.apache.felix.http.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRegistration.Dynamic;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.felix.http.proxy.impl.DispatcherTracker;
import org.osgi.framework.BundleContext;

public abstract class AbstractProxyServlet
    extends HttpServlet
{
    private volatile DispatcherTracker tracker;

    private volatile boolean initialized = false;

    private volatile ServletContext servletContext;

    @Override
    public void init(ServletConfig config)
        throws ServletException
    {
        super.init(config);
    }

    private void doInit()
        throws Exception
    {
        final ServletConfig origConfig = getServletConfig();
        ServletConfig config = origConfig;
        if ( this.servletContext != null ) {
            config = new ServletConfig() {

                @Override
                public String getServletName() {
                    return origConfig.getServletName();
                }

                @Override
                public ServletContext getServletContext() {
                    return servletContext;
                }

                @Override
                public Enumeration<String> getInitParameterNames() {
                    return origConfig.getInitParameterNames();
                }

                @Override
                public String getInitParameter(String name) {
                    return origConfig.getInitParameter(name);
                }
            };
        }
        this.tracker = new DispatcherTracker(getBundleContext(), config);
        this.tracker.open();
    }

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse res)
        throws ServletException, IOException
    {
        if ( !initialized ) {
            synchronized ( this ) {
                if (!initialized ) {
                    if ( ! "".equals(req.getServletPath()) ) {
                        this.servletContext = new ServletContextWrapper(req.getServletContext(), req.getContextPath() + req.getServletPath());
                    }

                    try {
                        doInit();
                    } catch (ServletException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new ServletException(e);
                    }

                    this.initialized = true;
                }
            }
        }

        final HttpServlet dispatcher = this.tracker.getDispatcher();
        if (dispatcher != null) {
            final HttpServletRequest r = (this.servletContext == null ? req : new BridgeHttpServletRequest(req, this.servletContext));
            dispatcher.service(r, res);
        } else {
            res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public void destroy()
    {
        if ( this.tracker != null ) {
            this.tracker.close();
            this.tracker = null;
        }
        super.destroy();
    }

    protected abstract BundleContext getBundleContext()
        throws ServletException;


    private static final class BridgeHttpServletRequest extends HttpServletRequestWrapper
    {
        private final ServletContext context;

        public BridgeHttpServletRequest(final HttpServletRequest req, final ServletContext context)
        {
            super(req);
            this.context = context;
        }

        @Override
        public String getServletPath()
        {
            return "";
        }

        @Override
        public String getContextPath()
        {
            return this.context.getContextPath();
        }

        @Override
        public ServletContext getServletContext() {
            return this.context;
        }


    }

    private static final class ServletContextWrapper implements ServletContext
    {
        private final ServletContext delegatee;

        private final String path;

        public ServletContextWrapper(final ServletContext sc, final String path)
        {
            this.delegatee = sc;
            this.path = path;
        }

        @Override
        public String getContextPath() {
            return path;
        }

        @Override
        public ServletContext getContext(String uripath) {
            return delegatee.getContext(uripath);
        }

        @Override
        public int getMajorVersion() {
            return delegatee.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegatee.getMinorVersion();
        }

        @Override
        public int getEffectiveMajorVersion() {
            return delegatee.getEffectiveMajorVersion();
        }

        @Override
        public int getEffectiveMinorVersion() {
            return delegatee.getEffectiveMinorVersion();
        }

        @Override
        public String getMimeType(String file) {
            return delegatee.getMimeType(file);
        }

        @Override
        public Set<String> getResourcePaths(String path) {
            return delegatee.getResourcePaths(path);
        }

        @Override
        public URL getResource(String path) throws MalformedURLException {
            return delegatee.getResource(path);
        }

        @Override
        public InputStream getResourceAsStream(String path) {
            return delegatee.getResourceAsStream(path);
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            return delegatee.getRequestDispatcher(path);
        }

        @Override
        public RequestDispatcher getNamedDispatcher(String name) {
            return delegatee.getNamedDispatcher(name);
        }

        @Override
        public void log(String msg) {
            delegatee.log(msg);
        }

        @Override
        public void log(String message, Throwable throwable) {
            delegatee.log(message, throwable);
        }

        @Override
        public String getRealPath(String path) {
            return delegatee.getRealPath(path);
        }

        @Override
        public String getServerInfo() {
            return delegatee.getServerInfo();
        }

        @Override
        public String getInitParameter(String name) {
            return delegatee.getInitParameter(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return delegatee.getInitParameterNames();
        }

        @Override
        public boolean setInitParameter(String name, String value) {
            return delegatee.setInitParameter(name, value);
        }

        @Override
        public Object getAttribute(String name) {
            return delegatee.getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return delegatee.getAttributeNames();
        }

        @Override
        public void setAttribute(String name, Object object) {
            delegatee.setAttribute(name, object);
        }

        @Override
        public void removeAttribute(String name) {
            delegatee.removeAttribute(name);
        }

        @Override
        public String getServletContextName() {
            return delegatee.getServletContextName();
        }

        @Override
        public Dynamic addServlet(String servletName, String className) {
            return delegatee.addServlet(servletName, className);
        }

        @Override
        public Dynamic addServlet(String servletName, Servlet servlet) {
            return delegatee.addServlet(servletName, servlet);
        }

        @Override
        public Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
            return delegatee.addServlet(servletName, servletClass);
        }

        @Override
        public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
            return delegatee.createServlet(clazz);
        }

        @Override
        public ServletRegistration getServletRegistration(String servletName) {
            return delegatee.getServletRegistration(servletName);
        }

        @Override
        public Map<String, ? extends ServletRegistration> getServletRegistrations() {
            return delegatee.getServletRegistrations();
        }

        @Override
        public jakarta.servlet.FilterRegistration.Dynamic addFilter(String filterName, String className) {
            return delegatee.addFilter(filterName, className);
        }

        @Override
        public jakarta.servlet.FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
            return delegatee.addFilter(filterName, filter);
        }

        @Override
        public jakarta.servlet.FilterRegistration.Dynamic addFilter(String filterName,
                Class<? extends Filter> filterClass) {
            return delegatee.addFilter(filterName, filterClass);
        }

        @Override
        public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
            return delegatee.createFilter(clazz);
        }

        @Override
        public FilterRegistration getFilterRegistration(String filterName) {
            return delegatee.getFilterRegistration(filterName);
        }

        @Override
        public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
            return delegatee.getFilterRegistrations();
        }

        @Override
        public SessionCookieConfig getSessionCookieConfig() {
            return delegatee.getSessionCookieConfig();
        }

        @Override
        public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
            delegatee.setSessionTrackingModes(sessionTrackingModes);
        }

        @Override
        public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
            return delegatee.getDefaultSessionTrackingModes();
        }

        @Override
        public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
            return delegatee.getEffectiveSessionTrackingModes();
        }

        @Override
        public void addListener(String className) {
            delegatee.addListener(className);
        }

        @Override
        public <T extends EventListener> void addListener(T t) {
            delegatee.addListener(t);
        }

        @Override
        public void addListener(Class<? extends EventListener> listenerClass) {
            delegatee.addListener(listenerClass);
        }

        @Override
        public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
            return delegatee.createListener(clazz);
        }

        @Override
        public JspConfigDescriptor getJspConfigDescriptor() {
            return delegatee.getJspConfigDescriptor();
        }

        @Override
        public ClassLoader getClassLoader() {
            return delegatee.getClassLoader();
        }

        @Override
        public void declareRoles(String... roleNames) {
            delegatee.declareRoles(roleNames);
        }

        @Override
        public String getVirtualServerName() {
            return delegatee.getVirtualServerName();
        }

        // 4.0.0

        @Override
        public Dynamic addJspFile(String servletName, String jspFile) {
            return delegatee.addJspFile(servletName, jspFile);
        }

        @Override
        public int getSessionTimeout() {
            return delegatee.getSessionTimeout();
        }

        @Override
        public void setSessionTimeout(int sessionTimeout) {
            delegatee.setSessionTimeout(sessionTimeout);
        }

        @Override
        public String getRequestCharacterEncoding() {
            return delegatee.getRequestCharacterEncoding();
        }

        @Override
        public void setRequestCharacterEncoding(String encoding) {
            delegatee.setRequestCharacterEncoding(encoding);
        }

        @Override
        public String getResponseCharacterEncoding() {
            return delegatee.getResponseCharacterEncoding();
        }

        @Override
        public void setResponseCharacterEncoding(String encoding) {
            delegatee.setResponseCharacterEncoding(encoding);
        }
    }
}

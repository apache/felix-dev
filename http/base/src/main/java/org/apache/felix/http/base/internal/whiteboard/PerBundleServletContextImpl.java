/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.http.base.internal.whiteboard;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import org.apache.felix.http.base.internal.HttpConfig;
import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.registry.PerContextHandlerRegistry;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.servlet.context.ServletContextHelper;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRegistration.Dynamic;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionListener;

/**
 * This servlet context implementation represents the per
 * bundle specific part of a servlet context backed by a
 * servlet context helper.
 *
 */
public class PerBundleServletContextImpl implements ExtServletContext {

    private final Bundle bundle;
    private final ServletContext delegatee;
    private final ServletContextHelper contextHelper;
    private final PerContextHandlerRegistry handlerRegistry;

    /**
     * Create a new context implementation
     * @param bundle The bundle
     * @param sharedContext The shared context
     * @param delegatee The delegatee
     * @param handlerRegistry The handler registry
     */
    public PerBundleServletContextImpl(final Bundle bundle,
            final ServletContext sharedContext,
            final ServletContextHelper delegatee,
            final PerContextHandlerRegistry handlerRegistry)
    {
        this.bundle = bundle;
        this.delegatee = sharedContext;
        this.contextHelper = delegatee;
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public boolean handleSecurity(final HttpServletRequest req,
            final HttpServletResponse res)
    throws IOException
    {
        return this.contextHelper.handleSecurity(req, res);
    }

    @Override
    public void finishSecurity(HttpServletRequest req, HttpServletResponse res)
    {
        this.contextHelper.finishSecurity(req, res);
    }

    @Override
    public HttpSessionListener getHttpSessionListener()
    {
        return this.handlerRegistry.getEventListenerRegistry();
    }

    @Override
    public HttpSessionAttributeListener getHttpSessionAttributeListener()
    {
        return this.handlerRegistry.getEventListenerRegistry();
    }

    @Override
    public ServletRequestListener getServletRequestListener()
    {
        return this.handlerRegistry.getEventListenerRegistry();
    }

    @Override
    public ServletRequestAttributeListener getServletRequestAttributeListener()
    {
        return this.handlerRegistry.getEventListenerRegistry();
    }

    @Override
    public HttpConfig getConfig()
    {
        return this.handlerRegistry.getConfig();
    }

    @Override
    public ClassLoader getClassLoader()
    {
        return this.bundle.adapt(BundleWiring.class).getClassLoader();
    }

    @Override
    public URL getResource(final String path)
    {
        return this.contextHelper.getResource(path);
    }

    @Override
    public String getMimeType(final String name)
    {
        return this.contextHelper.getMimeType(name);
    }

    @Override
    public String getRealPath(final String path)
    {
        return this.contextHelper.getRealPath(path);
    }

    @Override
    public Set<String> getResourcePaths(final String path)
    {
        return this.contextHelper.getResourcePaths(path);
    }

    @Override
    public String getContextPath()
    {
        return delegatee.getContextPath();
    }

    @Override
    public ServletContext getContext(String uripath)
    {
        return delegatee.getContext(uripath);
    }

    @Override
    public int getMajorVersion()
    {
        return delegatee.getMajorVersion();
    }

    @Override
    public int getMinorVersion()
    {
        return delegatee.getMinorVersion();
    }

    @Override
    public int getEffectiveMajorVersion()
    {
        return delegatee.getEffectiveMajorVersion();
    }

    @Override
    public int getEffectiveMinorVersion()
    {
        return delegatee.getEffectiveMinorVersion();
    }

    @Override
    public InputStream getResourceAsStream(final String path)
    {
        final URL res = getResource(path);
        if (res != null)
        {
            try
            {
                return res.openStream();
            }
            catch (IOException e)
            {
                // Do nothing
            }
        }
        return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String path)
    {
        return delegatee.getRequestDispatcher(path);
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String name)
    {
        return delegatee.getNamedDispatcher(name);
    }

    public Servlet getServlet(String name) throws ServletException
    {
		throw new UnsupportedOperationException("Deprecated method not supported");
    }

    public Enumeration<Servlet> getServlets()
    {
		throw new UnsupportedOperationException("Deprecated method not supported");
    }

    public Enumeration<String> getServletNames()
    {
		throw new UnsupportedOperationException("Deprecated method not supported");
    }

    @Override
    public void log(String msg)
    {
        delegatee.log(msg);
    }

    public void log(Exception exception, String msg)
    {
        delegatee.log(msg, exception);
    }

    @Override
    public void log(String message, Throwable throwable)
    {
        delegatee.log(message, throwable);
    }

    @Override
    public String getServerInfo()
    {
        return delegatee.getServerInfo();
    }

    @Override
    public String getInitParameter(String name)
    {
        return delegatee.getInitParameter(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames()
    {
        return delegatee.getInitParameterNames();
    }

    @Override
    public boolean setInitParameter(String name, String value)
    {
        return delegatee.setInitParameter(name, value);
    }

    @Override
    public Object getAttribute(String name)
    {
        return delegatee.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames()
    {
        return delegatee.getAttributeNames();
    }

    @Override
    public void setAttribute(String name, Object object)
    {
        delegatee.setAttribute(name, object);
    }

    @Override
    public void removeAttribute(String name)
    {
        delegatee.removeAttribute(name);
    }

    @Override
    public String getServletContextName()
    {
        return delegatee.getServletContextName();
    }

    @Override
    public Dynamic addServlet(String servletName, String className)
    {
        return delegatee.addServlet(servletName, className);
    }

    @Override
    public Dynamic addServlet(String servletName, Servlet servlet)
    {
        return delegatee.addServlet(servletName, servlet);
    }

    @Override
    public Dynamic addServlet(String servletName,
            Class<? extends Servlet> servletClass)
    {
        return delegatee.addServlet(servletName, servletClass);
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz)
    throws ServletException
    {
        return delegatee.createServlet(clazz);
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName)
    {
        return delegatee.getServletRegistration(servletName);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations()
    {
        return delegatee.getServletRegistrations();
    }

    @Override
    public jakarta.servlet.FilterRegistration.Dynamic addFilter(
            String filterName, String className)
    {
        return delegatee.addFilter(filterName, className);
    }

    @Override
    public jakarta.servlet.FilterRegistration.Dynamic addFilter(
            String filterName, Filter filter)
    {
        return delegatee.addFilter(filterName, filter);
    }

    @Override
    public jakarta.servlet.FilterRegistration.Dynamic addFilter(
            String filterName, Class<? extends Filter> filterClass)
    {
        return delegatee.addFilter(filterName, filterClass);
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> clazz)
            throws ServletException
    {
        return delegatee.createFilter(clazz);
    }

    @Override
    public FilterRegistration getFilterRegistration(String filterName)
    {
        return delegatee.getFilterRegistration(filterName);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations()
    {
        return delegatee.getFilterRegistrations();
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig()
    {
        return delegatee.getSessionCookieConfig();
    }

    @Override
    public void setSessionTrackingModes(
            Set<SessionTrackingMode> sessionTrackingModes)
    {
        delegatee.setSessionTrackingModes(sessionTrackingModes);
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes()
    {
        return delegatee.getDefaultSessionTrackingModes();
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes()
    {
        return delegatee.getEffectiveSessionTrackingModes();
    }

    @Override
    public void addListener(String className)
    {
        delegatee.addListener(className);
    }

    @Override
    public <T extends EventListener> void addListener(T t)
    {
        delegatee.addListener(t);
    }

    @Override
    public void addListener(Class<? extends EventListener> listenerClass)
    {
        delegatee.addListener(listenerClass);
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> clazz)
    throws ServletException
    {
        return delegatee.createListener(clazz);
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor()
    {
        return delegatee.getJspConfigDescriptor();
    }

    @Override
    public void declareRoles(String... roleNames)
    {
        delegatee.declareRoles(roleNames);
    }

    @Override
    public String getVirtualServerName()
    {
        return delegatee.getVirtualServerName();
    }

    @Override
    public Dynamic addJspFile(final String servletName, final String jspFile) {
        return this.delegatee.addJspFile(servletName, jspFile);
    }

    @Override
    public int getSessionTimeout() {
        return this.delegatee.getSessionTimeout();
    }

    @Override
    public void setSessionTimeout(final int sessionTimeout) {
        this.delegatee.setSessionTimeout(sessionTimeout);
    }

    @Override
    public String getRequestCharacterEncoding() {
        return this.delegatee.getRequestCharacterEncoding();
    }

    @Override
    public void setRequestCharacterEncoding(final String encoding) {
        this.delegatee.setRequestCharacterEncoding(encoding);
    }

    @Override
    public String getResponseCharacterEncoding() {
        return this.delegatee.getResponseCharacterEncoding();
    }

    @Override
    public void setResponseCharacterEncoding(final String encoding) {
        this.delegatee.setResponseCharacterEncoding(encoding);
    }
}

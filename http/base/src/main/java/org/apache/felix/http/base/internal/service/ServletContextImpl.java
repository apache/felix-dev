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
package org.apache.felix.http.base.internal.service;

import static org.apache.felix.http.base.internal.util.UriUtils.decodePath;
import static org.apache.felix.http.base.internal.util.UriUtils.removeDotSegments;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.felix.http.base.internal.HttpConfig;
import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.dispatch.RequestDispatcherImpl;
import org.apache.felix.http.base.internal.dispatch.RequestInfo;
import org.apache.felix.http.base.internal.handler.ServletHandler;
import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.apache.felix.http.base.internal.registry.PathResolution;
import org.apache.felix.http.base.internal.registry.PerContextHandlerRegistry;
import org.apache.felix.http.base.internal.registry.ServletResolution;
import org.apache.felix.http.base.internal.util.MimeTypes;
import org.apache.felix.http.base.internal.util.UriUtils;
import org.apache.felix.http.javaxwrappers.ServletRequestWrapper;
import org.apache.felix.http.javaxwrappers.ServletResponseWrapper;
import org.osgi.framework.Bundle;
import org.osgi.service.http.HttpContext;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextAttributeEvent;
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
import jakarta.servlet.http.MappingMatch;

public class ServletContextImpl implements ExtServletContext
{
    private final Bundle bundle;
    private final ServletContext context;
    private final HttpContext httpContext;
    private final Map<String, Object> attributes;
    private final PerContextHandlerRegistry handlerRegistry;

    /**
     * Create a new servlet context implementation
     * @param bundle The bundle
     * @param context The context
     * @param httpContext The http context
     * @param sharedAttributes shared attribtes
     * @param registry The registry
     */
    public ServletContextImpl(final Bundle bundle,
            final ServletContext context,
            final HttpContext httpContext,
            final boolean sharedAttributes,
            final PerContextHandlerRegistry registry)
    {
        this.bundle = bundle;
        this.context = context;
        this.httpContext = httpContext;
        this.attributes = sharedAttributes ? null : new ConcurrentHashMap<String, Object>();
        this.handlerRegistry = registry;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> type)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addListener(Class<? extends EventListener> type)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addListener(String className)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends EventListener> void addListener(T listener)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> type)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> type) throws ServletException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> type) throws ServletException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> type) throws ServletException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void declareRoles(String... roleNames)
    {
        this.context.declareRoles(roleNames);
    }

    @Override
    public String getVirtualServerName() {
        return context.getVirtualServerName();
    }

    @Override
    public Object getAttribute(String name)
    {
        return (this.attributes != null) ? this.attributes.get(name) : this.context.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames()
    {
        return (this.attributes != null) ? Collections.enumeration(this.attributes.keySet()) : this.context.getAttributeNames();
    }

    @Override
    public ClassLoader getClassLoader()
    {
        return bundle.getClass().getClassLoader();
    }

    @Override
    public ServletContext getContext(String uri)
    {
        return this.context.getContext(uri);
    }

    @Override
    public String getContextPath()
    {
        return this.context.getContextPath();
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes()
    {
        return this.context.getDefaultSessionTrackingModes();
    }

    @Override
    public int getEffectiveMajorVersion()
    {
        return this.context.getEffectiveMajorVersion();
    }

    @Override
    public int getEffectiveMinorVersion()
    {
        return this.context.getEffectiveMinorVersion();
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes()
    {
        return this.context.getEffectiveSessionTrackingModes();
    }

    @Override
    public FilterRegistration getFilterRegistration(String filterName)
    {
        return this.context.getFilterRegistration(filterName);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations()
    {
        return this.context.getFilterRegistrations();
    }

    @Override
    public String getInitParameter(String name)
    {
        return this.context.getInitParameter(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames()
    {
        return this.context.getInitParameterNames();
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMajorVersion()
    {
        return this.context.getMajorVersion();
    }

    @Override
    public String getMimeType(String file)
    {
        String type = this.httpContext.getMimeType(file);
        if (type != null)
        {
            return type;
        }

        return MimeTypes.get().getByFile(file);
    }

    @Override
    public int getMinorVersion()
    {
        return this.context.getMinorVersion();
    }

    @Override
    public String getRealPath(String name)
    {
        URL url = getResource(name);
        if (url == null)
        {
            return null;
        }
        return url.toExternalForm();
    }

    @Override
    public URL getResource(String path)
    {
        return this.httpContext.getResource(normalizeResourcePath(path));
    }

    @Override
    public InputStream getResourceAsStream(String path)
    {
        URL res = getResource(path);
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
    public Set<String> getResourcePaths(String path)
    {
        Enumeration<String> paths = this.bundle.getEntryPaths(normalizePath(path));
        if ((paths == null) || !paths.hasMoreElements())
        {
            return null;
        }

        Set<String> set = new HashSet<>();
        while (paths.hasMoreElements())
        {
            set.add(paths.nextElement());
        }

        return set;
    }

    @Override
    public String getServerInfo()
    {
        return this.context.getServerInfo();
    }

    public Servlet getServlet(String name) throws ServletException
    {
		throw new UnsupportedOperationException("Deprecated method not supported");
    }

    @Override
    public String getServletContextName()
    {
        return HttpServiceFactory.HTTP_SERVICE_CONTEXT_NAME;
    }

    public Enumeration<String> getServletNames()
    {
		throw new UnsupportedOperationException("Deprecated method not supported");
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName)
    {
        return this.context.getServletRegistration(servletName);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations()
    {
        return this.context.getServletRegistrations();
    }

    public Enumeration<Servlet> getServlets()
    {
		throw new UnsupportedOperationException("Deprecated method not supported");
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig()
    {
        return this.context.getSessionCookieConfig();
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
    public boolean handleSecurity(HttpServletRequest req, HttpServletResponse res) throws IOException
    {
        return this.httpContext.handleSecurity((javax.servlet.http.HttpServletRequest)ServletRequestWrapper.getWrapper(req),
                (javax.servlet.http.HttpServletResponse)ServletResponseWrapper.getWrapper(res));
    }

    @Override
    public void finishSecurity(HttpServletRequest req, HttpServletResponse res) {
        // nothing to do
    }

    public void log(Exception cause, String message)
    {
        SystemLogger.LOGGER.error(message, cause);
    }

    @Override
    public void log(String message)
    {
        SystemLogger.LOGGER.info(message);
    }

    @Override
    public void log(String message, Throwable cause)
    {
        SystemLogger.LOGGER.error(message, cause);
    }

    @Override
    public void removeAttribute(String name)
    {
        Object oldValue;
        if (this.attributes != null)
        {
            oldValue = this.attributes.remove(name);
        }
        else
        {
            oldValue = this.context.getAttribute(name);
            this.context.removeAttribute(name);
        }

        if (oldValue != null)
        {
            this.handlerRegistry.getEventListenerRegistry().attributeRemoved(new ServletContextAttributeEvent(this, name, oldValue));
        }
    }

    @Override
    public void setAttribute(String name, Object value)
    {
        if (value == null)
        {
            this.removeAttribute(name);
        }
        else if (name != null)
        {
            Object oldValue;
            if (this.attributes != null)
            {
                oldValue = this.attributes.put(name, value);
            }
            else
            {
                oldValue = this.context.getAttribute(name);
                this.context.setAttribute(name, value);
            }

            if (oldValue == null)
            {
                this.handlerRegistry.getEventListenerRegistry().attributeAdded(new ServletContextAttributeEvent(this, name, value));
            }
            else
            {
                this.handlerRegistry.getEventListenerRegistry().attributeReplaced(new ServletContextAttributeEvent(this, name, oldValue));
            }
        }
    }

    @Override
    public boolean setInitParameter(String name, String value)
    {
        return this.context.setInitParameter(name, value);
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> modes)
    {
        this.context.setSessionTrackingModes(modes);
    }

    @Override
    public RequestDispatcher getNamedDispatcher(final String name)
    {
        if (name == null)
        {
            return null;
        }

        final RequestDispatcher dispatcher;
        final ServletHandler servletHandler = this.handlerRegistry.resolveServletByName(name);
        if ( servletHandler != null )
        {
        	final ServletResolution resolution = new ServletResolution();
        	resolution.handler = servletHandler;
            resolution.handlerRegistry = this.handlerRegistry;
            final RequestInfo requestInfo = new RequestInfo("", null, null, null, name,
                    "", "", MappingMatch.EXACT, true);
            dispatcher = new RequestDispatcherImpl(resolution, requestInfo);
        }
        else
        {
        	dispatcher = null;
        }
        return dispatcher;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path)
    {
        // See section 9.1 of Servlet 3.x specification...
        if (path == null || (!path.startsWith("/") && !"".equals(path)))
        {
            return null;
        }

        String query = null;
        int q = 0;
        if ((q = path.indexOf('?')) > 0)
        {
            query = path.substring(q + 1);
            path = path.substring(0, q);
        }
        final String encodedRequestURI = path == null ? "" : removeDotSegments(path);
        final String requestURI = decodePath(encodedRequestURI);

        final RequestDispatcher dispatcher;
        final PathResolution pathResolution = this.handlerRegistry.resolve(requestURI);
        if ( pathResolution != null )
        {
            pathResolution.handlerRegistry = this.handlerRegistry;
            final RequestInfo requestInfo = new RequestInfo(pathResolution.servletPath,
                    pathResolution.pathInfo,
                    query,
                    UriUtils.concat(this.getContextPath(), encodedRequestURI),
                    pathResolution.handler.getName(),
                    pathResolution.matchedPattern,
                    pathResolution.matchValue, pathResolution.match, false);
            dispatcher = new RequestDispatcherImpl(pathResolution, requestInfo);
        }
        else
        {
        	dispatcher = null;
        }
        return dispatcher;
    }


    @Override
    public Dynamic addJspFile(final String servletName, final String jspFile) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSessionTimeout() {
        return context.getSessionTimeout();
    }

    @Override
    public void setSessionTimeout(final int sessionTimeout) {
        throw new IllegalStateException();
    }

    @Override
    public String getRequestCharacterEncoding() {
        return context.getRequestCharacterEncoding();
    }

    @Override
    public void setRequestCharacterEncoding(final String encoding) {
        throw new IllegalStateException();
    }

    @Override
    public String getResponseCharacterEncoding() {
        return context.getResponseCharacterEncoding();
    }

    @Override
    public void setResponseCharacterEncoding(final String encoding) {
        throw new IllegalStateException();
    }

    @Override
    public HttpConfig getConfig()
    {
        return this.handlerRegistry.getConfig();
    }

    private String normalizePath(String path)
    {
        if (path == null)
        {
            return null;
        }

        String normalizedPath = normalizeResourcePath(path);
        if (normalizedPath.startsWith("/") && (normalizedPath.length() > 1))
        {
            normalizedPath = normalizedPath.substring(1);
        }

        return normalizedPath;
    }

    private String normalizeResourcePath(String path)
    {
        if ( path == null)
        {
            return null;
        }
        String normalizedPath = path.trim().replaceAll("/+", "/");

        return normalizedPath;
    }
}

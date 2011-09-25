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

import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.apache.felix.webconsole.BrandingPlugin;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.felix.webconsole.WebConsoleSecurityProvider;
import org.apache.felix.webconsole.internal.OsgiManagerPlugin;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.core.BundlesServlet;
import org.apache.felix.webconsole.internal.filter.FilteringResponseWrapper;
import org.apache.felix.webconsole.internal.i18n.ResourceBundleManager;
import org.apache.felix.webconsole.internal.misc.ConfigurationRender;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The <code>OSGi Manager</code> is the actual Web Console Servlet which
 * is registered with the OSGi Http Service and which maintains registered
 * console plugins.
 */
public class OsgiManager extends GenericServlet
{

    /** Pseudo class version ID to keep the IDE quite. */
    private static final long serialVersionUID = 1L;

    /**
     * Old name of the request attribute providing the root to the web console.
     * This attribute is no deprecated and replaced by
     * {@link WebConsoleConstants#ATTR_APP_ROOT}.
     *
     * @deprecated use {@link WebConsoleConstants#ATTR_APP_ROOT} instead
     */
    private static final String ATTR_APP_ROOT_OLD = OsgiManager.class.getName()
        + ".appRoot";

    /**
     * Old name of the request attribute providing the mappings from label to
     * page title. This attribute is no deprecated and replaced by
     * {@link WebConsoleConstants#ATTR_LABEL_MAP}.
     *
     * @deprecated use {@link WebConsoleConstants#ATTR_LABEL_MAP} instead
     */
    private static final String ATTR_LABEL_MAP_OLD = OsgiManager.class.getName()
        + ".labelMap";

    /**
     * The name and value of a parameter which will prevent redirection to a
     * render after the action has been executed (value is "_noredir_"). This
     * may be used by programmatic action submissions.
     */
    public static final String PARAM_NO_REDIRECT_AFTER_ACTION = "_noredir_";

    /**
     * The name of the cookie storing user-configured locale
     * See https://issues.apache.org/jira/browse/FELIX-2267
     */
    private static final String COOKIE_LOCALE = "felix.webconsole.locale"; //$NON-NLS-1$

    static final String PROP_MANAGER_ROOT = "manager.root"; //$NON-NLS-1$

    static final String PROP_DEFAULT_RENDER = "default.render"; //$NON-NLS-1$

    static final String PROP_REALM = "realm"; //$NON-NLS-1$

    static final String PROP_USER_NAME = "username"; //$NON-NLS-1$

    static final String PROP_PASSWORD = "password"; //$NON-NLS-1$

    static final String PROP_ENABLED_PLUGINS = "plugins"; //$NON-NLS-1$

    static final String PROP_LOG_LEVEL = "loglevel"; //$NON-NLS-1$

    static final String PROP_LOCALE = "locale"; //$NON-NLS-1$

    static final String PROP_HTTP_SERVICE_SELECTOR = "http.service.filter"; //$NON-NLS-1$

    public static final int DEFAULT_LOG_LEVEL = LogService.LOG_WARNING;

    static final String DEFAULT_PAGE = BundlesServlet.NAME;

    static final String DEFAULT_REALM = "OSGi Management Console"; //$NON-NLS-1$

    static final String DEFAULT_USER_NAME = "admin"; //$NON-NLS-1$

    static final String DEFAULT_PASSWORD = "admin"; //$NON-NLS-1$

    static final String DEFAULT_HTTP_SERVICE_SELECTOR = ""; //$NON-NLS-1$

    /**
     * The default value for the {@link #PROP_MANAGER_ROOT} configuration
     * property (value is "/system/console").
     */
    static final String DEFAULT_MANAGER_ROOT = "/system/console"; //$NON-NLS-1$

    static final String[] PLUGIN_CLASSES = {
            "org.apache.felix.webconsole.internal.compendium.ConfigurationAdminConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.compendium.PreferencesConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.compendium.WireAdminConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.core.BundlesConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.core.PermissionsConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.core.ServicesConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.misc.SystemPropertiesPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.misc.ThreadPrinter", }; //$NON-NLS-1$

    static final String[] PLUGIN_MAP = {
            "org.apache.felix.webconsole.internal.compendium.ConfigManager", "configMgr", //$NON-NLS-1$ //$NON-NLS-2$
            "org.apache.felix.webconsole.internal.compendium.LogServlet", "logs", //$NON-NLS-1$ //$NON-NLS-2$
            "org.apache.felix.webconsole.internal.core.BundlesServlet", "bundles", //$NON-NLS-1$ //$NON-NLS-2$
            "org.apache.felix.webconsole.internal.core.ServicesServlet", "services", //$NON-NLS-1$ //$NON-NLS-2$
            "org.apache.felix.webconsole.internal.misc.LicenseServlet", "licenses", //$NON-NLS-1$ //$NON-NLS-2$
            "org.apache.felix.webconsole.internal.system.VMStatPlugin", "vmstat", //$NON-NLS-1$ //$NON-NLS-2$
    };

    private BundleContext bundleContext;

    private HttpServiceTracker httpServiceTracker;

    private HttpService httpService;

    private PluginHolder holder;

    private ServiceTracker brandingTracker;

    private ServiceTracker securityProviderTracker;

    private ServiceRegistration configurationListener;

    // list of OsgiManagerPlugin instances activated during init. All these
    // instances will have to be deactivated during destroy
    private List osgiManagerPlugins = new ArrayList();

    private String webManagerRoot;

    // true if the OsgiManager is registered as a Servlet with the HttpService
    private boolean httpServletRegistered;

    // true if the resources have been registered with the HttpService
    private boolean httpResourcesRegistered;

    private Dictionary configuration;

    // See https://issues.apache.org/jira/browse/FELIX-2267
    private Locale configuredLocale;

    private Set enabledPlugins;

    ResourceBundleManager resourceBundleManager;

    private int logLevel = DEFAULT_LOG_LEVEL;

    public OsgiManager(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;
        this.holder = new PluginHolder(bundleContext);

        // new plugins setup
        for (int i = 0; i < PLUGIN_MAP.length; i++)
        {
            final String pluginClassName = PLUGIN_MAP[i++];
            final String label = PLUGIN_MAP[i];
            holder.addInternalPlugin(this, pluginClassName, label);
        }

        // setup the included plugins
        ClassLoader classLoader = getClass().getClassLoader();
        for (int i = 0; i < PLUGIN_CLASSES.length; i++)
        {
            String pluginClassName = PLUGIN_CLASSES[i];

            try
            {
                Class pluginClass = classLoader.loadClass(pluginClassName);
                Object plugin = pluginClass.newInstance();

                if (plugin instanceof OsgiManagerPlugin)
                {
                    ((OsgiManagerPlugin) plugin).activate(bundleContext);
                    osgiManagerPlugins.add(plugin);
                }
                if (plugin instanceof BrandingPlugin)
                {
                    AbstractWebConsolePlugin.setBrandingPlugin((BrandingPlugin) plugin);
                }
            }
            catch (NoClassDefFoundError ncdfe)
            {
                String message = ncdfe.getMessage();
                if (message == null)
                {
                    // no message, construct it
                    message = "Class definition not found (NoClassDefFoundError)";
                }
                else if (message.indexOf(' ') < 0)
                {
                    // message is just a class name, try to be more descriptive
                    message = "Class " + message + " missing";
                }
                log(LogService.LOG_INFO, pluginClassName + " not enabled. Reason: "
                    + message);
            }
            catch (Throwable t)
            {
                log(LogService.LOG_INFO, "Failed to instantiate plugin "
                    + pluginClassName + ". Reason: " + t);
            }
        }

        // the resource bundle manager
        resourceBundleManager = new ResourceBundleManager(getBundleContext());

        // start the configuration render, providing the resource bundle manager
        ConfigurationRender cr = new ConfigurationRender(resourceBundleManager);
        cr.activate(bundleContext);
        osgiManagerPlugins.add(cr);
        holder.addOsgiManagerPlugin(cr);

        // start tracking external plugins after setting up our own plugins
        holder.open();

        // accept new console branding service
        brandingTracker = new BrandingServiceTracker(this);
        brandingTracker.open();

        // add support for pluggable security
        securityProviderTracker = new ServiceTracker(bundleContext,
            WebConsoleSecurityProvider.class.getName(), null);
        securityProviderTracker.open();

        // configure and start listening for configuration
        updateConfiguration(null);

        try
        {
            this.configurationListener = ConfigurationListener2.create(this);
        }
        catch (Throwable t2)
        {
            // might be caused by Metatype API not available
            // try without MetaTypeProvider
            try
            {
                this.configurationListener = ConfigurationListener.create(this);
            }
            catch (Throwable t)
            {
                // might be caused by CM API not available
            }
        }
    }

    public void dispose()
    {
        // dispose off held plugins
        holder.close();

        // dispose off the resource bundle manager
        if (resourceBundleManager != null)
        {
            resourceBundleManager.dispose();
            resourceBundleManager = null;
        }

        // stop listening for brandings
        if (brandingTracker != null)
        {
            brandingTracker.close();
            brandingTracker = null;
        }

        // deactivate any remaining plugins
        for (Iterator pi = osgiManagerPlugins.iterator(); pi.hasNext();)
        {
            Object plugin = pi.next();
            ((OsgiManagerPlugin) plugin).deactivate();
        }

        // simply remove all operations, we should not be used anymore
        this.osgiManagerPlugins.clear();

        // now drop the HttpService and continue with further destroyals
        if (httpServiceTracker != null)
        {
            httpServiceTracker.close();
            httpServiceTracker = null;
        }

        // stop listening for configuration
        if (configurationListener != null)
        {
            configurationListener.unregister();
            configurationListener = null;
        }

        // stop tracking security provider
        if (securityProviderTracker != null)
        {
            securityProviderTracker.close();
            securityProviderTracker = null;
        }

        this.bundleContext = null;
    }

    //---------- Servlet API

    /**
     * @see javax.servlet.GenericServlet#init()
     */
    public void init()
    {
        // base class initialization not needed, since the GenericServlet.init
        // is an empty method

        holder.setServletContext(getServletContext());

    }

    /**
     * @see javax.servlet.GenericServlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    public void service(final ServletRequest req, final ServletResponse res)
        throws ServletException, IOException
    {
        // don't really expect to be called within a non-HTTP environment
        service((HttpServletRequest) req, (HttpServletResponse) res);

        // ensure response has been sent back and response is committed
        // (we are authorative for our URL space and no other servlet should interfere)
        res.flushBuffer();
    }

    private void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        // check whether we are not at .../{webManagerRoot}
        final String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) //$NON-NLS-1$
        {
            String path = request.getRequestURI();
            if (!path.endsWith("/")) //$NON-NLS-1$
            {
                path = path.concat("/"); //$NON-NLS-1$
            }
            path = path.concat(holder.getDefaultPluginLabel());
            response.sendRedirect(path);
            return;
        }

        int slash = pathInfo.indexOf("/", 1);
        if (slash < 2)
        {
            slash = pathInfo.length();
        }

        final Locale locale = getConfiguredLocale(request);
        final String label = pathInfo.substring(1, slash);
        AbstractWebConsolePlugin plugin = getConsolePlugin(label);
        if (plugin != null)
        {
            final Map labelMap = holder.getLocalizedLabelMap(resourceBundleManager,
                locale);

            // the official request attributes
            request.setAttribute(WebConsoleConstants.ATTR_LANG_MAP, getLangMap());
            request.setAttribute(WebConsoleConstants.ATTR_LABEL_MAP, labelMap);
            request.setAttribute(WebConsoleConstants.ATTR_APP_ROOT,
                request.getContextPath() + request.getServletPath());
            request.setAttribute(WebConsoleConstants.ATTR_PLUGIN_ROOT,
                request.getContextPath() + request.getServletPath() + '/' + label);

            // deprecated request attributes
            request.setAttribute(ATTR_LABEL_MAP_OLD, labelMap);
            request.setAttribute(ATTR_APP_ROOT_OLD,
                request.getContextPath() + request.getServletPath());

            // wrap the response for localization and template variable replacement
            request = wrapRequest(request, locale);
            response = wrapResponse(request, response, plugin);

            plugin.service(request, response);
        }
        else
        {
            final String body404 = MessageFormat.format(
                resourceBundleManager.getResourceBundle(bundleContext.getBundle(), locale).getString(
                    "404"), //$NON-NLS-1$
                new Object[] { request.getContextPath() + request.getServletPath() + '/'
                    + BundlesServlet.NAME });
            response.setCharacterEncoding("utf-8"); //$NON-NLS-1$
            response.setContentType("text/html"); //$NON-NLS-1$
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().println(body404);
        }
    }

    private final AbstractWebConsolePlugin getConsolePlugin(final String label)
    {
        // backwards compatibility for the former "install" action which is
        // used by the Maven Sling Plugin
        if ("install".equals(label)) //$NON-NLS-1$
        {
            return holder.getPlugin(BundlesServlet.NAME);
        }

        AbstractWebConsolePlugin plugin = holder.getPlugin( label );
        if ( plugin == null && label.indexOf( '.' ) > 0 )
        {
            int last = 0;
            for ( int dot = label.indexOf( '.', last ); plugin == null && dot > last; last = dot + 1, dot = label
                .indexOf( '.', last ) )
            {
                final String pluginLabel = label.substring( 0, dot );
                plugin = holder.getPlugin( pluginLabel );
            }
        }
        return plugin;
    }

    // See https://issues.apache.org/jira/browse/FELIX-2267
    private final Locale getConfiguredLocale(HttpServletRequest request)
    {
        Locale locale = null;

        Cookie[] cookies = request.getCookies();
        for (int i = 0; cookies != null && i < cookies.length; i++)
        {
            if (COOKIE_LOCALE.equals(cookies[i].getName()))
            {
                locale = Util.parseLocaleString(cookies[i].getValue());
                break;
            }
        }

        // TODO: check UserAdmin ?

        if (locale == null)
            locale = configuredLocale;
        if (locale == null)
            locale = request.getLocale();

        return locale;
    }

    /**
     * @see javax.servlet.GenericServlet#destroy()
     */
    public void destroy()
    {
        // base class destroy not needed, since the GenericServlet.destroy
        // is an empty method

        holder.setServletContext(null);
    }

    //---------- internal

    BundleContext getBundleContext()
    {
        return bundleContext;
    }

    /**
     * Returns the Service PID used to retrieve configuration and to describe
     * the configuration properties.
     */
    String getConfigurationPid()
    {
        return getClass().getName();
    }

    /**
     * Calls the <code>GenericServlet.log(String)</code> method if the
     * configured log level is less than or equal to the given <code>level</code>.
     * <p>
     * Note, that the <code>level</code> parameter is only used to decide whether
     * the <code>GenericServlet.log(String)</code> method is called or not. The
     * actual implementation of the <code>GenericServlet.log</code> method is
     * outside of the control of this method.
     *
     * @param level The log level at which to log the message
     * @param message The message to log
     */
    void log(int level, String message)
    {
        if (logLevel >= level)
        {
            log(message);
        }
    }

    /**
     * Calls the <code>GenericServlet.log(String, Throwable)</code> method if
     * the configured log level is less than or equal to the given
     * <code>level</code>.
     * <p>
     * Note, that the <code>level</code> parameter is only used to decide whether
     * the <code>GenericServlet.log(String, Throwable)</code> method is called
     * or not. The actual implementation of the <code>GenericServlet.log</code>
     * method is outside of the control of this method.
     *
     * @param level The log level at which to log the message
     * @param message The message to log
     * @param t The <code>Throwable</code> to log with the message
     */
    void log(int level, String message, Throwable t)
    {
        if (logLevel >= level)
        {
            log(message, t);
        }
    }

    private HttpServletRequest wrapRequest(final HttpServletRequest request,
        final Locale locale)
    {
        return new HttpServletRequestWrapper(request)
        {
            /**
             * @see javax.servlet.ServletRequestWrapper#getLocale()
             */
            public Locale getLocale()
            {
                return locale;
            }
        };
    }

    private HttpServletResponse wrapResponse(final HttpServletRequest request,
        final HttpServletResponse response, final AbstractWebConsolePlugin plugin)
    {
        final Locale locale = request.getLocale();
        final ResourceBundle resourceBundle = resourceBundleManager.getResourceBundle(
            plugin.getBundle(), locale);
        return new FilteringResponseWrapper(response, resourceBundle, request);
    }

    private static class HttpServiceTracker extends ServiceTracker
    {

        private static final String HTTP_SERVICE = "org.osgi.service.http.HttpService"; //$NON-NLS-1$

        private final OsgiManager osgiManager;

        private final String httpServiceSelector;

        static HttpServiceTracker create(OsgiManager osgiManager,
            String httpServiceSelector)
        {
            // got a service selector filter
            if (httpServiceSelector != null && httpServiceSelector.length() > 0)
            {
                try
                {
                    final String filterString = "(&(" + Constants.OBJECTCLASS + "=" //$NON-NLS-1$ //$NON-NLS-2$
                        + HTTP_SERVICE + ")(" + httpServiceSelector + "))"; //$NON-NLS-1$ //$NON-NLS-2$
                    Filter filter = osgiManager.getBundleContext().createFilter(
                        filterString);
                    return new HttpServiceTracker(osgiManager, httpServiceSelector,
                        filter);
                }
                catch (InvalidSyntaxException ise)
                {
                    // TODO: log or throw or ignore ....
                }
            }

            // no filter or illegal filter string
            return new HttpServiceTracker(osgiManager);
        }

        private HttpServiceTracker(final OsgiManager osgiManager)
        {
            super(osgiManager.getBundleContext(), HTTP_SERVICE, null);
            this.osgiManager = osgiManager;
            this.httpServiceSelector = null;
        }

        private HttpServiceTracker(final OsgiManager osgiManager, final String httpServiceSelector, final Filter httpServiceFilter)
        {
            super(osgiManager.getBundleContext(), httpServiceFilter, null);
            this.osgiManager = osgiManager;
            this.httpServiceSelector = httpServiceSelector;
        }

        boolean isSameSelector(final String newHttpServiceSelector)
        {
            if (newHttpServiceSelector != null)
            {
                return newHttpServiceSelector.equals(httpServiceSelector);
            }
            return httpServiceSelector == null;
        }

        public Object addingService(ServiceReference reference)
        {
            Object service = super.addingService(reference);
            if (service instanceof HttpService)
            {
                osgiManager.bindHttpService((HttpService) service);
            }
            return service;
        }

        public void removedService(ServiceReference reference, Object service)
        {
            if (service instanceof HttpService)
            {
                osgiManager.unbindHttpService((HttpService) service);
            }

            super.removedService(reference, service);
        }
    }

    private static class BrandingServiceTracker extends ServiceTracker
    {
        private final OsgiManager osgiManager; // FIXME: never read locally

        BrandingServiceTracker(OsgiManager osgiManager)
        {
            super(osgiManager.getBundleContext(), BrandingPlugin.class.getName(), null);
            this.osgiManager = osgiManager;
        }

        public Object addingService(ServiceReference reference)
        {
            Object plugin = super.addingService(reference);
            if (plugin instanceof BrandingPlugin)
            {
                AbstractWebConsolePlugin.setBrandingPlugin((BrandingPlugin) plugin);
            }
            return plugin;
        }

        public void removedService(ServiceReference reference, Object service)
        {
            if (service instanceof BrandingPlugin)
            {
                AbstractWebConsolePlugin.setBrandingPlugin(null);
            }
            super.removedService(reference, service);
        }

    }

    protected synchronized void bindHttpService(HttpService httpService)
    {
        // do not bind service, when we are already bound
        if (this.httpService != null)
        {
            log(LogService.LOG_DEBUG,
                "bindHttpService: Already bound to an HTTP Service, ignoring further services");
            return;
        }

        Dictionary config = getConfiguration();

        // get authentication details
        String realm = ConfigurationUtil.getProperty(config, PROP_REALM, DEFAULT_REALM);
        String userId = ConfigurationUtil.getProperty(config, PROP_USER_NAME, DEFAULT_USER_NAME);
        String password = ConfigurationUtil.getProperty(config, PROP_PASSWORD, DEFAULT_PASSWORD);

        // register the servlet and resources
        try
        {
            HttpContext httpContext = new OsgiManagerHttpContext(httpService,
                securityProviderTracker, userId, password, realm);

            Dictionary servletConfig = toStringConfig(config);

            // register this servlet and take note of this
            httpService.registerServlet(this.webManagerRoot, this, servletConfig,
                httpContext);
            httpServletRegistered = true;

            // register resources and take of this
            httpService.registerResources(this.webManagerRoot + "/res", "/res",
                httpContext);
            httpResourcesRegistered = true;

        }
        catch (Exception e)
        {
            log(LogService.LOG_ERROR, "bindHttpService: Problem setting up", e);
        }

        this.httpService = httpService;
    }

    protected synchronized void unbindHttpService(HttpService httpService)
    {
        if (this.httpService != httpService)
        {
            log(LogService.LOG_DEBUG,
                "unbindHttpService: Ignoring unbind of an HttpService to which we are not registered");
            return;
        }

        // drop the service reference
        this.httpService = null;

        if (httpResourcesRegistered)
        {
            try
            {
                httpService.unregister(this.webManagerRoot + "/res");
            }
            catch (Throwable t)
            {
                log(LogService.LOG_WARNING,
                    "unbindHttpService: Failed unregistering Resources", t);
            }
            httpResourcesRegistered = false;
        }

        if (httpServletRegistered)
        {
            try
            {
                httpService.unregister(this.webManagerRoot);
            }
            catch (Throwable t)
            {
                log(LogService.LOG_WARNING,
                    "unbindHttpService: Failed unregistering Servlet", t);
            }
            httpServletRegistered = false;
        }
    }

    private Dictionary getConfiguration()
    {
        return configuration;
    }

    synchronized void updateConfiguration(Dictionary config)
    {
        if (config == null)
        {
            config = new Hashtable();
        }

        configuration = config;

        final Object locale = config.get(PROP_LOCALE);
        configuredLocale = locale == null || locale.toString().trim().length() == 0 //
        ? null : Util.parseLocaleString(locale.toString().trim());

        logLevel = ConfigurationUtil.getProperty(config, PROP_LOG_LEVEL, DEFAULT_LOG_LEVEL);
        AbstractWebConsolePlugin.setLogLevel(logLevel);

        // default plugin page configuration
        holder.setDefaultPluginLabel(ConfigurationUtil.getProperty(config, PROP_DEFAULT_RENDER, DEFAULT_PAGE));

        // get the web manager root path
        String newWebManagerRoot = ConfigurationUtil.getProperty(config, PROP_MANAGER_ROOT, DEFAULT_MANAGER_ROOT);
        if (!newWebManagerRoot.startsWith("/")) //$NON-NLS-1$
        {
            newWebManagerRoot = "/" + newWebManagerRoot; //$NON-NLS-1$
        }

        // get the HTTP Service selector (and dispose tracker for later
        // recreation)
        final String newHttpServiceSelector = ConfigurationUtil.getProperty(config,
            PROP_HTTP_SERVICE_SELECTOR, DEFAULT_HTTP_SERVICE_SELECTOR);
        if (httpServiceTracker != null
            && !httpServiceTracker.isSameSelector(newHttpServiceSelector))
        {
            httpServiceTracker.close();
            httpServiceTracker = null;
        }

        // get enabled plugins
        String[] plugins = ConfigurationUtil.getStringArrayProperty(config, PROP_ENABLED_PLUGINS);
        enabledPlugins = null == plugins ? null : new HashSet(Arrays.asList(plugins));
        initInternalPlugins();

        // might update HTTP service registration
        HttpService httpService = this.httpService;
        if (httpService != null)
        {
            // unbind old location first
            unbindHttpService(httpService);

            // switch location
            this.webManagerRoot = newWebManagerRoot;

            // bind new location now
            bindHttpService(httpService);
        }
        else
        {
            // just set the configured location (FELIX-2034)
            this.webManagerRoot = newWebManagerRoot;
        }

        // create or recreate the HTTP service tracker with the new selector
        if (httpServiceTracker == null)
        {
            httpServiceTracker = HttpServiceTracker.create(this, newHttpServiceSelector);
            httpServiceTracker.open();
        }
    }

    private void initInternalPlugins()
    {
        for (int i = 0; i < PLUGIN_MAP.length; i++)
        {
            final String pluginClassName = PLUGIN_MAP[i++];
            final String label = PLUGIN_MAP[i];
            boolean active = holder.getPlugin(label) != null;
            boolean disabled = isPluginDisabled(pluginClassName);
            if (disabled)
            {
                if (active)
                {
                    holder.removeOsgiManagerPlugin(label);
                }
            }
            else
            {
                if (!active)
                {
                    holder.addInternalPlugin(this, pluginClassName, label);
                }
            }
        }
    }

    /**
     * Returns <code>true</code> if the list of enabled plugins is
     * configured but the plugin is not contained in that list.
     * <p>
     * This method is intended to be used only for {@link InternalPlugin#isEnabled()}
     */
    boolean isPluginDisabled(String pluginClass)
    {
        return enabledPlugins != null && !enabledPlugins.contains(pluginClass);
    }

    private Dictionary toStringConfig(Dictionary config)
    {
        Dictionary stringConfig = new Hashtable();
        for (Enumeration ke = config.keys(); ke.hasMoreElements();)
        {
            Object key = ke.nextElement();
            stringConfig.put(key.toString(), String.valueOf(config.get(key)));
        }
        return stringConfig;
    }

    private Map langMap;

    private final Map getLangMap()
    {
        if (null != langMap)
            return langMap;
        final Map map = new HashMap();
        final Bundle bundle = bundleContext.getBundle();
        final Enumeration e = bundle.findEntries("res/flags", null, false); //$NON-NLS-1$
        while (e != null && e.hasMoreElements())
        {
            final URL img = (URL) e.nextElement();
            final String name = FilenameUtils.getBaseName(img.getFile());
            try
            {
                final String locale = new Locale(name, "").getDisplayLanguage(); //$NON-NLS-1$
                map.put(name, null != locale ? locale : name);
            }
            catch (Throwable t)
            {
                t.printStackTrace();
                /* ignore invalid locale? */
            }
        }
        return langMap = map;
    }

}

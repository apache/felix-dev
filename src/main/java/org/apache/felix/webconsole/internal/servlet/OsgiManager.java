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
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.servlet.GenericServlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.apache.felix.webconsole.BrandingPlugin;
import org.apache.felix.webconsole.User;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.felix.webconsole.WebConsoleSecurityProvider;
import org.apache.felix.webconsole.WebConsoleSecurityProvider2;
import org.apache.felix.webconsole.WebConsoleSecurityProvider3;
import org.apache.felix.webconsole.internal.OsgiManagerPlugin;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.core.BundlesServlet;
import org.apache.felix.webconsole.internal.filter.FilteringResponseWrapper;
import org.apache.felix.webconsole.internal.i18n.ResourceBundleManager;
import org.apache.felix.webconsole.internal.servlet.PluginHolder.InternalPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

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
    @Deprecated
    private static final String ATTR_APP_ROOT_OLD = OsgiManager.class.getName()
        + ".appRoot";

    /**
     * Old name of the request attribute providing the mappings from label to
     * page title. This attribute is now deprecated and replaced by
     * {@link WebConsoleConstants#ATTR_LABEL_MAP}.
     *
     * @deprecated use {@link WebConsoleConstants#ATTR_LABEL_MAP} instead
     */
    @Deprecated
    private static final String ATTR_LABEL_MAP_OLD = OsgiManager.class.getName()
        + ".labelMap";

    /**
     * The name of the (internal) request attribute providing the categorized
     * label map structure.
     */
    public static final String ATTR_LABEL_MAP_CATEGORIZED = WebConsoleConstants.ATTR_LABEL_MAP + ".categorized";

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
    private static final String COOKIE_LOCALE = "felix-webconsole-locale"; //$NON-NLS-1$

    private static final String FRAMEWORK_PROP_MANAGER_ROOT = "felix.webconsole.manager.root"; //$NON-NLS-1$

    private static final String FRAMEWORK_PROP_REALM = "felix.webconsole.realm"; //$NON-NLS-1$

    private static final String FRAMEWORK_PROP_USER_NAME = "felix.webconsole.username"; //$NON-NLS-1$

    private static final String FRAMEWORK_PROP_PASSWORD = "felix.webconsole.password"; //$NON-NLS-1$

    private static final String FRAMEWORK_PROP_LOG_LEVEL = "felix.webconsole.loglevel"; //$NON-NLS-1$

    private static final String FRAMEWORK_PROP_LOCALE = "felix.webconsole.locale"; //$NON-NLS-1$

    static final String FRAMEWORK_PROP_SECURITY_PROVIDERS = "felix.webconsole.security.providers"; //$NON-NLS-1$

    static final String SECURITY_PROVIDER_PROPERTY_NAME = "webconsole.security.provider.id"; //$NON-NLS-1$

    static final String PROP_MANAGER_ROOT = "manager.root"; //$NON-NLS-1$

    static final String PROP_DEFAULT_RENDER = "default.render"; //$NON-NLS-1$

    static final String PROP_REALM = "realm"; //$NON-NLS-1$

    static final String PROP_USER_NAME = "username"; //$NON-NLS-1$

    static final String PROP_PASSWORD = "password"; //$NON-NLS-1$

    static final String PROP_CATEGORY = "category"; //$NON-NLS-1$

    static final String PROP_ENABLED_PLUGINS = "plugins"; //$NON-NLS-1$

    static final String PROP_LOG_LEVEL = "loglevel"; //$NON-NLS-1$

    static final String PROP_LOCALE = "locale"; //$NON-NLS-1$

    static final String PROP_ENABLE_SECRET_HEURISTIC = "secret.heuristic.enabled"; //$NON-NLS-1$

    static final String PROP_HTTP_SERVICE_SELECTOR = "http.service.filter"; //$NON-NLS-1$

    public static final int DEFAULT_LOG_LEVEL = LogService.LOG_WARNING;

    static final String DEFAULT_PAGE = BundlesServlet.NAME;

    static final String DEFAULT_REALM = "OSGi Management Console"; //$NON-NLS-1$

    static final String DEFAULT_USER_NAME = "admin"; //$NON-NLS-1$

    static final String DEFAULT_PASSWORD = "{sha-256}jGl25bVBBBW96Qi9Te4V37Fnqchz/Eu4qB9vKrRIqRg="; //$NON-NLS-1$

    static final String DEFAULT_CATEGORY = "Main"; //$NON-NLS-1$

    static final String DEFAULT_HTTP_SERVICE_SELECTOR = ""; //$NON-NLS-1$

    /** Default value for secret heuristics */
    public static final boolean DEFAULT_ENABLE_SECRET_HEURISTIC = false;

    private static final String HEADER_AUTHORIZATION = "Authorization"; //$NON-NLS-1$

    private static final String HEADER_WWW_AUTHENTICATE = "WWW-Authenticate"; //$NON-NLS-1$

    /**
     * The default value for the {@link #PROP_MANAGER_ROOT} configuration
     * property (value is "/system/console").
     */
    static final String DEFAULT_MANAGER_ROOT = "/system/console"; //$NON-NLS-1$

    private static final String OLD_CONFIG_MANAGER_CLASS = "org.apache.felix.webconsole.internal.compendium.ConfigManager"; //$NON-NLS-1$
    private static final String NEW_CONFIG_MANAGER_CLASS = "org.apache.felix.webconsole.internal.configuration.ConfigManager"; //$NON-NLS-1$

    static final String[] PLUGIN_CLASSES = {
            "org.apache.felix.webconsole.internal.configuration.ConfigurationAdminConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.compendium.PreferencesConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.compendium.WireAdminConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.core.BundlesConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.core.CapabilitiesPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.core.FrameworkPropertiesPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.core.PermissionsConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.core.ServicesConfigurationPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.misc.SystemPropertiesPrinter", //$NON-NLS-1$
            "org.apache.felix.webconsole.internal.misc.ThreadPrinter", }; //$NON-NLS-1$

    static final String[] PLUGIN_MAP = {
            NEW_CONFIG_MANAGER_CLASS, "configMgr", //$NON-NLS-1$ //$NON-NLS-2$
            "org.apache.felix.webconsole.internal.compendium.LogServlet", "logs", //$NON-NLS-1$ //$NON-NLS-2$
            "org.apache.felix.webconsole.internal.core.BundlesServlet", "bundles", //$NON-NLS-1$ //$NON-NLS-2$
            "org.apache.felix.webconsole.internal.core.ServicesServlet", "services", //$NON-NLS-1$ //$NON-NLS-2$
            "org.apache.felix.webconsole.internal.misc.LicenseServlet", "licenses", //$NON-NLS-1$ //$NON-NLS-2$
            "org.apache.felix.webconsole.internal.system.VMStatPlugin", "vmstat", //$NON-NLS-1$ //$NON-NLS-2$
    };

    /** Flag to control whether secret heuristics is enabled */
    public static volatile boolean ENABLE_SECRET_HEURISTICS = OsgiManager.DEFAULT_ENABLE_SECRET_HEURISTIC;

    private BundleContext bundleContext;

    private HttpServiceTracker httpServiceTracker;

    private volatile HttpService httpService;

    private PluginHolder holder;

    private ServiceTracker brandingTracker;

    private ServiceTracker<WebConsoleSecurityProvider, WebConsoleSecurityProvider> securityProviderTracker;

    private ServiceRegistration configurationListener;

    // list of OsgiManagerPlugin instances activated during init. All these
    // instances will have to be deactivated during destroy
    private List<OsgiManagerPlugin> osgiManagerPlugins = new ArrayList<>();

    private String webManagerRoot;

    // not-null when the BasicWebConsoleSecurityProvider service is registered
    private ServiceRegistration<WebConsoleSecurityProvider> basicSecurityServiceRegistration;

    // true if the OsgiManager is registered as a Servlet with the HttpService
    private boolean httpServletRegistered;

    // true if the resources have been registered with the HttpService
    private boolean httpResourcesRegistered;

    // default configuration from framework properties
    private Map<String, Object> defaultConfiguration;

    // configuration from Configuration Admin
    private volatile Map<String, Object> configuration;

    // See https://issues.apache.org/jira/browse/FELIX-2267
    private Locale configuredLocale;

    private Set<String> enabledPlugins;

    final ConcurrentSkipListSet<String> registeredSecurityProviders = new ConcurrentSkipListSet<String>();

    final Set<String> requiredSecurityProviders;

    ResourceBundleManager resourceBundleManager;

    private int logLevel = DEFAULT_LOG_LEVEL;

    private String defaultCategory = DEFAULT_CATEGORY;

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
                final Class<?> pluginClass = classLoader.loadClass(pluginClassName);
                final Object plugin = pluginClass.getDeclaredConstructor().newInstance();

                if (plugin instanceof OsgiManagerPlugin)
                {
                    final OsgiManagerPlugin p = (OsgiManagerPlugin)plugin;
                    p.activate(bundleContext);
                    osgiManagerPlugins.add(p);
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
        //ConfigurationRender cr = new ConfigurationRender(resourceBundleManager);
        //cr.activate(bundleContext);
        //osgiManagerPlugins.add(cr);
        //holder.addOsgiManagerPlugin(cr);

        // start tracking external plugins after setting up our own plugins
        holder.open();

        // accept new console branding service
        brandingTracker = new BrandingServiceTracker(this);
        brandingTracker.open();

        this.requiredSecurityProviders = splitCommaSeparatedString(bundleContext.getProperty(FRAMEWORK_PROP_SECURITY_PROVIDERS));

        // add support for pluggable security
        securityProviderTracker = new ServiceTracker<>(bundleContext, WebConsoleSecurityProvider.class,
                                          new UpdateDependenciesStateCustomizer());
        securityProviderTracker.open();

        // load the default configuration from the framework
        this.defaultConfiguration = new HashMap<>();
        this.defaultConfiguration.put( PROP_MANAGER_ROOT,
            ConfigurationUtil.getProperty( bundleContext, FRAMEWORK_PROP_MANAGER_ROOT, DEFAULT_MANAGER_ROOT ) );
        this.defaultConfiguration.put( PROP_REALM,
            ConfigurationUtil.getProperty( bundleContext, FRAMEWORK_PROP_REALM, DEFAULT_REALM ) );
        this.defaultConfiguration.put( PROP_USER_NAME,
            ConfigurationUtil.getProperty( bundleContext, FRAMEWORK_PROP_USER_NAME, DEFAULT_USER_NAME ) );
        this.defaultConfiguration.put( PROP_PASSWORD,
            ConfigurationUtil.getProperty( bundleContext, FRAMEWORK_PROP_PASSWORD, DEFAULT_PASSWORD ) );
        this.defaultConfiguration.put( PROP_LOG_LEVEL,
            ConfigurationUtil.getProperty( bundleContext, FRAMEWORK_PROP_LOG_LEVEL, DEFAULT_LOG_LEVEL ) );
        this.defaultConfiguration.put( PROP_LOCALE,
            ConfigurationUtil.getProperty( bundleContext, FRAMEWORK_PROP_LOCALE, null ) );

        // configure and start listening for configuration
        updateConfiguration(null);

        // register managed service as a service factory
        this.configurationListener = bundleContext.registerService( "org.osgi.service.cm.ManagedService", //$NON-NLS-1$
            new ServiceFactory()
            {
                @Override
                public Object getService( Bundle bundle, ServiceRegistration registration )
                {
                    /*
                     * Explicitly load the class through the class loader to dynamically
                     * wire the API if not wired yet. Implicit loading by creating a
                     * class instance does not seem to properly work wiring the API
                     * in time.
                     */
                    try
                    {
                        OsgiManager.this.getClass().getClassLoader()
                            .loadClass( "org.osgi.service.metatype.MetaTypeProvider" );
                        return new ConfigurationMetatypeSupport( OsgiManager.this );
                    }
                    catch ( ClassNotFoundException cnfe )
                    {
                        // ignore
                    }

                    return new ConfigurationSupport( OsgiManager.this );
                }


                @Override
                public void ungetService( Bundle bundle, ServiceRegistration registration, Object service )
                {
                    // do nothing
                }
            }, new Hashtable<String, Object>()
            {
                {
                    put( Constants.SERVICE_VENDOR, "The Apache Software Foundation" ); //$NON-NLS-1$
                    put( Constants.SERVICE_DESCRIPTION, "OSGi Management Console Configuration Receiver" ); //$NON-NLS-1$
                    put( Constants.SERVICE_PID, getConfigurationPid() );
                }
            } );
    }

    void updateRegistrationState() {
        if (this.httpService != null) {
            if (this.registeredSecurityProviders.containsAll(this.requiredSecurityProviders)) {
                // register HTTP service
                registerHttpService();
                return;
            } else {
                log(LogService.LOG_INFO, "Not all requirements met for the Web Console. Required security providers: "
                        + this.registeredSecurityProviders + " Registered security providers: " + this.registeredSecurityProviders);
            }
        }
        // Not all requirements met, unregister service.
        unregisterHttpService();
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
        for (Iterator<OsgiManagerPlugin> pi = osgiManagerPlugins.iterator(); pi.hasNext();)
        {
            OsgiManagerPlugin plugin = pi.next();
            plugin.deactivate();
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
    @Override
    public void init()
    {
        // base class initialization not needed, since the GenericServlet.init
        // is an empty method

        holder.setServletContext(getServletContext());

    }

    /**
     * @see javax.servlet.GenericServlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    @Override
    public void service(final ServletRequest req, final ServletResponse res)
        throws ServletException, IOException
    {
        // don't really expect to be called within a non-HTTP environment
        try
        {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>()
            {
                @Override
                public Object run() throws Exception
                {
                    service((HttpServletRequest) req, (HttpServletResponse) res);
                    return null;
                }
            });
        }
        catch (PrivilegedActionException e)
        {
            Exception x = e.getException();
            if (x instanceof IOException)
            {
                throw (IOException) x;
            }
            else if (x instanceof ServletException)
            {
                throw (ServletException) x;
            }
            else
            {
                throw new IOException(x.toString());
            }
        }
    }

    private void ensureLocaleCookieSet(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        Cookie[] cookies = request.getCookies();
        boolean hasCookie = false;
        for(int i=0; cookies != null && i<cookies.length;i++) {
            if (COOKIE_LOCALE.equals(cookies[i].getName()) ) {
                hasCookie = true;
                break;
            }
        }
        if (!hasCookie) {
            Cookie cookie = new Cookie(COOKIE_LOCALE, locale.toString());
            cookie.setPath((String)request.getAttribute(WebConsoleConstants.ATTR_APP_ROOT));
            cookie.setMaxAge(20 * 365 * 24 * 60 * 60); // 20 years
            response.addCookie(cookie);
        }
    }

    void service(HttpServletRequest request, HttpServletResponse response)
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
            response.setContentLength(0);
            response.sendRedirect(path);
            return;
        }

        if (pathInfo.equals("/logout")) { //$NON-NLS-1$
            logout(request, response);
            return;
        }

        int slash = pathInfo.indexOf("/", 1); //$NON-NLS-1$
        if (slash < 2)
        {
            slash = pathInfo.length();
        }

        final Locale locale = getConfiguredLocale(request);
        final String label = pathInfo.substring(1, slash);
        AbstractWebConsolePlugin plugin = getConsolePlugin(label);
        if (plugin != null)
        {
            final Map labelMap = holder.getLocalizedLabelMap( resourceBundleManager, locale, this.defaultCategory );
            final Object flatLabelMap = labelMap.remove( WebConsoleConstants.ATTR_LABEL_MAP );

            // the official request attributes
            request.setAttribute(WebConsoleConstants.ATTR_LANG_MAP, getLangMap());
            request.setAttribute(WebConsoleConstants.ATTR_LABEL_MAP, flatLabelMap);
            request.setAttribute( ATTR_LABEL_MAP_CATEGORIZED, labelMap );
            request.setAttribute(WebConsoleConstants.ATTR_APP_ROOT,
                request.getContextPath() + request.getServletPath());
            request.setAttribute(WebConsoleConstants.ATTR_PLUGIN_ROOT,
                request.getContextPath() + request.getServletPath() + '/' + label);

            // deprecated request attributes
            request.setAttribute(ATTR_LABEL_MAP_OLD, flatLabelMap);
            request.setAttribute(ATTR_APP_ROOT_OLD,
                request.getContextPath() + request.getServletPath());

            // fix for https://issues.apache.org/jira/browse/FELIX-3408
            ensureLocaleCookieSet(request, response, locale);

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

    private final void logout(HttpServletRequest request, HttpServletResponse response)
        throws IOException
    {
        // check if special logout cookie is set, this is used to prevent
        // from an endless loop with basic auth
        Cookie[] cookies = request.getCookies();
        boolean found = false;
        if ( cookies != null )
        {
            for(int i=0;i<cookies.length;i++)
            {
                if ( cookies[i].getName().equals("logout") ) //$NON-NLS-1$
                {
                    found = true;
                    break;
                }
            }
        }
        if ( found )
        {
            // redirect to main page
            String url = request.getRequestURI();
            final int lastSlash = url.lastIndexOf('/');
            final Cookie c = new Cookie("logout", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            c.setMaxAge(0);
            response.addCookie(c);
            response.sendRedirect(url.substring(0, lastSlash));
            return;
        }
        Object securityProvider = securityProviderTracker.getService();
        if (securityProvider instanceof WebConsoleSecurityProvider3)
        {
            ((WebConsoleSecurityProvider3) securityProvider).logout(request, response);
        }
        else
        {
            // if the security provider doesn't support logout, we try to
            // logout the default basic authentication mechanism
            // See https://issues.apache.org/jira/browse/FELIX-3006

            // check for basic authentication
            String auth = request.getHeader(HEADER_AUTHORIZATION); //$NON-NLS-1$
            if (null != auth && auth.toLowerCase().startsWith("basic ")) { //$NON-NLS-1$
                Map<String, Object> config = getConfiguration();
                String realm = ConfigurationUtil.getProperty(config, PROP_REALM, DEFAULT_REALM);
                response.setHeader(HEADER_WWW_AUTHENTICATE, "Basic realm=\"" +  realm + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                response.addCookie(new Cookie("logout", "true")); //$NON-NLS-1$ //$NON-NLS-2$
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
        }

        // clean-up
        request.removeAttribute(HttpContext.REMOTE_USER);
        request.removeAttribute(HttpContext.AUTHORIZATION);
        request.removeAttribute(WebConsoleSecurityProvider2.USER_ATTRIBUTE);
        request.removeAttribute(User.USER_ATTRIBUTE);
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
        // this should never happen as request.getLocale()
        // must return a locale (and not null). But just as a sanity check.
        if (locale == null)
            locale = Locale.ENGLISH;

        return locale;
    }

    /**
     * @see javax.servlet.GenericServlet#destroy()
     */
    @Override
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
     * Calls the <code>ServletContext.log(String)</code> method if the
     * configured log level is less than or equal to the given <code>level</code>.
     * <p>
     * Note, that the <code>level</code> parameter is only used to decide whether
     * the <code>GenericServlet.log(String)</code> method is called or not. The
     * actual implementation of the <code>GenericServlet.log</code> method is
     * outside of the control of this method.
     * <p>
     * If the servlet has not been initialized yet or has already been destroyed
     * the message is printed to stderr.
     *
     * @param level The log level at which to log the message
     * @param message The message to log
     */
    void log(int level, String message)
    {
        if (logLevel >= level)
        {
            ServletConfig config = getServletConfig();
            if ( config != null )
            {
                ServletContext context = config.getServletContext();
                if ( context != null )
                {
                    context.log( message );
                    return;
                }
            }

            System.err.println( message );
        }
    }

    /**
     * Calls the <code>ServletContext.log(String, Throwable)</code> method if
     * the configured log level is less than or equal to the given
     * <code>level</code>.
     * <p>
     * Note, that the <code>level</code> parameter is only used to decide whether
     * the <code>GenericServlet.log(String, Throwable)</code> method is called
     * or not. The actual implementation of the <code>GenericServlet.log</code>
     * method is outside of the control of this method.
     * <p>
     * If the servlet has not been initialized yet or has already been destroyed
     * the message is printed to stderr.
     *
     * @param level The log level at which to log the message
     * @param message The message to log
     * @param t The <code>Throwable</code> to log with the message
     */
    void log(int level, String message, Throwable t)
    {
        if (logLevel >= level)
        {
            ServletConfig config = getServletConfig();
            if ( config != null )
            {
                ServletContext context = config.getServletContext();
                if ( context != null )
                {
                    context.log( message, t );
                    return;
                }
            }

            System.err.println( message );
            if ( t != null )
            {
                t.printStackTrace( System.err );
            }
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
            @Override
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

    private static class HttpServiceTracker extends ServiceTracker<HttpService, HttpService>
    {
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
                        + HttpService.class.getName() + ")(" + httpServiceSelector + "))"; //$NON-NLS-1$ //$NON-NLS-2$
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
            super(osgiManager.getBundleContext(), HttpService.class, null);
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

        @Override
        public HttpService addingService(ServiceReference<HttpService> reference)
        {
            HttpService service = super.addingService(reference);
            osgiManager.bindHttpService(service);
            return service;
        }

        @Override
        public void removedService(ServiceReference<HttpService> reference, HttpService service)
        {
            osgiManager.unbindHttpService(service);

            super.removedService(reference, service);
        }
    }

    private static class BrandingServiceTracker extends ServiceTracker<BrandingPlugin, BrandingPlugin>
    {
        BrandingServiceTracker(OsgiManager osgiManager)
        {
            super(osgiManager.getBundleContext(), BrandingPlugin.class, null);
        }

        @Override
        public BrandingPlugin addingService(ServiceReference<BrandingPlugin> reference)
        {
            BrandingPlugin plugin = super.addingService(reference);
            AbstractWebConsolePlugin.setBrandingPlugin( plugin);
            return plugin;
        }

        @Override
        public void removedService(ServiceReference<BrandingPlugin> reference, BrandingPlugin service)
        {
            AbstractWebConsolePlugin.setBrandingPlugin(null);
            super.removedService(reference, service);
        }

    }

    protected void bindHttpService(HttpService httpService)
    {
        // do not bind service, when we are already bound
        if (this.httpService != null)
        {
            log(LogService.LOG_DEBUG,
                "bindHttpService: Already bound to an HTTP Service, ignoring further services");
            return;
        }

        this.httpService = httpService;
        updateRegistrationState();
    }

    synchronized void registerHttpService() {
        Map<String, Object> config = getConfiguration();

        // get authentication details
        String realm = ConfigurationUtil.getProperty(config, PROP_REALM, DEFAULT_REALM);
        String userId = ConfigurationUtil.getProperty(config, PROP_USER_NAME, DEFAULT_USER_NAME);
        String password = ConfigurationUtil.getProperty(config, PROP_PASSWORD, DEFAULT_PASSWORD);

        // register the servlet and resources
        try
        {
            HttpContext httpContext = new OsgiManagerHttpContext(httpService,
                securityProviderTracker, realm);

            Dictionary<String, String> servletConfig = toStringConfig(config);

            if (basicSecurityServiceRegistration == null) {
                //register this component
                BasicWebConsoleSecurityProvider service = new BasicWebConsoleSecurityProvider(bundleContext,
                        userId, password, realm);
                Dictionary<String, Object> serviceProperties = new Hashtable<>(); // NOSONAR
                // this is a last resort service, so use a low service ranking to prefer all other services over this one
                serviceProperties.put(Constants.SERVICE_RANKING, Integer.MIN_VALUE);
                basicSecurityServiceRegistration = bundleContext.registerService(WebConsoleSecurityProvider.class,
                        service, serviceProperties);
            }

            if (!httpServletRegistered) {
                // register this servlet and take note of this
                httpService.registerServlet(this.webManagerRoot, this, servletConfig,
                    httpContext);
                httpServletRegistered = true;
            }

            if (!httpResourcesRegistered) {
                // register resources and take of this
                httpService.registerResources(this.webManagerRoot + "/res", "/res",
                    httpContext);
                httpResourcesRegistered = true;
            }
        }
        catch (Exception e)
        {
            log(LogService.LOG_ERROR, "bindHttpService: Problem setting up", e);
        }
    }

    protected void unbindHttpService(HttpService httpService)
    {
        if (this.httpService != httpService)
        {
            log(LogService.LOG_DEBUG,
                "unbindHttpService: Ignoring unbind of an HttpService to which we are not registered");
            return;
        }

        unregisterHttpService();

        // drop the service reference
        this.httpService = null;
    }

    synchronized void unregisterHttpService() {
        if (httpService == null)
            return;

        if (basicSecurityServiceRegistration != null) {
            try {
                basicSecurityServiceRegistration.unregister();
            } catch (Throwable t) {
                log(LogService.LOG_WARNING,
                        "unbindHttpService: Failed unregistering basic WebConsoleSecurityProvider", t);
            }
            basicSecurityServiceRegistration = null;
        }

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


    private Map<String, Object> getConfiguration()
    {
        return configuration;
    }


    Map<String, Object> getDefaultConfiguration()
    {
        return defaultConfiguration;
    }


    synchronized void updateConfiguration( Dictionary<String, Object> osgiConfig )
    {
        Map<String, Object> config = new HashMap<String, Object>( this.defaultConfiguration );

        if ( osgiConfig != null )
        {
            for ( Enumeration<String> keys = osgiConfig.keys(); keys.hasMoreElements(); )
            {
                final String key = keys.nextElement();
                config.put( key, osgiConfig.get( key ) );
            }
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

        // default category
        this.defaultCategory = ConfigurationUtil.getProperty( config, PROP_CATEGORY, DEFAULT_CATEGORY );

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

        // secret heuristics
        final boolean enableHeuristics = ConfigurationUtil.getProperty(config, PROP_ENABLE_SECRET_HEURISTIC, DEFAULT_ENABLE_SECRET_HEURISTIC);
        OsgiManager.ENABLE_SECRET_HEURISTICS = enableHeuristics;

        // get enabled plugins
        String[] plugins = ConfigurationUtil.getStringArrayProperty(config, PROP_ENABLED_PLUGINS);
        enabledPlugins = null == plugins ? null : new HashSet<String>(Arrays.asList(plugins));
        // check for moved config manager class (see FELIX-4074)
        if ( enabledPlugins != null )
        {
            if ( enabledPlugins.remove(OLD_CONFIG_MANAGER_CLASS) )
            {
                enabledPlugins.add(NEW_CONFIG_MANAGER_CLASS);
            }
        }
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
        return enabledPlugins != null && !enabledPlugins.contains( pluginClass );
    }


    private Dictionary<String, String> toStringConfig( Map<String, Object> config )
    {
        Dictionary<String, String> stringConfig = new Hashtable<>();
        for ( Iterator<Map.Entry<String, Object>> ei = config.entrySet().iterator(); ei.hasNext(); )
        {
            Entry<String, Object> entry = ei.next();
            stringConfig.put( entry.getKey(), String.valueOf( entry.getValue() ) );
        }
        return stringConfig;
    }

    static Set<String> splitCommaSeparatedString(final String str) {
        if (str == null)
            return Collections.emptySet();

        final Set<String> values = new HashSet<String>();
        for (final String s : str.split(",")) {
            String trimmed = s.trim();
            if (trimmed.length() > 0) {
                values.add(trimmed);
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private Map<String, String> langMap;


    private final Map<String, String> getLangMap()
    {
        if (null != langMap)
            return langMap;
        final Map<String, String> map = new HashMap<>();
        final Bundle bundle = bundleContext.getBundle();
        final Enumeration<URL> e = bundle.findEntries("res/flags", null, false); //$NON-NLS-1$
        while (e != null && e.hasMoreElements())
        {
            final URL img = e.nextElement();
            final String path = img.getPath();
            try {
                final int lastSlash = path.lastIndexOf('/');
                final int dot = path.indexOf('.', lastSlash);
                final String name = (dot == -1 ? path.substring(lastSlash+1) : path.substring(lastSlash + 1, dot));
                final String locale = new Locale(name, "").getDisplayLanguage(); //$NON-NLS-1$
                map.put(name, null != locale ? locale : name);
            }
            catch (Throwable t) {
                // Ignore invalid locale?
            }
        }
        return langMap = map;
    }

    class UpdateDependenciesStateCustomizer implements ServiceTrackerCustomizer<WebConsoleSecurityProvider, WebConsoleSecurityProvider> {
        @Override
        public WebConsoleSecurityProvider addingService(ServiceReference<WebConsoleSecurityProvider> reference) {
            Object nameObj = reference.getProperty(SECURITY_PROVIDER_PROPERTY_NAME);
            if (nameObj instanceof String) {
                String name = (String) nameObj;
                registeredSecurityProviders.add(name);
                updateRegistrationState();
            }
            return bundleContext.getService(reference);
        }

        @Override
        public void modifiedService(ServiceReference<WebConsoleSecurityProvider> reference, WebConsoleSecurityProvider service) {
            removedService(reference, service);
            addingService(reference);
        }

        @Override
        public void removedService(ServiceReference<WebConsoleSecurityProvider> reference, WebConsoleSecurityProvider service) {
            Object nameObj = reference.getProperty(SECURITY_PROVIDER_PROPERTY_NAME);
            if (nameObj instanceof String) {
                String name = (String) nameObj;
                registeredSecurityProviders.remove(name);
                updateRegistrationState();
            }
        }

    }
}

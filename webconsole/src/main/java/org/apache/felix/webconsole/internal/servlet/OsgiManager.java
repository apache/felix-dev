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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.felix.webconsole.servlet.User;
import org.apache.felix.webconsole.internal.OsgiManagerPlugin;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.core.BundlesServlet;
import org.apache.felix.webconsole.internal.filter.FilteringResponseWrapper;
import org.apache.felix.webconsole.internal.i18n.ResourceBundleManager;
import org.apache.felix.webconsole.servlet.RequestVariableResolver;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.apache.felix.webconsole.spi.BrandingPlugin;
import org.apache.felix.webconsole.spi.SecurityProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.servlet.context.ServletContextHelper;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The <code>OSGi Manager</code> is the actual Web Console Servlet. It is
 * registered with the OSGi Http Whiteboard Service and it manages registered
 * console plugins.
 *
 */
public class OsgiManager extends HttpServlet {

    /** Pseudo class version ID to keep the IDE quite. */
    private static final long serialVersionUID = 1L;

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
    private static final String COOKIE_LOCALE = "felix-webconsole-locale";

    private static final String FRAMEWORK_PROP_MANAGER_ROOT = "felix.webconsole.manager.root";

    private static final String FRAMEWORK_PROP_REALM = "felix.webconsole.realm";

    private static final String FRAMEWORK_PROP_USER_NAME = "felix.webconsole.username";

    private static final String FRAMEWORK_PROP_PASSWORD = "felix.webconsole.password";

    private static final String FRAMEWORK_PROP_LOCALE = "felix.webconsole.locale";

    private static final String FRAMEWORK_SHUTDOWN_TIMEOUT = "felix.webconsole.shutdown.timeout";

    private static final String FRAMEWORK_RELOAD_TIMEOUT = "felix.webconsole.reload.timeout";

    static final String FRAMEWORK_PROP_SECURITY_PROVIDERS = "felix.webconsole.security.providers";

    static final String PROP_MANAGER_ROOT = "manager.root";

    static final String PROP_DEFAULT_RENDER = "default.render";

    static final String PROP_REALM = "realm";

    static final String PROP_USER_NAME = "username";

    static final String PROP_PASSWORD = "password";

    static final String PROP_CATEGORY = "category";

    static final String PROP_ENABLED_PLUGINS = "plugins";

    static final String PROP_LOCALE = "locale";

    static final String PROP_ENABLE_SECRET_HEURISTIC = "secret.heuristic.enabled";

    static final String PROP_HTTP_SERVICE_SELECTOR = "http.service.filter";

    /** The framework shutdown timeout */
    public static final String PROP_SHUTDOWN_TIMEOUT = "shutdown.timeout";

    /** The timeout for VMStat plugin page reload */
    public static final String PROP_RELOAD_TIMEOUT = "reload.timeout";

    static final String DEFAULT_PAGE = BundlesServlet.NAME;

    static final String DEFAULT_REALM = "OSGi Management Console";

    static final String DEFAULT_USER_NAME = "admin";

    static final String DEFAULT_PASSWORD = "{sha-256}jGl25bVBBBW96Qi9Te4V37Fnqchz/Eu4qB9vKrRIqRg=";

    static final String DEFAULT_CATEGORY = "Main";

    static final int DEFAULT_SHUTDOWN_TIMEOUT = 5;

    static final int DEFAULT_RELOAD_TIMEOUT = 40;

    /** Default value for secret heuristics */
    public static final boolean DEFAULT_ENABLE_SECRET_HEURISTIC = false;

    private static final String HEADER_AUTHORIZATION = "Authorization";

    private static final String HEADER_WWW_AUTHENTICATE = "WWW-Authenticate";

    /**
     * The default value for the {@link #PROP_MANAGER_ROOT} configuration
     * property (value is "/system/console").
     */
    static final String DEFAULT_MANAGER_ROOT = "/system/console";

    static final String[] PLUGIN_CLASSES = {
            "org.apache.felix.webconsole.internal.configuration.ConfigurationAdminConfigurationPrinter",
            "org.apache.felix.webconsole.internal.compendium.PreferencesConfigurationPrinter",
            "org.apache.felix.webconsole.internal.compendium.WireAdminConfigurationPrinter",
            "org.apache.felix.webconsole.internal.core.BundlesConfigurationPrinter",
            "org.apache.felix.webconsole.internal.core.CapabilitiesPrinter",
            "org.apache.felix.webconsole.internal.core.FrameworkPropertiesPrinter",
            "org.apache.felix.webconsole.internal.core.PermissionsConfigurationPrinter",
            "org.apache.felix.webconsole.internal.core.ServicesConfigurationPrinter",
            "org.apache.felix.webconsole.internal.misc.SystemPropertiesPrinter",
            "org.apache.felix.webconsole.internal.misc.ThreadPrinter"
    };

    private static final String SERVLEXT_CONTEXT_NAME = "org.apache.felix.webconsole";

    /** Flag to control whether secret heuristics is enabled */
    public static volatile boolean ENABLE_SECRET_HEURISTICS = OsgiManager.DEFAULT_ENABLE_SECRET_HEURISTIC;

    private BundleContext bundleContext;

    private PluginHolder holder;

    private ServiceTracker<BrandingPlugin, BrandingPlugin> brandingTracker;

    private final AtomicReference<ServiceTracker<SecurityProvider, SecurityProvider>> securityProviderTracker = new AtomicReference<>();

    @SuppressWarnings("rawtypes")
    private ServiceRegistration configurationListener;

    // list of OsgiManagerPlugin instances activated during init. All these
    // instances will have to be deactivated during destroy
    private volatile List<OsgiManagerPlugin> osgiManagerPlugins = new ArrayList<>();

    private volatile String webManagerRoot;

    // not-null when the BasicWebConsoleSecurityProvider service is registered
    private ServiceRegistration<SecurityProvider> basicSecurityServiceRegistration;

    // not-null when the ServletContextHelper service is registered
    private volatile ServiceRegistration<ServletContextHelper> servletContextRegistration;

    // not-null when the main servlet and the resources are registered
    private volatile ServiceRegistration<Servlet> servletRegistration;

    // default configuration from framework properties
    private Map<String, Object> defaultConfiguration;

    // configuration from Configuration Admin
    private volatile Map<String, Object> configuration;

    // See https://issues.apache.org/jira/browse/FELIX-2267
    private volatile Locale configuredLocale;

    final ConcurrentSkipListSet<String> registeredSecurityProviders = new ConcurrentSkipListSet<String>();

    final Set<String> requiredSecurityProviders;

    final ResourceBundleManager resourceBundleManager;

    private volatile String defaultCategory = DEFAULT_CATEGORY;

    private final AtomicBoolean active = new AtomicBoolean(true);

    @SuppressWarnings("rawtypes")
    public OsgiManager(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        this.holder = new PluginHolder(this, bundleContext);

        // setup the included plugins
        for (int i = 0; i < PLUGIN_CLASSES.length; i++) {
            final String pluginClassName = PLUGIN_CLASSES[i];
            final OsgiManagerPlugin plugin = this.createInternalPlugin(pluginClassName);
            if ( plugin != null ) {
                plugin.activate(bundleContext);
                osgiManagerPlugins.add(plugin);
            }
        }

        // the resource bundle manager
        resourceBundleManager = new ResourceBundleManager(getBundleContext());

        // start tracking external plugins after setting up our own plugins
        holder.open();

        // accept new console branding service
        brandingTracker = new BrandingServiceTracker(this);
        brandingTracker.open();

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
        this.defaultConfiguration.put( PROP_LOCALE,
            ConfigurationUtil.getProperty( bundleContext, FRAMEWORK_PROP_LOCALE, null ) );
        this.defaultConfiguration.put( PROP_SHUTDOWN_TIMEOUT,
            ConfigurationUtil.getProperty( bundleContext, FRAMEWORK_SHUTDOWN_TIMEOUT, DEFAULT_SHUTDOWN_TIMEOUT ) );
        this.defaultConfiguration.put( PROP_RELOAD_TIMEOUT,
            ConfigurationUtil.getProperty( bundleContext, FRAMEWORK_RELOAD_TIMEOUT, DEFAULT_RELOAD_TIMEOUT ) );

        this.requiredSecurityProviders = splitCommaSeparatedString(bundleContext.getProperty(FRAMEWORK_PROP_SECURITY_PROVIDERS));

        // configure and start listening for configuration
        this.updateConfiguration(null);

        // add support for pluggable security
        securityProviderTracker.set(new ServiceTracker<>(bundleContext, SecurityProvider.class, new UpdateDependenciesStateCustomizer()));
        securityProviderTracker.get().open();

        // register managed service as a service factory
        this.configurationListener = bundleContext.registerService( "org.osgi.service.cm.ManagedService",
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
                    put( Constants.SERVICE_VENDOR, "The Apache Software Foundation" );
                    put( Constants.SERVICE_DESCRIPTION, "OSGi Management Console Configuration Receiver" );
                    put( Constants.SERVICE_PID, getConfigurationPid() );
                }
            } );
    }

    public OsgiManagerPlugin createInternalPlugin(final String pluginClassName) {
        try {
            final Class<?> pluginClass = getClass().getClassLoader().loadClass(pluginClassName);
            final Object plugin = pluginClass.getDeclaredConstructor().newInstance();

            if (plugin instanceof OsgiManagerPlugin) {
                final OsgiManagerPlugin p = (OsgiManagerPlugin)plugin;
                return p;
            }
        } catch (final NoClassDefFoundError ncdfe) {
            String message = ncdfe.getMessage();
            if (message == null) {
                // no message, construct it
                message = "Class definition not found (NoClassDefFoundError)";
            } else if (message.indexOf(' ') < 0) {
                // message is just a class name, try to be more descriptive
                message = "Class " + message + " missing";
            }
            Util.LOGGER.info("{} not enabled. Reason: {}", pluginClassName, message);
        } catch (final Throwable t) {
            Util.LOGGER.info("Failed to instantiate plugin: {}. Reason: {}", pluginClassName, t.getMessage(), t);
        }
        return null;
    }

    synchronized void updateRegistrationState() {
        if (this.active.get() && this.registeredSecurityProviders.containsAll(this.requiredSecurityProviders)) {
            // register servlet context helper, servlet, resources
            this.registerHttpWhiteboardServices();
        } else {
            // Not all requirements met, unregister services
            if (this.active.get()) {
                Util.LOGGER.info("Not all requirements met for the Web Console. Required security providers: {}."
                    + " Registered security providers: {}", this.registeredSecurityProviders, this.registeredSecurityProviders);
            }
            this.unregisterHttpWhiteboardServices();
        }
    }

    public void dispose() {
        // mark as disposed
        this.active.set(false);

        // remove registered http services
        this.unregisterHttpWhiteboardServices();

        // dispose off held plugins
        holder.close();

        // dispose off the resource bundle manager
        resourceBundleManager.dispose();

        // stop listening for brandings
        if (brandingTracker != null) {
            brandingTracker.close();
            brandingTracker = null;
        }

        // deactivate any remaining plugins
        for(final OsgiManagerPlugin pi : this.osgiManagerPlugins) {
            pi.deactivate();
        }
        this.osgiManagerPlugins.clear();

        // stop listening for configuration
        if (configurationListener != null) {
            configurationListener.unregister();
            configurationListener = null;
        }

        // stop tracking security provider
        final ServiceTracker<SecurityProvider, SecurityProvider> tracker = securityProviderTracker.get();
        securityProviderTracker.set(null);
        if (tracker != null) {
            tracker.close();
        }

        this.bundleContext = null;
    }

    //---------- Servlet API

    @Override
    public void init() {
        holder.setServletContext(getServletContext());
    }

    @Override
    public void service(final HttpServletRequest req, final HttpServletResponse res)
    throws ServletException, IOException {
        // don't really expect to be called within a non-HTTP environment
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws Exception {
                    final HttpServletRequest wrapper = new HttpServletRequestWrapper((HttpServletRequest) req) {
                        @Override
                        public String getServletPath() {
                            return "";
                        }

                        @Override
                        public String getPathInfo() {
                            return super.getServletPath();
                        }
                    };
                    doService(wrapper, res);
                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            Exception x = e.getException();
            if (x instanceof IOException) {
                throw (IOException) x;
            } else if (x instanceof ServletException) {
                throw (ServletException) x;
            } else {
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
            cookie.setPath((String)request.getAttribute(ServletConstants.ATTR_APP_ROOT));
            cookie.setMaxAge(20 * 365 * 24 * 60 * 60); // 20 years
            response.addCookie(cookie);
        }
    }

    void doService(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        // check whether we are not at .../{webManagerRoot}
        final String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/"))  {
            String path = request.getRequestURI();
            if (!path.endsWith("/")) {
                path = path.concat("/");
            }
            path = path.concat(holder.getDefaultPluginLabel());
            response.sendRedirect(path);
            return;
        }

        final Locale locale = getConfiguredLocale(request);

        // make sure to set the variable resolver
        initRequest(request, "", locale);

        if (pathInfo.equals("/logout")) {
            // make sure to set the variable resolver
            initRequest(request, "", locale);
            logout(request, response);
            return;
        }

        int slash = pathInfo.indexOf("/", 1);
        if (slash < 2) {
            slash = pathInfo.length();
        }

        final String label = pathInfo.substring(1, slash);
        Plugin plugin = getConsolePlugin(label);

        if (plugin == null) {
            final String body404 = MessageFormat.format(
                resourceBundleManager.getResourceBundle(bundleContext.getBundle(), locale).getString("404"),
                new Object[] { request.getContextPath() + request.getServletPath() + '/'
                    + BundlesServlet.NAME });
            response.setCharacterEncoding("utf-8");
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().println(body404);

            return;
        }

        // make sure to set the variable resolver
        initRequest(request, "/".concat(label), locale);

        // fix for https://issues.apache.org/jira/browse/FELIX-3408
        ensureLocaleCookieSet(request, response, locale);

        // wrap the response for localization and template variable replacement
        request = wrapRequest(request, locale);
        response = wrapResponse(request, response, plugin);

        plugin.getConsolePlugin().service(request, response);
    }

    @SuppressWarnings("deprecation")
    private void initRequest(final HttpServletRequest request, final String postfix, final Locale locale) {
        @SuppressWarnings("rawtypes")
        final Map labelMap = holder.getLocalizedLabelMap( resourceBundleManager, locale, this.defaultCategory );
        final Object flatLabelMap = labelMap.remove( PluginHolder.ATTR_FLAT_LABEL_MAP );

        // the official request attributes
        request.setAttribute(org.apache.felix.webconsole.WebConsoleConstants.ATTR_LANG_MAP, getLangMap());
        request.setAttribute(AbstractOsgiManagerPlugin.ATTR_LABEL_MAP, flatLabelMap);
        request.setAttribute(AbstractOsgiManagerPlugin.ATTR_LABEL_MAP_CATEGORIZED, labelMap );
        final String appRoot = request.getContextPath().concat(request.getServletPath());
        request.setAttribute(ServletConstants.ATTR_APP_ROOT, appRoot);
        request.setAttribute(ServletConstants.ATTR_PLUGIN_ROOT, appRoot.concat(postfix));
        request.setAttribute(ServletConstants.ATTR_CONFIGURATION, configuration);

        final RequestVariableResolver resolver = new org.apache.felix.webconsole.DefaultVariableResolver();
        request.setAttribute(RequestVariableResolver.REQUEST_ATTRIBUTE, resolver);
        resolver.put( RequestVariableResolver.KEY_APP_ROOT, (String) request.getAttribute( ServletConstants.ATTR_APP_ROOT ) );
        resolver.put( RequestVariableResolver.KEY_PLUGIN_ROOT, (String) request.getAttribute( ServletConstants.ATTR_PLUGIN_ROOT ) );
    }

    @SuppressWarnings("deprecation")
    private final void logout(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final ServiceTracker<SecurityProvider, SecurityProvider> tracker = this.securityProviderTracker.get();
        final SecurityProvider securityProvider = tracker != null ? tracker.getService() : null;
        if (securityProvider != null) {
            securityProvider.logout(request, response);
        }
        if (!response.isCommitted()) {
            // check if special logout cookie is set, this is used to prevent
            // from an endless loop with basic auth
            final Cookie[] cookies = request.getCookies();
            boolean found = false;
            if ( cookies != null ) {
                for(final Cookie c : cookies) {
                    if (c.getName().equals("logout") ) {
                        found = true;
                        break;
                    }
                }
            }
            if ( found ) {
                // redirect to main page
                final String url = request.getRequestURI();
                final int lastSlash = url.lastIndexOf('/');
                final Cookie c = new Cookie("logout", "true");
                c.setMaxAge(0);
                response.addCookie(c);
                response.sendRedirect(url.substring(0, lastSlash));
            } else {
                // if the security provider doesn't support logout, we try to
                // logout the default basic authentication mechanism
                // See https://issues.apache.org/jira/browse/FELIX-3006

                // check for basic authentication
                final String auth = request.getHeader(HEADER_AUTHORIZATION);
                if (null != auth && auth.toLowerCase().startsWith("basic ")) {
                    Map<String, Object> config = getConfiguration();
                    String realm = ConfigurationUtil.getProperty(config, PROP_REALM, DEFAULT_REALM);
                    response.setHeader(HEADER_WWW_AUTHENTICATE, "Basic realm=\"" +  realm + "\"");
                    response.addCookie(new Cookie("logout", "true"));
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                }
            }
        }

        // clean-up
        request.removeAttribute(ServletContextHelper.REMOTE_USER);
        request.removeAttribute(ServletContextHelper.AUTHORIZATION);
        request.removeAttribute(User.USER_ATTRIBUTE);
        request.removeAttribute( org.apache.felix.webconsole.WebConsoleSecurityProvider2.USER_ATTRIBUTE);
        request.removeAttribute(org.apache.felix.webconsole.User.USER_ATTRIBUTE);
    }

    private final Plugin getConsolePlugin(final String label)
    {
        // backwards compatibility for the former "install" action which is
        // used by the Maven Sling Plugin
        if ("install".equals(label))
        {
            return holder.getPlugin(BundlesServlet.NAME);
        }

        Plugin plugin = holder.getPlugin( label );
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

    @Override
    public void destroy() {
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
    String getConfigurationPid() {
        return getClass().getName();
    }

    private HttpServletRequest wrapRequest(final HttpServletRequest request, final Locale locale) {
        return new HttpServletRequestWrapper(request)  {
            @Override
            public Locale getLocale()
            {
                return locale;
            }
        };
    }

    private HttpServletResponse wrapResponse(final HttpServletRequest request,
        final HttpServletResponse response, final Plugin plugin) {
        final Locale locale = request.getLocale();
        final ResourceBundle resourceBundle = resourceBundleManager.getResourceBundle(plugin.getBundle(), locale);
        return new FilteringResponseWrapper(response, resourceBundle, request);
    }

    @SuppressWarnings("deprecation")
    private static class BrandingServiceTracker extends ServiceTracker<BrandingPlugin, BrandingPlugin> {

        private static Filter createFilter(final BundleContext context) {
            try {
                final Filter filter = context.createFilter("(|(" + Constants.OBJECTCLASS + "="
                        + BrandingPlugin.class.getName() + ")" + "(" + Constants.OBJECTCLASS + "=" + org.apache.felix.webconsole.BrandingPlugin.class.getName() + "))");
                return filter;
            } catch (final InvalidSyntaxException e) {
                // fail loud and clear
                throw new InternalError(e);
            }
        }

        public BrandingServiceTracker(final OsgiManager osgiManager) {
            super(osgiManager.getBundleContext(), createFilter(osgiManager.getBundleContext()), null);
        }

        @Override
        public BrandingPlugin addingService(final ServiceReference<BrandingPlugin> reference) {
            final BrandingPlugin plugin = super.addingService(reference);
            if (plugin != null) {
                if (plugin instanceof org.apache.felix.webconsole.BrandingPlugin) {
                    org.apache.felix.webconsole.AbstractWebConsolePlugin.setBrandingPlugin((org.apache.felix.webconsole.BrandingPlugin)plugin);
                } else {
                    org.apache.felix.webconsole.AbstractWebConsolePlugin.setBrandingPlugin(new BrandingPluginAdapter(plugin));
                }
                AbstractPluginAdapter.setBrandingPlugin(plugin);
            }
            return plugin;
        }

        @Override
        public void removedService(final ServiceReference<BrandingPlugin> reference, BrandingPlugin service) {
            org.apache.felix.webconsole.AbstractWebConsolePlugin.setBrandingPlugin(null);
            AbstractPluginAdapter.setBrandingPlugin(null);
            try {
                super.removedService(reference, service);
            } catch ( final IllegalStateException ise) {
                // ignore this as the service is already invalid
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static class BrandingPluginAdapter implements org.apache.felix.webconsole.BrandingPlugin {

        private final BrandingPlugin p;

        public BrandingPluginAdapter(final BrandingPlugin p) {
            this.p = p;
        }

        @Override
        public String getBrandName() {
            return p.getBrandName();
        }

        @Override
        public String getFavIcon() {
            return p.getFavIcon();
        }

        @Override
        public String getMainStyleSheet() {
            return p.getMainStyleSheet();
        }

        @Override
        public String getProductImage() {
            return p.getProductImage();
        }

        @Override
        public String getProductName() {
            return p.getProductName();
        }

        @Override
        public String getProductURL() {
            return p.getProductURL();
        }

        @Override
        public String getVendorImage() {
            return p.getVendorImage();
        }

        @Override
        public String getVendorName() {
            return p.getVendorName();
        }

        @Override
        public String getVendorURL() {
            return p.getVendorURL();
        }
    }

    synchronized void registerHttpWhiteboardServices() {
        final String realm = ConfigurationUtil.getProperty(this.getConfiguration(), PROP_REALM, DEFAULT_REALM);
        BasicWebConsoleSecurityProvider.REALM = realm;
        try{
            final String httpServiceSelector = ConfigurationUtil.getProperty(this.getConfiguration(), PROP_HTTP_SERVICE_SELECTOR, null);

            if (this.basicSecurityServiceRegistration == null) {
                // register this component
                final String userId = ConfigurationUtil.getProperty(this.getConfiguration(), PROP_USER_NAME, DEFAULT_USER_NAME);
                final String password = ConfigurationUtil.getProperty(this.getConfiguration(), PROP_PASSWORD, DEFAULT_PASSWORD);
                final BasicWebConsoleSecurityProvider service = new BasicWebConsoleSecurityProvider(bundleContext, userId, password);
                final Dictionary<String, Object> serviceProperties = new Hashtable<>(); // NOSONAR
                // this is a last resort service, so use a low service ranking to prefer all other services over this one
                serviceProperties.put(Constants.SERVICE_RANKING, Integer.MIN_VALUE);
                this.basicSecurityServiceRegistration = bundleContext.registerService(SecurityProvider.class, service, serviceProperties);
            }

            if (this.servletContextRegistration == null) {
                final ServletContextHelper httpContext = new OsgiManagerHttpContext(this.bundleContext.getBundle(), securityProviderTracker, this.webManagerRoot);
                final Dictionary<String, Object> props = new Hashtable<>();
                if (httpServiceSelector != null) {
                    props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, httpServiceSelector);
                }
                props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME, SERVLEXT_CONTEXT_NAME);
                props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH, this.webManagerRoot);

                this.servletContextRegistration = getBundleContext().registerService(ServletContextHelper.class,
                    httpContext, props);
            }

            if (this.servletRegistration == null) {
                // register this servlet and take note of this
                final Dictionary<String, Object> props = new Hashtable<>();
                for(final Map.Entry<String, Object> entry : this.getConfiguration().entrySet()) {
                    props.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
                if (httpServiceSelector != null) {
                    props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET, httpServiceSelector);
                }

                props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_MULTIPART_ENABLED, Boolean.TRUE);
                props.put("osgi.http.whiteboard.servlet.multipart.maxFileCount", 50);
                props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/");
                props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT, "(" + HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME + "=" + SERVLEXT_CONTEXT_NAME + ")");

                props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PATTERN, "/res/*");
                props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PREFIX, "/res");

                this.servletRegistration = getBundleContext().registerService(Servlet.class, this, props);
            }
        } catch (final Exception e) {
            Util.LOGGER.error("registerHttpWhiteboardServices: Problem setting up", e);
            this.unregisterHttpWhiteboardServices();
        }
    }

    synchronized void unregisterHttpWhiteboardServices() {
        if (this.basicSecurityServiceRegistration != null) {
            try {
                this.basicSecurityServiceRegistration.unregister();
            } catch (final IllegalStateException ignore) {
                // ignore
            }
            this.basicSecurityServiceRegistration = null;
        }

        if (this.servletRegistration != null) {
            try {
                this.servletRegistration.unregister();
            } catch (final IllegalStateException ignore) {
                // ignore
            }
            this.servletRegistration = null;
        }

        if (this.servletContextRegistration != null) {
            try {
                this.servletContextRegistration.unregister();
            } catch (final IllegalStateException ignore) {
                // ignore
            }
            this.servletContextRegistration = null;
        }
    }

    private Map<String, Object> getConfiguration() {
        return configuration;
    }

    Map<String, Object> getDefaultConfiguration() {
        return defaultConfiguration;
    }

    synchronized void updateConfiguration( final Dictionary<String, ?> osgiConfig) {
        final Map<String, Object> config = new HashMap<String, Object>( this.defaultConfiguration );

        if ( osgiConfig != null ) {
            for ( Enumeration<String> keys = osgiConfig.keys(); keys.hasMoreElements(); ) {
                final String key = keys.nextElement();
                final Object value = osgiConfig.get( key );
                if (PROP_SHUTDOWN_TIMEOUT.equals(key) || PROP_RELOAD_TIMEOUT.equals(key)) {
                    try {
                        config.put(key, Integer.parseInt(value.toString()));
                    } catch (final NumberFormatException nfe) {
                        Util.LOGGER.warn("Ignoring invalid value for {}: {}", key, value);
                    }
                } else {
                    config.put(key, value);
                }
            }
        }

        this.configuration = config;

        final Object locale = config.get(PROP_LOCALE);
        this.configuredLocale = locale == null || locale.toString().trim().length() == 0 ? null : Util.parseLocaleString(locale.toString().trim());

        // default plugin page configuration
        holder.setDefaultPluginLabel(ConfigurationUtil.getProperty(config, PROP_DEFAULT_RENDER, DEFAULT_PAGE));

        // get the web manager root path
        String newWebManagerRoot = ConfigurationUtil.getProperty(config, PROP_MANAGER_ROOT, DEFAULT_MANAGER_ROOT);
        if (!newWebManagerRoot.startsWith("/")) {
            newWebManagerRoot = "/".concat(newWebManagerRoot);
        }
        this.webManagerRoot = newWebManagerRoot;

        // default category
        this.defaultCategory = ConfigurationUtil.getProperty( config, PROP_CATEGORY, DEFAULT_CATEGORY );

        // secret heuristics
        final boolean enableHeuristics = ConfigurationUtil.getProperty(config, PROP_ENABLE_SECRET_HEURISTIC, DEFAULT_ENABLE_SECRET_HEURISTIC);
        OsgiManager.ENABLE_SECRET_HEURISTICS = enableHeuristics;

        // get enabled plugins
        final String[] plugins = ConfigurationUtil.getStringArrayProperty(config, PROP_ENABLED_PLUGINS);
        final Set<String> enabledPlugins = null == plugins ? null : new HashSet<String>(Arrays.asList(plugins));
        this.holder.initInternalPlugins(enabledPlugins, bundleContext);

        this.unregisterHttpWhiteboardServices();
        // update http service registrations.
        this.updateRegistrationState();
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
        final Enumeration<URL> e = bundle.findEntries("res/flags", null, false);
        while (e != null && e.hasMoreElements())
        {
            final URL img = e.nextElement();
            final String path = img.getPath();
            try {
                final int lastSlash = path.lastIndexOf('/');
                final int dot = path.indexOf('.', lastSlash);
                final String name = (dot == -1 ? path.substring(lastSlash+1) : path.substring(lastSlash + 1, dot));
                final String locale = new Locale(name, "").getDisplayLanguage();
                map.put(name, null != locale ? locale : name);
            }
            catch (Throwable t) {
                // Ignore invalid locale?
            }
        }
        return langMap = map;
    }

    class UpdateDependenciesStateCustomizer implements ServiceTrackerCustomizer<SecurityProvider, SecurityProvider> {

        private final Map<Long, String> registeredProviders = new ConcurrentHashMap<>();

        @Override
        public SecurityProvider addingService(ServiceReference<SecurityProvider> reference) {
            final SecurityProvider provider = bundleContext.getService(reference);
            if (provider != null) {
                final Object nameObj = reference.getProperty(SecurityProvider.PROPERTY_ID);
                if (nameObj instanceof String) {
                    final String name = (String) nameObj;
                    final Long id = (Long) reference.getProperty(Constants.SERVICE_ID);
                    registeredProviders.put(id, name);
                    registeredSecurityProviders.add(name);
                    updateRegistrationState();
                }
            }
            return provider;
        }

        @Override
        public void modifiedService(ServiceReference<SecurityProvider> reference, SecurityProvider service) {
            removedService(reference, service);
            addingService(reference);
        }

        @Override
        public void removedService(ServiceReference<SecurityProvider> reference, SecurityProvider service) {
            final String name = registeredProviders.remove(reference.getProperty(Constants.SERVICE_ID));
            if (name != null) {
                registeredSecurityProviders.remove(name);
                updateRegistrationState();
            }
            try {
                bundleContext.ungetService(reference);
            } catch (IllegalStateException ise) {
                // ignore on shutdown
            }
        }
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.webconsole.internal.servlet;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.felix.webconsole.internal.OsgiManagerPlugin;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.i18n.ResourceBundleManager;
import org.apache.felix.webconsole.internal.legacy.LegacyServicesTracker;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;


/**
 * The <code>PluginHolder</code> class implements the maintenance and lazy
 * access to web console plugin services.
 */
public class PluginHolder implements ServiceTrackerCustomizer<Servlet, Plugin> {

    private static final String OLD_CONFIG_MANAGER_CLASS = "org.apache.felix.webconsole.internal.compendium.ConfigManager";
    private static final String NEW_CONFIG_MANAGER_CLASS = "org.apache.felix.webconsole.internal.configuration.ConfigManager";

    static final String[] PLUGIN_MAP = {
        NEW_CONFIG_MANAGER_CLASS, "configMgr",
        "org.apache.felix.webconsole.internal.compendium.LogServlet", "logs",
        "org.apache.felix.webconsole.internal.core.BundlesServlet", "bundles",
        "org.apache.felix.webconsole.internal.core.ServicesServlet", "services",
        "org.apache.felix.webconsole.internal.misc.LicenseServlet", "licenses",
        "org.apache.felix.webconsole.internal.system.VMStatPlugin", "vmstat"
    };

    public static final String ATTR_FLAT_LABEL_MAP = PluginHolder.class.getName() + ".flatLabelMap";

    private final OsgiManager osgiManager;

    // The Web Console's bundle context to access the plugin services
    private final BundleContext bundleContext;

    // Registered plugins
    private final Map<String, List<Plugin>> plugins = new HashMap<>();

    // The servlet context used to initialize plugin services
    private volatile ServletContext servletContext;

    // the label of the default plugin
    private volatile String defaultPluginLabel;

    private final ServiceTracker<Servlet, Plugin> servletTracker;

    private volatile Closeable legacyTracker;

    private final Map<String, OsgiManagerPlugin> internalPlugins = new HashMap<>();

    PluginHolder( final OsgiManager osgiManager, final BundleContext context ) {
        this.osgiManager = osgiManager;
        this.bundleContext = context;
        Filter filter = null;
        try {
            filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "=" + Servlet.class.getName() + 
                ")(" + ServletConstants.PLUGIN_LABEL + "=*)(" + ServletConstants.PLUGIN_TITLE + "=*))");
        } catch (final InvalidSyntaxException e) {
            // not expected, thus fail hard
            throw new InternalError( "Failed creating filter: " + e.getMessage() );
        }
        this.servletTracker = new ServiceTracker<>(context, filter, this);
    }

    //---------- OsgiManager support API

    public void initInternalPlugins(final Set<String> enabledPlugins, final BundleContext context) {
        if (enabledPlugins != null) {
            if (enabledPlugins.contains(OLD_CONFIG_MANAGER_CLASS)) {
                enabledPlugins.add(NEW_CONFIG_MANAGER_CLASS);
            }
        }
        for (int i = 0; i < PLUGIN_MAP.length; i+=2) {
           final String pluginClassName = PLUGIN_MAP[i];

           final boolean enable = enabledPlugins == null || enabledPlugins.contains(pluginClassName);
           if ( enable && !internalPlugins.containsKey(pluginClassName) ) {
                final OsgiManagerPlugin plugin = this.osgiManager.createInternalPlugin(pluginClassName);
                if ( plugin != null ) {
                    internalPlugins.put(pluginClassName, plugin);
                    plugin.activate(context);
                }
           } else if ( !enable && internalPlugins.containsKey(pluginClassName) ) {
               final OsgiManagerPlugin plugin = internalPlugins.remove(pluginClassName);
               plugin.deactivate();
           }
        }
    }

    /**
     * Start using the plugin manager with registration as a service listener
     * and getting references to all plugins already registered in the
     * framework.
     */
    void open() {
        this.servletTracker.open();
        try {
            this.legacyTracker = new LegacyServicesTracker(this, this.getBundleContext());
            this.osgiManager.log(LogService.LOG_INFO, "Servlet 3 bridge enabled");
        } catch ( final Throwable t) {
            // ignore
            this.osgiManager.log(LogService.LOG_INFO, "Servlet 3 bridge not enabled");
        }
    }

    /**
     * Stop using the plugin manager by removing as a service listener and
     * releasing all held plugins, which includes ungetting and destroying any
     * held plugin services.
     */
    void close() {
        if (this.legacyTracker != null) {
            try {
                this.legacyTracker.close();
            } catch (final IOException e) {
                // ignore
            }
            this.legacyTracker = null;
        }
        this.servletTracker.close();

        for(final OsgiManagerPlugin plugin : internalPlugins.values()) {
            plugin.deactivate();
        }
        this.internalPlugins.clear();
        this.plugins.clear();
        this.servletContext = null;
        this.defaultPluginLabel = null;
    }

    public OsgiManager getOsgiManager() {
        return this.osgiManager;
    }

    /**
     * Returns label of the default plugin
     * @return label of the default plugin
     */
    String getDefaultPluginLabel() {
        return defaultPluginLabel;
    }

    /**
     * Sets the label of the default plugin
     * @param defaultPluginLabel
     */
    void setDefaultPluginLabel( final String defaultPluginLabel ) {
        this.defaultPluginLabel = defaultPluginLabel;
    }

    /**
     * Returns the plugin registered under the given label or <code>null</code>
     * if none is registered under that label. If the label is <code>null</code>
     * or empty, any registered plugin is returned or <code>null</code> if
     * no plugin is registered
     *
     * @param label The label of the plugin to return
     * @return The plugin or <code>null</code> if no plugin is registered with
     *      the given label.
     */
    Plugin getPlugin( final String label ) {
        Plugin consolePlugin = null;

        if ( label != null && label.length() > 0 ) {
            synchronized ( plugins ) {
                final List<Plugin> list = plugins.get( label );
                consolePlugin = list != null && !list.isEmpty() ? list.get(0) : null;
            }
        } else{
            final List<Plugin> plugins = getPlugins();
            for(final Plugin p : plugins) {
                consolePlugin = p;
                if ( consolePlugin != null ) {
                    break;
                }
            }
        }

        return consolePlugin;
    }


    /**
     * Builds the map of labels to plugin titles to be stored as the
     * <code>felix.webconsole.labelMap</code> request attribute. This map
     * optionally localizes the plugin title using the providing bundle's
     * resource bundle if the first character of the title is a percent
     * sign (%). Titles not prefixed with a percent sign are added to the
     * map unmodified.
     * <p>
     * The special entry {@code felix.webconsole.labelMap} is the flat,
     * unstructured map of labels to titles which is used as the
     * respective request attribute (see FELIX-3833).
     *
     * @param resourceBundleManager The ResourceBundleManager providing
     *      localized titles
     * @param locale The locale to which the titles are to be localized
     *
     * @return The localized map of labels to titles
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    Map getLocalizedLabelMap( final ResourceBundleManager resourceBundleManager, final Locale locale, final String defaultCategory )
    {
        final Map map = new HashMap();
        final Map flatMap = new HashMap();
        final List<Plugin> plugins = getPlugins();
        for(final Plugin plugin : plugins) {
            if ( !plugin.isEnabled() ) {
                continue;
            }

            // support only one level for now
            Map categoryMap = null;
            String category = plugin.getCategory();
            if ( category == null || category.trim().length() == 0 ) {
                // FELIX-3798 configured default category
                category = defaultCategory;
            }

            categoryMap = findCategoryMap( map, category );

            final String label = plugin.getLabel();
            String title = plugin.getTitle();
            if ( title.startsWith( "%" ) ) {
                try {
                    final ResourceBundle resourceBundle = resourceBundleManager.getResourceBundle( plugin.getBundle(), locale );
                    title = resourceBundle.getString( title.substring( 1 ) );
                } catch ( Throwable e ) {
                    // ignore missing resource - use default title
                }
            }

            categoryMap.put( label, title );
            flatMap.put( label, title );
        }

        // flat map of labels to titles (FELIX-3833)
        map.put( ATTR_FLAT_LABEL_MAP, flatMap );

        return map;
    }


    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Map findCategoryMap( Map map, String categoryPath )
    {
        Map categoryMap = null;
        Map searchMap = map;

        String categories[] = categoryPath.split( "/" );

        for ( int i = 0; i < categories.length; i++ )
        {
            String categoryKey = "category." + categories[i];
            if ( searchMap.containsKey( categoryKey ) )
            {
                categoryMap = ( Map ) searchMap.get( categoryKey );
            }
            else
            {
                categoryMap = new HashMap();
                searchMap.put( categoryKey, categoryMap );
            }
            searchMap = categoryMap;
        }

        return categoryMap;
    }


    /**
     * Returns the bundle context of the Web Console itself.
     * @return the bundle context of the Web Console itself.
     */
    public BundleContext getBundleContext() {
        return bundleContext;
    }

    /**
     * Sets the servlet context to be used to initialize plugin services
     * @param servletContext
     */
    void setServletContext( final ServletContext context ) {
        this.servletContext = context;
    }


    /**
     * Returns the servlet context to be used to initialize plugin services
     * @return the servlet context to be used to initialize plugin services
     */
    ServletContext getServletContext() {
        return servletContext;
    }

    private List<Plugin> getPlugins() {
        final List<Plugin> plugins = new ArrayList<>();
        synchronized ( plugins ) {
            for(final List<Plugin> c : this.plugins.values()) {
                if ( !c.isEmpty() ) {
                    plugins.add(c.get(0));
                }
            }
        }
        return plugins;
    }

    //---------- ServiceTrackerCustomizer

    @Override
    public Plugin addingService(final ServiceReference<Servlet> reference) {
        Plugin plugin = null;
        final String label = Util.getStringProperty( reference, ServletConstants.PLUGIN_LABEL );
        if ( label != null ) {
            plugin = new Plugin( this, reference, label );
            addPlugin( plugin );
        }
        return plugin;
    }


    @Override
    public void modifiedService(final ServiceReference<Servlet> reference, final Plugin plugin) {
        removedService( reference, plugin );
        addingService(reference);
    }


    @Override
    public void removedService(final ServiceReference<Servlet> reference, final Plugin plugin) {
        removePlugin( plugin );
    }

    public void addPlugin( final Plugin plugin ) {
        synchronized ( plugins ) {
            final List<Plugin> list = plugins.computeIfAbsent(plugin.getLabel(), k -> new ArrayList<>());
            final Plugin oldPlugin = list.isEmpty() ? null : list.get(0);
            list.add(plugin);
            Collections.sort(list);
            Collections.reverse(list);
            final Plugin first = list.get(0);
            if (first == plugin) {
                if (!first.init()) {
                    list.remove(plugin);
                } else if (oldPlugin != null) {
                    osgiManager.log(LogService.LOG_WARNING, "Overwriting existing plugin " + oldPlugin.getId() 
                            + " having label " + plugin.getLabel() + " with new plugin " + plugin.getId()
                            + " due to higher ranking " );
                    oldPlugin.dispose();
                }
            }
            if (first == oldPlugin) {
                osgiManager.log(LogService.LOG_WARNING, "Ignoring new plugin " + plugin.getId()
                        + " having existing label " + plugin.getLabel() + " due to lower ranking than old plugin " + oldPlugin.getId() );
            }
        }
    }

    public void removePlugin( final Plugin plugin ) {
        synchronized ( plugins ) {
            final List<Plugin> list = plugins.get( plugin.getLabel() );
            if ( list != null ) {
                final boolean isFirst = !list.isEmpty() && list.get(0) == plugin;
                list.remove( plugin );
                if ( list.isEmpty() ) {
                    plugins.remove( plugin.getLabel() );
                } else if ( isFirst ) {
                    while ( !list.isEmpty() && !list.get(0).init() ) {
                        list.remove(0);
                    }
                }
            }
            plugin.dispose();
        }
    }
}

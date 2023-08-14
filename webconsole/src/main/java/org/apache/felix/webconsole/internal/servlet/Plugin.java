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

import java.util.Collections;
import java.util.Enumeration;
import java.util.NoSuchElementException;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.felix.webconsole.internal.OsgiManagerPlugin;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.WebConsolePluginAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

public abstract class Plugin implements ServletConfig, Comparable<Plugin> {

    private final PluginHolder holder;
    private final String label;

    private final ServiceReference<Servlet> serviceReference; // used for comparing conflicting services

    protected volatile String title;

    protected volatile String category;

    private volatile AbstractWebConsolePlugin consolePlugin;

    public Plugin( final PluginHolder holder, final ServiceReference<Servlet> serviceReference, final String label ) {
        this.holder = holder;
        this.serviceReference = serviceReference;
        this.label = label;
    }

    public ServiceReference<Servlet> getServiceReference() {
        return this.serviceReference;
    }

    public Bundle getBundle() {
        if ( this.serviceReference != null ) {
            return this.serviceReference.getBundle();
        }
        return this.holder.getBundleContext().getBundle();
    }

    /**
     * Initialize everything including title and category
     */
    public boolean init() {
        final AbstractWebConsolePlugin plugin = this.doGetConsolePlugin();
        if ( plugin != null ) {
            try {
                plugin.init(this);
            } catch (final ServletException e) {
                // ignore this
            }
            this.consolePlugin = plugin;
        }
        return this.getConsolePlugin() != null;
    }

    /**
     * Cleans up this plugin when it is not used any longer. This means
     * destroying the plugin servlet and, if it was registered as an OSGi
     * service, ungetting the service.
     */
    public void dispose() {
        if (this.consolePlugin != null) {
           try {
                this.consolePlugin.destroy();
            } catch ( final Throwable t) {
                // ignore
            }
            this.doUngetConsolePlugin(this.consolePlugin);
            this.consolePlugin = null;
        }
    }

    private static Integer getRanking(final ServiceReference<Servlet> serviceReference) {
        // ranking must be of type Integer
        final Object ranking = serviceReference != null ? serviceReference.getProperty(Constants.SERVICE_RANKING) : null;
        if (ranking instanceof Integer) {
            return (Integer) ranking;
        }
        // otherwise return default value
        return 0;
    }

    @Override
    public int compareTo(final Plugin other) {
        int result = this.getLabel().compareTo(other.getLabel());
        if (result == 0) {
            // serviceReference = null means internal (i.e. service.ranking=0 and service.id=0)
            final Long id = serviceReference != null ? (Long) serviceReference.getProperty(Constants.SERVICE_ID) : 0;
            final Long otherId = other.serviceReference != null ? (Long) other.serviceReference.getProperty(Constants.SERVICE_ID) : 0;
            result = id.compareTo(otherId);
            if (result != 0) { // same service or both internal if 0
                final Integer rank = getRanking(this.serviceReference);
                final Integer otherRank = getRanking(other.serviceReference);
                result = rank.compareTo(otherRank);
                if (result == 0) {
                    // If ranks are equal, then sort by service id in descending order.
                    result = -1 * id.compareTo(otherId);
                }
            }
        }
        return result;
    }

    public String getLabel() {
        return label;
    }

    protected PluginHolder getHolder() {
        return holder;
    }

    public String getTitle() {
        return this.title != null ? this.title : this.getLabel();
    }

    public String getCategory() {
        return this.category;
    }

    public AbstractWebConsolePlugin getConsolePlugin() {
        return this.consolePlugin;
    }

    protected boolean isEnabled() {
        return true;
    }

    public abstract String getId();

    protected abstract AbstractWebConsolePlugin doGetConsolePlugin();

    protected abstract void doUngetConsolePlugin( AbstractWebConsolePlugin consolePlugin );

    //---------- ServletConfig interface

    @Override
    public String getInitParameter( final String name ) {
        return null;
    }

    @Override
    public Enumeration<?> getInitParameterNames() {
        return Collections.emptyEnumeration();
    }

    @Override
    public ServletContext getServletContext() {
        return getHolder().getServletContext();
    }

    @Override
    public String getServletName() {
        return getTitle();
    }

    public static class ServletPlugin extends Plugin {

        public ServletPlugin( final PluginHolder holder, final ServiceReference<Servlet> serviceReference, final String label ) {
            super(holder, serviceReference, label);
        }

        public String getId() {
            return this.getServiceReference().toString();
        }

        protected AbstractWebConsolePlugin doGetConsolePlugin() {
            final Servlet service = getHolder().getBundleContext().getService( this.getServiceReference() );
            if ( service != null ) {
                this.title = Util.getStringProperty( this.getServiceReference(), WebConsoleConstants.PLUGIN_TITLE );
                this.category = Util.getStringProperty( this.getServiceReference(), WebConsoleConstants.PLUGIN_CATEGORY );
                final AbstractWebConsolePlugin servlet;
                if ( service instanceof AbstractWebConsolePlugin ) {
                    servlet = ( AbstractWebConsolePlugin ) service;
                    if (this.title == null) {
                        this.title = servlet.getTitle();
                    }
                    if (this.category == null) {
                        this.category = servlet.getCategory();
                    }
                } else {
                    servlet = new WebConsolePluginAdapter( getLabel(), service, this.getServiceReference() );
                }
                return servlet;
            }
            return null;
        }

        protected void doUngetConsolePlugin( AbstractWebConsolePlugin consolePlugin ) {
            try {
                getHolder().getBundleContext().ungetService( this.getServiceReference() );
            } catch ( final IllegalStateException ise ) {
                // ignore - bundle context is no longer valid
            }
        }

        //---------- ServletConfig overwrite (based on ServletReference)

        @Override
        public String getInitParameter( final String name ) {
            Object property = this.getServiceReference().getProperty( name );
            if ( property != null && !property.getClass().isArray() ) {
                return property.toString();
            }

            return super.getInitParameter( name );
        }

        @Override
        public Enumeration<?> getInitParameterNames() {
            final String[] keys = this.getServiceReference().getPropertyKeys();
            return new Enumeration<Object>() {
                int idx = 0;

                public boolean hasMoreElements() {
                    return idx < keys.length;
                }

                public Object nextElement() {
                    if ( hasMoreElements() ) {
                        return keys[idx++];
                    }
                    throw new NoSuchElementException();
                }
            };
        }
    }

    public static class InternalPlugin extends Plugin {

        private final String pluginClassName;
        private final OsgiManager osgiManager;
        private volatile boolean doLog = true;

        public InternalPlugin(PluginHolder holder, OsgiManager osgiManager, String pluginClassName, String label) {
            super(holder, null, label);
            this.osgiManager = osgiManager;
            this.pluginClassName = pluginClassName;
        }

        public String getId() {
            return this.pluginClassName;
        }

        protected final boolean isEnabled() {
            // check if the plugin is enabled
            return !osgiManager.isPluginDisabled(pluginClassName);
        }

        protected AbstractWebConsolePlugin doGetConsolePlugin() {
            if (!isEnabled()) {
                if (doLog) {
                    osgiManager.log( LogService.LOG_INFO, "Ignoring plugin " + pluginClassName + ": Disabled by configuration" );
                    doLog = false;
                }
                return null;
            }

            AbstractWebConsolePlugin plugin = null;
            try {
                Class<?> pluginClass = getClass().getClassLoader().loadClass(pluginClassName);
                plugin = (AbstractWebConsolePlugin) pluginClass.getDeclaredConstructor().newInstance();

                if (plugin instanceof OsgiManagerPlugin) {
                    ((OsgiManagerPlugin) plugin).activate(osgiManager.getBundleContext());
                }
                this.title = plugin.getTitle();
                this.category = plugin.getCategory();
                doLog = true; // reset logging if it succeeded
            } catch (final Throwable t) {
                plugin = null; // in case only activate has faled!
                if (doLog) {
                    osgiManager.log( LogService.LOG_WARNING, "Failed to instantiate plugin " + pluginClassName, t );
                    doLog = false;
                }
            }

            return plugin;
        }

        protected void doUngetConsolePlugin(final AbstractWebConsolePlugin plugin) {
            if (plugin instanceof OsgiManagerPlugin) {
                ((OsgiManagerPlugin) plugin).deactivate();
            }
        }
    }
}

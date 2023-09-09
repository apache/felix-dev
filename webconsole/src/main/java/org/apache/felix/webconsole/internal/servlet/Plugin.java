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

import java.util.Enumeration;
import java.util.NoSuchElementException;

import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.servlet.AbstractServlet;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

public class Plugin implements ServletConfig, Comparable<Plugin> {

    private final PluginHolder holder;

    private final String label;

    private final ServiceReference<Servlet> serviceReference; // used for comparing conflicting services

    protected volatile String title;

    protected volatile String category;

    private volatile Servlet consolePlugin;

    public Plugin( final PluginHolder holder, final ServiceReference<Servlet> serviceReference, final String label ) {
        this.holder = holder;
        this.serviceReference = serviceReference;
        this.label = label;
        this.title = Util.getStringProperty( this.getServiceReference(), ServletConstants.PLUGIN_TITLE );
        this.category = Util.getStringProperty( this.getServiceReference(), ServletConstants.PLUGIN_CATEGORY );
    }

    public ServiceReference<Servlet> getServiceReference() {
        return this.serviceReference;
    }

    public Bundle getBundle() {
        return this.serviceReference.getBundle();
    }

    /**
     * Initialize everything including title and category
     * @return {@code true} if the plugin is initialized, {@code false} otherwise
     */
    public boolean init() {
        final Servlet plugin = this.doGetConsolePlugin();
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

    private Integer getRanking() {
        // ranking must be of type Integer
        final Object ranking = serviceReference.getProperty(Constants.SERVICE_RANKING);
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
                final Integer rank = getRanking();
                final Integer otherRank = getRanking();
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
        return this.title;
    }

    public String getCategory() {
        return this.category;
    }

    public Servlet getConsolePlugin() {
        return this.consolePlugin;
    }

    protected boolean isEnabled() {
        return true;
    }

    public String getId() {
        return this.getServiceReference().toString();
    }

    protected Servlet doGetConsolePlugin() {
        final Servlet service = getHolder().getBundleContext().getService( this.getServiceReference() );
        if ( service != null ) {
            final String[] css = Util.toStringArray( this.getServiceReference().getProperty( ServletConstants.PLUGIN_CSS_REFERENCES ) );
            if ( service instanceof AbstractServlet ) {
                return new EnhancedPluginAdapter((AbstractServlet)service, this.getServiceReference(), this.getLabel(), this.getTitle(), css);
            }
            return new SimplePluginAdapter(service, serviceReference, this.getLabel(), this.getTitle(), css);
        }
        return null;
    }

    protected void doUngetConsolePlugin( Servlet consolePlugin ) {
        try {
            getHolder().getBundleContext().ungetService( this.getServiceReference() );
        } catch ( final IllegalStateException ise ) {
            // ignore - bundle context is no longer valid
        }
    }


    //---------- ServletConfig interface

    public String getInitParameter( final String name ) {
        Object property = this.getServiceReference().getProperty( name );
        if ( property != null && !property.getClass().isArray() ) {
            return property.toString();
        }

        return null;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        final String[] keys = this.getServiceReference().getPropertyKeys();
        return new Enumeration<String>() {
            int idx = 0;

            public boolean hasMoreElements() {
                return idx < keys.length;
            }

            public String nextElement() {
                if ( hasMoreElements() ) {
                    return keys[idx++];
                }
                throw new NoSuchElementException();
            }
        };
    }

    @Override
    public ServletContext getServletContext() {
        return getHolder().getServletContext();
    }

    @Override
    public String getServletName() {
        return getTitle();
    }
}

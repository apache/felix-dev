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

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

public abstract class Plugin implements ServletConfig, Comparable<Plugin> {

    private final PluginHolder holder;
    private final String label;

    private final ServiceReference<Servlet> serviceReference; // used for comparing conflicting services

    private volatile String title;

    private volatile AbstractWebConsolePlugin consolePlugin;

    public Plugin( final PluginHolder holder, final ServiceReference<Servlet> serviceReference, final String label ) {
        this.holder = holder;
        this.serviceReference = serviceReference;
        this.label = label;
    }

    public ServiceReference<Servlet> getServiceReference() {
        return this.serviceReference;
    }

    public abstract String getId();

    public Bundle getBundle() {
        if ( this.serviceReference != null ) {
            return this.serviceReference.getBundle();
        }
        return this.holder.getBundleContext().getBundle();
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
        // serviceReference = null means internal (i.e. service.ranking=0 and service.id=0)
        final Long id = serviceReference != null ? (Long) serviceReference.getProperty(Constants.SERVICE_ID) : 0;
        final Long otherId = other.serviceReference != null ? (Long) other.serviceReference.getProperty(Constants.SERVICE_ID) : 0;
        if (id.compareTo(otherId) == 0) {
            return 0; // same service or both internal
        }

        final Integer rank = getRanking(this.serviceReference);
        final Integer otherRank = getRanking(other.serviceReference);

        // Sort by rank in ascending order.
        if (rank.compareTo(otherRank) < 0) {
            return -1; // lower rank
        } else if (rank.compareTo(otherRank) > 0) {
            return 1; // higher rank
        }

        // If ranks are equal, then sort by service id in descending order.
        return -1 * id.compareTo(otherId);
    }

    protected PluginHolder getHolder() {
        return holder;
    }

    public String getLabel() {
        return label;
    }

    protected void setTitle(final String title ) {
        this.title = title;
    }

    public String getTitle() {
        return this.title != null ? this.title : this.getLabel();
    }

    // methods added to support categories

    final String getCategory() {
        return doGetCategory();
    }

    protected String doGetCategory() {
        // get the service now
        final AbstractWebConsolePlugin plugin = getConsolePlugin();
        return ( plugin != null ) ? plugin.getCategory() : null;
    }

    public final AbstractWebConsolePlugin getConsolePlugin() {
        if ( this.consolePlugin == null ) {
            final AbstractWebConsolePlugin plugin = this.doGetConsolePlugin();
            if ( plugin != null ) {
                try {
                    plugin.init(this);
                } catch (final ServletException e) {
                    // ignore this
                }
                this.consolePlugin = plugin;
            }
        }
        return this.consolePlugin;
    }

    protected boolean isEnabled() {
        return true;
    }

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
}

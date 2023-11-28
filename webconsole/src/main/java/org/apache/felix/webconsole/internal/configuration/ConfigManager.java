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
package org.apache.felix.webconsole.internal.configuration;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.servlet.AbstractOsgiManagerPlugin;
import org.apache.felix.webconsole.servlet.RequestVariableResolver;
import org.apache.felix.webconsole.spi.ConfigurationHandler;
import org.apache.felix.webconsole.spi.ValidationException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * The <code>ConfigManager</code> class is the Web Console plugin to
 * manage configurations.
 */
public class ConfigManager extends AbstractOsgiManagerPlugin {

    private static final long serialVersionUID = 5021174538498622428L;

    private static final String LABEL = "configMgr"; // was name
    private static final String TITLE = "%configMgr.pluginTitle";
    private static final String CSS[] = { "/res/ui/config.css" };

    static final String PID_FILTER = "pidFilter";
    static final String PID = "pid";
    static final String FACTORY_PID = "factoryPid";
    static final String REFERER = "referer";
    static final String FACTORY_CREATE = "factoryCreate";

    static final String ACTION_CREATE = "create";
    static final String ACTION_DELETE = "delete";
    static final String ACTION_APPLY = "apply";
    static final String ACTION_UPDATE = "update";
    static final String ACTION_UNBIND = "unbind";
    static final String PROPERTY_LIST = "propertylist";
    static final String LOCATION = "$location";

    static final String CONFIGURATION_ADMIN_NAME = "org.osgi.service.cm.ConfigurationAdmin";
    static final String META_TYPE_NAME = "org.osgi.service.metatype.MetaTypeService";

    public static final String UNBOUND_LOCATION = "??unbound:bundle/location";

    // templates
    private final String TEMPLATE;

    // service tracker for SPI
    private ServiceTracker<ConfigurationHandler, ConfigurationHandler> spiTracker;

    /** 
     * Default constructor 
     * @throws IOException If template can't be read
     */
    public ConfigManager() throws IOException {
        // load templates
        TEMPLATE = readTemplateFile( "/templates/config.html" );
    }
    
    @Override
    protected String getCategory() {
        return CATEGORY_OSGI;
    }

    @Override
    protected String[] getCssReferences() {
        return CSS;
    }

    @Override
    protected String getLabel() {
        return LABEL;
    }

    @Override
    protected String getTitle() {
        return TITLE;
    }

    @Override
    public void activate(final BundleContext bundleContext) {
        super.activate(bundleContext);
        this.spiTracker = new ServiceTracker<>(bundleContext, ConfigurationHandler.class, new ServiceTrackerCustomizer<ConfigurationHandler, ConfigurationHandler>() {


            @Override
            public ConfigurationHandler addingService(final ServiceReference<ConfigurationHandler> reference) {
                return bundleContext.getService(reference);
            }

            public void modifiedService(final ServiceReference<ConfigurationHandler> reference, final ConfigurationHandler service) {
                // nothing to do
            }

            public void removedService(final ServiceReference<ConfigurationHandler> reference, final ConfigurationHandler service) {
                try {
                    bundleContext.ungetService(reference);
                } catch ( final IllegalStateException ise) {
                    // we ignore this as the service might have already been removed
                }
            }
        });
        this.spiTracker.open(true);
    }


    @Override
    public void deactivate() {
        if ( this.spiTracker != null ) {
            this.spiTracker.close();
            this.spiTracker = null;
        }
        super.deactivate();
    }

    @Override
    protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws IOException {
        // service unavailable if config admin is not available
        final ConfigAdminSupport cas = getConfigurationAdminSupport();
        if ( cas == null ) {
            response.sendError(503);
            return;
        }

        // needed multiple times below
        String pid = request.getParameter( ConfigManager.PID );
        if ( pid == null ) {
            String info = request.getPathInfo();
            pid = URLDecoder.decode( info.substring( info.lastIndexOf( '/' ) + 1 ), StandardCharsets.UTF_8 );
        }
        // ignore this request if the PID is invalid / not provided
        if ( pid == null || pid.length() == 0 || !ConfigurationUtil.isAllowedPid(pid)) {
            response.sendError(400);
            return;
        }

        // the filter to select part of the configurations
        final String pidFilter = request.getParameter( PID_FILTER );
        if ( pidFilter != null && !ConfigurationUtil.isAllowedPid(pidFilter) ) {
            response.sendError(400);
            return;
        }

        if ( request.getParameter( ACTION_APPLY ) != null ) {
            if ( request.getParameter( ConfigManager.ACTION_DELETE ) != null ) {
                try {
                    cas.deleteConfiguration( pid );
                    Util.sendJsonOk(response);
                } catch ( final ValidationException ve) {
                    response.sendError(400, ve.getMessage());
                }
            }
            else
            {
                final String propertyList = request.getParameter( ConfigManager.PROPERTY_LIST );
                if ( propertyList == null ) {
                    response.sendError(400);
                    return;
                }

                try {
                    String redirect = cas.applyConfiguration( request, pid, propertyList.split(","), ACTION_UPDATE.equals(request.getParameter(ACTION_APPLY)));
                    if (pidFilter != null) {
                        redirect = redirect.concat("?").concat(PID_FILTER).concat("=").concat(pidFilter);
                    }

                    Util.sendRedirect(request, response, redirect);
                } catch ( final ValidationException ve) {
                    response.sendError(400, ve.getMessage());
                }
            }

            return;
        }

        // the configuration to operate on (to be created or "missing")
        final Configuration config;

        // should actually apply the configuration before redirecting
        if ( request.getParameter( ACTION_CREATE ) != null ) {
            config = ConfigurationUtil.getPlaceholderConfiguration( pid );
            pid = config.getPid();
        } else {
            config = cas.findConfiguration( pid );
        }

        // check for configuration unbinding
        if ( request.getParameter( ACTION_UNBIND ) != null )
        {
            if ( config != null && config.getBundleLocation() != null ) {
                config.setBundleLocation( UNBOUND_LOCATION );

            }
            Util.sendJsonOk(response);
            return;
        }

        // send the result
        response.setContentType( "application/json" );
        response.setCharacterEncoding( "UTF-8" );
        final Locale loc = request.getLocale();
        final String locale = ( loc != null ) ? loc.toString() : null;
        cas.getJsonSupport().printConfigurationJson( response.getWriter(), pid, config, pidFilter, locale );
    }

    @Override
    protected void doGet( HttpServletRequest request, HttpServletResponse response )
    throws ServletException, IOException {
        // check for "post" requests from previous versions
        if ( "true".equals(request.getParameter("post")) ) {
            this.doPost(request, response);
            return;
        }
        final String info = request.getPathInfo();
        // let's check for a JSON request
        if ( info.endsWith( ".json" ) )
        {
            response.setContentType( "application/json" );
            response.setCharacterEncoding( "UTF-8" );

            // after last slash and without extension
            String pid = info.substring( info.lastIndexOf( '/' ) + 1, info.length() - 5 );
            // check whether the PID is actually a filter for the selection
            // of configurations to display, if the filter correctly converts
            // into an OSGi filter, we use it to select configurations
            // to display
            String pidFilter = request.getParameter( PID_FILTER );
            if ( pidFilter == null )
            {
                pidFilter = pid;
            }
            try
            {
                getBundleContext().createFilter( pidFilter );

                // if the pidFilter was set from the PID, clear the PID
                if ( pid == pidFilter )
                {
                    pid = null;
                }
            }
            catch ( InvalidSyntaxException ise )
            {
                // its OK, if the PID is just a single PID
                pidFilter = null;
            }

            // check both PID and PID filter
            if ( pid != null && !ConfigurationUtil.isAllowedPid(pid) )
            {
                response.sendError(400);
            }
            if ( pidFilter != null && !ConfigurationUtil.isAllowedPid(pidFilter) )
            {
                response.sendError(400);
            }


            final Locale loc = request.getLocale();
            final String locale = ( loc != null ) ? loc.toString() : null;

            final PrintWriter pw = response.getWriter();
            pw.write( "[" );
            final ConfigAdminSupport ca = this.getConfigurationAdminSupport();
            if ( ca != null )
            {
                // create filter
                final StringBuilder sb = new StringBuilder();
                if ( pid != null && pidFilter != null)
                {
                    sb.append("(&");
                }
                if ( pid != null )
                {
                    sb.append('(');
                    sb.append(Constants.SERVICE_PID);
                    sb.append('=');
                    sb.append(pid);
                    sb.append(')');
                }
                if ( pidFilter != null )
                {
                    sb.append(pidFilter);
                }
                if ( pid != null && pidFilter != null)
                {
                    sb.append(')');
                }
                final String filter = sb.toString();
                try
                {
                    // we use listConfigurations to not create configuration
                    // objects persistently without the user providing actual
                    // configuration
                    final Configuration[] configs = ca.listConfigurations( filter );
                    boolean printComma = false;
                    for(int i=0; configs != null && i<configs.length; i++)
                    {
                        final Configuration config = configs[i];
                        if ( config != null )
                        {
                            if ( printComma )
                            {
                                pw.print( ',' );
                            }
                            ca.getJsonSupport().printConfigurationJson( pw, config.getPid(), config, null, locale );
                            printComma = true;
                        }
                    }
                }
                catch ( final InvalidSyntaxException ise )
                {
                    // should print message
                    // however this should not happen as we checked the filter before
                }
                catch ( final IOException ioe )
                {
                    // should print message
                }
            }
            pw.write( "]" );

            // nothing more to do
            return;
        }

        super.doGet( request, response );
    }

    @Override
    public void renderContent( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {

        // extract the configuration PID from the request path
        String pid = request.getPathInfo().substring(this.getLabel().length() + 1);
        if ( pid.length() == 0 ) {
            pid = null;
        } else {
            pid = pid.substring( pid.lastIndexOf( '/' ) + 1 );
        }
        // check whether the PID is actually a filter for the selection
        // of configurations to display, if the filter correctly converts
        // into an OSGi filter, we use it to select configurations
        // to display
        String pidFilter = request.getParameter( PID_FILTER );
        if ( pidFilter == null )
        {
            pidFilter = pid;
        }
        if ( pidFilter != null )
        {
            try
            {
                getBundleContext().createFilter( pidFilter );

                // if the pidFilter was set from the PID, clear the PID
                if ( pid == pidFilter )
                {
                    pid = null;
                }
            }
            catch ( InvalidSyntaxException ise )
            {
                // its OK, if the PID is just a single PID
                pidFilter = null;
            }
        }

        // check both PID and PID filter
        if ( pid != null && !ConfigurationUtil.isAllowedPid(pid) )
        {
            response.sendError(400);
        }
        if ( pidFilter != null && !ConfigurationUtil.isAllowedPid(pidFilter) )
        {
            response.sendError(400);
        }

        final Locale loc = request.getLocale();
        final String locale = ( loc != null ) ? loc.toString() : null;


        StringWriter json = new StringWriter();
        JSONWriter jw = new JSONWriter(json);
        jw.object();
        final ConfigAdminSupport cas = getConfigurationAdminSupport();
        // check for osgi installer plugin
        @SuppressWarnings("unchecked")
        final Map<String, Object> labelMap = (Map<String, Object>) request.getAttribute(ATTR_LABEL_MAP);
        jw.key("jsonsupport").value( labelMap.containsKey("osgi-installer-config-printer") );
        final boolean hasMetatype = cas.getMetaTypeSupport() != null;
        jw.key("status").value( cas != null ? Boolean.TRUE : Boolean.FALSE);
        jw.key("metatype").value( hasMetatype ? Boolean.TRUE : Boolean.FALSE);
        boolean hasConfigs = true;
        if ( cas != null )
        {
            hasConfigs = cas.getJsonSupport().listConfigurations( jw, pidFilter, locale, loc);
            cas.getJsonSupport().listFactoryConfigurations( jw, pidFilter, locale );
        }
        if ( !hasConfigs && !hasMetatype && cas != null ) {
            jw.key("noconfigs").value(true);
        } else {
            jw.key("noconfigs").value(false);
        }

        jw.endObject();

        // if a configuration is addressed, display it immediately
        if ( request.getParameter( ACTION_CREATE ) != null && pid != null ) {
            pid = ConfigurationUtil.getPlaceholderPid();
        }


        // prepare variables
        final String referer = request.getParameter( REFERER );
        final boolean factoryCreate = "true".equals( request.getParameter(FACTORY_CREATE) );

        final RequestVariableResolver vars = (RequestVariableResolver) request.getAttribute(RequestVariableResolver.REQUEST_ATTRIBUTE);
        vars.put( "__data__", json.toString() ); 
        vars.put( "selectedPid", pid != null ? pid : "" );
        vars.put( "configurationReferer", referer != null ? referer : "" );
        vars.put( "factoryCreate", Boolean.valueOf(factoryCreate) );
        vars.put( "param.apply", ACTION_APPLY );
        vars.put( "param.create", ACTION_CREATE );
        vars.put( "param.unbind", ACTION_UNBIND );
        vars.put( "param.delete", ACTION_DELETE );
        vars.put( "param.propertylist", PROPERTY_LIST );
        vars.put( "param.pidFilter", PID_FILTER );

        response.getWriter().print(TEMPLATE);
    }

    private ConfigAdminSupport getConfigurationAdminSupport() {
        Object configurationAdmin = getService( CONFIGURATION_ADMIN_NAME );
        if ( configurationAdmin != null ) {
            final List<ConfigurationHandler> handlers = new ArrayList<>();
            Object[] services = this.spiTracker.getServices();
            if (services != null) {
                for(final Object o : services) {
                    handlers.add((ConfigurationHandler)o);
                }
            }
            return new ConfigAdminSupport( this, configurationAdmin, handlers );
        }
        return null;
    }
}


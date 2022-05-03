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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.webconsole.SimpleWebConsolePlugin;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.felix.webconsole.WebConsoleUtil;
import org.apache.felix.webconsole.internal.OsgiManagerPlugin;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.misc.ServletSupport;
import org.apache.felix.webconsole.spi.ConfigurationHandler;
import org.apache.felix.webconsole.spi.ValidationException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;


/**
 * The <code>ConfigManager</code> class is the Web Console plugin to
 * manage configurations.
 */
public class ConfigManager extends SimpleWebConsolePlugin implements OsgiManagerPlugin, ServletSupport
{

    private static final long serialVersionUID = 5021174538498622428L;

    private static final String LABEL = "configMgr"; // was name //$NON-NLS-1$
    private static final String TITLE = "%configMgr.pluginTitle"; //$NON-NLS-1$
    private static final String CSS[] = { "/res/ui/config.css" }; //$NON-NLS-1$

    static final String PID_FILTER = "pidFilter"; //$NON-NLS-1$
    static final String PID = "pid"; //$NON-NLS-1$
    static final String FACTORY_PID = "factoryPid"; //$NON-NLS-1$
    static final String REFERER = "referer"; //$NON-NLS-1$
    static final String FACTORY_CREATE = "factoryCreate"; //$NON-NLS-1$

    static final String ACTION_CREATE = "create"; //$NON-NLS-1$
    static final String ACTION_DELETE = "delete"; //$NON-NLS-1$
    static final String ACTION_APPLY = "apply"; //$NON-NLS-1$
    static final String ACTION_UPDATE = "update"; //$NON-NLS-1$
    static final String ACTION_UNBIND = "unbind"; //$NON-NLS-1$
    static final String PROPERTY_LIST = "propertylist"; //$NON-NLS-1$
    static final String LOCATION = "$location"; //$NON-NLS-1$

    static final String CONFIGURATION_ADMIN_NAME = "org.osgi.service.cm.ConfigurationAdmin"; //$NON-NLS-1$
    static final String META_TYPE_NAME = "org.osgi.service.metatype.MetaTypeService"; //$NON-NLS-1$

    public static final String UNBOUND_LOCATION = "??unbound:bundle/location"; //$NON-NLS-1$

    // templates
    private final String TEMPLATE;

    // service tracker for SPI
    private ServiceTracker<ConfigurationHandler, ConfigurationHandler> spiTracker;

    /** Default constructor */
    public ConfigManager() {
        super(LABEL, TITLE, CATEGORY_OSGI, CSS);

        // load templates
        TEMPLATE = readTemplateFile( "/templates/config.html" ); //$NON-NLS-1$
    }

    @Override
    public void activate(final BundleContext bundleContext) {
        super.activate(bundleContext);
        this.spiTracker = new ServiceTracker<>(bundleContext, ConfigurationHandler.class.getName(), null);
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

    /**
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
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
            pid = WebConsoleUtil.urlDecode( info.substring( info.lastIndexOf( '/' ) + 1 ) );
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
            if ( request.getParameter( ConfigManager.ACTION_DELETE ) != null ) { //$NON-NLS-1$
                try {
                    cas.deleteConfiguration( pid );
                    Util.sendJsonOk(response);
                } catch ( final ValidationException ve) {
                    response.sendError(400, ve.getMessage());
                }
            }
            else
            {
                final String propertyList = request.getParameter( ConfigManager.PROPERTY_LIST ); //$NON-NLS-1$
                if ( propertyList == null ) {
                    response.sendError(400);
                    return;
                }

                try {
                    cas.applyConfiguration( request, pid, propertyList.split(","), ACTION_UPDATE.equals(request.getParameter(ACTION_APPLY)));
                    String redirect = pid;
                    if (pidFilter != null) {
                        redirect = redirect.concat("?").concat(PID_FILTER).concat("=").concat(pidFilter);
                    }

                    WebConsoleUtil.sendRedirect(request, response, redirect);
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
        response.setContentType( "application/json" ); //$NON-NLS-1$
        response.setCharacterEncoding( "UTF-8" ); //$NON-NLS-1$
        final Locale loc = Util.getLocale( request );
        final String locale = ( loc != null ) ? loc.toString() : null;
        cas.getJsonSupport().printConfigurationJson( response.getWriter(), pid, config, pidFilter, locale, spiTracker );
    }


    /**
     * @see org.apache.felix.webconsole.AbstractWebConsolePlugin#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException {
        // check for "post" requests from previous versions
        if ( "true".equals(request.getParameter("post")) ) { //$NON-NLS-1$ //$NON-NLS-2$
            this.doPost(request, response);
            return;
        }
        final String info = request.getPathInfo();
        // let's check for a JSON request
        if ( info.endsWith( ".json" ) ) //$NON-NLS-1$
        {
            response.setContentType( "application/json" ); //$NON-NLS-1$
            response.setCharacterEncoding( "UTF-8" ); //$NON-NLS-1$

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


            final Locale loc = Util.getLocale( request );
            final String locale = ( loc != null ) ? loc.toString() : null;

            final PrintWriter pw = response.getWriter();
            pw.write( "[" ); //$NON-NLS-1$
            final ConfigAdminSupport ca = this.getConfigurationAdminSupport();
            if ( ca != null )
            {
                // create filter
                final StringBuffer sb = new StringBuffer();
                if ( pid != null && pidFilter != null)
                {
                    sb.append("(&"); //$NON-NLS-1$
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
                            ca.getJsonSupport().printConfigurationJson( pw, config.getPid(), config, null, locale, spiTracker);
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
            pw.write( "]" ); //$NON-NLS-1$

            // nothing more to do
            return;
        }

        super.doGet( request, response );
    }

    /**
     * @see org.apache.felix.webconsole.AbstractWebConsolePlugin#renderContent(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void renderContent( HttpServletRequest request, HttpServletResponse response ) throws IOException
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

        final Locale loc = Util.getLocale( request );
        final String locale = ( loc != null ) ? loc.toString() : null;


        StringWriter json = new StringWriter();
        JSONWriter jw = new JSONWriter(json);
        jw.object();
        final ConfigAdminSupport cas = getConfigurationAdminSupport();
        // check for osgi installer plugin
        @SuppressWarnings("unchecked")
        final Map<String, Object> labelMap = (Map<String, Object>) request.getAttribute(WebConsoleConstants.ATTR_LABEL_MAP);
        jw.key("jsonsupport").value( labelMap.containsKey("osgi-installer-config-printer") ); //$NON-NLS-1$
        final boolean hasMetatype = cas.getMetaTypeSupport() != null;
        jw.key("status").value( cas != null ? Boolean.TRUE : Boolean.FALSE); //$NON-NLS-1$
        jw.key("metatype").value( hasMetatype ? Boolean.TRUE : Boolean.FALSE); //$NON-NLS-1$
        boolean hasConfigs = true;
        if ( cas != null )
        {
            hasConfigs = cas.getJsonSupport().listConfigurations( jw, pidFilter, locale, loc, spiTracker);
            cas.getJsonSupport().listFactoryConfigurations( jw, pidFilter, locale );
        }
        if ( !hasConfigs && !hasMetatype && cas != null ) {
            jw.key("noconfigs").value(true); //$NON-NLS-1$
        } else {
            jw.key("noconfigs").value(false); //$NON-NLS-1$
        }

        jw.endObject();

        // if a configuration is addressed, display it immediately
        if ( request.getParameter( ACTION_CREATE ) != null && pid != null ) {
            pid = ConfigurationUtil.getPlaceholderPid();
        }


        // prepare variables
        final String referer = request.getParameter( REFERER );
        final boolean factoryCreate = "true".equals( request.getParameter(FACTORY_CREATE) ); //$NON-NLS-1$
        final Map<String, Object> vars = ( ( Map<String, Object> ) WebConsoleUtil.getVariableResolver( request ) );
        vars.put( "__data__", json.toString() ); //$NON-NLS-1$
        vars.put( "selectedPid", pid != null ? pid : "" ); //$NON-NLS-1$ //$NON-NLS-2$
        vars.put( "configurationReferer", referer != null ? referer : "" ); //$NON-NLS-1$ //$NON-NLS-2$
        vars.put( "factoryCreate", Boolean.valueOf(factoryCreate) ); //$NON-NLS-1$
        vars.put( "param.apply", ACTION_APPLY ); //$NON-NLS-1$
        vars.put( "param.create", ACTION_CREATE ); //$NON-NLS-1$
        vars.put( "param.unbind", ACTION_UNBIND ); //$NON-NLS-1$
        vars.put( "param.delete", ACTION_DELETE ); //$NON-NLS-1$
        vars.put( "param.propertylist", PROPERTY_LIST ); //$NON-NLS-1$
        vars.put( "param.pidFilter", PID_FILTER ); //$NON-NLS-1$

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

    @Override
    public BundleContext getBundleContext() {
        return super.getBundleContext();
    }
}


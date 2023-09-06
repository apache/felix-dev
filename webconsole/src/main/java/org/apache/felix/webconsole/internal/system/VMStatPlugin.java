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
package org.apache.felix.webconsole.internal.system;

import java.io.IOException;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Map;


import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.webconsole.internal.servlet.AbstractOsgiManagerPlugin;
import org.apache.felix.webconsole.internal.servlet.OsgiManager;
import org.apache.felix.webconsole.servlet.RequestVariableResolver;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.startlevel.FrameworkStartLevel;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * VMStatPlugin provides the System Information tab. This particular plugin uses
 * more than one templates.
 */
public class VMStatPlugin extends AbstractOsgiManagerPlugin {

    private static final long serialVersionUID = 2293375003997163600L;

    private static final String LABEL = "vmstat";
    private static final String TITLE = "%vmstat.pluginTitle";

    private static final String ATTR_TERMINATED = "terminated";

    private static final String PARAM_SHUTDOWN_TIMER = "shutdown_timer";
    private static final String PARAM_SHUTDOWN_TYPE = "shutdown_type";
    private static final String PARAM_SHUTDOWN_TYPE_RESTART = "Restart";

    private static final long startDate = System.currentTimeMillis();

    // templates
    private final String TPL_VM_MAIN;
    private final String TPL_VM_STOP;
    private final String TPL_VM_RESTART;


    /** 
     * Default constructor 
     * @throws IOException If template can't be read
     */
    public VMStatPlugin() throws IOException {
        // load templates
        TPL_VM_MAIN = readTemplateFile(  "/templates/vmstat.html"  );
        TPL_VM_STOP = readTemplateFile( "/templates/vmstat_stop.html" );
        TPL_VM_RESTART = readTemplateFile( "/templates/vmstat_restart.html" );
    }

    @Override
    protected String getCategory() {
        return CATEGORY_OSGI_MANAGER;
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
    protected void doPost( HttpServletRequest request, HttpServletResponse response )
    throws ServletException, IOException {
        final String action = request.getParameter( "action");

        if ( "setStartLevel".equals( action )) {
            final FrameworkStartLevel fsl = this.getBundleContext().getBundle(Constants.SYSTEM_BUNDLE_LOCATION).adapt(FrameworkStartLevel.class);
            if ( fsl != null ){
                int bundleSL = getParameterInt( request, "bundleStartLevel", -1 );
                if ( bundleSL > 0 && bundleSL != fsl.getInitialBundleStartLevel() ) {
                    fsl.setInitialBundleStartLevel( bundleSL );
                }

                int systemSL = getParameterInt( request, "systemStartLevel", -1 );
                if ( systemSL > 0 && systemSL != fsl.getStartLevel() ) {
                    fsl.setStartLevel( systemSL );
                }
            }
        } else if ( "gc".equals( action ) )  {
            System.gc();
            System.gc(); // twice for sure
        } else if ( request.getParameter( PARAM_SHUTDOWN_TIMER ) == null ) {

            // whether to stop or restart the framework
            final boolean restart = PARAM_SHUTDOWN_TYPE_RESTART.equals( request.getParameter( PARAM_SHUTDOWN_TYPE ) );

            // simply terminate VM in case of shutdown :-)
            final Bundle systemBundle = getBundleContext().getBundle( 0 );
            Thread t = new Thread( "Stopper" ) {
                public void run() {
                    try {
                        Thread.sleep( 2000L );
                    } catch ( InterruptedException ie ) {
                        // ignore
                    }

                    log( "Shutting down server now!" );

                    // stopping bundle 0 (system bundle) stops the framework
                    try {
                        if ( restart ) {
                            systemBundle.update();
                        } else {
                            systemBundle.stop();
                        }
                    } catch ( BundleException be ) {
                        log( "Problem stopping or restarting the Framework", be );
                    }
                }
            };
            t.start();

            request.setAttribute( ATTR_TERMINATED, ATTR_TERMINATED );
            request.setAttribute( PARAM_SHUTDOWN_TYPE, restart );
        }

        // render the response without redirecting
        doGet( request, response );
    }

    @Override
    @SuppressWarnings("unchecked")
    public void renderContent( HttpServletRequest request, HttpServletResponse response ) throws IOException {
        final FrameworkStartLevel fsl = this.getBundleContext().getBundle(Constants.SYSTEM_BUNDLE_LOCATION).adapt(FrameworkStartLevel.class);

        Map<String, Object> configuration = (Map<String, Object>) request.getAttribute( ServletConstants.ATTR_CONFIGURATION );
        String body;

        if ( request.getAttribute( ATTR_TERMINATED ) != null ) {
            Object restart = request.getAttribute( PARAM_SHUTDOWN_TYPE );
            if ( ( restart instanceof Boolean ) && ( ( Boolean ) restart ).booleanValue() ) {
                StringWriter json = new StringWriter();

                int reloadTimeout = (int) configuration.get( OsgiManager.PROP_RELOAD_TIMEOUT );
                JSONWriter jw = new JSONWriter(json);
                jw.object();
                jw.key( "reloadTimeout").value( reloadTimeout );
                jw.endObject();
                jw.flush();

                final RequestVariableResolver vars = this.getVariableResolver(request);
                vars.put( "data", json.toString() );

                body = TPL_VM_RESTART;
            } else {
                body = TPL_VM_STOP;
            }
            response.getWriter().print( body );
            return;
        }

        body = TPL_VM_MAIN;

        long freeMem = Runtime.getRuntime().freeMemory() / 1024;
        long totalMem = Runtime.getRuntime().totalMemory() / 1024;
        long usedMem = totalMem - freeMem;

        boolean shutdownTimer = request.getParameter( PARAM_SHUTDOWN_TIMER ) != null;
        String shutdownType = request.getParameter( PARAM_SHUTDOWN_TYPE );
        if ( shutdownType == null ) {
            shutdownType = "";
        }

        DateFormat format = DateFormat.getDateTimeInstance( DateFormat.LONG, DateFormat.LONG, request.getLocale() );
        final String startTime = format.format( new Date( startDate ) );
        final String upTime = formatPeriod( System.currentTimeMillis() - startDate );

        StringWriter json = new StringWriter();
        JSONWriter jw = new JSONWriter(json);
        jw.object();

        jw.key( "systemStartLevel").value(fsl.getStartLevel() );
        jw.key( "bundleStartLevel").value(fsl.getInitialBundleStartLevel() );
        jw.key( "lastStarted").value(startTime );
        jw.key( "upTime").value(upTime );
        jw.key( "runtime").value(sysProp( "java.runtime.name" ) + "(build "
                + sysProp( "java.runtime.version" ) + ")" );
        jw.key( "jvm").value(sysProp( "java.vm.name" ) + "(build " + sysProp( "java.vm.version" )
        + ", " + sysProp( "java.vm.info" ) + ")" );
        jw.key( "shutdownTimer").value(shutdownTimer );
        jw.key( "mem_total").value(totalMem );
        jw.key( "mem_free").value(freeMem );
        jw.key( "mem_used").value(usedMem );
        jw.key( "shutdownType").value(shutdownType );

        int shutdownTimeout = (int) configuration.get( OsgiManager.PROP_SHUTDOWN_TIMEOUT );
        jw.key( "shutdownTimeout").value(shutdownTimeout );

        // only add the processors if the number is available
        final int processors = getAvailableProcessors();
        if ( processors > 0 ) {
            jw.key( "processors").value(processors );
        }

        jw.endObject();

        jw.flush();

        final RequestVariableResolver vars = this.getVariableResolver(request);
        vars.put( "startData", json.toString() );

        response.getWriter().print( body );
    }

    private static final String sysProp( String name ) {
        String ret = System.getProperty( name );
        if ( null == ret || ret.length() == 0 ) {
            ret = "n/a";
        }
        return ret;
    }

    private static final String formatPeriod( final long period ) {
        final long msecs = period % 1000;
        final long secs = period / 1000 % 60;
        final long mins = period / 1000 / 60 % 60;
        final long hours = period / 1000 / 60 / 60 % 24;
        final long days = period / 1000 / 60 / 60 / 24;
        return MessageFormat.format(
                "{0,number} '${vmstat.upTime.format.days}' {1,number,00}:{2,number,00}:{3,number,00}.{4,number,000}",
                new Object[]
                        { days, hours, mins, secs, msecs } );
    }

    /**
     * Returns the number of processor available on Java 1.4 and newer runtimes.
     * If the Runtime.availableProcessors() method is not available, this
     * method returns -1.
     */
    private static final int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }
}

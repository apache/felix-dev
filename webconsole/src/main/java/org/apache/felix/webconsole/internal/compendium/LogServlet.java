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
package org.apache.felix.webconsole.internal.compendium;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.webconsole.SimpleWebConsolePlugin;
import org.apache.felix.webconsole.WebConsoleUtil;
import org.apache.felix.webconsole.internal.OsgiManagerPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;


/**
 * LogServlet provides support for reading the log messages.
 */
public class LogServlet extends SimpleWebConsolePlugin implements OsgiManagerPlugin
{
    private static final String LABEL = "logs";
    private static final String TITLE = "%logs.pluginTitle";
    private static final String CSS[] = { "/res/ui/logs.css" };

    private final static int MAX_LOGS = 200; //maximum number of log entries

    // templates
    private final String TEMPLATE;

    /** Default constructor */
    public LogServlet()
    {
        super(LABEL, TITLE, CATEGORY_OSGI, CSS);

        // load templates
        TEMPLATE = readTemplateFile( "/templates/logs.html" );
    }


    /**
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doPost( HttpServletRequest req, HttpServletResponse resp ) throws IOException
    {
        final int minLevel = WebConsoleUtil.getParameterInt( req, "minLevel", LogService.LOG_DEBUG);

        resp.setContentType( "application/json" );
        resp.setCharacterEncoding( "utf-8" );

        renderJSON( resp.getWriter(), minLevel, trasesEnabled(req) );
    }

    private static boolean trasesEnabled( final HttpServletRequest req )
    {
        String traces = req.getParameter("traces");
        return null == traces ? false : Boolean.valueOf( traces ).booleanValue();
    }

    @SuppressWarnings("rawtypes")
    private final void renderJSON( final PrintWriter pw, int minLogLevel, boolean traces ) throws IOException
    {
        // create status line
        final LogReaderService logReaderService = ( LogReaderService ) this.getService( LogReaderService.class
                .getName() );

        JSONWriter jw = new JSONWriter( pw );
        jw.object();

        jw.key( "status" );
        jw.value( logReaderService == null ? Boolean.FALSE : Boolean.TRUE );

        jw.key( "data" );
        jw.array();

        if ( logReaderService != null )
        {
            int index = 0;
            for ( Enumeration logEntries = logReaderService.getLog(); logEntries.hasMoreElements()
                    && index < MAX_LOGS; )
            {
                LogEntry nextLog = ( LogEntry ) logEntries.nextElement();
                if ( nextLog.getLevel() <= minLogLevel )
                {
                    logJson( jw, nextLog, index++, traces );
                }
            }
        }

        jw.endArray();

        jw.endObject();

    }


    /**
     * @see org.apache.felix.webconsole.AbstractWebConsolePlugin#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException,
    IOException
    {
        final int minLevel = WebConsoleUtil.getParameterInt( request, "minLevel", LogService.LOG_DEBUG );
        final String info = request.getPathInfo();
        if ( info.endsWith( ".json" ) )
        {
            response.setContentType( "application/json" );
            response.setCharacterEncoding( "UTF-8" );

            PrintWriter pw = response.getWriter();
            this.renderJSON( pw, minLevel, trasesEnabled(request) );
            return;
        }
        super.doGet( request, response );
    }


    /**
     * @see org.apache.felix.webconsole.AbstractWebConsolePlugin#renderContent(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void renderContent( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {
        response.getWriter().print(TEMPLATE);
    }


    private static final void logJson( JSONWriter jw, LogEntry info, int index, boolean traces ) throws IOException
    {
        jw.object();
        jw.key( "id" );
        jw.value( String.valueOf( index ) );
        jw.key( "received" );
        jw.value( info.getTime() );
        jw.key( "level" );
        jw.value( logLevel( info.getLevel() ) );
        jw.key( "raw_level" );
        jw.value( info.getLevel() );
        jw.key( "message" );
        jw.value( info.getMessage() );
        jw.key( "service" );
        jw.value( serviceDescription( info.getServiceReference() ) );
        jw.key( "exception" );
        jw.value( exceptionMessage( info.getException(), traces ) );
        Bundle bundle = info.getBundle();
        if (null != bundle)
        {
            jw.key("bundleId");
            jw.value(bundle.getBundleId());
            String name = (String) bundle.getHeaders().get(Constants.BUNDLE_NAME);
            if (null == name)
            {
                name = bundle.getSymbolicName();
            }
            if (null == name)
            {
                name = bundle.getLocation();
            }
            jw.key("bundleName");
            jw.value(name);
        }
        jw.endObject();
    }

    @SuppressWarnings("rawtypes")
    private static final String serviceDescription( ServiceReference serviceReference )
    {
        if ( serviceReference == null )
        {
            return "";
        }
        return serviceReference.toString();
    }


    private static final String logLevel( int level )
    {
        switch ( level )
        {
        case LogService.LOG_INFO:
            return "INFO";
        case LogService.LOG_WARNING:
            return "WARNING";
        case LogService.LOG_ERROR:
            return "ERROR";
        case LogService.LOG_DEBUG:
        default:
            return "DEBUG";
        }
    }

    private static final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private static final PrintStream printStream = new PrintStream(baos);

    private static final String exceptionMessage( Throwable e, boolean traces )
    {
        if ( e == null )
        {
            return "";
        }
        if (traces) {
            String ret = null;
            synchronized (printStream)
            {
                try {
                    e.printStackTrace(printStream);
                    ret = baos.toString();
                } finally {
                    baos.reset();
                }
            }
            return ret;
        }
        return e.getClass().getName() + ": " + e.getMessage();
    }

}

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
package org.apache.felix.webconsole.plugins.obr.internal;


import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.felix.webconsole.servlet.AbstractServlet;
import org.apache.felix.webconsole.servlet.RequestVariableResolver;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import jakarta.servlet.Servlet;


/**
 * This class provides a plugin for rendering the available OSGi Bundle Repositories
 * and the resources they provide.
 */
class WebConsolePlugin extends AbstractServlet
{
    private static final String LABEL = "obr";
    private static final String TITLE = "%obr.pluginTitle";
    private static final String CATEGORY = "OSGi";
    private static final String CSS[] = { "/" + LABEL + "/res/plugin.css" };

    // templates
    private final String TEMPLATE;

    private AbstractBundleRepositoryRenderHelper helper;

    private ServiceRegistration<Servlet> registration;

    private BundleContext bundleContext;

    /**
     *
     */
    public WebConsolePlugin()
    {
        // load templates
        try {
            TEMPLATE = readTemplateFile("/res/plugin.html");
        } catch (IOException e) {
            throw new RuntimeException("Unable to read template");
        }
    }

    public WebConsolePlugin register(final BundleContext context) {
        this.bundleContext = context;
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(ServletConstants.PLUGIN_LABEL, LABEL);
        props.put(ServletConstants.PLUGIN_TITLE, TITLE);
        props.put(ServletConstants.PLUGIN_CATEGORY, CATEGORY);
        props.put(ServletConstants.PLUGIN_CSS_REFERENCES, CSS);

        this.registration = context.registerService(Servlet.class, this, props);
        return this;
    }

    public void deactivate()
    {
        if (this.registration != null) {
            try {
                this.registration.unregister();
            } catch ( final IllegalStateException ignore) {
                // ignore
            }
            this.registration = null;
        }
        if ( helper != null )
        {
            helper.dispose();
            helper = null;
        }
    }

    @Override
    public void renderContent( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {
        // prepare variables
        RequestVariableResolver vars = this.getVariableResolver(request);
        vars.put( "__data__", getData( request ) );

        response.getWriter().print( TEMPLATE );
    }


    @Override
    protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException,
        IOException
    {
        if ( !hasRepositoryAdmin() )
        {
            response.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "RepositoryAdmin service is missing" );
            return;
        }

        final String action = request.getParameter( "action" );
        final String deploy = request.getParameter( "deploy" );
        final String deploystart = request.getParameter( "deploystart" );
        final String optional = request.getParameter( "optional" );

        if ( action != null )
        {
            doAction( action, request.getParameter( "url" ) );
            response.getWriter().print( getData( request ) );
            return;
        }

        if ( deploy != null || deploystart != null )
        {
            doDeploy( request.getParameterValues( "bundle" ), deploystart != null, optional != null );
            doGet( request, response );
            return;
        }

        super.doPost( request, response );
    }


    AbstractBundleRepositoryRenderHelper getHelper()
    {
        if ( helper == null )
        {
            try
            {
                helper = new FelixBundleRepositoryRenderHelper( this.bundleContext );
            }
            catch ( Throwable felixt )
            {
                // ClassNotFoundException, ClassDefNotFoundError

                try
                {
                    helper = new OsgiBundleRepositoryRenderHelper( this.bundleContext );
                }
                catch ( Throwable osgit )
                {
                    // ClassNotFoundException, ClassDefNotFoundError
                }
            }
        }

        return helper;
    }


    private boolean hasRepositoryAdmin()
    {
        AbstractBundleRepositoryRenderHelper helper = getHelper();
        return helper != null && helper.hasRepositoryAdmin();
    }


    private String getData( final HttpServletRequest request )
    {
        AbstractBundleRepositoryRenderHelper helper = getHelper();
        if ( helper == null || !helper.hasRepositoryAdmin() )
        {
            return "{}";
        }

        RequestInfo info = new RequestInfo( request );

        final String filter;
        String list = info.getList();
        if ( list != null )
        {
            if ( "-".equals( list ) )
            {
                StringBuffer sb = new StringBuffer( "(!(|" );
                for ( int c = 0; c < 26; c++ )
                {
                    sb.append( "(presentationname=" ).append( ( char ) ( 'a' + c ) )
                      .append( "*)(presentationname=" ).append( ( char ) ( 'A' + c ) )
                      .append( "*)" );
                }
                sb.append( "))" );
                filter = sb.toString();
            }
            else
            {
                filter = "(|(presentationname=" + list.toLowerCase()
                    + "*)(presentationname=" + list.toUpperCase() + "*))";
            }
        }
        else
        {
            String query = info.getQuery();
            if ( query != null )
            {
                if ( query.indexOf( '=' ) > 0 )
                {
                    if ( query.startsWith( "(" ) )
                    {
                        filter = query;
                    }
                    else
                    {
                        filter = "(" + query + ")";
                    }
                }
                else
                {
                    filter = "(|(presentationame=*" + query + "*)(symbolicname=*" + query + "*))";
                }
            }
            else
            {
                StringBuffer sb = new StringBuffer( "(&" );
                for ( Enumeration e = request.getParameterNames(); e.hasMoreElements(); )
                {
                    String k = ( String ) e.nextElement();
                    String v = request.getParameter( k );
                    if ( v != null && v.length() > 0
                        && !"details".equals( k ) 
                        && !"deploy".equals( k )
                        && !"deploystart".equals( k ) 
                        && !"bundle".equals( k ) 
                        && !"optional".equals( k ) )
                    {
                        sb.append( '(' ).append( k ).append( '=' ).append( v ).append( ')' );
                    }
                }
                sb.append( ')' );
                filter = (sb.length() > 3) ? sb.toString() : null;
            }
        }

        return helper.getData( filter, info.showDetails(), this.bundleContext.getBundles() );
    }


    private void doAction( String action, String urlParam ) throws IOException, ServletException
    {
        AbstractBundleRepositoryRenderHelper helper = getHelper();
        if ( helper != null )
        {
            helper.doAction( action, urlParam );
        }
    }


    private void doDeploy( String[] bundles, boolean start, boolean optional )
    {
        AbstractBundleRepositoryRenderHelper helper = getHelper();
        if ( helper != null )
        {
            helper.doDeploy( bundles, start, optional );
        }
    }

    private static class RequestInfo {

        private final HttpServletRequest request;

        private boolean details;
        private String query;
        private String list;


        RequestInfo( final HttpServletRequest request )
        {
            this.request = request;
        }


        boolean showDetails()
        {
            getQuery();
            return details;
        }


        String getQuery()
        {
            if ( query == null )
            {
                String query = request.getParameter( "query" );
                boolean details = false;
                if ( query == null && request.getPathInfo().length() > 5 )
                {
                    // cut off "/obr/" prefix (might want to use getTitle ??)
                    String path = request.getPathInfo().substring( 5 );
                    int slash = path.indexOf( '/' );
                    if ( slash < 0 )
                    {
                        // symbolic name only, version ??
                        query = "(symbolicname=" + path + ")";
                    }
                    else
                    {
                        query = "(&(symbolicname=" + path.substring( 0, slash ) 
                            + ")(version=" + path.substring( slash + 1 ) + "))";
                        details = true;
                    }
                }

                this.query = query;
                this.details = details;
            }
            return query;
        }


        String getList()
        {
            if ( list == null )
            {
                list = request.getParameter( "list" );
                if ( list == null && !request.getParameterNames().hasMoreElements() && getQuery() == null )
                {
                    list = "a";
                }
            }
            return list;
        }
    }
}

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
package org.apache.felix.webconsole;


import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.servlet.ServletRequestContext;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.servlet.OsgiManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;


public abstract class AbstractWebConsolePlugin extends HttpServlet
{

    /** Pseudo class version ID to keep the IDE quite. */
    private static final long serialVersionUID = 1L;

    /** The name of the request attribute containig the map of FileItems from the POST request */
    public static final String ATTR_FILEUPLOAD = "org.apache.felix.webconsole.fileupload";

    private static final String HEADER = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">"
        + "<html>"
        + "<head>"
        + "<meta http-equiv=\"Content-Type\" content=\"text/html; utf-8\">"
        + "<link rel=\"icon\" href=\"{15}/res/imgs/favicon.ico\">"
        + "<title>{0} - {12}</title>"
        + "<script src=\"{15}/res/ui/admin.js\" language=\"JavaScript\"></script>"
        + "<script language=\"JavaScript\">"
        + "ABOUT_VERSION=''{1}'';"
        + "ABOUT_JVERSION=''{2}'';"
        + "ABOUT_JRT=''{3} (build {2})'';"
        + "ABOUT_JVM=''{4} (build {5}, {6})'';"
        + "ABOUT_MEM=\"{7} KB\";"
        + "ABOUT_USED=\"{8} KB\";"
        + "ABOUT_FREE=\"{9} KB\";"
        + "appRoot = \"{15}\";"
        + "pluginRoot = appRoot + \"/{16}\";"
        + "</script>"
        + "<link href=\"{15}/res/ui/admin.css\" rel=\"stylesheet\" type=\"text/css\">"
        + "</head>"
        + "<body>"
        + "<div id=\"main\">"
        + "<div id=\"lead\">"
        + "<h1>"
        + "{0}<br>{12}"
        + "</h1>"
        + "<p>"
        + "<a target=\"_blank\" href=\"{13}\" title=\"{11}\"><img src=\"{15}/res/imgs/logo.png\" width=\"165\" height=\"63\" border=\"0\"></a>"
        + "</p>" + "</div>";

    private BundleContext bundleContext;

    private String adminTitle;
    private String adminVersion;
    private String productName;
    private String productWeb;
    private String vendorName;
    private String vendorWeb;


    //---------- HttpServlet Overwrites ----------------------------------------

    /**
     * Returns the title for this plugin as returned by {@link #getTitle()}
     */
    public String getServletName()
    {
        return getTitle();
    }


    /**
     * Renders the web console page for the request. This consist of the following
     * four parts called in order:
     * <ol>
     * <li>{@link #startResponse(HttpServletRequest, HttpServletResponse)}</li>
     * <li>{@link #renderTopNavigation(HttpServletRequest, PrintWriter)}</li>
     * <li>{@link #renderContent(HttpServletRequest, HttpServletResponse)}</li>
     * <li>{@link #endResponse(PrintWriter)}</li>
     * </ol>
     */
    protected void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException,
        IOException
    {
        PrintWriter pw = startResponse( request, response );
        renderTopNavigation( request, pw );
        renderContent( request, response );
        endResponse( pw );
    }


    //---------- AbstractWebConsolePlugin API ----------------------------------

    public void activate( BundleContext bundleContext )
    {
        this.bundleContext = bundleContext;

        Dictionary headers = bundleContext.getBundle().getHeaders();

        adminTitle = ( String ) headers.get( Constants.BUNDLE_NAME ); // "OSGi Management Console";
        adminVersion = ( String ) headers.get( Constants.BUNDLE_NAME ); // "1.0.0-SNAPSHOT";
        productName = "Apache Felix";
        productWeb = ( String ) headers.get( Constants.BUNDLE_DOCURL );
        vendorName = ( String ) headers.get( Constants.BUNDLE_VENDOR );
        vendorWeb = "http://www.apache.org";
    }


    public void deactivate()
    {
        this.bundleContext = null;
    }


    public abstract String getTitle();


    public abstract String getLabel();


    protected abstract void renderContent( HttpServletRequest req, HttpServletResponse res ) throws ServletException,
        IOException;


    protected BundleContext getBundleContext()
    {
        return bundleContext;
    }


    protected PrintWriter startResponse( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {
        response.setCharacterEncoding( "utf-8" );
        response.setContentType( "text/html" );

        PrintWriter pw = response.getWriter();

        long freeMem = Runtime.getRuntime().freeMemory() / 1024;
        long totalMem = Runtime.getRuntime().totalMemory() / 1024;
        long usedMem = totalMem - freeMem;

        String header = MessageFormat.format( HEADER, new Object[]
            { adminTitle, adminVersion, System.getProperty( "java.runtime.version" ),
                System.getProperty( "java.runtime.name" ), System.getProperty( "java.vm.name" ),
                System.getProperty( "java.vm.version" ), System.getProperty( "java.vm.info" ), new Long( totalMem ),
                new Long( usedMem ), new Long( freeMem ), vendorWeb, productName, getTitle(), productWeb, vendorName,
                ( String ) request.getAttribute( OsgiManager.ATTR_APP_ROOT ), getLabel() } );
        pw.println( header );

        return pw;
    }


    protected void renderTopNavigation( HttpServletRequest request, PrintWriter pw )
    {
        // assume pathInfo to not be null, else this would not be called
        String current = request.getPathInfo();
        int slash = current.indexOf( "/", 1 );
        if ( slash > 1 )
        {
            current = current.substring( 1, slash );
        }

        boolean disabled = false;
        String appRoot = ( String ) request.getAttribute( OsgiManager.ATTR_APP_ROOT );
        Map labelMap = ( Map ) request.getAttribute( OsgiManager.ATTR_LABEL_MAP );
        if ( labelMap != null )
        {
            pw.println( "<p id='technav'>" );

            SortedMap map = new TreeMap();
            for ( Iterator ri = labelMap.entrySet().iterator(); ri.hasNext(); )
            {
                Map.Entry labelMapEntry = ( Map.Entry ) ri.next();
                if ( labelMapEntry.getKey() == null )
                {
                    // ignore renders without a label
                }
                else if ( disabled || current.equals( labelMapEntry.getKey() ) )
                {
                    map.put( labelMapEntry.getValue(), "<span class='technavat'>" + labelMapEntry.getValue()
                        + "</span>" );
                }
                else
                {
                    map.put( labelMapEntry.getValue(), "<a href='" + appRoot + "/" + labelMapEntry.getKey() + "'>"
                        + labelMapEntry.getValue() + "</a></li>" );
                }
            }

            for ( Iterator li = map.values().iterator(); li.hasNext(); )
            {
                pw.println( li.next() );
            }

            pw.println( "</p>" );
        }
    }


    protected void endResponse( PrintWriter pw )
    {
        pw.println( "</body>" );
        pw.println( "</html>" );
    }


    public static String getParameter( HttpServletRequest request, String name )
    {
        // just get the parameter if not a multipart/form-data POST
        if ( !ServletFileUpload.isMultipartContent( new ServletRequestContext( request ) ) )
        {
            return request.getParameter( name );
        }

        // check, whether we alread have the parameters
        Map params = ( Map ) request.getAttribute( ATTR_FILEUPLOAD );
        if ( params == null )
        {
            // parameters not read yet, read now
            // Create a factory for disk-based file items
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setSizeThreshold( 256000 );

            // Create a new file upload handler
            ServletFileUpload upload = new ServletFileUpload( factory );
            upload.setSizeMax( -1 );

            // Parse the request
            params = new HashMap();
            try
            {
                List items = upload.parseRequest( request );
                for ( Iterator fiter = items.iterator(); fiter.hasNext(); )
                {
                    FileItem fi = ( FileItem ) fiter.next();
                    FileItem[] current = ( FileItem[] ) params.get( fi.getFieldName() );
                    if ( current == null )
                    {
                        current = new FileItem[]
                            { fi };
                    }
                    else
                    {
                        FileItem[] newCurrent = new FileItem[current.length + 1];
                        System.arraycopy( current, 0, newCurrent, 0, current.length );
                        newCurrent[current.length] = fi;
                        current = newCurrent;
                    }
                    params.put( fi.getFieldName(), current );
                }
            }
            catch ( FileUploadException fue )
            {
                // TODO: log
            }
            request.setAttribute( ATTR_FILEUPLOAD, params );
        }

        FileItem[] param = ( FileItem[] ) params.get( name );
        if ( param != null )
        {
            for ( int i = 0; i < param.length; i++ )
            {
                if ( param[i].isFormField() )
                {
                    return param[i].getString();
                }
            }
        }

        // no valid string parameter, fail
        return null;
    }

}

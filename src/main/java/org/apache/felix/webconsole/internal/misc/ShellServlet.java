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
package org.apache.felix.webconsole.internal.misc;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLDecoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.shell.ShellService;
import org.apache.felix.webconsole.SimpleWebConsolePlugin;
import org.apache.felix.webconsole.WebConsoleUtil;
import org.apache.felix.webconsole.internal.OsgiManagerPlugin;


/**
 * ShellServlet provides a Web bases interface to the Apache shell service, allowing
 * the user to execute shell commands from the browser.
 */
public class ShellServlet extends SimpleWebConsolePlugin implements OsgiManagerPlugin
{

    private static final String LABEL = "shell";
    private static final String TITLE = "Shell";
    private static final String[] CSS = { "/res/ui/shell.css" };

    // templates
    private final String TEMPLATE;

    /** Default constructor */
    public ShellServlet()
    {
        super(LABEL, TITLE, CSS);

        // load templates
        TEMPLATE = readTemplateFile( "/templates/shell.html" );
    }


    /**
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException,
        IOException
    {
        response.setCharacterEncoding( "utf-8" );
        response.setContentType( "text/html" );

        PrintWriter pw = response.getWriter();

        try
        {
            String command = request.getParameter( "command" );
            if (command != null) command = URLDecoder.decode(command);

            pw.print( "<span class=\"consolecommand\">-&gt; " );
            pw.print( command == null ? "" : WebConsoleUtil.escapeHtml( command ) );
            pw.println( "</span><br />" );

            if ( command != null && !"".equals( command ) )
            {
                ShellService shellService = getShellService();
                if ( shellService != null )
                {
                    ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
                    ByteArrayOutputStream baosErr = new ByteArrayOutputStream();

                    shellService.executeCommand( command, new PrintStream( baosOut, true ), new PrintStream( baosErr,
                        true ) );
                    if ( baosOut.size() > 0 )
                    {
                        pw.print( WebConsoleUtil.escapeHtml( new String( baosOut.toByteArray() ) ) );
                    }
                    if ( baosErr.size() > 0 )
                    {
                        pw.print( "<span class=\"error\">" );
                        pw.print( WebConsoleUtil.escapeHtml( new String( baosErr.toByteArray() ) ) );
                        pw.println( "</span>" );
                    }
                }
                else
                {
                    pw.print( "<span class=\"error\">" );
                    pw.print( "Error: No shell service available<br />" );
                    pw.println( "</span>" );
                }
            }
        }
        catch ( Throwable t )
        {
            pw.print( "<span class=\"error\">" );
            StringWriter out = new StringWriter();
            t.printStackTrace( new PrintWriter( out, true ) );
            pw.print( WebConsoleUtil.escapeHtml( out.toString() ) );
            pw.println( "</span>" );
        }
    }

    /**
     * @see org.apache.felix.webconsole.AbstractWebConsolePlugin#renderContent(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void renderContent( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {
        response.getWriter().print(TEMPLATE);
    }


    private final ShellService getShellService()
    {
        try {
            return ((ShellService) getService(ShellService.class.getName()));
        } catch (NoClassDefFoundError ncdfe) {
            // shell service class not available
        }
        return null;
    }

}

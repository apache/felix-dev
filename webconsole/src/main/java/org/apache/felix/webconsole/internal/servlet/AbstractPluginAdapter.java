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
package org.apache.felix.webconsole.internal.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.felix.webconsole.servlet.RequestVariableResolver;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.apache.felix.webconsole.spi.BrandingPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Base class for the servlet adapters
 */
public abstract class AbstractPluginAdapter extends HttpServlet {

    public static final String ATTR_LANG_MAP = "felix.webconsole.langMap";

    /** Pseudo class version ID to keep the IDE quite. */
    private static final long serialVersionUID = 1L;

    /**
     * The header fragment read from the templates/main_header.html file
     */
    private static final String HEADER = readTemplateFile( "/templates/main_header.html" );

    /**
     * The footer fragment read from the templates/main_footer.html file
     */
    private static final String FOOTER = readTemplateFile( "/templates/main_footer.html" );

    private static volatile BrandingPlugin BRANDING_PLUGIN = new BrandingPluginImpl();

    private static volatile int LOGLEVEL;

    private volatile BundleContext bundleContext;

    private final String title;

    protected final String label;

    private final String[] cssReferences;

    protected abstract URL getResource(final String pathInfo);

    public AbstractPluginAdapter(final BundleContext bundleContext, final String label, final String title, final String[] cssReferences) {
        this.title = title;
        this.label = label;
        this.cssReferences = cssReferences;
        this.activate(bundleContext);
    }

    /**
     * This method is used to render the content of the plug-in. It is called internally
     * from the Web Console.
     *
     * @param req the HTTP request send from the user
     * @param res the HTTP response object, where to render the plugin data.
     * @throws IOException if an input or output error is
     *  detected when the servlet handles the request
     * @throws ServletException  if the request for the GET
     *  could not be handled
     */
    protected abstract void renderContent(final HttpServletRequest req, final HttpServletResponse res )
    throws ServletException, IOException;

    //---------- HttpServlet Overwrites ----------------------------------------

    /**
     * Returns the title for this plugin
     */
    @Override
    public String getServletName() {
        return this.title;
    }

    /**
     * Renders the web console page for the request. This consist of the
     * following five parts called in order:
     * <ol>
     * <li>Send back a requested resource
     * <li>{@link #startResponse(HttpServletRequest, HttpServletResponse)}</li>
     * <li>{@link #renderTopNavigation(HttpServletRequest, PrintWriter)}</li>
     * <li>{@link #renderContent(HttpServletRequest, HttpServletResponse)}</li>
     * <li>{@link #endResponse(PrintWriter)}</li>
     * </ol>
     * <p>
     * <b>Note</b>: If a resource is sent back for the request only the first
     * step is executed. Otherwise the first step is a null-operation actually
     * and the latter four steps are executed in order.
     * <p>
     * If the {@link #isHtmlRequest(HttpServletRequest)} method returns
     * <code>false</code> only the
     * {@link #renderContent(HttpServletRequest, HttpServletResponse)} method is
     * called.
     */
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response )
    throws ServletException, IOException {
        if ( !spoolResource( request, response ) ) {
            // detect if this is an html request
            if ( isHtmlRequest(request) ) {
                // start the html response, write the header, open body and main div
                PrintWriter pw = startResponse( request, response );

                // render top navigation
                renderTopNavigation( request, pw );

                // wrap content in a separate div
                pw.println( "<div id='content'>" );
                renderContent( request, response );
                pw.println( "</div>" );

                // close the main div, body, and html
                endResponse( pw );
            } else {
                renderContent( request, response );
            }
        }
    }

    /**
     * Detects whether this request is intended to have the headers and
     * footers of this plugin be rendered or not. This method always returns
     * <code>true</code> and may be overwritten by plugins to detect
     * from the actual request, whether or not to render the header and
     * footer.
     *
     * @param request the original request passed from the HTTP server
     * @return <code>true</code> if the page should have headers and footers rendered
     */
    protected boolean isHtmlRequest( final HttpServletRequest request ) {
        return true;
    }
    /**
     * This method is called from the Felix Web Console to ensure the
     * AbstractWebConsolePlugin is correctly setup.
     *
     * It is called right after the Web Console receives notification for
     * plugin registration.
     *
     * @param bundleContext the context of the plugin bundle
     */
    protected void activate( BundleContext bundleContext ) {
        this.bundleContext = bundleContext;
    }

    /**
     * This method is called, by the Web Console to de-activate the plugin and release
     * all used resources.
     */
    public void deactivate() {
        this.bundleContext = null;
    }

    /**
     * Returns the <code>BundleContext</code> with which this plugin has been
     * activated. If the plugin has not be activated by calling the
     * {@link #activate(BundleContext)} method, this method returns
     * <code>null</code>.
     *
     * @return the bundle context or <code>null</code> if the bundle is not activated.
     */
    protected BundleContext getBundleContext() {
        return bundleContext;
    }

    /**
     * Returns the <code>Bundle</code> pertaining to the
     * {@link #getBundleContext() bundle context} with which this plugin has
     * been activated. If the plugin has not be activated by calling the
     * {@link #activate(BundleContext)} method, this method returns
     * <code>null</code>.
     *
     * @return the bundle or <code>null</code> if the plugin is not activated.
     */
    private final Bundle getBundle() {
        final BundleContext bundleContext = getBundleContext();
        return ( bundleContext != null ) ? bundleContext.getBundle() : null;
    }

    /**
     * Calls the <code>ServletContext.log(String)</code> method if the
     * configured log level is less than or equal to the given <code>level</code>.
     * <p>
     * Note, that the <code>level</code> paramter is only used to decide whether
     * the <code>GenericServlet.log(String)</code> method is called or not. The
     * actual implementation of the <code>GenericServlet.log</code> method is
     * outside of the control of this method.
     * <p>
     * If the servlet has not been initialized yet or has already been destroyed
     * the message is printed to stderr.
     *
     * @param level The log level at which to log the message
     * @param message The message to log
     */
    public void log(final int level, final String message ) {
        this.log(level, message, null);
    }

    /**
     * Calls the <code>ServletContext.log(String, Throwable)</code> method if
     * the configured log level is less than or equal to the given
     * <code>level</code>.
     * <p>
     * Note, that the <code>level</code> paramter is only used to decide whether
     * the <code>GenericServlet.log(String, Throwable)</code> method is called
     * or not. The actual implementation of the <code>GenericServlet.log</code>
     * method is outside of the control of this method.
     *
     * @param level The log level at which to log the message
     * @param message The message to log
     * @param t The <code>Throwable</code> to log with the message
     */
    public void log(final int level, final String message, final Throwable t ) {
        if ( LOGLEVEL >= level ) {
            final ServletConfig config = getServletConfig();
            if ( config != null ) {
                final ServletContext context = config.getServletContext();
                if ( context != null ) {
                    if ( t != null ) {
                        context.log( message, t );
                    } else {
                        context.log( message );
                    }
                    return;
                }
            }

            System.err.println( message );
            if ( t != null ) {
                t.printStackTrace( System.err );
            }
        }
    }
    
    /**
     * Spool the resource
     * @throws IOException If an error occurs accessing or spooling the resource.
     */
    private final boolean spoolResource(final HttpServletRequest request,  final HttpServletResponse response)
    throws IOException {
        final String pi = request.getPathInfo();
        final URL url = this.getResource(pi);
        if (url == null) {
            return false;
        }

        // open the connection and the stream (we use the stream to be able
        // to at least hint to close the connection because there is no
        // method to explicitly close the conneciton, unfortunately)
        URLConnection connection = url.openConnection();
        try ( InputStream ins = connection.getInputStream()) {
            // FELIX-2017 Equinox may return an URL for a non-existing
            // resource but then (instead of throwing) return null on
            // getInputStream. We should account for this situation and
            // just assume a non-existing resource in this case.
            if (ins == null) {
                return false;
            }

            // check whether we may return 304/UNMODIFIED
            final long lastModified = connection.getLastModified();
            if ( lastModified > 0 ) {
                long ifModifiedSince = request.getDateHeader( "If-Modified-Since" );
                if ( ifModifiedSince >= ( lastModified / 1000 * 1000 ) ) {
                    // Round down to the nearest second for a proper compare
                    // A ifModifiedSince of -1 will always be less
                    response.setStatus( HttpServletResponse.SC_NOT_MODIFIED );

                    return true;
                }

                // have to send, so set the last modified header now
                response.setDateHeader( "Last-Modified", lastModified );
            }

            // describe the contents
            response.setContentType( getServletContext().getMimeType( pi ) );
            response.setIntHeader( "Content-Length", connection.getContentLength() );

            // spool the actual contents
            final OutputStream out = response.getOutputStream();
            final byte[] buf = new byte[2048];
            int rd;
            while ( ( rd = ins.read( buf ) ) >= 0 ) {
                out.write( buf, 0, rd );
            }

            return true;
        }
    }

    /**
     * This method is responsible for generating the top heading of the page.
     *
     * @param request the HTTP request coming from the user
     * @param response the HTTP response, where data is rendered
     * @return the writer that was used for generating the response.
     * @throws IOException on I/O error
     * @see #endResponse(PrintWriter)
     */
    private PrintWriter startResponse(final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
        response.setCharacterEncoding( "utf-8" );
        response.setContentType( "text/html" );

        final PrintWriter pw = response.getWriter();

        final String appRoot = ( String ) request.getAttribute( ServletConstants.ATTR_APP_ROOT );

        // support localization of the plugin title
        String t= this.title;
        if ( t.startsWith( "%" ) ) {
            t = "${" + t.substring( 1 ) + "}";
        }

        final RequestVariableResolver r = this.getVariableResolver(request);
        r.put("head.title", t);
        r.put("head.label", this.label);
        r.put("head.cssLinks", getCssLinks(appRoot));
        r.put("brand.name", BRANDING_PLUGIN.getBrandName());
        r.put("brand.product.url", BRANDING_PLUGIN.getProductURL());
        r.put("brand.product.name", BRANDING_PLUGIN.getProductName());
        r.put("brand.product.img", toUrl( BRANDING_PLUGIN.getProductImage(), appRoot ));
        r.put("brand.favicon", toUrl( BRANDING_PLUGIN.getFavIcon(), appRoot ));
        r.put("brand.css", toUrl( BRANDING_PLUGIN.getMainStyleSheet(), appRoot ));
        pw.println( HEADER );

        return pw;
    }

    /**
     * This method is called to generate the top level links with the available plug-ins.
     *
     * @param request the HTTP request coming from the user
     * @param pw the writer, where the HTML data is rendered
     */
    @SuppressWarnings({ "rawtypes" })
    private void renderTopNavigation(final HttpServletRequest request, final PrintWriter pw ) {
        // assume pathInfo to not be null, else this would not be called
        String current = request.getPathInfo();
        int slash = current.indexOf( "/", 1 );
        if ( slash < 0 ) {
            slash = current.length();
        }
        current = current.substring( 1, slash );

        final String appRoot = ( String ) request.getAttribute( ServletConstants.ATTR_APP_ROOT );

        @SuppressWarnings("deprecation")
        final Map menuMap = ( Map ) request.getAttribute( OsgiManager.ATTR_LABEL_MAP_CATEGORIZED );
        this.renderMenu( menuMap, appRoot, pw );

        // render lang-box
        Map langMap = (Map) request.getAttribute(ATTR_LANG_MAP);
        if (null != langMap && !langMap.isEmpty()) {
            // determine the currently selected locale from the request and fail-back
            // to the default locale if not set
            // if locale is missing in locale map, the default 'en' locale is used
            Locale reqLocale = request.getLocale();
            String locale = null != reqLocale ? reqLocale.getLanguage()
                : Locale.getDefault().getLanguage();
            if (!langMap.containsKey(locale)) {
                locale = Locale.getDefault().getLanguage();
            }
            if (!langMap.containsKey(locale)) {
                locale = "en";
            }

            pw.println("<div id='langSelect'>");
            pw.println(" <span>");
            printLocaleElement(pw, appRoot, locale, langMap.get(locale));
            pw.println(" </span>");
            pw.println(" <span class='flags ui-helper-hidden'>");
            for (Iterator li = langMap.keySet().iterator(); li.hasNext();) {
                // <img src="us.gif" alt="en" title="English"/>
                final Object l = li.next();
                if (!l.equals(locale)) {
                    printLocaleElement(pw, appRoot, l, langMap.get(l));
                }
            }

            pw.println(" </span>");
            pw.println("</div>");
        }
    }

    @SuppressWarnings({ "rawtypes" })
    private void renderMenu(final Map menuMap, final String appRoot, final PrintWriter pw ) {
        if ( menuMap != null ) {
            final SortedMap categoryMap = sortMenuCategoryMap( menuMap, appRoot );
            pw.println( "<ul id=\"navmenu\">" );
            renderSubmenu( categoryMap, appRoot, pw, 0 );
            pw.println("<li class=\"logoutButton navMenuItem-0\">");
            pw.println("<a href=\"" + appRoot + "/logout\">${logout}</a>");
            pw.println("</li>");
            pw.println( "</ul>" );
        }
    }

    @SuppressWarnings({ "rawtypes" })
    private void renderMenu(final Map menuMap, final String appRoot, final PrintWriter pw, final int level ) {
        pw.println( "<ul class=\"navMenuLevel-" + level + "\">" );
        renderSubmenu( menuMap, appRoot, pw, level );
        pw.println( "</ul>" );
    }

    @SuppressWarnings({ "rawtypes" })
    private void renderSubmenu(final Map menuMap, final String appRoot, final PrintWriter pw, final int level ) {
        String liStyleClass = " class=\"navMenuItem-" + level + "\"";
        Iterator itr = menuMap.keySet().iterator();
        while ( itr.hasNext() )
        {
            String key = ( String ) itr.next();
            MenuItem menuItem = ( MenuItem ) menuMap.get( key );
            pw.println( "<li" + liStyleClass + ">" + menuItem.getLink() );
            Map subMenu = menuItem.getSubMenu();
            if ( subMenu != null )
            {
                renderMenu( subMenu, appRoot, pw, level + 1 );
            }
            pw.println( "</li>" );
        }
    }

    private static final void printLocaleElement( PrintWriter pw, String appRoot, Object langCode, Object langName ) {
        pw.print("  <img src='");
        pw.print(appRoot);
        pw.print("/res/flags/");
        pw.print(langCode);
        pw.print(".gif' alt='");
        pw.print(langCode);
        pw.print("' title='");
        pw.print(langName);
        pw.println("'/>");
    }

    /**
     * This method is responsible for generating the footer of the page.
     *
     * @param pw the writer, where the HTML data is rendered
     * @see #startResponse(HttpServletRequest, HttpServletResponse)
     */
    private void endResponse( PrintWriter pw ) {
        pw.println(FOOTER);
    }

    /**
     * Sets the {@link BrandingPlugin} to use globally by all extensions of
     * this class for branding.
     * <p>
     * Note: This method is intended to be used internally by the Web Console
     * to update the branding plugin to use.
     *
     * @param brandingPlugin the brandingPlugin to set
     */
    public static final void setBrandingPlugin(final BrandingPlugin brandingPlugin) {
        if (brandingPlugin == null){
            AbstractPluginAdapter.BRANDING_PLUGIN = new BrandingPluginImpl();
        } else {
            AbstractPluginAdapter.BRANDING_PLUGIN = brandingPlugin;
        }
    }

    /**
     * Sets the log level to be applied for calls to the {@link #log(int, String)}
     * and {@link #log(int, String, Throwable)} methods.
     * <p>
     * Note: This method is intended to be used internally by the Web Console
     * to update the log level according to the Web Console configuration.
     *
     * @param logLevel the maximum allowed log level. If message is logged with
     *        lower level it will not be forwarded to the logger.
     */
    public static final void setLogLevel( final int logLevel ) {
        AbstractPluginAdapter.LOGLEVEL = logLevel;
    }

    private static final String readTemplateFile( final String templateFile) {
        try(final InputStream templateStream = AbstractPluginAdapter.class.getResourceAsStream( templateFile )) {
            if ( templateStream != null ) {
                try ( final StringWriter w = new StringWriter()) {
                    final byte[] buf = new byte[2048];
                    int l;
                    while ( ( l = templateStream.read(buf)) > 0 ) {
                        w.write(new String(buf, 0, l, StandardCharsets.UTF_8));
                    }
                    String str = w.toString();
                    switch ( str.charAt(0) )
                    { // skip BOM
                        case 0xFEFF: // UTF-16/UTF-32, big-endian
                        case 0xFFFE: // UTF-16, little-endian
                        case 0xEFBB: // UTF-8
                            return str.substring(1);
                    }
                    return str;
                }
            }
        } catch (final IOException e ) {
            // don't use new Exception(message, cause) because cause is 1.4+
            throw new RuntimeException( "readTemplateFile: Error loading " + templateFile + ": " + e );
        }

        // template file does not exist, throw
        throw new RuntimeException("readTemplateFile: File '" + templateFile + "' not found in webconsole bundle");
    }

    private final String getCssLinks( final String appRoot ) {
        // get the CSS references and return nothing if there are none
        if ( this.cssReferences == null || this.cssReferences.length == 0) {
            return "";
        }

        // build the CSS links from the references
        final StringBuilder buf = new StringBuilder();
        for ( int i = 0; i < this.cssReferences.length; i++ ) {
            buf.append( "<link href='" );
            buf.append( toUrl( this.cssReferences[i], appRoot ) );
            buf.append( "' rel='stylesheet' type='text/css' />" );
        }

        return buf.toString();
    }

    /**
     * If the <code>url</code> starts with a slash, it is considered an absolute
     * path (relative URL) which must be prefixed with the Web Console
     * application root path. Otherwise the <code>url</code> is assumed to
     * either be a relative path or an absolute URL, both must not be prefixed.
     *
     * @param url The url path to optionally prefix with the application root
     *          path
     * @param appRoot The application root path to optionally put in front of
     *          the url.
     * @throws NullPointerException if <code>url</code> is <code>null</code>.
     */
    private static final String toUrl( final String url, final String appRoot ) {
        if ( url.startsWith( "/" ) ) {
            return appRoot + url;
        }
        return url;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private SortedMap sortMenuCategoryMap(final Map map, final String appRoot ) {
        final SortedMap sortedMap = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
        final Iterator keys = map.keySet().iterator();
        while ( keys.hasNext() ) {
            final String key = ( String ) keys.next();
            if ( key.startsWith( "category." ) ) {
                final SortedMap categoryMap = sortMenuCategoryMap( ( Map ) map.get( key ), appRoot );
                final String title = key.substring( key.indexOf( '.' ) + 1 );
                if ( sortedMap.containsKey( title ) ) {
                    ( ( MenuItem ) sortedMap.get( title ) ).setSubMenu( categoryMap );
                } else {
                    final String link = "<a href=\"#\">" + title + "</a>";
                    final MenuItem menuItem = new MenuItem( link, categoryMap );
                    sortedMap.put( title, menuItem );
                }
            } else {
                final String title = ( String ) map.get( key );
                final String link = "<a href=\"" + appRoot + "/" + key + "\">" + title + "</a>";
                if ( sortedMap.containsKey( title ) ) {
                    ( ( MenuItem ) sortedMap.get( title ) ).setLink( link );
                } else {
                    final MenuItem menuItem = new MenuItem( link );
                    sortedMap.put( title, menuItem );
                }
            }
        }
        return sortedMap;
    }

    /**
     * Get the variable resolver
     * @param request The request
     * @return The resolver
     */
    protected RequestVariableResolver getVariableResolver( final ServletRequest request) {
        return (RequestVariableResolver) request.getAttribute( RequestVariableResolver.REQUEST_ATTRIBUTE );
    }

    @SuppressWarnings({ "rawtypes" })
    private static class MenuItem {

        private String link;
        private Map subMenu;

        public MenuItem(final String link ) {
            this.link = link;
        }

        public MenuItem(final String link, final Map subMenu ) {
            this.link = link;
            this.subMenu = subMenu;
        }

        public String getLink() {
            return link;
        }


        public void setLink(final String link ) {
            this.link = link;
        }


        public Map getSubMenu() {
            return subMenu;
        }


        public void setSubMenu(final Map subMenu ) {
            this.subMenu = subMenu;
        }
    }
}

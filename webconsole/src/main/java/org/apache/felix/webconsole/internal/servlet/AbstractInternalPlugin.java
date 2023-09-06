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
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.felix.webconsole.DefaultBrandingPlugin;
import org.apache.felix.webconsole.SimpleWebConsolePlugin;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.felix.webconsole.WebConsoleUtil;
import org.apache.felix.webconsole.i18n.LocalizationHelper;
import org.apache.felix.webconsole.internal.servlet.OsgiManager;
import org.apache.felix.webconsole.servlet.RequestVariableResolver;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.apache.felix.webconsole.spi.BrandingPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This is an internal base class for web console plugins
 */
public abstract class AbstractInternalPlugin extends HttpServlet {

    /**
     * The name of the request attribute providing a mapping of labels to page
     * titles of registered console plugins (value is "felix.webconsole.labelMap").
     * This map may be used to render a navigation of the console plugins as the
     * {@link AbstractWebConsolePlugin#renderTopNavigation(javax.servlet.http.HttpServletRequest, java.io.PrintWriter)}
     * method does.
     * <p>
     * The type of this request attribute is <code>Map&lt;String, String&gt;</code>.
     */
    public static final String ATTR_LABEL_MAP = "felix.webconsole.labelMap";

    /**
     * The header fragment read from the templates/main_header.html file
     */
    private static String HEADER;

    /**
     * The footer fragment read from the templates/main_footer.html file
     */
    private static String FOOTER;

    private static volatile BrandingPlugin BRANDING_PLUGIN = DefaultBrandingPlugin.getInstance();

    private static volatile int LOGLEVEL;

    private final String css[];

    private final String labelRes;
    private final int labelResLen;

    // localized title as servlet name
    private volatile String servletName;

    private volatile BundleContext bundleContext;

    // used for service registration
    private final Object regLock = new Object();
    private volatile ServiceRegistration<Servlet> reg;

    // used to obtain services. Structure is: service name -> ServiceTracker
    private final Map<String, ServiceTracker<?, ?>> services = new HashMap<>();

    /**
     * Creates new plugin
     *
     * @param label the front label
     * @param title the plugin title
     * @param category the plugin's navigation category (optional)
     * @param css the additional plugin CSS (optional)
     * @throws NullPointerException if either <code>label</code> or <code>title</code> is <code>null</code>
     */
    public AbstractInternalPlugin( final String label, final String css[] ) {
        if ( label == null ) {
            throw new NullPointerException( "Null label" );
        }
        this.css = css;
        this.labelRes = '/' + label + '/';
        this.labelResLen = labelRes.length() - 1;
    }

    public void activate(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        if (this.title.startsWith( "%" ) ) {
            // FELIX-6341 - dig out the localized title for use in log messages
            final Bundle bundle = bundleContext.getBundle();
            if (bundle != null) {
                final LocalizationHelper localization = new LocalizationHelper( bundle );
                final ResourceBundle rb = localization.getResourceBundle(Locale.getDefault());
                if (rb != null) {
                    final String key = this.title.substring(1);
                    if (rb.containsKey(key)) {
                        this.servletName = rb.getString(key);
                    }
                }
            }
        }
    }

    @Override
    public String getServletName() {
        // use the localized title if we have one
        if (this.servletName != null) {
            return this.servletName;
        }
        return this.getTitle();
    }

    public final String getLabel() {
        return label;
    }

    public final String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    /**
     * Called internally to load resources.
     *
     * This particular implementation depends on the label. As example, if the
     * plugin is accessed as <code>/system/console/abc</code>, and the plugin
     * resources are accessed like <code>/system/console/abc/res/logo.gif</code>,
     * the code here will try load resource <code>/res/logo.gif</code> from the
     * bundle, providing the plugin.
     *
     *
     * @param path the path to read.
     * @return the URL of the resource or <code>null</code> if not found.
     */
    protected URL getResource( String path ) {
        return ( path != null && path.startsWith( labelRes ) ) ? //
            getClass().getResource( path.substring( labelResLen ) )
            : null;
    }

    /**
     * This is an utility method. It is used to register the plugin service. Don't
     * forget to call the {@link #unregister()} when the plugin is no longer
     * needed.
     *
     * @param bc the bundle context used for service registration.
     * @return self
     */
    public final AbstractInternalPlugin register() {
        synchronized ( regLock ) {
            final Dictionary<String, Object> props = new Hashtable<>();
            props.put( ServletConstants.PLUGIN_LABEL, getLabel() );
            props.put( ServletConstants.PLUGIN_TITLE, getTitle() );
            if ( getCategory() != null ) {
                props.put( ServletConstants.PLUGIN_CATEGORY, getCategory() );
            }
            if ( this.css != null && this.css.length > 0 ) {
                props.put( ServletConstants.PLUGIN_CSS_REFERENCES, this.css );
            }
            reg = this.bundleContext.registerService( Servlet.class, this, props );
        }
        return this;
    }

    /**
     * An utility method that removes the service, registered by the
     * {@link #register()} method.
     */
    public final void unregister() {
        synchronized ( regLock ) {
            if ( reg != null ) {
                try {
                    reg.unregister();
                } catch ( final IllegalStateException ise ) {
                    // ignore, bundle context already invalid
                }
            }
            reg = null;
        }
    }

    // -- begin methods for obtaining services

    /**
     * Gets the service with the specified class name. Will create a new
     * {@link ServiceTracker} if the service is not already got.
     *
     * @param serviceName the service name to obtain
     * @return the service or <code>null</code> if missing.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public final Object getService( String serviceName ) {
        ServiceTracker<?,?> serviceTracker = services.get( serviceName );
        if ( serviceTracker == null ) {
            serviceTracker = new ServiceTracker( getBundleContext(), serviceName, new ServiceTrackerCustomizer() {
                    public Object addingService( ServiceReference reference ) {
                        return getBundleContext().getService( reference );
                    }

                    public void removedService( ServiceReference reference, Object service ) {
                        try {
                            getBundleContext().ungetService( reference );
                        } catch ( IllegalStateException ise ) {
                            // ignore, bundle context was shut down
                        }
                    }

                    public void modifiedService( ServiceReference reference, Object service ) {
                        // nothing to do
                    }
            } );
            serviceTracker.open();

            services.put( serviceName, serviceTracker );
        }

        return serviceTracker.getService();
    }


    /**
     * This method will close all service trackers, created by
     * {@link #getService(String)} method. If you override this method, don't
     * forget to call the super.
     */
    public void deactivate() {
        for ( Iterator<ServiceTracker<?, ?>> ti = services.values().iterator(); ti.hasNext(); ) {
            ServiceTracker<?, ?> tracker = ti.next();
            tracker.close();
            ti.remove();
        }
        this.bundleContext = null;
    }

    //---------- HttpServlet Overwrites ----------------------------------------

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
     *
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest,
     *      javax.servlet.http.HttpServletResponse)
     */
    protected void doGet( HttpServletRequest request, HttpServletResponse response )
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
    protected abstract void renderContent( HttpServletRequest req, HttpServletResponse res )
    throws ServletException, IOException;

    /**
     * Returns the <code>BundleContext</code> with which this plugin has been
     * activated. If the plugin has not be activated by calling the
     * {@link #activate(BundleContext)} method, this method returns
     * <code>null</code>.
     *
     * @return the bundle context or <code>null</code> if the bundle is not activated.
     */
    protected BundleContext getBundleContext()  {
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
    protected final Bundle getBundle() {
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
    public void log( int level, String message ) {
        if ( LOGLEVEL >= level ) {
            ServletConfig config = getServletConfig();
            if ( config != null ) {
                ServletContext context = config.getServletContext();
                if ( context != null ) {
                    context.log( message );
                    return;
                }
            }

            System.err.println( message );
        }
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
    public void log( int level, String message, Throwable t ) {
        if ( LOGLEVEL >= level ) {
            ServletConfig config = getServletConfig();
            if ( config != null ) {
                ServletContext context = config.getServletContext();
                if ( context != null ) {
                    context.log( message, t );
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
     * If the request addresses a resource which may be served by the
     * <code>getResource</code> method of the
     * {@link #getResourceProvider() resource provider}, this method serves it
     * and returns <code>true</code>. Otherwise <code>false</code> is returned.
     * <code>false</code> is also returned if the resource provider has no
     * <code>getResource</code> method.
     * <p>
     * If <code>true</code> is returned, the request is considered complete and
     * request processing terminates. Otherwise request processing continues
     * with normal plugin rendering.
     *
     * @param request The request object
     * @param response The response object
     * @return <code>true</code> if the request causes a resource to be sent back.
     *
     * @throws IOException If an error occurs accessing or spooling the resource.
     */
    private boolean spoolResource(final HttpServletRequest request, 
        final HttpServletResponse response) throws IOException {
        try {
            // We need to call spoolResource0 in privileged block because it uses reflection, which
            // requires the following set of permissions:
            // (java.lang.RuntimePermission "getClassLoader")
            // (java.lang.RuntimePermission "accessDeclaredMembers")
            // (java.lang.reflect.ReflectPermission "suppressAccessChecks")
            // See also https://issues.apache.org/jira/browse/FELIX-4652
            final Boolean ret = AccessController.doPrivileged(new PrivilegedExceptionAction<Boolean>() {

                public Boolean run() throws Exception {
                    return spoolResource0(request, response) ? Boolean.TRUE : Boolean.FALSE;
                }
            });
            return ret.booleanValue();
        } catch (final PrivilegedActionException e) {
            final Exception x = e.getException();
            throw x instanceof IOException ? (IOException) x : new IOException(x.toString());
        }
    }

    private boolean spoolResource0(final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
        final URL url = getResource(request.getPathInfo());
        if ( url == null ) {
            return false;
        }
        // open the connection and the stream (we use the stream to be able
        // to at least hint to close the connection because there is no
        // method to explicitly close the conneciton, unfortunately)
        final URLConnection connection = url.openConnection();
        try ( InputStream ins = connection.getInputStream()) {
            // FELIX-2017 Equinox may return an URL for a non-existing
            // resource but then (instead of throwing) return null on
            // getInputStream. We should account for this situation and
            // just assume a non-existing resource in this case.
            if (ins == null) {
                return false;
            }

            // check whether we may return 304/UNMODIFIED
            long lastModified = connection.getLastModified();
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
            response.setContentType( getServletContext().getMimeType( request.getPathInfo() ) );
            response.setIntHeader( "Content-Length", connection.getContentLength() );

            // spool the actual contents
            OutputStream out = response.getOutputStream();
            byte[] buf = new byte[2048];
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
    private PrintWriter startResponse( HttpServletRequest request, HttpServletResponse response ) throws IOException {
        response.setCharacterEncoding( "utf-8" );
        response.setContentType( "text/html" );

        final PrintWriter pw = response.getWriter();

        final String appRoot = ( String ) request.getAttribute( ServletConstants.ATTR_APP_ROOT );

        // support localization of the plugin title
        String title = getTitle();
        if ( title.startsWith( "%" ) ) {
            title = "${" + title.substring( 1 ) + "}";
        }

        final RequestVariableResolver r = this.getVariableResolver(request);
        r.put("head.title", title);
        r.put("head.label", getLabel());
        r.put("head.cssLinks", getCssLinks(appRoot));
        r.put("brand.name", BRANDING_PLUGIN.getBrandName());
        r.put("brand.product.url", BRANDING_PLUGIN.getProductURL());
        r.put("brand.product.name", BRANDING_PLUGIN.getProductName());
        r.put("brand.product.img", toUrl( BRANDING_PLUGIN.getProductImage(), appRoot ));
        r.put("brand.favicon", toUrl( BRANDING_PLUGIN.getFavIcon(), appRoot ));
        r.put("brand.css", toUrl( BRANDING_PLUGIN.getMainStyleSheet(), appRoot ));
        pw.println( getHeader() );

        return pw;
    }


    /**
     * This method is called to generate the top level links with the available plug-ins.
     *
     * @param request the HTTP request coming from the user
     * @param pw the writer, where the HTML data is rendered
     */
    @SuppressWarnings({ "rawtypes" })
    private void renderTopNavigation( HttpServletRequest request, PrintWriter pw ) {
        // assume pathInfo to not be null, else this would not be called
        String current = request.getPathInfo();
        int slash = current.indexOf( "/", 1 );
        if ( slash < 0 ) {
            slash = current.length();
        }
        current = current.substring( 1, slash );

        String appRoot = ( String ) request.getAttribute( ServletConstants.ATTR_APP_ROOT );

        Map menuMap = ( Map ) request.getAttribute( OsgiManager.ATTR_LABEL_MAP_CATEGORIZED );
        this.renderMenu( menuMap, appRoot, pw );

        // render lang-box
        Map langMap = (Map) request.getAttribute(WebConsoleConstants.ATTR_LANG_MAP);
        if (null != langMap && !langMap.isEmpty())
        {
            // determine the currently selected locale from the request and fail-back
            // to the default locale if not set
            // if locale is missing in locale map, the default 'en' locale is used
            Locale reqLocale = request.getLocale();
            String locale = null != reqLocale ? reqLocale.getLanguage()
                : Locale.getDefault().getLanguage();
            if (!langMap.containsKey(locale))
            {
                locale = Locale.getDefault().getLanguage();
            }
            if (!langMap.containsKey(locale))
            {
                locale = "en";
            }

            pw.println("<div id='langSelect'>");
            pw.println(" <span>");
            printLocaleElement(pw, appRoot, locale, langMap.get(locale));
            pw.println(" </span>");
            pw.println(" <span class='flags ui-helper-hidden'>");
            for (Iterator li = langMap.keySet().iterator(); li.hasNext();)
            {
                // <img src="us.gif" alt="en" title="English"/>
                final Object l = li.next();
                if (!l.equals(locale))
                {
                    printLocaleElement(pw, appRoot, l, langMap.get(l));
                }
            }

            pw.println(" </span>");
            pw.println("</div>");
        }
    }


    @SuppressWarnings({ "rawtypes" })
    protected void renderMenu( Map menuMap, String appRoot, PrintWriter pw )
    {
        if ( menuMap != null )
        {
            SortedMap categoryMap = sortMenuCategoryMap( menuMap, appRoot );
            pw.println( "<ul id=\"navmenu\">" );
            renderSubmenu( categoryMap, appRoot, pw, 0 );
            pw.println("<li class=\"logoutButton navMenuItem-0\">");
            pw.println("<a href=\"" + appRoot + "/logout\">${logout}</a>");
            pw.println("</li>");
            pw.println( "</ul>" );
        }
    }


    @SuppressWarnings({ "rawtypes" })
    private void renderMenu( Map menuMap, String appRoot, PrintWriter pw, int level )
    {
        pw.println( "<ul class=\"navMenuLevel-" + level + "\">" );
        renderSubmenu( menuMap, appRoot, pw, level );
        pw.println( "</ul>" );
    }


    @SuppressWarnings({ "rawtypes" })
    private void renderSubmenu( Map menuMap, String appRoot, PrintWriter pw, int level )
    {
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


    private static final void printLocaleElement( PrintWriter pw, String appRoot, Object langCode, Object langName )
    {
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
    protected void endResponse( PrintWriter pw )
    {
        pw.println(getFooter());
    }

    /**
     * Sets the {@link BrandingPlugin} to use globally by all extensions of
     * this class for branding.
     * <p>
     * Note: This method is intended to be used internally by the Web Console
     * to update the branding plugin to use.
     *
     * @param brandingPlugin the brandingPlugin to set
     * @deprecated
     */
    @Deprecated
    public static final void setBrandingPlugin(final BrandingPlugin brandingPlugin) {
        if (brandingPlugin == null){
            AbstractInternalPlugin.BRANDING_PLUGIN = DefaultBrandingPlugin.getInstance();
        } else {
            AbstractInternalPlugin.BRANDING_PLUGIN = brandingPlugin;
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
        AbstractInternalPlugin.LOGLEVEL = logLevel;
    }

    private final String getHeader() {
        // MessageFormat pattern place holder
        //  0 main title (brand name)
        //  1 console plugin title
        //  2 application root path (ATTR_APP_ROOT)
        //  3 console plugin label (from the URI)
        //  4 branding favourite icon (BrandingPlugin.getFavIcon())
        //  5 branding main style sheet (BrandingPlugin.getMainStyleSheet())
        //  6 branding product URL (BrandingPlugin.getProductURL())
        //  7 branding product name (BrandingPlugin.getProductName())
        //  8 branding product image (BrandingPlugin.getProductImage())
        //  9 additional HTML code to be inserted into the <head> section
        //    (for example plugin provided CSS links)
        if ( HEADER == null )
        {
            HEADER = readTemplateFile( AbstractInternalPlugin.class, "/templates/main_header.html" );
        }
        return HEADER;
    }


    private final String getFooter()
    {
        if ( FOOTER == null )
        {
            FOOTER = readTemplateFile( AbstractInternalPlugin.class, "/templates/main_footer.html" );
        }
        return FOOTER;
    }

    /**
     * Reads the <code>templateFile</code> as a resource through the class
     * loader of this class converting the binary data into a string using
     * UTF-8 encoding.
     * <p>
     * If the template file cannot read into a string and an exception is
     * caused, the exception is logged and an empty string returned.
     *
     * @param templateFile The absolute path to the template file to read.
     * @return The contents of the template file as a string or and empty
     *      string if the template file fails to be read.
     *
     * @throws NullPointerException if <code>templateFile</code> is
     *      <code>null</code>
     * @throws RuntimeException if an <code>IOException</code> is thrown reading
     *      the template file into a string. The exception provides the
     *      exception thrown as its cause.
     */
    protected final String readTemplateFile( final String templateFile ) {
        return readTemplateFile( getClass(), templateFile );
    }

    /**
     * Retrieves a request parameter and converts it to int.
     *
     * @param request the HTTP request
     * @param name the name of the request parameter
     * @param defaultValue the default value returned if the parameter is not set or is not a valid integer.
     * @return the request parameter if set and is valid integer, or the default value
     */
    protected static final int getParameterInt(final HttpServletRequest request, final String name, final int defaultValue) {
        int ret = defaultValue;
        final String param = request.getParameter(name);
        if (param != null) {
            try {
                ret = Integer.parseInt(param);
            } catch (final NumberFormatException nfe) {
            // don't care, will return default
            }
        }
        return ret;
    }

    protected void sendJsonOk(final HttpServletResponse response) throws IOException {
        response.setContentType( "application/json" );
        response.setCharacterEncoding( "UTF-8" );
        response.getWriter().print( "{ \"status\": true }" );
    }

    private final String readTemplateFile( final Class<?> clazz, final String templateFile) {
        
        try(InputStream templateStream = clazz.getResourceAsStream( templateFile )) {
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
        }
        catch ( IOException e )
        {
            // don't use new Exception(message, cause) because cause is 1.4+
            throw new RuntimeException( "readTemplateFile: Error loading " + templateFile + ": " + e );
        }

        // template file does not exist, return an empty string
        log( LogService.LOG_ERROR, "readTemplateFile: File '" + templateFile + "' not found through class " + clazz );
        return "";
    }

    private final String getCssLinks( final String appRoot ) {
        if ( css == null || css.length == 0) {
            return "";
        }

        // build the CSS links from the references
        final StringBuilder buf = new StringBuilder();
        for(final String ref : css) {
            buf.append( "<link href='" );
            buf.append( toUrl( ref, appRoot ) );
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
        if ( url.startsWith( "/" ) )
        {
            return appRoot.concat(url);
        }
        return url;
    }


    @SuppressWarnings({ "unchecked", "rawtypes" })
    private SortedMap sortMenuCategoryMap( Map map, String appRoot )
    {
        SortedMap sortedMap = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
        Iterator keys = map.keySet().iterator();
        while ( keys.hasNext() )
        {
            String key = ( String ) keys.next();
            if ( key.startsWith( "category." ) )
            {
                SortedMap categoryMap = sortMenuCategoryMap( ( Map ) map.get( key ), appRoot );
                String title = key.substring( key.indexOf( '.' ) + 1 );
                if ( sortedMap.containsKey( title ) )
                {
                    ( ( MenuItem ) sortedMap.get( title ) ).setSubMenu( categoryMap );
                }
                else
                {
                    String link = "<a href=\"#\">" + title + "</a>";
                    MenuItem menuItem = new MenuItem( link, categoryMap );
                    sortedMap.put( title, menuItem );
                }
            }
            else
            {
                String title = ( String ) map.get( key );
                String link = "<a href=\"" + appRoot + "/" + key + "\">" + title + "</a>";
                if ( sortedMap.containsKey( title ) )
                {
                    ( ( MenuItem ) sortedMap.get( title ) ).setLink( link );
                }
                else
                {
                    MenuItem menuItem = new MenuItem( link );
                    sortedMap.put( title, menuItem );
                }
            }

        }
        return sortedMap;
    }

     /**
     * Returns the {@link RequestVariableResolver} for the given request.
     *
     * @param request The request whose attribute is returned 
     */
    protected RequestVariableResolver getVariableResolver( final ServletRequest request) {
        return (RequestVariableResolver) request.getAttribute( RequestVariableResolver.REQUEST_ATTRIBUTE );
    }

 
    @SuppressWarnings({ "rawtypes" })
    private static class MenuItem
    {
    private String link;
        private Map subMenu;

        public MenuItem( String link )
        {
            this.link = link;
        }

        public MenuItem( String link, Map subMenu )
        {
            super();
            this.link = link;
            this.subMenu = subMenu;
        }


        public String getLink()
        {
            return link;
        }


        public void setLink( String link )
        {
            this.link = link;
        }


        public Map getSubMenu()
        {
            return subMenu;
        }


        public void setSubMenu( Map subMenu )
        {
            this.subMenu = subMenu;
        }
    }
}

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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.webconsole.internal.NavigationRenderer;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.servlet.AbstractOsgiManagerPlugin;
import org.apache.felix.webconsole.servlet.RequestVariableResolver;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;


/**
 * The Web Console can be extended by registering an OSGi service for the interface
 * {@link javax.servlet.Servlet} with the service property
 * <code>felix.webconsole.label</code> set to the label (last segment in the URL)
 * of the page. The respective service is called a Web Console Plugin or a plugin
 * for short.
 *
 * To help rendering the response the Apache Felix Web Console bundle provides two
 * options. One of the options is to extend the AbstractWebConsolePlugin overwriting
 * the {@link #renderContent(HttpServletRequest, HttpServletResponse)} method.
 *
 * @deprecated Either register a servlet using Servlet API 5 or use {@link org.apache.felix.webconsole.servlet.AbstractServlet}
 */
@Deprecated
public abstract class AbstractWebConsolePlugin extends HttpServlet {

    /** Pseudo class version ID to keep the IDE quite. */
    private static final long serialVersionUID = 1L;

    /**
     * This attribute is not supported anymore
     * @deprecated Use the Servlet API for uploads
     */
    @Deprecated
    public static final String ATTR_FILEUPLOAD = "org.apache.felix.webconsole.fileupload";

    /**
     * This attribute is not supported anymore
     * @deprecated Use the Servlet API for uploads
     */
    @Deprecated
    public static final String ATTR_FILEUPLOAD_REPO = "org.apache.felix.webconsole.fileupload.repo";

    /**
     * Web Console Plugin typically consists of servlet and resources such as images,
     * scripts or style sheets.
     *
     * To load resources, a Resource Provider is used. The resource provider is an object,
     * that provides a method which name is specified by this constants and it is
     * 'getResource'.
     *
     *  @see #getResourceProvider()
     */
    public static final String GET_RESOURCE_METHOD_NAME = "getResource";

    /**
     * The reference to the getResource method provided by the
     * {@link #getResourceProvider()}. This is <code>null</code> if there is
     * none or before the first check if there is one.
     *
     * @see #getGetResourceMethod()
     */
    private Method getResourceMethod;

    /**
     * flag indicating whether the getResource method has already been looked
     * up or not. This prevens the {@link #getGetResourceMethod()} method from
     * repeatedly looking up the resource method on plugins which do not have
     * one.
     */
    private boolean getResourceMethodChecked;

    private BundleContext bundleContext;

    private static volatile BrandingPlugin BRANDING_PLUGIN = DefaultBrandingPlugin.getInstance();


    //---------- HttpServlet Overwrites ----------------------------------------

    /**
     * Returns the title for this plugin as returned by {@link #getTitle()}
     *
     * @see javax.servlet.GenericServlet#getServletName()
     */
    public String getServletName()
    {
        return getTitle();
    }


    /**
     * This method should return category string which will be used to render
     * the plugin in the navigation menu. Default implementation returns null,
     * which will result in the plugin link rendered as top level menu item.
     * Concrete implementations wishing to be rendered as a sub-menu item under
     * a category should override this method and return a string or define
     * <code>felix.webconsole.category</code> OSGi property. Currently only
     * single level categories are supported. So, this should be a simple
     * String.
     *
     * @return category
     */
    public String getCategory()
    {
        return null;
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


    //---------- AbstractWebConsolePlugin API ----------------------------------

    /**
     * This method is called from the Felix Web Console to ensure the
     * AbstractWebConsolePlugin is correctly setup.
     *
     * It is called right after the Web Console receives notification for
     * plugin registration.
     *
     * @param bundleContext the context of the plugin bundle
     */
    public void activate( BundleContext bundleContext )
    {
        this.bundleContext = bundleContext;
    }


    /**
     * This method is called, by the Web Console to de-activate the plugin and release
     * all used resources.
     */
    public void deactivate()
    {
        this.bundleContext = null;
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
    protected abstract void renderContent( HttpServletRequest req, HttpServletResponse res ) throws ServletException,
        IOException;

    /**
     * Retrieves the label. This is the last component in the servlet path.
     *
     * This method MUST be overridden, if the {@link #AbstractWebConsolePlugin()}
     * constructor is used.
     *
     * @return the label.
     */
    public abstract String getLabel();

    /**
     * Retrieves the title of the plug-in. It is displayed in the page header
     * and is also included in the title of the HTML document.
     *
     * This method MUST be overridden, if the {@link #AbstractWebConsolePlugin()}
     * constructor is used.
     *
     * @return the plugin title.
     */
    public abstract String getTitle();

    /**
     * Returns a list of CSS reference paths or <code>null</code> if no
     * additional CSS files are provided by the plugin.
     * <p>
     * The result is an array of strings which are used as the value of
     * the <code>href</code> attribute of the <code>&lt;link&gt;</code> elements
     * placed in the head section of the HTML generated. If the reference is
     * a relative path, it is turned into an absolute path by prepending the
     * value of the {@link WebConsoleConstants#ATTR_APP_ROOT} request attribute.
     *
     * @return The list of additional CSS files to reference in the head
     *      section or <code>null</code> if no such CSS files are required.
     */
    protected String[] getCssReferences()
    {
        return null;
    }

    /**
     * Returns the <code>BundleContext</code> with which this plugin has been
     * activated. If the plugin has not be activated by calling the
     * {@link #activate(BundleContext)} method, this method returns
     * <code>null</code>.
     *
     * @return the bundle context or <code>null</code> if the bundle is not activated.
     */
    protected BundleContext getBundleContext()
    {
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
    public final Bundle getBundle()
    {
        final BundleContext bundleContext = getBundleContext();
        return ( bundleContext != null ) ? bundleContext.getBundle() : null;
    }

    /**
     * Returns the object which might provide resources. The class of this
     * object is used to find the <code>getResource</code> method.
     * <p>
     * This method may be overwritten by extensions. This base class
     * implementation returns this instance.
     *
     * @return The resource provider object or <code>null</code> if no
     *      resources will be provided by this plugin.
     */
    protected Object getResourceProvider()
    {
        return this;
    }


    /**
     * Returns a method which is called on the
     * {@link #getResourceProvider() resource provider} class to return an URL
     * to a resource which may be spooled when requested. The method has the
     * following signature:
     * <pre>
     * [modifier] URL getResource(String path);
     * </pre>
     * Where the <i>[modifier]</i> may be <code>public</code>, <code>protected</code>
     * or <code>private</code> (if the method is declared in the class of the
     * resource provider). It is suggested to use the <code>private</code>
     * modifier if the method is declared in the resource provider class or
     * the <code>protected</code> modifier if the method is declared in a
     * base class of the resource provider.
     *
     * @return The <code>getResource(String)</code> method or <code>null</code>
     *      if the {@link #getResourceProvider() resource provider} is
     *      <code>null</code> or does not provide such a method.
     */
    private final Method getGetResourceMethod()
    {
        // return what we know of the getResourceMethod, if we already checked
        if (getResourceMethodChecked) {
            return getResourceMethod;
        }

        Method tmpGetResourceMethod = null;
        Object resourceProvider = getResourceProvider();
        if ( resourceProvider != null )
        {
            try
            {
                Class<?> cl = resourceProvider.getClass();
                while ( tmpGetResourceMethod == null && cl != Object.class )
                {
                    Method[] methods = cl.getDeclaredMethods();
                    for ( int i = 0; i < methods.length; i++ )
                    {
                        Method m = methods[i];
                        if ( GET_RESOURCE_METHOD_NAME.equals( m.getName() ) && m.getParameterTypes().length == 1
                            && m.getParameterTypes()[0] == String.class && m.getReturnType() == URL.class )
                        {
                            // ensure modifier is protected or public or the private
                            // method is defined in the plugin class itself
                            int mod = m.getModifiers();
                            if ( Modifier.isProtected( mod ) || Modifier.isPublic( mod )
                                || ( Modifier.isPrivate( mod ) && cl == resourceProvider.getClass() ) )
                            {
                                m.setAccessible( true );
                                tmpGetResourceMethod = m;
                                break;
                            }
                        }
                    }
                    cl = cl.getSuperclass();
                }
            }
            catch ( Throwable t )
            {
                tmpGetResourceMethod = null;
            }
        }

        // set what we have found and prevent future lookups
        getResourceMethod = tmpGetResourceMethod;
        getResourceMethodChecked = true;

        // now also return the method
        return getResourceMethod;
    }

    /**
     * Logs the message in the level
     *
     * @param level The log level at which to log the message
     * @param message The message to log
     */
    public void log(final int level, final String message ) {
        log(level, message, null);
    }

    /**
     * Logs the message in the level
     *
     * @param level The log level at which to log the message
     * @param message The message to log
     * @param t The <code>Throwable</code> to log with the message
     */
    public void log(final int level, final String message, final Throwable t ) {
        final String text = "[".concat(this.getTitle()).concat("] ").concat(message);
        switch(level) {
            case LogService.LOG_DEBUG: Util.LOGGER.debug(text, t);
                                       break;
            case LogService.LOG_INFO:  Util.LOGGER.info(text, t);
                                       break;
            case LogService.LOG_WARNING: Util.LOGGER.warn(text, t);
                                         break;
            case LogService.LOG_ERROR: Util.LOGGER.error(text, t);
                                        break;
            default: Util.LOGGER.debug(message, t);
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
    private final boolean spoolResource(final HttpServletRequest request,
        final HttpServletResponse response) throws IOException
    {
        try
        {
            // We need to call spoolResource0 in privileged block because it uses reflection, which
            // requires the following set of permissions:
            // (java.lang.RuntimePermission "getClassLoader")
            // (java.lang.RuntimePermission "accessDeclaredMembers")
            // (java.lang.reflect.ReflectPermission "suppressAccessChecks")
            // See also https://issues.apache.org/jira/browse/FELIX-4652
            final Boolean ret = AccessController.doPrivileged(new PrivilegedExceptionAction<Boolean>()
            {

                public Boolean run() throws Exception
                {
                    return spoolResource0(request, response) ? Boolean.TRUE : Boolean.FALSE;
                }
            });
            return ret.booleanValue();
        }
        catch (PrivilegedActionException e)
        {
            final Exception x = e.getException();
            throw x instanceof IOException ? (IOException) x : new IOException(
                x.toString());
        }
    }

    final boolean spoolResource0( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {
        // no resource if no resource accessor
        Method getResourceMethod = getGetResourceMethod();
        if ( getResourceMethod == null )
        {
            return false;
        }

        String pi = request.getPathInfo();
        try
        {

            // check for a resource, fail if none
            URL url = ( URL ) getResourceMethod.invoke( getResourceProvider(), new Object[]
                { pi } );
            if ( url == null ) {
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
                long lastModified = connection.getLastModified();
                if ( lastModified > 0 )
                {
                    long ifModifiedSince = request.getDateHeader( "If-Modified-Since" );
                    if ( ifModifiedSince >= ( lastModified / 1000 * 1000 ) )
                    {
                        // Round down to the nearest second for a proper compare
                        // A ifModifiedSince of -1 will always be less
                        response.setStatus( HttpServletResponse.SC_NOT_MODIFIED );

                        return true;
                    }

                    // have to send, so set the last modified header now
                    response.setDateHeader( "Last-Modified", lastModified );
                }

                // describe the contents
                final String contentType = getServletContext().getMimeType(pi);
                if ( contentType != null )
                {
                    response.setContentType( contentType );
                }
                if (connection.getContentLength() != -1)
                {
                    response.setContentLength( connection.getContentLength() );
                }

                // spool the actual contents
                OutputStream out = response.getOutputStream();
                byte[] buf = new byte[2048];
                int rd;
                while ( ( rd = ins.read( buf ) ) >= 0 )
                {
                    out.write( buf, 0, rd );
                }

                // over and out ...
                return true;
            }

        } catch ( IllegalAccessException | InvocationTargetException ignore ) {
            // log or throw ???
        }

        return false;
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
    protected PrintWriter startResponse( HttpServletRequest request, HttpServletResponse response ) throws IOException {
        response.setCharacterEncoding( "utf-8" );
        response.setContentType( "text/html" );

        final PrintWriter pw = response.getWriter();

        final String appRoot = ( String ) request.getAttribute( WebConsoleConstants.ATTR_APP_ROOT );

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
        pw.println( NavigationRenderer.HEADER );

        return pw;
    }


    /**
     * This method is called to generate the top level links with the available plug-ins.
     *
     * @param request the HTTP request coming from the user
     * @param pw the writer, where the HTML data is rendered
     */
    @SuppressWarnings({ "rawtypes" })
    protected void renderTopNavigation(final HttpServletRequest request, final PrintWriter pw ) {
        final String appRoot = ( String ) request.getAttribute( WebConsoleConstants.ATTR_APP_ROOT );
        final Map menuMap = ( Map ) request.getAttribute( AbstractOsgiManagerPlugin.ATTR_LABEL_MAP_CATEGORIZED );
        final Map langMap = (Map) request.getAttribute(WebConsoleConstants.ATTR_LANG_MAP);

        NavigationRenderer.renderTopNavigation(pw, appRoot, menuMap, langMap, request.getLocale());
    }

    @SuppressWarnings({ "rawtypes" })
    protected void renderMenu(final Map menuMap, final String appRoot, final PrintWriter pw ) {
        throw new UnsupportedOperationException();
    }

    /**
     * This method is responsible for generating the footer of the page.
     *
     * @param pw the writer, where the HTML data is rendered
     * @see #startResponse(HttpServletRequest, HttpServletResponse)
     */
    protected void endResponse( PrintWriter pw ) {
        pw.println(NavigationRenderer.FOOTER);
    }

    /**
     * Do not use this method anymore. Use the Servlet API for request parameter
     * handling.
     * @param request The request object
     * @param name The name of the parameter
     * @return The parameter value or <code>null</code> if the parameter is not set
     * @deprecated Use the Servlet API for uploads
     */
    @Deprecated
    public static final String getParameter( HttpServletRequest request, String name ) {
        return WebConsoleUtil.getParameter(request, name);
    }

    /**
     * Utility method to handle relative redirects.
     * Some application servers like Web Sphere handle relative redirects differently
     * therefore we should make an absolute URL before invoking send redirect.
     *
     * @param request the HTTP request coming from the user
     * @param response the HTTP response, where data is rendered
     * @param redirectUrl the redirect URI.
     * @throws IOException If an input or output exception occurs
     * @throws IllegalStateException   If the response was committed or if a partial
     *  URL is given and cannot be converted into a valid URL
     * @deprecated use {@link WebConsoleUtil#sendRedirect(HttpServletRequest, HttpServletResponse, String)}
     */
    protected void sendRedirect(final HttpServletRequest request,
                                final HttpServletResponse response,
                                String redirectUrl) throws IOException {
        WebConsoleUtil.sendRedirect(request, response, redirectUrl);
    }

    /**
     * Returns the {@link BrandingPlugin} currently used for web console
     * branding.
     *
     * @return the brandingPlugin
     * @deprecated
     */
    @Deprecated
    public static BrandingPlugin getBrandingPlugin() {
        return AbstractWebConsolePlugin.BRANDING_PLUGIN;
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
            AbstractWebConsolePlugin.BRANDING_PLUGIN = DefaultBrandingPlugin.getInstance();
        } else {
            AbstractWebConsolePlugin.BRANDING_PLUGIN = brandingPlugin;
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
        // nothing to do
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
        try {
            return Util.readTemplateFile( getClass(), templateFile );
        } catch (final IOException e) {
            Util.LOGGER.error("readTemplateFile: File '{}' not found through class {}", templateFile, getClass() );
            return "";
        }
    }

    private final String getCssLinks( final String appRoot )
    {
        // get the CSS references and return nothing if there are none
        final String[] cssRefs = getCssReferences();
        if ( cssRefs == null )
        {
            return "";
        }

        // build the CSS links from the references
        final StringBuilder buf = new StringBuilder();
        for ( int i = 0; i < cssRefs.length; i++ )
        {
            buf.append( "<link href='" );
            buf.append( toUrl( cssRefs[i], appRoot ) );
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
    private static final String toUrl( final String url, final String appRoot )
    {
        if ( url.startsWith( "/" ) )
        {
            return appRoot + url;
        }
        return url;
    }

    /**
     * Returns the {@link RequestVariableResolver} for the given request.
     * <p>
     * The resolver is added to the request attributes via the web console main
     * servlet before it invokes any plugins.
     * The preset properties are
     * <code>appRoot</code> set to the value of the
     * {@link WebConsoleConstants#ATTR_APP_ROOT} request attribute and
     * <code>pluginRoot</code> set to the value of the
     * {@link WebConsoleConstants#ATTR_PLUGIN_ROOT} request attribute.
     * <p>
     *
     * @param request The request whose attribute is returned
     *
     * @return The {@link RequestVariableResolver} for the given request.
     * @since 3.5.0
     */
    public RequestVariableResolver getVariableResolver( final ServletRequest request) {
        return (RequestVariableResolver) request.getAttribute( RequestVariableResolver.REQUEST_ATTRIBUTE );
    }
}

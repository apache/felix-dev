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
package org.apache.felix.webconsole.internal.core;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.felix.inventory.Format;
import org.apache.felix.inventory.InventoryPrinter;
import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Parser;
import org.apache.felix.webconsole.bundleinfo.BundleInfo;
import org.apache.felix.webconsole.bundleinfo.BundleInfoProvider;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.servlet.AbstractOsgiManagerPlugin;
import org.apache.felix.webconsole.servlet.RequestVariableResolver;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.owasp.encoder.Encode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;


/**
 * The <code>BundlesServlet</code> provides the bundles plugins, used to display
 * the list of bundles, installed on the framework. It also adds ability to control
 * the lifecycle of the bundles, like start, stop, uninstall, install.
 */
@SuppressWarnings("deprecation")
public class BundlesServlet extends AbstractOsgiManagerPlugin implements InventoryPrinter {

    /** the label of the bundles plugin - used by other plugins to reference to plugin details */
    public static final String NAME = "bundles";
    public static final String PRINTER_NAME = "Bundles";
    private static final String TITLE = "%bundles.pluginTitle";
    private static final String CSS[] = { "/res/ui/bundles.css" };

    // an LDAP filter, that is used to search manifest headers, see FELIX-1441
    private static final String FILTER_PARAM = "filter";

    private static final String FIELD_STARTLEVEL = "bundlestartlevel";

    private static final String FIELD_START = "bundlestart";

    private static final String FIELD_BUNDLEFILE = "bundlefile";

    private static final String FIELD_UPLOADID = "uploadid";

    // set to ask for PackageAdmin.refreshPackages() after install/update
    private static final String FIELD_REFRESH_PACKAGES = "refreshPackages";

    // set to force a parallel version to be created instead of updating an existing version of a bundle
    private static final String FIELD_PARALLEL_VERSION = "parallelVersion";

    // bootdelegation property entries. wildcards are converted to package
    // name prefixes. whether an entry is a wildcard or not is set as a flag
    // in the bootPkgWildcards array.
    // see #activate and #isBootDelegated
    private String[] bootPkgs;

    // a flag for each entry in bootPkgs indicating whether the respective
    // entry was declared as a wildcard or not
    // see #activate and #isBootDelegated
    private boolean[] bootPkgWildcards;

    private ServiceRegistration<InventoryPrinter> configurationPrinter;
    private ServiceTracker<BundleInfoProvider, BundleInfoProvider> bundleInfoTracker;

    // templates
    private final String TEMPLATE_MAIN;

    private ServiceRegistration<BundleInfoProvider> bipCapabilitiesProvided;

    private ServiceRegistration<BundleInfoProvider> bipCapabilitiesRequired;

    /**
     * Default constructor
     * @throws IOException If template can't be read
     */
    public BundlesServlet() throws IOException {
        // load templates
        TEMPLATE_MAIN = readTemplateFile( "/templates/bundles.html" );
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
        return NAME;
    }

    @Override
    protected String getTitle() {
        return TITLE;
    }

    @Override
    public void activate( BundleContext bundleContext ) {
        super.activate( bundleContext );

        bundleInfoTracker = new ServiceTracker<>( bundleContext, BundleInfoProvider.class, new ServiceTrackerCustomizer<BundleInfoProvider,BundleInfoProvider>() {

                @Override
                public BundleInfoProvider addingService(ServiceReference<BundleInfoProvider> reference) {
                    return bundleContext.getService(reference);
                }

                @Override
                public void modifiedService(ServiceReference<BundleInfoProvider> reference, BundleInfoProvider service) {
                    // nothing to do
                }

                @Override
                public void removedService(ServiceReference<BundleInfoProvider> reference, BundleInfoProvider service) {
                    try {
                        bundleContext.ungetService(reference);
                    } catch ( final IllegalStateException ise) {
                        // might happen on shutdown, ignore
                    }
                }
        });
        bundleInfoTracker.open();

        // bootdelegation property parsing from Apache Felix R4SearchPolicyCore
        String bootDelegation = bundleContext.getProperty( Constants.FRAMEWORK_BOOTDELEGATION );
        bootDelegation = ( bootDelegation == null ) ? "java.*" : bootDelegation + ",java.*";
        StringTokenizer st = new StringTokenizer( bootDelegation, " ," );
        bootPkgs = new String[st.countTokens()];
        bootPkgWildcards = new boolean[bootPkgs.length];
        for ( int i = 0; i < bootPkgs.length; i++ ) {
            bootDelegation = st.nextToken();
            if ( bootDelegation.endsWith( "*" ) ) {
                bootPkgWildcards[i] = true;
                bootDelegation = bootDelegation.substring( 0, bootDelegation.length() - 1 );
            }
            bootPkgs[i] = bootDelegation;
        }

        Hashtable<String, Object> props = new Hashtable<>();
        props.put(InventoryPrinter.TITLE, this.getTitle());
        props.put(InventoryPrinter.NAME, PRINTER_NAME);
        configurationPrinter = bundleContext.registerService( InventoryPrinter.class, this, props );
        bipCapabilitiesProvided = bundleContext.registerService( BundleInfoProvider.class, new CapabilitiesProvidedInfoProvider( bundleContext.getBundle() ), null );
        bipCapabilitiesRequired = bundleContext.registerService( BundleInfoProvider.class, new CapabilitiesRequiredInfoProvider( bundleContext.getBundle() ), null );
    }

    @Override
    public void deactivate() {
        if ( configurationPrinter != null ) {
            configurationPrinter.unregister();
            configurationPrinter = null;
        }

        if ( bundleInfoTracker != null ) {
            bundleInfoTracker.close();
            bundleInfoTracker = null;
        }

        if ( bipCapabilitiesProvided != null ) {
            bipCapabilitiesProvided.unregister();
            bipCapabilitiesProvided = null;
        }
        if ( bipCapabilitiesRequired != null ) {
            bipCapabilitiesRequired.unregister();
            bipCapabilitiesRequired = null;
        }
        super.deactivate();
    }

    //---------- InventoryPrinter

    @Override
    @SuppressWarnings({"rawtypes"})
    public void print(PrintWriter pw, Format format, boolean isZip) {
        try
        {
            final Map<String, Object> map = createObjectStructure(null, null, null, true, Locale.ENGLISH, null, null );

            pw.println( "Status: " + map.get( "status" ) );
            pw.println();

            Object[] data = (Object[]) map.get( "data" );
            for ( int i = 0; i < data.length; i++ )
            {
                Map bundle = (Map) data[i];

                pw.println( MessageFormat.format( "Bundle {0} - {1} {2} (state: {3})", new Object[]
                        { bundle.get( "id" ), bundle.get( "name" ), bundle.get( "version" ), bundle.get( "state" ) } ) );

                Object[] props = (Object[]) bundle.get( "props" );
                for ( int pi = 0; pi < props.length; pi++ )
                {
                    Map entry = (Map) props[pi];
                    String key = ( String ) entry.get( "key" );
                    if ( "nfo".equals( key ) )
                    {
                        // BundleInfo (see #bundleInfo & #bundleInfoDetails
                        Map infos = ( Map ) entry.get( "value" );
                        Iterator infoKeys = infos.keySet().iterator();
                        while ( infoKeys.hasNext() )
                        {
                            String infoKey = ( String ) infoKeys.next();
                            pw.println( "    " + infoKey + ": " );

                            Object[] infoA = (Object[]) infos.get(infoKey);
                            for ( int iai = 0; iai < infoA.length; iai++ )
                            {
                                if ( infoA[iai] != null )
                                {
                                    Map info = (Map) infoA[iai];
                                    pw.println( "        " + info.get( "name" ) );
                                }
                            }
                        }
                    }
                    else
                    {
                        // regular data
                        pw.print( "    " + key + ": " );

                        Object entryValue = entry.get( "value" );
                        if ( entryValue.getClass().isArray() )
                        {
                            pw.println();
                            for ( int ei = 0; ei < Array.getLength(entryValue); ei++ )
                            {
                                Object o = Array.get(entryValue, ei);
                                if ( o != null )
                                {
                                    pw.println( "        " + o );
                                }
                            }
                        }
                        else
                        {
                            pw.println( entryValue );
                        }
                    }
                }

                pw.println();
            }
        }
        catch ( Exception e )
        {
            Util.LOGGER.error( "Problem rendering Bundle details for configuration status", e );
        }
    }


    //---------- BaseWebConsolePlugin

    @Override
    protected void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
        final RequestInfo reqInfo = new RequestInfo(request);
        if ( "upload".equals(reqInfo.pathInfo) )
        {
            super.doGet(request, response);
            return;
        }
        if ( reqInfo.bundle == null && reqInfo.bundleRequested )
        {
            response.sendError(404);
            return;
        }
        if ( reqInfo.extension.equals("json")  )
        {
            final String pluginRoot = ( String ) request.getAttribute( ServletConstants.ATTR_PLUGIN_ROOT );
            final String servicesRoot = getServicesRoot( request );
            try
            {
                this.renderJSON(response, reqInfo.bundle, pluginRoot, servicesRoot, request.getLocale(), request.getParameter(FILTER_PARAM), null );
            }
            catch (InvalidSyntaxException e)
            {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid LDAP filter specified");
            }

            // nothing more to do
            return;
        }

        super.doGet( request, response );
    }

    @Override
    protected void doPost( HttpServletRequest req, HttpServletResponse resp )
    throws ServletException, IOException {
        boolean success = false;
        BundleException bundleException = null;
        final String action = req.getParameter( "action" );

        if ( "refreshPackages".equals( action ) ) {
            // refresh packages and give it most 15 seconds to finish
            final FrameworkWiring fw = this.bundleContext.getBundle(Constants.SYSTEM_BUNDLE_LOCATION).adapt(FrameworkWiring.class);
            BaseUpdateInstallHelper.refreshPackages( fw, this.bundleContext, 15000L, null );
            success = true;
        } else if ( "install".equals( action ) ) {
            installBundles( req );

            if (req.getRequestURI().endsWith( "/install" )) {
                // just send 200/OK, no content
                resp.setContentLength( 0 );
                resp.setStatus(200);
            } else {
                // redirect to URL
                resp.sendRedirect( req.getRequestURI() );
            }

            return;
        } else {
            final RequestInfo reqInfo = new RequestInfo( req );
            if ( reqInfo.bundle == null && reqInfo.bundleRequested ) {
                resp.sendError( 404 );
                return;
            }

            final Bundle bundle = reqInfo.bundle;
            if ( bundle != null ) {
                if ( "start".equals( action ) ) {
                    // start bundle
                    try {
                        bundle.start();
                    } catch ( BundleException be ) {
                        bundleException = be;
                        Util.LOGGER.error( "Cannot start", be );
                    }
                } else if ( "stop".equals( action ) ) {
                    // stop bundle
                    try {
                        bundle.stop();
                    } catch ( BundleException be ) {
                        bundleException = be;
                        Util.LOGGER.error( "Cannot stop", be );
                    }
                } else if ( "refresh".equals( action ) ) {
                    // refresh bundle wiring and give at most 5 seconds to finish
                    final FrameworkWiring fw = this.bundleContext.getBundle(Constants.SYSTEM_BUNDLE_LOCATION).adapt(FrameworkWiring.class);
                    BaseUpdateInstallHelper.refreshPackages( fw, this.bundleContext, 5000L, bundle );
                } else if ( "update".equals( action ) ) {
                    // update the bundle
                    update( bundle );
                } else if ( "uninstall".equals( action ) ) {
                    // uninstall bundle
                    try {
                        bundle.uninstall();
                    } catch ( BundleException be ) {
                        bundleException = be;
                        Util.LOGGER.error( "Cannot uninstall", be );
                    }
                }

                // write the state only
                resp.setContentType( "application/json" );
                resp.setCharacterEncoding( "UTF-8" );
                if ( null == this.bundleContext ) {
                    // refresh package on the web console itself or some of it's dependencies
                    resp.getWriter().print("false");
                } else {
                    resp.getWriter().print( "{\"fragment\":" + isFragmentBundle( bundle )
                    + ",\"stateRaw\":" + bundle.getState() + "}" );
                }
                return;
            }
        }

        if ( success && null != this.bundleContext ) {
            final String pluginRoot = ( String ) req.getAttribute( ServletConstants.ATTR_PLUGIN_ROOT );
            final String servicesRoot = getServicesRoot( req );
            try {
                this.renderJSON( resp, null, pluginRoot, servicesRoot, req.getLocale(), req.getParameter(FILTER_PARAM), bundleException );
            } catch (InvalidSyntaxException e) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid LDAP filter specified");
            }
        } else {
            super.doPost( req, resp );
        }
    }

    private String getServicesRoot(HttpServletRequest request) {
        return ( ( String ) request.getAttribute( ServletConstants.ATTR_APP_ROOT ) ) +
                "/" + ServicesServlet.LABEL + "/";
    }

    Bundle getBundle( String pathInfo )
    {
        // only use last part of the pathInfo
        pathInfo = pathInfo.substring( pathInfo.lastIndexOf( '/' ) + 1 );

        // assume bundle Id
        try
        {
            final long bundleId = Long.parseLong( pathInfo );
            if ( bundleId >= 0 )
            {
                return BundleContextUtil.getWorkingBundleContext(this.bundleContext).getBundle( bundleId );
            }
        }
        catch ( NumberFormatException nfe )
        {
            // check if this follows the pattern {symbolic-name}[:{version}]
            final int pos = pathInfo.indexOf(':');
            final String symbolicName;
            final String version;
            if ( pos == -1 ) {
                symbolicName = pathInfo;
                version = null;
            } else {
                symbolicName = pathInfo.substring(0, pos);
                version = pathInfo.substring(pos+1);
            }

            // search
            final Bundle[] bundles = BundleContextUtil.getWorkingBundleContext(this.bundleContext).getBundles();
            for(int i=0; i<bundles.length; i++) {
                final Bundle bundle = bundles[i];
                // check symbolic name first
                if ( symbolicName.equals(bundle.getSymbolicName()) ) {
                    if ( version == null || version.equals(bundle.getHeaders().get(Constants.BUNDLE_VERSION)) ) {
                        return bundle;
                    }
                }
            }
        }


        return null;
    }


    private void appendBundleInfoCount( final StringBuilder buf, String msg, int count ) {
        buf.append(count);
        buf.append(" bundle");
        if ( count != 1 )
            buf.append( 's' );
        buf.append(' ');
        buf.append(msg);
    }

    @Override
    public void renderContent( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {
        // get request info from request attribute
        final RequestInfo reqInfo = getRequestInfo(request);

        final Bundle systemBundle = this.bundleContext.getBundle(Constants.SYSTEM_BUNDLE_LOCATION);
        final FrameworkStartLevel fsl = systemBundle.adapt(FrameworkStartLevel.class);
        final int startLevel = fsl.getInitialBundleStartLevel();

        // prepare variables
        final RequestVariableResolver vars = this.getVariableResolver(request);
        vars.put( "startLevel", String.valueOf(startLevel));
        vars.put( "drawDetails", reqInfo.bundleRequested ? Boolean.TRUE : Boolean.FALSE );
        vars.put( "currentBundle", (reqInfo.bundleRequested && reqInfo.bundle != null ? String.valueOf(reqInfo.bundle.getBundleId()) : "null"));

        final String pluginRoot = ( String ) request.getAttribute( ServletConstants.ATTR_PLUGIN_ROOT );
        final String servicesRoot = getServicesRoot ( request );
        StringWriter w = new StringWriter();
        try
        {
            writeJSON(w, reqInfo.bundle, pluginRoot, servicesRoot, request.getLocale(), request.getParameter(FILTER_PARAM), null );
        }
        catch (InvalidSyntaxException e)
        {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid LDAP filter specified");
            return;
        }
        vars.put( "__bundles__", w.toString());

        response.getWriter().print(TEMPLATE_MAIN);
    }

    private void renderJSON( final HttpServletResponse response, final Bundle bundle, final String pluginRoot, final String servicesRoot, final Locale locale, final String filter, final BundleException be )
            throws IOException, InvalidSyntaxException
    {
        response.setContentType( "application/json" );
        response.setCharacterEncoding( "UTF-8" );

        final PrintWriter pw = response.getWriter();
        writeJSON(pw, bundle, pluginRoot, servicesRoot, locale, filter, be);
    }


    private void writeJSON( final Writer pw, final Bundle bundle, final String pluginRoot, final String servicesRoot, final Locale locale, final String filter, final BundleException be )
            throws IOException, InvalidSyntaxException
    {
        final Map<String, Object> map = createObjectStructure( bundle, pluginRoot, servicesRoot, false, locale, filter, be );
        final JSONWriter writer = new JSONWriter(pw);

        writer.value(map);
    }

    private Map<String, Object> createObjectStructure( final Bundle bundle, final String pluginRoot,
            final String servicesRoot, final boolean fullDetails, final Locale locale, final String filter, final BundleException be ) throws IOException, InvalidSyntaxException
    {
        final Bundle[] allBundles = this.getBundles();
        final List<Object> status = getStatusLine(allBundles);
        final String statusLine = (String) status.remove(5);
        // filter bundles by headers
        final Bundle[] bundles;
        if (bundle != null)
        {
            bundles = new Bundle[] { bundle };
        }
        else if (filter != null)
        {
            Filter f = this.bundleContext.createFilter(filter);
            ArrayList<Bundle> list = new ArrayList<Bundle>(allBundles.length);
            final String localeString = locale.toString();
            for (int i = 0, size = allBundles.length; i < size; i++)
            {
                if (f.match(allBundles[i].getHeaders(localeString)))
                {
                    list.add(allBundles[i]);
                }
            }
            bundles = list.toArray(new Bundle[list.size()]);
        }
        else
        {
            bundles = allBundles;
        }

        Util.sort( bundles, locale );

        final Map<String, Object> map = new LinkedHashMap<String, Object>();

        if (null != be)
        {
            final StringWriter s = new StringWriter();
            final Throwable t = be.getNestedException() != null ? be.getNestedException() : be;
            t.printStackTrace( new PrintWriter(s) );
            map.put("error", s.toString());
        }

        map.put("status", statusLine);

        // add raw status
        map.put( "s", status.toArray() );

        final Object[] bundlesArray = new Object[bundles.length];
        for ( int i = 0; i < bundles.length; i++ )
        {
            bundlesArray[i] =
                    bundleInfo( bundles[i], fullDetails || bundle != null, pluginRoot, servicesRoot, locale );
        }

        map.put("data", bundlesArray);
        return map;
    }

    private List<Object> getStatusLine(final Bundle[] bundles)
    {
        List<Object> ret = new ArrayList<Object>();
        int active = 0, installed = 0, resolved = 0, fragments = 0;
        for ( int i = 0; i < bundles.length; i++ )
        {
            switch ( bundles[i].getState() )
            {
            case Bundle.ACTIVE:
                active++;
                break;
            case Bundle.INSTALLED:
                installed++;
                break;
            case Bundle.RESOLVED:
                if ( isFragmentBundle( bundles[i] ) )
                {
                    fragments++;
                }
                else
                {
                    resolved++;
                }
                break;
            }
        }
        final StringBuilder buffer = new StringBuilder();
        buffer.append("Bundle information: ");
        appendBundleInfoCount(buffer, "in total", bundles.length);
        if ( active == bundles.length || active + fragments == bundles.length )
        {
            buffer.append(" - all ");
            appendBundleInfoCount(buffer, "active.", bundles.length);
        }
        else
        {
            if ( active != 0 )
            {
                buffer.append(", ");
                appendBundleInfoCount(buffer, "active", active);
            }
            if ( fragments != 0 )
            {
                buffer.append(", ");
                appendBundleInfoCount(buffer, "active fragments", fragments);
            }
            if ( resolved != 0 )
            {
                buffer.append(", ");
                appendBundleInfoCount(buffer, "resolved", resolved);
            }
            if ( installed != 0 )
            {
                buffer.append(", ");
                appendBundleInfoCount(buffer, "installed", installed);
            }
            buffer.append('.');
        }
        ret.add(bundles.length);
        ret.add(active);
        ret.add(fragments);
        ret.add(resolved);
        ret.add(installed);
        ret.add(buffer.toString());
        return ret;
    }

    private Map<String, Object> bundleInfo( final Bundle bundle,
            final boolean details,
            final String pluginRoot,
            final String servicesRoot,
            final Locale locale )
    {
        final Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", bundle.getBundleId() );
        result.put("name", Util.getName( bundle, locale ) );
        result.put("fragment", isFragmentBundle(bundle) );
        result.put("stateRaw", bundle.getState() );
        result.put("state", toStateString( bundle ) );
        result.put("version", Util.getHeaderValue(bundle, Constants.BUNDLE_VERSION) );
        if ( bundle.getSymbolicName() != null )
        {
            result.put("symbolicName",  bundle.getSymbolicName() );
        }
        result.put("category",  Util.getHeaderValue(bundle, Constants.BUNDLE_CATEGORY) );

        if ( details )
        {
            bundleDetails( result, bundle, pluginRoot, servicesRoot, locale );
        }

        return result;
    }


    private final Bundle[] getBundles()
    {
        return BundleContextUtil.getWorkingBundleContext(this.bundleContext).getBundles();
    }


    private String toStateString( final Bundle bundle )
    {
        switch ( bundle.getState() )
        {
        case Bundle.INSTALLED:
            return "Installed";
        case Bundle.RESOLVED:
            if ( isFragmentBundle(bundle) )
            {
                return "Fragment";
            }
            return "Resolved";
        case Bundle.STARTING:
            return "Starting";
        case Bundle.ACTIVE:
            return "Active";
        case Bundle.STOPPING:
            return "Stopping";
        case Bundle.UNINSTALLED:
            return "Uninstalled";
        default:
            return "Unknown: " + bundle.getState();
        }
    }


    private final boolean isFragmentBundle(final Bundle bundle ) {
        // Workaround for FELIX-3670
        if ( bundle.getState() == Bundle.UNINSTALLED ) {
            return bundle.getHeaders().get( Constants.FRAGMENT_HOST ) != null;
        }
        final BundleRevision rev = bundle.adapt(BundleRevision.class);
        return rev != null && (rev.getTypes() & BundleRevision.TYPE_FRAGMENT) == BundleRevision.TYPE_FRAGMENT;
    }

    private void keyVal(final List<Map<String, Object>> props, final String key, final Object val)
    {
        if ( val != null )
        {
            final Map<String, Object> obj = new LinkedHashMap<String, Object>();
            obj.put("key", key);
            if ( val instanceof String ) {
                obj.put("value", Encode.forJavaScript((String)val));
            } else {
                obj.put("value", val);
            }
            props.add(obj);
        }
    }
    private final void bundleDetails( final Map<String, Object> result,
            final Bundle bundle,
            final String pluginRoot,
            final String servicesRoot,
            final Locale locale)
    {
        final Dictionary<String, String> headers = bundle.getHeaders( locale == null ? null : locale.toString() );

        final List<Map<String, Object>> props = new ArrayList<Map<String, Object>>();

        keyVal( props, "Symbolic Name", bundle.getSymbolicName() );
        keyVal( props, "Version", headers.get( Constants.BUNDLE_VERSION ) );
        keyVal( props, "Bundle Location", bundle.getLocation() );
        keyVal( props, "Last Modification", new Date( bundle.getLastModified() ) );

        String docUrl = headers.get( Constants.BUNDLE_DOCURL );
        if ( docUrl != null )
        {
            keyVal( props, "Bundle Documentation", docUrl );
        }

        keyVal( props, "Vendor", headers.get( Constants.BUNDLE_VENDOR ) );
        keyVal( props, "Copyright", headers.get( Constants.BUNDLE_COPYRIGHT ) );
        keyVal( props, "Description", headers.get( Constants.BUNDLE_DESCRIPTION ) );

        keyVal( props, "Start Level", getStartLevel( bundle ) );

        keyVal( props, "Bundle Classpath", headers.get( Constants.BUNDLE_CLASSPATH ) );

        listFragmentInfo( props, bundle, pluginRoot );

        if ( bundle.getState() == Bundle.INSTALLED )
        {
            listImportExportsUnresolved( props, bundle, pluginRoot );
        }
        else
        {
            listImportExport( props, bundle, pluginRoot );
        }

        if ( bundle.getState() != Bundle.UNINSTALLED )
        {
            listServices( props, bundle, servicesRoot );
        }

        listHeaders( props, bundle );
        final String appRoot = ( pluginRoot == null ) ? "" : pluginRoot.substring( 0, pluginRoot.lastIndexOf( "/" ) );
        bundleInfoDetails( props, bundle, appRoot, locale );

        result.put( "props", props.toArray(new Object[props.size()]));
    }


    private final void bundleInfoDetails( List<Map<String, Object>> props, Bundle bundle, String appRoot, final Locale locale)
    {
        final Map<String, Object> val = new LinkedHashMap<String, Object>();
        val.put("key", "nfo");
        final Map<String, Object[]> value = new LinkedHashMap<String, Object[]>();
        final Object[] bundleInfoProviders = bundleInfoTracker.getServices();
        for ( int i = 0; bundleInfoProviders != null && i < bundleInfoProviders.length; i++ )
        {
            final BundleInfoProvider infoProvider = (BundleInfoProvider) bundleInfoProviders[i];
            final BundleInfo[] infos = infoProvider.getBundleInfo(bundle, appRoot, locale);
            if ( null != infos && infos.length > 0)
            {
                final Object[] infoArray = new Object[infos.length];
                for ( int j = 0; j < infos.length; j++ )
                {
                    infoArray[j] = bundleInfo( infos[j] );
                }
                value.put(infoProvider.getName(locale), infoArray);
            }
        }
        val.put("value", value);
        props.add(val);
    }


    private static final Map<String, Object> bundleInfo( BundleInfo info )
    {
        final Map<String, Object> val = new LinkedHashMap<String, Object>();
        val.put( "name", info.getName() );
        val.put( "description", info.getDescription() );
        val.put( "type", info.getType().getName() );
        val.put( "value", info.getValue() );
        return val;
    }


    private final Integer getStartLevel( Bundle bundle ) {
        if ( bundle.getState() != Bundle.UNINSTALLED ){
            final BundleStartLevel bsl = bundle.adapt( BundleStartLevel.class );
            if (bsl != null ) {
                return bsl.getStartLevel();
            }
        }

        // bundle has been uninstalled or StartLevel service is not available
        return null;
    }


    private void listImportExport( List<Map<String, Object>> props, Bundle bundle, final String pluginRoot ) {
        PackageAdmin packageAdmin = getPackageAdmin();
        if ( packageAdmin == null )
        {
            return;
        }

        Map<String, Bundle> usingBundles = new TreeMap<>();

        ExportedPackage[] exports = packageAdmin.getExportedPackages( bundle );
        if ( exports != null && exports.length > 0 ) {
            // do alphabetical sort
            Arrays.sort( exports, new Comparator<ExportedPackage>() {
                @Override
                public int compare( ExportedPackage p1, ExportedPackage p2 ) {
                    return p1.getName().compareTo( p2.getName() );
                }
            } );

            Object[] val = new Object[exports.length];
            for ( int j = 0; j < exports.length; j++ )
            {
                ExportedPackage export = exports[j];
                val[j] = collectExport( export.getName(), export.getVersion() );
                Bundle[] ubList = export.getImportingBundles();
                if ( ubList != null )
                {
                    for ( int i = 0; i < ubList.length; i++ )
                    {
                        Bundle ub = ubList[i];
                        String name = ub.getSymbolicName();
                        if (name == null) name = ub.getLocation();
                        usingBundles.put( name, ub );
                    }
                }
            }
            keyVal( props, "Exported Packages", val );
        }
        else
        {
            keyVal( props, "Exported Packages", "---" );
        }

        exports = packageAdmin.getExportedPackages( ( Bundle ) null );
        if ( exports != null && exports.length > 0 )
        {
            // collect import packages first
            final List<ExportedPackage> imports = new ArrayList<>();
            for ( int i = 0; i < exports.length; i++ )
            {
                final ExportedPackage ep = exports[i];
                final Bundle[] importers = ep.getImportingBundles();
                for ( int j = 0; importers != null && j < importers.length; j++ )
                {
                    if ( importers[j].getBundleId() == bundle.getBundleId() )
                    {
                        imports.add( ep );

                        break;
                    }
                }
            }
            // now sort
            Object[] val;
            if ( imports.size() > 0 ) {
                final ExportedPackage[] packages = ( ExportedPackage[] ) imports.toArray( new ExportedPackage[imports.size()] );
                Arrays.sort( packages, new Comparator<ExportedPackage>() {
                    @Override
                    public int compare( ExportedPackage p1, ExportedPackage p2 ) {
                        return p1.getName().compareTo( p2.getName() );
                    }
                } );
                // and finally print out
                val = new Object[packages.length];
                for ( int i = 0; i < packages.length; i++ ) {
                    ExportedPackage ep = packages[i];
                    val[i] = collectImport( ep.getName(), ep.getVersion(), false, ep, pluginRoot );
                }
            } else {
                // add description if there are no imports
                val = new Object[1];
                val[0] =  "None";
            }

            keyVal( props, "Imported Packages", val );
        }

        if ( !usingBundles.isEmpty() ) {
            Object[] val = new Object[usingBundles.size()];
            int index = 0;
            for(final Bundle usingBundle : usingBundles.values()) {
                val[index] = getBundleDescriptor( usingBundle, pluginRoot );
                index++;
            }
            keyVal( props, "Importing Bundles", val );
        }
    }


    private void listImportExportsUnresolved( final List<Map<String, Object>> props, Bundle bundle, final String pluginRoot ) {
        final Dictionary<String, String> dict = bundle.getHeaders();

        String target = ( String ) dict.get( Constants.EXPORT_PACKAGE );
        if ( target != null ) {
            Clause[] pkgs = Parser.parseHeader( target );
            if ( pkgs != null && pkgs.length > 0 ) {
                // do alphabetical sort
                Arrays.sort( pkgs, new Comparator<Clause>() {
                    @Override
                    public int compare( Clause p1, Clause p2 )
                    {
                        return p1.getName().compareTo( p2.getName() );
                    }
                } );

                Object[] val = new Object[pkgs.length];
                for ( int i = 0; i < pkgs.length; i++ )
                {
                    Clause export = new Clause( pkgs[i].getName(), pkgs[i].getDirectives(), pkgs[i].getAttributes() );
                    val[i] = collectExport( export.getName(), export.getAttribute( Constants.VERSION_ATTRIBUTE ) );
                }
                keyVal( props, "Exported Packages", val );
            }
            else
            {
                keyVal( props, "Exported Packages", "---" );
            }
        }

        target = ( String ) dict.get( Constants.IMPORT_PACKAGE );
        if ( target != null )
        {
            Clause[] pkgs = Parser.parseHeader( target );
            if ( pkgs != null && pkgs.length > 0 )
            {
                Map<String, Clause> imports = new TreeMap<>();
                for ( int i = 0; i < pkgs.length; i++ )
                {
                    Clause pkg = pkgs[i];
                    imports.put( pkg.getName(), new Clause( pkg.getName(), pkg.getDirectives(), pkg.getAttributes() ) );
                }

                // collect import packages first
                final Map<String, ExportedPackage> candidates = new HashMap<>();
                PackageAdmin packageAdmin = getPackageAdmin();
                if ( packageAdmin != null )
                {
                    ExportedPackage[] exports = packageAdmin.getExportedPackages( ( Bundle ) null );
                    if ( exports != null && exports.length > 0 )
                    {

                        for ( int i = 0; i < exports.length; i++ )
                        {
                            final ExportedPackage ep = exports[i];

                            Clause imp = ( Clause ) imports.get( ep.getName() );
                            if ( imp != null && isSatisfied( imp, ep ) )
                            {
                                candidates.put( ep.getName(), ep );
                            }
                        }
                    }
                }

                // now sort
                Object[] val;
                if ( imports.size() > 0 )
                {
                    final List<StringBuilder> importList = new ArrayList<>();
                    for ( Iterator<Clause> ii = imports.values().iterator(); ii.hasNext(); )
                    {
                        Clause r4Import = ( Clause ) ii.next();
                        ExportedPackage ep = ( ExportedPackage ) candidates.get( r4Import.getName() );

                        // if there is no matching export, check whether this
                        // bundle has the package, ignore the entry in this case
                        if ( ep == null )
                        {
                            String path = r4Import.getName().replace( '.', '/' );
                            if ( bundle.getEntry( path ) != null )
                            {
                                continue;
                            }
                        }

                        importList.add(collectImport(  r4Import.getName(), r4Import.getAttribute( Constants.VERSION_ATTRIBUTE ),
                                Constants.RESOLUTION_OPTIONAL.equals( r4Import
                                        .getDirective( Constants.RESOLUTION_DIRECTIVE ) ), ep, pluginRoot ));
                    }
                    val = importList.toArray(new Object[importList.size()]);
                }
                else
                {
                    // add description if there are no imports
                    val = new Object[1];
                    val[0] = "---" ;
                }

                keyVal( props, "Imported Packages", val );
            }
        }
    }

    private String getServiceID(ServiceReference<?> ref, final String servicesRoot)
    {
        String id = ref.getProperty( Constants.SERVICE_ID ).toString();

        if ( servicesRoot != null )
        {
            StringBuilder val = new StringBuilder();
            val.append( "<a href='" ).append( servicesRoot ).append( id ).append( "'>" );
            val.append( id );
            val.append( "</a>" );
            return val.toString();
        }

        return id;
    }


    private void listServices( List<Map<String, Object>> props, Bundle bundle, final String servicesRoot )
    {
        ServiceReference<?>[] refs = bundle.getRegisteredServices();
        if ( refs == null || refs.length == 0 )
        {
            return;
        }

        for ( int i = 0; i < refs.length; i++ )
        {


            String key = "Service ID " + getServiceID( refs[i], servicesRoot );

            List<String> val = new ArrayList<>();

            appendProperty( val, refs[i], Constants.OBJECTCLASS, "Types" );
            appendProperty( val, refs[i], Constants.SERVICE_PID, "Service PID" );
            appendProperty( val, refs[i], "org.apache.felix.karaf.features.configKey", "Feature PID" );
            appendProperty( val, refs[i], ConfigurationAdmin.SERVICE_FACTORYPID, "Factory PID" );
            appendProperty( val, refs[i], "component.name", "Component Name" );
            appendProperty( val, refs[i], "component.id", "Component ID" );
            appendProperty( val, refs[i], "component.factory", "Component Factory" );
            appendProperty( val, refs[i], Constants.SERVICE_DESCRIPTION, "Description" );
            appendProperty( val, refs[i], Constants.SERVICE_VENDOR, "Vendor" );

            keyVal( props, key, val.toArray(new String[val.size()]));
        }
    }


    private void listHeaders( List<Map<String, Object>> props, Bundle bundle )
    {
        final List<String> val = new ArrayList<>();

        final Dictionary<String, String> headers = bundle.getHeaders(""); // don't localize at all - raw headers
        final Enumeration<String> he = headers.keys();
        while ( he.hasMoreElements() ) {
            final String header = he.nextElement();
            String value = headers.get( header );
            // Package headers may be long, support line breaking by
            // ensuring blanks after comma and semicolon.
            value = enableLineWrapping(value);
            val.add( header + ": " + value );
        }

        keyVal( props, "Manifest Headers", val.toArray(new String[val.size()]) );
    }

    private static final String enableLineWrapping(final String value)
    {
        StringBuilder sb = new StringBuilder(value.length() * 2 / 3);
        for (int i = 0; i < value.length(); i++)
        {
            final char ch = value.charAt( i );
            sb.append( ch );
            if ( ch == ';' || ch == ',' )
            {
                sb.append( ' ' );
            }
        }
        return sb.toString();
    }

    private void listFragmentInfo( final List<Map<String, Object>> props, final Bundle bundle, final String pluginRoot )
    {

        if ( isFragmentBundle( bundle ) )
        {
            Bundle[] hostBundles = getPackageAdmin().getHosts( bundle );
            if ( hostBundles != null )
            {
                final Object[] val = new Object[hostBundles.length];
                for ( int i = 0; i < hostBundles.length; i++ )
                {
                    val[i] = getBundleDescriptor( hostBundles[i], pluginRoot );
                }
                keyVal( props, "Host Bundles", val );
            }
        }
        else
        {
            Bundle[] fragmentBundles = getPackageAdmin().getFragments( bundle );
            if ( fragmentBundles != null )
            {
                final Object[] val = new Object[fragmentBundles.length];
                for ( int i = 0; i < fragmentBundles.length; i++ )
                {
                    val[i] = getBundleDescriptor( fragmentBundles[i], pluginRoot );
                }
                keyVal( props, "Fragments Attached", val );
            }
        }

    }


    private void appendProperty( final List<String> props, ServiceReference<?> ref, String name, String label )
    {
        StringBuilder dest = new StringBuilder();
        Object value = ref.getProperty( name );
        if ( value instanceof Object[] )
        {
            Object[] values = ( Object[] ) value;
            dest.append( label ).append( ": " );
            for ( int j = 0; j < values.length; j++ )
            {
                if ( j > 0 )
                    dest.append( ", " );
                dest.append( values[j] );
            }
            props.add(dest.toString());
        }
        else if ( value != null )
        {
            dest.append( label ).append( ": " ).append( value );
            props.add(dest.toString());
        }
    }


    private Object collectExport( String name, Version version )
    {
        return collectExport( name, ( version == null ) ? null : version.toString() );
    }


    private Object collectExport( String name, String version )
    {
        StringBuilder val = new StringBuilder();
        boolean bootDel = isBootDelegated( name );
        if ( bootDel )
        {
            val.append( "!! " );
        }

        val.append( name );

        if ( version != null )
        {
            val.append( ",version=" ).append( version );
        }

        if ( bootDel )
        {
            val.append( " -- Overwritten by Boot Delegation" );
        }

        return val.toString();
    }


    private StringBuilder collectImport(String name, Version version, boolean optional,
            ExportedPackage export, final String pluginRoot )
    {
        return collectImport( name, ( version == null ) ? null : version.toString(), optional, export, pluginRoot );
    }


    private StringBuilder collectImport( String name, String version, boolean optional, ExportedPackage export,
            final String pluginRoot )
    {
        StringBuilder val = new StringBuilder();
        boolean bootDel = isBootDelegated( name );

        String marker = null;
        val.append( name );

        if ( version != null )
        {
            val.append( ",version=" ).append( version );
        }

        if ( export != null )
        {
            val.append( " from " );
            val.append( getBundleDescriptor( export.getExportingBundle(), pluginRoot ) );

            if ( bootDel )
            {
                val.append( " -- Overwritten by Boot Delegation" );
                marker = "INFO";
            }
        }
        else
        {
            val.append( " -- Cannot be resolved" );
            marker = "ERROR";

            if ( optional )
            {
                val.append( " but is not required" );
            }

            if ( bootDel )
            {
                val.append( " and overwritten by Boot Delegation" );
            }
        }

        if ( marker != null ) {
            val.insert(0, ": ");
            val.insert(0, marker);
        }

        return val;
    }


    // returns true if the package is listed in the bootdelegation property
    private boolean isBootDelegated( String pkgName )
    {

        // bootdelegation analysis from Apache Felix R4SearchPolicyCore

        // Only consider delegation if we have a package name, since
        // we don't want to promote the default package. The spec does
        // not take a stand on this issue.
        if ( pkgName.length() > 0 )
        {

            // Delegate any packages listed in the boot delegation
            // property to the parent class loader.
            for ( int i = 0; i < bootPkgs.length; i++ )
            {

                // A wildcarded boot delegation package will be in the form of
                // "foo.", so if the package is wildcarded do a startsWith() or
                // a regionMatches() to ignore the trailing "." to determine if
                // the request should be delegated to the parent class loader.
                // If the package is not wildcarded, then simply do an equals()
                // test to see if the request should be delegated to the parent
                // class loader.
                if ( ( bootPkgWildcards[i] && ( pkgName.startsWith( bootPkgs[i] ) || bootPkgs[i].regionMatches( 0,
                        pkgName, 0, pkgName.length() ) ) )
                        || ( !bootPkgWildcards[i] && bootPkgs[i].equals( pkgName ) ) )
                {
                    return true;
                }
            }
        }

        return false;
    }


    private boolean isSatisfied( Clause imported, ExportedPackage exported )
    {
        if ( imported.getName().equals( exported.getName() ) )
        {
            String versionAttr = imported.getAttribute( Constants.VERSION_ATTRIBUTE );
            if ( versionAttr == null )
            {
                // no specific version required, this export surely satisfies it
                return true;
            }

            VersionRange required = VersionRange.valueOf( versionAttr );
            return required.includes( exported.getVersion() );
        }

        // no this export does not satisfy the import
        return false;
    }


    private String getBundleDescriptor( Bundle bundle, final String pluginRoot )
    {
        StringBuilder val = new StringBuilder();

        if ( pluginRoot != null )
        {
            val.append( "<a href='" ).append( pluginRoot ).append( '/' ).append( bundle.getBundleId() ).append( "'>" );
        }

        if ( bundle.getSymbolicName() != null )
        {
            // list the bundle name if not null
            val.append( bundle.getSymbolicName() );
            val.append( " (" ).append( bundle.getBundleId() );
            val.append( ")" );
        }
        else if ( bundle.getLocation() != null )
        {
            // otherwise try the location
            val.append( bundle.getLocation() );
            val.append( " (" ).append( bundle.getBundleId() );
            val.append( ")" );
        }
        else
        {
            // fallback to just the bundle id
            // only append the bundle
            val.append( bundle.getBundleId() );
        }
        if ( pluginRoot != null )
        {
            val.append( "</a>" );
        }
        return val.toString();
    }

    private void update( final Bundle bundle )
    {
        UpdateHelper t = new UpdateHelper( this, bundle, false );
        t.start();
    }

    private final class RequestInfo
    {
        public final String extension;
        public final Bundle bundle;
        public final boolean bundleRequested;
        public final String pathInfo;

        protected RequestInfo( final HttpServletRequest request )
        {
            String info = request.getPathInfo();
            // remove label and starting slash
            info = info.substring(getLabel().length() + 1);

            // get extension
            if ( info.endsWith(".json") )
            {
                extension = "json";
                info = info.substring(0, info.length() - 5);
            }
            else
            {
                extension = "html";
            }

            // we only accept direct requests to a bundle if they have a slash after the label
            String bundleInfo = null;
            if (info.startsWith("/") )
            {
                bundleInfo = info.substring(1);
            }
            if ( bundleInfo == null || bundleInfo.length() == 0 )
            {
                bundle = null;
                bundleRequested = false;
                pathInfo = null;
            }
            else
            {
                bundle = getBundle(bundleInfo);
                bundleRequested = true;
                pathInfo = bundleInfo;
            }
            request.setAttribute(BundlesServlet.class.getName(), this);
        }

    }

    static final RequestInfo getRequestInfo(final HttpServletRequest request)
    {
        return (RequestInfo)request.getAttribute( BundlesServlet.class.getName() );
    }

    private final PackageAdmin getPackageAdmin()
    {
        return ( PackageAdmin ) getService( PackageAdmin.class.getName() );
    }

    //---------- Bundle Installation handler (former InstallAction)

    private void installBundles( final HttpServletRequest request )
    throws IOException, ServletException {
        final Collection<Part> bundleItems = request.getParts();
        if ( bundleItems.isEmpty() ) {
            return;
        }

        final long uploadId;
        final String uidVal = request.getParameter(FIELD_UPLOADID);
        if ( uidVal != null ) {
            uploadId = Long.valueOf(uidVal);
        } else {
            uploadId = -1;
        }

        final String startItem = request.getParameter(FIELD_START);
        final String startLevelItem = request.getParameter(FIELD_STARTLEVEL);
        final String refreshPackagesItem = request.getParameter(FIELD_REFRESH_PACKAGES);
        final String parallelVersionItem = request.getParameter(FIELD_PARALLEL_VERSION);

        // default values
        // it exists
        int startLevel = -1;
        String bundleLocation = "inputstream:";

        // convert the start level value
        if ( startLevelItem != null ) {
            try {
                startLevel = Integer.parseInt( startLevelItem );
            } catch ( NumberFormatException nfe ) {
                Util.LOGGER.info("Cannot parse start level parameter {} to a number, not setting start level", startLevelItem );
            }
        }

        for(final Part part : bundleItems) {
            if (!FIELD_BUNDLEFILE.equals(part.getName())) {
                continue;
            }
            // write the bundle data to a temporary file to ease processing
            File tmpFile = null;
            try {
                // copy the data to a file for better processing
                tmpFile = File.createTempFile( "install", ".tmp" );
                try (final InputStream bundleStream = part.getInputStream()) {
                    Files.copy(bundleStream, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch ( final Exception e ) {
                Util.LOGGER.error("Problem accessing uploaded bundle file: {}", part.getSubmittedFileName(), e );

                // remove the tmporary file
                if ( tmpFile != null && tmpFile.exists()) {
                    tmpFile.delete();
                    tmpFile = null;
                }
            }

            // install or update the bundle now
            if ( tmpFile != null ) {
                // start, refreshPackages just needs to exist, don't care for value
                final boolean start = startItem != null;
                final boolean refreshPackages = refreshPackagesItem != null;
                final boolean parallelVersion = parallelVersionItem != null;

                bundleLocation = "inputstream:".concat(part.getSubmittedFileName());
                installBundle( bundleLocation, tmpFile, startLevel, start, refreshPackages, parallelVersion, uploadId);
            }
        }
    }

    private void installBundle( String location, File bundleFile, int startLevel, boolean start, boolean refreshPackages, boolean parallelVersion, final long uploadId)
    throws IOException
    {
        // try to get the bundle name & version, fail if none
        Map.Entry<String, String> snv = getSymbolicNameVersion( bundleFile );
        if ( snv == null || snv.getKey() == null ) {
            bundleFile.delete();
            throw new IOException( Constants.BUNDLE_SYMBOLICNAME + " header missing, cannot install bundle" );
        }
        String symbolicName = snv.getKey();
        String version = snv.getValue();

        // check for existing bundle first
        Bundle updateBundle = null;
        if ( Constants.SYSTEM_BUNDLE_SYMBOLICNAME.equals( symbolicName ) )
        {
            updateBundle = this.bundleContext.getBundle( 0 );
        }
        else
        {
            if ( uploadId != -1 ) {
                updateBundle = BundleContextUtil.getWorkingBundleContext(this.bundleContext).getBundle(uploadId);
            } else {
                Bundle[] bundles = BundleContextUtil.getWorkingBundleContext(this.bundleContext).getBundles();
                for ( int i = 0; i < bundles.length; i++ )
                {
                    boolean isSameBSN = (bundles[i].getSymbolicName() != null && bundles[i].getSymbolicName().equals( symbolicName ));
                    boolean isSameVersion = (bundles[i].getVersion() != null && bundles[i].getVersion().equals( Version.parseVersion(version) ));
                    if ( ( bundles[i].getLocation() != null && bundles[i].getLocation().equals( location ) )
                            || ( isSameBSN && !(parallelVersion && !isSameVersion) ) )
                    {
                        updateBundle = bundles[i];
                        break;
                    }
                }
            }
        }

        if ( updateBundle != null )
        {

            updateBackground( updateBundle, bundleFile, refreshPackages );

        }
        else
        {

            installBackground( bundleFile, location, startLevel, start, refreshPackages );

        }
    }

    private Map.Entry<String, String> getSymbolicNameVersion( File bundleFile ) {
        JarFile jar = null;
        try {
            jar = new JarFile( bundleFile );
            final Manifest m = jar.getManifest();
            if ( m != null ) {
                String sn = m.getMainAttributes().getValue( Constants.BUNDLE_SYMBOLICNAME );
                if ( sn != null ) {
                    final int paramPos = sn.indexOf(';');
                    if ( paramPos != -1 ) {
                        sn = sn.substring(0, paramPos);
                    }
                }
                final String v = m.getMainAttributes().getValue( Constants.BUNDLE_VERSION );
                return new AbstractMap.SimpleImmutableEntry<>(sn, v);
            }
        } catch ( final IOException ioe ) {
            Util.LOGGER.warn("Cannot extract symbolic name and version of bundle file {}", bundleFile, ioe );
        } finally {
            if ( jar != null ) {
                try {
                    jar.close();
                } catch ( IOException ioe ) {
                    // ignore
                }
            }
        }

        // fall back to "not found"
        return null;
    }

    private void installBackground( final File bundleFile, final String location, final int startlevel,
            final boolean doStart, final boolean refreshPackages ) {

        InstallHelper t = new InstallHelper( this, this.bundleContext, bundleFile, location, startlevel, doStart,
                refreshPackages );
        t.start();
    }

    private void updateBackground( final Bundle bundle, final File bundleFile, final boolean refreshPackages ) {
        UpdateHelper t = new UpdateHelper( this, bundle, bundleFile, refreshPackages );
        t.start();
    }
}

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


import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Parser;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.servlet.AbstractOsgiManagerPlugin;
import org.apache.felix.webconsole.servlet.RequestVariableResolver;
import org.osgi.framework.Bundle;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * LicenseServlet provides the licenses plugin that browses through the bundles,
 * searching for common license files.
 *
 */
public final class LicenseServlet extends AbstractOsgiManagerPlugin {

    public static final class Entry {
        String url;
        String path;
        String jar;
    }

    // common names (without extension) of the license files.
    private static final String LICENSE_FILES[] =
        { "README", "DISCLAIMER", "LICENSE", "NOTICE", "DEPENDENCIES" };

    static final String LABEL = "licenses";

    // templates
    private final String template;

    /**
     * Default constructor
     * @throws IOException
     */
    public LicenseServlet() throws IOException {
        // load templates
        template = readTemplateFile( "/templates/license.html" );
    }

    @Override
    protected String getLabel() {
        return LABEL;
    }

    @Override
    protected String getTitle() {
        return "%licenses.pluginTitle";
    }

    @Override
    protected String getCategory() {
        return CATEGORY_OSGI_MANAGER;
    }

    @Override
    protected String[] getCssReferences() {
        return new String[] { "/res/ui/license.css" };
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        final PathInfo pathInfo = PathInfo.parse( request.getPathInfo() );
        if ( pathInfo != null ) {
            if ( !sendResource( pathInfo, response ) ) {
                response.sendError( HttpServletResponse.SC_NOT_FOUND, "Cannot send data .." );
            }
        } else {
            super.doGet( request, response );
        }
    }

    @Override
    public void renderContent( HttpServletRequest request, HttpServletResponse res ) throws IOException {
        final Bundle[] bundles = this.bundleContext.getBundles();
        Util.sort( bundles, request.getLocale() );

        // prepare variables
        final RequestVariableResolver vars = this.getVariableResolver(request);
        vars.put( "__data__", getBundleData( bundles, request.getLocale() ));

        res.getWriter().print(template);
    }

    private static final String getBundleData(Bundle[] bundles, Locale locale) throws IOException {
        final StringWriter json = new StringWriter();
        final JSONWriter jw = new JSONWriter(json);
        jw.array();

        for (final Bundle bundle : bundles) {
            List<Entry> files = findResource(bundle, LICENSE_FILES);
            addLicensesFromHeader(bundle, files);
            if (!files.isEmpty()) { // has resources
                jw.object();
                jw.key( "bid").value( bundle.getBundleId() );
                jw.key( "title").value( Util.getName( bundle, locale ) );
                jw.key( "files");
                jw.object();
                jw.key("__res__");
                jw.array();
                Iterator<Entry> iter = files.iterator();
                while ( iter.hasNext() ) {
                    jw.object();
                    Entry entry = (Entry) iter.next();
                    jw.key("path").value(entry.path);
                    jw.key("url").value(entry.url);
                    if ( entry.jar != null ) {
                        jw.key("jar").value(entry.jar);
                    }
                    jw.endObject();
                }
                jw.endArray();
                jw.endObject();
                jw.endObject();
            }
        }

        jw.endArray();
        return json.toString();
    }

    private static final String getName( final String path ) {
        return path.substring( path.lastIndexOf( '/' ) + 1 );
    }

    private static final void addLicensesFromHeader(final Bundle bundle, final List<Entry> files) {
        String target = (String) bundle.getHeaders("").get("Bundle-License");
        if (target != null) {
            final Clause[] licenses = Parser.parseHeader(target);
            for (int i = 0; licenses != null && i < licenses.length; i++) {
                final String name = licenses[i].getName();
                if (!"<<EXTERNAL>>".equals(name)) {
                    final String link = licenses[i].getAttribute("link");
                    final String path;
                    final String url;
                    if (link == null) {
                        path = name;
                        url = getName(name);
                    } else {
                        path = link;
                        url = name;
                    }

                    // skip entry URL is bundle resources, but doesn't exists
                    if (path.indexOf("://") == -1 && null == bundle.getEntry(path))
                        continue;

                    final Entry entry = new Entry();
                    entry.path = path;
                    entry.url = url;

                    files.add(entry);
                }
            }
        }
    }

    private static final List<Entry> findResource( Bundle bundle, String[] patterns ) throws IOException {
        final List<Entry> files = new ArrayList<>();

        for ( int i = 0; i < patterns.length; i++ ) {
            Enumeration<URL> entries = bundle.findEntries( "/", patterns[i] + "*", true );
            if ( entries != null ) {
                while ( entries.hasMoreElements() ) {
                    URL url = entries.nextElement();
                    Entry entry = new Entry();
                    entry.path = url.getPath();
                    entry.url = getName( url.getPath() ) ;
                    files.add(entry);
                }
            }
        }

        Enumeration<URL> entries = bundle.findEntries( "/", "*.jar", true );
        if ( entries != null ) {
            while ( entries.hasMoreElements() ) {
                URL url = entries.nextElement();

                try(ZipInputStream zin = new ZipInputStream( url.openStream() )) {
                    for ( ZipEntry zentry = zin.getNextEntry(); zentry != null; zentry = zin.getNextEntry() ) {
                        String name = zentry.getName();

                        // ignore directory entries
                        if ( name.endsWith( "/" ) ) {
                            continue;
                        }

                        // cut off path and use file name for checking against patterns
                        name = name.substring( name.lastIndexOf( '/' ) + 1 );
                        for ( int i = 0; i < patterns.length; i++ ) {
                            if ( name.startsWith( patterns[i] ) ) {
                                Entry entry = new Entry();
                                entry.path = zentry.getName();
                                entry.url = getName( name ) ;
                                entry.jar = url.getPath();
                                files.add(entry);
                            }
                        }
                    }
                }
            }
        }

        return files;
    }

    private boolean sendResource( final PathInfo pathInfo, final HttpServletResponse response ) throws IOException {

        final String name = pathInfo.licenseFile.substring( pathInfo.licenseFile.lastIndexOf( '/' ) + 1 );
        boolean isLicense = false;
        for ( int i = 0; !isLicense && i < LICENSE_FILES.length; i++ ) {
            isLicense = name.startsWith( LICENSE_FILES[i] );
        }

        final Bundle bundle = this.bundleContext.getBundle( pathInfo.bundleId );
        if ( bundle == null ) {
            return false;
        }

        // prepare the response
        setNoCache( response );
        response.setContentType( "text/plain" );

        if ( pathInfo.innerJar == null ) {
            URL resource = bundle.getEntry( pathInfo.licenseFile );
            if ( resource == null) {
                resource = bundle.getResource( pathInfo.licenseFile );
            }

            if ( resource != null ){
                try( InputStream input = resource.openStream()) {
                    copy( input, response.getWriter() );
                }
                return true;
            }
        } else {
            // license is in a nested JAR
            final URL zipResource = bundle.getResource( pathInfo.innerJar );
            if ( zipResource != null ) {
                try(final ZipInputStream zin = new ZipInputStream( zipResource.openStream() )) {
                    for ( ZipEntry zentry = zin.getNextEntry(); zentry != null; zentry = zin.getNextEntry() ) {
                        if ( pathInfo.licenseFile.equals( zentry.getName() ) ) {
                            copy( zin, response.getWriter() );
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private void copy(final InputStream in, final Writer out) throws IOException {
        final Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
        final char[] buf = new char[4096];
        int l;
        while ((l = r.read(buf)) > 0 ) {
            out.write(buf, 0, l);
        }
    }

    // package private for unit testing of the parse method
    static class PathInfo {
        final long bundleId;
        final String innerJar;
        final String licenseFile;


        static PathInfo parse( final String pathInfo ) {
            if ( pathInfo == null || pathInfo.length() == 0 || !pathInfo.startsWith( "/" + LABEL + "/" ) )
            {
                return null;
            }

            // cut off label prefix including slashes around the label
            final String parts = pathInfo.substring( LABEL.length() + 2 );

            int slash = parts.indexOf( '/' );
            if ( slash <= 0 )
            {
                return null;
            }

            final long bundleId;
            try
            {
                bundleId = Long.parseLong( parts.substring( 0, slash ) );
                if ( bundleId < 0 )
                {
                    return null;
                }
            }
            catch ( NumberFormatException nfe )
            {
                // illegal bundle id
                return null;
            }

            final String innerJar;
            int jarSep = parts.indexOf( "!/", slash );
            if ( jarSep < 0 )
            {
                innerJar = null;
            }
            else
            {
                innerJar = parts.substring( slash, jarSep );
                slash = jarSep + 2; // ignore bang-slash
            }

            final String licenseFile = parts.substring( slash );

            return new PathInfo( bundleId, innerJar, licenseFile );
        }

        private PathInfo( final long bundleId, final String innerJar, final String licenseFile ) {
            this.bundleId = bundleId;
            this.innerJar = innerJar;
            this.licenseFile = licenseFile;
        }
    }
}

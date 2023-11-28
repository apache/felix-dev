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
package org.apache.felix.metatype.internal.l10n;


import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.Bundle;
import org.osgi.service.metatype.MetaTypeService;


/**
 * The <code>BundleResources</code>
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class BundleResources
{

    private final Bundle bundle;
    private volatile long bundleLastModified;

    private final Map<String, Resources> resourcesByLocale = new ConcurrentHashMap<>();

    private static final Map<Long, BundleResources> resourcesByBundle = new ConcurrentHashMap<>();


    public static Resources getResources( final Bundle bundle, final String basename, final String locale )
    {
        // the bundle has been uninstalled, ensure removed from the cache
        // and return null (e.g. no resources now)
        if ( bundle.getState() == Bundle.UNINSTALLED )
        {
            resourcesByBundle.remove( bundle.getBundleId() );
            return null;
        }

        // else check whether we know the bundle already
        BundleResources bundleResources = resourcesByBundle.get( bundle.getBundleId() );

        if ( bundleResources == null )
        {
            bundleResources = new BundleResources( bundle );
            resourcesByBundle.put( bundle.getBundleId(), bundleResources );
        }

        return bundleResources.getResources( basename, locale );
    }


    public static void clearResourcesCache()
    {
        resourcesByBundle.clear();
    }


    private BundleResources( Bundle bundle )
    {
        this.bundle = bundle;
        this.bundleLastModified = bundle.getLastModified();
    }


    private boolean isUpToDate()
    {
        return bundle.getState() != Bundle.UNINSTALLED && bundleLastModified >= bundle.getLastModified();
    }


    private Resources getResources( String basename, String locale )
    {
        // ensure locale - use VM default locale if null
        if ( locale == null )
        {
            locale = Locale.getDefault().toString();
        }

        final String key = basename + "-" + locale;

        // check the cache, if the bundle has not changed
        if ( isUpToDate() )
        {
            Resources res = resourcesByLocale.get( key );
            if ( res != null )
            {
                return res;
            }
        }
        else
        {
            // otherwise clear the cache and update last modified
            resourcesByLocale.clear();
            this.bundleLastModified = bundle.getLastModified();
        }

        // get the list of potential resource names files
        Properties parentProperties = null;
        List<String> resList = createResourceList( locale );
        for ( final String tmpLocale : resList)
        {
            final String tmpKey = basename + "-" + tmpLocale;
            Resources res = resourcesByLocale.get( tmpKey );
            if ( res != null )
            {
                parentProperties = res.getResources();
            }
            else
            {
                Properties props = loadProperties( basename, tmpLocale, parentProperties );
                res = new Resources( tmpLocale, props );
                resourcesByLocale.put( tmpKey, res );
                parentProperties = props;
            }
        }

        // just return from the cache again
        return resourcesByLocale.get( key );
    }


    private Properties loadProperties( String basename, String locale, Properties parentProperties )
    {
        String resourceName = basename;
        if ( locale != null && locale.length() > 0 )
        {
            resourceName += "_" + locale;
        }
        resourceName += ".properties";

        Properties props = new Properties( parentProperties );
        // FELIX-5173 - allow the resource to be provided by fragments as well...
        URL resURL = bundle.getResource( resourceName );

        // FELIX-607 backwards compatibility, support
        if ( resURL == null )
        {
            // FELIX-5173 - allow the resource to be provided by fragments as well...
            resURL = bundle.getResource( MetaTypeService.METATYPE_DOCUMENTS_LOCATION + "/" + resourceName );
        }

        if ( resURL != null )
        {
            try (final InputStream ins = resURL.openStream())
            {
                props.load( ins );
            }
            catch ( IOException ex )
            {
                // File doesn't exist, just continue loop
            }
        }

        return props;
    }


    private List<String> createResourceList( final String locale )
    {
        final List<String> result = new ArrayList<>( 4 );

        StringTokenizer tokens;
        final StringBuilder tempLocale = new StringBuilder();

        result.add( tempLocale.toString() );

        if ( locale != null && locale.length() > 0 )
        {
            tokens = new StringTokenizer( locale, "_" );
            while ( tokens.hasMoreTokens() )
            {
                if ( tempLocale.length() > 0 )
                {
                    tempLocale.append( "_" );
                }
                tempLocale.append( tokens.nextToken() );
                result.add( tempLocale.toString() );
            }
        }
        return result;
    }
}

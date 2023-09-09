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
package org.apache.felix.webconsole.internal;


import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * The <code>Util</code> class contains various utility methods used internally
 * by the web console implementation and the build-in plugins.
 */
public class Util {

    /**
     * Return a display name for the given <code>bundle</code>:
     * <ol>
     * <li>If the bundle has a non-empty <code>Bundle-Name</code> manifest
     * header that value is returned.</li>
     * <li>Otherwise the symbolic name is returned if set</li>
     * <li>Otherwise the bundle's location is returned if defined</li>
     * <li>Finally, as a last resort, the bundles id is returned</li>
     * </ol>
     *
     * @param bundle the bundle which name to retrieve
     * @param locale the locale, in which the bundle name is requested
     * @return the bundle name - see the description of the method for more details.
     */
    public static String getName( Bundle bundle, Locale locale )
    {
        final String loc = locale == null ? null : locale.toString();
        String name = ( String ) bundle.getHeaders( loc ).get( Constants.BUNDLE_NAME );
        if ( name == null || name.length() == 0 )
        {
            name = bundle.getSymbolicName();
            if ( name == null )
            {
                name = bundle.getLocation();
                if ( name == null )
                {
                    name = String.valueOf( bundle.getBundleId() );
                }
            }
        }
        return name;
    }

    /**
     * Returns the value of the header or the empty string if the header
     * is not available.
     *
     * @param bundle the bundle which header to retrieve
     * @param headerName the name of the header to retrieve
     * @return the header or empty string if it is not set
     */
    public static String getHeaderValue( Bundle bundle, String headerName )
    {
        Object value = bundle.getHeaders().get(headerName);
        if ( value != null )
        {
            return value.toString();
        }
        return "";
    }

    /**
     * Orders the bundles according to their name as returned by
     * {@link #getName(Bundle, Locale)}, with the exception that the system bundle is
     * always place as the first entry. If two bundles have the same name, they
     * are ordered according to their version. If they have the same version,
     * the bundle with the lower bundle id comes before the other.
     *
     * @param bundles the bundles to sort
     * @param locale the locale, used to obtain the localized bundle name
     */
    public static void sort( Bundle[] bundles, Locale locale )
    {
        Arrays.sort( bundles, new BundleNameComparator( locale ) );
    }

    /**
     * This method expects a locale string in format language_COUNTRY, or
     * language. The method will determine which is the correct form of locale
     * string and construct a <code>Locale</code> object.
     *
     * @param locale the locale string, if <code>null</code> - default locale is
     *          returned
     * @return a locale object
     * @see Locale
     */
    public static final Locale parseLocaleString(String locale)
    {
        if (locale == null)
        {
            return Locale.getDefault();
        }
        int idx = locale.indexOf('_');
        String language;
        String country = "";
        if (idx < 0)
        { // "en"
            language = locale;
        }
        else
        { // "en_US"
            language = locale.substring(0, idx); // "en"
            idx++; // "_"
            int last = locale.indexOf('_', idx); // "US"
            if (last < 0)
            {
                last = locale.length();
            }
            country = locale.substring(idx, last);
        }
        return new Locale(language, country);
    }

    private static final class BundleNameComparator implements Comparator<Bundle>
    {
        private final Locale locale;


        BundleNameComparator( final Locale locale ) {
            this.locale = locale;
        }

        public int compare( Bundle b1, Bundle b2 )
        {

            // the same bundles
            if ( b1 == b2 || b1.getBundleId() == b2.getBundleId() )
            {
                return 0;
            }

            // special case for system bundle, which always is first
            if ( b1.getBundleId() == 0 )
            {
                return -1;
            }
            else if ( b2.getBundleId() == 0 )
            {
                return 1;
            }

            // compare the symbolic names
            int snComp = Util.getName( b1, locale ).compareToIgnoreCase( Util.getName( b2, locale ) );
            if ( snComp != 0 )
            {
                return snComp;
            }

            // same names, compare versions
            Version v1 = Version.parseVersion( ( String ) b1.getHeaders().get( Constants.BUNDLE_VERSION ) );
            Version v2 = Version.parseVersion( ( String ) b2.getHeaders().get( Constants.BUNDLE_VERSION ) );
            int vComp = v1.compareTo( v2 );
            if ( vComp != 0 )
            {
                return vComp;
            }

            // same version ? Not really, but then, we compare by bundle id
            if ( b1.getBundleId() < b2.getBundleId() )
            {
                return -1;
            }

            // b1 id must be > b2 id because equality is already checked
            return 1;
        }
    }

    public static void sendJsonOk(final HttpServletResponse response) throws IOException {
        response.setContentType( "application/json" );
        response.setCharacterEncoding( "UTF-8" );
        response.getWriter().print( "{ \"status\": true }" );
    }

   /**
     * Sets response headers to force the client to not cache the response
     * sent back. This method must be called before the response is committed
     * otherwise it will have no effect.
     * <p>
     * This method sets the <code>Cache-Control</code>, <code>Expires</code>,
     * and <code>Pragma</code> headers.
     *
     * @param response The response for which to set the cache prevention
     */
    public static void setNoCache(final HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Cache-Control", "must-revalidate");
        response.addHeader("Cache-Control", "max-age=0");
        response.setHeader("Expires", "Thu, 01 Jan 1970 01:00:00 GMT");
        response.setHeader("Pragma", "no-cache");
    }

    public static String getStringProperty( final ServiceReference<?> service, final String propertyName ) {
        final Object property = service.getProperty( propertyName );
        if ( property instanceof String ) {
            return ( String ) property;
        }
        return null;
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
     */
    public static void sendRedirect(final HttpServletRequest request, final HttpServletResponse response, String redirectUrl) 
    throws IOException {
        // check for relative URL
        if ( !redirectUrl.startsWith("/") ) {
            String base = request.getContextPath() + request.getServletPath() + request.getPathInfo();
            int i = base.lastIndexOf('/');
            if (i > -1) {
                base = base.substring(0, i);
            } else {
                i = base.indexOf(':');
                base = (i > -1) ? base.substring(i + 1, base.length()) : "";
            }
            if (!base.startsWith("/")) {
                base = '/' + base;
            }
            redirectUrl = base + '/' + redirectUrl;

        }
        response.sendRedirect(redirectUrl);
    }

    @SuppressWarnings("rawtypes")
    public static String[] toStringArray( final Object value ) {
        if ( value instanceof String ) {
            return new String[] { ( String ) value };
        } else if ( value != null ) {
            final Collection col;
            if ( value.getClass().isArray() ) {
                col = Arrays.asList( ( Object[] ) value );
            } else if ( value instanceof Collection ) {
                col = ( Collection ) value;
            } else {
                col = null;
            }

            if ( col != null && !col.isEmpty() ) {
                final String[] entries = new String[col.size()];
                int i = 0;
                for (final Iterator cli = col.iterator(); cli.hasNext(); i++ )  {
                    entries[i] = String.valueOf( cli.next() );
                }
                return entries;
            }
        }

        return null;
    }

    /**
     * This method will stringify a Java object. It is mostly used to print the values
     * of unknown properties. This method will correctly handle if the passed object
     * is array and will property display it.
     *
     * If the value is byte[] the elements are shown as Hex
     *
     * @param value the value to convert
     * @return the string representation of the value
     */
    public static final String toString(Object value) {
        if (value == null) {
            return "n/a";
        } else if (value.getClass().isArray()) {
            final StringBuilder sb = new StringBuilder();
            int len = Array.getLength(value);
            sb.append('[');

            for(int i = 0; i < len; ++i) {
                final Object element = Array.get(value, i);
                if (element instanceof Byte) {
                    sb.append("0x");
                    final String x = Integer.toHexString(((Byte)element).intValue() & 255);
                    if (1 == x.length()) {
                        sb.append('0');
                    }

                    sb.append(x);
                } else {
                    sb.append(toString(element));
                }

                if (i < len - 1) {
                    sb.append(", ");
                }
            }

            return sb.append(']').toString();
        } else {
            return value.toString();
        }
     }
}
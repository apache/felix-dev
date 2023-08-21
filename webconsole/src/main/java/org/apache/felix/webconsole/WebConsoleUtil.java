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
package org.apache.felix.webconsole;


import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.servlet.ServletRequestContext;
import org.apache.felix.webconsole.servlet.RequestVariableResolver;


/**
 * The <code>WebConsoleUtil</code> provides various utility methods for use
 * by Web Console plugins.
 */
public final class WebConsoleUtil
{

    private WebConsoleUtil()
    {
        /* no instantiation */
    }

    /**
     * Returns the {@link VariableResolver} for the given request.
     * <p>
     * If no resolver has yet be created for the requests, an instance of the
     * {@link DefaultVariableResolver} is created with preset properties,
     * placed into the request and returned. The preset properties are
     * <code>appRoot</code> set to the value of the
     * {@link WebConsoleConstants#ATTR_APP_ROOT} request attribute and
     * <code>pluginRoot</code> set to the value of the
     * {@link WebConsoleConstants#ATTR_PLUGIN_ROOT} request attribute.
     * <p>
     * <b>Note</b>: An object not implementing the {@link VariableResolver}
     * interface already stored as the
     * {@link WebConsoleConstants#ATTR_CONSOLE_VARIABLE_RESOLVER} attribute
     * will silently be replaced by the {@link DefaultVariableResolver}
     * instance.
     *
     * @param request The request whose attribute is returned (or set)
     *
     * @return The {@link VariableResolver} for the given request.
     * @deprecated Use the {@link getRequestVariableResolver} instead.
     */
    @Deprecated
    public static VariableResolver getVariableResolver( final ServletRequest request ) {
        final Object resolverObj = request.getAttribute( WebConsoleConstants.ATTR_CONSOLE_VARIABLE_RESOLVER );
        if ( resolverObj instanceof VariableResolver ) {
            return ( VariableResolver ) resolverObj;
        }

        final DefaultVariableResolver resolver = new DefaultVariableResolver();
        resolver.put( "appRoot", (String) request.getAttribute( WebConsoleConstants.ATTR_APP_ROOT ) );
        resolver.put( "pluginRoot", (String) request.getAttribute( WebConsoleConstants.ATTR_PLUGIN_ROOT ) );
        setVariableResolver( request, resolver );
        return resolver;
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
    public static RequestVariableResolver getRequestVariableResolver( final ServletRequest request) {
        return (RequestVariableResolver) request.getAttribute( RequestVariableResolver.REQUEST_ATTRIBUTE );
    }

    /**
     * Sets the {@link VariableResolver} as the
     * {@link WebConsoleConstants#ATTR_CONSOLE_VARIABLE_RESOLVER}
     * attribute in the given request. An attribute of that name already
     * existing is silently replaced.
     *
     * @param request The request whose attribute is set
     * @param resolver The {@link VariableResolver} to place into the request
     * @deprecated Use the {@link RequestVaraibleResolver} instead.
     */
    @Deprecated
    public static void setVariableResolver( final ServletRequest request, final VariableResolver resolver )  {
        request.setAttribute( WebConsoleConstants.ATTR_CONSOLE_VARIABLE_RESOLVER, resolver );
    }


    /**
     * An utility method, that is used to filter out simple parameter from file
     * parameter when multipart transfer encoding is used.
     *
     * This method processes the request and sets a request attribute
     * {@link AbstractWebConsolePlugin#ATTR_FILEUPLOAD}. The attribute value is a {@link Map}
     * where the key is a String specifying the field name and the value
     * is a {@link org.apache.commons.fileupload.FileItem}.
     *
     * @param request the HTTP request coming from the user
     * @param name the name of the parameter
     * @return if not multipart transfer encoding is used - the value is the
     *  parameter value or <code>null</code> if not set. If multipart is used,
     *  and the specified parameter is field - then the value of the parameter
     *  is returned.
     * @deprecated Use the Servlet API for uploads
     */
    @Deprecated
    public static final String getParameter( final HttpServletRequest request, final String name ) {
        // just get the parameter if not a multipart/form-data POST
        if ( !FileUploadBase.isMultipartContent( new ServletRequestContext( request ) ) ) {
            return request.getParameter( name );
        }

        // check, whether we already have the parameters
        @SuppressWarnings("unchecked")
        Map<String, FileItem[]> params = ( Map<String, FileItem[]> ) request.getAttribute( AbstractWebConsolePlugin.ATTR_FILEUPLOAD );
        if ( params == null ) {
            // parameters not read yet, read now
            // Create a factory for disk-based file items
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setSizeThreshold( 256000 );
            // See https://issues.apache.org/jira/browse/FELIX-4660
            final Object repo = request.getAttribute( AbstractWebConsolePlugin.ATTR_FILEUPLOAD_REPO );
            if ( repo instanceof File ) {
                factory.setRepository( (File) repo );
            }

            // Create a new file upload handler
            ServletFileUpload upload = new ServletFileUpload( factory );
            upload.setSizeMax( -1 );
            upload.setFileCountMax(50);

            // Parse the request
            params = new HashMap<>();
            try {
                final List<FileItem> items = upload.parseRequest( request );
                for(final FileItem fi : items) {
                    FileItem[] current = ( FileItem[] ) params.get( fi.getFieldName() );
                    if ( current == null ) {
                        current = new FileItem[] { fi };
                    } else {
                        FileItem[] newCurrent = new FileItem[current.length + 1];
                        System.arraycopy( current, 0, newCurrent, 0, current.length );
                        newCurrent[current.length] = fi;
                        current = newCurrent;
                    }
                    params.put( fi.getFieldName(), current );
                }
            } catch ( FileUploadException fue ) {
                // fail
                return null;
            }
            request.setAttribute( AbstractWebConsolePlugin.ATTR_FILEUPLOAD, params );
        }

        final FileItem[] param = ( FileItem[] ) params.get( name );
        if ( param != null ) {
            for ( int i = 0; i < param.length; i++ ) {
                if ( param[i].isFormField() ) {
                    return param[i].getString();
                }
            }
        }

        // no valid string parameter, fail
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
    public static final void sendRedirect(final HttpServletRequest request,
            final HttpServletResponse response,
            String redirectUrl) throws IOException {
        // check for relative URL
        if ( !redirectUrl.startsWith("/") ) { //$NON-NLS-1$
            String base = request.getContextPath() + request.getServletPath() + request.getPathInfo();
            int i = base.lastIndexOf('/');
            if (i > -1) {
                base = base.substring(0, i);
            } else {
                i = base.indexOf(':');
                base = (i > -1) ? base.substring(i + 1, base.length()) : ""; //$NON-NLS-1$
            }
            if (!base.startsWith("/")) { //$NON-NLS-1$
                base = '/' + base;
            }
            redirectUrl = base + '/' + redirectUrl;

        }
        response.sendRedirect(redirectUrl);
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
    public static final void setNoCache(final HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
        response.addHeader("Cache-Control", "no-store"); //$NON-NLS-1$ //$NON-NLS-2$
        response.addHeader("Cache-Control", "must-revalidate"); //$NON-NLS-1$ //$NON-NLS-2$
        response.addHeader("Cache-Control", "max-age=0"); //$NON-NLS-1$ //$NON-NLS-2$
        response.setHeader("Expires", "Thu, 01 Jan 1970 01:00:00 GMT"); //$NON-NLS-1$ //$NON-NLS-2$
        response.setHeader("Pragma", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Escapes HTML special chars like: &lt;&gt;&amp;\r\n and space
     *
     *
     * @param text the text to escape
     * @return the escaped text
     * @deprecated It is better to use specialized encoders instead
     */
    @Deprecated
    public static final String escapeHtml(String text) {
        StringBuilder sb = new StringBuilder(text.length() * 4 / 3);
        char ch, oldch = '_';
        for (int i = 0; i < text.length(); i++) {
            switch (ch = text.charAt(i))
            {
            case '<':
                sb.append("&lt;"); //$NON-NLS-1$
                break;
            case '>':
                sb.append("&gt;"); //$NON-NLS-1$
                break;
            case '&':
                sb.append("&amp;"); //$NON-NLS-1$
                break;
            case ' ':
                sb.append("&nbsp;"); //$NON-NLS-1$
                break;
            case '\'':
                sb.append("&apos;"); //$NON-NLS-1$
                break;
            case '"':
                sb.append("&quot;"); //$NON-NLS-1$
                break;
            case '\r':
            case '\n':
                if (oldch != '\r' && oldch != '\n') // don't add twice <br>
                    sb.append("<br/>\n"); //$NON-NLS-1$
                break;
            default:
                sb.append(ch);
            }
            oldch = ch;
        }

        return sb.toString();
    }

    /**
     * Retrieves a request parameter and converts it to int.
     *
     * @param request the HTTP request
     * @param name the name of the request parameter
     * @param _default the default value returned if the parameter is not set or is not a valid integer.
     * @return the request parameter if set and is valid integer, or the default value
     */
    public static final int getParameterInt(HttpServletRequest request, String name,
            int _default)
    {
        int ret = _default;
        String param = request.getParameter(name);
        try
        {
            if (param != null)
                ret = Integer.parseInt(param);
        }
        catch (NumberFormatException nfe)
        {
            // don't care, will return default
        }

        return ret;
    }

    /**
     * Decode the given value expected to be URL encoded.
     * <p>
     * This method first tries to use the Java 1.4 method
     * <code>URLDecoder.decode(String, String)</code> method and falls back to
     * the now deprecated <code>URLDecoder.decode(String, String)</code>
     * which uses the platform character set to decode the string. This is
     * because the platforms before 1.4 and most notably some OSGi Execution
     * Environments (such as Minimum EE) do not provide the newer method.
     *
     * @param value the value to decode
     * @return the decoded string
     */
    @SuppressWarnings("deprecation")
    public static String urlDecode( final String value )
    {
        // shortcut for empty or missing values
        if ( value == null || value.length() == 0 )
        {
            return value;
        }

        try {
            return URLDecoder.decode( value, "UTF-8" );
        } catch (UnsupportedEncodingException e) {
            return URLDecoder.decode( value );
        }
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

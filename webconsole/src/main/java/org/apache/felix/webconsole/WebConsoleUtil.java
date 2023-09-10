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


import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.webconsole.internal.Util;


/**
 * The <code>WebConsoleUtil</code> provides various utility methods for use
 * by Web Console plugins.
 *
 * @deprecated Some of the methods can be replaced with direct Servlet API calls.
 */
@Deprecated
public final class WebConsoleUtil {

    private WebConsoleUtil() {
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
     * @deprecated Use the {@link org.apache.felix.webconsole.servlet.RequestVariableResolver} instead.
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
     * Sets the {@link VariableResolver} as the
     * {@link WebConsoleConstants#ATTR_CONSOLE_VARIABLE_RESOLVER}
     * attribute in the given request. An attribute of that name already
     * existing is silently replaced.
     *
     * @param request The request whose attribute is set
     * @param resolver The {@link VariableResolver} to place into the request
     * @deprecated Use the {@link org.apache.felix.webconsole.servlet.RequestVariableResolver} instead.
     */
    @Deprecated
    public static void setVariableResolver( final ServletRequest request, final VariableResolver resolver )  {
        request.setAttribute( WebConsoleConstants.ATTR_CONSOLE_VARIABLE_RESOLVER, resolver );
    }


    /**
     * An utility method to get a parameter value
     * @param request the HTTP request coming from the user
     * @param name the name of the parameter
     * @return The value or {@code null}.
     * @deprecated Use the Servlet API for uploads
     */
    @Deprecated
    public static final String getParameter( final HttpServletRequest request, final String name ) {
        return request.getParameter( name );
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
        response.setHeader("Cache-Control", "no-cache");
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Cache-Control", "must-revalidate");
        response.addHeader("Cache-Control", "max-age=0");
        response.setHeader("Expires", "Thu, 01 Jan 1970 01:00:00 GMT");
        response.setHeader("Pragma", "no-cache");
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
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '&':
                sb.append("&amp;");
                break;
            case ' ':
                sb.append("&nbsp;");
                break;
            case '\'':
                sb.append("&apos;");
                break;
            case '"':
                sb.append("&quot;");
                break;
            case '\r':
            case '\n':
                if (oldch != '\r' && oldch != '\n') // don't add twice <br>
                    sb.append("<br/>\n");
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
    public static String urlDecode( final String value ) {
        // shortcut for empty or missing values
        if ( value == null || value.length() == 0 ) {
            return value;
        }

        return URLDecoder.decode(value, StandardCharsets.UTF_8);
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
        return Util.toString(value);
     }
}

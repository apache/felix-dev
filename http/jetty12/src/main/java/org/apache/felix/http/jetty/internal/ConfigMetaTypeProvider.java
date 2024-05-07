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
package org.apache.felix.http.jetty.internal;

import java.io.InputStream;
import java.util.ArrayList;

import org.apache.felix.http.base.internal.HttpConfig;
import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.session.HouseKeeper;
import org.osgi.framework.Bundle;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeProvider;
import org.osgi.service.metatype.ObjectClassDefinition;

class ConfigMetaTypeProvider implements MetaTypeProvider
{

    private final Bundle bundle;

    public ConfigMetaTypeProvider(final Bundle bundle)
    {
        this.bundle = bundle;
    }

    /**
     * @see org.osgi.service.metatype.MetaTypeProvider#getLocales()
     */
    @Override
    public String[] getLocales()
    {
        return null;
    }

    /**
     * @see org.osgi.service.metatype.MetaTypeProvider#getObjectClassDefinition(java.lang.String, java.lang.String)
     */
    @Override
    public ObjectClassDefinition getObjectClassDefinition( String id, String locale )
    {
        if ( !JettyService.PID.equals( id ) )
        {
            return null;
        }

        final ArrayList<AttributeDefinition> adList = new ArrayList<>();

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HOST,
                "Host Name",
                "IP Address or Host Name of the interface to which HTTP and HTTPS bind. The default is " +
                   "\"0.0.0.0\" indicating all interfaces.",
                "0.0.0.0",
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HOST)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTP_ENABLE,
                "Enable HTTP",
                "Whether or not HTTP is enabled. Defaults to true thus HTTP enabled.",
                true,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTP_ENABLE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.HTTP_PORT,
                "HTTP Port",
                "Port to listen on for HTTP requests. Defaults to 8080.",
                8080,
                bundle.getBundleContext().getProperty(JettyConfig.HTTP_PORT)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.HTTP_TIMEOUT,
                "Connection Timeout",
                "Time limit for reaching an timeout specified in milliseconds. This property applies to both HTTP and HTTP connections. Defaults to 60 seconds.",
                60000,
                bundle.getBundleContext().getProperty(JettyConfig.HTTP_TIMEOUT)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTPS_ENABLE,
                "Enable HTTPS",
                "Whether or not HTTPS is enabled. Defaults to false thus HTTPS disabled.",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTPS_ENABLE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.HTTPS_PORT,
                "HTTPS Port",
                "Port to listen on for HTTPS requests. Defaults to 443.",
                443,
                bundle.getBundleContext().getProperty(JettyConfig.HTTPS_PORT)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_KEYSTORE,
                "Keystore",
                "Absolute Path to the Keystore to use for HTTPS. Only used if HTTPS is enabled in which case this property is required.",
                null,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_KEYSTORE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_KEYSTORE_PASSWORD,
                "Keystore Password",
                "Password to access the Keystore. Only used if HTTPS is enabled."));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_KEYSTORE_KEY_PASSWORD,
                "Key Password",
                "Password to unlock the secret key from the Keystore. Only used if HTTPS is enabled."));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_TRUSTSTORE,
                "Truststore",
                "Absolute Path to the Truststore to use for HTTPS. Only used if HTTPS is enabled.",
                null,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_TRUSTSTORE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_TRUSTSTORE_PASSWORD,
                "Truststore Password",
                "Password to access the Truststore. Only used if HTTPS is enabled."));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTPS_CLIENT_CERT,
                "Client Certificate",
                "Requirement for the Client to provide a valid certificate. Defaults to none.",
                AttributeDefinition.STRING,
                new String[] {"none"},
                0,
                new String[] {"No Client Certificate", "Client Certificate Wanted", "Client Certificate Needed"},
                new String[] {"none", "wants", "needs"},
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTPS_CLIENT_CERT)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTP_CONTEXT_PATH,
                "Context Path",
                "The Servlet Context Path to use for the Http Service. If this property is not configured it " +
                    "defaults to \"/\". This must be a valid path starting with a slash and not ending with a slash (unless it is the root context).",
                "/",
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTP_CONTEXT_PATH)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTP_MBEANS,
                "Register MBeans",
                "Whether or not to use register JMX MBeans from the servlet container (Jetty). If this is " +
                    "enabled Jetty Request and Connector statistics are also added. The default is to not enable JMX.",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTP_MBEANS)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_STATISTICS_HANDLER_ENABLE,
                "Enable Statistics",
                "Whether or not to use enable Statistics in the servlet container (Jetty).",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_STATISTICS_HANDLER_ENABLE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_SESSION_TIMEOUT,
                "Session Timeout",
                "Default lifetime of an HTTP session specified in a whole number of minutes. If the timeout is 0 or less, sessions will by default never timeout. The default is 0.",
                0,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_SESSION_TIMEOUT)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_THREADPOOL_MAX,
                "Thread Pool Max",
                "Maximum number of jetty threads. Using the default -1 uses Jetty's default (200).",
                -1,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_THREADPOOL_MAX)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_ACCEPTORS,
                "Acceptors",
                "Number of acceptor threads to use, or -1 for a default value. Acceptors accept new TCP/IP connections. If 0, then the selector threads are used to accept connections.",
                -1,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_ACCEPTORS)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_SELECTORS,
                "Selectors",
                "Number of selector threads, or <=0 for a default value. Selectors notice and schedule established connection that can make IO progress.",
                -1,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_SELECTORS)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_HEADER_BUFFER_SIZE,
                "Header Buffer Size",
                "Size of the buffer for request and response headers. Default is 16KB.",
                16384,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_HEADER_BUFFER_SIZE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_REQUEST_BUFFER_SIZE,
                "Request Buffer Size",
                "Size of the buffer for requests not fitting the header buffer. Default is 8KB.",
                8192,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_REQUEST_BUFFER_SIZE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_RESPONSE_BUFFER_SIZE,
                "Response Buffer Size",
                "Size of the buffer for responses. Default is 24KB.",
                24576,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_RESPONSE_BUFFER_SIZE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_MAX_FORM_SIZE,
                "Maximum Form Size",
                "Size of Body for submitted form content. Default is 200KB.",
                204800,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_MAX_FORM_SIZE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTP_PATH_EXCLUSIONS,
                "Path Exclusions",
                "Contains a list of context path prefixes. If a Web Application Bundle is started with a context path matching any " +
                    "of these prefixes, it will not be deployed in the servlet container.",
                AttributeDefinition.STRING,
                new String[] {"/system"},
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTP_PATH_EXCLUSIONS))));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_EXCLUDED_SUITES,
                "Excluded Cipher Suites",
                "List of cipher suites that should be excluded. Default is none.",
                AttributeDefinition.STRING,
                null,
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_EXCLUDED_SUITES))));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_INCLUDED_SUITES,
                "Included Cipher Suites",
                "List of cipher suites that should be included. Default is none.",
                AttributeDefinition.STRING,
                null,
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_INCLUDED_SUITES))));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_SEND_SERVER_HEADER,
                "Send Server Header",
                "If enabled, the server header is sent.",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_SEND_SERVER_HEADER)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_INCLUDED_PROTOCOLS,
                "Included Protocols",
                "List of SSL protocols to include by default. Protocols may be any supported by the Java " +
                    "platform such as SSLv2Hello, SSLv3, TLSv1, TLSv1.1, or TLSv1.2. Any listed protocol " +
                    "not supported is silently ignored. Default is none assuming to use any protocol enabled " +
                    "and supported on the platform.",
                AttributeDefinition.STRING,
                null,
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_INCLUDED_PROTOCOLS))));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_EXCLUDED_PROTOCOLS,
                "Excluded Protocols",
                "List of SSL protocols to exclude. This property further restricts the enabled protocols by " +
                    "explicitly disabling. Any protocol listed in both this property and the Included " +
                    "protocols property is excluded. Default is none such as to accept all protocols enabled " +
                    "on platform or explicitly listed by the Included protocols property.",
                AttributeDefinition.STRING,
                null,
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_EXCLUDED_PROTOCOLS))));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_PROXY_LOAD_BALANCER_CONNECTION_ENABLE,
                "Enable Proxy/Load Balancer Connection",
                "Whether or not the Proxy/Load Balancer Connection is enabled. Defaults to false thus disabled.",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_PROXY_LOAD_BALANCER_CONNECTION_ENABLE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_RENEGOTIATION_ALLOWED,
                "Renegotiation allowed",
                "Whether TLS renegotiation is allowed (true by default)",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_RENEGOTIATION_ALLOWED)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_SESSION_COOKIE_HTTP_ONLY,
                "Session Cookie httpOnly",
                "Session Cookie httpOnly (true by default)",
                true,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_SESSION_COOKIE_HTTP_ONLY)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_SESSION_COOKIE_SECURE,
                "Session Cookie secure",
                "Session Cookie secure (false by default)",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_SESSION_COOKIE_SECURE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_URI_COMPLIANCE_MODE,
                "Jetty URI compliance mode",
                "Jetty URI compliance mode (if not set, Jetty will configure a default)",
                null,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_URI_COMPLIANCE_MODE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_SERVLET_SESSION_ID_PATH_PARAMETER_NAME,
                "Session Id path parameter",
                "Defaults to jsessionid. If set to null or \"none\" no URL rewriting will be done.",
                "jsessionid",
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_SERVLET_SESSION_ID_PATH_PARAMETER_NAME)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_SERVLET_CHECK_REMOTE_SESSION_ENCODING,
                "Check remote session encoding",
                "If true, Jetty will add JSESSIONID parameter even when encoding external urls with calls to encodeURL() (true by default)",
                true,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_SERVLET_CHECK_REMOTE_SESSION_ENCODING)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_SERVLET_SESSION_COOKIE_NAME,
                "Session Cookie Name",
                "Session Cookie Name",
                "JSESSIONID",
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_SERVLET_SESSION_COOKIE_NAME)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_SERVLET_SESSION_DOMAIN,
                "Session Domain",
                "If this property is set, then it is used as the domain for session cookies. If it is not set, then no domain is specified for the session cookie. Default is none.",
                null,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_SERVLET_SESSION_DOMAIN)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_SERVLET_SESSION_PATH,
                "Session Path",
                "If this property is set, then it is used as the path for the session cookie. Default is context path.",
                null,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_SERVLET_SESSION_DOMAIN)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_SERVLET_SESSION_MAX_AGE,
                "Session Max Age",
                "Max age for the session cookie. Default is -1.",
                -1,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_SERVLET_SESSION_MAX_AGE)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_SESSION_SCAVENGING_INTERVAL,
                "Session Scavenging Interval",
                "Interval of session scavenging in seconds. Default is " + String.valueOf(HouseKeeper.DEFAULT_PERIOD_MS / 1000),
                HouseKeeper.DEFAULT_PERIOD_MS / 1000,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_SESSION_SCAVENGING_INTERVAL)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTP_SERVICE_NAME,
                "HTTP Service Name",
                "HTTP Service Name used in service filter to target specific HTTP instance. Default is null.",
                null,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTP_SERVICE_NAME)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_GZIP_HANDLER_ENABLE,
                "Enable GzipHandler",
                "Whether the server should use a server-wide gzip handler. Default is false.",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_GZIP_HANDLER_ENABLE)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_GZIP_MIN_GZIP_SIZE,
                "Gzip Min Size",
                String.format("The minimum response size to trigger dynamic compression. Default is %d.", GzipHandler.DEFAULT_MIN_GZIP_SIZE),
                GzipHandler.DEFAULT_MIN_GZIP_SIZE,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_GZIP_MIN_GZIP_SIZE)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_GZIP_INFLATE_BUFFER_SIZE,
                "Gzip Inflate Buffer Size",
                "The size in bytes of the buffer to inflate compressed request, or <= 0 for no inflation. Default is -1.",
                -1,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_GZIP_INFLATE_BUFFER_SIZE)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_GZIP_SYNC_FLUSH,
                "Gzip Sync Flush",
                "True if Deflater#SYNC_FLUSH should be used, else Deflater#NO_FLUSH will be used. Default is false.",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_GZIP_SYNC_FLUSH)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_GZIP_INCLUDED_METHODS,
                "Gzip Include Methods",
                "The additional http methods to include in compression. Default is none.",
                AttributeDefinition.STRING,
                null,
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_GZIP_INCLUDED_METHODS))));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_GZIP_EXCLUDED_METHODS,
                "Gzip Exclude Methods",
                "The additional http methods to exclude in compression. Default is none.",
                AttributeDefinition.STRING,
                null,
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_GZIP_EXCLUDED_METHODS))));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_GZIP_INCLUDED_PATHS,
                "Gzip Included Paths",
                "The additional path specs to include. Inclusion takes precedence over exclusion. Default is none.",
                AttributeDefinition.STRING,
                null,
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_GZIP_INCLUDED_PATHS))));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_GZIP_EXCLUDED_PATHS,
                "Gzip Excluded Paths",
                "The additional path specs to exclude. Inclusion takes precedence over exclusion. Default is none.",
                AttributeDefinition.STRING,
                null,
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_GZIP_EXCLUDED_PATHS))));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_GZIP_INCLUDED_MIME_TYPES,
                "Gzip Included Mime Types",
                "The included mime types. Inclusion takes precedence over exclusion. Default is none.",
                AttributeDefinition.STRING,
                null,
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_GZIP_INCLUDED_MIME_TYPES))));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_GZIP_EXCLUDED_MIME_TYPES,
                "Gzip Excluded Mime Types",
                "The excluded mime types. Inclusion takes precedence over exclusion. Default is none.",
                AttributeDefinition.STRING,
                null,
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_GZIP_EXCLUDED_MIME_TYPES))));
        adList.add(new AttributeDefinitionImpl(HttpConfig.PROP_INVALIDATE_SESSION,
                "Invalidate Container Session",
                "If this property is set, the container session is automatically validated.",
                HttpConfig.DEFAULT_INVALIDATE_SESSION,
                bundle.getBundleContext().getProperty(HttpConfig.PROP_INVALIDATE_SESSION)));
        adList.add(new AttributeDefinitionImpl(HttpConfig.PROP_CONTAINER_ADDED_ATTRIBUTE,
                "Attributes added by server.",
                "The attributes added by underlying session. Use this to invalidate session.",
                AttributeDefinition.STRING,
                new String[] {"org.eclipse.jetty.security.sessionCreatedSecure"},
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(HttpConfig.PROP_CONTAINER_ADDED_ATTRIBUTE))));
        adList.add(new AttributeDefinitionImpl(HttpConfig.PROP_UNIQUE_SESSION_ID,
                "Unique Session Id",
                "If this property is set, each http context gets a unique session id (derived from the container session).",
                HttpConfig.DEFAULT_UNIQUE_SESSION_ID,
                bundle.getBundleContext().getProperty(HttpConfig.PROP_UNIQUE_SESSION_ID)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_STOP_TIMEOUT, "Server stop timeout",
                "If not -1, stop timeout for the server in milliseconds.", -1L,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_STOP_TIMEOUT)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTP2_ENABLE,
                "Enable Http/2",
                "Whether to enable HTTP/2. Default is false.",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTP2_ENABLE)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_HTTP2_MAX_CONCURRENT_STREAMS,
                "Http/2 Max Concurrent Streams",
                "The max number of concurrent streams per connection. Default is 128.",
                128,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_HTTP2_MAX_CONCURRENT_STREAMS)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_HTTP2_INITIAL_STREAM_RECV_WINDOW,
                "Http/2 Initial Stream Recieve Window",
                "The initial stream receive window (client to server). Default is 524288.",
                524288,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_HTTP2_INITIAL_STREAM_RECV_WINDOW)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_HTTP2_INITIAL_SESSION_RECV_WINDOW,
                "Http/2 Initial Session Recieve Window",
                "The initial session receive window (client to server). Default is 1048576.",
                1048576,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_HTTP2_INITIAL_SESSION_RECV_WINDOW)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_ALPN_PROTOCOLS,
                "ALPN Protocols",
                "The ALPN protocols to consider. Default is h2, http/1.1.",
                AttributeDefinition.STRING,
                new String[] {"h2", "http/1.1"},
                2147483647,
                null, null,
                getStringArray(bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_ALPN_PROTOCOLS))));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_ALPN_DEFAULT_PROTOCOL,
                "ALPN Default Protocol",
                "The default protocol when negotiation fails. Default is http/1.1.",
                "http/1.1",
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_ALPN_DEFAULT_PROTOCOL)));

        // most important request logging attributes
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTP_REQUEST_LOG_FILE_PATH,
                "Request Log File Path",
                "The path to the log file which is receiving request log entries. If empty no request log file is created",
                null,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTP_REQUEST_LOG_FILE_PATH)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTP_REQUEST_LOG_FILE_FORMAT,
                "Request Log File Format",
                "The format of the request log file entries. Only relevant if 'Request Log File Path' is set. Valid placeholders are described in https://www.eclipse.org/jetty/documentation/jetty-11/operations-guide/index.html#og-module-requestlog",
                CustomRequestLog.NCSA_FORMAT,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTP_REQUEST_LOG_FILE_FORMAT)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTP_REQUEST_LOG_OSGI_ENABLE,
                "Enable SLF4J Request Logging",
                "Select to log requests through SLF4J logger with given name (on level INFO)",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTP_REQUEST_LOG_OSGI_ENABLE)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTP_REQUEST_LOG_OSGI_LOGGER_NAME,
                "SLF4J Request Log Logger Name",
                "The name of the SLF4J request logger. Only relevant if 'Enable SLF4J Request Logging' is checked.",
                SystemLogger.LOGGER.getName(),
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTP_REQUEST_LOG_OSGI_LOGGER_NAME)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_HTTP_REQUEST_LOG_FORMAT,
                "SLF4J Request Log Format",
                "The format of the request log entries. Only relevant if 'Enable SLF4J Request Logging' is checked. Valid placeholders are described in https://www.eclipse.org/jetty/documentation/jetty-11/operations-guide/index.html#og-module-requestlog",
                CustomRequestLog.NCSA_FORMAT,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_HTTP_REQUEST_LOG_FORMAT)));

        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JAKARTA_WEBSOCKET_ENABLE,
                "Enable Jakarta standard WebSocket support",
                "Whether to enable jakarta standard WebSocket support. Default is false.",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JAKARTA_WEBSOCKET_ENABLE)));
        adList.add(new AttributeDefinitionImpl(JettyConfig.FELIX_JETTY_WEBSOCKET_ENABLE,
                "Enable Jetty specific WebSocket support",
                "Whether to enable jetty specific WebSocket support. Default is false.",
                false,
                bundle.getBundleContext().getProperty(JettyConfig.FELIX_JETTY_WEBSOCKET_ENABLE)));
        return new ObjectClassDefinition()
        {

            private final AttributeDefinition[] attrs = adList
                .toArray(new AttributeDefinition[adList.size()]);

            @Override
            public String getName()
            {
                return "Apache Felix Jetty Based Http Service";
            }

            @Override
            public InputStream getIcon(int arg0)
            {
                return null;
            }

            @Override
            public String getID()
            {
                return JettyService.PID;
            }

            @Override
            public String getDescription()
            {
                return "Configuration for the embedded Jetty Servlet Container.";
            }

            @Override
            public AttributeDefinition[] getAttributeDefinitions(int filter)
            {
                return (filter == OPTIONAL) ? null : attrs;
            }
        };
    }

    private String [] getStringArray(final String value)
    {
        if ( value != null )
        {
            return value.trim().split(",");
        }
        return null;
    }

    private static class AttributeDefinitionImpl implements AttributeDefinition
    {

        private final String id;
        private final String name;
        private final String description;
        private final int type;
        private final String[] defaultValues;
        private final int cardinality;
        private final String[] optionLabels;
        private final String[] optionValues;

        /**
         * Constructor for password properties
         * @param id The id of the property
         * @param name The property name
         * @param description The property description
         */
        AttributeDefinitionImpl( final String id, final String name, final String description )
        {
            this( id, name, description, PASSWORD, (String[])null, 0, null, null, (String[])null );
        }

        AttributeDefinitionImpl( final String id, final String name, final String description, final String defaultValue, final String overrideValue )
        {
            this( id, name, description, STRING, defaultValue == null ? null : new String[] { defaultValue }, 0, null, null, overrideValue == null ? null : new String[] { overrideValue } );
        }

        AttributeDefinitionImpl( final String id, final String name, final String description, final long defaultValue, final String overrideValue )
        {
            this( id, name, description, LONG, new String[]
                { String.valueOf(defaultValue) }, 0, null, null, overrideValue == null ? null : new String[] { overrideValue } );
        }

        AttributeDefinitionImpl( final String id, final String name, final String description, final int defaultValue, final String overrideValue )
        {
            this( id, name, description, INTEGER, new String[]
                { String.valueOf(defaultValue) }, 0, null, null, overrideValue == null ? null : new String[] { overrideValue } );
        }

        AttributeDefinitionImpl( final String id, final String name, final String description, final boolean defaultValue, final String overrideValue )
        {
            this( id, name, description, BOOLEAN, new String[]
                { String.valueOf(defaultValue) }, 0, null, null, overrideValue == null ? null : new String[] { overrideValue } );
        }

        AttributeDefinitionImpl( final String id, final String name, final String description, final int type,
                final String[] defaultValues, final int cardinality, final String[] optionLabels,
                final String[] optionValues,
                final String overrideValue)
        {
            this(id, name, description, type, defaultValues, cardinality, optionLabels, optionValues, overrideValue == null ? null : new String[] { overrideValue });
        }

        AttributeDefinitionImpl( final String id, final String name, final String description, final int type,
            final String[] defaultValues, final int cardinality, final String[] optionLabels,
            final String[] optionValues,
            final String[] overrideValues)
        {
            this.id = id;
            this.name = name;
            this.description = description;
            this.type = type;
            if ( overrideValues != null )
            {
               this.defaultValues = overrideValues;
            }
            else
            {
                this.defaultValues = defaultValues;
            }
            this.cardinality = cardinality;
            this.optionLabels = optionLabels;
            this.optionValues = optionValues;
        }


        @Override
        public int getCardinality()
        {
            return cardinality;
        }


        @Override
        public String[] getDefaultValue()
        {
            return defaultValues;
        }


        @Override
        public String getDescription()
        {
            return description;
        }


        @Override
        public String getID()
        {
            return id;
        }


        @Override
        public String getName()
        {
            return name;
        }


        @Override
        public String[] getOptionLabels()
        {
            return optionLabels;
        }


        @Override
        public String[] getOptionValues()
        {
            return optionValues;
        }


        @Override
        public int getType()
        {
            return type;
        }


        @Override
        public String validate( String arg0 )
        {
            return null;
        }
    }
}

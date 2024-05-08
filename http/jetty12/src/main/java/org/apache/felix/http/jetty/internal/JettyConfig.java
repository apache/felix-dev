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
package org.apache.felix.http.jetty.internal;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.zip.Deflater;

import org.apache.felix.http.base.internal.HttpConfig;
import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.osgi.framework.BundleContext;

public final class JettyConfig
{
    /** Felix specific property to set the interface to listen on. Applies to both HTTP and HTTP */
    public static final String FELIX_HOST = "org.apache.felix.http.host";

    /** Standard OSGi port property for HTTP service */
    public static final String HTTP_PORT = "org.osgi.service.http.port";

    /** Standard OSGi port property for HTTPS service */
    public static final String HTTPS_PORT = "org.osgi.service.http.port.secure";

    /** Felix specific property to set http reaching timeout limit */
    public static final String HTTP_TIMEOUT = "org.apache.felix.http.timeout";

    /** Felix specific property to override the keystore file location. */
    public static final String FELIX_KEYSTORE = "org.apache.felix.https.keystore";
    private static final String OSCAR_KEYSTORE = "org.ungoverned.osgi.bundle.https.keystore";

    /** Felix specific property to override the keystore password. */
    public static final String FELIX_KEYSTORE_PASSWORD = "org.apache.felix.https.keystore.password";
    private static final String OSCAR_KEYSTORE_PASSWORD = "org.ungoverned.osgi.bundle.https.password";

    /** Felix specific property to override the keystore key password. */
    public static final String FELIX_KEYSTORE_KEY_PASSWORD = "org.apache.felix.https.keystore.key.password";
    private static final String OSCAR_KEYSTORE_KEY_PASSWORD = "org.ungoverned.osgi.bundle.https.key.password";

    /** Felix specific property to override the type of keystore (JKS). */
    public static final String FELIX_KEYSTORE_TYPE = "org.apache.felix.https.keystore.type";

    /** Felix specific property to control whether to enable HTTPS. */
    public static final String FELIX_HTTPS_ENABLE = "org.apache.felix.https.enable";
    private static final String OSCAR_HTTPS_ENABLE = "org.ungoverned.osgi.bundle.https.enable";

    /** Felix specific property to control whether to enable HTTP. */
    public static final String FELIX_HTTP_ENABLE = "org.apache.felix.http.enable";

    /** Felix specific property to override the truststore file location. */
    public static final String FELIX_TRUSTSTORE = "org.apache.felix.https.truststore";

    /** Felix specific property to override the truststore password. */
    public static final String FELIX_TRUSTSTORE_PASSWORD = "org.apache.felix.https.truststore.password";

    /** Felix specific property to override the type of truststore (JKS). */
    public static final String FELIX_TRUSTSTORE_TYPE = "org.apache.felix.https.truststore.type";

    /** Felix specific property to control whether to want or require HTTPS client certificates. Valid values are "none", "wants", "needs". Default is "none". */
    public static final String FELIX_HTTPS_CLIENT_CERT = "org.apache.felix.https.clientcertificate";

    /** Felix specific property to configure the session timeout in minutes (same session-timout in web.xml). Default is servlet container specific */
    public static final String FELIX_SESSION_TIMEOUT = "org.apache.felix.http.session.timeout";

    /** Felix specific property to control the maximum size of the jetty thread pool */
    public static final String FELIX_JETTY_THREADPOOL_MAX = "org.apache.felix.http.jetty.threadpool.max";

    /** Felix specific property to control the number of jetty acceptor threads */
    public static final String FELIX_JETTY_ACCEPTORS = "org.apache.felix.http.jetty.acceptors";

    /** Felix specific property to control the number of jetty selector threads */
    public static final String FELIX_JETTY_SELECTORS = "org.apache.felix.http.jetty.selectors";

    /** Felix specific property to configure the request buffer size. Default is 16KB (instead of Jetty's default of 4KB) */
    public static final String FELIX_JETTY_HEADER_BUFFER_SIZE = "org.apache.felix.http.jetty.headerBufferSize";

    /** Felix specific property to configure the request buffer size. Default is 8KB */
    public static final String FELIX_JETTY_REQUEST_BUFFER_SIZE = "org.apache.felix.http.jetty.requestBufferSize";

    /** Felix specific property to configure the request buffer size. Default is 24KB */
    public static final String FELIX_JETTY_RESPONSE_BUFFER_SIZE = "org.apache.felix.http.jetty.responseBufferSize";

    /** Felix specific property to configure the max form size. Default is 200KB */
    public static final String FELIX_JETTY_MAX_FORM_SIZE = "org.apache.felix.http.jetty.maxFormSize";

    /** Felix specific property to enable Jetty MBeans. Valid values are "true", "false". Default is false */
    public static final String FELIX_HTTP_MBEANS = "org.apache.felix.http.mbeans";

    /** Felix specific property to set the servlet context path of the Http Service */
    public static final String FELIX_HTTP_CONTEXT_PATH = "org.apache.felix.http.context_path";

    /** Felix specific property to set the list of path exclusions for Web Application Bundles */
    public static final String FELIX_HTTP_PATH_EXCLUSIONS = "org.apache.felix.http.path_exclusions";

    /** Felix specific property to configure the excluded cipher suites. @deprecated use {@link #FELIX_JETTY_EXCLUDED_SUITES} instead. */
    @Deprecated
    public static final String FELIX_JETTY_EXCLUDED_SUITES_OLD = "org.apache.felix.https.jetty.cipersuites.excluded";
    /** Felix specific property to configure the excluded cipher suites */
    public static final String FELIX_JETTY_EXCLUDED_SUITES = "org.apache.felix.https.jetty.ciphersuites.excluded";

    /** Felix specific property to configure the included cipher suites. @deprecated use {@link #FELIX_JETTY_INCLUDED_SUITES} instead. */
    @Deprecated
    public static final String FELIX_JETTY_INCLUDED_SUITES_OLD = "org.apache.felix.https.jetty.cipersuites.included";
    /** Felix specific property to configure the included cipher suites. */
    public static final String FELIX_JETTY_INCLUDED_SUITES = "org.apache.felix.https.jetty.ciphersuites.included";

    /** Felix specific property to specify whether a server header should be sent (defaults to true) */
    public static final String FELIX_JETTY_SEND_SERVER_HEADER = "org.apache.felix.http.jetty.sendServerHeader";

    /** Felix specific property to configure the included protocols */
    public static final String FELIX_JETTY_INCLUDED_PROTOCOLS = "org.apache.felix.https.jetty.protocols.included";

    /** Felix specific property to configure the excluded protocols */
    public static final String FELIX_JETTY_EXCLUDED_PROTOCOLS = "org.apache.felix.https.jetty.protocols.excluded";

    /** Felix specific properties to be able to disable renegotiation protocol for TLSv1 */
    public static final String FELIX_JETTY_RENEGOTIATION_ALLOWED = "org.apache.felix.https.jetty.renegotiateAllowed";

    /** Felix specific property to control whether to enable Proxy/Load Balancer Connection */
    public static final String FELIX_PROXY_LOAD_BALANCER_CONNECTION_ENABLE = "org.apache.felix.proxy.load.balancer.connection.enable";

    /** Felix specific property to configure the session cookie httpOnly flag */
    public static final String FELIX_JETTY_SESSION_COOKIE_HTTP_ONLY = "org.apache.felix.https.jetty.session.cookie.httpOnly";

    /** Felix specific property to configure the session cookie secure flag */
    public static final String FELIX_JETTY_SESSION_COOKIE_SECURE = "org.apache.felix.https.jetty.session.cookie.secure";

    /** Felix specific property to configure session id path parameter*/
    public static final String FELIX_JETTY_SERVLET_SESSION_ID_PATH_PARAMETER_NAME = "org.eclipse.jetty.servlet.SessionIdPathParameterName";

    /** Felix specific property to configure whether JSESSIONID parameter will be added when encoding external URLs */
    public static final String FELIX_JETTY_SERVLET_CHECK_REMOTE_SESSION_ENCODING = "org.eclipse.jetty.servlet.CheckingRemoteSessionIdEncoding";

    /** Felix specific property to configure session cookie name */
    public static final String FELIX_JETTY_SERVLET_SESSION_COOKIE_NAME = "org.eclipse.jetty.servlet.SessionCookie";

    /** Felix specific property to configure session domain */
    public static final String FELIX_JETTY_SERVLET_SESSION_DOMAIN = "org.eclipse.jetty.servlet.SessionDomain";

    /**  Felix specific property to configure session path */
    public static final String FELIX_JETTY_SERVLET_SESSION_PATH = "org.eclipse.jetty.servlet.SessionPath";

    /**  Felix specific property to configure session max age */
    public static final String FELIX_JETTY_SERVLET_SESSION_MAX_AGE = "org.eclipse.jetty.servlet.MaxAge";

    /**  Felix specific property to configure the uri compliance mode (https://eclipse.dev/jetty/documentation/jetty-12/programming-guide/index.html#pg-server-compliance-uri) */
    public static final String FELIX_JETTY_URI_COMPLIANCE_MODE = "org.eclipse.jetty.UriComplianceMode";

    /** Felix specific property to configure session scavenging interval in Seconds */
    public static final String FELIX_JETTY_SESSION_SCAVENGING_INTERVAL = "org.eclipse.jetty.servlet.SessionScavengingInterval";

    /** Felix specific property to set HTTP instance name. */
    public static final String FELIX_HTTP_SERVICE_NAME = "org.apache.felix.http.name";

    /** Felix specific property to configure a filter for RequestLog services */
    public static final String FELIX_HTTP_REQUEST_LOG_FILTER = "org.apache.felix.http.requestlog.filter";

    /** Felix specific property to enable request logging through SLF4J name "org.apache.felix.http" (with level INFO) */
    public static final String FELIX_HTTP_REQUEST_LOG_OSGI_ENABLE = "org.apache.felix.http.requestlog.osgi.enable";

    /** Felix specific property to specify the published "name" property of the SLF4J Service-based Request Log service. Allows server configs to filter on specific log services. */
    public static final String FELIX_HTTP_REQUEST_LOG_OSGI_SERVICE_NAME = "org.apache.felix.http.requestlog.osgi.name";

    /** Felix specific property to specify the logger name of the SLF4J Service-based Request Log service. Log entries are emitted with level INFO. */
    public static final String FELIX_HTTP_REQUEST_LOG_OSGI_LOGGER_NAME = "org.apache.felix.http.requestlog.osgi.logger.name";

    /** Felix specific property to control the format of the log messages generated by the OSGi Log Service-based request log. */
    public static final String FELIX_HTTP_REQUEST_LOG_FORMAT = "org.apache.felix.http.requestlog.osgi.format";

    /** Felix specific property to enable request logging to a file and provide the path to that file. Default is null meaning that the file log is disabled. */
    public static final String FELIX_HTTP_REQUEST_LOG_FILE_PATH = "org.apache.felix.http.requestlog.file.path";

    /** Felix specific property to specify the published "name" property of the file-based RequestLog service. Allows server configs to filter on specific log services. */
    public static final String FELIX_HTTP_REQUEST_LOG_FILE_SERVICE_NAME = "org.apache.felix.http.requestlog.file.name";

    /** Felix specific property to enable file request logging to be asynchronous */
    public static final String FELIX_HTTP_REQUEST_LOG_FILE_ASYNC = "org.apache.felix.http.requestlog.file.async";

    /** Felix specific property to enable request logging to append to the log file rather than overwriting */
    public static final String FELIX_HTTP_REQUEST_LOG_FILE_APPEND = "org.apache.felix.http.requestlog.file.append";

    /** Felix specific property to specify the number of days the request log file is retained */
    public static final String FELIX_HTTP_REQUEST_LOG_FILE_RETAIN_DAYS = "org.apache.felix.http.requestlog.file.retaindays";

    /** Felix specific property to specify the date format in request log file names */
    public static final String FELIX_HTTP_REQUEST_LOG_FILE_FILENAME_DATE_FORMAT = "org.apache.felix.http.requestlog.file.dateformat";

    /** Felix specific property to ignore matching paths in the request log file */
    public static final String FELIX_HTTP_REQUEST_LOG_FILE_IGNORE_PATHS = "org.apache.felix.http.requestlog.file.ignorepaths";

    /** Felix specific property to specify the output format for the request log file */
    public static final String FELIX_HTTP_REQUEST_LOG_FILE_FORMAT = "org.apache.felix.http.requestlog.file.format";

    /** Felix specific property to define custom properties for the http runtime service. */
    public static final String FELIX_CUSTOM_HTTP_RUNTIME_PROPERTY_PREFIX = "org.apache.felix.http.runtime.init.";

    /** Felix specific property to specify whether the server should collect statistics information (defaults to false) */
    public static final String FELIX_JETTY_STATISTICS_HANDLER_ENABLE = "org.apache.felix.jetty.statisticshandler.enable";

    /** Felix specific property to specify whether the server should use a server-wide gzip handler (defaults to true) */
    public static final String FELIX_JETTY_GZIP_HANDLER_ENABLE = "org.apache.felix.jetty.gziphandler.enable";

    /** Felix specific property to specify the minimum response size to trigger dynamic compression */
    public static final String FELIX_JETTY_GZIP_MIN_GZIP_SIZE = "org.apache.felix.jetty.gzip.minGzipSize";

    /** Felix specific property to specify the size in bytes of the buffer to inflate compressed request, or 0 for no inflation. */
    public static final String FELIX_JETTY_GZIP_INFLATE_BUFFER_SIZE = "org.apache.felix.jetty.gzip.inflateBufferSize";

    /** Felix specific property to specify the {@link Deflater} flush mode to use. */
    public static final String FELIX_JETTY_GZIP_SYNC_FLUSH = "org.apache.felix.jetty.gzip.syncFlush";

    /** Felix specific property to specify the methods to include in compression */
    public static final String FELIX_JETTY_GZIP_INCLUDED_METHODS = "org.apache.felix.jetty.gzip.includedMethods";

    /** Felix specific property to specify the methods to exclude from compression */
    public static final String FELIX_JETTY_GZIP_EXCLUDED_METHODS = "org.apache.felix.jetty.gzip.excludedMethods";

    /** Felix specific property to specify the path specs to include. Inclusion takes precedence over exclusion. */
    public static final String FELIX_JETTY_GZIP_INCLUDED_PATHS = "org.apache.felix.jetty.gzip.includedPaths";

    /** Felix specific property to specify the path specs to exclude. */
    public static final String FELIX_JETTY_GZIP_EXCLUDED_PATHS = "org.apache.felix.jetty.gzip.excludedPaths";

    /** Felix specific property to specify the included mime types. Inclusion takes precedence over exclusion. */
    public static final String FELIX_JETTY_GZIP_INCLUDED_MIME_TYPES = "org.apache.felix.jetty.gzip.includedMimeTypes";

    /** Felix specific property to specify the excluded mime types. */
    public static final String FELIX_JETTY_GZIP_EXCLUDED_MIME_TYPES = "org.apache.felix.jetty.gzip.excludedMimeTypes";

    /** Felix specific property to specify the stop timeout of the jetty server */
    public static final String FELIX_JETTY_STOP_TIMEOUT = "org.apache.felix.jetty.stopTimeout";

    /** Felix specific property to control whether to enable HTTP/2. */
    public static final String FELIX_HTTP2_ENABLE = "org.apache.felix.http2.enable";

    /** Felix specific property to specify the max number of concurrent streams per connection  */
    public static final String FELIX_JETTY_HTTP2_MAX_CONCURRENT_STREAMS = "org.apache.felix.jetty.http2.maxConcurrentStreams";

    /** Felix specific property to specify the initial stream receive window (client to server)  */
    public static final String FELIX_JETTY_HTTP2_INITIAL_STREAM_RECV_WINDOW = "org.apache.felix.jetty.http2.initialStreamRecvWindow";

    /** Felix specific property to specify the initial session receive window (client to server)  */
    public static final String FELIX_JETTY_HTTP2_INITIAL_SESSION_RECV_WINDOW = "org.apache.felix.jetty.http2.initialSessionRecvWindow";

    /** Felix specific property to specify the ALPN protocols to consider  */
    public static final String FELIX_JETTY_ALPN_PROTOCOLS = "org.apache.felix.jetty.alpn.protocols";

    /** Felix specific property to specify the default protocol when negotiation fails  */
    public static final String FELIX_JETTY_ALPN_DEFAULT_PROTOCOL = "org.apache.felix.jetty.alpn.defaultProtocol";

    /** Felix specific property to control whether to enable the standard jakarta.websocket APIs provided by Jakarta WebSocket 2.1 */
    public static final String FELIX_JAKARTA_WEBSOCKET_ENABLE = "org.apache.felix.jakarta.websocket.enable";

    /** Felix specific property to control whether to enable they Jetty-specific WebSocket APIs */
    public static final String FELIX_JETTY_WEBSOCKET_ENABLE = "org.apache.felix.jetty.websocket.enable";

    /** Felix specific property to control whether an OSGi configuration is required */
    private static final String FELIX_REQUIRE_OSGI_CONFIG = "org.apache.felix.http.require.config";

    private static String validateContextPath(String ctxPath)
    {
        // undefined, empty, or root context path
        if (ctxPath == null || ctxPath.length() == 0 || "/".equals(ctxPath))
        {
            return "/";
        }

        // ensure leading but no trailing slash
        if (!ctxPath.startsWith("/"))
        {
            ctxPath = "/".concat(ctxPath);
        }
        while (ctxPath.endsWith("/"))
        {
            ctxPath = ctxPath.substring(0, ctxPath.length() - 1);
        }

        return ctxPath;
    }

    private final BundleContext context;

    /**
     * Properties from the configuration not matching any of the
     * predefined properties. These properties can be accessed from the
     * getProperty* methods.
     * <p>
     * This map is indexed by String objects (the property names) and
     * the values are just objects as provided by the configuration.
     */
    private volatile Dictionary<String, ?> config;

    private volatile Integer httpPort;

    private volatile Integer httpsPort;

    public JettyConfig(final BundleContext context)
    {
        this.context = context;
        reset();
    }

    /**
     * Returns the named generic configuration property from the
     * configuration or the bundle context. If neither property is defined
     * return the defValue.
     */
    public boolean getBooleanProperty(String name, boolean defValue)
    {
        String value = getProperty(name, null);
        if (value != null)
        {
            return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
        }

        return defValue;
    }

    public String getClientcert()
    {
        return getProperty(FELIX_HTTPS_CLIENT_CERT, "none");
    }

    public String getContextPath()
    {
        return validateContextPath(getProperty(FELIX_HTTP_CONTEXT_PATH, null));
    }

    public String[] getExcludedCipherSuites()
    {
        return getStringArrayProperty(FELIX_JETTY_EXCLUDED_SUITES, getStringArrayProperty(FELIX_JETTY_EXCLUDED_SUITES_OLD, null));
    }

    public String[] getIncludedProtocols()
    {
        return getStringArrayProperty(FELIX_JETTY_INCLUDED_PROTOCOLS, null);
    }

    public String[] getExcludedProtocols()
    {
        return getStringArrayProperty(FELIX_JETTY_EXCLUDED_PROTOCOLS, null);
    }

    public int getHeaderSize()
    {
        return getIntProperty(FELIX_JETTY_HEADER_BUFFER_SIZE, 16 * 1024);
    }

    public String getHost()
    {
        return getProperty(FELIX_HOST, null);
    }

    public int getHttpPort()
    {
        if (httpPort == null) {
            httpPort = determinePort(String.valueOf(getProperty(HTTP_PORT)), 8080);
        }
        return httpPort;
    }

    public int getHttpsPort()
    {
        if (httpsPort == null) {
            httpsPort = determinePort(String.valueOf(getProperty(HTTPS_PORT)), 8443);
        }
        return httpsPort;
    }

    public int getHttpTimeout()
    {
        return getIntProperty(HTTP_TIMEOUT, 60000);
    }

    public String[] getIncludedCipherSuites()
    {
        return getStringArrayProperty(FELIX_JETTY_INCLUDED_SUITES, getStringArrayProperty(FELIX_JETTY_INCLUDED_SUITES_OLD, null));
    }

    /**
     * Returns the named generic configuration property from the
     * configuration or the bundle context. If neither property is defined
     * return the defValue.
     */
    public int getIntProperty(String name, int defValue)
    {
        return parseInt(getProperty(name, null), defValue);
    }

    /**
     * Returns the named generic configuration property from the
     * configuration or the bundle context. If neither property is defined
     * return the defValue.
     */
    public long getLongProperty(String name, long defValue)
    {
        return parseLong(getProperty(name, null), defValue);
    }

    public String getKeyPassword()
    {
        return getProperty(FELIX_KEYSTORE_KEY_PASSWORD, this.context.getProperty(OSCAR_KEYSTORE_KEY_PASSWORD));
    }

    public String getKeystore()
    {
        return getProperty(FELIX_KEYSTORE, this.context.getProperty(OSCAR_KEYSTORE));
    }

    public String getKeystoreType()
    {
        return getProperty(FELIX_KEYSTORE_TYPE, KeyStore.getDefaultType());
    }

    public String getPassword()
    {
        return getProperty(FELIX_KEYSTORE_PASSWORD, this.context.getProperty(OSCAR_KEYSTORE_PASSWORD));
    }

    public String[] getPathExclusions()
    {
        return getStringArrayProperty(FELIX_HTTP_PATH_EXCLUSIONS, new String[] { "/system" });
    }

    /**
     * Returns the named generic configuration property from the
     * configuration or the bundle context. If neither property is defined
     * return the defValue.
     */
    public String getProperty(String name, String defValue)
    {
        Object value = getProperty(name);
        return value != null ? String.valueOf(value) : defValue;
    }

    public int getThreadPoolMax()
    {
        return getIntProperty(FELIX_JETTY_THREADPOOL_MAX, -1);
    }

    public int getAcceptors()
    {
        return getIntProperty(FELIX_JETTY_ACCEPTORS, -1);
    }

    public int getSelectors()
    {
        return getIntProperty(FELIX_JETTY_SELECTORS, -1);
    }

    public int getRequestBufferSize()
    {
        return getIntProperty(FELIX_JETTY_REQUEST_BUFFER_SIZE, 8 * 1024);
    }

    public int getResponseBufferSize()
    {
        return getIntProperty(FELIX_JETTY_RESPONSE_BUFFER_SIZE, 24 * 1024);
    }

    public int getMaxFormSize()
    {
        return getIntProperty(FELIX_JETTY_MAX_FORM_SIZE, 200 * 1024);
    }

    /**
     * Returns the configured session timeout in minutes or zero if not
     * configured.
     */
    public int getSessionTimeout()
    {
        return getIntProperty(FELIX_SESSION_TIMEOUT, 0);
    }

    public String getTrustPassword()
    {
        return getProperty(FELIX_TRUSTSTORE_PASSWORD, null);
    }

    public String getTruststore()
    {
        String value = getProperty(FELIX_TRUSTSTORE, null);
        return value == null || value.trim().length() == 0 ? null : value;
    }

    public String getTruststoreType()
    {
        return getProperty(FELIX_TRUSTSTORE_TYPE, KeyStore.getDefaultType());
    }

    public boolean isRegisterMBeans()
    {
        return getBooleanProperty(FELIX_HTTP_MBEANS, false);
    }

    /**
     * Returns <code>true</code> if HTTP is configured to be used (
     * {@link #FELIX_HTTP_ENABLE}) and
     * the configured HTTP port ({@link #HTTP_PORT}) is higher than zero.
     */
    public boolean isUseHttp()
    {
        boolean useHttp = getBooleanProperty(FELIX_HTTP_ENABLE, true);
        return useHttp && getHttpPort() > 0;
    }

    public boolean isSendServerHeader()
    {
        return getBooleanProperty(FELIX_JETTY_SEND_SERVER_HEADER, false);
    }

    /**
     * Returns <code>true</code> if HTTPS is configured to be used (
     * {@link #FELIX_HTTPS_ENABLE}) and
     * the configured HTTP port ({@link #HTTPS_PORT}) is higher than zero.
     */
    public boolean isUseHttps()
    {
        boolean useHttps = getBooleanProperty(FELIX_HTTPS_ENABLE, getBooleanProperty(OSCAR_HTTPS_ENABLE, false));
        return useHttps && getHttpsPort() > 0;
    }

    /**
     * Returns <code>true</code> if HTTP/2 is configured to be used (
     * {@link #FELIX_HTTP2_ENABLE})
     */
    public boolean isUseHttp2()
    {
        return getBooleanProperty(FELIX_HTTP2_ENABLE, false);
    }

    public int getHttp2MaxConcurrentStreams() {
        return getIntProperty(FELIX_JETTY_HTTP2_MAX_CONCURRENT_STREAMS, 128);
    }

    public int getHttp2InitialStreamRecvWindow() {
        return getIntProperty(FELIX_JETTY_HTTP2_INITIAL_STREAM_RECV_WINDOW, 524288);
    }

    public int getHttp2InitialSessionRecvWindow() {
        return getIntProperty(FELIX_JETTY_HTTP2_INITIAL_SESSION_RECV_WINDOW, 1048576);
    }

    public String[] getAlpnProtocols() {
        return getStringArrayProperty(FELIX_JETTY_ALPN_PROTOCOLS, new String[] {"h2", "http/1.1"} );
    }

    public String getAlpnDefaultProtocol() {
        return getProperty(FELIX_JETTY_ALPN_DEFAULT_PROTOCOL, "http/1.1");
    }

    public boolean isProxyLoadBalancerConnection()
    {
        return getBooleanProperty(FELIX_PROXY_LOAD_BALANCER_CONNECTION_ENABLE, false);
    }

    public boolean isRenegotiationAllowed() {
        return getBooleanProperty(FELIX_JETTY_RENEGOTIATION_ALLOWED, false);
    }

    public String getHttpServiceName()
    {
    	return (String) getProperty(FELIX_HTTP_SERVICE_NAME);
    }

    public String getRequestLogFilter() {
        return getProperty(FELIX_HTTP_REQUEST_LOG_FILTER, null);
    }

    public boolean isRequestLogOSGiEnabled() {
        return getBooleanProperty(FELIX_HTTP_REQUEST_LOG_OSGI_ENABLE, false);
    }

    public String getRequestLogOSGiServiceName() {
        return (String) getProperty(FELIX_HTTP_REQUEST_LOG_OSGI_SERVICE_NAME);
    }

    public String getRequestLogOsgiSlf4JLoggerName() {
        return getProperty(FELIX_HTTP_REQUEST_LOG_OSGI_LOGGER_NAME, SystemLogger.LOGGER.getName());
    }

    public String getRequestLogOSGiFormat() {
        return getProperty(FELIX_HTTP_REQUEST_LOG_FORMAT, CustomRequestLog.EXTENDED_NCSA_FORMAT);
    }

    public String getRequestLogFilePath() {
        return getProperty(FELIX_HTTP_REQUEST_LOG_FILE_PATH, null);
    }

    public String getRequestLogFileServiceName() {
        return getProperty(FELIX_HTTP_REQUEST_LOG_FILE_SERVICE_NAME, "file");
    }

    public boolean isRequestLogFileAsync() {
        return getBooleanProperty(FELIX_HTTP_REQUEST_LOG_FILE_ASYNC, false);
    }

    public boolean isRequestLogFileAppend() {
        return getBooleanProperty(FELIX_HTTP_REQUEST_LOG_FILE_APPEND, true);
    }

    public int getRequestLogFileRetainDays() {
        return getIntProperty(FELIX_HTTP_REQUEST_LOG_FILE_RETAIN_DAYS, 31);
    }

    public String getRequestLogFileFormat() {
        return getProperty(FELIX_HTTP_REQUEST_LOG_FILE_FORMAT, CustomRequestLog.NCSA_FORMAT);
    }

    public String getRequestLogFilenameDateFormat() {
        return getProperty(FELIX_HTTP_REQUEST_LOG_FILE_FILENAME_DATE_FORMAT, null);
    }

    public String[] getRequestLogFileIgnorePaths() {
        return getStringArrayProperty(FELIX_HTTP_REQUEST_LOG_FILE_IGNORE_PATHS, new String[0]);
    }

    public boolean isStatisticsHandlerEnabled() {
        return getBooleanProperty(FELIX_JETTY_STATISTICS_HANDLER_ENABLE, false);
    }

    public boolean isGzipHandlerEnabled() {
        return getBooleanProperty(FELIX_JETTY_GZIP_HANDLER_ENABLE, false);
    }

    public int getGzipMinGzipSize() {
        return getIntProperty(FELIX_JETTY_GZIP_MIN_GZIP_SIZE, GzipHandler.DEFAULT_MIN_GZIP_SIZE);
    }

    public int getGzipInflateBufferSize() {
        return getIntProperty(FELIX_JETTY_GZIP_INFLATE_BUFFER_SIZE, -1);
    }

    public boolean isGzipSyncFlush() {
        return getBooleanProperty(FELIX_JETTY_GZIP_SYNC_FLUSH, false);
    }

    public String[] getGzipIncludedMethods() {
        return getStringArrayProperty(FELIX_JETTY_GZIP_INCLUDED_METHODS, new String[0]);
    }

    public String[] getGzipExcludedMethods() {
        return getStringArrayProperty(FELIX_JETTY_GZIP_EXCLUDED_METHODS, new String[0]);
    }

    public String[] getGzipIncludedPaths() {
        return getStringArrayProperty(FELIX_JETTY_GZIP_INCLUDED_PATHS, new String[0]);
    }

    public String[] getGzipExcludedPaths() {
        return getStringArrayProperty(FELIX_JETTY_GZIP_EXCLUDED_PATHS, new String[0]);
    }

    public String[] getGzipIncludedMimeTypes() {
        return getStringArrayProperty(FELIX_JETTY_GZIP_INCLUDED_MIME_TYPES, new String[0]);
    }

    public String[] getGzipExcludedMimeTypes() {
        return getStringArrayProperty(FELIX_JETTY_GZIP_EXCLUDED_MIME_TYPES, new String[0]);
    }

    public long getStopTimeout() {
        return getLongProperty(FELIX_JETTY_STOP_TIMEOUT, -1l);
    }

    /**
     * Returns <code>true</code> if jakarta websocket is configured to be used (
     * {@link #FELIX_JAKARTA_WEBSOCKET_ENABLE})
     */
    public boolean isUseJakartaWebsocket() {
        return getBooleanProperty(FELIX_JAKARTA_WEBSOCKET_ENABLE, false);
    }

    /**
     * Returns <code>true</code> if jetty websocket is configured to be used (
     * {@link #FELIX_JETTY_WEBSOCKET_ENABLE})
     */
    public boolean isUseJettyWebsocket() {
        return getBooleanProperty(FELIX_JETTY_WEBSOCKET_ENABLE, false);
    }

    public void reset()
    {
        update(null);
    }

    public void setServiceProperties(Hashtable<String, Object> props)
    {
        props.put(HTTP_PORT, Integer.toString(getHttpPort()));
        props.put(HTTPS_PORT, Integer.toString(getHttpsPort()));
        props.put(FELIX_HTTP_ENABLE, Boolean.toString(isUseHttp()));
        props.put(FELIX_HTTPS_ENABLE, Boolean.toString(isUseHttps()));
        if (getHttpServiceName() != null)
        {
			props.put(FELIX_HTTP_SERVICE_NAME, getHttpServiceName());
        }

        props.put(HttpConfig.PROP_INVALIDATE_SESSION, getBooleanProperty(HttpConfig.PROP_INVALIDATE_SESSION,
                HttpConfig.DEFAULT_INVALIDATE_SESSION));
        props.put(HttpConfig.PROP_UNIQUE_SESSION_ID, getBooleanProperty(HttpConfig.PROP_UNIQUE_SESSION_ID,
                HttpConfig.DEFAULT_UNIQUE_SESSION_ID));
        props.put(HttpConfig.PROP_CONTAINER_ADDED_ATTRIBUTE, getStringArrayProperty(HttpConfig.PROP_CONTAINER_ADDED_ATTRIBUTE,
                new String[] {"org.eclipse.jetty.security.sessionCreatedSecure"}));

        addCustomServiceProperties(props);
    }

    private void addCustomServiceProperties(final Hashtable<String, Object> props)
    {
        final Enumeration<String> keys = this.config.keys();
        while(keys.hasMoreElements())
        {
            final String key = keys.nextElement();
            if (key.startsWith(FELIX_CUSTOM_HTTP_RUNTIME_PROPERTY_PREFIX))
            {
                props.put(key.substring(FELIX_CUSTOM_HTTP_RUNTIME_PROPERTY_PREFIX.length()), this.config.get(key));
            }
        }
    }

    /**
     * Updates this configuration with the given dictionary.
     *
     * @param props the dictionary with the new configuration values, can be <code>null</code> to reset this configuration to its defaults.
     * @return <code>true</code> if the configuration was updated due to a changed value, or <code>false</code> if no change was found.
     */
    public boolean update(Dictionary<String, ?> props)
    {
        if (props == null)
        {
            props = new Hashtable<>();
        }

        // clear cached ports
        this.httpPort = null;
        this.httpsPort = null;

        // FELIX-4312 Check whether there's something changed in our configuration...
        Dictionary<String, ?> currentConfig = this.config;
        if (currentConfig == null || !props.equals(currentConfig))
        {
            this.config = props;

            return true;
        }

        return false;
    }

    private void closeSilently(ServerSocket resource)
    {
        if (resource != null)
        {
            try
            {
                resource.close();
            }
            catch (IOException e)
            {
                // Ignore...
            }
        }
    }

    /**
     * Determine the appropriate port to use. <code>portProp</code> is based
     * "version range" as described in OSGi Core Spec v4.2 3.2.6. It can use the
     * following forms:
     * <dl>
     * <dd>8000 | 8000</dd>
     * <dd>[8000,9000] | 8000 &lt;= port &lt;= 9000</dd>
     * <dd>[8000,9000) | 8000 &lt;= port &lt; 9000</dd>
     * <dd>(8000,9000] | 8000 &lt; port &lt;= 9000</dd>
     * <dd>(8000,9000) | 8000 &lt; port &lt; 9000</dd>
     * <dd>[,9000) | 1 &lt; port &lt; 9000</dd>
     * <dd>[8000,) | 8000 &lt;= port &lt; 65534</dd>
     * </dl>
     *
     * @param portProp
     *            The port property value to parse.
     * @return The port determined to be usable. -1 if failed to find a port.
     */
    private int determinePort(String portProp, int dflt)
    {
        // Default cases include null/empty range pattern or pattern == *.
        if (portProp == null || "".equals(portProp.trim()))
        {
            return dflt;
        }

        // asking for random port, so let ServerSocket handle it and return the answer
        portProp = portProp.trim();
        if ("*".equals(portProp) || "0".equals(portProp))
        {
            return getSocketPort(0);
        }
        else
        {
            // check that the port property is a version range as described in
            // OSGi Core Spec v4.2 3.2.6.
            // deviations from the spec are limited to:
            // * start, end of interval defaults to 1, 65535, respectively, if missing.
            char startsWith = portProp.charAt(0);
            char endsWith = portProp.charAt(portProp.length() - 1);

            int minPort = 1;
            int maxPort = 65535;

            if (portProp.contains(",") && (startsWith == '[' || startsWith == '(') && (endsWith == ']' || endsWith == ')'))
            {
                String interval = portProp.substring(1, portProp.length() - 1);
                int comma = interval.indexOf(',');

                // check if the comma is first (start port in range is missing)
                int start = (comma == 0) ? minPort : parseInt(interval.substring(0, comma), minPort);
                // check if the comma is last (end port in range is missing)
                int end = (comma == interval.length() - 1) ? maxPort : parseInt(interval.substring(comma + 1), maxPort);
                // check for exclusive notation
                if (startsWith == '(')
                {
                    start++;
                }
                if (endsWith == ')')
                {
                    end--;
                }
                // find a port in the requested range
                int port = start - 1;
                for (int i = start; port < start && i <= end; i++)
                {
                    port = getSocketPort(i);
                }

                return (port < start) ? dflt : port;
            }
            else
            {
                // We don't recognize the pattern as special, so try to parse it to an int
                return parseInt(portProp, dflt);
            }
        }
    }

    private int getSocketPort(int i)
    {
        int port = -1;
        ServerSocket ss = null;
        try
        {
            ss = new ServerSocket(i);
            port = ss.getLocalPort();
        }
        catch (IOException e)
        {
            SystemLogger.LOGGER.debug("Unable to bind to port: {}", i);
        }
        finally
        {
            closeSilently(ss);
        }
        return port;
    }

    private Object getProperty(final String name)
    {
        Dictionary<String, ?> conf = this.config;
        Object value = (conf != null) ? conf.get(name) : null;
        if (value == null)
        {
            value = this.context.getProperty(name);
        }
        return value;
    }

    /**
     * Get the property value as a string array.
     * Empty values are filtered out - if the resulting array is empty
     * the default value is returned.
     */
    private String[] getStringArrayProperty(String name, String[] defValue)
    {
        Object value = getProperty(name);
        if (value instanceof String)
        {
            final String stringVal = ((String) value).trim();
            if (stringVal.length() > 0)
            {
                return stringVal.split(",");
            }
        }
        else if (value instanceof String[])
        {
            final String[] stringArr = (String[]) value;
            final List<String> list = new ArrayList<>();
            for (final String stringVal : stringArr)
            {
                if (stringVal.trim().length() > 0)
                {
                    list.add(stringVal.trim());
                }
            }
            if (list.size() > 0)
            {
                return list.toArray(new String[list.size()]);
            }
        }
        else if (value instanceof Collection)
        {
            final ArrayList<String> conv = new ArrayList<>();
            for (Iterator<?> vi = ((Collection<?>) value).iterator(); vi.hasNext();)
            {
                Object object = vi.next();
                if (object != null)
                {
                    conv.add(String.valueOf(object));
                }
            }
            if (conv.size() > 0)
            {
                return conv.toArray(new String[conv.size()]);
            }
        }
        return defValue;
    }

    private int parseInt(String value, int dflt)
    {
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            return dflt;
        }
    }

    private long parseLong(String value, long dflt)
    {
        try
        {
            return Long.parseLong(value);
        }
        catch (NumberFormatException e)
        {
            return dflt;
        }
    }

    public boolean isRequireConfiguration() {
        return this.getBooleanProperty(FELIX_REQUIRE_OSGI_CONFIG, false);
    }
}

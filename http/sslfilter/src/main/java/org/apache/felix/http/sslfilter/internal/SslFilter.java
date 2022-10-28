/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.http.sslfilter.internal;

import static org.apache.felix.http.sslfilter.internal.SslFilterConstants.HDR_X_FORWARDED_SSL;
import static org.apache.felix.http.sslfilter.internal.SslFilterConstants.HDR_X_FORWARDED_SSL_CERTIFICATE;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;
import org.osgi.service.servlet.whiteboard.Preprocessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Designate(ocd = SslFilter.Config.class)
@Component( service = Preprocessor.class,
    configurationPid = "org.apache.felix.http.sslfilter.Configuration",
    property = {
        HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT + "=(" + HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME + "=*)",
        HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_PATTERN + "=/"
    })
public class SslFilter implements  Preprocessor {

    public static final Logger LOGGER = LoggerFactory.getLogger(SslFilter.class);

    @ObjectClassDefinition(name = "Apache Felix Http Service SSL Filter",
        description = "Configuration for the Http Service SSL Filter. Please consult the documentation of your proxy for the actual headers and values to use.")
    public @interface Config {

        @AttributeDefinition(name = "SSL forward header",
            description = "HTTP Request header name that indicates a request is a SSL request terminated at a" +
                          " proxy between the client and the originating server. The default value is 'X-Forwarded-SSL' as is " +
                          "customarily used in the wild. Other commonly used names are: 'X-Forwarded-Proto' (Amazon ELB), " +
                          "'X-Forwarded-Protocol' (alternative), and 'Front-End-Https' (Microsoft IIS).")
        String ssl_forward_header() default HDR_X_FORWARDED_SSL ;
        
        @AttributeDefinition(name = "SSL forward value",
            description = "HTTP Request header value that indicates a request is a SSL request terminated at a proxy. " +
                          "The default value is 'on'. Another commonly used value is 'https'.")
        String ssl_forward_value() default "on";

        @AttributeDefinition(name = "SSL client header",
            description = "HTTP Request header name that contains the client certificate forwarded by a proxy. The default " +
                          "value is 'X-Forwarded-SSL-Certificate'. Another commonly used value is 'X-Forwarded-SSL-Client-Cert'.")
        String ssl_forward_cert_header() default HDR_X_FORWARDED_SSL_CERTIFICATE;

        @AttributeDefinition(name = "Rewrite Absolute URLs",
            description = "If enabled, absolute URLs passed to either sendRedirect or by setting the location header are rewritten as well.")
        boolean rewrite_absolute_urls() default false;
    }

    private volatile Config config;

    @Activate
    public SslFilter(final Config config) {
        updateConfig(config);
    }

    @Modified
    public void updateConfig(final Config config) {
        this.config = config;
        LOGGER.info("SSL filter (re)configured with: " +
            "rewrite absolute urls = {}; SSL forward header = '{}'; SSL forward value = '{}'; SSL certificate header = '{}'",
            config.rewrite_absolute_urls(), config.ssl_forward_header(), config.ssl_forward_value(), config.ssl_forward_cert_header());
    }

    @Override
    public void init(final FilterConfig config) {
        // No explicit init needed...
    }

    @Override
    public void destroy() {
        // No explicit destroy needed...
    }

    @Override
    public void doFilter(final ServletRequest req, final ServletResponse res, final FilterChain chain)
    throws IOException, ServletException {
        final Config cfg = this.config;

        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpResp = (HttpServletResponse) res;

        if (cfg.ssl_forward_value().equalsIgnoreCase(httpReq.getHeader(cfg.ssl_forward_header()))) {
            httpResp = new SslFilterResponse(httpResp, httpReq, cfg);
            httpReq = new SslFilterRequest(httpReq, httpReq.getHeader(cfg.ssl_forward_cert_header()));
        }

        // forward the request making sure any certificate is removed again after the request processing gets back here
        try {
            chain.doFilter(httpReq, httpResp);
        } finally {
            if (httpReq instanceof SslFilterRequest) {
                ((SslFilterRequest) httpReq).done();
            }
        }
    }
}

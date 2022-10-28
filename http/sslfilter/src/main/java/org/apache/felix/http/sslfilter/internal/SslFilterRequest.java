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

import static org.apache.felix.http.sslfilter.internal.SslFilterConstants.ATTR_SSL_CERTIFICATE;
import static org.apache.felix.http.sslfilter.internal.SslFilterConstants.HDR_X_FORWARDED_PORT;
import static org.apache.felix.http.sslfilter.internal.SslFilterConstants.HTTPS;
import static org.apache.felix.http.sslfilter.internal.SslFilterConstants.HTTPS_PORT;
import static org.apache.felix.http.sslfilter.internal.SslFilterConstants.HTTP_SCHEME_PREFIX;
import static org.apache.felix.http.sslfilter.internal.SslFilterConstants.X_509;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

class SslFilterRequest extends HttpServletRequestWrapper {

    // pattern to convert the header to a PEM certificate for parsing
    // by replacing spaces with line breaks
    private static final Pattern HEADER_TO_CERT = Pattern.compile("(?! CERTIFICATE)(?= ) ");

    @SuppressWarnings("unchecked")
    SslFilterRequest(final HttpServletRequest request, final String clientCertHeader) {
        super(request);

        if (clientCertHeader != null && clientCertHeader.trim().length() > 0) {
            final String clientCert = HEADER_TO_CERT.matcher(clientCertHeader).replaceAll("\n");

            try (InputStream instream = new ByteArrayInputStream(clientCert.getBytes(StandardCharsets.UTF_8))) {
                 final CertificateFactory fac = CertificateFactory.getInstance(X_509);
                 Collection<X509Certificate> certs = (Collection<X509Certificate>) fac.generateCertificates(instream);
                    request.setAttribute(ATTR_SSL_CERTIFICATE, certs.toArray(new X509Certificate[certs.size()]));    
            } catch ( final IOException ignore) {
                    // ignore - can only happen on close
            } catch ( final CertificateException ce) {
                SslFilter.LOGGER.warn("Failed to create SSL filter request! Problem parsing client certificates?! Client certificate will *not* be forwarded...", ce);
            }
        }
    }

    void done() {
        getRequest().removeAttribute(ATTR_SSL_CERTIFICATE);
    }

    @Override
    public String getScheme() {
        return HTTPS;
    }

    @Override
    public boolean isSecure() {
        return true;
    }

    @Override
    public StringBuffer getRequestURL() {
        final StringBuffer result = super.getRequestURL();
        // In case the request happened over http, simply insert an additional 's'
        // to make the request appear to be done over https...
        if (result.indexOf(HTTP_SCHEME_PREFIX) == 0) {
            result.insert(4, 's');
        }
        return result;
    }

    @Override
    public int getServerPort() {
        int port;

        try {
            port = Integer.parseInt(getHeader(HDR_X_FORWARDED_PORT));
        } catch (final Exception e) {
            // Use default port
            port = HTTPS_PORT;
        }
        return port;
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.felix.hc.generalchecks;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.felix.hc.api.FormattingResultLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HttpRequestsCheckTrustedCerts {

    private static final Logger LOG = LoggerFactory.getLogger(HttpRequestsCheckTrustedCerts.class);

    private final List<X509Certificate> trustedCertificates;
    private X509TrustManager trustManager;
    private final SSLContext sslContext;
    private final SSLSocketFactory socketFactory;

    HttpRequestsCheckTrustedCerts(String[] trustedCertificateConfigs, FormattingResultLog configErrors) {
        if (trustedCertificateConfigs.length == 0) {
            throw new IllegalStateException("No trusted certificates configured");
        }
        try {
            this.trustedCertificates = parseTrustedCertificates(trustedCertificateConfigs, configErrors);
            this.trustManager = createCompositeTrustManager();
            this.sslContext = createSslContext();
            this.socketFactory = sslContext.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not initialize SSL context", e);
        }
    }

    List<X509Certificate> getTrustedCertificates() {
        return Collections.unmodifiableList(trustedCertificates);
    }

    SSLSocketFactory getSocketFactory() {
        return socketFactory;
    }

    X509TrustManager getTrustManager() {
        return trustManager;
    }

    private SSLContext createSslContext() throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] { trustManager }, null);
        return context;
    }

    private CompositeX509TrustManager createCompositeTrustManager() throws GeneralSecurityException {
        TrustManagerFactory defaultFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaultFactory.init((KeyStore) null);
        X509TrustManager defaultTrustManager = getX509TrustManager(defaultFactory);

        KeyStore trustedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try {
            trustedKeyStore.load(null, null);
        } catch (IOException e) {
            throw new GeneralSecurityException("Could not initialize trust store", e);
        }
        int index = 0;
        for (X509Certificate certificate : trustedCertificates) {
            if (certificate != null) {
                trustedKeyStore.setCertificateEntry("trusted-cert-" + index, certificate);
                index++;
            }
        }
        TrustManagerFactory customFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        customFactory.init(trustedKeyStore);
        X509TrustManager customTrustManager = getX509TrustManager(customFactory);

        return new CompositeX509TrustManager(defaultTrustManager, customTrustManager);
    }

    private X509TrustManager getX509TrustManager(TrustManagerFactory factory) throws GeneralSecurityException {
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new GeneralSecurityException("No X509TrustManager available");
    }

    class CompositeX509TrustManager implements X509TrustManager {
        private final X509TrustManager defaultTrustManager;
        private final X509TrustManager customTrustManager;

        CompositeX509TrustManager(X509TrustManager defaultTrustManager, X509TrustManager customTrustManager) {
            this.defaultTrustManager = Objects.requireNonNull(defaultTrustManager, "defaultTrustManager");
            this.customTrustManager = Objects.requireNonNull(customTrustManager, "customTrustManager");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            defaultTrustManager.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                defaultTrustManager.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                customTrustManager.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] defaultIssuers = defaultTrustManager.getAcceptedIssuers();
            X509Certificate[] customIssuers = customTrustManager.getAcceptedIssuers();
            X509Certificate[] combined = Arrays.copyOf(defaultIssuers, defaultIssuers.length + customIssuers.length);
            System.arraycopy(customIssuers, 0, combined, defaultIssuers.length, customIssuers.length);
            return combined;
        }
    }

    private List<X509Certificate> parseTrustedCertificates(String[] trustedCertificateConfigs, FormattingResultLog configErrors) {
        if (trustedCertificateConfigs == null || trustedCertificateConfigs.length == 0) {
            return Collections.emptyList();
        }
        List<X509Certificate> certificates = new ArrayList<>();

        CertificateFactory certificateFactory;
        try {
            certificateFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            LOG.error("Could not initialize certificate parser: {}", e.getMessage(), e);
            return java.util.Collections.emptyList();
        }
        for (String certificateText : trustedCertificateConfigs) {
            if (certificateText == null) {
                continue;
            }
            String trimmed = certificateText.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                if (trimmed.contains("BEGIN CERTIFICATE")) {
                    java.util.Collection<? extends Certificate> parsed = certificateFactory.generateCertificates(
                        new java.io.ByteArrayInputStream(trimmed.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
                    for (Certificate cert : parsed) {
                        if (cert instanceof X509Certificate) {
                            certificates.add((X509Certificate) cert);
                        }
                    }
                } else {
                    byte[] decoded = java.util.Base64.getMimeDecoder().decode(trimmed);
                    Certificate cert = certificateFactory.generateCertificate(new java.io.ByteArrayInputStream(decoded));
                    if (cert instanceof X509Certificate) {
                        certificates.add((X509Certificate) cert);
                    }
                }
            } catch (Exception e) {
                LOG.error("Invalid trusted certificate entry: {}", e.getMessage(), e);
                configErrors.healthCheckError("Invalid trusted certificate entry: " + e.getMessage(), e);
            }
        }
        return certificates;
    }
}

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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.felix.hc.api.FormattingResultLog;
import org.junit.Test;

public class HttpRequestsCheckTrustedCertsTest {

    private static final String TEST_CERT_PEM =
            "-----BEGIN CERTIFICATE-----\n"
            + "MIIDOTCCAiGgAwIBAgIUU+jOS79VZNr1+tfQOfoKXWcffd8wDQYJKoZIhvcNAQEL\n"
            + "BQAwLDEqMCgGA1UEAwwhSHR0cFJlcXVlc3RzQ2hlY2tUcnVzdGVkQ2VydHNUZXN0\n"
            + "MB4XDTI2MDIwNDE0MjQzOVoXDTI3MDIwNDE0MjQzOVowLDEqMCgGA1UEAwwhSHR0\n"
            + "cFJlcXVlc3RzQ2hlY2tUcnVzdGVkQ2VydHNUZXN0MIIBIjANBgkqhkiG9w0BAQEF\n"
            + "AAOCAQ8AMIIBCgKCAQEApdd8XtpYWm/BQsrjQusgZBH3FU8kr3K57NhvAp/lg+Mv\n"
            + "htSlPVBX8NiBgM5z7NpQUXaXUqzq4cmhNHhnWFbzOmxDuiFwkSHgXq8V9wmZ46zD\n"
            + "tJy7Jtwngw6Ap4EV4MuMc9qDvaX3tPgDtIPHhA5/VBwfbF8IFTxp8kJw0bdyL75g\n"
            + "LUUOhpRvWxe8hSvEYSGNpgArokQJabScEwfPCVBaU52PQlKUPROKVfBu2UMhHFLa\n"
            + "BXuyYQFW4hYUnrUQblYPUlutginf6Qkq9iaXCIQBoOrGGHwGa44rVzLtsi4R9q1U\n"
            + "MD898gJYBDS8oHQSrzlGy+/KtcIdBlDQ34o0N2OR5wIDAQABo1MwUTAdBgNVHQ4E\n"
            + "FgQU/ecb+5ELP+P5Aep3aBeFeGDoEFAwHwYDVR0jBBgwFoAU/ecb+5ELP+P5Aep3\n"
            + "aBeFeGDoEFAwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAIFC3\n"
            + "iTtSO/5h3Ko3ejeLP+5iMV3evRa5b0ttAQIjEWxJPMVVdiSiFmQbfKN9aBGfmnaG\n"
            + "c9IxHbsLw8+kl3K3NwXa3j661tRE0m/6G+09I5xEK8wTraS3Alg+0W7sOqKP7N3o\n"
            + "UQLKUVa4PTaBKZ7O8hLloEcRwjP6yGvQqEcqsGOViSkyEoFE9sRbPwiQo2ImNIlL\n"
            + "dMDijnEBr6p9iUDXKQqrkk7uccY7FdfZ5Pk7k7SNh92KHEwuZl4r8XFUTjIQtzCI\n"
            + "0pGIKfS5+e6ZwPsZWmFLJqRYquvdGyxYR6igLvYp6s/17vaqlOpTaUnzIM3ZVpw/\n"
            + "UuTa6SnXc4bPQVZaDg==\n"
            + "-----END CERTIFICATE-----";
    

    @Test
    public void testCreateSslContextWithoutCertificates() {
        FormattingResultLog configErrors = new FormattingResultLog();
        try {
            HttpRequestsCheckTrustedCerts trustedCerts = new HttpRequestsCheckTrustedCerts(new String[] {}, configErrors);
            assertFalse("No config error expected", configErrors.iterator().hasNext());
            trustedCerts.getSocketFactory();
            fail("Expected IllegalStateException for empty trusted certificates");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), containsString("No trusted certificates configured"));
        }
    }

    @Test
    public void testCreateSslContextWithInvalidCertificate() {
        FormattingResultLog configErrors = new FormattingResultLog();
        HttpRequestsCheckTrustedCerts trustedCerts = new HttpRequestsCheckTrustedCerts(new String[] { "-----BEGIN CERTIFICATE-----\nR5wIDAQABo1MwUTAdB\n-----END CERTIFICATE-----" }, configErrors);
        trustedCerts.getSocketFactory();
        assertTrue("Config error expected", configErrors.iterator().hasNext());
    }


    @Test
    public void testSelfSignedCertificate() throws Exception {
        FormattingResultLog configErrors = new FormattingResultLog();
        HttpRequestsCheckTrustedCerts trustedCerts = new HttpRequestsCheckTrustedCerts(new String[] { TEST_CERT_PEM }, configErrors);

        assertFalse("No config error expected", configErrors.iterator().hasNext());

        SSLSocketFactory socketFactory = trustedCerts.getSocketFactory();
        assertNotNull(socketFactory);
        assertEquals(1, trustedCerts.getTrustedCertificates().size());

        X509Certificate expectedCert = trustedCerts.getTrustedCertificates().get(0);
        assertSocketFactoryTrustsCertificate(trustedCerts.getTrustManager(), expectedCert);
    }

    private void assertSocketFactoryTrustsCertificate(X509TrustManager trustManager, X509Certificate expectedCert) throws Exception {
        trustManager.checkServerTrusted(new X509Certificate[] { expectedCert }, expectedCert.getPublicKey().getAlgorithm());

        boolean issuerFound = false;
        for (X509Certificate issuer : trustManager.getAcceptedIssuers()) {
            if (expectedCert.equals(issuer)) {
                issuerFound = true;
                break;
            }
        }
        assertTrue("Expected certificate to be present in accepted issuers", issuerFound);
    }

}

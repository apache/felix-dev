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
package org.apache.felix.framework.cache;

import org.apache.felix.framework.Logger;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.framework.util.Util;
import org.apache.felix.framework.util.WeakZipFileFactory;
import org.apache.felix.framework.util.WeakZipFileFactory.WeakZipFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.zip.ZipEntry;

/**
 * <p>
 * This class implements a bundle archive revision for a standard bundle
 * JAR file. The specified location is the URL of the JAR file. By default,
 * the associated JAR file is copied into the revision's directory on the
 * file system, but it is possible to mark the JAR as 'by reference', which
 * will result in the bundle JAR be used 'in place' and not being copied. In
 * either case, some of the contents may be extracted into the revision
 * directory, such as embedded JAR files and native libraries.
 * </p>
**/
class JarRevision extends BundleArchiveRevision
{
    private static final transient String BUNDLE_JAR_FILE = "bundle.jar";

    private final WeakZipFileFactory m_zipFactory;
    private final File m_bundleFile;
    private final WeakZipFile m_zipFile;

    public JarRevision(
        Logger logger, Map configMap, WeakZipFileFactory zipFactory,
        File revisionRootDir, String location, boolean byReference, InputStream is)
        throws Exception
    {
        super(logger, configMap, revisionRootDir, location);

        m_zipFactory = zipFactory;

        if (byReference)
        {
            m_bundleFile = new File(location.substring(
                location.indexOf(BundleArchive.FILE_PROTOCOL)
                    + BundleArchive.FILE_PROTOCOL.length()));
        }
        else
        {
            m_bundleFile = new File(getRevisionRootDir(), BUNDLE_JAR_FILE);
        }

        // Save and process the bundle JAR.
        initialize(byReference, is);

        // Open shared copy of the JAR file.
        WeakZipFile zipFile = null;
        try
        {
            // Open bundle JAR file.
            zipFile = m_zipFactory.create(m_bundleFile);
            // Error if no jar file.
            if (zipFile == null)
            {
                throw new IOException("No JAR file found.");
            }
            m_zipFile = zipFile;
        }
        catch (Exception ex)
        {
            if (zipFile != null) zipFile.close();
            throw ex;
        }
    }

    public Map<String, Object> getManifestHeader() throws Exception
    {
        // Read and parse headers into a case insensitive map of manifest attributes and return it.
        ZipEntry manifestEntry = m_zipFile.getEntry("META-INF/MANIFEST.MF");

        Map<String, Object> manifest = manifestEntry != null ? BundleCache.getMainAttributes(new StringMap(), m_zipFile.getInputStream(manifestEntry), manifestEntry.getSize()) : null;

        return manifest;
    }

    public Content getContent() throws Exception
    {
        return new JarContent(getLogger(), getConfig(), m_zipFactory,
            this, getRevisionRootDir(), m_bundleFile, m_zipFile);
    }

    protected void close() throws Exception
    {
        m_zipFile.close();
    }

    //
    // Private methods.
    //

    private void initialize(boolean byReference, InputStream is)
        throws Exception
    {
        try
        {
            // If the revision directory does not exist, then create it.
            if (!BundleCache.getSecureAction().fileExists(getRevisionRootDir()))
            {
                if (!BundleCache.getSecureAction().mkdir(getRevisionRootDir()))
                {
                    getLogger().log(
                        Logger.LOG_ERROR,
                        getClass().getName() + ": Unable to create revision directory.");
                    throw new IOException("Unable to create archive directory.");
                }

                if (!byReference)
                {
                    URLConnection conn = null;
                    try
                    {
                        if (is == null)
                        {
                            // Do it the manual way to have a chance to
                            // set request properties such as proxy auth.
                            URL url = BundleCache.getSecureAction().createURL(
                                null, getLocation(), null);
                            conn = BundleCache.getSecureAction().openURLConnection(url);

                            // Support for http proxy authentication.
                            String auth = BundleCache.getSecureAction()
                                .getSystemProperty("http.proxyAuth", null);
                            if ((auth != null) && (auth.length() > 0))
                            {
                                if ("http".equals(url.getProtocol()) ||
                                    "https".equals(url.getProtocol()))
                                {
                                    String base64 = Util.base64Encode(auth);
                                    conn.setRequestProperty(
                                        "Proxy-Authorization", "Basic " + base64);
                                }
                            }
                            is = BundleCache.getSecureAction()
                                .getURLConnectionInputStream(conn);
                        }

                        // Save the bundle jar file.
                        BundleCache.copyStreamToFile(is, m_bundleFile);
                    }
                    finally
                    {
                        // This is a hack to fix an issue on Android, where
                        // HttpURLConnections are not properly closed. (FELIX-2728)
                        if ((conn != null) && (conn instanceof HttpURLConnection))
                        {
                            ((HttpURLConnection) conn).disconnect();
                        }
                    }
                }
            }
        }
        finally
        {
            if (is != null) is.close();
        }
    }
}
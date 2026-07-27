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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.apache.felix.framework.Logger;
import org.apache.felix.framework.util.WeakZipFileFactory;
import org.osgi.framework.connect.ConnectContent;

public class ConnectContentContent implements Content
{
    private static final transient String EMBEDDED_DIRECTORY = "-embedded";
    private static final transient String LIBRARY_DIRECTORY = "-lib";

    private final Logger m_logger;
    private final WeakZipFileFactory m_zipFactory;
    private final Map<?,?> m_configMap;
    private final String m_name;
    private final File m_rootDir;
    private final Object m_revisionLock;
    private final ConnectContent m_content;

    public ConnectContentContent(Logger logger, WeakZipFileFactory zipFactory, Map<?,?> configMap, String name, File rootDir, Object revisionLock, ConnectContent content) throws IOException
    {
        m_logger = logger;
        m_zipFactory = zipFactory;
        m_configMap = configMap;
        m_name = name;
        m_rootDir = rootDir;
        m_revisionLock = revisionLock;
        m_content = content;
    }

    @Override
    public void close()
    {
        // TODO: Connect
    }

    @Override
    public boolean hasEntry(String name)
    {
        return m_content.getEntry(name).isPresent();
    }

    @Override
    public boolean isDirectory(String name)
    {
        return m_content.getEntry(name).map(entry -> entry.getName().endsWith("/")).orElse(false);
    }

    @Override
    public Enumeration<String> getEntries()
    {
        try
        {
            Iterator<String> entries = m_content.getEntries().iterator();
            return new Enumeration<String>()
            {
                @Override
                public boolean hasMoreElements()
                {
                    return entries.hasNext();
                }

                @Override
                public String nextElement()
                {
                    return entries.next();
                }
            };
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public byte[] getEntryAsBytes(String name)
    {
        return m_content.getEntry(name).flatMap(entry ->
        {
            try
            {
                return Optional.of(entry.getBytes());
            }
            catch (IOException e)
            {
                e.printStackTrace();
                return null;
            }
        }).orElse(null);
    }

    @Override
    public InputStream getEntryAsStream(String name) throws IOException
    {
        return m_content.getEntry(name).flatMap(entry ->
        {
            try
            {
                return Optional.of(entry.getInputStream());
            }
            catch (IOException e)
            {
                e.printStackTrace();
                return null;
            }
        }).orElse(null);
    }

    public ClassLoader getClassLoader() {
        return m_content.getClassLoader().orElse(null);
    }

    @Override
    public Content getEntryAsContent(String name)
    {
        if (".".equals(name) || "".equals(name))
        {
            return this;
        }
        String dir = name.endsWith("/") ? name : name + "/";

        if (hasEntry(dir))
        {
            return new ContentDirectoryContent(this, name);
        }

        if (hasEntry(name) && name.endsWith(".jar"))
        {
            // Any embedded JAR files will be extracted to the embedded directory.
            // Since embedded JAR file names may clash when extracting from multiple
            // embedded JAR files, the embedded directory is per embedded JAR file.
            File embedDir = new File(m_rootDir, m_name + EMBEDDED_DIRECTORY);

            File extractJar = new File(embedDir, name);

            try
            {
                if (!BundleCache.getSecureAction().fileExists(extractJar))
                {
                    // Extracting the embedded JAR file impacts all other existing
                    // contents for this revision, so we have to grab the revision
                    // lock first before trying to extract the embedded JAR file
                    // to avoid a race condition.
                    synchronized (m_revisionLock)
                    {
                        if (!BundleCache.getSecureAction().fileExists(extractJar))
                        {
                            // Make sure that the embedded JAR's parent directory exists;
                            // it may be in a sub-directory.
                            File jarDir = extractJar.getParentFile();
                            if (!BundleCache.getSecureAction().fileExists(jarDir) && !BundleCache.getSecureAction().mkdirs(jarDir))
                            {
                                throw new IOException("Unable to create embedded JAR directory.");
                            }

                            // Extract embedded JAR into its directory.
                            BundleCache.copyStreamToFile(m_content.getEntry(name).get().getInputStream(), extractJar);
                        }
                    }
                }
                return new JarContent(
                    m_logger, m_configMap, m_zipFactory, m_revisionLock,
                    extractJar.getParentFile(), extractJar, null);
            }
            catch (Exception ex)
            {
                m_logger.log(
                    Logger.LOG_ERROR,
                    "Unable to extract embedded JAR file.", ex);
            }
        }

        return null;
    }

    @Override
    public String getEntryAsNativeLibrary(String entryName)
    {
        // TODO: Connect
        return null;
    }

    @Override
    public URL getEntryAsURL(String name)
    {
        // TODO: Connect
        return null;
    }

    @Override
    public long getContentTime(String urlPath)
    {
        return m_content.getEntry(urlPath).flatMap(entry -> Optional.of(entry.getLastModified())).orElse(-1L);
    }
}

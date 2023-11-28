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
import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.Util;
import org.apache.felix.framework.util.WeakZipFileFactory;
import org.osgi.framework.Constants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

public class DirectoryContent implements Content
{
    private static final int BUFSIZE = 4096;
    private static final transient String EMBEDDED_DIRECTORY = "-embedded";
    private static final transient String LIBRARY_DIRECTORY = "-lib";

    private final Logger m_logger;
    private final Map m_configMap;
    private final WeakZipFileFactory m_zipFactory;
    private final Object m_revisionLock;
    private final File m_rootDir;
    private final File m_dir;
    private Map m_nativeLibMap;
    private final String m_canonicalRoot;

    public DirectoryContent(Logger logger, Map configMap,
                            WeakZipFileFactory zipFactory, Object revisionLock, File rootDir, File dir)
    {
        m_logger = logger;
        m_configMap = configMap;
        m_zipFactory = zipFactory;
        m_revisionLock = revisionLock;
        m_rootDir = rootDir;
        m_dir = dir;
        String canonicalPath = null;
        try {
            canonicalPath = BundleCache.getSecureAction().getCanonicalPath(m_dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!canonicalPath.endsWith(File.separator)) {
            canonicalPath = canonicalPath + File.separator;
        }
        m_canonicalRoot = canonicalPath;
    }

    public File getFile()
    {
        return m_dir;
    }

    public void close()
    {
        // Nothing to clean up.
    }

    public boolean hasEntry(String name) throws IllegalStateException
    {
        name = getName(name);

        // Return true if the file associated with the entry exists,
        // unless the entry name ends with "/", in which case only
        // return true if the file is really a directory.
        File file = null;
        try
        {
            file = getFile(name);
        }
        catch (IOException e)
        {
            return false;
        }
        return BundleCache.getSecureAction().fileExists(file)
                && (name.endsWith("/")
                ? BundleCache.getSecureAction().isFileDirectory(file) : true);
    }

    @Override
    public boolean isDirectory(String name)
    {
        name = getName(name);

        // Return true if the file associated with the entry exists,
        // unless the entry name ends with "/", in which case only
        // return true if the file is really a directory.
        File file = null;
        try
        {
            file = getFile(name);
        }
        catch (IOException e)
        {
            return false;
        }
        return BundleCache.getSecureAction().isFileDirectory(file);
    }

    public Enumeration<String> getEntries()
    {
        // Wrap entries enumeration to filter non-matching entries.
        Enumeration<String> e = new EntriesEnumeration(m_dir);

        // Spec says to return null if there are no entries.
        return (e.hasMoreElements()) ? e : null;
    }

    public byte[] getEntryAsBytes(String name) throws IllegalStateException
    {
        name = getName(name);

        // Get the embedded resource.

        try
        {
            File file = getFile(name);

            return BundleCache.getSecureAction().fileExists(file) ? BundleCache.read(BundleCache.getSecureAction().getInputStream(file), file.length()) : null;
        }
        catch (Exception ex)
        {
            m_logger.log(
                    Logger.LOG_ERROR,
                    "DirectoryContent: Unable to read bytes for file " + name + " from file " + new File(m_dir, name).getAbsolutePath(), ex);
            return null;
        }
    }

    public InputStream getEntryAsStream(String name)
            throws IllegalStateException, IOException
    {
        name = getName(name);

        try
        {
            File file = getFile(name);
            return BundleCache.getSecureAction().fileExists(file) ? BundleCache.getSecureAction().getInputStream(file) : null;
        }
        catch (Exception ex)
        {
            m_logger.log(
                    Logger.LOG_ERROR,
                    "DirectoryContent: Unable to create inputstream for file " + name + " from file " + new File(m_dir, name).getAbsolutePath(), ex);
            return null;
        }
    }

    private String getName(String name)
    {
        if ((name.length() > 0) && (name.charAt(0) == '/')) {
            name = name.substring(1);
        }
        return name;
    }

    private File getFile(String name) throws IOException
    {
        File result = new File(m_dir, name);

        String canonicalPath = BundleCache.getSecureAction().getCanonicalPath(result);
        if (BundleCache.getSecureAction().isFileDirectory(result) && !canonicalPath.endsWith(File.separator))
        {
            canonicalPath += File.separator;
        }
        if (!canonicalPath.startsWith(m_canonicalRoot))
        {
            throw new IOException("File outside the root: " + canonicalPath);
        }
        return result;
    }

    public URL getEntryAsURL(String name)
    {
        name = getName(name);

        if (hasEntry(name))
        {
            try
            {
                return BundleCache.getSecureAction().toURI(getFile(name)).toURL();
            }
            catch (IOException e)
            {
                return null;
            }
        }
        else
        {
            return null;
        }
    }

    @Override
    public long getContentTime(String name)
    {
        name = getName(name);

        File file = null;
        try {
            file = getFile(name);
        } catch (IOException e) {
            return 0L;
        }

        return BundleCache.getSecureAction().getLastModified(file);
    }

    public Content getEntryAsContent(String entryName)
    {
        // If the entry name refers to the content itself, then
        // just return it immediately.
        if (entryName.equals(FelixConstants.CLASS_PATH_DOT))
        {
            return new DirectoryContent(
                    m_logger, m_configMap, m_zipFactory, m_revisionLock, m_rootDir, m_dir);
        }

        // Remove any leading slash, since all bundle class path
        // entries are relative to the root of the bundle.
        entryName = (entryName.startsWith("/")) ? entryName.substring(1) : entryName;

        if (entryName.trim().startsWith(".." + File.separatorChar) ||
                entryName.contains(File.separator + ".." + File.separatorChar) ||
                entryName.trim().endsWith(File.separator + "..") ||
                entryName.trim().equals(".."))
        {
            return null;
        }

        // Any embedded JAR files will be extracted to the embedded directory.
        File embedDir = new File(m_rootDir, m_dir.getName() + EMBEDDED_DIRECTORY);

        // Determine if the entry is an embedded JAR file or
        // directory in the bundle JAR file. Ignore any entries
        // that do not exist per the spec.
        File file = null;
        try {
            file = getFile(entryName);
        } catch (IOException e) {
            return null;
        }
        if (BundleCache.getSecureAction().isFileDirectory(file))
        {
            return new DirectoryContent(
                    m_logger, m_configMap, m_zipFactory, m_revisionLock, m_rootDir, file);
        }
        else if (BundleCache.getSecureAction().fileExists(file)
                && entryName.endsWith(".jar"))
        {
            File extractDir = new File(embedDir,
                    (entryName.lastIndexOf('/') >= 0)
                            ? entryName.substring(0, entryName.lastIndexOf('/'))
                            : entryName);

            return new JarContent(
                    m_logger, m_configMap, m_zipFactory, m_revisionLock,
                    extractDir, file, null);
        }

        // The entry could not be found, so return null.
        return null;
    }

    // TODO: SECURITY - This will need to consider security.
    public String getEntryAsNativeLibrary(String entryName)
    {
        // Return result.
        String result = null;

        // Remove any leading slash, since all bundle class path
        // entries are relative to the root of the bundle.
        entryName = (entryName.startsWith("/")) ? entryName.substring(1) : entryName;

        if (entryName.trim().startsWith(".." + File.separatorChar) ||
                entryName.contains(File.separator + ".." + File.separatorChar) ||
                entryName.trim().endsWith(File.separator + "..") ||
                entryName.trim().equals(".."))
        {
            return null;
        }

        // Any embedded native library files will be extracted to the lib directory.
        File libDir = new File(m_rootDir, m_dir.getName() + LIBRARY_DIRECTORY);

        // The entry must exist and refer to a file, not a directory,
        // since we are expecting it to be a native library.
        File entryFile = null;
        try
        {
            entryFile = getFile(entryName);
        }
        catch (IOException e)
        {
            return null;
        }

        if (BundleCache.getSecureAction().fileExists(entryFile)
                && !BundleCache.getSecureAction().isFileDirectory(entryFile))
        {
            // Extracting the embedded native library file impacts all other
            // existing contents for this revision, so we have to grab the
            // revision lock first before trying to extract the embedded JAR
            // file to avoid a race condition.
            synchronized (m_revisionLock)
            {
                // Since native libraries cannot be shared, we must extract a
                // separate copy per request, so use the request library counter
                // as part of the extracted path.
                if (m_nativeLibMap == null)
                {
                    m_nativeLibMap = new HashMap();
                }
                Integer libCount = (Integer) m_nativeLibMap.get(entryName);
                // Either set or increment the library count.
                libCount = (libCount == null) ? new Integer(0) : new Integer(libCount.intValue() + 1);
                m_nativeLibMap.put(entryName, libCount);
                File libFile = new File(
                        libDir, libCount.toString() + File.separatorChar + entryName);

                if (!BundleCache.getSecureAction().fileExists(libFile))
                {
                    if (!BundleCache.getSecureAction().fileExists(libFile.getParentFile())
                            && !BundleCache.getSecureAction().mkdirs(libFile.getParentFile()))
                    {
                        m_logger.log(
                                Logger.LOG_ERROR,
                                "Unable to create library directory.");
                    }
                    else
                    {
                        InputStream is = null;

                        try
                        {
                            is = BundleCache.getSecureAction().getInputStream(entryFile);

                            // Create the file.
                            BundleCache.copyStreamToFile(is, libFile);

                            // Perform exec permission command on extracted library
                            // if one is configured.
                            String command = (String) m_configMap.get(
                                    Constants.FRAMEWORK_EXECPERMISSION);
                            if (command != null)
                            {
                                Properties props = new Properties();
                                props.setProperty("abspath", libFile.toString());
                                command = Util.substVars(command, "command", null, props);
                                Process p = BundleCache.getSecureAction().exec(command);
                                p.waitFor();
                            }

                            // Return the path to the extracted native library.
                            result = BundleCache.getSecureAction().getAbsolutePath(libFile);
                        }
                        catch (Exception ex)
                        {
                            m_logger.log(
                                    Logger.LOG_ERROR,
                                    "Extracting native library.", ex);
                        }
                    }
                }
                else
                {
                    // Return the path to the extracted native library.
                    result = BundleCache.getSecureAction().getAbsolutePath(libFile);
                }
            }
        }

        return result;
    }

    public String toString()
    {
        return "DIRECTORY " + m_dir;
    }

    private static class EntriesEnumeration implements Enumeration
    {
        private final File m_dir;
        private final File[] m_children;
        private int m_counter = 0;

        public EntriesEnumeration(File dir)
        {
            m_dir = dir;
            m_children = listFilesRecursive(m_dir);
        }

        public synchronized boolean hasMoreElements()
        {
            return (m_children != null) && (m_counter < m_children.length);
        }

        public synchronized Object nextElement()
        {
            if ((m_children == null) || (m_counter >= m_children.length))
            {
                throw new NoSuchElementException("No more entry paths.");
            }

            // Convert the file separator character to slashes.
            String abs = BundleCache.getSecureAction()
                    .getAbsolutePath(m_children[m_counter]).replace(File.separatorChar, '/');

            // Remove the leading path of the reference directory, since the
            // entry paths are supposed to be relative to the root.
            StringBuilder sb = new StringBuilder(abs);
            sb.delete(0, BundleCache.getSecureAction().getAbsolutePath(m_dir).length() + 1);
            // Add a '/' to the end of directory entries.
            if (BundleCache.getSecureAction().isFileDirectory(m_children[m_counter]))
            {
                sb.append('/');
            }
            m_counter++;
            return sb.toString();
        }

        private File[] listFilesRecursive(File dir)
        {
            File[] children = BundleCache.getSecureAction().listDirectory(dir);
            File[] combined = children;
            if (children != null)
            {
                for (int i = 0; i < children.length; i++)
                {
                    if (BundleCache.getSecureAction().isFileDirectory(children[i]))
                    {
                        File[] grandchildren = listFilesRecursive(children[i]);
                        if (grandchildren != null && grandchildren.length > 0)
                        {
                            File[] tmp = new File[combined.length + grandchildren.length];
                            System.arraycopy(combined, 0, tmp, 0, combined.length);
                            System.arraycopy(
                                    grandchildren, 0, tmp, combined.length, grandchildren.length);
                            combined = tmp;
                        }
                    }
                }
            }
            return combined;
        }
    }
}
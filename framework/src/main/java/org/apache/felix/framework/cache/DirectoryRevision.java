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
import org.apache.felix.framework.util.WeakZipFileFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * <p>
 * This class implements a bundle archive revision for exploded bundle
 * JAR files. It uses the specified location directory "in-place" to
 * execute the bundle and does not copy the bundle content at all.
 * </p>
**/
class DirectoryRevision extends BundleArchiveRevision
{
    private final WeakZipFileFactory m_zipFactory;
    private final File m_refDir;

    public DirectoryRevision(
        Logger logger, Map configMap, WeakZipFileFactory zipFactory,
        File revisionRootDir, String location) throws Exception
    {
        super(logger, configMap, revisionRootDir, location);
        m_zipFactory = zipFactory;
        m_refDir = new File(location.substring(
            location.indexOf(BundleArchive.FILE_PROTOCOL)
                + BundleArchive.FILE_PROTOCOL.length()));

        // If the revision directory exists, then we don't
        // need to initialize since it has already been done.
        if (BundleCache.getSecureAction().fileExists(getRevisionRootDir()))
        {
            return;
        }

        // Create revision directory, we only need this to store the
        // revision location, since nothing else needs to be extracted
        // since we are referencing a read directory already.
        if (!BundleCache.getSecureAction().mkdir(getRevisionRootDir()))
        {
            getLogger().log(
                Logger.LOG_ERROR,
                getClass().getName() + ": Unable to create revision directory.");
            throw new IOException("Unable to create archive directory.");
        }
    }

    public Map<String, Object> getManifestHeader()
        throws Exception
    {
        File manifest = new File(m_refDir, "META-INF/MANIFEST.MF");
        return BundleCache.getSecureAction().isFile(manifest) ? BundleCache.getMainAttributes(new StringMap(), BundleCache.getSecureAction().getInputStream(manifest), manifest.length()) : null;
    }

    public Content getContent() throws Exception
    {
        return new DirectoryContent(getLogger(), getConfig(), m_zipFactory,
            this, getRevisionRootDir(), m_refDir);
    }

    protected void close() throws Exception
    {
        // Nothing to close since we don't maintain any state outside
        // of the revision directory, which will be automatically deleted
        // by the parent bundle archive.
    }
}
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
import java.util.Map;
import java.util.Optional;

import org.apache.felix.framework.Logger;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.framework.util.WeakZipFileFactory;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.connect.ConnectModule;

public class ConnectRevision extends BundleArchiveRevision
{
    private final WeakZipFileFactory m_zipFactory;
    private final ConnectContent m_module;

    public ConnectRevision(Logger logger, Map<?,?> configMap, WeakZipFileFactory zipFactory,
        File revisionRootDir, String location, ConnectModule module) throws Exception
    {
        super(logger, configMap, revisionRootDir, location);
        m_zipFactory = zipFactory;
        m_module = module.getContent();
        m_module.open();
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

    @Override
    public Map<String, String> getManifestHeader() throws Exception
    {
        return (Map<String,String>) m_module.getHeaders().orElseGet(() -> m_module.getEntry("META-INF/MANIFEST.MF").flatMap(entry -> {
                try
                {
                    byte[] manifest = entry.getBytes();

                    return Optional.of((Map<String, String>) BundleCache.getMainAttributes(new StringMap<>(), new java.io.ByteArrayInputStream(manifest), manifest.length));
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            }).orElse(null));
    }

    @Override
    public Content getContent() throws Exception
    {
        return new ConnectContentContent(getLogger(), m_zipFactory, getConfig(), "connect", getRevisionRootDir(), this, m_module);
    }

    @Override
    protected void close() throws Exception
    {
        m_module.close();
    }
}

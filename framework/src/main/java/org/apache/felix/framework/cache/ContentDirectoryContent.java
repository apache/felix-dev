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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.NoSuchElementException;

public class ContentDirectoryContent implements Content
{
    private final Content m_content;
    private final String m_rootPath;

    public ContentDirectoryContent(Content content, String path)
    {
        m_content = content;
        // Add a '/' to the end if not present.
        m_rootPath = (path.length() > 0) && (path.charAt(path.length() - 1) != '/')
            ? path + "/" : path;
    }

    @Override
	public void close()
    {
        // We do not actually close the associated content
        // from which we are filtering our directory because
        // we assume that this will be close manually by
        // the owner of that content.
    }

    @Override
	public boolean hasEntry(String name) throws IllegalStateException
    {
        name = getName(name);

        return m_content.hasEntry(m_rootPath + name);
    }

    @Override
    public boolean isDirectory(String name)
    {
        name = getName(name);

        return m_content.isDirectory(m_rootPath + name);
    }

    @Override
	public Enumeration<String> getEntries()
    {
        Enumeration<String> result = new EntriesEnumeration(m_content.getEntries(), m_rootPath);
        return result.hasMoreElements() ? result : null;
    }

    @Override
	public byte[] getEntryAsBytes(String name) throws IllegalStateException
    {
        name = getName(name);

        return m_content.getEntryAsBytes(m_rootPath + name);
    }

    @Override
	public InputStream getEntryAsStream(String name)
        throws IllegalStateException, IOException
    {
        name = getName(name);

        return m_content.getEntryAsStream(m_rootPath + name);
    }

    private String getName(String name)
    {
        if ((name.length() > 0) && (name.charAt(0) == '/')) {
            name = name.substring(1);
        }
        return name;
    }

    @Override
	public URL getEntryAsURL(String name)
    {
        return m_content.getEntryAsURL(m_rootPath + name);
    }

    @Override
    public long getContentTime(String name)
    {
        name = getName(name);

        return m_content.getContentTime(m_rootPath + name);
    }

    @Override
	public Content getEntryAsContent(String name)
    {
        name = getName(name);

        return m_content.getEntryAsContent(m_rootPath + name);
    }

    @Override
	public String getEntryAsNativeLibrary(String name)
    {
        name = getName(name);

        return m_content.getEntryAsNativeLibrary(m_rootPath + name);
    }

    @Override
	public String toString()
    {
        return "CONTENT DIR " + m_rootPath + " (" + m_content + ")";
    }

    private static class EntriesEnumeration implements Enumeration<String>
    {
        private final Enumeration<String> m_enumeration;
        private final String m_rootPath;
        private String m_nextEntry = null;

        public EntriesEnumeration(Enumeration<String> enumeration, String rootPath)
        {
            m_enumeration = enumeration;
            m_rootPath = rootPath;
            m_nextEntry = findNextEntry();
        }

        @Override
		public synchronized boolean hasMoreElements()
        {
            return (m_nextEntry != null);
        }

        @Override
		public synchronized String nextElement()
        {
            if (m_nextEntry == null)
            {
                throw new NoSuchElementException("No more elements.");
            }
            String currentEntry = m_nextEntry;
            m_nextEntry = findNextEntry();
            return currentEntry;
        }

        private String findNextEntry()
        {
            if (m_enumeration != null)
            {
                // Find next entry that is inside the root directory.
                while (m_enumeration.hasMoreElements())
                {
                    String next = m_enumeration.nextElement();
                    if (next.startsWith(m_rootPath) && !next.equals(m_rootPath))
                    {
                        // Strip off the root directory.
                        return next.substring(m_rootPath.length());
                    }
                }
            }
            return null;
        }
    }
}
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

public interface Content
{
    /**
     * <p>
     * This method must be called when the content is no longer needed so
     * that any resourses being used (e.g., open files) can be closed. Once
     * this method is called, the content is no longer usable. If the content
     * is already closed, then calls on this method should have no effect.
     * </p>
    **/
    void close();

    /**
     * <p>
     * This method determines if the specified named entry is contained in
     * the associated content. The entry name is a relative path with '/'
     * separators.
     * </p>
     * @param name The name of the entry to find.
     * @return <tt>true</tt> if a corresponding entry was found, <tt>false</tt>
     *         otherwise.
    **/
    boolean hasEntry(String name);

    /**
     * <p>
     * This method determines if the specified named entry is contained in
     * the associated content and is a directory. The entry name is a relative path with '/'
     * separators.
     * </p>
     * @param name The name of the entry to find.
     * @return <tt>true</tt> if a corresponding entry was found and is a directory, <tt>false</tt>
     *         otherwise.
     **/
    boolean isDirectory(String name);

    /**
     * <p>
     * Returns an enumeration of entry names as <tt>String</tt> objects.
     * An entry name is a path constructed with '/' as path element
     * separators and is relative to the root of the content. Entry names
     * for entries that represent directories should end with the '/'
     * character.
     * </p>
     * @returns An enumeration of entry names or <tt>null</tt>.
    **/
    Enumeration<String> getEntries();

    /**
     * <p>
     * This method returns the named entry as an array of bytes.
     * </p>
     * @param name The name of the entry to retrieve as a byte array.
     * @return An array of bytes if the corresponding entry was found, <tt>null</tt>
     *         otherwise.
    **/
    byte[] getEntryAsBytes(String name);

    /**
     * <p>
     * This method returns the named entry as an input stream.
     * </p>
     * @param name The name of the entry to retrieve as an input stream.
     * @return An input stream if the corresponding entry was found, <tt>null</tt>
     *         otherwise.
     * @throws <tt>java.io.IOException</tt> if any error occurs.
    **/
    InputStream getEntryAsStream(String name) throws IOException;

    /**
     * <p>
     * This method returns the named entry as an <tt>IContent</tt> Typically,
     * this method only makes sense for entries that correspond to some form
     * of aggregated resource (e.g., an embedded JAR file or directory), but
     * implementations are free to interpret this however makes sense. This method
     * should return a new <tt>IContent</tt> instance for every invocation and
     * the caller is responsible for opening and closing the returned content
     * object.
     * </p>
     * @param name The name of the entry to retrieve as an <tt>IContent</tt>.
     * @return An <tt>IContent</tt> instance if a corresponding entry was found,
     *         <tt>null</tt> otherwise.
    **/
    Content getEntryAsContent(String name);

    /**
     * <p>
     * This method returns the named entry as a file in the file system for
     * use as a native library. It may not be possible for all content
     * implementations (e.g., memory only) to implement this method, in which
     * case it is acceptable to return <tt>null</tt>. Since native libraries
     * can only be associated with a single class loader, this method should
     * return a unique file per request.
     * </p>
     * @param name The name of the entry to retrieve as a file.
     * @return A string corresponding to the absolute path of the file if a
     *         corresponding entry was found, <tt>null</tt> otherwise.
    **/
    String getEntryAsNativeLibrary(String name);

    /**
     * <p>
     *  This method allows retrieving an entry as a local URL.
     * </p>
     *
     * @param name The name of the entry to retrieve as a URL
     * @return A URL using a local protocol such as file, jar
     *           or null if not possible.
     */
    URL getEntryAsURL(String name);

    long getContentTime(String urlPath);
}
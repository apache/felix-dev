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
package org.apache.felix.fileinstall.internal;

import java.io.Closeable;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * A Scanner object is able to detect and report new, modified
 * and deleted files.
 *
 * The scanner use an internal checksum to identify the signature
 * of a file or directory.  The checksum will change if the file
 * or any of the directory's child is modified.
 *
 * In addition, if the scanner detects a change on a given file, it
 * will wait until the checksum does not change anymore before reporting
 * the change on this file.  This allows to not report the change until
 * a big copy if complete for example.
 */
public class Scanner implements Closeable {

    public final static String SUBDIR_MODE_JAR = "jar";
    public final static String SUBDIR_MODE_SKIP = "skip";
    public final static String SUBDIR_MODE_RECURSE = "recurse";

    final File directory;
    final FilenameFilter filter;
    final boolean jarSubdir;
    final boolean skipSubdir;
    final boolean recurseSubdir;

    // Store checksums of files or directories
    Map<File, Long> lastChecksums = new HashMap<File, Long>();
    Map<File, Long> storedChecksums = new HashMap<File, Long>();

    /**
     * Create a scanner for the specified directory
     *
     * @param directory the directory to scan
     */
    public Scanner(File directory)
    {
        this(directory, null, null);
    }

    /**
     * Create a scanner for the specified directory and file filter
     *
     * @param directory the directory to scan
     * @param filterString a filter for file names
     * @param subdirMode to use when scanning
     */
    public Scanner(File directory, final String filterString, String subdirMode)
    {
        this.directory = canon(directory);
        if (filterString != null && filterString.length() > 0)
        {
            this.filter = new FilenameFilter()
            {
                Pattern pattern = Pattern.compile(filterString);
                public boolean accept(File dir, String name)
                {
                    return pattern.matcher(name).matches();
                }
            };
        }
        else
        {
            this.filter = null;
        }
        this.jarSubdir = subdirMode == null || SUBDIR_MODE_JAR.equals(subdirMode);
        this.skipSubdir = SUBDIR_MODE_SKIP.equals(subdirMode);
        this.recurseSubdir = SUBDIR_MODE_RECURSE.equals(subdirMode);
    }

    /**
     * Initialize the list of known files.
     * This should be called before the first scan to initialize
     * the list of known files.  The purpose is to be able to detect
     * files that have been deleted while the scanner was inactive.
     *
     * @param checksums a map of checksums
     */
    public void initialize(Map<File, Long> checksums)
    {
        storedChecksums.putAll(checksums);
    }

    /**
     * Report a set of new, modified or deleted files.
     * Modifications are checked against a computed checksum on some file
     * attributes to detect any modification.
     * Upon restart, such checksums are not known so that all files will
     * be reported as modified.
     *
     * @param reportImmediately report all files immediately without waiting for the checksum to be stable
     * @return a list of changes on the files included in the directory
     */
    public Set<File> scan(boolean reportImmediately)
    {

        File[] list = directory.listFiles();
        Set<File> files = processFiles(reportImmediately, list);
        return new TreeSet<>(files);
    }

    private Set<File> processFiles(boolean reportImmediately, File[] list)
    {
        if (list == null)
        {
            return new HashSet<>();
        }
        Set<File> files = new HashSet<File>();
        Set<File> removed = new HashSet<File>(storedChecksums.keySet());
        for (File file : list)
        {
            if (file.isDirectory())
            {
                if (skipSubdir)
                {
                    continue;
                }
                else if (recurseSubdir)
                {
                    files.addAll(processFiles(reportImmediately, file.listFiles()));
                    continue;
                }
            }
            else {
                if (filter != null && !filter.accept(file.getParentFile(),file.getName())){
                    continue;
                }
            }
            verifyChecksum(files, file, reportImmediately);
            removed.remove(file);
        }
        // Make sure we'll handle a file that has been deleted
        files.addAll(removed);
        for (File file : removed)
        {
            // Remove no longer used checksums
            lastChecksums.remove(file);
            storedChecksums.remove(file);
        }
        // Double check known files because modifications from externally mounted
        // file systems are not well handled by inotify in Linux.
        for (File file : new HashSet<File>(storedChecksums.keySet())) {
            verifyChecksum(files, file, false);
        }
        return files;
    }

    public void close() throws IOException {
    }

    private static File canon(File file)
    {
        try
        {
            return file.getCanonicalFile();
        }
        catch (IOException e)
        {
            return file;
        }
    }

    /**
     * Retrieve the previously computed checksum for a give file.
     *
     * @param file the file to retrieve the checksum
     * @return the checksum
     */
    public long getChecksum(File file)
    {
        Long c = storedChecksums.get(file);
        return c != null ? c : 0;
    }

    /**
      * Update the checksum of a file if that file is already known locally.
      */
    public void updateChecksum(File file)
    {
        if (file != null && storedChecksums.containsKey(file))
        {
            long newChecksum = checksum(file);
            storedChecksums.put(file, newChecksum);
        }
    }

    void verifyChecksum(Set<File> files, File file, boolean reportImmediately) {
        long lastChecksum = lastChecksums.get(file) != null ? (Long) lastChecksums.get(file) : 0;
        long storedChecksum = storedChecksums.get(file) != null ? (Long) storedChecksums.get(file) : 0;
        long newChecksum = checksum(file);
        lastChecksums.put(file, newChecksum);
        // Only handle file when it does not change anymore and it has changed
        // since last reported
        if ((newChecksum == lastChecksum || reportImmediately) && newChecksum != storedChecksum)
        {
            storedChecksums.put(file, newChecksum);
            files.add(file);
        }
    }

    /**
     * Compute a cheksum for the file or directory that consists of the name, length and the last modified date
     * for a file and its children in case of a directory
     *
     * @param file the file or directory
     * @return a checksum identifying any change
     */
    static long checksum(File file)
    {
        CRC32 crc = new CRC32();
        checksum(file, crc);
        return crc.getValue();
    }

    private static void checksum(File file, CRC32 crc)
    {
        crc.update(file.getName().getBytes());
        if (file.isFile())
        {
            checksum(file.lastModified(), crc);
            checksum(file.length(), crc);
            checksum(Util.collectWriteableChecksum(file), crc);
        }
        else if (file.isDirectory())
        {
            File[] children = file.listFiles();
            if (children != null)
            {
                for (File aChildren : children)
                {
                    checksum(aChildren, crc);
                }
            }
        }
    }

    private static void checksum(long l, CRC32 crc)
    {
        for (int i = 0; i < 8; i++)
        {
            crc.update((int) (l & 0x000000ff));
            l >>= 8;
        }
    }

}

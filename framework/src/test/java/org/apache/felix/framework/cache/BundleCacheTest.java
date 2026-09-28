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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.apache.felix.framework.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

class BundleCacheTest
{
    private final List<BundleArchive> archives = new ArrayList<>();
    private File tempDir;
    private File cacheDir;
    private File filesDir;
    private BundleCache cache;
    private File archiveFile;
    private File jarFile;

    private static final String SPECIAL_JAR_ENTRY = File.separatorChar == '\\'
        ? "inner/i+~äö §$%nner.jar"
        : "inner/i+~äö \\§$%nner.jar";

    private BundleArchive track(BundleArchive archive)
    {
        if (archive != null)
        {
            archives.add(archive);
        }
        return archive;
    }

    @BeforeEach
    void setUp() throws Exception
    {
        tempDir = File.createTempFile("felix-temp", ".dir");
        assertThat(tempDir.delete()).as("precondition").isTrue();
        assertThat(tempDir.mkdirs()).as("precondition").isTrue();

        cacheDir = new File(tempDir, "felix-cache");
        assertThat(cacheDir.mkdir()).as("precondition").isTrue();

        filesDir = new File(tempDir, "files");
        String cacheDirPath = cacheDir.getPath();

        Map<String, String> params = new HashMap<>();
        params.put("felix.cache.profiledir", cacheDirPath);
        params.put("felix.cache.dir", cacheDirPath);
        params.put(Constants.FRAMEWORK_STORAGE, cacheDirPath);

        cache = new BundleCache(new Logger(){
            @Override
            protected void doLog(int level, String msg, Throwable throwable) {
            }
        }, params);

        archiveFile = new File(filesDir, "bundle1");

        createTestArchive(archiveFile);

        File innerArchiveFile = new File(archiveFile, "inner");
        createTestArchive(innerArchiveFile);

        new File(innerArchiveFile, "empty").mkdirs();

        createJar(archiveFile, new File(archiveFile, SPECIAL_JAR_ENTRY));

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("foo", "bar");
        manifest.getMainAttributes().putValue("Manifest-Version", "v1");

        File mf = new File(archiveFile, "META-INF/MANIFEST.MF");
        mf.getParentFile().mkdirs();
        FileOutputStream output = new FileOutputStream(mf);
        manifest.write(output);
        output.close();
        jarFile = new File(filesDir, "bundle1.jar");
        createJar(archiveFile, jarFile);
    }

    @Test
    void noZipSlip() throws Exception
    {
        File bundle = new File(filesDir, "slip");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "v1");
        manifest.getMainAttributes().putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        manifest.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, "slip");

        JarOutputStream output = new JarOutputStream(new FileOutputStream(bundle),manifest);

        output.putNextEntry(new ZipEntry("../../bar.jar"));

        try (FileInputStream fis = new FileInputStream(jarFile))
        {
            output.write(BundleCache.read(fis, jarFile.length()));
        }

        output.closeEntry();

        output.close();

        BundleArchive archive;
        try (FileInputStream fis = new FileInputStream(bundle))
        {
            archive = track(cache.create(1, 1, "slip", fis, null));
        }

        noZipSlip(archive);

        archive = track(cache.create(1, 1, bundle.toURI().toURL().toString(), null, null));

        noZipSlip(archive);

        archive = track(cache.create(1, 1, "reference:" + bundle.toURI().toURL().toString(), null, null));

        noZipSlip(archive);

        File dir = new File(filesDir, "exploded");

        dir.mkdirs();

        File test = new File(dir, "../../bar.jar");
        test.createNewFile();
        test.deleteOnExit();

        archive = track(cache.create(1, 1, "reference:" + dir.toURI().toURL().toString(), null, null));

        noZipSlip(archive);
    }

    void noZipSlip(BundleArchive archive) throws Exception
    {
        Content content = archive.getCurrentRevision().getContent().getEntryAsContent("../../bar.jar");

        assertThat(content).isNull();

        String lib = archive.getCurrentRevision().getContent().getEntryAsNativeLibrary("../../bar.jar");

        assertThat(lib).isNull();
    }

    @Test
    void directoryReference() throws Exception
    {
        BundleArchive archive = bundle("reference:" + archiveFile.toURI().toURL(), null);
        String path = "../../../../../../../../../../../../../../../../..".replace("/", File.separator);
        assertThat(archive.getCurrentRevision().getContent().hasEntry(path)).isFalse();
        assertThat(archive.getCurrentRevision().getContent().isDirectory(path)).isFalse();
        assertThat(archive.getCurrentRevision().getContent().getEntryAsURL(path)).isNull();
        assertThat(archive.getCurrentRevision().getContent().getEntryAsBytes(path + jarFile.getAbsolutePath())).isNull();
        assertThat(archive.getCurrentRevision().getContent().getEntryAsNativeLibrary(path + jarFile.getAbsolutePath())).isNull();
        assertThat(archive.getCurrentRevision().getContent().getContentTime(path + jarFile.getAbsolutePath())).isEqualTo(0L);
    }

    @Test
    void jarReference() throws Exception
    {
       bundle("reference:" + jarFile.toURI().toURL().toString(), null);
    }

    @Test
    void jar() throws Exception
    {
       bundle(jarFile.toURI().toURL().toString(), null);
    }

    @Test
    void inputStream() throws Exception
    {
        bundle("bla", jarFile);
    }

    @Test
    private BundleArchive bundle(String location, File file) throws Exception
    {
        BundleArchive archive;
        if (file != null)
        {
            try (FileInputStream fis = new FileInputStream(file))
            {
                archive = track(cache.create(1, 1, location, fis, null));
            }
        }
        else
        {
            archive = track(cache.create(1, 1, location, null, null));
        }

        assertThat(archive).isNotNull();

        assertThat(archive.getCurrentRevisionNumber()).isEqualTo(0L);

        revision(archive);

        String nativeLib = archive.getCurrentRevision().getContent().getEntryAsNativeLibrary("file1");

        assertThat(nativeLib).isNotNull();
        assertThat(new File(nativeLib)).isFile();

        if (file != null)
        {
            try (FileInputStream fis = new FileInputStream(file))
            {
                archive.revise(location, fis);
            }
        }
        else
        {
            archive.revise(location, null);
        }

        assertThat(archive.getCurrentRevisionNumber()).isEqualTo(1L);

        revision(archive);

        String nativeLib2 = archive.getCurrentRevision().getContent().getEntryAsNativeLibrary("file1");

        assertThat(nativeLib2).isNotNull();
        assertThat(new File(nativeLib)).isFile();
        assertThat(new File(nativeLib2)).isFile();

        archive.purge();

        assertThat(archive.getCurrentRevisionNumber()).isEqualTo(1L);

        revision(archive);

        String nativeLib3 = archive.getCurrentRevision().getContent().getEntryAsNativeLibrary("file1");

        assertThat(nativeLib3).isNotNull();
        assertNotSame(nativeLib, nativeLib2);
        assertNotSame(nativeLib2, nativeLib3);
        assertThat(new File(nativeLib).isFile()).isFalse();
        assertThat(new File(nativeLib2)).isFile();
        assertThat(new File(nativeLib3)).isFile();

        return archive;
    }

    @Test
    private void revision(BundleArchive archive) throws Exception
    {
        BundleArchiveRevision revision = archive.getCurrentRevision();
        assertThat(revision).isNotNull();
        assertThat(revision.getManifestHeader()).isNotNull();
        assertThat(revision.getManifestHeader()).containsEntry("foo", "bar");
        String specialEntry = SPECIAL_JAR_ENTRY.replace(File.separatorChar, '/');
        perRevision(revision.getContent(),  new TreeSet<>(Arrays.asList("META-INF/", "META-INF/MANIFEST.MF", "file1", "inner/", "inner/empty/", "inner/file1", specialEntry)));
        perRevision(revision.getContent().getEntryAsContent("inner"),  new TreeSet<>(Arrays.asList("file1", "empty/", specialEntry.substring("inner/".length()))));
        assertThat(revision.getContent().getEntryAsContent("inner/inner")).isNull();
        assertThat(revision.getContent().getEntryAsContent("inner/empty/")).isNotNull();
        assertThat(revision.getContent().getEntryAsContent("inner/empty").getEntries()).isNull();
        perRevision(revision.getContent().getEntryAsContent("inner/").getEntryAsContent(specialEntry.substring("inner/".length())), new TreeSet<>(Arrays.asList("file1", "inner/", "inner/empty/", "inner/file1")));
    }

    private void perRevision(Content content, Set<String> expectedEntries) throws Exception
    {
        assertThat(content).isNotNull();
        Enumeration<String> entries =  content.getEntries();
        Set<String> foundEntries = new TreeSet<>();
        while (entries.hasMoreElements())
        {
            foundEntries.add(entries.nextElement());
        }
        assertThat(foundEntries).isEqualTo(expectedEntries);

        assertThat(content.hasEntry("file1")).isTrue();
        assertThat(content.hasEntry("foo")).isFalse();
        assertThat(content.hasEntry("foo/bar")).isFalse();

        byte[] entry = content.getEntryAsBytes("file1");
        assertThat(entry).isNotNull();
        assertThat(new String(entry, "UTF-8")).isEqualTo("file1");
        assertThat(content.getEntryAsBytes("foo")).isNull();
        assertThat(content.getEntryAsBytes("foo/bar")).isNull();


        try (InputStream input = content.getEntryAsStream("file1"))
        {
            assertThat(input).isNotNull();
            entry = new byte[1014];
            int j = 0;
            for (int i = input.read();i != -1; i = input.read())
            {
                entry[j++] = (byte) i;
            }
            assertThat(new String(entry, 0, j, "UTF-8")).isEqualTo("file1");
        }
        assertThat(content.getEntryAsStream("foo")).isNull();
        assertThat(content.getEntryAsStream("foo/bar")).isNull();

        URL url = content.getEntryAsURL("file1");
        assertThat(url).isNotNull();
        java.net.URLConnection conn = url.openConnection();
        conn.setUseCaches(false);
        try (InputStream input = conn.getInputStream())
        {
            assertThat(input).isNotNull();
            entry = new byte[1014];
            int j = 0;
            for (int i = input.read();i != -1; i = input.read())
            {
                entry[j++] = (byte) i;
            }
            assertThat(new String(entry, 0, j, "UTF-8")).isEqualTo("file1");
        }
        assertThat(content.getEntryAsURL("foo")).isNull();
        assertThat(content.getEntryAsURL("foo/bar")).isNull();

        assertThat(content.getEntryAsNativeLibrary("blub")).isNull();
        String nativeLib = content.getEntryAsNativeLibrary("file1");
        assertThat(nativeLib).isNotNull();
        assertThat(new File(nativeLib)).isFile();
        content.close();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (BundleArchive archive : archives)
        {
            try
            {
                archive.close();
            }
            catch (Exception e)
            {
                // ignore
            }
        }
        archives.clear();
        cache.release();
        cache.delete();
        if (cacheDir.exists())
        {
            System.out.println("--- CACHE DIR DELETION FAILED. FILES REMAINING: ---");
            printFiles(cacheDir);
        }
        assertThat(cacheDir.exists()).isFalse();
        assertThat(BundleCache.deleteDirectoryTree(tempDir)).isTrue();
    }

    private void printFiles(File dir)
    {
        File[] files = dir.listFiles();
        if (files != null)
        {
            for (File f : files)
            {
                System.out.println(f.getAbsolutePath());
                if (f.isDirectory())
                {
                    printFiles(f);
                }
            }
        }
    }

    private void createTestArchive(File archiveFile) throws Exception
    {
        createFile(archiveFile, "file1", "file1".getBytes("UTF-8"));
    }

    private void createFile(File parent, String path, byte[] content) throws IOException
    {
        File target = new File(parent, path);

        target.getParentFile().mkdirs();

        assertThat(target.getParentFile()).isDirectory();

        try (FileOutputStream output = new FileOutputStream(target))
        {
            output.write(content);
        }
    }

    private void createJar(File source, File target) throws Exception
    {
        File tmp = File.createTempFile("bundle", ".jar", filesDir);
        JarOutputStream output;
        if (new File(source, "META-INF/MANIFEST.MF").isFile())
        {
            try (FileInputStream fis = new FileInputStream(new File(source, "META-INF/MANIFEST.MF")))
            {
                output = new JarOutputStream(new FileOutputStream(tmp), new Manifest(fis));
            }
        }
        else
        {
            output = new JarOutputStream(new FileOutputStream(tmp));
        }

        writeRecursive(source, "", output);

        output.close();
        target.delete();
        tmp.renameTo(target);
    }

    private void writeRecursive(File current, String path, JarOutputStream output) throws Exception
    {
        if (current.isDirectory())
        {
            File[] children = current.listFiles();
            if (children != null)
            {
                for (File file : children)
                {
                    if (!file.getName().equals("MANIFEST.MF"))
                    {
                        String next = path + file.getName();
                        if (file.isDirectory())
                        {
                            next += "/";
                        }
                        output.putNextEntry(new ZipEntry(next));
                        if (file.isDirectory())
                        {
                            output.closeEntry();
                        }
                        writeRecursive(file, next, output);
                    }
                }
            }
        }
        else if (current.isFile())
        {
            try (FileInputStream fis = new FileInputStream(current))
            {
                output.write(BundleCache.read(fis, current.length()));
            }
            output.closeEntry();
        }
    }
}

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
package org.apache.felix.bundlerepository.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import junit.framework.TestCase;

public class FileUtilTest extends TestCase
{
    public void testUnjarZipSlip() throws Exception
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JarOutputStream jos = new JarOutputStream(baos);
        try
        {
            JarEntry entry = new JarEntry("../../evil.txt");
            jos.putNextEntry(entry);
            jos.write("malicious content".getBytes());
            jos.closeEntry();
        }
        finally
        {
            jos.close();
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        JarInputStream jis = new JarInputStream(bais);

        File targetDir = new File("target/extraction-test-fileutil");
        targetDir.mkdirs();

        try
        {
            FileUtil.unjar(jis, targetDir);
            fail("Expected IOException due to Zip Slip path traversal");
        }
        catch (IOException e)
        {
            assertTrue(e.getMessage().contains("resolves outside the target directory"));
        }
        finally
        {
            deleteDirectory(targetDir);
        }
    }

    private void deleteDirectory(File dir)
    {
        File[] files = dir.listFiles();
        if (files != null)
        {
            for (File f : files)
            {
                deleteDirectory(f);
            }
        }
        dir.delete();
    }
}

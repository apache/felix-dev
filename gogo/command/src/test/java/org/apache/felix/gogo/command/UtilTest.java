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

package org.apache.felix.gogo.command;

import org.apache.felix.service.command.CommandSession;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public class UtilTest {

    @Test
    public void relativeUri() throws Exception {
        CommandSession session = mock(CommandSession.class);
        when(session.get(Util.CWD)).thenReturn(null); // start with no CWD
        String u = Util.resolveUri(session, "three");
        assertEquals(new File(new File("").getAbsoluteFile(), "three").toURI().toString(), u);

        when(session.get(Util.CWD)).thenReturn(new File(".").getCanonicalFile());
        u = Util.resolveUri(session, "file:/a/b/c");
        assertEquals("file:/a/b/c", u);
        u = Util.resolveUri(session, "three");
        assertEquals(new File("./three").getCanonicalFile().toURI().toString(), u);
    }

    @Test
    public void testUnjarZipSlip() throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(baos)) {
            java.util.jar.JarEntry entry = new java.util.jar.JarEntry("../../evil.txt");
            jos.putNextEntry(entry);
            jos.write("malicious content".getBytes());
            jos.closeEntry();
        }

        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
        java.util.jar.JarInputStream jis = new java.util.jar.JarInputStream(bais);

        File targetDir = new File("target/extraction-test-util");
        targetDir.mkdirs();

        try {
            Util.unjar(jis, targetDir);
            org.junit.Assert.fail("Expected IOException due to Zip Slip path traversal");
        } catch (java.io.IOException e) {
            org.junit.Assert.assertTrue(e.getMessage().contains("resolves outside the target directory"));
        } finally {
            deleteDirectory(targetDir);
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                deleteDirectory(f);
            }
        }
        dir.delete();
    }
}

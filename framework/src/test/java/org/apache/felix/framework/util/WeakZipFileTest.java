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
package org.apache.felix.framework.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.felix.framework.util.WeakZipFileFactory.WeakZipFile;
import org.junit.jupiter.api.Test;

class WeakZipFileTest
{
    private static final String ENTRY_NAME = "entry.txt";

    @Test
    void weakClose()
    {
        // Create a reasonably big random string.
        byte[] contentBytes = new byte[16384];
        for (int i = 0; i < contentBytes.length; i++)
        {
            contentBytes[i] = (byte) ((i % 65) + 65);
        }
        String contentString = new String(contentBytes);

        // Create a temporary zip file.
        AtomicReference<File> tmpZipRef = new AtomicReference<>();
        assertDoesNotThrow(() -> {
        	File tmpZip = File.createTempFile("felix.test", ".zip");
        	tmpZipRef.set(tmpZip);
            tmpZip.deleteOnExit();
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmpZip));
            ZipEntry ze = new ZipEntry(ENTRY_NAME);
            zos.putNextEntry(ze);
            zos.write(contentBytes, 0, contentBytes.length);
            zos.close();
        }, "Unable to create temporary zip file: ");

        File tmpZip = tmpZipRef.get();
        assertDoesNotThrow(() -> {
            WeakZipFileFactory factory = new WeakZipFileFactory(1);
            WeakZipFile zipFile = factory.create(tmpZip);
            assertThat(factory.getZipZiles().contains(zipFile)).as("Zip file not recorded.").isTrue();
            assertThat(factory.getOpenZipZiles().contains(zipFile)).as("Open zip file not recorded.").isTrue();
            ZipEntry ze = zipFile.getEntry(ENTRY_NAME);
            assertThat(ze).as("Zip entry not found").isNotNull();
            byte[] firstHalf = new byte[contentBytes.length / 2];
            byte[] secondHalf = new byte[contentBytes.length - firstHalf.length];
            InputStream is = zipFile.getInputStream(ze);
            is.read(firstHalf);
            zipFile.closeWeakly();
            assertThat(factory.getZipZiles().contains(zipFile)).as("Zip file not recorded.").isTrue();
            assertThat(factory.getOpenZipZiles().contains(zipFile)).as("Open zip file still recorded.").isFalse();
            is.read(secondHalf);
            assertThat(factory.getZipZiles().contains(zipFile)).as("Zip file not recorded.").isTrue();
            assertThat(factory.getOpenZipZiles().contains(zipFile)).as("Open zip file not recorded.").isTrue();
            byte[] complete = new byte[firstHalf.length + secondHalf.length];
            System.arraycopy(firstHalf, 0, complete, 0, firstHalf.length);
            System.arraycopy(secondHalf, 0, complete, firstHalf.length, secondHalf.length);
            String completeString = new String(complete);
            assertThat(completeString).isEqualTo(contentString);
            zipFile.close();
        }, "Unable to read zip file entry: ");
    }
}
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
package org.apache.felix.configurator.impl.json;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.cm.json.io.ConfigurationReader.BinaryHandler;
import org.apache.felix.configurator.impl.json.JSONUtil.Report;

public class BinaryManager implements BinaryHandler {

    private final List<File> allFiles = new ArrayList<>();

    private final Map<String, List<File>> files = new HashMap<>();

    private final BinUtil.ResourceProvider provider;

    private final Report report;

    /**
     * Create a new instance
     * @param provider The bundle provider, might be {@code null}.
     */
    public BinaryManager(final BinUtil.ResourceProvider provider, final Report report) {
        this.provider = provider;
        this.report = report;
    }


    @Override
    public String handleBinaryValue(final String pid, final String key, final String value) {
        if (provider == null) {
            final String msg = provider.getIdentifier().concat(" : PID ").concat(pid)
                    .concat(" : Invalid value/type for configuration : ").concat(key).concat(" : ")
                    .concat("Binary files only allowed within a bundle");
            report.errors.add(msg);
        } else {
            final String path = value;
            final File filePath;
            try {
                filePath = BinUtil.extractFile(provider, pid, path);
                if ( filePath == null ) {
                    final String msg = provider.getIdentifier().concat(" : PID ").concat(pid)
                            .concat(" : Invalid value/type for configuration : ").concat(key).concat(" : ")
                            .concat("Entry ").concat(path).concat(" not found in bundle");
                    report.errors.add(msg);
                } else {
                    files.computeIfAbsent(pid, mapKey -> new ArrayList<>()).add(filePath);
                    allFiles.add(filePath);

                    return filePath.getAbsolutePath();
                }
            } catch ( final IOException ioe ) {
                final String msg = provider.getIdentifier().concat(" : PID ").concat(pid)
                        .concat(" : Invalid value/type for configuration : ").concat(key).concat(" : ")
                        .concat("Unable to read ")
                        .concat(path)
                        .concat(" and write to ")
                        .concat(BinUtil.binDirectory.getAbsolutePath())
                        .concat(" : ")
                        .concat(ioe.getMessage());
                report.errors.add(msg);
            }
        }
        return null;
    }

    public void cleanupFiles() {
        allFiles.stream().filter(f -> f.exists()).forEach(f -> f.delete());
        this.files.clear();
    }

    public List<File> flushFiles(final String pid) {
        return this.files.remove(pid);
    }
}

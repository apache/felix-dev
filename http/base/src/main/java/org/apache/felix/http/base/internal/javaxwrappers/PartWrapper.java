/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.http.base.internal.javaxwrappers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.http.Part;

/**
 * Part wrapper
 */
public class PartWrapper implements javax.servlet.http.Part {

    private final Part part;

    /**
     * Create new part
     * @param p Wrapped part
     */
    public PartWrapper(@NotNull final Part p) {
        this.part = p;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return this.part.getInputStream();
    }

    @Override
    public String getContentType() {
        return this.part.getContentType();
    }

    @Override
    public String getName() {
        return this.part.getName();
    }

    @Override
    public String getSubmittedFileName() {
        return this.part.getSubmittedFileName();
    }

    @Override
    public long getSize() {
        return this.part.getSize();
    }

    @Override
    public void write(String fileName) throws IOException {
        this.part.write(fileName);
    }

    @Override
    public void delete() throws IOException {
        this.part.delete();
    }

    @Override
    public String getHeader(final String name) {
        return this.part.getHeader(name);
    }

    @Override
    public Collection<String> getHeaders(final String name) {
        return this.part.getHeaders(name);
    }

    @Override
    public Collection<String> getHeaderNames() {
        return this.part.getHeaderNames();
    }
}

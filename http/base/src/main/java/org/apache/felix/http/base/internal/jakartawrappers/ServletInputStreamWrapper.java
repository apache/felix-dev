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
package org.apache.felix.http.base.internal.jakartawrappers;

import java.io.IOException;
import java.io.OutputStream;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

/**
 * Servlet input stream wrapper
 */
public class ServletInputStreamWrapper extends ServletInputStream {

    private final javax.servlet.ServletInputStream stream;

    /**
     * Create input stream
     * @param stream Wrapped stream
     */
    public ServletInputStreamWrapper(@NotNull final javax.servlet.ServletInputStream stream) {
        this.stream = stream;
    }

    @Override
    public int readLine(final byte[] b, final int off, final int len) throws IOException {
        return this.stream.readLine(b, off, len);
    }

    @Override
    public boolean isFinished() {
        return this.stream.isFinished();
    }

    @Override
    public boolean isReady() {
        return this.stream.isReady();
    }

    @Override
    public void setReadListener(final ReadListener readListener) {
        this.stream.setReadListener(new org.apache.felix.http.base.internal.javaxwrappers.ReadListenerWrapper(readListener));
    }

    @Override
    public int read() throws IOException {
        return this.stream.read();
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return this.stream.read(b);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return this.stream.read(b, off, len);
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return this.stream.readAllBytes();
    }

    @Override
    public byte[] readNBytes(final int len) throws IOException {
        return this.stream.readNBytes(len);
    }

    @Override
    public int readNBytes(final byte[] b, final int off, final int len) throws IOException {
        return this.stream.readNBytes(b, off, len);
    }

    @Override
    public long skip(final long n) throws IOException {
        return this.stream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return this.stream.available();
    }

    @Override
    public void close() throws IOException {
        this.stream.close();
    }

    @Override
    public synchronized void mark(final int readlimit) {
        this.stream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        this.stream.reset();
    }

    @Override
    public boolean markSupported() {
        return this.stream.markSupported();
    }

    @Override
    public long transferTo(final OutputStream out) throws IOException {
        return this.stream.transferTo(out);
    }


}

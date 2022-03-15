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

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

/**
 * Servlet output stream
 */
public class ServletOutputStreamWrapper extends ServletOutputStream {

    private final javax.servlet.ServletOutputStream stream;

    /**
     * Create new stream
     * @param stream wrapped stream
     */
    public ServletOutputStreamWrapper(@NotNull final javax.servlet.ServletOutputStream stream) {
        this.stream = stream;
    }

    @Override
    public void print(final String s) throws IOException {
        this.stream.print(s);
    }

    @Override
    public void print(final boolean b) throws IOException {
        this.stream.print(b);
    }

    @Override
    public void print(final char c) throws IOException {
        this.stream.print(c);
    }

    @Override
    public void print(final int i) throws IOException {
        this.stream.print(i);
    }

    @Override
    public void print(final long l) throws IOException {
        this.stream.print(l);
    }

    @Override
    public void print(final float f) throws IOException {
        this.stream.print(f);
    }

    @Override
    public void print(final double d) throws IOException {
        this.stream.print(d);
    }

    @Override
    public void println() throws IOException {
        this.stream.println();
    }

    @Override
    public void println(final String s) throws IOException {
        this.stream.println(s);
    }

    @Override
    public void println(final boolean b) throws IOException {
        this.stream.println(b);
    }

    @Override
    public void println(final char c) throws IOException {
        this.stream.println(c);
    }

    @Override
    public void println(final int i) throws IOException {
        this.stream.println(i);
    }

    @Override
    public void println(final long l) throws IOException {
        this.stream.println(l);
    }

    @Override
    public void println(final float f) throws IOException {
        this.stream.println(f);
    }

    @Override
    public void println(final double d) throws IOException {
        this.stream.println(d);
    }

    @Override
    public boolean isReady() {
        return this.stream.isReady();
    }

    @Override
    public void setWriteListener(final WriteListener writeListener) {
        this.stream.setWriteListener(new org.apache.felix.http.base.internal.javaxwrappers.WriteListenerWrapper(writeListener));
    }

    @Override
    public void write(final int b) throws IOException {
        this.stream.write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        this.stream.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        this.stream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        this.stream.flush();
    }

    @Override
    public void close() throws IOException {
        this.stream.close();
    }
}

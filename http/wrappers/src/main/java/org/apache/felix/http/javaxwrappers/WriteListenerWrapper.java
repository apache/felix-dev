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
package org.apache.felix.http.javaxwrappers;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.WriteListener;

/**
 * Write listener
 */
public class WriteListenerWrapper implements javax.servlet.WriteListener {

    private final WriteListener listener;

    /**
     * Create new lister
     * @param listener Wrapped listener
     */
    public WriteListenerWrapper(@NotNull final WriteListener listener) {
        this.listener = listener;
    }

    @Override
    public void onWritePossible() throws IOException {
        listener.onWritePossible();
    }

    @Override
    public void onError(final Throwable t) {
        listener.onError(t);
    }
}

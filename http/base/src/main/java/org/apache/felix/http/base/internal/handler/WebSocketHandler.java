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
package org.apache.felix.http.base.internal.handler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.felix.http.base.internal.logger.SystemLogger;

/**
 * Class that handles initialization for servlets extending JettyWebSocketServlet.
 */
public final class WebSocketHandler {
    private final AtomicBoolean lazyFirstInitCall = new AtomicBoolean(true);
    private final CountDownLatch initBarrier = new CountDownLatch(1);
    private final ServletHandler servletHandler;

    public WebSocketHandler(ServletHandler servletHandler) {
        this.servletHandler = servletHandler;
    }

    /*
     * Lazy initialization of the servlet.
     * Will only be called once for each servlet instance and is thread-safe.
     */
    public void lazyInit() {
        if (lazyFirstInitCall.compareAndSet(true, false)) {
            try {
                this.servletHandler.init();
            } catch (final Exception e) {
                SystemLogger.LOGGER.error(SystemLogger.formatMessage(
                        this.servletHandler.getServletInfo().getServiceReference(),
                        "Error calling init() lazy on servlet ".concat(
                                this.servletHandler.getServletInfo().getClassName(this.servletHandler.getServlet()))), e);
            } finally {
                initBarrier.countDown();
            }
        } else {
            // already initialized, await the first initialization
            try {
                initBarrier.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Returns true if the servlet should be initialized, false otherwise.
     * @return true if the servlet should be initialized, false otherwise
     */
    public boolean shouldInit() {
        return !lazyFirstInitCall.get() && initBarrier.getCount() > 0;
    }

    /**
     * Returns true if the servlet was initialized earlier, false otherwise.
     * @return true if the servlet should be destroyed, false otherwise
     */
    public boolean shouldDestroy() {
        if (!lazyFirstInitCall.get()){
            try {
                initBarrier.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }
        return false;
    }
}

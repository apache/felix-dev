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
package org.apache.felix.dm.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.felix.dm.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 * FELIX-6893: a thread which is executing a DispatchExecutor queue must run its reentrant tasks inline,
 * even if another thread has just finished executing the same queue.
 *
 * The pool thread is paused right after it has released the queue, while the test thread takes the
 * queue over and executes it from the caller thread (threadpool bypassed, like for REMOVED events).
 * Once the pool thread has completed its run, a reentrant task scheduled by the test thread must
 * still be executed inline.
 */
public class FELIX6893_DispatchExecutorReentrancyTest {
    private static final long TIMEOUT = 5;

    @Test
    public void testReentrantTaskRunsInlineAfterQueueHandOver() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            AtomicBoolean pauseOnRelease = new AtomicBoolean(true);
            CountDownLatch poolThreadReleasedQueue = new CountDownLatch(1);
            CountDownLatch callerThreadOwnsQueue = new CountDownLatch(1);

            DispatchExecutor exec = new DispatchExecutor(pool, new Logger(null)) {
                @Override
                void queueReleased() {
                    if (pauseOnRelease.compareAndSet(true, false)) {
                        poolThreadReleasedQueue.countDown();
                        await(callerThreadOwnsQueue);
                    }
                }
            };

            // The pool thread executes the queue, then pauses just after having released it.
            exec.execute(() -> {}, true);
            await(poolThreadReleasedQueue);

            AtomicBoolean reentrantTaskRunInline = new AtomicBoolean();
            CountDownLatch reentrantTaskDone = new CountDownLatch(1);

            // The test thread takes the queue over and executes it from the caller thread.
            exec.execute(() -> {
                callerThreadOwnsQueue.countDown();
                // Wait until the pool thread has completed its run (the single pool thread only runs this
                // latch once DispatchExecutor.run() has returned).
                CountDownLatch poolThreadDone = new CountDownLatch(1);
                pool.execute(poolThreadDone::countDown);
                await(poolThreadDone);

                boolean[] inline = new boolean[1];
                exec.execute(() -> {
                    inline[0] = true;
                    reentrantTaskDone.countDown();
                }, false);
                reentrantTaskRunInline.set(inline[0]);
            }, false);

            Assert.assertTrue("reentrant task not executed", reentrantTaskDone.await(TIMEOUT, TimeUnit.SECONDS));
            Assert.assertTrue("reentrant task was deferred instead of being executed inline", reentrantTaskRunInline.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

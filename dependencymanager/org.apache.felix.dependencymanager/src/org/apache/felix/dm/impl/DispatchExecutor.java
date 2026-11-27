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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.felix.dm.Logger;
import org.osgi.service.log.LogService;

/**
 * A lock free queue of tasks executed serially, in FIFO order, either in a shared threadpool or
 * from the caller thread. Many DispatchExecutor instances may run concurrently in the same threadpool.
 * <p>
 * A task scheduled by the thread which is currently executing the queue is run inline (immediately).
 * <p>
 * {@code m_pending} counts the tasks submitted but not yet executed: the thread which increments it
 * from 0 becomes the owner of the queue, and runs tasks until the counter drops back to 0.
 * Safe publication between the submitting threads and the owner is ensured by the concurrent queue
 * and the atomic counter.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class DispatchExecutor implements Executor, Runnable {
    private final ConcurrentLinkedQueue<Runnable> m_tasks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger m_pending = new AtomicInteger();
    private final AtomicReference<Thread> m_executingThread = new AtomicReference<>();
    private final Executor m_threadPool;
    private final Logger m_logger;

    public DispatchExecutor(Executor threadPool, Logger logger) {
        m_threadPool = threadPool;
        m_logger = logger;
    }

    /**
     * Executes a task in the threadpool (or inline if called from the thread executing this queue).
     */
    @Override
    public void execute(Runnable task) {
        execute(task, true);
    }

    /**
     * Executes a task inline if called from the thread executing this queue. Otherwise, enqueues it and,
     * if the queue is idle, executes the queue in the threadpool, or from the caller thread if
     * {@code threadpool} is false.
     */
    public void execute(Runnable task, boolean threadpool) {
        if (m_executingThread.get() == Thread.currentThread()) {
            runTask(task);
            return;
        }
        m_tasks.offer(task);
        if (m_pending.getAndIncrement() == 0) {
            if (threadpool) {
                try {
                    m_threadPool.execute(this);
                } catch (RejectedExecutionException e) {
                    run(); // the threadpool is stopped (framework shutting down?): run from the caller thread
                }
            } else {
                run();
            }
        }
    }

    /**
     * Runs the queued tasks until the queue is empty. Only called by the owner of the queue.
     */
    @Override
    public void run() {
        Thread self = Thread.currentThread();
        m_executingThread.set(self);
        do {
            runTask(m_tasks.poll());
        } while (m_pending.decrementAndGet() > 0);
        queueReleased();
        // Another thread may already own the queue: only clear the executing thread if it is still us.
        m_executingThread.compareAndSet(self, null);
    }

    /**
     * Called by the thread which has just executed this queue, right after it has released the queue
     * (so another thread may already be executing it). Does nothing by default: only used by tests.
     */
    void queueReleased() {
    }

    /**
     * Runs a task. Never throws: an exception escaping from run() would leave m_pending
     * above 0, and the queue would never be executed again.
     */
    private void runTask(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            try {
                m_logger.log(LogService.LOG_ERROR, "Error processing tasks", t);
            } catch (Throwable ignored) {
            }
        }
    }
}

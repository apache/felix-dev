/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.felix.hc.core.impl.scheduling.cron.embedded;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EmbeddedCronScheduler implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /* Object used for internal locking/notifications */
    private final Object monitor = new Object();

    /* List of managed tasks */
    private final Map<String, EmbeddedCronSchedulerTask> tasks = new HashMap<>();

    /* Task executor instance */
    public final ExecutorService tasksExecutor;

    /* Thread that makes all scheduling job */
    public final SchedulerThread schedulerThread;

    /* Flag to check if the scheduler is active */
    private final AtomicBoolean isActive;

    public EmbeddedCronScheduler(final ExecutorService tasksExecutor, final long initialDelay, final long checkInterval,
            final TimeUnit timeUnit, final String schedulerThreadName) {
        schedulerThread = new SchedulerThread(initialDelay, checkInterval, timeUnit, schedulerThreadName);
        this.tasksExecutor = tasksExecutor;
        isActive = new AtomicBoolean(true);
        schedulerThread.start();
    }

    public void schedule(final EmbeddedCronJob cronJob) {
        synchronized (monitor) {
            tasks.put(cronJob.name(), new EmbeddedCronSchedulerTask(cronJob, new EmbeddedCronParser(cronJob.cron())));
        }
    }

    public void remove(final String name) {
        synchronized (monitor) {
            tasks.remove(name);
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        isActive.set(false);
        synchronized (monitor) {
            monitor.notify();
        }
        schedulerThread.interrupt();
    }

    public boolean isShutdown() {
        return !isActive.get();
    }

    public Collection<EmbeddedCronSchedulerTask> getTasks() {
        return tasks.values();
    }

    public class SchedulerThread extends Thread {
        public final long initialDelay;
        public final long checkInterval;
        public final TimeUnit timeUnit;

        private SchedulerThread(final long initialDelay, final long checkInterval, final TimeUnit timeUnit,
                final String threadName) {
            if (initialDelay < 0) {
                throw new IllegalArgumentException("initialDelay < 0. Value: " + initialDelay);
            }
            if (checkInterval <= 0) {
                throw new IllegalArgumentException("checkInterval must be > 0. Value: " + checkInterval);
            }
            if (timeUnit == null) {
                throw new IllegalArgumentException("timeUnit is null");
            }
            this.initialDelay = initialDelay;
            this.checkInterval = checkInterval;
            this.timeUnit = timeUnit;
            setName(threadName);
            setDaemon(true);
        }

        @Override
        public void run() {
            if (initialDelay > 0) {
                pause(timeUnit.toMillis(initialDelay));
            }
            final long checkIntervalMillis = timeUnit.toMillis(checkInterval);
            while (isActive.get()) {
                try {
                    checkAndExecute();
                    pause(checkIntervalMillis);
                } catch (final Exception e) {
                    logger.error("Got internal error that must never happen!", e);
                }
            }
        }

        private void pause(final long checkIntervalMillis) {
            try {
                synchronized (monitor) {
                    monitor.wait(checkIntervalMillis);
                }
            } catch (final InterruptedException e) {
                logger.error("Got unexpected interrupted exception! Ignoring", e);
                Thread.currentThread().interrupt();
            }
        }

        private void checkAndExecute() {
            synchronized (monitor) {
                final long currentTime = System.currentTimeMillis();
                for (final Entry<String, EmbeddedCronSchedulerTask> entry : tasks.entrySet()) {
                    final EmbeddedCronSchedulerTask task = entry.getValue();
                    if (task.nextExecutingTime >= currentTime) {
                        continue;
                    }
                    if (task.executing) {
                        task.nextExecutingTime = task.cronParser.next(currentTime);
                        continue;
                    }
                    try {
                        task.lastExecutingTime = System.currentTimeMillis();
                        tasksExecutor.execute(task);
                        task.executing = true;
                    } catch (final RejectedExecutionException e) {
                        logger.error("Failed to start task: {}", task, e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

}
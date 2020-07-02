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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.apache.felix.hc.core.impl.scheduling.AsyncJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs health checks/monitors that are configured with a cron expression for
 * asynchronous execution.
 */
public final class AsyncEmbeddedCronJob extends AsyncJob {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncEmbeddedCronJob.class);

    private final String id;
    private final String cronExpression;
    private final EmbeddedCronParser cronParser;
    
    private final EmbeddedCronScheduler scheduler;

    private volatile boolean executing = false;
    private volatile long lastExecutingTime = 0;
    private volatile long nextExecutingTime = 0;

    public AsyncEmbeddedCronJob(final Runnable runnable, final EmbeddedCronSchedulerProvider embeddedCronSchedulerProvider,
            final String id, final String cronExpression) {
        super(runnable);
        this.id = id;

        this.scheduler = embeddedCronSchedulerProvider.getScheduler();
        
        this.cronExpression = cronExpression;
        this.cronParser= new EmbeddedCronParser(cronExpression);
        nextExecutingTime = cronParser.next(System.currentTimeMillis());
    }

    @Override
    public boolean schedule() {
        try {
            scheduler.schedule(this);
            LOG.info("Scheduled job {} with trigger {}", id, cronExpression);
            return true;
        } catch (final Exception e) {
            LOG.error("Could not schedule job for {}", runnable, e);
            return false;
        }
    }

    @Override
    public boolean unschedule() {

        LOG.debug("Unscheduling job {}", id);
        try {
            scheduler.remove(this);
            return true;
        } catch (final Exception e) {
            LOG.error("Could not unschedule job for {}", id, e);
            return false;
        }
    }

    @Override
    public String toString() {
        return "[Async embedded cron job for " + runnable + "]";
    }

    public String getId() {
        return id;
    }

    public long getLastExecutingTime() {
        return lastExecutingTime;
    }

    public long getNextExecutingTime() {
        return nextExecutingTime;
    }

    public boolean isExecuting() {
        return executing;
    }
    
    public void checkAndExecute(ExecutorService tasksExecutor) {
        final long currentTime = System.currentTimeMillis();
        if (nextExecutingTime >= currentTime) {
            return;
        }
        if(executing) {
            nextExecutingTime = cronParser.next(System.currentTimeMillis());
            LOG.trace("Cron task {} would be fired but is still executing, skipping this execution, next execution: {}", id, nextExecutingTime);
            return;
        }

        try {
            lastExecutingTime = currentTime;
            executing = true;
            tasksExecutor.execute(() -> {
                try {
                    runnable.run();
                } catch (final Exception e) {
                    LOG.error("Exception while executing cron task {}", id, e);
                } finally {
                    nextExecutingTime = cronParser.next(System.currentTimeMillis());
                    executing = false;
                }
            });
            
        } catch (final RejectedExecutionException e) {
            executing = false;
            LOG.error("Failed to start cron task: {}", id, e);
        }
    }
    
}
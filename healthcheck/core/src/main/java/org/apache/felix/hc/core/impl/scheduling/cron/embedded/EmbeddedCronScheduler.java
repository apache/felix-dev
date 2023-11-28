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

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EmbeddedCronScheduler implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedCronScheduler.class);

    /* List of managed tasks */
    private final Map<String, AsyncEmbeddedCronJob> tasks = new ConcurrentHashMap<>();

    /* Task executor service */
    public final ScheduledExecutorService scheduledExecutorService;

    private String schedulerThreadName;
    
    public EmbeddedCronScheduler(final ScheduledExecutorService scheduledExecutorService, final long initialDelay, final long checkInterval,
            final TimeUnit timeUnit, final String schedulerThreadName) {
        
        this.scheduledExecutorService = scheduledExecutorService;
        scheduledExecutorService.scheduleAtFixedRate(this, initialDelay, checkInterval, timeUnit);
        
        this.schedulerThreadName = schedulerThreadName;
    }

    public void schedule(AsyncEmbeddedCronJob cronJob) {
        tasks.put(cronJob.getId(), cronJob);
    }

    public void remove(AsyncEmbeddedCronJob cronJob) {
        tasks.remove(cronJob.getId());
    }

    @Override
    public void run() {
        String threadNameToRestore = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName(schedulerThreadName);
            LOG.trace("tasks: {}", tasks);
            
            for (final Entry<String, AsyncEmbeddedCronJob> entry : tasks.entrySet()) {
                final AsyncEmbeddedCronJob job = entry.getValue();
                job.checkAndExecute(scheduledExecutorService);
            }
        } finally {
            Thread.currentThread().setName(threadNameToRestore);
        }

    }

}
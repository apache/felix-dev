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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class EmbeddedCronSchedulerTask implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    final EmbeddedCronJob cronJob;
    final EmbeddedCronParser cronParser;

    volatile boolean executing;
    volatile long lastExecutingTime = 0;
    volatile long nextExecutingTime = 0;

    public EmbeddedCronSchedulerTask(final EmbeddedCronJob cronJob, final EmbeddedCronParser cronParser) {
        this.cronJob = cronJob;
        this.cronParser = cronParser;
    }

    @Override
    public void run() {
        try {
            cronJob.run();
        } catch (final Exception e) {
            logger.error("Exception while executing cron task", e);
        } finally {
            nextExecutingTime = cronParser.next(System.currentTimeMillis());
            executing = false;
        }
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

    @Override
    public String toString() {
        return "EmbeddedCronSchedulerTask [" + cronJob.name() + "]";
    }
}
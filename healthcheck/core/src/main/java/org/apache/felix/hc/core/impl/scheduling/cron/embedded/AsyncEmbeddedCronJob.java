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

import org.apache.felix.hc.core.impl.scheduling.AsyncJob;
import org.apache.felix.hc.core.impl.scheduling.cron.HealthCheckCronScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs health checks that are configured with a cron expression for
 * asynchronous execution.
 */
public final class AsyncEmbeddedCronJob extends AsyncJob {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EmbeddedCronJob cronJob;
    private final EmbeddedCronScheduler scheduler;

    public AsyncEmbeddedCronJob(final Runnable runnable, final HealthCheckCronScheduler healtchCheckScheduler,
            final String id, final String cronExpression) {
        super(runnable);
        cronJob = new HealthCheckJob(cronExpression, id);
        scheduler = (EmbeddedCronScheduler) healtchCheckScheduler.getScheduler();
    }

    @Override
    public boolean schedule() {
        try {
            scheduler.schedule(cronJob);
            logger.info("Scheduled job {} with trigger {}", cronJob.name(), cronJob.cron());
            return true;
        } catch (final Exception e) {
            logger.error("Could not schedule job for {}", runnable, e);
            return false;
        }
    }

    @Override
    public boolean unschedule() {
        final String name = cronJob.name();
        logger.debug("Unscheduling job {}", name);
        try {
            scheduler.remove(name);
            return true;
        } catch (final Exception e) {
            logger.error("Could not unschedule job for {}", name, e);
            return false;
        }
    }

    @Override
    public String toString() {
        return "[Async embedded cron job for " + runnable + "]";
    }

    private class HealthCheckJob implements EmbeddedCronJob {

        private final String name;
        private final String cronExpression;

        public HealthCheckJob(final String cronExpression, final String name) {
            this.name = name;
            this.cronExpression = cronExpression;
        }

        @Override
        public String cron() {
            return cronExpression;
        }

        @Override
        public String name() {
            return "job-hc-" + name;
        }

        @Override
        public void run() throws Exception {
            runnable.run();
        }
    }

}
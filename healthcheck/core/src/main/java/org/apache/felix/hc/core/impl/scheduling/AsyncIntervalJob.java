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
package org.apache.felix.hc.core.impl.scheduling;

import java.util.concurrent.ScheduledFuture;

import org.apache.felix.hc.core.impl.executor.HealthCheckExecutorThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs health checks that are configured with an interval (ScheduledThreadPoolExecutor.scheduleAtFixedRate()) for asynchronous execution.  */
public class AsyncIntervalJob extends AsyncJob {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncIntervalJob.class);

    private final HealthCheckExecutorThreadPool healthCheckExecutorThreadPool;
    private final Long asyncIntervalInSec;
    private final Long asyncInitialDelayForIntervalInSec;
    
    private ScheduledFuture<?> scheduleFuture;

    public AsyncIntervalJob(Runnable runnable, HealthCheckExecutorThreadPool healthCheckExecutorThreadPool, Long asyncIntervalInSec, Long asyncInitialDelayForIntervalInSec) {
        super(runnable);
        this.healthCheckExecutorThreadPool = healthCheckExecutorThreadPool;
        this.asyncIntervalInSec = asyncIntervalInSec;
        this.asyncInitialDelayForIntervalInSec = asyncInitialDelayForIntervalInSec;
    }

    public boolean schedule() {
        scheduleFuture = healthCheckExecutorThreadPool.scheduleAtFixedRate(runnable, asyncIntervalInSec, asyncInitialDelayForIntervalInSec);
        LOG.info("Scheduled job {} for execution every {}sec", this, asyncIntervalInSec);
        return true;
    }

    @Override
    public boolean unschedule() {

        if (scheduleFuture != null) {
            LOG.debug("Unscheduling async job for {}", runnable);
            return scheduleFuture.cancel(false);
        } else {
            LOG.debug("No scheduled future for {} exists", runnable);
            return false;
        }
    }
    
    @Override
    public String toString() {
        return "[Async interval job for " + runnable + "]";
    }

}
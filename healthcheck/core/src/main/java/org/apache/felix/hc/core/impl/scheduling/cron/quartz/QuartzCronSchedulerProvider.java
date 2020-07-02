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
package org.apache.felix.hc.core.impl.scheduling.cron.quartz;

import org.apache.felix.hc.core.impl.executor.HealthCheckExecutorThreadPool;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Component without direct quartz imports (can always start) that will provide a QuartzCronScheduler on demand. */
@Component(service = QuartzCronSchedulerProvider.class)
public class QuartzCronSchedulerProvider {

    private static final Logger LOG = LoggerFactory.getLogger(QuartzCronSchedulerProvider.class);

    private QuartzCronScheduler quartzCronScheduler = null;

    @Reference
    HealthCheckExecutorThreadPool healthCheckExecutorThreadPool;

    public synchronized QuartzCronScheduler getQuartzCronScheduler()  {
        if (quartzCronScheduler == null) {
            quartzCronScheduler = new QuartzCronScheduler(healthCheckExecutorThreadPool);
            LOG.info("Created QuartzCronScheduler");
        }
        return quartzCronScheduler;
    }

    @Deactivate
    protected synchronized void deactivate() {
        if (quartzCronScheduler != null) {
            quartzCronScheduler.shutdown();
            quartzCronScheduler = null;
            LOG.info("Shutdown of QuartzCronScheduler");
        }
    }

}

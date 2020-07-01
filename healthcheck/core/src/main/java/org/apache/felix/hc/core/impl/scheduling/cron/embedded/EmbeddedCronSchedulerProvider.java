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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.felix.hc.core.impl.scheduling.cron.HealthCheckCronScheduler.CRON_TYPE_PROPERTY;
import static org.apache.felix.hc.core.impl.scheduling.cron.embedded.EmbeddedCronSchedulerProvider.CRON_TYPE_EMBEDDED;

import java.util.concurrent.TimeUnit;

import org.apache.felix.hc.core.impl.executor.HealthCheckExecutorThreadPool;
import org.apache.felix.hc.core.impl.scheduling.cron.HealthCheckCronScheduler;
import org.apache.felix.hc.core.impl.scheduling.cron.embedded.EmbeddedCronSchedulerProvider.Configuration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Designate(ocd = Configuration.class)
@Component(enabled = false, property = CRON_TYPE_PROPERTY + "=" + CRON_TYPE_EMBEDDED)
public final class EmbeddedCronSchedulerProvider implements HealthCheckCronScheduler {

    public static final String CRON_TYPE_EMBEDDED = "embedded";
    public static final String HC_SCHEDULER_NAME = "felix.hc.embedded.scheduler";

    @ObjectClassDefinition(name = "Embedded Cron Scheduler Configuration")
    @interface Configuration {
        @AttributeDefinition(name = "Initial Delay", description = "The initial delay before the embedded cron scheduler thread becomes active")
        long initialDelay() default 10L;

        @AttributeDefinition(name = "Check Interval Delay", description = "The delay to check for a scheduled task to satisfy the cron expression")
        long checkInterval() default 3L;

        @AttributeDefinition(name = "Time unit", description = "The time unit of the delays as specified in this configuration")
        TimeUnit timeUnit() default SECONDS;

        @AttributeDefinition(name = "Scheduler Name", description = "The embedded cron scheduler thread name")
        String schedulerName() default HC_SCHEDULER_NAME;
    }

    private EmbeddedCronScheduler scheduler;

    @Reference
    private HealthCheckExecutorThreadPool threadPool;

    @Activate
    void activate(final Configuration configuration) {
        scheduler = new EmbeddedCronScheduler( //
                threadPool.getExecutor(), //
                configuration.initialDelay(), //
                configuration.checkInterval(), //
                configuration.timeUnit(), //
                configuration.schedulerName());
    }

    @Deactivate
    void deactivate() {
        scheduler.shutdown();
        scheduler = null;
    }

    @Override
    public EmbeddedCronScheduler getScheduler() {
        return scheduler;
    }

}
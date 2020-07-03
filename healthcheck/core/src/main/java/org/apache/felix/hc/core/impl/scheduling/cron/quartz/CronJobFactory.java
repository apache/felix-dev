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

import static org.apache.felix.hc.core.impl.scheduling.cron.HealthCheckCronScheduler.CRON_TYPE_PROPERTY;
import static org.apache.felix.hc.core.impl.scheduling.cron.quartz.QuartzCronSchedulerProvider.CRON_TYPE_QUARTZ;

import org.apache.felix.hc.core.impl.scheduling.AsyncJob;
import org.apache.felix.hc.core.impl.scheduling.cron.HealthCheckCronScheduler;
import org.apache.felix.hc.core.impl.scheduling.cron.embedded.AsyncEmbeddedCronJob;
import org.osgi.service.component.ComponentServiceObjects;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = CronJobFactory.class)
public final class CronJobFactory {

    @Reference
    private ComponentServiceObjects<HealthCheckCronScheduler> cronSchedulerProvider;

    public AsyncJob createNewJob(final Runnable runnable, final String id, final String group,
            final String cronExpression) {
        final String property = (String) cronSchedulerProvider.getServiceReference().getProperty(CRON_TYPE_PROPERTY);
        final HealthCheckCronScheduler cronScheduler = cronSchedulerProvider.getService();
        if (CRON_TYPE_QUARTZ.equals(property)) {
            return new AsyncQuartzCronJob(runnable, cronScheduler, id, group, cronExpression);
        } else {
            return new AsyncEmbeddedCronJob(runnable, cronScheduler, id, cronExpression);
        }
    }

}

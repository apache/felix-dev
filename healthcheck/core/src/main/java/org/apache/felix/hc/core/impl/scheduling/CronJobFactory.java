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

import org.apache.felix.hc.core.impl.executor.HealthCheckExecutorThreadPool;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Component without direct quartz imports (can always start) that will provide a QuartzCronScheduler on demand. */
@Component(service = CronJobFactory.class)
public class CronJobFactory {

    private static final Logger LOG = LoggerFactory.getLogger(CronJobFactory.class);
    private static final String CLASS_FROM_QUARTZ_FRAMEWORK = "org.quartz.CronTrigger";

    @Reference
    HealthCheckExecutorThreadPool healthCheckExecutorThreadPool;

    org.quartz.Scheduler quartzScheduler;

    public AsyncJob getAsyncCronJob(Runnable runnable, String id, String group, String cronExpression) {

        if(isQuartzAvailable()) {
            return new AsyncQuartzCronJob(runnable, getQuartzCronScheduler(), id, group, cronExpression);
        } else {
            return new AsyncSimpleCronJob(runnable, cronExpression);
        }
    }
    
    private synchronized org.quartz.Scheduler getQuartzCronScheduler() {
        if (quartzScheduler == null) {
            QuartzCronSchedulerBuilder quartzCronSchedulerBuilder = new QuartzCronSchedulerBuilder(healthCheckExecutorThreadPool);
            quartzScheduler = quartzCronSchedulerBuilder.getScheduler();
            LOG.info("Created quartz scheduler health check core bundle");
        }
        return quartzScheduler;
    }

    
    public boolean isQuartzAvailable() {
        return classExists(CLASS_FROM_QUARTZ_FRAMEWORK);
    }

    @Deactivate
    protected synchronized void deactivate() {
        // simpleScheduler follows its own SCR lifecycle
        
        if (quartzScheduler != null) { // quartz scheduler needs to be shut down
            try {
                quartzScheduler.shutdown();
                LOG.info("QuartzCronScheduler shutdown");
            } catch (SchedulerException e) {
                LOG.info("QuartzCronScheduler shutdown with exception: "+e);
            } finally {
               quartzScheduler = null;
            }
        }
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

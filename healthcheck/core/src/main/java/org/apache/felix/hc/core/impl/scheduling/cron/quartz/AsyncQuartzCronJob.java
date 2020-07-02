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

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import org.apache.felix.hc.core.impl.scheduling.AsyncJob;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Async job to be used by async health checks and async monitor. 
 * 
 * This implementation uses quartz to support the cron syntax (which is not supported by executors from standard java java.util.concurrent
 * package) */
public class AsyncQuartzCronJob extends AsyncJob {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncQuartzCronJob.class);

    private static final String JOB_DATA_KEY_JOB = "asyncHcJob";

    private final String id;
    private final String group;
    private final String cronExpression;

    private final Scheduler quartzScheduler;

    private JobKey jobKey = null;

    public AsyncQuartzCronJob(Runnable runnable, QuartzCronSchedulerProvider quartzCronSchedulerProvider, String id, String group, String cronExpression) {
        super(runnable);
        this.quartzScheduler = quartzCronSchedulerProvider.getQuartzCronScheduler().getScheduler();
        this.id = id;
        this.group = group;
        this.cronExpression = cronExpression;
    }

    public JobKey getJobKey() {
        return jobKey;
    }

    private JobDetail getQuartzJobDetail() {
        JobDataMap jobData = new JobDataMap();
        jobData.put(JOB_DATA_KEY_JOB, runnable);

        JobDetail job = newJob(AsyncQuartzCronJob.QuartzJob.class).setJobData(jobData)
                .withIdentity(id, group)
                .build();

        jobKey = job.getKey();

        return job;
    }

    public boolean schedule() {

        try {

            JobDetail job = getQuartzJobDetail();
            CronTrigger cronTrigger = newTrigger().withSchedule(cronSchedule(cronExpression)).forJob(job)
                    .build();

            quartzScheduler.scheduleJob(job, cronTrigger);
            LOG.info("Scheduled job {} with trigger {}", job, cronTrigger);
            return true;
        } catch (SchedulerException e) {
            LOG.error("Could not schedule job for " + runnable + ": " + e, e);
            return false;
        }

    }

    @Override
    public boolean unschedule() {
        LOG.debug("Unscheduling job {}", jobKey);
        try {
            quartzScheduler.deleteJob(jobKey);
            return true;
        } catch (SchedulerException e) {
            LOG.error("Could not unschedule job for " + jobKey + ": " + e, e);
            return false;
        }
    }

    @Override
    public String toString() {
        return "[Async quartz job for " + runnable + "]";
    }
    
    // quartz forces to pass in a class object (and not an instance), hence this helper class is needed
    public static class QuartzJob implements Job {

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            Runnable ayncJob = (Runnable) context.getJobDetail().getJobDataMap().get(JOB_DATA_KEY_JOB);
            ayncJob.run();
        }
    }

}
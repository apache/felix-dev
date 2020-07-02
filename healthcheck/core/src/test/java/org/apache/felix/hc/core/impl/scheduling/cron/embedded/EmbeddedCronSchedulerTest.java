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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedCronSchedulerTest {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedCronScheduler.class);

    @Spy
    EmbeddedCronSchedulerProvider embeddedCronSchedulerProvider = new EmbeddedCronSchedulerProvider();
    
    EmbeddedCronScheduler cronScheduler;
    
    ScheduledExecutorService scheduledExecutorService;
    
    @Before
    public void setup() {
        initMocks(this);
        
        scheduledExecutorService = Executors.newScheduledThreadPool(10);
        cronScheduler = new EmbeddedCronScheduler(scheduledExecutorService, 0, 50, TimeUnit.MILLISECONDS /* for exact fires in JUnit */, "T1");
        doReturn(cronScheduler).when(embeddedCronSchedulerProvider).getScheduler();
    }

    @After
    public void teardown() {
        scheduledExecutorService.shutdown();
    }
    
    public static class SampleCounterJob implements Runnable {

        public volatile int counter = 0;
        
        private final String name;
        
        public SampleCounterJob(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            counter++;
            LOG.info("{}  Name: {} counter: {}", new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()), name, counter);
        }

    }


    @Test
    public void checkSchedulerCheckMultipleThreads() throws InterruptedException {

        List<SampleCounterJob> counterRunnables = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {

            String name = "abc" + i;
            SampleCounterJob counterRunnable = new SampleCounterJob(name);
            AsyncEmbeddedCronJob job = new AsyncEmbeddedCronJob(counterRunnable, embeddedCronSchedulerProvider, name, "* * * * * *");
            job.schedule();
            counterRunnables.add(counterRunnable);
        }
        
        int waitForSeconds = 3;
        try {
            Thread.sleep(waitForSeconds * 1000); 
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
        
        for (SampleCounterJob counterRunnable : counterRunnables) {
            // expect each to fire waitForSeconds times for cron "* * * * * *"
            // no matter at what exact milliseconds we are
            assertEquals(waitForSeconds, counterRunnable.counter);
        }
        
        

    }


    @Test
    public void checkScheduledPeriodsOverlap() throws InterruptedException {
        final int callCostInMs = 1500;
        final AtomicInteger count = new AtomicInteger();

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        LOG.info("Test Start {}", simpleDateFormat.format(new Date()));
        
        Runnable jobRunnable = () -> {
            count.incrementAndGet();
            LOG.info("Execution Start {}", simpleDateFormat.format(new Date()));
            try {
                Thread.sleep(callCostInMs);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            LOG.info("Execution End {}", simpleDateFormat.format(new Date()));
        };
        
        AsyncEmbeddedCronJob job = new AsyncEmbeddedCronJob(jobRunnable, embeddedCronSchedulerProvider, "test", "* * * * * *");
        job.schedule();
        // it can take up to 1000ms until first fire (depending on start time of JUnit test)
        // as job runs 1500ms the second fire has to be cancelled
        // the third fire has to run again 
        // a potential 4th fire (with 3000ms) has too be cancelled (as third is still running)
        // => exact two executions
        Thread.sleep(3000);
        LOG.info("Test Assert {}", simpleDateFormat.format(new Date()));
        assertEquals("A job running "+callCostInMs+"ms in one execution fired every second can only be executed twice", 2, count.get());

    }


}
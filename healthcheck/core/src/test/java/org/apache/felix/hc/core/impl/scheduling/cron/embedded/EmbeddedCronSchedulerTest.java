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
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.felix.hc.core.impl.scheduling.cron.embedded.EmbeddedSampleCronJob1.Callback;
import org.junit.Test;

public class EmbeddedCronSchedulerTest {

    private static final Object SYNC_OBJECT = new Object();

    @Test
    public void checkSchedulerCheckSingleThread() throws InterruptedException {
        try (final EmbeddedCronScheduler scheduler = new EmbeddedCronScheduler(Executors.newFixedThreadPool(1), 0, 1,
                TimeUnit.SECONDS, "T1")) {
            final int nIterations = 3;

            final EmbeddedSampleCronJob1 service = new EmbeddedSampleCronJob2(s -> {
                if (s.counter == nIterations) {
                    scheduler.shutdown();
                    synchronized (SYNC_OBJECT) {
                        SYNC_OBJECT.notify();
                    }
                }
            });
            assertEquals(0, service.counter);
            final long t0 = System.currentTimeMillis();
            scheduler.schedule(service);

            synchronized (SYNC_OBJECT) {
                SYNC_OBJECT.wait(5_000L);
            }
            final int counter = service.counter;
            final long totalTime = System.currentTimeMillis() - t0;

            assertEquals(nIterations, counter);
            assertTrue(scheduler.isShutdown());
            assertTrue(totalTime < 4_000);

            Thread.sleep(2_000L);
            assertEquals(counter, service.counter);
        }
    }

    @Test
    public void checkSchedulerCheckMultipleThreads() throws InterruptedException {
        try (EmbeddedCronScheduler scheduler = new EmbeddedCronScheduler(Executors.newFixedThreadPool(10), 0, 1,
                TimeUnit.SECONDS, "T2")) {
            EmbeddedSampleCronJob1.staticCounter = 0;
            final int nIterations = 3;

            final Callback c = s -> {
                try {
                    Thread.sleep(3000);
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (EmbeddedSampleCronJob1.staticCounter == nIterations) {
                    synchronized (SYNC_OBJECT) {
                        SYNC_OBJECT.notify();
                    }
                }
            };
            synchronized (SYNC_OBJECT) {
                for (int i = 0; i < 10; i++) {
                    scheduler.schedule(new EmbeddedSampleCronJob1(c, "abc" + i));
                }
                SYNC_OBJECT.wait(5 * 1000L);
            }
            scheduler.shutdown();
            assertTrue("Number of iterations: " + EmbeddedSampleCronJob1.staticCounter + ">=" + nIterations,
                    EmbeddedSampleCronJob1.staticCounter >= nIterations);
        }
    }

    @Test
    public void checkScheduledPeriodsOverlap() throws InterruptedException {
        final int callCostInSeconds = 3;
        final AtomicInteger count = new AtomicInteger();
        try (EmbeddedCronScheduler scheduler = new EmbeddedCronScheduler(Executors.newFixedThreadPool(1), 0, 200,
                TimeUnit.MILLISECONDS, "T10")) {
            final EmbeddedCronJob scheduled = new EmbeddedCronJob() {
                @Override
                public void run() throws Exception {
                    count.incrementAndGet();
                    Thread.sleep(callCostInSeconds * 1000);
                }

                @Override
                public String cron() {
                    return "* * * * * *";
                }

                @Override
                public String name() {
                    return "test";
                }
            };
            scheduler.schedule(scheduled);

            final int nCyclesToCheck = 5;
            Thread.sleep(nCyclesToCheck * callCostInSeconds * 1000L);
            final int minCyclesExpected = nCyclesToCheck - 1;
            assertTrue(
                    "At least " + minCyclesExpected + " cycles must be started at this point, actual: " + count.get(),
                    count.get() == nCyclesToCheck || count.get() == nCyclesToCheck - 1);
        }
    }
}
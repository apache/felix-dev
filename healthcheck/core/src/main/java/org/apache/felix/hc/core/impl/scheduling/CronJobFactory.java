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

import static org.osgi.framework.namespace.PackageNamespace.PACKAGE_NAMESPACE;

import org.apache.felix.hc.core.impl.executor.HealthCheckExecutorThreadPool;
import org.apache.felix.hc.core.impl.scheduling.cron.embedded.AsyncEmbeddedCronJob;
import org.apache.felix.hc.core.impl.scheduling.cron.embedded.EmbeddedCronSchedulerProvider;
import org.apache.felix.hc.core.impl.scheduling.cron.quartz.AsyncQuartzCronJob;
import org.apache.felix.hc.core.impl.scheduling.cron.quartz.QuartzCronSchedulerProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Component without direct quartz imports (can always start) that will provide a QuartzCronScheduler on demand. */
@Component(service = CronJobFactory.class)
public class CronJobFactory {
    private static final Logger LOG = LoggerFactory.getLogger(CronJobFactory.class);

    private static final String QUARTZ_PACKAGE_PREFIX = "org.quartz";
    
    @Reference
    private HealthCheckExecutorThreadPool healthCheckExecutorThreadPool;

    @Reference
    private QuartzCronSchedulerProvider quartzCronSchedulerProvider;
    
    @Reference
    private EmbeddedCronSchedulerProvider embeddedCronSchedulerProvider;
    
    boolean isQuartzAvailable;

    @Activate
    protected synchronized void activate(BundleContext bundleContext) {
        isQuartzAvailable = isQuartzAvailable(bundleContext);
        LOG.info("Quartz is " + (isQuartzAvailable?"":"NOT ")+"available for scheduling cron jobs");
    }

    public AsyncJob createAsyncCronJob(Runnable runnable, String id, String group, String cronExpression) {

        if(isQuartzAvailable) {
            return new AsyncQuartzCronJob(runnable, quartzCronSchedulerProvider, id, group, cronExpression);
        } else {
            return new AsyncEmbeddedCronJob(runnable, embeddedCronSchedulerProvider, id, cronExpression);
        }
    }

    private boolean isQuartzAvailable(BundleContext bundleContext) {
        final BundleWiring wiring = bundleContext.getBundle().adapt(BundleWiring.class);
        for (final BundleWire wire : wiring.getRequiredWires(PACKAGE_NAMESPACE)) {
            final String pkg = (String) wire.getCapability().getAttributes().get(PACKAGE_NAMESPACE);
            if (pkg.startsWith(QUARTZ_PACKAGE_PREFIX)) {
                return true;
            }
        }
        return false;
    }

}

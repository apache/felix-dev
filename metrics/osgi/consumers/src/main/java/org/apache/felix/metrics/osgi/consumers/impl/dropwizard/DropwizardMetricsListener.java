/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.metrics.osgi.consumers.impl.dropwizard;

import java.util.ArrayList;
import java.util.List;

import org.apache.felix.metrics.osgi.StartupMetrics;
import org.apache.felix.metrics.osgi.StartupMetricsListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;

@Component
@Designate(ocd = DropwizardMetricsListener.Config.class)
public class DropwizardMetricsListener implements StartupMetricsListener {

    @ObjectClassDefinition(name = "Apache Felix Dropwizard Startup Metrics Listener")
    public @interface Config {
        @AttributeDefinition(name = "Service Restart Threshold", description="Minimum number of service restarts during startup needed to create a metric for the service")
        int serviceRestartThreshold() default 3;
        @AttributeDefinition(name = "Slow Bundle Startup Threshold", description="Minimum bundle startup duration in milliseconds needed to create a metric for the bundle")
        long slowBundleThresholdMillis() default 200;
    }

    private static final String APPLICATION_STARTUP_GAUGE_NAME = "osgi.application_startup_time_millis";
    private static final String BUNDLE_STARTUP_GAUGE_NAME_PREFIX = "osgi.slow_bundle_startup_time_millis.";
    private static final String SERVICE_RESTART_GAUGE_NAME_PREFIX = "osgi.excessive_service_restarts_count.";
    
    @Reference
    private MetricRegistry registry;
    
    private int serviceRestartThreshold;
    private long slowBundleThresholdMillis;
    private List<String> registeredMetricNames = new ArrayList<>();
    
    @Activate
    protected void activate(Config cfg) {
        this.serviceRestartThreshold = cfg.serviceRestartThreshold();
        this.slowBundleThresholdMillis = cfg.slowBundleThresholdMillis();
    }

    @Deactivate
    protected void deactivate() {
        registeredMetricNames.forEach( m -> registry.remove(m) );
    }
    
    @Override
    public void onStartupComplete(StartupMetrics event) {
        register(APPLICATION_STARTUP_GAUGE_NAME, (Gauge<Long>) () -> event.getStartupTime().toMillis() );
        event.getBundleStartDurations().stream()
            .filter( bsd -> bsd.getStartedAfter().toMillis() >= slowBundleThresholdMillis )
            .forEach( bsd -> register(BUNDLE_STARTUP_GAUGE_NAME_PREFIX + bsd.getSymbolicName(), (Gauge<Long>) () -> bsd.getStartedAfter().toMillis()));
        event.getServiceRestarts().stream()
            .filter( src -> src.getServiceRestarts() >= serviceRestartThreshold )
            .forEach( src -> register(SERVICE_RESTART_GAUGE_NAME_PREFIX + src.getServiceIdentifier(), (Gauge<Integer>) src::getServiceRestarts) );
    }
    
    private void register(String name, Metric metric) {
        registry.register(name, metric);
        registeredMetricNames.add(name);
    }
}

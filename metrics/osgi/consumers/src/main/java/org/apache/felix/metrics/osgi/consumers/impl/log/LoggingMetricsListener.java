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
package org.apache.felix.metrics.osgi.consumers.impl.log;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.felix.metrics.osgi.BundleStartDuration;
import org.apache.felix.metrics.osgi.ServiceRestartCounter;
import org.apache.felix.metrics.osgi.StartupMetrics;
import org.apache.felix.metrics.osgi.StartupMetricsListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Designate(ocd = LoggingMetricsListener.Config.class)
public class LoggingMetricsListener implements StartupMetricsListener {
    
    @ObjectClassDefinition(name = "Apache Felix Logging Startup Metrics Listener")
    public @interface Config {
        
        @AttributeDefinition(name = "Service Restart Threshold", description="Minimum number of service restarts during startup needed log the number of service restarts")
        int serviceRestartThreshold() default 3;
        @AttributeDefinition(name = "Slow Bundle Startup Threshold", description="Minimum bundle startup duration in milliseconds needed to log the bundle startup time")
        long slowBundleThresholdMillis() default 200;
    }

    private int serviceRestartThreshold;
    private long slowBundleThresholdMillis;
    
    @Activate
    protected void activate(Config cfg) {
        this.serviceRestartThreshold = cfg.serviceRestartThreshold();
        this.slowBundleThresholdMillis = cfg.slowBundleThresholdMillis();
    }
    
    @Override
    public void onStartupComplete(StartupMetrics event) {
        Logger log = LoggerFactory.getLogger(getClass());
        log.info("Application startup completed in {}", event.getStartupTime());

        List<BundleStartDuration> slowStartBundles = event.getBundleStartDurations().stream()
                .filter( bsd -> bsd.getStartedAfter().toMillis() >= slowBundleThresholdMillis )
                .collect(Collectors.toList());
        
        if ( !slowStartBundles.isEmpty() && log.isInfoEnabled() ) {
            StringBuilder logEntry = new StringBuilder();
            logEntry.append("The following bundles started in more than ")
                .append(slowBundleThresholdMillis)
                .append(" milliseconds: \n");
            slowStartBundles
                .forEach( ssb -> logEntry.append("- ").append(ssb.getSymbolicName()).append(" : ").append(ssb.getStartedAfter()).append('\n'));
            
            log.info(logEntry.toString());
        }
        
        List<ServiceRestartCounter> oftenRestartedServices = event.getServiceRestarts().stream()
                .filter( src -> src.getServiceRestarts() >= serviceRestartThreshold )
                .collect(Collectors.toList());
        
        if ( !oftenRestartedServices.isEmpty() && log.isInfoEnabled() ) {
            StringBuilder logEntry = new StringBuilder();
            logEntry.append("The following services have restarted more than ")
                .append(serviceRestartThreshold)
                .append(" times during startup :\n");
            oftenRestartedServices
                .forEach(ors -> logEntry.append("- ").append(ors.getServiceIdentifier()).append(" : ").append(ors.getServiceRestarts()).append(" restarts\n"));
            
            log.info(logEntry.toString());
        }
    }

}

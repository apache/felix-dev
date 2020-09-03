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
package org.apache.felix.metrics.osgi.consumers.impl.json;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.felix.metrics.osgi.BundleStartDuration;
import org.apache.felix.metrics.osgi.ServiceRestartCounter;
import org.apache.felix.metrics.osgi.StartupMetrics;
import org.apache.felix.metrics.osgi.StartupMetricsListener;
import org.apache.felix.utils.json.JSONWriter;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JsonWritingMetricsListener implements StartupMetricsListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    private BundleContext ctx;

    @Activate
    protected void activate(BundleContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onStartupComplete(StartupMetrics metrics) {

        File metricsFile = ctx.getDataFile("startup-metrics-" + System.currentTimeMillis() + ".json");
        if ( metricsFile == null ) {
            logger.warn("Unable to get data file in the bundle area, startup metrics will not be written");
            return;
        }
        
        try {
            try ( FileWriter fw = new FileWriter(metricsFile)) {
                JSONWriter w = new JSONWriter(fw);
                w.object();
                // application metrics
                w.key("application");
                w.object();
                w.key("startTimeMillis").value(metrics.getJvmStartup().toEpochMilli());
                w.key("startDurationMillis").value(metrics.getStartupTime().toMillis());
                w.endObject();
                
                // bundle metrics
                w.key("bundles");
                w.array();
                for ( BundleStartDuration bsd : metrics.getBundleStartDurations() ) {
                    w.object();
                    w.key("symbolicName").value(bsd.getSymbolicName());
                    w.key("startTimeMillis").value(bsd.getStartingAt().toEpochMilli());
                    w.key("startDurationMillis").value(bsd.getStartedAfter().toMillis());
                    w.endObject();
                }
                w.endArray();
                
                // service metrics
                w.key("services");
                w.array();
                for ( ServiceRestartCounter src : metrics.getServiceRestarts() ) {
                    w.object();
                    w.key("identifier").value(src.getServiceIdentifier());
                    w.key("restarts").value(src.getServiceRestarts());
                    w.endObject();
                }
                w.endArray();
                
                w.endObject();
            }
        } catch (IOException e) {
            logger.warn("Failed wrting startup metrics", e);
        }
    }
}

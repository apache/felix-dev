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
package org.apache.felix.metrics.osgi;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Provides metrics about the OSGi framework startup and associated services
 *
 * <p>The calculation of the application being "ready" is based on the Apache Felix SystemReady bundle and
 * requires a proper configuration of all checks.</p>
 */
public final class StartupMetrics {
    
    public static final class Builder {
        
        private StartupMetrics startupMetrics = new StartupMetrics();
        
        public static Builder withJvmStartup(Instant jvmStartup) {
            Builder builder = new Builder();
            builder.startupMetrics.jvmStartup = jvmStartup;
            return builder;
        }
        
        public Builder withStartupTime(Duration startupTime) {
            startupMetrics.startupTime = startupTime;
            return this;
        }
        
        public Builder withBundleStartDurations(List<BundleStartDuration> bundleStartDurations) {
            startupMetrics.bundleStartDurations = Collections.unmodifiableList(bundleStartDurations);
            return this;
        }
        
        public Builder withServiceRestarts(List<ServiceRestartCounter> serviceRestarts) {
            startupMetrics.serviceRestarts = Collections.unmodifiableList(serviceRestarts);
            return this;
        }
        
        public StartupMetrics build() {
            return startupMetrics;
        }
    }

    private Instant jvmStartup;
    private Duration startupTime;
    private List<BundleStartDuration> bundleStartDurations;
    private List<ServiceRestartCounter> serviceRestarts;
 
    private StartupMetrics() { }

    /**
     * Returns the instant when the JVM has started
     * 
     * <p>Note that this is different from the OSGi startup process, and may lead to unexpected results if the
     * OSGi framework starts considerably later compared to the JVM.</p>
     * 
     * @return the instant when the JVM has started
     */
    public Instant getJvmStartup() {
        return jvmStartup;
    }
    
    /**
     * @return the time between the {@link #getJvmStartup()} and the application being ready
     */
    public Duration getStartupTime() {
        return startupTime;
    }
    
    /**
     * @return all bundle start durations
     */
    public List<BundleStartDuration> getBundleStartDurations() {
        return bundleStartDurations;
    }
    
    /**
     * @return tracked services with at least one restart
     */
    public List<ServiceRestartCounter> getServiceRestarts() {
        return serviceRestarts;
    }
}

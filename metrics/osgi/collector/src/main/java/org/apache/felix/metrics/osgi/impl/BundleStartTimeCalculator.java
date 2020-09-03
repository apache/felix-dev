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
package org.apache.felix.metrics.osgi.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.felix.metrics.osgi.BundleStartDuration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.SynchronousBundleListener;

public class BundleStartTimeCalculator implements SynchronousBundleListener {

    private Map<Long, StartTime> bundleToStartTime = new HashMap<>();
    private Clock clock = Clock.systemUTC();
    private final long ourBundleId;

    public BundleStartTimeCalculator(long ourBundleId) {
        this.ourBundleId = ourBundleId;
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        Bundle bundle = event.getBundle();
        
        // this bundle is already starting by the time this is invoked. We also can't get proper timing
        // from the framework bundle
        
        if ( bundle.getBundleId() == Constants.SYSTEM_BUNDLE_ID 
                || bundle.getBundleId() == ourBundleId ) {
            return;
        }
        
        synchronized (bundleToStartTime) {

            switch (event.getType()) {
                case BundleEvent.STARTING:
                    bundleToStartTime.put(bundle.getBundleId(), new StartTime(bundle.getSymbolicName(), clock.millis()));
                    break;
    
                case BundleEvent.STARTED:
                    StartTime startTime = bundleToStartTime.get(bundle.getBundleId());
                    if ( startTime == null ) {
                        Log.debug(getClass(), "No previous data for started bundle {}/{}", new Object[] { bundle.getBundleId(), bundle.getSymbolicName() });
                        return;
                    }
                    startTime.started(clock.millis());
                    break;
                
                default: // nothing to do here
                    break;
            }
        }
    }
    
    public List<BundleStartDuration> getBundleStartDurations() {
        
        synchronized (bundleToStartTime) {
            return bundleToStartTime.values().stream()
                .map( StartTime::toBundleStartDuration )
                .collect( Collectors.toList() );                    
        }
    }

    class StartTime {
        private final String bundleSymbolicName;
        private long startingTimestamp;
        private long startedTimestamp;
        
        public StartTime(String bundleSymbolicName, long startingTimestamp) {
            this.bundleSymbolicName = bundleSymbolicName;
            this.startingTimestamp = startingTimestamp;
        }

        public long getDuration() {
            return startedTimestamp - startingTimestamp;
        }
        
        public String getBundleSymbolicName() {
            return bundleSymbolicName;
        }

        public void started(long startedTimestamp) {
            this.startedTimestamp = startedTimestamp;
        }
        
        public BundleStartDuration toBundleStartDuration() {
            return new BundleStartDuration(bundleSymbolicName, Instant.ofEpochMilli(startingTimestamp), Duration.ofMillis(startedTimestamp - startingTimestamp));
        }
    }
}

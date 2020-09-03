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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.apache.felix.metrics.osgi.BundleStartDuration;
import org.apache.felix.metrics.osgi.impl.BundleStartTimeCalculator;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;

public class BundleStartTimeCalculatorTest {

    @Test
    public void bundleStarted() {

        Instant now = Instant.now();
        
        BundleStartTimeCalculator c = new BundleStartTimeCalculator(1l);
        Bundle mockBundle = newMockBundle(5l, "foo");
        c.bundleChanged(new BundleEvent(BundleEvent.STARTING, mockBundle));
        c.bundleChanged(new BundleEvent(BundleEvent.STARTED, mockBundle));
        
        assertThat("Expected one entry for bundle durations",c.getBundleStartDurations().size(), CoreMatchers.equalTo(1));
        BundleStartDuration duration = c.getBundleStartDurations().get(0);
        assertThat("Bundle duration refers to wrong bundle symbolic name", duration.getSymbolicName(), CoreMatchers.equalTo("foo"));
        
        assertTrue("Bundle STARTING time (" + duration.getStartingAt() + " must be after test start time(" + now + ")", 
                duration.getStartingAt().isAfter(now));
        assertFalse("Bundle start duration (" + duration.getStartedAfter() + ") must not be negative",
                duration.getStartedAfter().isNegative());
    }
    
    private Bundle newMockBundle(long id, String symbolicName) {
        
        Bundle mockBundle = Mockito.mock(Bundle.class);
        Mockito.when(mockBundle.getBundleId()).thenReturn(id);
        Mockito.when(mockBundle.getSymbolicName()).thenReturn(symbolicName);
        return mockBundle;
    }
}

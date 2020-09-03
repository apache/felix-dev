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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.frameworkProperty;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.felix.metrics.osgi.StartupMetrics;
import org.apache.felix.metrics.osgi.StartupMetricsListener;
import org.apache.felix.metrics.osgi.impl.StartupTimeCalculator;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;

public abstract class AbstractIT {

    private static final String TESTED_BUNDLE_LOCATION = "reference:file:target/classes";

    @Inject
    protected BundleContext bc;

    @Configuration
    public Option[] config() {
        return options(
            // lower timeout, we don't have bounces
            frameworkProperty(StartupTimeCalculator.PROPERTY_READINESS_DELAY).value("100"),
            bundle(TESTED_BUNDLE_LOCATION),
            junitBundles(),
            mavenBundle("org.apache.felix", "org.apache.felix.scr", "2.1.16"),
            mavenBundle("org.osgi", "org.osgi.util.promise", "1.1.1"),
            mavenBundle("org.osgi", "org.osgi.util.function", "1.1.0"),
            composite(specificOptions())
        );
    }
    
    protected abstract Option[] specificOptions();

    @Test
    public void registerListenerAfterSystemIsReady() throws InterruptedException {
        runBasicTest(false);
    }

    @Test
    public void registerListenerBeforeSystemIsReady() throws InterruptedException {
        runBasicTest(true);
    }

    private void runBasicTest(boolean registerListenerFirst) throws InterruptedException {
        
        Set<String> expectedBundleNames = Arrays.stream(bc.getBundles())
            .filter( b -> b.getBundleId() != Constants.SYSTEM_BUNDLE_ID ) // no framework bundle
            .filter( b -> !b.getLocation().equals(TESTED_BUNDLE_LOCATION) ) // not the bundle under test
            .map ( b -> b.getSymbolicName() )
            .filter( bsn ->  ! bsn.startsWith("org.ops4j")  ) // no ops4j bundles
            .filter( bsn ->  ! bsn.startsWith("PAXEXAM")  ) // no ops4j bundles
            .filter( bsn -> ! bsn.contains("geronimo-atinject")) // injected early on by Pax-Exam
            .collect(Collectors.toSet());
        
        WaitForResultsStartupMetricsListener listener = new WaitForResultsStartupMetricsListener();

        // service that will be tracked as restarting
        Runnable foo = () -> {};
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_PID, "some.service.pid");
        ServiceRegistration<Runnable> reg = bc.registerService(Runnable.class, foo, props);
        reg.unregister();
        reg = bc.registerService(Runnable.class, foo, props);
        reg.unregister();

        if ( registerListenerFirst ) {
            markSystemReady();
            bc.registerService(StartupMetricsListener.class, listener, null);
        } else {
            markSystemReady();
            bc.registerService(StartupMetricsListener.class, listener, null);
        }
        
        StartupMetrics metrics = listener.getMetrics();
        
        assertThat(metrics, notNullValue());
        Set<String> trackedBundleNames = metrics.getBundleStartDurations().stream()
                .map( bsd -> bsd.getSymbolicName())
                .collect(Collectors.toSet());
        
        assertTrue("Tracked bundle names " + trackedBundleNames + " did not contain " + expectedBundleNames, 
            trackedBundleNames.containsAll(expectedBundleNames));
        
        assertThat("Service restarts", metrics.getServiceRestarts().size(), equalTo(1));
        assertThat("Restarted component service identifier", metrics.getServiceRestarts().get(0).getServiceIdentifier(), equalTo(Constants.SERVICE_PID+"="+props.get(Constants.SERVICE_PID)));
    }

    protected abstract void markSystemReady();
}

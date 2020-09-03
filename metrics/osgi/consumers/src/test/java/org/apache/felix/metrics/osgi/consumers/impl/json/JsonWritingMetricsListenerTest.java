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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import org.apache.felix.metrics.osgi.BundleStartDuration;
import org.apache.felix.metrics.osgi.ServiceRestartCounter;
import org.apache.felix.metrics.osgi.StartupMetrics;
import org.apache.felix.metrics.osgi.consumers.impl.json.JsonWritingMetricsListener;
import org.apache.felix.utils.json.JSONParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;

public class JsonWritingMetricsListenerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    
    @Test
    public void metricsArePersisted() throws IOException {
        // bridge the mock bundle context with the temporary folder
        BundleContext mockBundleContext = mock(BundleContext.class);
        when(mockBundleContext.getDataFile(Mockito.anyString())).thenAnswer( i -> tmp.newFile(i.getArgument(0, String.class)));
        
        JsonWritingMetricsListener listener = new JsonWritingMetricsListener();
        listener.activate(mockBundleContext);
        
        StartupMetrics metrics = StartupMetrics.Builder
                .withJvmStartup(Instant.now())
                .withStartupTime(Duration.ofMillis(50))
                .withBundleStartDurations(Arrays.asList(new BundleStartDuration("foo", Instant.now(), Duration.ofMillis(5))))
                .withServiceRestarts(Arrays.asList(new ServiceRestartCounter("some.service", 1)))
                .build();
        
        listener.onStartupComplete(metrics);
        
        File[] files = tmp.getRoot().listFiles();
        
        assertThat("Bundle data area should hold one file", files.length, equalTo(1));
        
        File metricsFile = files[0];
        try ( FileInputStream fis = new FileInputStream(metricsFile)) {
            JSONParser p = new JSONParser(fis);
            assertThat(p.getParsed().keySet(), hasItem("application"));
            assertThat(p.getParsed().keySet(), hasItem("bundles"));
            assertThat(p.getParsed().keySet(), hasItem("services"));
        }
    }
}

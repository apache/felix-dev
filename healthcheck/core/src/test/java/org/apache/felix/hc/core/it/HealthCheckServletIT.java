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
package org.apache.felix.hc.core.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.runtime.dto.RuntimeDTO;
import org.osgi.service.servlet.runtime.dto.ServletContextDTO;
import org.osgi.service.servlet.runtime.dto.ServletDTO;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Verify that the HealthCheckExecutorServlet becomes available after creating the corresponding config */
@RunWith(PaxExam.class)
public class HealthCheckServletIT {
    private static final int EXPECTED_SERVLET_COUNT = 6;
    private static final String HTTP_CONTEXT_NAME = "org.osgi.service.http";
    private static final int DEFAULT_TIMEOUT = 10000;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Map<String, ServiceTracker<?, ?>> trackers = new HashMap<>();

    private final AtomicLong runtimeCounter = new AtomicLong();

    private ServiceListener serviceListener;

    @Inject
    private ConfigurationAdmin configAdmin;

    @Inject
    private BundleContext bundleContext;

    @Configuration
    public Option[] config() {
        return U.config();
    }

    @Before
    public void setup() throws Exception {
        this.serviceListener = new ServiceListener() {

            @Override
            public void serviceChanged(final ServiceEvent event) {
                runtimeCounter.set((Long) event.getServiceReference().getProperty("service.changecount"));
            }

        };

        this.bundleContext.addServiceListener(this.serviceListener,
                "(objectClass=" + HttpServiceRuntime.class.getName() + ")");

        awaitService(HttpServiceRuntime.class);
    }

    @After
    public void tearDown() throws Exception {
        synchronized (trackers) {
            for (final ServiceTracker<?, ?> entry : trackers.values()) {
                entry.close();
            }
            trackers.clear();
        }

        if (this.serviceListener != null) {
            this.bundleContext.removeServiceListener(this.serviceListener);
            this.serviceListener = null;
        }
    }
    
    @Test
    public void testServletBecomesActive() throws InvalidSyntaxException, IOException, InterruptedException {
        final String servletPathPropertyName = "servletPath";
        final String servletPathPropertyVal = "/test/" + UUID.randomUUID();
        final String packagePrefix = "org.apache.felix.hc";

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTOBeforeHealthCheckExecutorServletActivation = serviceRuntime.getRuntimeDTO();

        ServletContextDTO contextDTO = assertDefaultContext(runtimeDTOBeforeHealthCheckExecutorServletActivation);

        int actualServletCount = contextDTO.servletDTOs.length;

        assertEquals("Initially expecting no servlet from " + packagePrefix, 0, actualServletCount);

        // Activate servlet and wait for it to show up
        final String factoryPid = "org.apache.felix.hc.core.impl.servlet.HealthCheckExecutorServlet";
        org.osgi.service.cm.Configuration cfg = configAdmin.createFactoryConfiguration(factoryPid, null);
        log.info("Created factory configuration for servlet with pid = {}", cfg.getPid());
        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(servletPathPropertyName, servletPathPropertyVal);
        cfg.update(props);
        log.info("Updated config with properties {}", props);

        this.waitForRuntimeCounterIncrease(EXPECTED_SERVLET_COUNT);

        RuntimeDTO runtimeDTOAfterHealthCheckExecutorServletActivation = serviceRuntime.getRuntimeDTO();

        contextDTO = assertDefaultContext(runtimeDTOAfterHealthCheckExecutorServletActivation);

        ServletDTO[] servletDTOs = contextDTO.servletDTOs;

        actualServletCount = contextDTO.servletDTOs.length;

        assertEquals("After adding configuration, expecting six servlets from " + packagePrefix, EXPECTED_SERVLET_COUNT,
                actualServletCount);

        assertEquals("Expecting the HC servlet to be registered at " + servletPathPropertyVal, servletPathPropertyVal,
                servletDTOs[servletDTOs.length - 6].patterns[0]);
        assertEquals("Expecting the HTML HC servlet to be registered at " + servletPathPropertyVal + ".html",
                servletPathPropertyVal + ".html", servletDTOs[servletDTOs.length - 5].patterns[0]);
        assertEquals("Expecting the JSON HC servlet to be registered at " + servletPathPropertyVal + ".json",
                servletPathPropertyVal + ".json", servletDTOs[servletDTOs.length - 4].patterns[0]);
        assertEquals("Expecting the JSONP HC servlet to be registered at " + servletPathPropertyVal + ".jsonp",
                servletPathPropertyVal + ".jsonp", servletDTOs[servletDTOs.length - 3].patterns[0]);
        assertEquals("Expecting the TXT HC servlet to be registered at " + servletPathPropertyVal + ".txt",
                servletPathPropertyVal + ".txt", servletDTOs[servletDTOs.length - 2].patterns[0]);
        assertEquals(
                "Expecting the verbose TXT HC servlet to be registered at " + servletPathPropertyVal + ".verbose.txt",
                servletPathPropertyVal + ".verbose.txt", servletDTOs[servletDTOs.length - 1].patterns[0]);
    }
    
    // Adopted from org.apache.felix.http.itest (HttpServiceRuntimeTest, Servlet5BaseIntegrationTest, BaseIntegrationTest) 
    private HttpServiceRuntime getHttpServiceRuntime() {
        final HttpServiceRuntime runtime = this.getService(HttpServiceRuntime.class);
        assertNotNull(runtime);
        return runtime;
    }
    
    protected <T> T awaitService(Class<T> clazz) throws Exception {
        ServiceTracker<T, T> tracker = null;
        tracker = getTracker(clazz);
        return tracker.waitForService(DEFAULT_TIMEOUT);
    }

    protected <T> T getService(final Class<T> clazz) {
        final ServiceTracker<T, T> tracker = getTracker(clazz);
        return tracker.getService();
    }    
    
    private <T> ServiceTracker<T, T> getTracker(Class<T> clazz) {
        synchronized ( this.trackers ) {
            ServiceTracker<T, T> tracker = (ServiceTracker<T, T>) trackers.get(clazz.getName());
            if ( tracker == null ) {
                tracker = new ServiceTracker<>(bundleContext, clazz, null);
                trackers.put(clazz.getName(), tracker);
                tracker.open();
            }
            return tracker;
        }
    }    
    
    private ServletContextDTO assertDefaultContext(RuntimeDTO runtimeDTO) {
        assertTrue(1 < runtimeDTO.servletContextDTOs.length);
        assertEquals(HTTP_CONTEXT_NAME, runtimeDTO.servletContextDTOs[0].name);
        assertEquals("default", runtimeDTO.servletContextDTOs[1].name);
        return runtimeDTO.servletContextDTOs[1];
    }

    public long getRuntimeCounter() {
        return this.runtimeCounter.get();
    }
    
    public void waitForRuntimeCounterIncrease(final long expectedServletCount) {
        while (runtimeCounter.get() < expectedServletCount) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }
}

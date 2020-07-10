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
package org.apache.felix.hc.core.impl.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.condition.Healthy;
import org.apache.felix.hc.api.condition.Unhealthy;
import org.apache.felix.hc.api.execution.HealthCheckMetadata;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.apache.felix.hc.core.impl.executor.ExecutionResult;
import org.apache.felix.hc.core.impl.executor.ExtendedHealthCheckExecutor;
import org.apache.felix.hc.core.impl.executor.HealthCheckExecutorThreadPool;
import org.apache.felix.hc.core.impl.scheduling.AsyncIntervalJob;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

public class HealthCheckMonitorTest {

    private static final String TEST_TAG = "test-tag";

    @InjectMocks
    private HealthCheckMonitor healthCheckMonitor = new HealthCheckMonitor();

    @Mock
    private BundleContext bundleContext;

    @Mock
    private ComponentContext componentContext;
    
    @Mock
    private EventAdmin eventAdmin;

    @Mock
    private HealthCheckMonitor.Config config;

    @Mock
    private HealthCheckExecutorThreadPool healthCheckExecutorThreadPool;

    @Mock
    private ExtendedHealthCheckExecutor healthCheckExecutor;
    
    @Mock
    private HealthCheckMetadata healthCheckMetadata;

    @Mock
    private ServiceReference<HealthCheck> healthCheckServiceRef;
    
    @Captor
    private ArgumentCaptor<Event> postedEventsCaptor1;

    @Captor
    private ArgumentCaptor<Event> postedEventsCaptor2;
        
    @Mock
    private ServiceRegistration<? extends Healthy> healthyRegistration;
    
    @Mock
    private ServiceRegistration<Unhealthy> unhealthyRegistration;
    
    @Before
    public void before() throws ReflectiveOperationException {
        MockitoAnnotations.initMocks(this);

        for (Method m : HealthCheckMonitor.Config.class.getDeclaredMethods()) {
            when(m.invoke(config)).thenReturn(m.getDefaultValue());
        }
        
        when(config.intervalInSec()).thenReturn(1000L);
        when(config.tags()).thenReturn(new String[] { TEST_TAG });
        
        when(healthCheckMetadata.getServiceReference()).thenReturn(healthCheckServiceRef);
        
        Dictionary<String,Object> componentProps = new Hashtable<>();
        componentProps.put(ComponentConstants.COMPONENT_ID, 7L);
        when(componentContext.getProperties()).thenReturn(componentProps);
    }

    @Test
    public void testActivate() throws InvalidSyntaxException {


        healthCheckMonitor.activate(bundleContext, config, componentContext);

        assertTrue(healthCheckMonitor.monitorJob instanceof AsyncIntervalJob);

        verify(healthCheckExecutorThreadPool).scheduleAtFixedRate(any(Runnable.class), eq(1000L));

        assertEquals("[HealthCheckMonitor tags=[test-tag]/names=[], intervalInSec=1000/cron=]", healthCheckMonitor.toString());

        assertEquals(1, healthCheckMonitor.healthStates.size());
        assertEquals("[HealthState tagOrName=test-tag, isTag=true, status=null, isHealthy=false, statusChanged=false]", healthCheckMonitor.healthStates.get(TEST_TAG).toString());
        
        healthCheckMonitor.deactivate();
        assertEquals(0, healthCheckMonitor.healthStates.size());
        
    }

    @Test
    public void testRunRegisterMarkerServices() throws InvalidSyntaxException {

        when(config.registerHealthyMarkerService()).thenReturn(true);
        when(config.registerUnhealthyMarkerService()).thenReturn(true);
        healthCheckMonitor.activate(bundleContext, config, componentContext);

        resetMarkerServicesContext();

        setHcResult(Result.Status.OK);

        healthCheckMonitor.run();
        
        verify(healthCheckExecutor).execute(HealthCheckSelector.tags(TEST_TAG));
        
        verify(bundleContext).registerService(eq(Healthy.class), eq(HealthState.MARKER_SERVICE_HEALTHY), any());
        verify(bundleContext, never()).registerService(eq(Unhealthy.class), eq(HealthState.MARKER_SERVICE_UNHEALTHY), any());
        verifyNoInteractions(healthyRegistration, unhealthyRegistration);

        resetMarkerServicesContext();
        healthCheckMonitor.run();
        // no status change, no interaction
        verifyNoInteractions(bundleContext, healthyRegistration, unhealthyRegistration);
        
        // change, unhealthy should be registered
        resetMarkerServicesContext();
        setHcResult(Result.Status.TEMPORARILY_UNAVAILABLE);
        healthCheckMonitor.run();
        
        verify(bundleContext, never()).registerService(eq(Healthy.class), eq(HealthState.MARKER_SERVICE_HEALTHY), any());
        verify(bundleContext).registerService(eq(Unhealthy.class), eq(HealthState.MARKER_SERVICE_UNHEALTHY), any());
        verify(healthyRegistration).unregister();
        verifyNoInteractions(unhealthyRegistration);
        
        // change, health should be registered
        resetMarkerServicesContext();
        setHcResult(Result.Status.WARN); // WARN is healthy by default config
        healthCheckMonitor.run();
        verify(bundleContext).registerService(eq(Healthy.class), eq(HealthState.MARKER_SERVICE_HEALTHY), any());
        verify(bundleContext, never()).registerService(eq(Unhealthy.class), eq(HealthState.MARKER_SERVICE_UNHEALTHY), any());
        verify(unhealthyRegistration).unregister();
        verifyNoInteractions(healthyRegistration);
    }

    private void resetMarkerServicesContext() {
        reset(bundleContext, healthyRegistration, unhealthyRegistration);
        when(bundleContext.registerService(eq(Healthy.class), eq(HealthState.MARKER_SERVICE_HEALTHY), any())).thenReturn((ServiceRegistration<Healthy>) healthyRegistration);
        when(bundleContext.registerService(eq(Unhealthy.class), eq(HealthState.MARKER_SERVICE_UNHEALTHY), any())).thenReturn(unhealthyRegistration);
    }
    
    @Test
    public void testRunSendEventsStatusChanges() throws InvalidSyntaxException {

        when(config.sendEvents()).thenReturn(HealthCheckMonitor.ChangeType.STATUS_CHANGES);
        when(healthCheckServiceRef.getProperty(ComponentConstants.COMPONENT_NAME)).thenReturn("org.apache.felix.TestHealthCheck");

        healthCheckMonitor.activate(bundleContext, config, componentContext);

        setHcResult(Result.Status.OK);

        healthCheckMonitor.run();
        
        verify(healthCheckExecutor).execute(HealthCheckSelector.tags(TEST_TAG));
        
        verify(eventAdmin, times(2)).postEvent(postedEventsCaptor1.capture());
        List<Event> postedEvents = postedEventsCaptor1.getAllValues();
        assertEquals(2, postedEvents.size());
        assertEquals("org/apache/felix/health/tag/test-tag/STATUS_CHANGED", postedEvents.get(0).getTopic());
        assertEquals(Result.Status.OK, postedEvents.get(0).getProperty(HealthState.EVENT_PROP_STATUS));
        assertEquals("org/apache/felix/health/component/org/apache/felix/TestHealthCheck/STATUS_CHANGED", postedEvents.get(1).getTopic());

        reset(eventAdmin);
        // without status change
        healthCheckMonitor.run();
        // no event
        verifyNoInteractions(eventAdmin);
        
        setHcResult(Result.Status.CRITICAL);
        reset(eventAdmin);
        // with status change
        healthCheckMonitor.run();
        verify(eventAdmin, times(2)).postEvent(postedEventsCaptor2.capture());
        postedEvents = postedEventsCaptor2.getAllValues();
        assertEquals(2, postedEvents.size());
        assertEquals("org/apache/felix/health/tag/test-tag/STATUS_CHANGED", postedEvents.get(0).getTopic());
        assertEquals(Result.Status.CRITICAL, postedEvents.get(0).getProperty(HealthState.EVENT_PROP_STATUS));
        assertEquals(Result.Status.OK, postedEvents.get(0).getProperty(HealthState.EVENT_PROP_PREVIOUS_STATUS));
        assertEquals("org/apache/felix/health/component/org/apache/felix/TestHealthCheck/STATUS_CHANGED", postedEvents.get(1).getTopic());
        
        reset(eventAdmin);
        // without status change
        healthCheckMonitor.run();
        // no event
        verifyNoInteractions(eventAdmin);

    }

    @Test
    public void testRunSendEventsAll() throws InvalidSyntaxException {

        when(config.sendEvents()).thenReturn(HealthCheckMonitor.ChangeType.ALL);
        when(healthCheckServiceRef.getProperty(ComponentConstants.COMPONENT_NAME)).thenReturn("org.apache.felix.TestHealthCheck");

        healthCheckMonitor.activate(bundleContext, config, componentContext);
        
        setHcResult(Result.Status.OK);

        healthCheckMonitor.run();
        
        verify(healthCheckExecutor).execute(HealthCheckSelector.tags(TEST_TAG));
        
        verify(eventAdmin, times(2)).postEvent(postedEventsCaptor1.capture());
        List<Event> postedEvents = postedEventsCaptor1.getAllValues();
        assertEquals(2, postedEvents.size());
        assertEquals("org/apache/felix/health/tag/test-tag/STATUS_CHANGED", postedEvents.get(0).getTopic());
        assertEquals(Result.Status.OK, postedEvents.get(0).getProperty(HealthState.EVENT_PROP_STATUS));
        assertEquals("org/apache/felix/health/component/org/apache/felix/TestHealthCheck/STATUS_CHANGED", postedEvents.get(1).getTopic());

        reset(eventAdmin);
        // without status change
        healthCheckMonitor.run();

        verify(eventAdmin, times(2)).postEvent(postedEventsCaptor2.capture());
        postedEvents = postedEventsCaptor2.getAllValues();
        assertEquals(2, postedEvents.size());
        assertEquals("org/apache/felix/health/tag/test-tag/UPDATED", postedEvents.get(0).getTopic());
        assertEquals(Result.Status.OK, postedEvents.get(0).getProperty(HealthState.EVENT_PROP_STATUS));
        assertEquals(Result.Status.OK, postedEvents.get(0).getProperty(HealthState.EVENT_PROP_PREVIOUS_STATUS));
        assertEquals("org/apache/felix/health/component/org/apache/felix/TestHealthCheck/UPDATED", postedEvents.get(1).getTopic());
    }
    
    
    private void setHcResult(Result.Status status) {
        when(healthCheckExecutor.execute(HealthCheckSelector.tags(TEST_TAG)))
            .thenReturn(Arrays.asList(new ExecutionResult(healthCheckMetadata, new Result(status, status.toString()), 1)));
    }
}

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

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result.Status;
import org.apache.felix.hc.api.condition.Healthy;
import org.apache.felix.hc.api.condition.SystemReady;
import org.apache.felix.hc.api.condition.Unhealthy;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.apache.felix.hc.core.impl.executor.CombinedExecutionResult;
import org.apache.felix.hc.core.impl.executor.ExtendedHealthCheckExecutor;
import org.apache.felix.hc.core.impl.executor.HealthCheckExecutorThreadPool;
import org.apache.felix.hc.core.impl.scheduling.AsyncIntervalJob;
import org.apache.felix.hc.core.impl.scheduling.AsyncJob;
import org.apache.felix.hc.core.impl.scheduling.CronJobFactory;
import org.apache.felix.hc.core.impl.servlet.ResultTxtVerboseSerializer;
import org.apache.felix.hc.core.impl.util.HealthCheckFilter;
import org.apache.felix.hc.core.impl.util.lang.StringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors health check tags and/or names and depending on configuration:
 * <p>
 * <ul>
 * <li>Activates the condition marker services {@link SystemReady},
 * {@link Healthy}, {@link Unhealthy}</li>
 * <li>Sends OSGi events</li>
 * </ul>
 * <p>
 * 
 */
@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = HealthCheckMonitor.Config.class, factory = true)
public class HealthCheckMonitor implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckMonitor.class);

    public enum ChangeType {
        NONE, STATUS_CHANGES, ALL
    }
    
    @ObjectClassDefinition(name = "Health Check Monitor", description = "Regularly executes health checks according to given interval/cron expression")
    public @interface Config {

        @AttributeDefinition(name = "Tags", description = "List of tags to query regularly")
        String[] tags() default {};

        @AttributeDefinition(name = "Names", description = "List of health check names to query regularly")
        String[] names() default {};

        @AttributeDefinition(name = "Interval (Sec)", description = "Will execute the checks for give tags every n seconds (either use intervalInSec or cronExpression )")
        long intervalInSec() default 0;

        @AttributeDefinition(name = "Interval (Cron Expresson)", description = "Will execute the checks for give tags according to cron expression")
        String cronExpression() default "";

        @AttributeDefinition(name = "Register Healthy Marker Service", description = "For the case a given tag/name is healthy, will register a service Healthy with property tag=<tagname> (or name=<hc.name>) that other services can depend on")
        boolean registerHealthyMarkerService() default true;

        @AttributeDefinition(name = "Register Unhealthy Marker Service", description = "For the case a given tag/name is unhealthy, will register a service Unhealthy with property tag=<tagname> (or name=<hc.name>) that other services can depend on")
        boolean registerUnhealthyMarkerService() default false;

        @AttributeDefinition(name = "Treat WARN as Healthy", description = "Whether to treat status WARN as healthy (it normally should because WARN indicates a working system that only possibly might become unavailable if no action is taken")
        boolean treatWarnAsHealthy() default true;

        @AttributeDefinition(name = "Send Events", description = "Send OSGi events for the case a status has changed or for all executions or for none.")
        ChangeType sendEvents() default ChangeType.STATUS_CHANGES;

        @AttributeDefinition(name = "Log results", description = "Log the result to the regular log file.")
        ChangeType logResults() default ChangeType.NONE;
        
        @AttributeDefinition(name = "Dynamic Mode", description = "In dynamic mode all checks for names/tags are monitored individually (this means events are sent/services registered for name only, never for given tags). This mode allows to use '*' in tags to query for all health checks in system. It is also possible to query for all except certain tags by using '-', e.g. by configuring the values '*', '-tag1' and '-tag2' for tags.")
        boolean isDynamic() default false;
        
        @AttributeDefinition
        String webconsole_configurationFactory_nameHint() default "Health Monitor for '{tags}'/'{names}', {intervalInSec}sec/{cronExpression}, Marker Service Healthy:{registerHealthyMarkerService} Unhealthy:{registerUnhealthyMarkerService}, Send Events {sendEvents}";
    }

    @Reference
    ExtendedHealthCheckExecutor executor;

    @Reference
    HealthCheckExecutorThreadPool healthCheckExecutorThreadPool;

    @Reference
    ResultTxtVerboseSerializer resultTxtVerboseSerializer;
    
    @Reference
    CronJobFactory cronJobFactory;

    @Reference
    private EventAdmin eventAdmin;

    // component state
    AsyncJob monitorJob = null;
    List<String> tags;
    List<String> names;
    Map<Object,HealthState> healthStates = new ConcurrentHashMap<>();

    private long intervalInSec;
    private String cronExpression;

    private boolean registerHealthyMarkerService;
    private boolean registerUnhealthyMarkerService;

    private boolean treatWarnAsHealthy;

    private ChangeType sendEvents;
    private ChangeType logResults;

    private BundleContext bundleContext;
    
    private String monitorId;
    
    private boolean isDynamic;
    private ServiceListener healthCheckServiceListener = null;

    @Activate
    protected final void activate(BundleContext bundleContext, Config config, ComponentContext componentContext) throws InvalidSyntaxException {

        this.bundleContext = bundleContext;

        this.tags = Arrays.stream(config.tags()).filter(StringUtils::isNotBlank).collect(toList());
        this.names = Arrays.stream(config.names()).filter(StringUtils::isNotBlank).collect(toList());
        this.isDynamic = config.isDynamic();
        initHealthStates();
        
        this.registerHealthyMarkerService = config.registerHealthyMarkerService();
        this.registerUnhealthyMarkerService = config.registerUnhealthyMarkerService();

        this.treatWarnAsHealthy = config.treatWarnAsHealthy();
        this.sendEvents = config.sendEvents();
        this.logResults = config.logResults();

        this.intervalInSec = config.intervalInSec();
        this.cronExpression = config.cronExpression();
        
        this.monitorId = getMonitorId(componentContext.getProperties().get(ComponentConstants.COMPONENT_ID));
        if (StringUtils.isNotBlank(cronExpression)) {
            monitorJob = cronJobFactory.createAsyncCronJob(this, monitorId, "healthcheck-monitor", cronExpression);
        } else if (intervalInSec > 0) {
            monitorJob = new AsyncIntervalJob(this, healthCheckExecutorThreadPool, intervalInSec);
        } else {
            throw new IllegalArgumentException("Either cronExpression or intervalInSec needs to be set");
        }
        monitorJob.schedule();
        LOG.info("Monitor active for tags {} and names {} (isDynamic={})", this.tags, this.names, this.isDynamic);
    }
    
    private void initHealthStates() throws InvalidSyntaxException {
        if(!this.isDynamic) {
            this.tags.stream().filter(StringUtils::isNotBlank).forEach(tag -> {
                if(tag.contains("*") || tag.startsWith("-")) {
                    throw new IllegalArgumentException("Health check monitor is configured to isDyamic=false but tags contain query items like '*' or '-': "+String.join(",", this.tags));
                }
                healthStates.put(tag, new HealthState(this, tag, true));
            });
            this.names.stream().filter(StringUtils::isNotBlank).forEach(name -> {
                healthStates.put(name, new HealthState(this, name, false));
            });
        } else {
            updateHealthStatesMap();
            healthCheckServiceListener = new HealthCheckServiceListener();
            bundleContext.addServiceListener(healthCheckServiceListener, HealthCheckFilter.HC_FILTER_OBJECT_CLASS);
        }

    }

    private String getMonitorId(Object compId) {
        return "hc-monitor-" + compId + '-' + String.join(",", tags)+(!names.isEmpty()?"-"+names.size()+"_names":"");
    }

    @Override
    public String toString() {
        return "[HealthCheckMonitor tags=" + tags + "/names=" + names + ", intervalInSec=" + intervalInSec + "/cron="
                + cronExpression + "]";
    }

    @Deactivate
    protected final void deactivate() {
        if(healthCheckServiceListener != null) {
            bundleContext.removeServiceListener(healthCheckServiceListener);
        }
        healthStates.values().stream().forEach(HealthState::cleanUp);
        healthStates.clear();
        monitorJob.unschedule();
        LOG.info("Monitor deactivated for tags {} and names {}", this.tags, this.names);
    }

    public void updateHealthStatesMap() {
        HealthCheckFilter filter = new HealthCheckFilter(bundleContext);
        HealthCheckSelector selector = HealthCheckSelector.tags(tags.toArray(new String[tags.size()])).withNames(names.toArray(new String[names.size()]));
        ServiceReference<HealthCheck>[] refs = filter.getHealthCheckServiceReferences(selector, true);
        LOG.debug("Found {} health check service refs", refs.length);
        List<Object> oldServiceIds = new ArrayList<>(healthStates.keySet()); // start with all keys
        for (ServiceReference<HealthCheck> ref : refs) {
            Long serviceId = (Long) ref.getProperty(Constants.SERVICE_ID);
            if(healthStates.containsKey(serviceId)) {
                // HC state exists, keep
                oldServiceIds.remove(serviceId);
            } else {
                // add HC state
                HealthState healthState = new HealthState(this, ref);
                LOG.debug("Monitoring health state: {}", healthState);
                healthStates.put(serviceId, healthState);
            }
        }
        // Remove obsolete HC states 
        for (Object oldServiceId : oldServiceIds) {
            HealthState removed = healthStates.remove(oldServiceId);
            removed.cleanUp();
            LOG.debug("Removed monitoring for health state: {}", removed);
        }
    }
    
    public void run() {
        runWithThreadNameContext(() -> {
            try {

                // run in tags/names in parallel
                healthStates.values().parallelStream().forEach(healthState -> 
                    runWithThreadNameContext(healthState::update)
                );
                

                if(logResults != ChangeType.NONE) {
                    logResults();
                }
                

                LOG.debug("Updated {} health states for tags {} and names {}", healthStates.size(), this.tags, this.names);
            } catch (Exception e) {
                LOG.error("Exception during execution of checks in HealthCheckMonitor: " + e, e);
            } 
        });
    }

    private void logResults() {

        List<HealthCheckExecutionResult> executionResults = healthStates.values().stream()
            .filter(healthState -> { return healthState.hasChanged() || logResults == ChangeType.ALL; })
            .flatMap( healthState -> {
                HealthCheckExecutionResult executionResult = healthState.getExecutionResult();
                List<HealthCheckExecutionResult> execResults;
                if (executionResult instanceof CombinedExecutionResult) {
                    execResults = ((CombinedExecutionResult) executionResult).getExecutionResults();
                } else {
                    execResults = Arrays.asList(executionResult);
                }
                return execResults.stream();
            })
            .sorted()
            .collect(toList());
        
        if(executionResults.isEmpty()) {
            return;
        }
        
        CombinedExecutionResult combinedResultForLogging = new CombinedExecutionResult(executionResults);
        Status hcStatus = combinedResultForLogging.getHealthCheckResult().getStatus();
        if(!LOG.isInfoEnabled() && hcStatus == Status.OK) {
            return;
        }

        String logMsg = resultTxtVerboseSerializer.serialize(combinedResultForLogging.getHealthCheckResult(), combinedResultForLogging.getExecutionResults(), false);
        String firstLineMsg = (logResults == ChangeType.STATUS_CHANGES) ? "Status Changes:" : "";
        if(hcStatus == Status.OK) {
            LOG.info(firstLineMsg+"\n"+logMsg);
        } else {
            LOG.warn(firstLineMsg+"\n"+logMsg);
        }

    }
    
    private void runWithThreadNameContext(Runnable r) {
        String threadNameToRestore = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName(monitorId);
            r.run();
        } finally {
            Thread.currentThread().setName(threadNameToRestore);
        }
    }


    ExtendedHealthCheckExecutor getExecutor() {
        return executor;
    }

    EventAdmin getEventAdmin() {
        return eventAdmin;
    }

    boolean isRegisterHealthyMarkerService() {
        return registerHealthyMarkerService;
    }

    boolean isRegisterUnhealthyMarkerService() {
        return registerUnhealthyMarkerService;
    }

    ChangeType getSendEvents() {
        return sendEvents;
    }

    BundleContext getBundleContext() {
        return bundleContext;
    }

    boolean isTreatWarnAsHealthy() {
        return treatWarnAsHealthy;
    }

    private final class HealthCheckServiceListener implements ServiceListener {
        @Override
        public void serviceChanged(ServiceEvent event) {
            updateHealthStatesMap();
        }
    }
    
}

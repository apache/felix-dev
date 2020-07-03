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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.condition.Healthy;
import org.apache.felix.hc.api.condition.SystemReady;
import org.apache.felix.hc.api.condition.Unhealthy;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckExecutor;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.apache.felix.hc.core.impl.executor.CombinedExecutionResult;
import org.apache.felix.hc.core.impl.executor.HealthCheckExecutorThreadPool;
import org.apache.felix.hc.core.impl.scheduling.AsyncIntervalJob;
import org.apache.felix.hc.core.impl.scheduling.AsyncJob;
import org.apache.felix.hc.core.impl.scheduling.CronJobFactory;
import org.apache.felix.hc.core.impl.util.lang.StringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
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

    public static final String TAG_SYSTEMREADY = "systemready";

    public static final String EVENT_TOPIC_PREFIX = "org/apache/felix/health";
    public static final String EVENT_TOPIC_SUFFIX_STATUS_CHANGED = "STATUS_CHANGED";
    public static final String EVENT_TOPIC_SUFFIX_STATUS_UPDATED = "UPDATED";

    public static final String EVENT_PROP_EXECUTION_RESULT = "executionResult";
    public static final String EVENT_PROP_STATUS = "status";
    public static final String EVENT_PROP_PREVIOUS_STATUS = "previousStatus";

    static final Healthy MARKER_SERVICE_HEALTHY = new Healthy() {
    };
    static final Unhealthy MARKER_SERVICE_UNHEALTHY = new Unhealthy() {
    };
    static final SystemReady MARKER_SERVICE_SYSTEMREADY = new SystemReady() {
    };

    public enum SendEventsConfig {
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
        SendEventsConfig sendEvents() default SendEventsConfig.STATUS_CHANGES;

        @AttributeDefinition
        String webconsole_configurationFactory_nameHint() default "Health Monitor for '{tags}'/'{names}', {intervalInSec}sec/{cronExpression}, Marker Service Healthy:{registerHealthyMarkerService} Unhealthy:{registerUnhealthyMarkerService}, Send Events {sendEvents}";
    }

    @Reference
    HealthCheckExecutor executor;

    @Reference
    HealthCheckExecutorThreadPool healthCheckExecutorThreadPool;

    @Reference
    CronJobFactory cronJobFactory;

    @Reference
    private EventAdmin eventAdmin;

    // component state
    AsyncJob monitorJob = null;
    List<String> tags;
    List<String> names;
    List<HealthState> healthStates = new ArrayList<>();

    private long intervalInSec;
    private String cronExpression;

    private boolean registerHealthyMarkerService;
    private boolean registerUnhealthyMarkerService;

    private boolean treatWarnAsHealthy;

    private SendEventsConfig sendEventsConfig;

    private BundleContext bundleContext;
    
    private String monitorId;

    @Activate
    protected final void activate(BundleContext bundleContext, Config config, ComponentContext componentContext)
            throws InvalidSyntaxException {

        this.bundleContext = bundleContext;

        this.tags = Arrays.asList(config.tags());
        this.tags.stream().filter(StringUtils::isNotBlank).forEach(tag -> healthStates.add(new HealthState(tag, true)));

        this.names = Arrays.asList(config.names());
        this.names.stream().filter(StringUtils::isNotBlank)
                .forEach(name -> healthStates.add(new HealthState(name, false)));

        this.registerHealthyMarkerService = config.registerHealthyMarkerService();
        this.registerUnhealthyMarkerService = config.registerUnhealthyMarkerService();

        this.treatWarnAsHealthy = config.treatWarnAsHealthy();
        this.sendEventsConfig = config.sendEvents();

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
        LOG.info("HealthCheckMonitor active for tags {} and names {}", this.tags, this.names);
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
        healthStates.stream().forEach(HealthState::cleanUp);
        healthStates.clear();
        monitorJob.unschedule();
        LOG.info("HealthCheckMonitor deactivated for tags {} and names {}", this.tags, this.names);
    }

    public void run() {
        String threadNameToRestore = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName(monitorId);
            
            // run in tags/names in parallel
            healthStates.parallelStream().forEach(this::runFor);

            LOG.trace("HealthCheckMonitor: updated results for tags {} and names {}", this.tags, this.names);
        } catch (Exception e) {
            LOG.error("Exception HealthCheckMonitor run(): " + e, e);
        } finally {
            Thread.currentThread().setName(threadNameToRestore);
        }
    }

    public void runFor(HealthState healthState) {
        String threadNameToRestore = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName(monitorId);
            HealthCheckSelector selector = healthState.isTag ? HealthCheckSelector.tags(healthState.tagOrName)
                    : HealthCheckSelector.names(healthState.tagOrName);
            List<HealthCheckExecutionResult> executionResults = executor.execute(selector);

            HealthCheckExecutionResult result = executionResults.size() == 1 ? executionResults.get(0)
                    : new CombinedExecutionResult(executionResults, Result.Status.TEMPORARILY_UNAVAILABLE);
            LOG.trace("Result of '{}' => {}", healthState.tagOrName, result.getHealthCheckResult().getStatus());

            healthState.update(result);
        } finally {
            Thread.currentThread().setName(threadNameToRestore);
        }
    }

    
    class HealthState {

        private String tagOrName;
        private boolean isTag;
        private String propertyName;

        private ServiceRegistration<?> healthyRegistration = null;
        private ServiceRegistration<Unhealthy> unhealthyRegistration = null;

        private Result.Status status = null;
        private boolean isHealthy = false;
        private boolean statusChanged = false;

        HealthState(String tagOrName, boolean isTag) {
            this.tagOrName = tagOrName;
            this.isTag = isTag;
            this.propertyName = isTag ? "tag" : "name";
        }

        @Override
        public String toString() {
            return "[HealthState tagOrName=" + tagOrName + ", isTag=" + isTag + ", status=" + status + ", isHealthy="
                    + isHealthy + ", statusChanged=" + statusChanged + "]";
        }

        synchronized void update(HealthCheckExecutionResult executionResult) {
            Result.Status previousStatus = status;
            status = executionResult.getHealthCheckResult().getStatus();

            isHealthy = (status == Result.Status.OK || (treatWarnAsHealthy && status == Result.Status.WARN));
            statusChanged = previousStatus != status;
            LOG.trace("  {}: isHealthy={} statusChanged={}", tagOrName, isHealthy, statusChanged);

            registerMarkerServices();
            sendEvents(executionResult, previousStatus);
        }

        private void registerMarkerServices() {
            if (registerHealthyMarkerService) {
                if (isHealthy && healthyRegistration == null) {
                    registerHealthyService();
                } else if (!isHealthy && healthyRegistration != null) {
                    unregisterHealthyService();
                }
            }
            if (registerUnhealthyMarkerService) {
                if (!isHealthy && unhealthyRegistration == null) {
                    registerUnhealthyService();
                } else if (isHealthy && unhealthyRegistration != null) {
                    unregisterUnhealthyService();
                }
            }
        }

        private void registerHealthyService() {
            if (healthyRegistration == null) {
                LOG.debug("HealthCheckMonitor: registerHealthyService() {} ", tagOrName);
                Dictionary<String, String> registrationProps = new Hashtable<>();
                registrationProps.put(propertyName, tagOrName);
                registrationProps.put("activated", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

                if (TAG_SYSTEMREADY.equals(tagOrName)) {
                    LOG.debug("HealthCheckMonitor: SYSTEM READY");
                    healthyRegistration = bundleContext.registerService(
                            new String[] { SystemReady.class.getName(), Healthy.class.getName() },
                            MARKER_SERVICE_SYSTEMREADY, registrationProps);
                } else {
                    healthyRegistration = bundleContext.registerService(Healthy.class, MARKER_SERVICE_HEALTHY,
                            registrationProps);
                }
                LOG.debug("HealthCheckMonitor: Healthy service for {} '{}' registered", propertyName, tagOrName);
            }
        }

        private void unregisterHealthyService() {
            if (healthyRegistration != null) {
                healthyRegistration.unregister();
                healthyRegistration = null;
                LOG.debug("HealthCheckMonitor: Healthy service for {} '{}' unregistered", propertyName, tagOrName);
            }
        }

        private void registerUnhealthyService() {
            if (unhealthyRegistration == null) {
                Dictionary<String, String> registrationProps = new Hashtable<>();
                registrationProps.put("tag", tagOrName);
                registrationProps.put("activated", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                unhealthyRegistration = bundleContext.registerService(Unhealthy.class, MARKER_SERVICE_UNHEALTHY,
                        registrationProps);
                LOG.debug("HealthCheckMonitor: Unhealthy service for {} '{}' registered", propertyName, tagOrName);
            }
        }

        private void unregisterUnhealthyService() {
            if (unhealthyRegistration != null) {
                unhealthyRegistration.unregister();
                unhealthyRegistration = null;
                LOG.debug("HealthCheckMonitor: Unhealthy service for {} '{}' unregistered", propertyName, tagOrName);
            }
        }

        private void sendEvents(HealthCheckExecutionResult executionResult, Result.Status previousStatus) {
            if ((sendEventsConfig == SendEventsConfig.STATUS_CHANGES && statusChanged) || sendEventsConfig == SendEventsConfig.ALL) {
                
                String eventSuffix = statusChanged ? EVENT_TOPIC_SUFFIX_STATUS_CHANGED : EVENT_TOPIC_SUFFIX_STATUS_UPDATED;
                String logMsg = "Posted event for topic '{}': " + (statusChanged ? "Status change from {} to {}" : "Result updated (status {})");

                Map<String, Object> properties = new HashMap<>();
                properties.put(EVENT_PROP_STATUS, status);
                if (previousStatus != null) {
                    properties.put(EVENT_PROP_PREVIOUS_STATUS, previousStatus);
                }
                properties.put(EVENT_PROP_EXECUTION_RESULT, executionResult);
                String topic = String.join("/", EVENT_TOPIC_PREFIX, propertyName, tagOrName.replaceAll("\\s+", "_"), eventSuffix);
                eventAdmin.postEvent(new Event(topic, properties));
                LOG.debug(logMsg, topic, previousStatus, status);
                if (!(executionResult instanceof CombinedExecutionResult)) {
                    String componentName = (String) executionResult.getHealthCheckMetadata().getServiceReference()
                            .getProperty(ComponentConstants.COMPONENT_NAME);
                    if (StringUtils.isNotBlank(componentName)) {
                        String topicClass = String.join("/", EVENT_TOPIC_PREFIX, "component", componentName.replace(".", "/"), eventSuffix);
                        eventAdmin.postEvent(new Event(topicClass, properties));
                        LOG.debug(logMsg, topicClass, previousStatus, status);
                    }
                }
            }
        }

        synchronized void cleanUp() {
            unregisterHealthyService();
            unregisterUnhealthyService();
        }

    }
}

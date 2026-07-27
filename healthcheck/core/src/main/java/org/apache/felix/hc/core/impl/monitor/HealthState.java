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

import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.condition.Healthy;
import org.apache.felix.hc.api.condition.SystemReady;
import org.apache.felix.hc.api.condition.Unhealthy;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckMetadata;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.apache.felix.hc.core.impl.executor.CombinedExecutionResult;
import org.apache.felix.hc.core.impl.monitor.HealthCheckMonitor.ChangeType;
import org.apache.felix.hc.core.impl.util.lang.StringUtils;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.condition.Condition;
import org.osgi.service.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HealthState {
    private static final Logger LOG = LoggerFactory.getLogger(HealthState.class);

    public static final String TAG_SYSTEMREADY = "systemready";

    public static final String EVENT_TOPIC_PREFIX = "org/apache/felix/health";
    public static final String EVENT_TOPIC_SUFFIX_STATUS_CHANGED = "STATUS_CHANGED";
    public static final String EVENT_TOPIC_SUFFIX_STATUS_UPDATED = "UPDATED";

    public static final String EVENT_PROP_EXECUTION_RESULT = "executionResult";
    public static final String EVENT_PROP_STATUS = "status";
    public static final String EVENT_PROP_PREVIOUS_STATUS = "previousStatus";

    static final class HealthyCondition implements Condition, Healthy {};
    static final class SystemReadyCondition implements Condition, SystemReady {};

    static final Healthy MARKER_SERVICE_HEALTHY = new HealthyCondition();
    static final Unhealthy MARKER_SERVICE_UNHEALTHY = new Unhealthy() {
    };
    static final SystemReady MARKER_SERVICE_SYSTEMREADY = new SystemReadyCondition();

    private final HealthCheckMonitor monitor;

    private final String tagOrName;
    private final ServiceReference<HealthCheck> healthCheckRef;
    private final boolean isTag;
    private final String propertyName;

    private ServiceRegistration<?> healthyRegistration = null;
    private ServiceRegistration<Unhealthy> unhealthyRegistration = null;

    private HealthCheckExecutionResult executionResult;
    private Result.Status status = null;
    private boolean isHealthy = false;
    private boolean statusChanged = false;
    private boolean isLive = true;

    HealthState(HealthCheckMonitor healthCheckMonitor, ServiceReference<HealthCheck> ref) {
        this.monitor = healthCheckMonitor;
        HealthCheckMetadata metadata = new HealthCheckMetadata(ref);
        this.tagOrName = metadata.getTitle(); // using title here to ensure it is never null, usually title is nc.name
        this.healthCheckRef = ref;
        this.isTag = false;
        this.propertyName = this.isTag ? "tag" : "name";
    }

    HealthState(HealthCheckMonitor healthCheckMonitor, String tagOrName, boolean isTag) {
        this.monitor = healthCheckMonitor;
        this.tagOrName = tagOrName;
        this.healthCheckRef = null;
        this.isTag = isTag;
        this.propertyName = isTag ? "tag" : "name";
    }

    @Override
    public String toString() {
        return "[HealthState tagOrName=" + tagOrName + (healthCheckRef!=null?" (service ref)":"") + ", isTag=" + isTag + ", status=" + status + ", isHealthy="
                + isHealthy + ", statusChanged=" + statusChanged + "]";
    }

    public boolean hasChanged() {
        return statusChanged;
    }

    public boolean isHealthy() {
        return isHealthy;
    }

    String getTagOrName() {
        return tagOrName;
    }

    boolean isTag() {
        return isTag;
    }

    HealthCheckExecutionResult getExecutionResult() {
        return executionResult;
    }

    public void update() {

        List<HealthCheckExecutionResult> executionResults;
        if(healthCheckRef != null) {
            executionResults = Arrays.asList(monitor.getExecutor().execute(healthCheckRef));
        } else {
            HealthCheckSelector selector = isTag ? HealthCheckSelector.tags(tagOrName)  : HealthCheckSelector.names(tagOrName);
            executionResults = monitor.getExecutor().execute(selector);
        }

        HealthCheckExecutionResult result = executionResults.size() == 1 ? executionResults.get(0)
                : new CombinedExecutionResult(executionResults, Result.Status.TEMPORARILY_UNAVAILABLE);
        LOG.trace("Result of '{}' => {}", tagOrName, result.getHealthCheckResult().getStatus());

        update(result);

    }

    synchronized void update(HealthCheckExecutionResult executionResult) {
        if(!isLive) {
            LOG.trace("Not live anymore, skipping result update for {}", this);
            return;
        }
        this.executionResult = executionResult;
        Result.Status previousStatus = status;
        status = executionResult.getHealthCheckResult().getStatus();

        isHealthy = (status == Result.Status.OK || (monitor.isTreatWarnAsHealthy() && status == Result.Status.WARN));
        statusChanged = previousStatus != status;
        LOG.trace("  {}: isHealthy={} statusChanged={}", tagOrName, isHealthy, statusChanged);

        registerMarkerServices();
        sendEvents(executionResult, previousStatus);
    }

    private void registerMarkerServices() {
        if (monitor.isRegisterHealthyMarkerService()) {
            if (isHealthy && healthyRegistration == null) {
                registerHealthyService();
            } else if (!isHealthy && healthyRegistration != null) {
                unregisterHealthyService();
            }
        }
        if (monitor.isRegisterUnhealthyMarkerService()) {
            if (!isHealthy && unhealthyRegistration == null) {
                registerUnhealthyService();
            } else if (isHealthy && unhealthyRegistration != null) {
                unregisterUnhealthyService();
            }
        }
    }

    private void registerHealthyService() {
        if (healthyRegistration == null) {
            final boolean isSystemReady = TAG_SYSTEMREADY.equals(tagOrName);
            LOG.debug("HealthCheckMonitor: registerHealthyService() {} ", tagOrName);
            Dictionary<String, String> registrationProps = new Hashtable<>();
            registrationProps.put(propertyName, tagOrName);
            registrationProps.put("activated", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            registrationProps.put(Condition.CONDITION_ID, "felix.hc.".concat(tagOrName));

            if (isSystemReady) {
                LOG.debug("HealthCheckMonitor: SYSTEM READY");
            }
            final List<String> services = new ArrayList<>();
            services.add(Healthy.class.getName());
            services.add(Condition.class.getName());
            if (isSystemReady) {
                services.add(SystemReady.class.getName());
            }
            final Object service = isSystemReady ? MARKER_SERVICE_SYSTEMREADY : MARKER_SERVICE_HEALTHY;
            healthyRegistration = monitor.getBundleContext().registerService(services.toArray(new String[0]), service, registrationProps);
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
            unhealthyRegistration = monitor.getBundleContext().registerService(Unhealthy.class, MARKER_SERVICE_UNHEALTHY,
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
        ChangeType sendEventsConfig = monitor.getSendEvents();
        if (sendEventsConfig == ChangeType.ALL
                || (statusChanged && (sendEventsConfig == ChangeType.STATUS_CHANGES || sendEventsConfig == ChangeType.STATUS_CHANGES_OR_NOT_OK))
                || (!executionResult.getHealthCheckResult().isOk() && sendEventsConfig == ChangeType.STATUS_CHANGES_OR_NOT_OK)) {

            String eventSuffix = statusChanged ? EVENT_TOPIC_SUFFIX_STATUS_CHANGED : EVENT_TOPIC_SUFFIX_STATUS_UPDATED;
            String logMsg = "Posted event for topic '{}': " + (statusChanged ? "Status change from {} to {}" : "Result updated (status {})");

            Map<String, Object> properties = new HashMap<>();
            properties.put(EVENT_PROP_STATUS, status);
            if (previousStatus != null) {
                properties.put(EVENT_PROP_PREVIOUS_STATUS, previousStatus);
            }
            properties.put(EVENT_PROP_EXECUTION_RESULT, executionResult);
            String topic = String.join("/", EVENT_TOPIC_PREFIX, propertyName, tagOrName.replaceAll("[^A-Za-z0-9-_]+", "_"), eventSuffix);
            monitor.getEventAdmin().postEvent(new Event(topic, properties));
            LOG.debug(logMsg, topic, previousStatus, status);
            if (!(executionResult instanceof CombinedExecutionResult)) {
                String componentName = (String) executionResult.getHealthCheckMetadata().getServiceReference()
                        .getProperty(ComponentConstants.COMPONENT_NAME);
                if (StringUtils.isNotBlank(componentName)) {
                    String topicClass = String.join("/", EVENT_TOPIC_PREFIX, "component", componentName.replace(".", "/"), eventSuffix);
                    monitor.getEventAdmin().postEvent(new Event(topicClass, properties));
                    LOG.debug(logMsg, topicClass, previousStatus, status);
                }
            }
        }
    }

    synchronized void cleanUp() {
        unregisterHealthyService();
        unregisterUnhealthyService();
        isLive = false;
    }

}

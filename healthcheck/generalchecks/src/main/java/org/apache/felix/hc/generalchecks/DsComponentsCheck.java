/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.hc.generalchecks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.felix.hc.annotation.HealthCheckService;
import org.apache.felix.hc.api.FormattingResultLog;
import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.ResultLog.Entry;
import org.apache.felix.hc.generalchecks.scrutil.DsRootCauseAnalyzer;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
@HealthCheckService(name = DsComponentsCheck.HC_NAME, tags = { DsComponentsCheck.HC_DEFAULT_TAG })
@Designate(ocd = DsComponentsCheck.Config.class, factory = true)
public class DsComponentsCheck implements HealthCheck {

    private final Logger LOG = LoggerFactory.getLogger(DsComponentsCheck.class);

    public static final String HC_NAME = "DS Components Ready Check";
    public static final String HC_DEFAULT_TAG = "systemalive";

    @ObjectClassDefinition(name = "Health Check: "
            + HC_NAME, description = "System ready check that checks a list of DS components and provides root cause analysis in case of errors")
    public @interface Config {

        @AttributeDefinition(name = "Name", description = "Name of this health check")
        String hc_name() default HC_NAME;

        @AttributeDefinition(name = "Tags", description = "List of tags for this health check, used to select subsets of health checks for execution e.g. by a composite health check.")
        String[] hc_tags() default { HC_DEFAULT_TAG };

        @AttributeDefinition(name = "Required Component Names", description = "The components that are required to be enabled")
        String[] components_list();

        @AttributeDefinition(name = "Status for missing component", description = "Status in case components are missing enabled components")
        Result.Status statusForMissing() default Result.Status.TEMPORARILY_UNAVAILABLE;

        @AttributeDefinition
        String webconsole_configurationFactory_nameHint() default "{hc.name}: {components.list} / missing -> {statusForMissing}";
    }

    private List<String> componentsList;

    private Result.Status statusForMissing;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private DsRootCauseAnalyzer analyzer;

    volatile ServiceComponentRuntime scr;

    private final AtomicBoolean refreshCache = new AtomicBoolean();

    private final AtomicReference<Result> cache = new AtomicReference<>();

    @Activate
    public void activate(final BundleContext ctx, final Config config) throws InterruptedException {
        componentsList = Arrays.asList(config.components_list());
        statusForMissing = config.statusForMissing();
        this.refreshCache.set(false); // cache is empty
        LOG.debug("Activated DS Components HC for componentsList={}", componentsList);
    }

    @Override
    public Result execute() {
        Result result = this.cache.get();
        if ( result == null || this.refreshCache.compareAndSet(true, false) ) {
            FormattingResultLog log = new FormattingResultLog();

            Collection<ComponentDescriptionDTO> componentDescriptionDTOs = null;
            try {
                componentDescriptionDTOs = scr.getComponentDescriptionDTOs();
            } catch ( final Throwable e) {
                log.temporarilyUnavailable("Exception while getting ds component dtos {}", e.getMessage(), e);
            }
            if ( componentDescriptionDTOs != null ) {
                final List<ComponentDescriptionDTO> watchedComps = new LinkedList<ComponentDescriptionDTO>();
                final List<String> missingComponents = new LinkedList<String>(componentsList);
                for (final ComponentDescriptionDTO desc : componentDescriptionDTOs) {
                    if (componentsList.contains(desc.name)) {
                        watchedComps.add(desc);
                        missingComponents.remove(desc.name);
                    }
                }
                for (final String missingComp : missingComponents) {
                    log.info("No component with name {} is registered in SCR runtime", missingComp);
                }

                int countEnabled = 0;
                int countDisabled = 0;
                for (final ComponentDescriptionDTO dsComp : watchedComps) {

                    boolean isActive;

                    boolean componentEnabled = scr.isComponentEnabled(dsComp);
                    if (componentEnabled) {

                        try {
                            Collection<ComponentConfigurationDTO> componentConfigurationDTOs = scr.getComponentConfigurationDTOs(dsComp);
                            List<String> idStateTuples = new ArrayList<>();
                            boolean foundActiveOrSatisfiedConfig = false;
                            for (ComponentConfigurationDTO configDto : componentConfigurationDTOs) {
                                idStateTuples.add("id " + configDto.id + ":" + toStateString(configDto.state));
                                if (configDto.state == ComponentConfigurationDTO.ACTIVE || configDto.state == ComponentConfigurationDTO.SATISFIED) {
                                    foundActiveOrSatisfiedConfig = true;
                                }
                            }
                            log.debug(dsComp.name + " (" + String.join(",", idStateTuples) + ")");

                            if (componentConfigurationDTOs.isEmpty() || foundActiveOrSatisfiedConfig) {
                                countEnabled++;
                                isActive = true;
                            } else {
                                countDisabled++;
                                isActive = false;
                            }
                        } catch ( final Throwable e) {
                            log.temporarilyUnavailable("Exception while getting ds component dtos {}", e.getMessage(), e);
                            isActive = true; // no info available, doesn't make sense to report as inactive
                        }

                    } else {
                        countDisabled++;
                        isActive = false;
                    }

                    if (!isActive) {
                        analyzer.logNotEnabledComponent(log, dsComp, componentDescriptionDTOs);
                    }
                }

                if (!missingComponents.isEmpty()) {
                    log.add(new Entry(statusForMissing, missingComponents.size() + " required components are missing in SCR runtime"));
                }
                if (countDisabled > 0) {
                    log.add(new Entry(statusForMissing, countDisabled + " required components are not active"));
                }
                log.info("{} required components are active", countEnabled);
            }
            result = new Result(log);
            this.cache.set(result);
        }
        return result;
    }

    static final String toStateString(int state) {

        final int FAILED_ACTIVATION = 16; // not yet available in r6, but dependency should be left on r6 for max compatibility

        switch (state) {
        case ComponentConfigurationDTO.ACTIVE:
            return "active";
        case ComponentConfigurationDTO.SATISFIED:
            return "satisfied";
        case ComponentConfigurationDTO.UNSATISFIED_CONFIGURATION:
            return "unsatisfied (configuration)";
        case ComponentConfigurationDTO.UNSATISFIED_REFERENCE:
            return "unsatisfied (reference)";
        case FAILED_ACTIVATION:
            return "failed activation";
        default:
            return String.valueOf(state);
        }
    }

    @Reference(updated = "updatedServiceComponentRuntime")
    private void setServiceComponentRuntime(final ServiceComponentRuntime c) {
        this.scr = c;
    }

    private void unsetServiceComponentRuntime(final ServiceComponentRuntime c) {
        this.scr = null;
    }

    private void updatedServiceComponentRuntime(final ServiceComponentRuntime c) {
        // change in DS - mark cache
        this.refreshCache.compareAndSet(false, true);
    }
}

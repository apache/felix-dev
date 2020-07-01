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
package org.apache.felix.hc.core.impl.scheduling.cron;

import static org.apache.felix.hc.core.impl.scheduling.cron.CronSchedulerProviderEnabler.CronType.EMBEDDED;
import static org.apache.felix.hc.core.impl.scheduling.cron.CronSchedulerProviderEnabler.CronType.QUARTZ;
import static org.apache.felix.hc.core.impl.scheduling.cron.HealthCheckCronScheduler.CRON_TYPE_PROPERTY;
import static org.osgi.framework.namespace.PackageNamespace.PACKAGE_NAMESPACE;

import java.util.Collection;

import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public final class CronSchedulerProviderEnabler {

    enum CronType {
        QUARTZ, EMBEDDED
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String QUARTZ_PACKAGE_PREFIX = "org.quartz";

    @Reference
    private ServiceComponentRuntime scr;

    private BundleContext bundleContext;

    @Activate
    void activate(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        if (isQuartzPackageImported()) {
            enableCronSchedulerProvider(QUARTZ);
            logger.info("Quartz cron scheduler enabled");
        } else {
            enableCronSchedulerProvider(EMBEDDED);
            logger.info("Embedded cron scheduler enabled");
        }
    }

    private void enableCronSchedulerProvider(final CronType cronType) {
        final Collection<ComponentDescriptionDTO> dtos = scr.getComponentDescriptionDTOs(bundleContext.getBundle());
        for (final ComponentDescriptionDTO dto : dtos) {
            if (dto.properties.containsKey(CRON_TYPE_PROPERTY)) {
                final String type = (String) dto.properties.get(CRON_TYPE_PROPERTY);
                if (type.equalsIgnoreCase(cronType.name())) {
                    scr.enableComponent(dto);
                }
            }
        }
    }

    private boolean isQuartzPackageImported() {
        final BundleWiring wiring = bundleContext.getBundle().adapt(BundleWiring.class);
        for (final BundleWire wire : wiring.getRequiredWires(PACKAGE_NAMESPACE)) {
            final String pkg = (String) wire.getCapability().getAttributes().get(PACKAGE_NAMESPACE);
            if (pkg.startsWith(QUARTZ_PACKAGE_PREFIX)) {
                return true;
            }
        }
        return false;
    }

}

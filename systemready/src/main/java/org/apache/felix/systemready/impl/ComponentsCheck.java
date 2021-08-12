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
package org.apache.felix.systemready.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.felix.rootcause.DSComp;
import org.apache.felix.rootcause.DSRootCause;
import org.apache.felix.rootcause.RootCausePrinter;
import org.apache.felix.systemready.CheckStatus;
import org.apache.felix.systemready.StateType;
import org.apache.felix.systemready.SystemReadyCheck;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = {SystemReadyCheck.class},
        name = ComponentsCheck.PID,
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd=ComponentsCheck.Config.class)
public class ComponentsCheck implements SystemReadyCheck, ServiceListener {

    public static final String PID = "org.apache.felix.systemready.impl.ComponentsCheck";

    @ObjectClassDefinition(
            name="DS Components System Ready Check",
            description="System ready check that checks a list of DS components"
                + "and provides root cause analysis in case of errors"
    )
    public @interface Config {

        @AttributeDefinition(name = "Components list", description = "The components that need to come up before this check reports GREEN")
        String[] components_list();

        @AttributeDefinition(name = "Check type")
        StateType type() default StateType.ALIVE;

    }
    private final Logger log = LoggerFactory.getLogger(getClass());

    private List<String> componentsList;

    private DSRootCause analyzer;

    private StateType type;

    @Reference
    private ServiceComponentRuntime scr;

    private final AtomicReference<CheckStatus> cache = new AtomicReference<>();

    private static final CheckStatus INVALID = new CheckStatus("invalid", StateType.READY, CheckStatus.State.RED, "invalid");

    @Activate
    public void activate(final BundleContext ctx, final Config config) throws InterruptedException, InvalidSyntaxException {
        this.analyzer = new DSRootCause(scr);
        this.type = config.type();
        this.componentsList = Arrays.asList(config.components_list());
        this.cache.set(INVALID);
//        ctx.addServiceListener(this, "(objectClass=" + ServiceComponentRuntime.class.getName() + ")");
    }

    @Deactivate
    public void deactivate(final BundleContext ctx) {
//        ctx.removeServiceListener(this);
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
//        log.info("CALLED");
        this.cache.set(INVALID);
    }

    @Override
    public String getName() {
        return "Components Check " + componentsList;
    }

    private List<DSComp> getComponents(final Collection<ComponentDescriptionDTO> descriptions) {
        try {
            return descriptions.stream()
                .filter(desc -> componentsList.contains(desc.name))
                .map(analyzer::getRootCause)
                .collect(Collectors.toList());
        } catch (Throwable e) {
            log.error("Exception while getting ds component dtos {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public CheckStatus getStatus() {
        CheckStatus result = null;
/*
        while ( result == null )
        {
            this.cache.compareAndSet(INVALID, null);
            result = this.cache.get();
            if ( result == INVALID )
            {
                result = null; // repeat
            }
            else if ( result == null )
            {
*/                final List<DSComp> watchedComps = getComponents(scr.getComponentDescriptionDTOs());
                if (watchedComps.size() < componentsList.size()) {
                    final List<String> missed = new ArrayList<>(this.componentsList);
                    for(final DSComp c : watchedComps)
                    {
                        missed.remove(c.desc.name);
                    }
                    result = new CheckStatus(getName(), type, CheckStatus.State.RED, "Not all named components could be found, missing : " + missed);
                } else {
                    try {
                        final StringBuilder details = new StringBuilder();
                        watchedComps.stream().forEach(dsComp -> addDetails(dsComp, details));
                        final CheckStatus.State state = CheckStatus.State.worstOf(watchedComps.stream().map(this::status));
                        result = new CheckStatus(getName(), type, state, details.toString());
                    } catch (Throwable e) {
                        log.error("Exception while checking ds component dtos {}", e.getMessage(), e);
                        throw e;
                    }
                }
/*                if ( !this.cache.compareAndSet(null, result) )
                {
                    result = null;
                }
            }
        }
*/        return result;
     }

    private CheckStatus.State status(DSComp component) {
        boolean missingConfig = component.config == null && "require".equals(component.desc.configurationPolicy);
        boolean unsatisfied = !component.unsatisfied.isEmpty();
        return (missingConfig || unsatisfied) ? CheckStatus.State.YELLOW : CheckStatus.State.GREEN;
    }

    private void addDetails(final DSComp component, final StringBuilder details) {
        RootCausePrinter printer = new RootCausePrinter(st -> details.append(st + "\n"));
        printer.print(component);
    }
}

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
package org.apache.felix.hc.core.impl.commands;

import static org.apache.felix.hc.core.impl.servlet.ResultTxtVerboseSerializer.rightPad;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.execution.HealthCheckMetadata;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.apache.felix.hc.core.impl.util.HealthCheckFilter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Component(
        service = HealthCheckListCommand.class,
        property = {
                "osgi.command.scope=hc", 
                "osgi.command.function=list"
        }
        )
public class HealthCheckListCommand {
    
    private BundleContext bundleContext;

    @Activate
    protected final void activate(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
    public String list(String... params) {

        boolean isVerbose = false;
        for (String param : params) {
            if("-v".equals(param)) {
                isVerbose = true;
            } else if("-h".equals(param)) {
                return getHelpText();
            } else {
                System.out.println("unrecognized option: "+param);
                return getHelpText();
            }
        }

        HealthCheckFilter hcFilter = new HealthCheckFilter(bundleContext);
        HealthCheckSelector selector = HealthCheckSelector.empty();

        ServiceReference<HealthCheck>[] hcRefs = hcFilter.getHealthCheckServiceReferences(selector);
        Stream<ServiceReference<HealthCheck>> hcRefsStream = Arrays.asList(hcRefs).stream();
        
        if(isVerbose) {

            return hcRefsStream
                    .map(ref -> { 
                        HealthCheckMetadata metadata = new HealthCheckMetadata(ref);
                        return String.join(" ", 
                                rightPad(metadata.getTitle(), 60), 
                                rightPad(String.join(",", metadata.getTags()), 50), 
                                ref.getBundle().getSymbolicName());
                    })
                    .collect(Collectors.joining("\n"));
            
        } else {

            return hcRefsStream
                .flatMap(ref -> { return new HealthCheckMetadata(ref).getTags().stream(); })
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(","));

        }
    }

    private String getHelpText() {
        return "Usage: hc:list [-v]\n  -v verbose";
    }
}

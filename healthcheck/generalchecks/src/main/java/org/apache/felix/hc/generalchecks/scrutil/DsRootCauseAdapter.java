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
package org.apache.felix.hc.generalchecks.scrutil;

import java.util.Optional;
import java.util.function.Consumer;

import org.apache.felix.hc.api.FormattingResultLog;
import org.apache.felix.hc.api.Result.Status;
import org.apache.felix.hc.api.ResultLog.Entry;
import org.apache.felix.rootcause.DSComp;
import org.apache.felix.rootcause.DSRootCause;
import org.apache.felix.rootcause.RootCausePrinter;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;

/** Minimal bridge to root cause in order to allow making that dependency optional. */
public class DsRootCauseAdapter {

    private DSRootCause analyzer;

    public DsRootCauseAdapter(ServiceComponentRuntime scr) {
        this.analyzer = new DSRootCause(scr);
    }

    public void logMissingService(FormattingResultLog log, String missingServiceName) {
        Optional<DSComp> rootCauseOptional = analyzer.getRootCause(missingServiceName);
        if (rootCauseOptional.isPresent()) {
            logRootCause(log, rootCauseOptional.get());
        } else {
            log.info("Missing service without matching DS component: " + missingServiceName);
        }
    }

    public void logNotEnabledComponent(FormattingResultLog log, ComponentDescriptionDTO desc) {
        DSComp component = analyzer.getRootCause(desc);
        logRootCause(log, component);
    }

    private void logRootCause(FormattingResultLog log, DSComp component) {
        new RootCausePrinter(new Consumer<String>() {
            @Override
            public void accept(String str) {
                log.add(new Entry(Status.OK, str.replaceFirst("    ", "-- ").replaceFirst("  ", "- ")));
            }
        }).print(component);
    }
}

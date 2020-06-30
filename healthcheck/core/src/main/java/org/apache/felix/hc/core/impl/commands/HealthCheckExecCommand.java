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

import java.util.Arrays;
import java.util.List;

import org.apache.felix.hc.api.FormattingResultLog;
import org.apache.felix.hc.api.execution.HealthCheckExecutionOptions;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckExecutor;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.apache.felix.hc.core.impl.CompositeResult;
import org.apache.felix.hc.core.impl.servlet.ResultTxtVerboseSerializer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
        service = HealthCheckExecCommand.class,
        property = {
                "osgi.command.scope=hc", 
                "osgi.command.function=exec"
        }
        )
public class HealthCheckExecCommand {
    
    @Reference
    HealthCheckExecutor healthCheckExecutor;
    
    @Reference
    ResultTxtVerboseSerializer resultTxtVerboseSerializer;
    
    public String exec(String... params) {

        boolean isDebug = false;
        boolean combineWithOr = true;
        HealthCheckSelector selector = HealthCheckSelector.empty();
        for (String param : params) {
            if(param.startsWith("-")) {
                if("-v".equals(param)) {
                    isDebug = true;
                } else if("-a".equals(param)) {
                    combineWithOr = false;
                } else if("-h".equals(param)) {
                    return getHelpText();
                } else {
                    System.out.println("unrecognized option: "+param);
                }
            } else {
                selector = HealthCheckSelector.tags(param.split(","));
            }
        }
        if(selector.tags() == null) {
            return getHelpText();
        }
        
        HealthCheckExecutionOptions options = new HealthCheckExecutionOptions();
        options.setCombineTagsWithOr(combineWithOr);
        List<HealthCheckExecutionResult> executionResult = healthCheckExecutor.execute(selector, options);
        String result = resultTxtVerboseSerializer.serialize(new CompositeResult(new FormattingResultLog(), executionResult), executionResult, isDebug);
        return result;
    }

    private String getHelpText() {
        return "Usage: hc:exec [-v] [-a] tag1,tag2\n  -v verbose/debug\n  -a combine tags with and";
    }
}

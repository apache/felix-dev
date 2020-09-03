/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.metrics.osgi.impl;

import static org.ops4j.pax.exam.CoreOptions.frameworkProperty;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.inject.Inject;

import org.apache.felix.hc.api.condition.Healthy;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;

@RunWith(PaxExam.class)
public class HealthCheckSmokeIT extends AbstractIT {

    @Inject
    private BundleContext bc;
    
    @Override
    protected Option[] specificOptions() {
        return options(
            frameworkProperty("org.apache.felix.http.enable").value("false"),
            mavenBundle("org.apache.felix", "org.apache.felix.healthcheck.api", "2.0.4"),
            mavenBundle("org.apache.felix", "org.apache.felix.healthcheck.core", "2.0.8"),
            mavenBundle("org.apache.felix", "org.apache.felix.healthcheck.generalchecks", "2.0.4"),
            mavenBundle("org.apache.felix", "org.apache.felix.http.servlet-api", "1.1.2"),
            mavenBundle("org.apache.felix", "org.apache.felix.http.jetty", "4.0.18"),
            mavenBundle("org.apache.commons", "commons-lang3", "3.9"),
            mavenBundle("org.apache.felix", "org.apache.felix.eventadmin", "1.5.0"),
            mavenBundle("org.apache.felix", "org.apache.felix.rootcause", "0.1.0")
        );
    }
    
    @Override
    protected void markSystemReady() {
        Dictionary<String, Object> regProps = new Hashtable<>();
        regProps.put("tag", "systemalive");
        bc.registerService(Healthy.class, new Healthy() {}, regProps);
    }
}

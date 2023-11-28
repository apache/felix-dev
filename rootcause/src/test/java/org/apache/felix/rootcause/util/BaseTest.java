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
package org.apache.felix.rootcause.util;

import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.util.Optional;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;


public class BaseTest {
    @Inject
    public BundleContext context;

    @Inject
    public ServiceComponentRuntime scr;


    public Option baseConfiguration() {
        return CoreOptions.composite(
                systemProperty("pax.exam.invoker").value("junit"),
                systemProperty("pax.exam.osgi.unresolved.fail").value("true"),
                systemProperty("logback.configurationFile")
                    .value("src/test/resources/logback.xml"),
                mavenBundle().groupId("org.slf4j").artifactId("slf4j-api").version("1.7.6"),
                mavenBundle().groupId("ch.qos.logback").artifactId("logback-core").version("1.0.13"),
                mavenBundle().groupId("ch.qos.logback").artifactId("logback-classic").version("1.0.13"),

                bundle("link:classpath:META-INF/links/org.ops4j.pax.tipi.junit.link"),
                bundle("link:classpath:META-INF/links/org.ops4j.pax.exam.invoker.junit.link"),
                mavenBundle().groupId("org.apache.servicemix.bundles").artifactId("org.apache.servicemix.bundles.hamcrest").version("1.3_1"),
                mavenBundle().groupId("org.awaitility").artifactId("awaitility").version("3.1.0"),

                mavenBundle().groupId("org.osgi").artifactId("org.osgi.util.function").version("1.1.0"),
                mavenBundle().groupId("org.osgi").artifactId("org.osgi.util.promise").version("1.1.1"),
                mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.scr").version("2.1.26"),
                mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.configadmin").version("1.9.22"),
                bundle("reference:file:target/classes/")

        );
    }

    public ComponentDescriptionDTO getComponentDesc(String compName) {
        return getComponentDesc(desc -> desc.name.equals(compName), compName);
    }

    public ComponentDescriptionDTO getComponentDesc(Class<?> compClass) {
        return getComponentDesc(desc -> desc.implementationClass.equals(compClass.getName()), compClass.getName());
    }

    public ComponentDescriptionDTO getComponentDesc(Predicate<ComponentDescriptionDTO> predicate, String label) {
        Optional<ComponentDescriptionDTO> result = scr.getComponentDescriptionDTOs().stream()
                .filter(predicate)
                .findFirst();
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new RuntimeException("Component " + label + " not found");
        }
    }

}

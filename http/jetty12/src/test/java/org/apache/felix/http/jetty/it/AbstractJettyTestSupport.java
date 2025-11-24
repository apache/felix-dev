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
package org.apache.felix.http.jetty.it;

import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.keepCaches;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.ModifiableCompositeOption;
import org.ops4j.pax.exam.options.OptionalCompositeOption;
import org.ops4j.pax.exam.options.SystemPropertyOption;
import org.ops4j.pax.exam.options.UrlProvisionOption;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.ops4j.pax.exam.util.PathUtils;

public abstract class AbstractJettyTestSupport {
    protected static final String JETTY_VERSION = "12.0.30";

    private final String workingDirectory = String.format("%s/target/paxexam/%s/%s", PathUtils.getBaseDir(), getClass().getSimpleName(), UUID.randomUUID());

    /**
     * Provides a random path for a working directory below Maven's build target directory.
     *
     * @return the absolute path for working directory
     */
    protected String workingDirectory() {
        return workingDirectory;
    }

    @Configuration
    public Option[] configuration() throws IOException {
        final String vmOpt = System.getProperty("pax.vm.options");
        VMOption vmOption = null;
        if (vmOpt != null && !vmOpt.isEmpty()) {
            vmOption = new VMOption(vmOpt);
        }

        final int httpPort = findFreePort();

        return options(
                composite(
                        when(vmOption != null).useOptions(vmOption),
                        failOnUnresolvedBundles(),
                        keepCaches(),
                        localMavenRepo(),
                        CoreOptions.workingDirectory(workingDirectory()),
                        // update pax logging for SLF4J 2
                        mavenBundle().groupId("org.ops4j.pax.logging").artifactId("pax-logging-api").version("2.3.0"),
                        optionalRemoteDebug(),
                        mavenBundle().groupId("commons-fileupload").artifactId("commons-fileupload").version("1.6.0"),
                        mavenBundle().groupId("commons-io").artifactId("commons-io").version("2.19.0"),
                        mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.http.servlet-api").version("6.1.0"),
                        testBundle("bundle.filename"),
                        junitBundles(),
                        awaitility(),

                        config(),
                        felixHttpConfig(httpPort)
                ).add(
                        additionalOptions()
                )
        );
    }

    public static ModifiableCompositeOption awaitility() {
        return composite(
                mavenBundle().groupId("org.awaitility").artifactId("awaitility").version("4.2.1"),
                mavenBundle().groupId("org.hamcrest").artifactId("hamcrest").version("2.2")
        );
    }

    public static ModifiableCompositeOption config() {
        return composite(
                mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.configadmin").version("1.9.26")
        );
    }

    protected Option felixHttpConfig(final int httpPort) {
        return newConfiguration("org.apache.felix.http")
                .put("org.osgi.service.http.port", httpPort)
                .asOption();
    }

    protected Option[] additionalOptions() throws IOException { // NOSONAR
        return new Option[]{};
    }

    /**
     * Finds a free local port.
     *
     * @return the free local port
     */
    public static int findFreePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Provides an option to set the System property {@code pax.exam.osgi.unresolved.fail} to {@code "true"}.
     *
     * @return the property option
     */
    public static SystemPropertyOption failOnUnresolvedBundles() {
        return systemProperty("pax.exam.osgi.unresolved.fail").value("true");
    }

    /**
     * Reads the System property {@code maven.repo.local} and provides an option to set the System property {@code org.ops4j.pax.url.mvn.localRepository} when former is not empty.
     *
     * @return the property option
     */
    public static OptionalCompositeOption localMavenRepo() {
        final String localRepository = System.getProperty("maven.repo.local", ""); // PAXEXAM-543
        return when(!localRepository.isBlank()).useOptions(
                systemProperty("org.ops4j.pax.url.mvn.localRepository").value(localRepository)
        );
    }

    /**
     * Reads the pathname of the test bundle from the given System property and provides a provisioning option.
     *
     * @param systemProperty the System property which contains the pathname of the test bundle
     * @return the provisioning option
     */
    protected UrlProvisionOption testBundle(final String systemProperty) {
        final String pathname = System.getProperty(systemProperty);
        final File file = new File(pathname);
        return bundle(file.toURI().toString());
    }

    /**
     * Optionally configure remote debugging on the port supplied by the "debugPort"
     * system property.
     */
    protected ModifiableCompositeOption optionalRemoteDebug() {
        VMOption option = null;
        String property = System.getProperty("debugPort");
        if (property != null) {
            option = vmOption(String.format("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%s", property));
        }
        return composite(option);
    }

    public static ModifiableCompositeOption spifly() {
        return composite(
                mavenBundle().groupId("org.apache.aries.spifly").artifactId("org.apache.aries.spifly.dynamic.bundle").version("1.3.7"),
                mavenBundle().groupId("org.ow2.asm").artifactId("asm-analysis").version("9.7"),
                mavenBundle().groupId("org.ow2.asm").artifactId("asm-commons").version("9.7"),
                mavenBundle().groupId("org.ow2.asm").artifactId("asm-tree").version("9.7"),
                mavenBundle().groupId("org.ow2.asm").artifactId("asm-util").version("9.7"),
                mavenBundle().groupId("org.ow2.asm").artifactId("asm").version("9.7")
        );
    }
}

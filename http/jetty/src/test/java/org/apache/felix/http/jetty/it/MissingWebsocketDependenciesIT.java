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

import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;

/**
 *
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class MissingWebsocketDependenciesIT extends AbstractJettyTestSupport {

    @Inject
    protected BundleContext bundleContext;

    @Override
    protected Option felixHttpConfig(int httpPort) {
        return newConfiguration("org.apache.felix.http")
                .put("org.osgi.service.http.port", httpPort)
                .put("org.apache.felix.jetty.websocket.enable", true)
                .put("org.apache.felix.jakarta.websocket.enable", true)
                .asOption();
    }

    @Test
    public void testMissingDepencencyWarningLogs() throws Exception {
        // should have warnings in the log file output
        File logFile = new File("target/failsafe-reports/org.apache.felix.http.jetty.it.MissingWebsocketDependenciesIT-output.txt");
        assertTrue(logFile.exists());

        // wait for the log buffer to be written to the file
        Awaitility.await("waitForLogs")
            .atMost(Duration.ofSeconds(50))
            .pollDelay(Duration.ofMillis(200))
            .until(() -> containsString(logFile, "org.apache.felix.http.jetty [org.apache.felix.http]"));

        assertTrue(containsString(logFile, "org.apache.felix.http.jetty [org.apache.felix.http] WARN : Failed to initialize jetty specific websocket "
                + "support since the initializer class was not found. Check if the websocket-jetty-server bundle is deployed."));
        assertTrue(containsString(logFile, "org.apache.felix.http.jetty [org.apache.felix.http] WARN : Failed to initialize jakarta standard websocket"
                + " support since the initializer class was not found. Check if the websocket-jakarta-server bundle is deployed."));
    }

    /**
     * Checks if the text is present in the file
     *
     * @param file the file to check
     * @param expected the text to look for
     * @return true if the text was found, false otherwise
     */
    private boolean containsString(File file, String expected) throws IOException {
        try (Stream<String> stream = Files.lines(file.toPath())) {
            return stream.anyMatch(line -> line.contains(expected));
        }
    }

}

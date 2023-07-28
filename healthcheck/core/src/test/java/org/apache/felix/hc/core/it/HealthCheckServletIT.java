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
package org.apache.felix.hc.core.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.security.Provider.Service;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.Servlet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Verify that the HealthCheckExecutorServlet becomes available after creating the corresponding config */
@RunWith(PaxExam.class)
public class HealthCheckServletIT {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Inject
    private ConfigurationAdmin configAdmin;

    @Inject
    private BundleContext bundleContext;

    @Configuration
    public Option[] config() {
        return U.config();
    }

    private List<String> getRegisteredServletPaths() throws InvalidSyntaxException {
        final Collection<ServiceReference<Servlet>> refs = bundleContext.getServiceReferences(Servlet.class, null);
        final List<String> result = new ArrayList<>();
        for(final ServiceReference<Servlet> ref : refs) {
            final String path = (String) ref.getProperty(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN);
            if (path != null) {
                result.add(path);
            }
        }
        return result;
    }

    @Test
    public void testServletBecomesActive() throws InvalidSyntaxException, IOException, InterruptedException {
        final String servletPathPropertyName = "servletPath";
        final String servletPathPropertyVal = "/test/" + UUID.randomUUID();

        final List<String> initialPaths = this.getRegisteredServletPaths();
        assertEquals("Initially expecting no servlets", 0, initialPaths.size());

        // Activate servlet and wait for it to show up
        final String factoryPid = "org.apache.felix.hc.core.impl.servlet.HealthCheckExecutorServlet";
        org.osgi.service.cm.Configuration cfg = configAdmin.createFactoryConfiguration(factoryPid, null);
        log.info("Created factory configuration for servlet with pid = {}", cfg.getPid());
        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(servletPathPropertyName, servletPathPropertyVal);
        cfg.update(props);
        log.info("Updated config with properties {}", props);

        final long timeoutMsec = 5000L;
        final long endTime = System.currentTimeMillis() + timeoutMsec;
        int expectedServletCount = 6;
        while (System.currentTimeMillis() < endTime) {
            if (getRegisteredServletPaths().size() >= expectedServletCount) {
                break;
            }
            Thread.sleep(50L);
        }

        final List<String> paths = this.getRegisteredServletPaths();
        assertEquals("After adding configuration, expecting six servlets", expectedServletCount, paths.size());
        assertTrue("Expecting the HC servlet to be registered at " + servletPathPropertyVal, paths.contains(servletPathPropertyVal));
        assertTrue("Expecting the HTML HC servlet to be registered at " + servletPathPropertyVal + ".html", paths.contains(servletPathPropertyVal + ".html"));
        assertTrue("Expecting the JSON HC servlet to be registered at " + servletPathPropertyVal + ".json", paths.contains(servletPathPropertyVal + ".json"));
        assertTrue("Expecting the JSONP HC servlet to be registered at " + servletPathPropertyVal + ".jsonp", paths.contains(servletPathPropertyVal + ".jsonp"));
        assertTrue("Expecting the TXT HC servlet to be registered at " + servletPathPropertyVal + ".txt", paths.contains(servletPathPropertyVal + ".txt"));
        assertTrue("Expecting the verbose TXT HC servlet to be registered at " + servletPathPropertyVal + ".verbose.txt", paths.contains(servletPathPropertyVal + ".verbose.txt"));
    }
}

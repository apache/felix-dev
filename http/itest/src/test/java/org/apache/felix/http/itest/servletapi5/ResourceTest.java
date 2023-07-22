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
package org.apache.felix.http.itest.servletapi5;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.servlet.context.ServletContextHelper;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class ResourceTest extends Servlet5BaseIntegrationTest {

    @Test
    public void testHandleResourceRegistrationOk() throws Exception {
        long counter = this.getRuntimeCounter();
        ServletContextHelper context = new ServletContextHelper() {
            @Override
            public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
                return true;
            }

            @Override
            public URL getResource(String name) {
                try {
                    File f = new File("src/test/resources/" + name);
                    if (f.exists()) {
                        return f.toURI().toURL();
                    }
                } catch (MalformedURLException e) {
                    fail();
                }
                return null;
            }
        };
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME, "test");
        props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH, "/");

        this.registrations.add(this.m_context.registerService(ServletContextHelper.class, context, props));
        counter = this.waitForRuntime(counter);

        final Dictionary<String, Object> resourcesProps = new Hashtable<>();
        resourcesProps.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PATTERN, "/");
        resourcesProps.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PREFIX, "/resource");
        resourcesProps.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT,
                "(" + HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME + "=test)");
        final ServiceRegistration<Object> reg = this.m_context.registerService(Object.class, new Object(),
                resourcesProps);

        counter = this.waitForRuntime(counter);

        URL testHtmlURL = createURL("/test.html");
        URL testURL = createURL("/test");

        assertResponseCode(SC_OK, testHtmlURL);
        assertResponseCode(SC_OK, testURL);

        reg.unregister();
        counter = this.waitForRuntime(counter);

        assertResponseCode(SC_NOT_FOUND, testHtmlURL);
        assertResponseCode(SC_NOT_FOUND, testURL);
    }
}

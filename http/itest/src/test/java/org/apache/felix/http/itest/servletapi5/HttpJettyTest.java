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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.ConnectException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.Bundle;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class HttpJettyTest extends Servlet5BaseIntegrationTest {

    /**
     * Tests the starting of Jetty.
     */
    @Test
    public void test00_StartJettyOk() throws Exception {
        assertTrue(getHttpJettyBundle().getState() == Bundle.ACTIVE);

        assertResponseCode(SC_NOT_FOUND, createURL("/"));
    }

    /**
     * Tests the starting of Jetty.
     */
    @Test
    public void test00_StopJettyOk() throws Exception {
        final Bundle bundle = getHttpJettyBundle();
        assertTrue(bundle.getState() == Bundle.ACTIVE);

        this.setupLatches(1);
        TestServlet servlet = new TestServlet();
        registerServlet("/test", servlet);
        this.waitForInit();

        assertResponseCode(SC_OK, createURL("/test"));

        bundle.stop();

        this.waitForDestroy();

        try {
            createURL("/test").openStream();
            fail("Could connect to stopped Jetty instance?!");
        } catch (ConnectException e) {
            // Ok; expected...
        }

        bundle.start();

        Thread.sleep(500); // Allow Jetty to start (still done asynchronously)...

        assertResponseCode(SC_OK, createURL("/test"));
    }
}

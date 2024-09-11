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
package org.apache.felix.scr.integration;

import static org.junit.Assert.fail;

import javax.inject.Inject;

import org.apache.felix.scr.integration.components.felix6726.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;

/**
 * This test validates the FELIX-6726 issue.
 */
@RunWith(PaxExam.class)
public class Felix6726Test extends ComponentTestBase
{
    static
    {
        // uncomment to enable debugging of this test class
        //        paxRunnerVmOption = DEBUG_VM_OPTION;
        descriptorFile = "/integration_test_FELIX_6726.xml";
        COMPONENT_PACKAGE = COMPONENT_PACKAGE + ".felix6726";
        restrictedLogging = false;
        //comment to get debug logging if the test fails.
        DS_LOGLEVEL = "debug";
        //paxRunnerVmOption = DEBUG_VM_OPTION;
    }

    @Inject
    protected BundleContext bundleContext;

    protected static void delay(int secs)
    {
        try
        {
            Thread.sleep(secs * 1000);
        }
        catch (InterruptedException ie)
        {
        }
    }

    /**
     * This Test actually never fails, but it will provoke the {@link IllegalStateException} to be logged by SCR
     */
    @Test
    public void test_IllegalStateExceptionLogging()
    {
        final ComponentConfigurationDTO bImplDTO = findComponentConfigurationByName("felix.6726.B", 4);
        final ComponentConfigurationDTO consumerDTO = findComponentConfigurationByName( "felix.6726.Consumer", 4);

        Consumer consumer = getServiceFromConfiguration(consumerDTO, Consumer.class);
        try {
        	ungetServiceFromConfiguration(consumerDTO, Consumer.class);
        } catch (IllegalStateException e) {
			fail();
		}
      
    }
}

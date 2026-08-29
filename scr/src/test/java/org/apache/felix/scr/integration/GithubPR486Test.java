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

import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.OptionUtils;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;

@RunWith(PaxExam.class)
public class GithubPR486Test extends ComponentTestBase
{

    // test min timeout checking
    private static final long DS_SERVICE_CHANGECOUNT_TIMEOUT = 0;

    static
    {
        descriptorFile = "/integration_test_simple_components.xml";
        // uncomment to enable debugging of this test class
        //paxRunnerVmOption = DEBUG_VM_OPTION;
    }

    @Configuration
    public static Option[] configuration()
    {
        return OptionUtils.combine(ComponentTestBase.configuration(),
                systemProperty( "ds.service.changecount.timeout" ).value( Long.toString(DS_SERVICE_CHANGECOUNT_TIMEOUT) ));
    }


    private ServiceReference<ServiceComponentRuntime> scrRef;
    @Before
    public void addServiceListener() throws InvalidSyntaxException
    {
        scrRef = bundleContext.getServiceReference(ServiceComponentRuntime.class);
    }

    @Test
    public void verify_changecount_updates_with_min_timeout() throws InterruptedException, BundleException
    {
        // Wait a second the changecount timeout`to account for the asynchronous service.changecount property update
        Thread.sleep(1000);

        // Check that the service.changecount update was recorded
        assertEquals(13L, scrRef.getProperty(Constants.SERVICE_CHANGECOUNT));

        // Trigger a change by stopping the bundle with components
        bundle.stop();

        // Wait a second the changecount timeout`to account for the asynchronous service.changecount property update
        Thread.sleep(1000);

        // Check that another service.changecount update was recorded
        assertEquals(26L, scrRef.getProperty(Constants.SERVICE_CHANGECOUNT));
    }
}

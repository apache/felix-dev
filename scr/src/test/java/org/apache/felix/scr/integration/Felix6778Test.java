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

import org.junit.After;
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
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.service.component.runtime.ServiceComponentRuntime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

@RunWith(PaxExam.class)
public class Felix6778Test extends ComponentTestBase implements ServiceListener
{

    private static final long DS_SERVICE_CHANGECOUNT_TIMEOUT = 1000;

    static
    {
        descriptorFile = "/integration_test_simple_components.xml";
    }

    class RecordedScrChangeCount
    {
        private final Thread thread;
        private final long changecount;

        RecordedScrChangeCount(ServiceEvent event)
        {
            this.thread = Thread.currentThread();
            this.changecount = (long) event.getServiceReference().getProperty(Constants.SERVICE_CHANGECOUNT);
        }
    }

    @Configuration
    public static Option[] configuration()
    {
        return OptionUtils.combine(ComponentTestBase.configuration(),
                systemProperty( "ds.service.changecount.timeout" ).value( Long.toString(DS_SERVICE_CHANGECOUNT_TIMEOUT) ));
    }

    private List<RecordedScrChangeCount> recordedEvents = new CopyOnWriteArrayList<>();

    @Before
    public void addServiceListener() throws InvalidSyntaxException
    {
        bundleContext.addServiceListener(
                this,
                "("+Constants.OBJECTCLASS + "=" + ServiceComponentRuntime.class.getName() + ")"
        );
    }

    @After
    public void removeServiceListener()
    {
        bundleContext.removeServiceListener(this);
    }

    @Test
    public void verify_changecount_updates() throws InterruptedException, BundleException
    {
        // Wait for 2x the changecount timeout`to account for the asynchronous service.changecount property update
        Thread.sleep(DS_SERVICE_CHANGECOUNT_TIMEOUT * 2);

        // Check that the service.changecount update was recorded
        assertEquals(1, recordedEvents.size());
        assertEquals(13L, recordedEvents.get(0).changecount);
        assertEquals("SCR Component Actor", recordedEvents.get(0).thread.getName());

        // Trigger a change by stopping the bundle with components
        bundle.stop();

        // Wait for 2x the changecount timeout`to account for the asynchronous service.changecount property update
        Thread.sleep(DS_SERVICE_CHANGECOUNT_TIMEOUT * 2);

        // Check that another service.changecount update was recorded
        assertEquals(2, recordedEvents.size());
        assertEquals(26L, recordedEvents.get(1).changecount);
        assertEquals("SCR Component Actor", recordedEvents.get(1).thread.getName());

        // Check if both events originate from the same thread
        assertSame(recordedEvents.get(0).thread, recordedEvents.get(1).thread);
    }

    @Override
    public void serviceChanged(ServiceEvent event)
    {
        if (event.getType() == ServiceEvent.MODIFIED)
        {
            recordedEvents.add(new RecordedScrChangeCount(event));
        }
    }


}

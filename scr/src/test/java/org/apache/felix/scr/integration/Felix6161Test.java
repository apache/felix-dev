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

import junit.framework.TestCase;
import org.apache.felix.scr.integration.components.SimpleService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RunWith(PaxExam.class)
public class Felix6161Test extends ComponentTestBase
{
    static
    {
        descriptorFile = "/integration_test_FELIX_6161.xml";
//        paxRunnerVmOption = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:5005";
    }

    @Test
    public void test_service_listenerhook() throws Exception
    {
        // Register a ListenerHook that records all ListenerInfo on the added/removed callbacks
        final List<ListenerHook.ListenerInfo> listenerInfoAdded = new CopyOnWriteArrayList<>();
        final List<ListenerHook.ListenerInfo> listenerInfoRemoved = new CopyOnWriteArrayList<>();
        final ListenerHook listenerHook = new ListenerHook()
        {
            @Override
            public void added(Collection<ListenerInfo> listeners)
            {
                listenerInfoAdded.addAll(listeners);
            }

            @Override
            public void removed(Collection<ListenerInfo> listeners)
            {
                listenerInfoRemoved.addAll(listeners);
            }
        };
        bundleContext.registerService(ListenerHook.class, listenerHook, null);

        final String firstComponent = "felix.6161";
        final String secondComponent = "felix.6161.2nd";

        // Enabling the component should activate the ServiceListener
        getDisabledConfigurationAndEnable(firstComponent, ComponentConfigurationDTO.ACTIVE);

        // Validate if the ListenerHook was triggered on the added flow
        TestCase.assertTrue(isMatchingListenerInfoPresent(listenerInfoAdded));

        // Clear the list of added callbacks, as we want to be able to verify if no additional ServiceListener is opened in the next step
        listenerInfoAdded.clear();

        // Enable second component, that requires the same service (and therefor re-uses the existing ServiceListener)
        getDisabledConfigurationAndEnable(secondComponent, ComponentConfigurationDTO.ACTIVE);

        // Verify that indeed there was no additional ServiceListener opened
        TestCase.assertFalse(isMatchingListenerInfoPresent(listenerInfoAdded));

        // Disable the first component, whilst the second remains active
        disableAndCheck(firstComponent);

        // The ListenerHook should not have seen the ServiceListener being closed (as it is still in use for the second component)
        TestCase.assertFalse(isMatchingListenerInfoPresent(listenerInfoRemoved));

        // Disable the second component as well
        disableAndCheck(secondComponent);

        // Now the ListenerHook should have received the removed callback
        TestCase.assertTrue(isMatchingListenerInfoPresent(listenerInfoRemoved));
    }

    private boolean isMatchingListenerInfoPresent(List<ListenerHook.ListenerInfo> listenerInfoRemoved)
    {
        boolean matchingListenerInfoPresent = false;
        for (ListenerHook.ListenerInfo listenerInfo : listenerInfoRemoved)
        {
            if (listenerInfo.getFilter() != null && listenerInfo.getFilter().contains("objectClass=" + SimpleService.class.getName()))
            {
                TestCase.assertTrue(listenerInfo.getFilter().contains("(value=foo)"));
                matchingListenerInfoPresent = true;
            }
        }
        return matchingListenerInfoPresent;
    }
}

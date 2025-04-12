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
package org.apache.felix.framework;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.service.EventHook;

class EventDispatcherTest
{
    @Test
    void fireServiceEvent()
    {
        final Bundle b1 = getMockBundle();
        final Bundle b2 = getMockBundle();
        final Bundle b3 = getMockBundle();
        final Bundle b4 = getMockBundle();

        final Set<EventHook> calledHooks = new HashSet<>();
        final EventHook eh1 = new EventHook()
        {
            @Override
			public void event(ServiceEvent event, Collection contexts)
            {
                calledHooks.add(this);
            }
        };
        final EventHook eh2 = new EventHook()
        {
            @Override
			public void event(ServiceEvent event, Collection contexts)
            {
                calledHooks.add(this);
                for (Iterator it = contexts.iterator(); it.hasNext();)
                {
                    BundleContext bc = (BundleContext) it.next();
                    if (bc.getBundle() == b1)
                    {
                        it.remove();
                    }
                    if (bc.getBundle() == b2)
                    {
                        it.remove();
                    }
                }
            }
        };

        Logger logger = new Logger();
        ServiceRegistry registry = new ServiceRegistry(logger, null);
        registry.registerService(b4, new String [] {EventHook.class.getName()}, eh1, new Hashtable());
        registry.registerService(b4, new String [] {EventHook.class.getName()}, eh2, new Hashtable());

        // -- Set up event dispatcher
        EventDispatcher ed = new EventDispatcher(logger, registry);

        // -- Register some listeners
        final List<Object> fired = Collections.synchronizedList(new ArrayList<>());
        ServiceListener sl1 = new ServiceListener()
        {
            @Override
			public void serviceChanged(ServiceEvent arg0)
            {
                fired.add(this);
            }
        };
        ed.addListener(b1.getBundleContext(), ServiceListener.class, sl1, null);

        ServiceListener sl2 = new ServiceListener()
        {
            @Override
			public void serviceChanged(ServiceEvent arg0)
            {
                fired.add(this);
            }
        };
        ed.addListener(b2.getBundleContext(), ServiceListener.class, sl2, null);

        ServiceListener sl3 = new ServiceListener()
        {
            @Override
			public void serviceChanged(ServiceEvent arg0)
            {
                fired.add(this);
            }
        };
        ed.addListener(b3.getBundleContext(), ServiceListener.class, sl3, null);

        // --- make the invocation
        ServiceReference<?> sr = Mockito.mock(ServiceReference.class);
        Mockito.when(sr.getProperty(Constants.OBJECTCLASS)).thenReturn(new String[]
            {
                "java.lang.String"
            });
        
        Mockito.when(sr.isAssignableTo(b1, String.class.getName())).thenReturn(true);
        Mockito.when(sr.isAssignableTo(b2, String.class.getName())).thenReturn(true);
        Mockito.when(sr.isAssignableTo(b3, String.class.getName())).thenReturn(true);

        ServiceEvent event = new ServiceEvent(ServiceEvent.REGISTERED, sr);

        assertThat(fired.size()).as("Precondition failed").isEqualTo(0);

        Felix framework = new Felix(new HashMap<>());

        ed.fireServiceEvent(event, null, framework);
        assertThat(fired).hasSize(1);
        assertThat(fired.iterator().next()).isSameAs(sl3);

        assertThat(calledHooks).hasSize(2);
        assertThat(calledHooks).contains(eh1);
        assertThat(calledHooks).contains(eh2);
    }

    private Bundle getMockBundle()
    {
        BundleContext bc = Mockito.mock(BundleContext.class);
        Bundle b = Mockito.mock(Bundle.class);
        Mockito.when(b.getBundleContext()).thenReturn(bc);
        Mockito.when(b.getState()).thenReturn(Bundle.ACTIVE);
        Mockito.when(bc.getBundle()).thenReturn(b);

        return b;
    }
}

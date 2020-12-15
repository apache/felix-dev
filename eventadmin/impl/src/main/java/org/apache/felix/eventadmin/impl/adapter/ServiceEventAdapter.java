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
package org.apache.felix.eventadmin.impl.adapter;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;

/**
 * This class registers itself as a listener for service events and posts them via
 * the EventAdmin as specified in 113.6.5 OSGi R4 compendium.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class ServiceEventAdapter extends AbstractAdapter implements ServiceListener
{
    /**
     * The constructor of the adapter. This will register the adapter with the
     * given context as a {@code ServiceListener} and subsequently, will
     * post received events via the given EventAdmin.
     *
     * @param context The bundle context with which to register as a listener.
     * @param admin The {@code EventAdmin} to use for posting events.
     */
    public ServiceEventAdapter(final BundleContext context, final EventAdmin admin)
    {
        super(admin);

        context.addServiceListener(this);
    }

    @Override
    public void destroy(BundleContext context) {
        context.removeServiceListener(this);
    }

    /**
     * Once a Service event is received this method assembles and posts an event
     * via the {@code EventAdmin} as specified in 113.6.5 OSGi R4 compendium.
     *
     * @param event The event to adapt.
     */
    @Override
    public void serviceChanged(final ServiceEvent event)
    {
        final Dictionary<String, Object> properties = new Hashtable<String, Object>();

        properties.put(EventConstants.EVENT, event);

        properties.put(EventConstants.SERVICE, event.getServiceReference());

        properties.put(EventConstants.SERVICE_ID,
                event.getServiceReference().getProperty(EventConstants.SERVICE_ID));

        properties.put(EventConstants.SERVICE_OBJECTCLASS,
                event.getServiceReference().getProperty(Constants.OBJECTCLASS));

        final Object pid = event.getServiceReference().getProperty(
                EventConstants.SERVICE_PID);
        if (null != pid)
        {
            properties.put(EventConstants.SERVICE_PID, pid);
        }

        final StringBuilder topic = new StringBuilder(ServiceEvent.class
            .getName().replace('.', '/')).append('/');

        switch (event.getType())
        {
            case ServiceEvent.REGISTERED:
                topic.append("REGISTERED");
                break;
            case ServiceEvent.MODIFIED:
                topic.append("MODIFIED");
                break;
            case ServiceEvent.UNREGISTERING:
                topic.append("UNREGISTERING");
                break;
            default:
                return; // IGNORE
        }

        try {
            getEventAdmin().postEvent(new Event(topic.toString(), properties));
        } catch(IllegalStateException e) {
            // This is o.k. - indicates that we are stopped.
        }
    }
}

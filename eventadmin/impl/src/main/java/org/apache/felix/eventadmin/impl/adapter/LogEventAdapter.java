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

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;

/**
 * This class registers itself as a listener for {@code LogReaderService} services
 * with the framework and subsequently, a {@code LogListener} callback with any
 * currently available {@code LogReaderService}. Any received log event is then
 * posted via the EventAdmin as specified in 113.6.6 OSGi R4 compendium.
 * Note that this class does not create a hard dependency on the org.osgi.service.log
 * packages. The adaption only takes place if it is present or once it becomes
 * available hence, combined with a DynamicImport-Package no hard dependency is
 * needed.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class LogEventAdapter extends AbstractAdapter implements ServiceListener
{
    // The internal lock for this object used instead synchronized(this)
    private final Object m_lock = new Object();

    private BundleContext m_context;

    // A singleton instance of the used log listener that is the adapter
    private Object m_logListener;

    /**
     * The constructor of the adapter. This will register the adapter with the
     * given context as a listener for {@code LogReaderService} services and
     * subsequently, a {@code LogListener} callback with any currently available
     * {@code LogReaderService}. Any received log event is then posted via the given
     * EventAdmin.
     *
     * @param context The bundle context with which to register as a listener.
     * @param admin The {@code EventAdmin} to use for posting events.
     */
    public LogEventAdapter(final BundleContext context, final EventAdmin admin)
    {
        super(admin);
        m_context = context;

        try
        {
            m_context.addServiceListener(this, "(" + Constants.OBJECTCLASS
                + "=org.osgi.service.log.LogReaderService)");

            final ServiceReference<?>[] refs;

            refs = m_context.getServiceReferences(
                "org.osgi.service.log.LogReaderService", null);

            if (null != refs)
            {
                for (int i = 0; i < refs.length; i++)
                {
                    final org.osgi.service.log.LogReaderService logReader =
                        (org.osgi.service.log.LogReaderService) m_context
                        .getService(refs[i]);

                    if (null != logReader)
                    {
                        logReader.addLogListener((org.osgi.service.log.LogListener)
                            getLogListener());
                    }
                }
            }
        } catch (InvalidSyntaxException e)
        {
            // This never happens
        }
    }

    @Override
    public void destroy(BundleContext context) {
        context.removeServiceListener(this);
    }

    /**
     * Once a {@code LogReaderService} register event is received this method
     * registers a {@code LogListener} with the received service that assembles
     * and posts any log event via the {@code EventAdmin} as specified in
     * 113.6.6 OSGi R4 compendium.
     *
     * @param event The event to adapt.
     */
    @Override
    public void serviceChanged(final ServiceEvent event)
    {
        if (ServiceEvent.REGISTERED == event.getType())
        {
            final org.osgi.service.log.LogReaderService logReader =
                (org.osgi.service.log.LogReaderService) m_context
                .getService(event.getServiceReference());

            if (null != logReader)
            {
                logReader.addLogListener((org.osgi.service.log.LogListener)
                    getLogListener());
            }
        }
    }

    /*
     * Constructs a LogListener that assembles and posts any log event via the
     * EventAdmin as specified in 113.6.6 OSGi R4 compendium. Note that great
     * care is taken to not create a hard dependency on the org.osgi.service.log
     * package.
     */
    private Object getLogListener()
    {
        synchronized (m_lock)
        {
            if (null != m_logListener)
            {
                return m_logListener;
            }

            m_logListener = new org.osgi.service.log.LogListener()
            {
                @Override
                public void logged(final org.osgi.service.log.LogEntry entry)
                {
                    // This is where the assembly as specified in 133.6.6 OSGi R4
                    // compendium is taking place (i.e., the log entry is adapted to
                    // an event and posted via the EventAdmin)

                    final Dictionary<String, Object> properties = new Hashtable<String, Object>();

                    final Bundle bundle = entry.getBundle();

                    if (null != bundle)
                    {
                        properties.put("bundle.id", bundle.getBundleId());

                        final String symbolicName = bundle.getSymbolicName();
                        if (null != symbolicName)
                        {
                            properties.put(EventConstants.BUNDLE_SYMBOLICNAME,
                                symbolicName);
                        }

                        properties.put("bundle", bundle);
                    }

                    properties.put("log.level", entry.getLevel());

		            properties.put(EventConstants.MESSAGE,
				        (entry.getMessage()) != null ? entry.getMessage() : "" );

                    properties.put(EventConstants.TIMESTAMP, entry.getTime());

                    properties.put("log.entry", entry);

                    final Throwable exception = entry.getException();

                    if (null != exception)
                    {
                        properties.put(EventConstants.EXCEPTION_CLASS,
                            exception.getClass().getName());

                        final String message = exception.getMessage();

                        if (null != message)
                        {
                            properties.put(EventConstants.EXCEPTION_MESSAGE,
                                message);
                        }

                        properties.put(EventConstants.EXCEPTION, exception);
                    }

                    final ServiceReference<?> service = entry.getServiceReference();

                    if (null != service)
                    {
                        properties.put(EventConstants.SERVICE, service);
                        properties.put(EventConstants.SERVICE_ID, service.getProperty(EventConstants.SERVICE_ID));
                        properties.put(
                                EventConstants.SERVICE_OBJECTCLASS,
                                service.getProperty(Constants.OBJECTCLASS));

                        final Object pid = service.getProperty(EventConstants.SERVICE_PID);
                        if (null != pid)
                        {
                            properties.put(EventConstants.SERVICE_PID, pid);
                        }

                    }

                    final StringBuilder topic = new StringBuilder(
                        org.osgi.service.log.LogEntry.class.getName().replace(
                            '.', '/')).append('/');

                    switch (entry.getLevel())
                    {
                        case org.osgi.service.log.LogService.LOG_ERROR:
                            topic.append("LOG_ERROR");
                            break;
                        case org.osgi.service.log.LogService.LOG_WARNING:
                            topic.append("LOG_WARNING");
                            break;
                        case org.osgi.service.log.LogService.LOG_INFO:
                            topic.append("LOG_INFO");
                            break;
                        case org.osgi.service.log.LogService.LOG_DEBUG:
                            topic.append("LOG_DEBUG");
                            break;
                        default:
                            topic.append("LOG_OTHER");
                            break;
                    }

                    try {
                        getEventAdmin().postEvent(new Event(topic.toString(), properties));
                    } catch(IllegalStateException e) {
                        // This is o.k. - indicates that we are stopped.
                    }
                }
            };

            return m_logListener;
        }
    }
}

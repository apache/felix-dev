/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.webconsole.plugins.event.internal;


import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.EventAdmin;

/**
 * This class handles all optional stuff.
 * It listens for specific services and activates/deactivates
 * features.
 */
public class OptionalFeaturesHandler
    implements ServiceListener
{
    private static final String EVENT_ADMIN_CLASS_NAME = "org.osgi.service.event.EventAdmin"; //$NON-NLS-1$
    private static final String CONFIGURATION_ADMIN_CLASS_NAME = "org.osgi.service.cm.ConfigurationAdmin"; //$NON-NLS-1$
    private static final String EVENT_HANDLER_CLASS_NAME = "org.osgi.service.event.EventHandler"; //$NON-NLS-1$

    private static final String FILTER = "(|(" + Constants.OBJECTCLASS + "=" + EVENT_ADMIN_CLASS_NAME + ")"
                                        +"(" + Constants.OBJECTCLASS + "=" + CONFIGURATION_ADMIN_CLASS_NAME + "))";

    /** Event admin service id */
    //private Long eventAdminServiceId;
    private ServiceReference<?> eventAdminServiceRef;

    /** Registration for the event handler. */
    private ServiceRegistration<?> eventHandlerRegistration;

    /** Configuration admin service id */
    private Long configAdminServiceId;

    /** Registration for the configuration listener. */
    private ServiceRegistration<?> configListenerRegistration;

    /** Bundle context. */
    private final BundleContext bundleContext;

    /** The plugin */
    private final PluginServlet plugin;

    public OptionalFeaturesHandler(final PluginServlet plugin, final BundleContext context)
    {
        this.plugin = plugin;
        this.bundleContext = context;
        // check if event admin is already available
        final ServiceReference<?> ref = this.bundleContext.getServiceReference(EVENT_ADMIN_CLASS_NAME);
        if ( ref != null )
        {
            bindEventAdmin(ref);
        }

        // check if config admin is already available
        this.configAdminServiceId = null;
        final ServiceReference<?> cfaRef = this.bundleContext.getServiceReference(CONFIGURATION_ADMIN_CLASS_NAME);
        if ( cfaRef != null )
        {
            final Long id = (Long)cfaRef.getProperty(Constants.SERVICE_ID);
            bindConfigAdmin(id);
        }

        // listen for event and config admin from now on
        try {
            context.addServiceListener(this, FILTER);
        } catch (InvalidSyntaxException ise) {
            // this should never happen as this is a constant, so we ignore it
        }
    }

    /**
     * @see javax.servlet.GenericServlet#destroy()
     */
    public void destroy()
    {
        this.bundleContext.removeServiceListener(this);
        this.unbindEventAdmin(this.eventAdminServiceRef);
        this.unbindConfigAdmin(this.configAdminServiceId);
    }

    /**
     * @see org.osgi.framework.ServiceListener#serviceChanged(org.osgi.framework.ServiceEvent)
     */
    @Override
    public void serviceChanged(final ServiceEvent event)
    {
        final ServiceReference<?> ref = event.getServiceReference();
        final String[] objectClasses =  (String[])ref.getProperty(Constants.OBJECTCLASS);
        if ( objectClasses != null)
        {
            for(int i=0; i<objectClasses.length; i++)
            {
                if ( objectClasses[i].equals(EVENT_ADMIN_CLASS_NAME) )
                {
                    if ( event.getType() == ServiceEvent.REGISTERED )
                    {
                        new Thread()
                        {
                            @Override
                            public void run()
                            {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException ignore) {}
                                bindEventAdmin(ref);
                            }
                        }.start();
                    }
                    else if ( event.getType() == ServiceEvent.UNREGISTERING )
                    {
                        new Thread()
                        {
                            @Override
                            public void run()
                            {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException ignore) {}
                                unbindEventAdmin(ref);
                            }
                        }.start();
                    }
                }
                else if ( objectClasses[i].equals(CONFIGURATION_ADMIN_CLASS_NAME) )
                {
                    final Long id = (Long)event.getServiceReference().getProperty(Constants.SERVICE_ID);
                    if ( event.getType() == ServiceEvent.REGISTERED )
                    {
                        new Thread()
                        {
                            @Override
                            public void run()
                            {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException ignore) {}
                                bindConfigAdmin(id);
                            }
                        }.start();
                    }
                    else if ( event.getType() == ServiceEvent.UNREGISTERING )
                    {
                        new Thread()
                        {
                            @Override
                            public void run()
                            {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException ignore) {}
                                unbindConfigAdmin(id);
                            }
                        }.start();
                    }
                }
            }
        }
    }

    synchronized void bindEventAdmin(ServiceReference<?> ref)
    {
        if ( this.eventAdminServiceRef != null)
        {
            this.unbindEventAdmin(this.eventAdminServiceRef);
        }
        this.eventAdminServiceRef = ref;
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put( Constants.SERVICE_DESCRIPTION, "Event handler for the Apache Felix Web Console" );
        props.put( Constants.SERVICE_VENDOR, "The Apache Software Foundation" );
        props.put( "event.topics", "*"); //$NON-NLS-1$ //$NON-NLS-2$
        this.plugin.setEventAdmin((EventAdmin) bundleContext.getService(ref));

        this.eventHandlerRegistration = this.bundleContext.registerService(EVENT_HANDLER_CLASS_NAME,
                new EventHandler(this.plugin.getCollector()), props);
    }

    synchronized void unbindEventAdmin(ServiceReference<?> ref)
    {
        if ( this.eventAdminServiceRef != null && this.eventAdminServiceRef.equals(ref) )
        {
            bundleContext.ungetService(ref);
            this.eventAdminServiceRef = null;
            this.plugin.setEventAdmin(null);
            if ( this.eventHandlerRegistration != null )
            {
                this.eventHandlerRegistration.unregister();
                this.eventHandlerRegistration = null;
            }
        }
    }

    synchronized void bindConfigAdmin(final Long id)
    {
        if ( this.configAdminServiceId != null )
        {
            this.unbindConfigAdmin(this.configAdminServiceId);
        }
        this.plugin.setConfigAdminAvailable(true);
        this.configAdminServiceId = id;
        this.configListenerRegistration = ConfigurationListener.create(this.bundleContext, this.plugin);
    }

    synchronized void unbindConfigAdmin(final Long id)
    {
        if ( this.configAdminServiceId != null && this.configAdminServiceId.equals(id) )
        {
            this.configAdminServiceId = null;
            this.plugin.setConfigAdminAvailable(false);
            if ( this.configListenerRegistration != null )
            {
                this.configListenerRegistration.unregister();
                this.configListenerRegistration = null;
            }
        }
    }
}

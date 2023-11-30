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
package org.apache.felix.http.base.internal.service;

import static java.util.Collections.list;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.felix.http.base.internal.registry.HandlerRegistry;
import org.apache.felix.http.base.internal.runtime.dto.RequestInfoDTOBuilder;
import org.apache.felix.http.base.internal.runtime.dto.RuntimeDTOBuilder;
import org.apache.felix.http.base.internal.whiteboard.WhiteboardManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.http.runtime.HttpServiceRuntime;
import org.osgi.service.http.runtime.dto.RequestInfoDTO;
import org.osgi.service.http.runtime.dto.RuntimeDTO;

public final class HttpServiceRuntimeImpl implements HttpServiceRuntime
{
    /**
     * Service property for change count. This constant is defined here to avoid
     * a dependency on R7 of the framework.
     * The value of the property is of type {@code Long}.
     */
    private static final String PROP_CHANGECOUNT = "service.changecount";

    private static final String PROP_CHANGECOUNTDELAY = "org.apache.felix.http.whiteboard.changecount.delay";

    private volatile Hashtable<String, Object> attributes = new Hashtable<>();

    private final HandlerRegistry registry;
    private final WhiteboardManager contextManager;

    private volatile ServiceRegistration<HttpServiceRuntime> serviceReg;

    private volatile ServiceReferenceDTO serviceRefDTO;

    private final AtomicLong changeCount = new AtomicLong();

    private volatile Timer changeCountTimer;

    private final Object changeCountTimerLock = new Object();

    private final long updateChangeCountDelay;

    public HttpServiceRuntimeImpl(HandlerRegistry registry,
            WhiteboardManager contextManager,
            BundleContext bundleContext)
    {
        this.registry = registry;
        this.contextManager = contextManager;
        final Object val = bundleContext.getProperty(PROP_CHANGECOUNTDELAY);
        long value = 2000L;
        if ( val != null )
        {
        	try
        	{
        		value = Long.parseLong(val.toString());
        	}
        	catch ( final NumberFormatException nfe)
        	{
        		// ignore
        	}
        	if ( value < 1 )
        	{
        		value = 0L;
        	}
        }
    	updateChangeCountDelay = value;
    }

    @Override
    public RuntimeDTO getRuntimeDTO()
    {
    	if ( this.serviceRefDTO == null )
    	{
    		// it might happen that this code is executed in several threads
    		// but that's very unlikely and even if, fetching the service
    		// reference several times is not a big deal
    		final ServiceRegistration<HttpServiceRuntime> reg = this.serviceReg;
    		if ( reg != null )
    		{
                final long id = (long) reg.getReference().getProperty(Constants.SERVICE_ID);
                final ServiceReferenceDTO[] dtos = reg.getReference().getBundle().adapt(ServiceReferenceDTO[].class);
                for(final ServiceReferenceDTO dto : dtos)
                {
                	if ( dto.id == id)
                	{
                		this.serviceRefDTO = dto;
                		break;
                	}
                }

    		}
    	}
        if ( this.serviceRefDTO != null )
        {
            final RuntimeDTOBuilder runtimeDTOBuilder = new RuntimeDTOBuilder(contextManager.getRuntimeInfo(),
                    this.serviceRefDTO);
            return runtimeDTOBuilder.build();
        }
        throw new IllegalStateException("Service is already unregistered");
    }

    @Override
    public RequestInfoDTO calculateRequestInfoDTO(final String path)
    {
        return new RequestInfoDTOBuilder(registry, path).build();
    }

    public synchronized void setAttribute(String name, Object value)
    {
        Hashtable<String, Object> newAttributes = new Hashtable<>(attributes);
        newAttributes.put(name, value);
        attributes = newAttributes;
    }

    public synchronized void setAllAttributes(Dictionary<String, Object> newAttributes)
    {
        Hashtable<String, Object> replacement = new Hashtable<>();
        for (String key : list(newAttributes.keys()))
        {
            replacement.put(key, newAttributes.get(key));
        }
        replacement.put(PROP_CHANGECOUNT, this.changeCount.get());
        attributes = replacement;
    }

    public void register(final BundleContext bundleContext)
    {
        this.serviceReg = bundleContext.registerService(HttpServiceRuntime.class,
                this,
                attributes);
    }

    public void unregister()
    {
    	if ( this.serviceReg != null )
    	{
        	try
        	{
        	    this.serviceReg.unregister();
        	}
        	catch ( final IllegalStateException ise)
        	{
        		// we just ignore it
        	}
        	this.serviceReg = null;
    	}
        synchronized ( changeCountTimerLock )
        {
            if ( this.changeCountTimer != null )
            {
                this.changeCountTimer.cancel();
                this.changeCountTimer = null;
            }
        }
        this.serviceRefDTO = null;
    }

    public ServiceReference<HttpServiceRuntime> getServiceReference()
    {
    	final ServiceRegistration<HttpServiceRuntime> reg = this.serviceReg;
    	if ( reg != null )
    	{
    		return reg.getReference();
    	}
    	return null;
    }

    public void updateChangeCount()
    {
        final ServiceRegistration<HttpServiceRuntime> reg = this.serviceReg;
        if ( reg != null )
        {
            final long count = this.changeCount.incrementAndGet();

            this.setAttribute(PROP_CHANGECOUNT, this.changeCount.get());
            if ( this.updateChangeCountDelay <= 0L )
            {
                try
                {
                    reg.setProperties(attributes);
                }
                catch ( final IllegalStateException ise)
                {
                    // we ignore this as this might happen on shutdown
                }
            }
            else
            {
                final Timer timer;
                synchronized ( this.changeCountTimerLock ) {
                    if ( this.changeCountTimer == null ) {
                        this.changeCountTimer = new Timer("Apache Felix Http Runtime Timer", true);
                    }
                    timer = this.changeCountTimer;
                }
                try
                {
                    timer.schedule(new TimerTask()
                    {

                        @Override
                        public void run()
                        {
                            if ( changeCount.get() == count )
                            {
                                try
                                {
                                    reg.setProperties(attributes);
                                }
                                catch ( final IllegalStateException ise)
                                {
                                    // we ignore this as this might happen on shutdown
                                }
                                synchronized ( changeCountTimerLock )
                                {
                                    if ( changeCount.get() == count )
                                    {
                                        if ( changeCountTimer != null ) 
                                        {
                                            changeCountTimer.cancel();
                                            changeCountTimer = null;    
                                        }
                                    }
                                }

                            }
                        }
                    }, this.updateChangeCountDelay);
                }
                catch (final Exception e) {
                    // we ignore this
                }
            }
        }
    }

}

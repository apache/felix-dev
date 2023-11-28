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
package org.apache.felix.eventadmin.impl.handler;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.felix.eventadmin.impl.security.PermissionsUtil;
import org.apache.felix.eventadmin.impl.util.LogWrapper;
import org.osgi.framework.Bundle;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

/**
 * This is a proxy for event handlers. It gets the real event handler
 * on demand and prepares some information for faster processing.
 *
 * It checks the timeout handling for the implementation as well as
 * putting the handler on the deny list.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class EventHandlerProxy {

    /** The service reference for the event handler. */
    private final ServiceReference<EventHandler> reference;

    /** The handler context. */
    private final EventHandlerTracker.HandlerContext handlerContext;

    /** The event topics. */
    private volatile String[] topics;

    /** Optional filter. */
    private volatile Filter filter;

    /** Lazy fetched event handler. */
    private volatile EventHandler handler;

    /** Is this handler denied? */
    private final AtomicBoolean denied = new AtomicBoolean();

    /** Use timeout. */
    private boolean useTimeout;

    /** Deliver async ordered. */
    private boolean asyncOrderedDelivery;

    /**
     * Create an EventHandlerProxy.
     *
     * @param context The handler context
     * @param reference Reference to the EventHandler
     */
    public EventHandlerProxy(final EventHandlerTracker.HandlerContext context,
                    final ServiceReference<EventHandler> reference)
    {
        this.handlerContext = context;
        this.reference = reference;
    }

    /**
     * Update the state with current properties from the service
     * @return <code>true</code> if the handler configuration is valid.
     */
    public boolean update()
    {
        this.denied.set(false);
        boolean valid = true;
        // First check, topic
        final Object topicObj = reference.getProperty(EventConstants.EVENT_TOPIC);
        if (topicObj instanceof String)
        {
            if ( topicObj.toString().equals("*") )
            {
                this.topics = null;
            }
            else
            {
                this.topics = new String[] {topicObj.toString()};
            }
        }
        else if (topicObj instanceof String[])
        {
            // check if one value matches '*'
            final String[] values = (String[])topicObj;
            boolean matchAll = false;
            for(int i=0;i<values.length;i++)
            {
                if ( "*".equals(values[i]) )
                {
                    matchAll = true;
                }
            }
            if ( matchAll )
            {
                this.topics = null;
            }
            else
            {
                this.topics = values;
            }
        }
        else if (topicObj instanceof Collection)
        {
            @SuppressWarnings("unchecked")
            final Collection<Object> col = (Collection<Object>)topicObj;
            final String[] values = new String[col.size()];
            int index = 0;
            // check if one value matches '*'
            final Iterator<Object> i = col.iterator();
            boolean matchAll = false;
            while ( i.hasNext() )
            {
                final String v = i.next().toString();
                values[index] = v;
                index++;
                if ( "*".equals(v) )
                {
                    matchAll = true;
                }
            }
            if ( matchAll )
            {
                this.topics = null;
            }
            else
            {
                this.topics = values;
            }
        }
        else if ( topicObj == null && !this.handlerContext.requireTopic )
        {
            this.topics = null;
        }
        else
        {
            final String reason;
            if ( topicObj == null )
            {
                reason = "Missing";
            }
            else
            {
                reason = "Neither of type String nor String[] : " + topicObj.getClass().getName();
            }
            LogWrapper.getLogger().log(
                            this.reference,
                            LogWrapper.LOG_WARNING,
                            "Invalid EVENT_TOPICS : " + reason + " - Ignoring ServiceReference ["
                                            + this.reference + " | Bundle("
                                            + this.reference.getBundle() + ")]");
            this.topics = null;
            valid = false;
        }
        // Second check filter (but only if topics is valid)
        Filter handlerFilter = null;
        if ( valid )
        {
            final Object filterObj = reference.getProperty(EventConstants.EVENT_FILTER);
            if (filterObj instanceof String)
            {
                try
                {
                    handlerFilter = this.handlerContext.bundleContext.createFilter(filterObj.toString());
                }
                catch (final InvalidSyntaxException e)
                {
                    valid = false;
                    LogWrapper.getLogger().log(
                                    this.reference,
                                    LogWrapper.LOG_WARNING,
                                    "Invalid EVENT_FILTER - Ignoring ServiceReference ["
                                                    + this.reference + " | Bundle("
                                                    + this.reference.getBundle() + ")]", e);
                }
            }
            else if ( filterObj != null )
            {
                valid = false;
                LogWrapper.getLogger().log(
                                this.reference,
                                LogWrapper.LOG_WARNING,
                                "Invalid EVENT_FILTER - Ignoring ServiceReference ["
                                                + this.reference + " | Bundle("
                                                + this.reference.getBundle() + ")]");
            }
        }
        this.filter = handlerFilter;

        // new in 1.3 - deliver
        this.asyncOrderedDelivery = true;
        Object delivery = reference.getProperty(EventConstants.EVENT_DELIVERY);
        if ( delivery instanceof Collection )
        {
            @SuppressWarnings("unchecked")
            final Collection<String> col = (Collection<String>)delivery;
            delivery = col.toArray(new String[col.size()]);
        }
        if ( delivery instanceof String )
        {
            this.asyncOrderedDelivery =  !(EventConstants.DELIVERY_ASYNC_UNORDERED.equals(delivery.toString()));
        }
        else if ( delivery instanceof String[] )
        {
            final String[] deliveryArray = (String[])delivery;
            boolean foundOrdered = false, foundUnordered = false;
            for(int i=0; i<deliveryArray.length; i++)
            {
                final String value = deliveryArray[i];
                if ( EventConstants.DELIVERY_ASYNC_UNORDERED.equals(value) )
                {
                    foundUnordered = true;
                }
                else if ( EventConstants.DELIVERY_ASYNC_ORDERED.equals(value) )
                {
                    foundOrdered = true;
                }
                else
                {
                    LogWrapper.getLogger().log(
                                    this.reference,
                                    LogWrapper.LOG_WARNING,
                                    "Invalid EVENT_DELIVERY - Ignoring invalid value for event delivery property " + value + " of ServiceReference ["
                                                    + this.reference + " | Bundle("
                                                    + this.reference.getBundle() + ")]");

                }
            }
            if ( !foundOrdered && foundUnordered )
            {
                this.asyncOrderedDelivery = false;
            }
        }
        else if ( delivery != null )
        {
            LogWrapper.getLogger().log(
                            this.reference,
                            LogWrapper.LOG_WARNING,
                            "Invalid EVENT_DELIVERY - Ignoring event delivery property " + delivery + " of ServiceReference ["
                                            + this.reference + " | Bundle("
                                            + this.reference.getBundle() + ")]");
        }
        // make sure to release the handler
        this.release();

        return valid;
    }

    /**
     * Get some info about the event handler
     * @return Handler info
     */
    public String getInfo() {
        return this.reference.toString() + " [Bundle " + this.reference.getBundle() + "]";
    }

    /**
     * Dispose the proxy and release the handler
     */
    public void dispose()
    {
        this.release();
    }

    /**
     * Get the event handler.
     * @return The event handler or {@code null}
     */
    private synchronized EventHandler obtain() {
        if (this.handler == null)
        {
            try
            {
                this.handler = this.handlerContext.bundleContext.getService(this.reference);
                if ( this.handler != null )
                {
                    this.checkTimeout(this.handler.getClass().getName());
                }
            }
            catch (final IllegalStateException ignore)
            {
                // event handler might be stopped - ignore
            }
        }
        return this.handler;
    }

    /**
     * Release the handler
     */
    private synchronized void release()
    {
        if ( this.handler != null )
        {
            try
            {
                this.handlerContext.bundleContext.ungetService(this.reference);
            }
            catch (final IllegalStateException ignore)
            {
                // event handler might be stopped - ignore
            }
            this.handler = null;
        }
    }

    /**
     * Get the topics of this handler.
     * If this handler matches all topics {@code null} is returned
     * @return The topics of this handler or {@code null}
     */
    public String[] getTopics()
    {
        return this.topics;
    }

    /**
     * Check if this handler is allowed to receive the event
     * - denied
     * - check filter
     * - check permission
     * @param event The event
     * @return {@code true} if the event can be delivered
     */
    public boolean canDeliver(final Event event)
    {
        if ( this.denied.get() )
        {
            return false;
        }
        final Bundle bundle = reference.getBundle();
        // is service unregistered?
        if (bundle == null)
        {
            return false;
        }

        // filter match
        final Filter eventFilter = this.filter;
        if ( eventFilter != null && !event.matches(eventFilter) )
        {
            return false;
        }

        // permission check
        final Object p = PermissionsUtil.createSubscribePermission(event.getTopic());
        if (p != null && !bundle.hasPermission(p) )
        {
            return false;
        }

        return true;
    }

    /**
     * Should a timeout be used for this handler?
     * @return {@code true} if a timeout should be used
     */
    public boolean useTimeout()
    {
        return this.useTimeout;
    }

    /**
     * Should async events be delivered in order?
     * @return {@code true} if async events should be delivered in order
     */
    public boolean isAsyncOrderedDelivery()
    {
        return this.asyncOrderedDelivery;
    }

    /**
     * Check the timeout configuration for this handler.
     * @param className Handler name
     */
    private void checkTimeout(final String className)
    {
        if ( this.handlerContext.ignoreTimeoutMatcher != null )
        {
            for(int i=0;i<this.handlerContext.ignoreTimeoutMatcher.length;i++)
            {
                if ( this.handlerContext.ignoreTimeoutMatcher[i] != null)
                {
                    if ( this.handlerContext.ignoreTimeoutMatcher[i].match(className) )
                    {
                        this.useTimeout = false;
                        return;
                    }
                }
            }
        }
        this.useTimeout = true;
    }

    /**
     * Send the event.
     * @param event The event
     */
    public void sendEvent(final Event event)
    {
        final EventHandler handlerService = this.obtain();
        if (handlerService == null)
        {
            return;
        }

        try
        {
            handlerService.handleEvent(event);
        }
        catch (final Throwable e)
        {
            // The spec says that we must catch exceptions and log them:
            LogWrapper.getLogger().log(
                            this.reference,
                            LogWrapper.LOG_ERROR,
                            String.format("Exception during event dispatch [%s | %s | Bundle(%s) | Handler(%s)]", 
                                event, this.reference, this.reference.getBundle(), handlerService), e);
        }
    }

    /**
     * Deny the handler.
     */
    public void denyEventHandler()
    {
        if ( this.denied.compareAndSet(false, true) ) {
            final EventHandler handlerService = this.handler;
            LogWrapper.getLogger().log(
                    LogWrapper.LOG_ERROR,
                    String.format("Denying event handler from ServiceReference [%s | Bundle(%s)%s] due to timeout!",
                        this.reference,
                        this.reference.getBundle(),
                        handlerService == null ? "" : " | Handler(".concat(handlerService.getClass().getName()).concat(")")));

            this.release();
    	}
    }

    public boolean isDenied()
    {
        return this.denied.get();
    }
}

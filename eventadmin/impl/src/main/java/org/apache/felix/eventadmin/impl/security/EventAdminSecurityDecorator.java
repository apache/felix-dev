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
package org.apache.felix.eventadmin.impl.security;

import java.security.Permission;

import org.osgi.framework.Bundle;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

/**
 * This class is a decorator for an {@code EventAdmin} service. It secures the
 * service by checking any call from a given bundle (i.e., the caller) to the admins
 * post or send methods for the appropriate permissions based on a given permission
 * factory. This methods then in turn throw a {@code SecurityException} in case
 * the given bundle doesn't pass the check or delegate the call to decorated service
 * instance, respectively.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class EventAdminSecurityDecorator implements EventAdmin
{
    // The bundle used to determine appropriate permissions
    private final Bundle m_bundle;

    // The decorated service instance
    private final EventAdmin m_admin;

    /**
     * The constructor of this decorator. The given bundle and permission factory
     * will be used to determine appropriate permissions for any call to
     * {@code postEvent()} or {@code sendEvent()}, respectively. This method then
     * in turn throw a {@code SecurityException} in case the given bundle doesn't
     * pass the check.
     *
     * @param bundle The calling bundle used to determine appropriate permissions
     * @param admin The decorated service instance
     */
    public EventAdminSecurityDecorator(final Bundle bundle, final EventAdmin admin)
    {
        checkNull(bundle, "Bundle");
        checkNull(admin, "Admin");

        m_bundle = bundle;

        m_admin = admin;
    }

    /**
     * This method checks whether the given (i.e., calling) bundle has
     * appropriate permissions to post an event to the targeted topic. A
     * {@code SecurityException} is thrown in case it has not. Otherwise, the
     * event is posted using this decorator's service instance.
     *
     * @param event The event that should be posted
     *
     * @see org.osgi.service.event.EventAdmin#postEvent(org.osgi.service.event.Event)
     */
    public void postEvent(final Event event)
    {
        checkPermission(event.getTopic());

        m_admin.postEvent(event);
    }

    /**
     * This method checks whether the given (i.e., calling) bundle has
     * appropriate permissions to send an event to the targeted topic. A
     * {@code SecurityException} is thrown in case it has not. Otherwise,
     * the event is posted using this decorator's service instance.
     *
     * @param event The event that should be send
     *
     * @see org.osgi.service.event.EventAdmin#sendEvent(org.osgi.service.event.Event)
     */
    public void sendEvent(final Event event)
    {
        checkPermission(event.getTopic());

        m_admin.sendEvent(event);
    }

    /**
     * Overrides {@code hashCode()} and returns the hash code of the decorated
     * service instance.
     *
     * @return The hash code of the decorated service instance
     *
     * @see java.lang.Object#hashCode()
     * @see org.osgi.service.event.EventAdmin
     */
    public int hashCode()
    {
        return m_admin.hashCode();
    }

    /**
     * Overrides {@code equals()} and delegates the call to the decorated service
     * instance. In case that o is an instance of this class it passes o's service
     * instance instead of o.
     *
     * @param o The object to compare with this decorator's service instance
     *
     * @see java.lang.Object#equals(java.lang.Object)
     * @see org.osgi.service.event.EventAdmin
     */
    public boolean equals(final Object o)
    {
        if(o instanceof EventAdminSecurityDecorator)
        {
            return m_admin.equals(((EventAdminSecurityDecorator) o).m_admin);
        }

        return m_admin.equals(o);
    }

    /**
     * This is a utility method that will throw a {@code SecurityExcepiton} in case
     * that the given bundle (i.e, the caller) has not appropriate permissions to
     * publish to this topic. This method uses Bundle.hasPermission() and the given
     * permission factory to determine this.
     */
    private void checkPermission(final String topic)
    {
        final Permission p = PermissionsUtil.createPublishPermission(topic);
        if(p != null && !m_bundle.hasPermission(p))
        {
            throw new SecurityException("Bundle[" + m_bundle +
                "] has no PUBLISH permission for topic [" + topic + "]");
        }
    }

    /*
     * This is a utility method that will throw a {@code NullPointerException}
     * in case that the given object is null. The message will be of the form name +
     * may not be null.
     */
    private void checkNull(final Object object, final String name)
    {
        if(null == object)
        {
            throw new NullPointerException(name + " may not be null");
        }
    }
}

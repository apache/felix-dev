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

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.EventAdmin;

/**
 * This class is a factory that secures a given {@code EventAdmin} service by
 * wrapping it with a new instance of an {@code EventAdminSecurityDecorator} on
 * any call to its {@code getService()} method. The decorator will determine the
 * appropriate permissions by using the given permission factory and the bundle
 * parameter passed to the {@code getService()} method.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class SecureEventAdminFactory implements ServiceFactory<EventAdmin>
{
    // The EventAdmin to secure
    private final EventAdmin m_admin;

    /**
     * The constructor of the factory. The factory will use the given event admin and
     * permission factory to create a new {@code EventAdminSecurityDecorator}
     * on any call to {@code getService()}.
     *
     * @param admin The {@code EventAdmin} service to secure.
     */
    public SecureEventAdminFactory(final EventAdmin admin)
    {
        checkNull(admin, "Admin");

        m_admin = admin;
    }

    /**
     * Returns a new {@code EventAdminSecurityDecorator} initialized with the
     * given {@code EventAdmin}. That in turn will check any call to post or
     * send for the appropriate permissions based on the bundle parameter.
     *
     * @param bundle The bundle used to determine the permissions of the caller
     * @param registration The ServiceRegistration that is not used
     *
     * @return The given service instance wrapped by an {@code EventAdminSecuriryDecorator}
     *
     * @see org.osgi.framework.ServiceFactory#getService(org.osgi.framework.Bundle,
     *      org.osgi.framework.ServiceRegistration)
     */
    @Override
    public EventAdmin getService(final Bundle bundle,
        final ServiceRegistration<EventAdmin> registration)
    {
        // We don't need to cache this objects since the framework already does this.
        return new EventAdminSecurityDecorator(bundle, m_admin);
    }

    /**
     * This method doesn't do anything at the moment.
     *
     * @param bundle The bundle object that is not used
     * @param registration The ServiceRegistration that is not used
     * @param service The service object that is not used
     *
     * @see org.osgi.framework.ServiceFactory#ungetService(org.osgi.framework.Bundle,
     *      org.osgi.framework.ServiceRegistration, java.lang.Object)
     */
    @Override
    public void ungetService(final Bundle bundle,
        final ServiceRegistration<EventAdmin> registration, final EventAdmin service)
    {
        // We don't need to do anything here since we hand-out a new instance with
        // any call to getService hence, it is o.k. to just wait for the next gc.
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

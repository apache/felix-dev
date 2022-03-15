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
package org.apache.felix.http.base.internal.runtime;

import java.util.EventListener;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

/**
 * Info object for registered listeners.
 */
public class ListenerInfo extends WhiteboardServiceInfo<EventListener>
{
    private static final Set<String> ALLOWED_INTERFACES;
    static {
        ALLOWED_INTERFACES = new HashSet<String>();
        ALLOWED_INTERFACES.add(HttpSessionAttributeListener.class.getName());
        ALLOWED_INTERFACES.add(HttpSessionIdListener.class.getName());
        ALLOWED_INTERFACES.add(HttpSessionListener.class.getName());
        ALLOWED_INTERFACES.add(ServletContextAttributeListener.class.getName());
        ALLOWED_INTERFACES.add(ServletContextListener.class.getName());
        ALLOWED_INTERFACES.add(ServletRequestAttributeListener.class.getName());
        ALLOWED_INTERFACES.add(ServletRequestListener.class.getName());
    }

    /** Is the listener enabled (per properties) */
    private final boolean enabled;

    /** The service types */
    private final Set<String> types;

    /** The service types as reported through DTOs */
    private final String[] dtoTypes;

    /**
     * Constructor
     * @param ref The service reference
     */
    public ListenerInfo(final ServiceReference<EventListener> ref)
    {
        this(ref, getTypes(ref), null);
    }

    /**
     * Constructor
     * @param ref The service reference
     * @param dtoTypes Optional dto types
     * @param types The listener types
     */
    public ListenerInfo(final ServiceReference<EventListener> ref, final Set<String> types, final String[] dtos)
    {
        super(ref);
        this.enabled = this.getBooleanProperty(ref, HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER);
        this.types = types;
        this.dtoTypes = dtos == null ? types.toArray(new String[types.size()]) : dtos;
    }

    private static Set<String> getTypes(final ServiceReference<EventListener> ref) {
        final String[] objectClass = (String[])ref.getProperty(Constants.OBJECTCLASS);
        final Set<String> names = new HashSet<String>();
        for(final String name : objectClass)
        {
            if ( ALLOWED_INTERFACES.contains(name) )
            {
                names.add(name);
            }
        }
        return names;
    }

    @Override
    public boolean isValid()
    {
        return super.isValid() && this.enabled;
    }

    /**
     * The types as reported through the DTOs
     * @return Array of types
     */
    public @NotNull String[] getDTOListenerTypes()
    {
        return this.dtoTypes;
    }

    /**
     * Is this listener of the required type?
     * @param className The listener type
     * @return {@code true} If the listener should be registered as that type
     */
    public boolean isListenerType(@NotNull final String className)
    {
        for(final String t : this.types)
        {
            if ( t.equals(className) )
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the registered listener types
     * @return The set of types
     */
    public @NotNull Set<String> getListenerTypes() {
        return this.types;
    }

    @Override
    public @NotNull String getType() {
        return "Listener";
    }
}

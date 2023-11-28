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
package org.apache.felix.http.base.internal.whiteboard.tracker;

import java.util.EventListener;

import org.apache.felix.http.base.internal.runtime.ListenerInfo;
import org.apache.felix.http.base.internal.runtime.WhiteboardServiceInfo;
import org.apache.felix.http.base.internal.whiteboard.WhiteboardManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

public final class ListenersTracker extends WhiteboardServiceTracker<EventListener>
{
    /**
     * Create a filter expression for all supported listener.
     */
    private static String createListenersFilterExpression()
    {
        return String.format("(&" +
                             "(|(objectClass=%s)(objectClass=%s)(objectClass=%s)(objectClass=%s)(objectClass=%s)(objectClass=%s)(objectClass=%s))" +
                             "(%s=*)(!(%s~=false)))",
                HttpSessionAttributeListener.class.getName(),
                HttpSessionIdListener.class.getName(),
                HttpSessionListener.class.getName(),
                ServletContextListener.class.getName(),
                ServletContextAttributeListener.class.getName(),
                ServletRequestListener.class.getName(),
                ServletRequestAttributeListener.class.getName(),
                HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER,
                HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER);
    }

    public ListenersTracker(final BundleContext context, final WhiteboardManager manager)
    {
        super(manager, context, createListenersFilterExpression());
    }

    @Override
    protected WhiteboardServiceInfo<EventListener> getServiceInfo(final ServiceReference<EventListener> ref) {
        return new ListenerInfo(ref);
    }
}

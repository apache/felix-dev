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
package org.apache.felix.log.itests.log.events;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.annotation.bundle.Requirement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.namespace.service.ServiceNamespace;
import org.osgi.resource.Namespace;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.Logger;

@Requirement(namespace = ServiceNamespace.SERVICE_NAMESPACE,
             filter = "(objectClass=org.osgi.service.event.EventAdmin)",
             effective = Namespace.EFFECTIVE_ACTIVE)
public class LogEventsTest {
    public static org.osgi.service.log.Logger getLogger(Class<?> clazz) {
        BundleContext bundleContext = FrameworkUtil.getBundle(clazz).getBundleContext();
        ServiceReference<org.osgi.service.log.LoggerFactory> serviceReference =
                bundleContext.getServiceReference(org.osgi.service.log.LoggerFactory.class);
        org.osgi.service.log.LoggerFactory loggerFactory = bundleContext.getService(serviceReference);
        final Logger logger = loggerFactory.getLogger(clazz);
        Assert.assertNotNull(logger);
        return logger;
    }

    public static EventAdmin getEventAdmin() {
        final Bundle bundle = FrameworkUtil.getBundle(LogEventsTest.class);
        final BundleContext bundleContext = bundle.getBundleContext();
        final ServiceReference<EventAdmin> eventAdminRef = bundleContext.getServiceReference(EventAdmin.class);
        final EventAdmin eventAdmin = bundleContext.getService(eventAdminRef);
        Assert.assertNotNull(eventAdmin);
        return eventAdmin;
    }

    static class LogEventHandler implements EventHandler {
        private final List<Event> events = new ArrayList<>(1);
        private final CountDownLatch latch = new CountDownLatch(1);

        public synchronized List<Event> getEvents() {
            return new ArrayList<>(events);
        }

        public CountDownLatch getLatch() {
            return latch;
        }

        @Override
        public synchronized void handleEvent(Event event) {
            events.add(event);
            latch.countDown();
        }
    }

    @Test
    public void test() throws InterruptedException {
        final Bundle bundle = FrameworkUtil.getBundle(LogEventsTest.class);
        final BundleContext bundleContext = bundle.getBundleContext();
        final LogEventHandler handler = new LogEventHandler();
        Dictionary<String, Object> handlerProps = new Hashtable<>(2);
        handlerProps.put(EventConstants.EVENT_TOPIC, "org/osgi/service/log/LogEntry/LOG_ERROR");
        handlerProps.put(EventConstants.EVENT_FILTER, "(bundle.symbolicName=org.apache.felix.log.itests.logevents)" );
        ServiceRegistration<?> eventHandlerRegistration
                = bundleContext.registerService(EventHandler.class.getName(), handler, handlerProps);
        try {
            final org.osgi.service.log.Logger logger = getLogger(LogEventsTest.class);
            logger.error("test");
            handler.getLatch().await(1, TimeUnit.SECONDS);
            eventHandlerRegistration.unregister();
            eventHandlerRegistration = null;
            final List<Event> events = handler.getEvents();
            Assert.assertEquals("Expecting one event", 1, events.size());
        } finally {
            if (eventHandlerRegistration != null) {
                eventHandlerRegistration.unregister();
            }
        }
    }
}

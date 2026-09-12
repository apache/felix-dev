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
package org.apache.felix.logback.test.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.List;

import org.apache.felix.logback.test.helper.ls.LogServiceHelper;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;
import org.osgi.service.log.Logger;
import org.slf4j.event.KeyValuePair;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;


public abstract class LogTestHelper {

    protected static ListAppender<ILoggingEvent> listAppender;
    protected static PatternLayoutEncoder encoder;

    @BeforeClass
    public static void before() throws Exception {
        LoggerContext context = (LoggerContext)org.slf4j.LoggerFactory.getILoggerFactory();

        for (ch.qos.logback.classic.Logger logger : context.getLoggerList()) {
            for (Iterator<Appender<ILoggingEvent>> index = logger.iteratorForAppenders(); index.hasNext();) {
                Appender<ILoggingEvent> appender = index.next();

                if (appender instanceof ListAppender) {
                    listAppender = (ListAppender<ILoggingEvent>)appender;
                }
            }
        }

        encoder = new PatternLayoutEncoder();
        encoder.setPattern("%level|%logger{1000}|%msg");
        encoder.setContext(context);
        encoder.start();
    }

    public String getBSN() {
        return FrameworkUtil.getBundle(getClass()).getSymbolicName();
    }

    protected void assertLog(String level, String name, long time) {
        assertLog(level + "|" + name + "|" + time);
    }

    protected void assertLog(String record) {
        findLog(record);
    }

    protected ILoggingEvent findLog(String record) {
        try {
            // we need to make sure we wait for async logging internals to cool down
            Thread.sleep(10);
        }
        catch (InterruptedException e) {
        }

        if (listAppender.list != null) {
            for (ILoggingEvent event : listAppender.list) {
                if (record.equals(new String(encoder.encode(event)))) {
                    return event;
                }
            }
        }

        throw new RuntimeException("Log record not found: " + record);
    }

    protected void assertExtendedServiceLog(
        String record, int type, ServiceReference<?> reference) {

        ILoggingEvent event = findLog(record);
        Object context = getKeyValue(event, "osgi.log.context");
        Object threadId = getKeyValue(event, "osgi.log.thread.id");

        assertTrue(context instanceof ServiceEvent);
        ServiceEvent serviceEvent = (ServiceEvent)context;
        assertEquals(type, serviceEvent.getType());
        assertEquals(reference, serviceEvent.getServiceReference());
        assertTrue(threadId instanceof Long);
        assertTrue(((Long)threadId).longValue() > 0);
    }

    private static Object getKeyValue(ILoggingEvent event, String key) {
        List<KeyValuePair> values = event.getKeyValuePairs();

        if (values != null) {
            for (KeyValuePair value : values) {
                if (key.equals(value.key)) {
                    return value.value;
                }
            }
        }

        return null;
    }

}

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
package org.apache.felix.http.jetty.internal;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.RequestLog;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RequestLog that logs via SLF4J when present. Not registered by default.
 */
class LogServiceRequestLog extends CustomRequestLog {

    public static final String SVC_PROP_NAME = "name";
    public static final String DEFAULT_NAME = "osgi";
    public static final String PREFIX = "REQUEST: ";

    private final String serviceName;

    private volatile ServiceRegistration<RequestLog> registration;

    
    LogServiceRequestLog(JettyConfig config) {
        super(new Slf4JRequestLogWriter(config.getRequestLogOsgiSlf4JLoggerName()), config.getRequestLogOSGiFormat());
        this.serviceName = config.getRequestLogOSGiServiceName();
    }

    private static final class Slf4JRequestLogWriter implements RequestLog.Writer {

        private final Logger logger;

        public Slf4JRequestLogWriter(String name) {
            this.logger = LoggerFactory.getLogger(name);
        }

        @Override
        public void write(String requestEntry) throws IOException {
            logger.info(PREFIX.concat(requestEntry));
        }
    }

    public synchronized void register(BundleContext context) throws IllegalStateException {
        if (registration != null) {
            throw new IllegalStateException(getClass().getSimpleName() + " already registered");
        }
        Dictionary<String, Object> svcProps = new Hashtable<>();
        svcProps.put(SVC_PROP_NAME, serviceName);
        this.registration = context.registerService(RequestLog.class, this, svcProps);
    }

    public synchronized void unregister() {
        if (registration != null) {
            try {
                registration.unregister();
            } catch ( final IllegalStateException ignore ) {
                // ignore
            } finally {
                registration = null;
            }
        }
    }
}

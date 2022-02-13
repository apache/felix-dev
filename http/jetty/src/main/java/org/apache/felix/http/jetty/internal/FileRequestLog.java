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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.eclipse.jetty.server.AsyncRequestLogWriter;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.RequestLogWriter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

class FileRequestLog {

    public static final String SVC_PROP_NAME = "name";
    public static final String DEFAULT_NAME = "file";
    public static final String SVC_PROP_FILEPATH = "filepath";

    private final CustomRequestLog delegate;
    private final String logFilePath;
    private final String serviceName;
    private volatile ServiceRegistration<RequestLog> registration;

    FileRequestLog(JettyConfig config) {
        logFilePath = config.getRequestLogFilePath();
        serviceName = config.getRequestLogFileServiceName() != null ? config.getRequestLogFileServiceName() : DEFAULT_NAME;
        final RequestLogWriter writer;
        if (config.isRequestLogFileAsync()) {
            writer = new AsyncRequestLogWriter(logFilePath);
        } else {
            writer = new RequestLogWriter(logFilePath);
        }
        writer.setAppend(config.isRequestLogFileAppend());
        writer.setRetainDays(config.getRequestLogFileRetainDays());
        writer.setFilenameDateFormat(config.getRequestLogFilenameDateFormat());

        delegate = new CustomRequestLog(writer, config.getRequestLogFileFormat());
        delegate.setIgnorePaths(config.getRequestLogFileIgnorePaths());
    }

    synchronized void start(BundleContext context) throws IOException, IllegalStateException {
        File logFile = new File(logFilePath).getAbsoluteFile();
        File logFileDir = logFile.getParentFile();
        if (logFileDir != null && !logFileDir.isDirectory()) {
            SystemLogger.info("Creating directory " + logFileDir.getAbsolutePath());
            Files.createDirectories(logFileDir.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        }

        if (registration != null) {
            throw new IllegalStateException(getClass().getSimpleName() + " is already started");
        }
        try {
            delegate.start();
            Dictionary<String, Object> svcProps = new Hashtable<>();
            svcProps.put(SVC_PROP_NAME, serviceName);
            svcProps.put(SVC_PROP_FILEPATH, logFilePath);
            registration = context.registerService(RequestLog.class, delegate, svcProps);
        } catch (Exception e) {
            SystemLogger.error("Error starting File Request Log", e);
        }
    }

    synchronized void stop() {
        try {
            if (registration != null) {
                registration.unregister();
            }
            delegate.stop();
        } catch (Exception e) {
            SystemLogger.error("Error shutting down File Request Log", e);
        } finally {
            registration = null;
        }
    }

}

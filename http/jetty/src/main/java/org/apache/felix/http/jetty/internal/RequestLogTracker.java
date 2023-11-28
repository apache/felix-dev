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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.apache.felix.http.base.internal.util.ServiceUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

/**
 * An instance of Jetty's RequestLog that dispatches to registered RequestLog services in the service registry. A filter
 * can be provided so that it only dispatches to selected services.
 * <p>
 * Unchecked exceptions from the RequestLog services are caught and logged to the OSGi LogService. to avoid flooding the
 * LogService, we will remove a RequestLog service if it breaches a maximum number of errors (see {@link
 * RequestLogTracker#MAX_ERROR_COUNT}). Once this happens we will stop dispatching to that service entirely until it is
 * unregistered.
 */
class RequestLogTracker extends ServiceTracker<RequestLog, RequestLog>  implements RequestLog {

    public static final int MAX_ERROR_COUNT = 100;

    private final ConcurrentMap<ServiceReference<?>, RequestLog> logSvcs = new ConcurrentHashMap<>();
    private final ConcurrentMap<ServiceReference<?>, Integer> naughtyStep = new ConcurrentHashMap<>();

    RequestLogTracker(BundleContext context, String filter) throws InvalidSyntaxException {
        super(context, buildFilter(filter), null);
    }

    private static Filter buildFilter(String inputFilter) throws InvalidSyntaxException {
        String objectClassFilter = String.format("(%s=%s)", Constants.OBJECTCLASS, RequestLog.class.getName());
        String compositeFilter;
        if (inputFilter != null) {
            // Parse the input filter just for validation before we insert into ours.
            FrameworkUtil.createFilter(inputFilter);
            compositeFilter = "(&" + objectClassFilter + inputFilter + ")";
        } else {
            compositeFilter = objectClassFilter;
        }
        return FrameworkUtil.createFilter(compositeFilter);
    }

    @Override
    public RequestLog addingService(ServiceReference<RequestLog> reference) {
        RequestLog logSvc = ServiceUtils.safeGetService(context, reference);
        if ( logSvc != null ) {
            logSvcs.put(reference, logSvc);
        }
        return logSvc;
    }

    @Override
    public void removedService(ServiceReference<RequestLog> reference, RequestLog logSvc) {
        logSvcs.remove(reference);
        naughtyStep.remove(reference);
        ServiceUtils.safeUngetService(context, reference);
    }

    @Override
    public void log(Request request, Response response) {
        for (Map.Entry<ServiceReference<?>, RequestLog> entry : logSvcs.entrySet()) {
            try {
                entry.getValue().log(request, response);
            } catch (Exception e) {
                processError(entry.getKey(), e);
            }
        }
    }

    /**
     * Process an exception from a RequestLog service instance, and remove the service if it has reached the maximum
     * error limit.
     */
    private void processError(ServiceReference<?> reference, Exception e) {
        SystemLogger.LOGGER.error(SystemLogger.formatMessage(reference, String.format("Error dispatching to request log service ID %d from bundle %s:%s",
                reference.getProperty(Constants.SERVICE_ID), reference.getBundle().getSymbolicName(), reference.getBundle().getVersion())), e);

        int naughty = naughtyStep.merge(reference, 1, Integer::sum);
        if (naughty >= MAX_ERROR_COUNT) {
            // We have reached (but not exceeded) the maximum, and the last error has been logged. Remove from the maps
            // so we will not invoke the service again.
            logSvcs.remove(reference);
            naughtyStep.remove(reference);
            SystemLogger.LOGGER.error(SystemLogger.formatMessage(reference, String.format("RequestLog service ID %d from bundle %s:%s threw too many errors, it will no longer be invoked.",
                    reference.getProperty(Constants.SERVICE_ID), reference.getBundle().getSymbolicName(), reference.getBundle().getVersion())));
        }
    }
}

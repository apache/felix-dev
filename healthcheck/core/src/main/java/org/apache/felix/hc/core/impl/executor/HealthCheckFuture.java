/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.felix.hc.core.impl.executor;

import static org.apache.felix.hc.api.FormattingResultLog.msHumanReadable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import org.apache.felix.hc.api.FormattingResultLog;
import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.ResultLog;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckMetadata;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Future to be able to schedule a health check for parallel execution. */
public class HealthCheckFuture extends FutureTask<ExecutionResult> {

    public interface Callback {
        public void finished(final HealthCheckExecutionResult result);
    }

    private final static Logger LOG = LoggerFactory.getLogger(HealthCheckFuture.class);

    private static Result createResult(final HealthCheckMetadata metadata, Exception e) {
        return new Result(Result.Status.HEALTH_CHECK_ERROR, "Exception during execution of '" + metadata.getName() + "'", e);
    }

    private final HealthCheckMetadata metadata;
    private final Date createdTime;

    public HealthCheckFuture(final HealthCheckMetadata metadata, final BundleContext bundleContext, final Callback callback) {
        super(new Callable<ExecutionResult>() {
            @Override
            public ExecutionResult call() throws Exception {
                Thread.currentThread().setName("HealthCheck " + metadata.getTitle());
                LOG.debug("Starting check {}", metadata);

                long startTime = System.currentTimeMillis();
                Result resultFromHealthCheck = null;
                ExecutionResult executionResult = null;

                Object healthCheckObject = bundleContext.getService(metadata.getServiceReference());
                try {
                    if (healthCheckObject != null) {
                        HealthCheck healthCheck = healthCheckObject instanceof HealthCheck 
                                ? (HealthCheck) healthCheckObject 
                                : new LegacyHealthCheckWrapper(healthCheckObject);
                        resultFromHealthCheck = healthCheck.execute();

                    } else {
                        throw new IllegalStateException("Service cannot be retrieved - probably activate() failed or there are unsatisfied references");
                    }
                } catch (final Exception e) {
                    resultFromHealthCheck = createResult(metadata, e);
                } catch (final Throwable t) {
                	Exception e = new RuntimeException("System error during health check execution", t);
                    resultFromHealthCheck = createResult(metadata, e);
                } finally {
                    // unget service ref
                    bundleContext.ungetService(metadata.getServiceReference());

                    // update result with information about this run
                    long elapsedTime = (System.currentTimeMillis() - startTime);
                    if (resultFromHealthCheck != null) {
                        // wrap the result in an execution result
                        executionResult = new ExecutionResult(metadata, resultFromHealthCheck, elapsedTime);
                    }
                    LOG.debug("Time consumed for {}: {}", metadata, msHumanReadable(elapsedTime));
                }

                callback.finished(executionResult);
                Thread.currentThread().setName("HealthCheck-idle");
                return executionResult;
            }

        });
        this.createdTime = new Date();
        this.metadata = metadata;

    }

    Date getCreatedTime() {
        return this.createdTime;
    }

    public HealthCheckMetadata getHealthCheckMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "[Future for " + this.metadata + ", createdTime=" + this.createdTime + "]";
    }

    private static class LegacyHealthCheckWrapper implements HealthCheck {
        private final Object legacyHealthCheck;
        private final FormattingResultLog log;
        
        public LegacyHealthCheckWrapper(Object legacyHealthCheck) {
            this.legacyHealthCheck = legacyHealthCheck;
            this.log = new FormattingResultLog();
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Result execute() {
            Class<?> hcClass = legacyHealthCheck.getClass();
            log.debug("Running legacy HC {}, please convert to new interface org.apache.felix.hc.api.HealthCheck!",
                    hcClass.getName());
            
            Object result;
            try {
                Method executeMethod = hcClass.getMethod("execute");
                result = executeMethod.invoke(legacyHealthCheck);
            } catch (InvocationTargetException e) {
                log.healthCheckError("Exception during execute() of Sling HC {}: {}", hcClass.getName(), String.valueOf(e.getTargetException()), e);
                return new Result(log);
            } catch (ReflectiveOperationException e) {
                log.healthCheckError("Could not call Sling HC {} from Felix Runtime: {}", hcClass.getName(), String.valueOf(e), e);
                return new Result(log);
            }
            
            try {
                Object resultLog = readPrivateField(result, "resultLog");

                List<?> entries = (List) readPrivateField(resultLog, "entries");
                if(entries != null) {
                    for (Object object : entries) {
                        String statusLegacy = String.valueOf(readPrivateField(object, "status"));
                        String message = (String) readPrivateField(object, "message");
                        Exception exception = (Exception) readPrivateField(object, "exception");
                        if(statusLegacy.equals("DEBUG")) {
                            log.add(new ResultLog.Entry(message, true, exception));
                        } else {
                            statusLegacy = statusLegacy.replace("INFO", Result.Status.OK.name());
                            log.add(new ResultLog.Entry(Result.Status.valueOf(statusLegacy), message, exception));
                        }
                    }
                }
            } catch (ReflectiveOperationException e) {
                log.healthCheckError("Could convert Sling HC result of {} for Felix Runtime: {}", hcClass.getName(), String.valueOf(e), e);
            }
            return new Result(log);
        }
        
        private Object readPrivateField(Object target, String fieldName) throws ReflectiveOperationException {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        }
    }

}

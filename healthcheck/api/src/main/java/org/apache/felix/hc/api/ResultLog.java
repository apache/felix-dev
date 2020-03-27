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
package org.apache.felix.hc.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.felix.hc.api.Result.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The log of a Result, allows for providing multiple lines of information which are aggregated as a single Result. */
public class ResultLog implements Iterable<ResultLog.Entry> {
    private static final String HC_LOGGING_SYS_PROP = "org.apache.felix.hc.autoLogging";
    private static final String HC_LOGGING_PREFIX = "healthchecks.";
    
    private List<Entry> entries = new LinkedList<Entry>();
    private Status aggregateStatus;
    
    private Logger hcLogger = null;

    /** An entry in this log */
    public static class Entry {
        private final Status status;
        private final String message;
        private final boolean isDebug;
        private final Exception exception;

        /**
         * @param s The status of the message 
         * @param message The message
         */
        public Entry(Status s, String message) {
            this(s, message, false, null);
        }
        
        /**
         * @param message The message with status OK
         * @param isDebug Whether this is a debug message
         */
        public Entry(String message, boolean isDebug) {
            this(Status.OK, message, isDebug, null);
        }
        /**
         * @param message The message with status OK
         * @param isDebug Whether this is a debug message
         * @param exception An exception that belongs to this message
         */
        public Entry(String message, boolean isDebug, Exception exception) {
            this(Status.OK, message, isDebug, exception);
        }
        
        /**
         * @param s The status of the message 
         * @param message The message 
         * @param exception An exception that belongs to this message
         */
        public Entry(Status s, String message, Exception exception) {
            this(s, message, false, exception);
        }

        // private to not allow invalid combinations of isDebug=true and a status different than Status.OK
        private Entry(Status s, String message, boolean isDebug, Exception exception) {
            this.status = s;
            this.message = message;
            this.exception = exception;
            this.isDebug = isDebug;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(getLogLevel()).append(" ").append(message);
            if (exception != null) {
                builder.append(" Exception: " + exception.getMessage());
            }
            return builder.toString();
        }

        /**
         * @return The status of this entry
         */
        public Status getStatus() {
            return status;
        }

        /**
         * @return The log level of this entry
         */
        public String getLogLevel() {
            switch (status) {
            case OK:
                return isDebug ? "DEBUG" : "INFO";
            default:
                return status.toString();
            }
        }

        /**
         * @return The message of this entry
         */
        public String getMessage() {
            return message;
        }

        /**
         * @return The exception of this entry or null if no exception exists for this message
         */
        public Exception getException() {
            return exception;
        }

        /**
         * @return true if this is a debug message
         */
        public boolean isDebug() {
            return isDebug;
        }
    }

    /** Build a log. Initial aggregate status is set to WARN, as an empty log is not considered ok. That's reset to OK before adding the
     * first log entry, and then the status aggregation rules take over. */
    public ResultLog() {
        aggregateStatus = Result.Status.WARN;
        setupLogger();
    }

    /** Create a copy of the result log
     * @param log Clone constructor */
    public ResultLog(final ResultLog log) {
        this.aggregateStatus = log.aggregateStatus;
        this.entries = new ArrayList<ResultLog.Entry>(log.entries);
        setupLogger();
    }

    /** Add an entry to this log. The aggregate status of this is set to the highest of the current 
     * aggregate status and the new Entry's status 
     * @param entry The entry to add
     * @return the result log for chaining */
    public ResultLog add(Entry entry) {
        if (entries.isEmpty()) {
            aggregateStatus = Result.Status.OK;
        }

        entries.add(entry);

        logEntry(entry);

        if (entry.getStatus().ordinal() > aggregateStatus.ordinal()) {
            aggregateStatus = entry.getStatus();
        }
        return this;
    }
    
    private void setupLogger() {
        if(Boolean.valueOf(System.getProperty(HC_LOGGING_SYS_PROP))) {
            StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
            for (int i = 2; i < stackTraceElements.length; i++) {
                StackTraceElement stackTraceElement = stackTraceElements[i];
                String className = stackTraceElement.getClassName();
                if(className.startsWith(getClass().getPackage().getName())) {
                    continue; // same package we ignore
                }
                if(className.startsWith("org.apache.felix.hc.core.impl.executor")) {
                    break; // internal helper results
                }
                hcLogger = LoggerFactory.getLogger(HC_LOGGING_PREFIX + className);
                break; // stop searching
            }
       }
    }

    private void logEntry(Entry entry) {
        if(hcLogger != null) {
            if(entry.isDebug()) {
                if(hcLogger.isDebugEnabled()) {
                    hcLogger.debug(getAutoLogMessage(entry), entry.exception);
                }
            } else {
                switch(entry.getStatus()) {
                case OK: 
                case TEMPORARILY_UNAVAILABLE: 
                    if(hcLogger.isInfoEnabled()) {
                        hcLogger.info(getAutoLogMessage(entry), entry.exception);
                    }
                    break;
                case WARN: 
                    hcLogger.warn(getAutoLogMessage(entry), entry.exception);
                    break;
                case CRITICAL: 
                case HEALTH_CHECK_ERROR: 
                    hcLogger.error(getAutoLogMessage(entry), entry.exception);
                    break;
                }
            }
        }
    }

    private String getAutoLogMessage(Entry e) {
        return e.status.name() + " " + e.getMessage();
    }
    
    /** Return an Iterator on our entries
     * @return the iterator over all entries */
    @Override
    public Iterator<ResultLog.Entry> iterator() {
        return entries.iterator();
    }

    /** Return our aggregate status, i.e. the highest status of the entries added to this log. Starts at OK for an empty ResultLog, so
     * cannot be lower than that.
     * 
     *  @return the aggregate status */
    public Status getAggregateStatus() {
        return aggregateStatus;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ResultLog: ");
        sb.append(this.entries.toString());
        return sb.toString();
    }
}
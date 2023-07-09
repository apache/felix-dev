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

import java.text.NumberFormat;
import java.util.Locale;

import org.apache.felix.hc.api.util.FormatUtil;
import org.osgi.annotation.versioning.ProviderType;

/** Utility that provides a logging-like facade on a ResultLog. */
@ProviderType
public class FormattingResultLog extends ResultLog {

    /**
     * @param message The message for the log entry (with {} placeholders as known from slf4j)
     * @param args The args for the placeholders given in message 
     */
    public void debug(String message, Object... args) {
        add(createEntry(true, message, args));
    }

    /**
     * @param message The message for the log entry (with {} placeholders as known from slf4j)
     * @param args The args for the placeholders given in message 
     */
    public void info(String message, Object... args) {
        add(createEntry(false, message, args));
    }

    /**
     * @param message The message for the log entry (with {} placeholders as known from slf4j)
     * @param args The args for the placeholders given in message 
     */
    public void warn(String message, Object... args) {
        add(createEntry(Result.Status.WARN, message, args));
    }

    /**
     * @param message The message for the log entry (with {} placeholders as known from slf4j)
     * @param args The args for the placeholders given in message 
     */
    public void critical(String message, Object... args) {
        add(createEntry(Result.Status.CRITICAL, message, args));
    }

    /**
     * 
     * @param message The message for the log entry (with {} placeholders as known from slf4j)
     * @param args The args for the placeholders given in message 
     */
    public void temporarilyUnavailable(String message, Object... args) {
        add(createEntry(Result.Status.TEMPORARILY_UNAVAILABLE, message, args));
    }
    
    /**
     * @param message The message for the log entry (with {} placeholders as known from slf4j)
     * @param args The args for the placeholders given in message 
     */
    public void healthCheckError(String message, Object... args) {
        add(createEntry(Result.Status.HEALTH_CHECK_ERROR, message, args));
    }

    /** Utility method to return any magnitude of milliseconds in a human readable message using the appropriate time unit (ms, sec, min)
     * depending on the magnitude of the input.
     * 
     * @param millis milliseconds
     * @return a string with a number and a unit */
    public static String msHumanReadable(final long millis) {

        double number = millis;
        final String[] units = new String[] { "ms", "sec", "min", "h", "days" };
        final double[] divisors = new double[] { 1000, 60, 60, 24 };

        int magnitude = 0;
        do {
            double currentDivisor = divisors[Math.min(magnitude, divisors.length - 1)];
            if (number < currentDivisor) {
                break;
            }
            number /= currentDivisor;
            magnitude++;
        } while (magnitude < units.length - 1);
        NumberFormat format = NumberFormat.getNumberInstance(Locale.UK);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(1);
        String result = format.format(number) + units[magnitude];
        return result;
    }
    

    /**
     * Utility method to return any magnitude of bytes in a human readable format using the appropriate unit (kB, MB, GB)
     * depending on the magnitude of the input.
     * 
     * @param size in bytes
     * @return a human readable result 
     */
    public static String bytesHumanReadable(double size) {
        
        double step = 1024, current = step;
        final String SIZES[] = { "kB", "MB", "GB", "TB" };
        int i;
        for (i = 0; i < SIZES.length - 1; ++i) {
            if (size < current * step) {
                break;
            }
            current *= step;
        }

        String unit = SIZES[i];
        double value = size / current;
        String retVal = String.format("%.1f", value) + unit;
        return retVal;
    }
        

    private ResultLog.Entry createEntry(Result.Status status, String message, Object... args) {
        return new ResultLog.Entry(status, FormatUtil.format(message, args));
    }
    
    private ResultLog.Entry createEntry(boolean isDebug, String message, Object... args) {
        return new ResultLog.Entry(FormatUtil.format(message, args), isDebug);
    }
}
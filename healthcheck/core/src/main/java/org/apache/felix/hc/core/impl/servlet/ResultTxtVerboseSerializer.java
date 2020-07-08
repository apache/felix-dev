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
package org.apache.felix.hc.core.impl.servlet;

import static org.apache.felix.hc.api.FormattingResultLog.msHumanReadable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.ResultLog;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.core.impl.util.lang.StringUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Serializes health check results into a verbose text message. */
@Component(service = ResultTxtVerboseSerializer.class)
public class ResultTxtVerboseSerializer {

    private static final Logger LOG = LoggerFactory.getLogger(ResultTxtVerboseSerializer.class);

    private static final String NEWLINE = "\n"; // not using system prop 'line.separator' as not the local but the calling system is
                                                // relevant.

    private int totalWidth;

    private int colWidthName;

    private int colWidthResult;

    private int colWidthTiming;

    private int colWidthWithoutLog;
    private int colWidthLog;

    @Activate
    protected final void activate(final ResultTxtVerboseSerializerConfiguration configuration) {
        this.totalWidth = configuration.totalWidth();
        this.colWidthName = configuration.colWidthName();
        this.colWidthResult = configuration.colWidthResult();
        this.colWidthTiming = configuration.colWidthTiming();
        colWidthWithoutLog = colWidthName + colWidthResult + colWidthTiming;
        colWidthLog = totalWidth - colWidthWithoutLog;
    }

    public String serialize(final Result overallResult, final List<HealthCheckExecutionResult> executionResults, boolean includeDebug) {

        LOG.debug("Sending verbose txt response... ");

        StringBuilder resultStr = new StringBuilder();

        resultStr.append(StringUtils.repeat("-", totalWidth) + NEWLINE);
        resultStr.append(center("Overall Health Result: " + overallResult.getStatus().toString(), totalWidth) + NEWLINE);
        resultStr.append(StringUtils.repeat("-", totalWidth) + NEWLINE);
        resultStr.append(rightPad("Name", colWidthName));
        resultStr.append(rightPad("Result", colWidthResult));
        resultStr.append(rightPad("Timing", colWidthTiming));
        resultStr.append("Logs" + NEWLINE);
        resultStr.append(StringUtils.repeat("-", totalWidth) + NEWLINE);

        final DateFormat dfShort = new SimpleDateFormat("HH:mm:ss.SSS");

        for (HealthCheckExecutionResult healthCheckResult : executionResults) {
            appendVerboseTxtForResult(resultStr, healthCheckResult, includeDebug, dfShort);
        }
        resultStr.append(StringUtils.repeat("-", totalWidth) + NEWLINE);

        return resultStr.toString();

    }

    private void appendVerboseTxtForResult(StringBuilder resultStr, HealthCheckExecutionResult healthCheckResult, boolean includeDebug,
            DateFormat dfShort) {

        String wrappedName = wordWrap(healthCheckResult.getHealthCheckMetadata().getTitle(), colWidthName, "\n");

        int lastIndexOfNewline = wrappedName.lastIndexOf("\n");
        String relevantNameStringForPadding = lastIndexOfNewline >= 0 ? wrappedName.substring(lastIndexOfNewline+1) : wrappedName;
        int paddingSize = colWidthName - relevantNameStringForPadding.length();

        resultStr.append(wrappedName + StringUtils.repeat(" ", paddingSize));
        resultStr.append(rightPad(healthCheckResult.getHealthCheckResult().getStatus().toString(), colWidthResult));
        resultStr.append(rightPad("[" + dfShort.format(healthCheckResult.getFinishedAt())
                + "|" + msHumanReadable(healthCheckResult.getElapsedTimeInMs()) + "]", colWidthTiming));

        boolean isFirst = true;
        for (ResultLog.Entry logEntry : healthCheckResult.getHealthCheckResult()) {
            if (!includeDebug && logEntry.isDebug()) {
                continue;
            }
            if (isFirst) {
                isFirst = false;
            } else {
                resultStr.append(StringUtils.repeat(" ", colWidthWithoutLog));
            }

            String oneLineMessage = getStatusForTxtLog(logEntry) + logEntry.getMessage();
            String messageToPrint = wordWrap(oneLineMessage, colWidthLog, "\n" + StringUtils.repeat(" ", colWidthWithoutLog));

            resultStr.append(messageToPrint);
            resultStr.append(NEWLINE);
        }

        if (isFirst) {
            // no log entry exists, ensure newline
            resultStr.append(NEWLINE);
        }

    }

    private String getStatusForTxtLog(ResultLog.Entry logEntry) {
        if (logEntry.getStatus() == Result.Status.OK) {
            return "";
        } else {
            return logEntry.getStatus().toString() + " ";
        }
    }

    // -- Some simple helpers directly here as commons lang is removed

    String wordWrap(String s, int maxWidth, String newlineDelimiter) {
        return s.replaceAll("(.{1,"+maxWidth+"})(?: +|$)\\n?|(.{"+maxWidth+"})", "$1$2"+newlineDelimiter).trim();
    }
    
    public static String rightPad(String s, int size) {
        if(s.length() < size) {
            return s + StringUtils.repeat(" ", size - s.length());
        } else {
            return s;
        }
    }
    
    static String center(String s, int size) {
        if(s.length() < size) {
            int padding = size - s.length();
            int paddingLeft = padding / 2;
            int paddingRight = padding / 2;
            if(padding % 2 == 1) {
                paddingRight++;
            }
            return StringUtils.repeat(" ", paddingLeft) + s + StringUtils.repeat(" ", paddingRight);
        } else {
            return s;
        }
    }
}

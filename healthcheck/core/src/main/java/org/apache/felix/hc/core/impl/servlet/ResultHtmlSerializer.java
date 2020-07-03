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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.ResultLog.Entry;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckMetadata;
import org.apache.felix.hc.core.impl.servlet.HealthCheckExecutorServlet.Param;
import org.apache.felix.hc.core.impl.util.lang.StringUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/** Serializes health check results into html format. */
@Component(service = ResultHtmlSerializer.class)
public class ResultHtmlSerializer {

    private String styleString;

    @Activate
    protected final void activate(final ResultHtmlSerializerConfiguration configuration) {
        this.styleString = configuration.styleString();
    }

    public String serialize(final Result overallResult, final List<HealthCheckExecutionResult> executionResults, List<Param> paramList,
            boolean includeDebug) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        writer.println("<html><head><title>System Health</title>" +
                "<meta http-equiv='Content-Type' content='text/html; charset=UTF-8' /><style>" + styleString +
                "</style></head><body><h1>System Health</h1>");

        writer.println("<p><span class=\"" + getClassForStatus(overallResult.getStatus()) + "\" style=\"padding:4px\"><strong>Overall Result: "
                + overallResult.getStatus() + "</strong></span></p>");

        final DateFormat dfShort = new SimpleDateFormat("HH:mm:ss.SSS");
        final DateFormat dfLong = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        writer.println("<table id=\"healthCheckResults\" cellspacing=\"0\">");
        writer.println(
                "<thead><tr><th>Health Check <span style='color:gray'>(tags)</span></th><th>Status</th><th>Log</th><th>Finished At</th><th>Time</th></tr></thead>");
        for (HealthCheckExecutionResult executionResult : executionResults) {
            Result result = executionResult.getHealthCheckResult();
            List<String> tags = executionResult.getHealthCheckMetadata().getTags();
            boolean hasTags = tags != null && tags.size() > 0 && StringUtils.isNotBlank(tags.get(0));
            writer.print("<tr class=\"" + getClassForStatus(result.getStatus()) + "\">");
            String tooltipHint = getTooltipHint(executionResult.getHealthCheckMetadata());
            writer.print("<td><p title=\"" + escapeHtml(tooltipHint) + "\">"
                    + escapeHtml(executionResult.getHealthCheckMetadata().getTitle()) + "");
            if (hasTags) {
                writer.println("<br/><span style='color:gray'>" + escapeHtml(String.join(", ", tags)) + "</span>");
            }
            writer.println("</p></td>");
            writer.println("<td style='font-weight:bold;'>" + escapeHtml(result.getStatus().toString()) + "</td>");
            writer.println("<td>");
            boolean isFirst = true;

            boolean isSingleResult = isSingleResult(result);

            for (Entry entry : result) {
                if (!includeDebug && entry.isDebug()) {
                    continue;
                }

                if (isFirst) {
                    isFirst = false;
                } else {
                    writer.println("<br/>\n");
                }

                boolean showStatus = !isSingleResult && !entry.isDebug() && entry.getStatus() != Result.Status.OK;

                String message = escapeHtml(entry.getMessage());
                if (entry.isDebug()) {
                    message = "<span style='color:gray'/>" + message + "</span>";
                }
                writer.println((showStatus ? escapeHtml(entry.getStatus().toString()) + " " : "") + message);

                Exception exception = entry.getException();
                if (exception != null) {
                    writer.println("<span style='width:20px'/>" + escapeHtml(exception.toString()));
                    writer.println("<!--");
                    exception.printStackTrace(writer);
                    writer.println("-->");
                }
            }
            writer.println("</td>");
            Date finishedAt = executionResult.getFinishedAt();
            writer.println("<td>" + (isToday(finishedAt) ? dfShort.format(finishedAt) : dfLong.format(finishedAt)) + "</td>");
            writer.println("<td>" + msHumanReadable(executionResult.getElapsedTimeInMs()) + "</td>");

            writer.println("</tr>");
        }
        writer.println("</table>");

        writer.println("<div class='helpText'>");
        writer.println(getHtmlHelpText(paramList));
        writer.println("</div>");
        writer.println("</body></html>");

        return stringWriter.toString();

    }

    private String getTooltipHint(HealthCheckMetadata healthCheckMetadata) {
        List<String> hints = new ArrayList<>();
        ServiceReference<?> serviceReference = healthCheckMetadata.getServiceReference();
        Object compName = serviceReference.getProperty(ComponentConstants.COMPONENT_NAME);
        if(compName != null) {
            hints.add("Component Name: "+compName);
        }
        Bundle bundle = serviceReference.getBundle();
        if(bundle != null) {
            hints.add("Bundle: "+bundle.getSymbolicName());
        }
        return String.join(" ", hints);
    }

    private String getClassForStatus(final Result.Status status) {
        return "status" + status.name();
    }

    private boolean isSingleResult(final Result result) {
        int count = 0;
        for (Entry entry : result) {
            count++;
            if (count > 1) {
                return false;
            }
        }
        return true;
    }

    private boolean isToday(Date date) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date);
        boolean isToday = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        return isToday;

    }
    
    private String getHtmlHelpText(List<Param> paramList) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<h3>Supported URL parameters</h3>\n");
        for (Param p : paramList) {
            sb.append("<b>").append(p.name).append("</b>:");
            sb.append(escapeHtml(p.description));
            sb.append("<br/>");
        }
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if(text == null) {
            return null;
        }
        return text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}

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
package org.apache.felix.http.webconsoleplugin.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.runtime.dto.DTOConstants;
import org.osgi.service.servlet.runtime.dto.ErrorPageDTO;
import org.osgi.service.servlet.runtime.dto.FailedErrorPageDTO;
import org.osgi.service.servlet.runtime.dto.FailedFilterDTO;
import org.osgi.service.servlet.runtime.dto.FailedListenerDTO;
import org.osgi.service.servlet.runtime.dto.FailedResourceDTO;
import org.osgi.service.servlet.runtime.dto.FailedServletContextDTO;
import org.osgi.service.servlet.runtime.dto.FailedServletDTO;
import org.osgi.service.servlet.runtime.dto.FilterDTO;
import org.osgi.service.servlet.runtime.dto.ListenerDTO;
import org.osgi.service.servlet.runtime.dto.RequestInfoDTO;
import org.osgi.service.servlet.runtime.dto.ResourceDTO;
import org.osgi.service.servlet.runtime.dto.RuntimeDTO;
import org.osgi.service.servlet.runtime.dto.ServletContextDTO;
import org.osgi.service.servlet.runtime.dto.ServletDTO;
import org.owasp.encoder.Encode;

/**
 * This is a web console plugin.
 */
public class HttpServicePlugin extends HttpServlet {
    private static final String ATTR_TEST = "test";
    private static final String ATTR_MSG = "msg";
    private static final String ATTR_SUBMIT = "resolve";

    private final BundleContext context;

    private final HttpServiceRuntime runtime;

    public HttpServicePlugin(final BundleContext context, final HttpServiceRuntime runtime) {
        this.context = context;
        this.runtime = runtime;
    }

    @Override
    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {

        final String test = request.getParameter(ATTR_TEST);
        String msg = null;
        if (test != null && test.length() > 0) {

            final RequestInfoDTO dto = this.runtime.calculateRequestInfoDTO(test);

            final StringBuilder sb = new StringBuilder();
            if ( dto.servletDTO != null ) {
                sb.append("Servlet: ");
                sb.append(getValueAsString(dto.servletDTO.patterns));
                sb.append(" (");
                sb.append("service.id=");
                sb.append(String.valueOf(dto.servletDTO.serviceId));
                sb.append("), Filters: [");
                boolean first = true;
                for(final FilterDTO f : dto.filterDTOs) {
                    if ( first ) {
                        first = false;
                    } else {
                        sb.append(", ");
                    }
                    sb.append(f.serviceId);
                }
                sb.append("]");
            } else if ( dto.resourceDTO != null ) {
                sb.append("Resource: ");
                sb.append(getValueAsString(dto.resourceDTO.patterns));
                sb.append(" (");
                sb.append("service.id=");
                sb.append(String.valueOf(dto.resourceDTO.serviceId));
                sb.append("), Filters: [");
                boolean first = true;
                for(final FilterDTO f : dto.filterDTOs) {
                    if ( first ) {
                        first = false;
                    } else {
                        sb.append(", ");
                    }
                    sb.append(f.serviceId);
                }
                sb.append("]");
            } else {
                sb.append("<404>");
            }
            msg = sb.toString();
        }

        // finally redirect
        final String path = request.getContextPath() + request.getServletPath()
                + request.getPathInfo();
        final String redirectTo;
        if (msg == null) {
            redirectTo = path;
        } else {
            redirectTo = path + '?' + ATTR_MSG + '=' + encodeParam(msg) + '&'
                    + ATTR_TEST + '=' + encodeParam(test);
        }
        response.sendRedirect(redirectTo);
    }

    private String encodeParam(final String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // should never happen
            return value;
        }
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        final RuntimeDTO dto = this.runtime.getRuntimeDTO();

        final PrintWriter pw = resp.getWriter();

        String path = req.getContextPath() + req.getServletPath();
        if ( req.getPathInfo() != null ) {
            path = path + req.getPathInfo();
        }
        printForm(pw, req.getParameter(ATTR_TEST), req.getParameter(ATTR_MSG), path);

        printRuntimeDetails(pw, dto.serviceDTO);

        for(final ServletContextDTO ctxDto : dto.servletContextDTOs ) {
            printContextDetails(pw, ctxDto);
        }
        for(final FailedServletContextDTO ctxDto : dto.failedServletContextDTOs ) {
            printFailedContextDetails(pw, ctxDto);
        }
        printFailedServletDetails(pw, dto);
        printFailedFilterDetails(pw, dto);
        printFailedResourceDetails(pw, dto);
        printFailedErrorPageDetails(pw, dto);
        printFailedListenerDetails(pw, dto);

        pw.println("<br/>");
    }

    private void printForm(final PrintWriter pw, final String value, final String msg, final String path) {
        pw.println("<table class='content' cellpadding='0' cellspacing='0' width='100%'>");

        separatorHtml(pw);

        titleHtml(
                pw,
                "Test Servlet Resolution",
                "To test the servlet resolution, enter a relative URL into "
                        + "the field and click 'Resolve'.");

        pw.println("<tr class='content'>");
        pw.println("<td class='content'>Test</td>");
        pw.print("<td class='content' colspan='2'>");
        pw.print("<form method='POST' action='");
        pw.print(path);
        pw.print("'>");
        pw.print("<input type='text' name='" + ATTR_TEST + "' value='");
        if (value != null) {
            pw.print(Encode.forHtmlAttribute(value));
        }
        pw.println("' class='input' size='50'>");
        pw.println("&nbsp;&nbsp;<input type='submit' name='" + ATTR_SUBMIT
                + "' value='Resolve' class='submit'>");
        pw.print("</form>");
        pw.print("</td>");
        pw.println("</tr>");

        if (msg != null) {
            pw.println("<tr class='content'>");
            pw.println("<td class='content'>&nbsp;</td>");
            pw.print("<td class='content' colspan='2'>");
            pw.print(Encode.forHtmlContent(msg));
            pw.println("</td>");
            pw.println("</tr>");
        }
        pw.println("</table>");
    }

    private void titleHtml(PrintWriter pw, String title, String description) {
        pw.println("<tr class='content'>");
        pw.println("<th colspan='3'class='content container'>" + title
                + "</th>");
        pw.println("</tr>");

        if (description != null) {
            pw.println("<tr class='content'>");
            pw.println("<td colspan='3'class='content'>" + description
                    + "</th>");
            pw.println("</tr>");
        }
    }

    private void separatorHtml(PrintWriter pw) {
        pw.println("<tr class='content'>");
        pw.println("<td class='content' colspan='3'>&nbsp;</td>");
        pw.println("</tr>");
    }

    private String getValueAsString(final Object value) {
        if ( value.getClass().isArray() ) {
            if (value instanceof long[]) {
                return Arrays.toString((long[])value);
            } else if (value instanceof int[]) {
                return Arrays.toString((int[])value);
            } else if (value instanceof double[]) {
                return Arrays.toString((double[])value);
            } else if (value instanceof byte[]) {
                return Arrays.toString((byte[])value);
            } else if (value instanceof float[]) {
                return Arrays.toString((float[])value);
            } else if (value instanceof short[]) {
                return Arrays.toString((short[])value);
            } else if (value instanceof boolean[]) {
                return Arrays.toString((boolean[])value);
            } else if (value instanceof char[]) {
                return Arrays.toString((char[])value);
            } else {
                return Arrays.toString((Object[])value);
            }
        }
        return value.toString();
    }

    private void printRuntimeDetails(final PrintWriter pw, final ServiceReferenceDTO dto) {
        pw.println("<p class=\"statline ui-state-highlight\">${Runtime Properties}</p>");
        pw.println("<table class=\"nicetable\">");
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Name}</th>");
        pw.println("<th class=\"header\">${Value}</th>");
        pw.println("</tr></thead>");
        boolean odd = true;
        for(final Map.Entry<String, Object> prop : dto.properties.entrySet()) {
            odd = printRow(pw, odd, prop.getKey(), getValueAsString(prop.getValue()));
        }
        pw.println("</table>");
        pw.println("<br/>");
    }

    private boolean printRow(final PrintWriter pw, final boolean odd, final String...columns) {
        pw.print("<tr class=\"");
        if ( odd ) pw.print("odd"); else pw.print("even");
        pw.println(" ui-state-default\">");

        for(final String val : columns) {
            pw.print("<td>");
            if ( val != null ) {
                String text = Encode.forHtmlContent(val).replace("\n", "<br/>");
                int pos;
                while ( (pos = text.indexOf("${#link:")) != -1) {
                    final int endPos = text.indexOf("}", pos);
                    final int bundleId = Integer.valueOf(text.substring(pos + 8, endPos));
                    final int tokenEndPos = text.indexOf("${link#}", pos);

                    text = text.substring(0, pos) + "<a href=\"${appRoot}/bundles/" + String.valueOf(bundleId) + "\">" +
                           text.substring(endPos + 1, tokenEndPos) + "</a>" + text.substring(tokenEndPos + 8);
                }
                pw.print(text);
            }
            pw.println("</td>");
        }

        pw.println("</tr>");
        return !odd;
    }

    private String getContextPath(final String path) {
        if ( path.length() == 0 ) {
            return "<root>";
        }
        return path;
    }

    private boolean printServiceRankingRow(final PrintWriter pw, final long serviceId, final boolean odd) {
        int ranking = 0;
        final ServiceReference<?> ref = this.getServiceReference(serviceId);
        if ( ref != null ) {
            final Object obj = ref.getProperty(Constants.SERVICE_RANKING);
            if ( obj instanceof Integer)
            {
                ranking = (Integer)obj;
            }
        }
        return printRow(pw, odd, "${ranking}", String.valueOf(ranking));
    }

    private void printContextDetails(final PrintWriter pw, final ServletContextDTO dto) {
        pw.print("<p class=\"statline ui-state-highlight\">${Servlet Context} '");
        pw.print(Encode.forHtmlContent(dto.name));
        pw.println("'</p>");

        pw.println("<table class=\"nicetable\">");

        boolean odd = true;
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Name}</th>");
        pw.println("<th class=\"header\">${Value)}</th>");
        pw.println("</tr></thead>");
        odd = printRow(pw, odd, "${Path}", getContextPath(dto.contextPath));
        odd = printRow(pw, odd, "${service.id}", String.valueOf(dto.serviceId));
        odd = printServiceRankingRow(pw, dto.serviceId, odd);
        pw.println("</table>");

        printServletDetails(pw, dto);
        printFilterDetails(pw, dto);
        printResourceDetails(pw, dto);
        printErrorPageDetails(pw, dto);
        printListenerDetails(pw, dto);

        pw.println("<br/>");
    }

    private void printFailedContextDetails(final PrintWriter pw, final FailedServletContextDTO dto) {
        pw.print("<p class=\"statline ui-state-highlight\">${Servlet Context} '");
        pw.print(Encode.forHtmlContent(dto.name));
        pw.println("'</p>");

        pw.println("<table class=\"nicetable\">");

        boolean odd = true;
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Name}</th>");
        pw.println("<th class=\"header\">${Value)}</th>");
        pw.println("</tr></thead>");
        odd = printRow(pw, odd, "${Path}",
                dto.contextPath == null ? dto.contextPath : getContextPath(dto.contextPath));
        odd = printRow(pw, odd, "${reason}", getErrorText(dto.failureReason));
        odd = printRow(pw, odd, "${service.id}", String.valueOf(dto.serviceId));
        pw.println("</table>");
    }

    private void appendServiceRanking(final StringBuilder sb, final ServiceReference<?> ref) {
        int ranking = 0;
        if ( ref != null ) {
            final Object obj = ref.getProperty(Constants.SERVICE_RANKING);
            if ( obj instanceof Integer) {
                ranking = (Integer)obj;
            }
        }
        sb.append("${ranking} : ").append(String.valueOf(ranking)).append("\n");
    }

    private void printFilterDetails(final PrintWriter pw, final ServletContextDTO dto) {
        if ( dto.filterDTOs.length == 0 ) {
            return;
        }
        pw.print("<p class=\"statline ui-state-highlight\">${Servlet Context} '");
        pw.print(Encode.forHtmlContent(dto.name));
        pw.println("' ${Registered Filter Services}</p>");

        pw.println("<table class=\"nicetable\">");
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Pattern}</th>");
        pw.println("<th class=\"header\">${Filter}</th>");
        pw.println("<th class=\"header\">${Info}</th>");
        pw.println("</tr></thead>");

        boolean odd = true;
        for (final FilterDTO filter : dto.filterDTOs) {
            final ServiceReference<?> ref = this.getServiceReference(filter.serviceId);
            final StringBuilder sb = new StringBuilder();
            sb.append("${service.id} : ").append(String.valueOf(filter.serviceId)).append("\n");
            appendServiceRanking(sb, ref);
            sb.append("${async} : ").append(String.valueOf(filter.asyncSupported)).append("\n");
            sb.append("${dispatcher} : ").append(getValueAsString(filter.dispatcher)).append("\n");
            if ( ref != null ) {
                sb.append("${bundle} : ");
                sb.append("${#link:");
                sb.append(ref.getBundle().getBundleId());
                sb.append("}");
                sb.append(ref.getBundle().getSymbolicName());
                sb.append("${link#}\n");
            }

            final List<String> patterns = new ArrayList<>();
            patterns.addAll(Arrays.asList(filter.patterns));
            patterns.addAll(Arrays.asList(filter.regexs));
            for(final String name : filter.servletNames) {
                patterns.add("Servlet : " + name);
            }
            Collections.sort(patterns);
            final StringBuilder psb = new StringBuilder();
            for(final String p : patterns) {
                psb.append(p).append('\n');
            }
            odd = printRow(pw, odd, psb.toString(), filter.name, sb.toString());
        }
        pw.println("</table>");
    }

    private String getErrorText(final int reason) {
        switch ( reason ) {
        case DTOConstants.FAILURE_REASON_EXCEPTION_ON_INIT : return "Exception on init";
        case DTOConstants.FAILURE_REASON_NO_SERVLET_CONTEXT_MATCHING : return "No match";
        case DTOConstants.FAILURE_REASON_SERVICE_IN_USE : return "In use";
        case DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE : return "Not gettable";
        case DTOConstants.FAILURE_REASON_SERVLET_CONTEXT_FAILURE : return "Context failure";
        case DTOConstants.FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE : return "Shadowed";
        case DTOConstants.FAILURE_REASON_VALIDATION_FAILED : return "Invalid";
        default: return "unknown";
        }
    }
    private void printFailedFilterDetails(final PrintWriter pw, final RuntimeDTO dto) {
        if ( dto.failedFilterDTOs.length == 0 ) {
            return;
        }
        pw.print("<p class=\"statline ui-state-highlight\">${Failed Filter Services}</p>");

        pw.println("<table class=\"nicetable\">");
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Pattern}</th>");
        pw.println("<th class=\"header\">${Filter}</th>");
        pw.println("<th class=\"header\">${Info}</th>");
        pw.println("</tr></thead>");

        boolean odd = true;
        for (final FailedFilterDTO filter : dto.failedFilterDTOs) {
            final StringBuilder sb = new StringBuilder();
            sb.append("${reason} : ").append(getErrorText(filter.failureReason)).append("\n");
            final ServiceReference<?> ref = this.getServiceReference(filter.serviceId);
            sb.append("${service.id} : ").append(String.valueOf(filter.serviceId)).append("\n");
            appendServiceRanking(sb, ref);
            sb.append("${async} : ").append(String.valueOf(filter.asyncSupported)).append("\n");
            sb.append("${dispatcher} : ").append(getValueAsString(filter.dispatcher)).append("\n");
            if ( ref != null ) {
                sb.append("${bundle} : ");
                sb.append("${#link:");
                sb.append(ref.getBundle().getBundleId());
                sb.append("}");
                sb.append(ref.getBundle().getSymbolicName());
                sb.append("${link#}\n");
            }

            final List<String> patterns = new ArrayList<>();
            patterns.addAll(Arrays.asList(filter.patterns));
            patterns.addAll(Arrays.asList(filter.regexs));
            for(final String name : filter.servletNames) {
                patterns.add("Servlet : " + name);
            }
            Collections.sort(patterns);
            final StringBuilder psb = new StringBuilder();
            for(final String p : patterns) {
                psb.append(p).append('\n');
            }
            odd = printRow(pw, odd, psb.toString(), filter.name, sb.toString());
        }
        pw.println("</table>");
    }

    private ServiceReference<?> getServiceReference(final long serviceId) {
        if ( serviceId > 0 ) {
            try {
                final ServiceReference<?>[] ref = this.context.getServiceReferences((String)null, "(" + Constants.SERVICE_ID + "=" + String.valueOf(serviceId) + ")");
                if ( ref != null && ref.length > 0 ) {
                    return ref[0];
                }
            } catch (final InvalidSyntaxException e) {
                // ignore
            }
        }
        return null;
    }

    private void printServletDetails(final PrintWriter pw, final ServletContextDTO dto) {
        if ( dto.servletDTOs.length == 0 ) {
            return;
        }
        pw.print("<p class=\"statline ui-state-highlight\">${Servlet Context} '");
        pw.print(Encode.forHtmlContent(dto.name));
        pw.println("' ${Registered Servlet Services}</p>");

        pw.println("<table class=\"nicetable\">");
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Path}</th>");
        pw.println("<th class=\"header\">${Name}</th>");
        pw.println("<th class=\"header\">${Info}</th>");
        pw.println("</tr></thead>");

        boolean odd = true;
        for (final ServletDTO servlet : dto.servletDTOs) {
            final StringBuilder sb = new StringBuilder();
            final ServiceReference<?> ref = this.getServiceReference(servlet.serviceId);
            sb.append("${service.id} : ").append(String.valueOf(servlet.serviceId)).append("\n");
            appendServiceRanking(sb, ref);
            sb.append("${async} : ").append(String.valueOf(servlet.asyncSupported)).append("\n");
            if ( ref != null ) {
                sb.append("${bundle} : ");
                sb.append("${#link:");
                sb.append(ref.getBundle().getBundleId());
                sb.append("}");
                sb.append(ref.getBundle().getSymbolicName());
                sb.append("${link#}\n");
            }

            final StringBuilder psb = new StringBuilder();
            for(final String p : servlet.patterns) {
                psb.append(p).append('\n');
            }
            odd = printRow(pw, odd, psb.toString(), servlet.name, sb.toString());
        }
        pw.println("</table>");
    }

    private void printFailedServletDetails(final PrintWriter pw, final RuntimeDTO dto) {
        if ( dto.failedServletDTOs.length == 0 ) {
            return;
        }
        pw.print("<p class=\"statline ui-state-highlight\">${Failed Servlet Services}</p>");

        pw.println("<table class=\"nicetable\">");
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Path}</th>");
        pw.println("<th class=\"header\">${Name}</th>");
        pw.println("<th class=\"header\">${Info}</th>");
        pw.println("</tr></thead>");

        boolean odd = true;
        for (final FailedServletDTO servlet : dto.failedServletDTOs) {
            final StringBuilder sb = new StringBuilder();
            sb.append("${reason} : ").append(getErrorText(servlet.failureReason)).append("\n");
            final ServiceReference<?> ref = this.getServiceReference(servlet.serviceId);
            sb.append("${service.id} : ").append(String.valueOf(servlet.serviceId)).append("\n");
            appendServiceRanking(sb, ref);
            sb.append("${async} : ").append(String.valueOf(servlet.asyncSupported)).append("\n");
            if ( ref != null ) {
                sb.append("${bundle} : ");
                sb.append("${#link:");
                sb.append(ref.getBundle().getBundleId());
                sb.append("}");
                sb.append(ref.getBundle().getSymbolicName());
                sb.append("${link#}\n");
            }

            final StringBuilder psb = new StringBuilder();
            for(final String p : servlet.patterns) {
                psb.append(p).append('\n');
            }
            odd = printRow(pw, odd, psb.toString(), servlet.name, sb.toString());
        }
        pw.println("</table>");
    }

    private void printResourceDetails(final PrintWriter pw, final ServletContextDTO dto) {
        if ( dto.resourceDTOs.length == 0 ) {
            return;
        }
        pw.print("<p class=\"statline ui-state-highlight\">${Servlet Context} '");
        pw.print(Encode.forHtmlContent(dto.name));
        pw.println("' ${Registered Resource Services}</p>");

        pw.println("<table class=\"nicetable\">");
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Path}</th>");
        pw.println("<th class=\"header\">${Prefix}</th>");
        pw.println("<th class=\"header\">${Info}</th>");
        pw.println("</tr></thead>");

        boolean odd = true;
        for (final ResourceDTO rsrc : dto.resourceDTOs) {
            final StringBuilder sb = new StringBuilder();
            final ServiceReference<?> ref = this.getServiceReference(rsrc.serviceId);
            sb.append("${service.id} : ").append(String.valueOf(rsrc.serviceId)).append("\n");
            appendServiceRanking(sb, ref);
            if ( ref != null ) {
                sb.append("${bundle} : ");
                sb.append("${#link:");
                sb.append(ref.getBundle().getBundleId());
                sb.append("}");
                sb.append(ref.getBundle().getSymbolicName());
                sb.append("${link#}\n");
            }

            final StringBuilder psb = new StringBuilder();
            for(final String p : rsrc.patterns) {
                psb.append(p).append('\n');
            }
            odd = printRow(pw, odd, psb.toString(), rsrc.prefix, sb.toString());
        }
        pw.println("</table>");
    }

    private void printFailedResourceDetails(final PrintWriter pw, final RuntimeDTO dto) {
        if ( dto.failedResourceDTOs.length == 0 ) {
            return;
        }
        pw.print("<p class=\"statline ui-state-highlight\">${Failed Resource Services}</p>");

        pw.println("<table class=\"nicetable\">");
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Path}</th>");
        pw.println("<th class=\"header\">${Prefix}</th>");
        pw.println("<th class=\"header\">${Info}</th>");
        pw.println("</tr></thead>");

        boolean odd = true;
        for (final FailedResourceDTO rsrc : dto.failedResourceDTOs) {
            final StringBuilder sb = new StringBuilder();
            sb.append("${reason} : ").append(getErrorText(rsrc.failureReason)).append("\n");
            final ServiceReference<?> ref = this.getServiceReference(rsrc.serviceId);
            sb.append("${service.id} : ").append(String.valueOf(rsrc.serviceId)).append("\n");
            appendServiceRanking(sb, ref);
            if ( ref != null ) {
                sb.append("${bundle} : ");
                sb.append("${#link:");
                sb.append(ref.getBundle().getBundleId());
                sb.append("}");
                sb.append(ref.getBundle().getSymbolicName());
                sb.append("${link#}\n");
            }

            final StringBuilder psb = new StringBuilder();
            for(final String p : rsrc.patterns) {
                psb.append(p).append('\n');
            }
            odd = printRow(pw, odd, psb.toString(), rsrc.prefix, sb.toString());
        }
        pw.println("</table>");
    }

    private void printErrorPageDetails(final PrintWriter pw, final ServletContextDTO dto) {
        if ( dto.errorPageDTOs.length == 0 ) {
            return;
        }
        pw.print("<p class=\"statline ui-state-highlight\">${Servlet Context} '");
        pw.print(Encode.forHtmlContent(dto.name));
        pw.println("' ${Registered Error Pages}</p>");

        pw.println("<table class=\"nicetable\">");
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Path}</th>");
        pw.println("<th class=\"header\">${Name}</th>");
        pw.println("<th class=\"header\">${Info}</th>");
        pw.println("</tr></thead>");

        boolean odd = true;
        for (final ErrorPageDTO ep : dto.errorPageDTOs) {
            final StringBuilder sb = new StringBuilder();
            final ServiceReference<?> ref = this.getServiceReference(ep.serviceId);
            sb.append("${service.id} : ").append(String.valueOf(ep.serviceId)).append("\n");
            appendServiceRanking(sb, ref);
            sb.append("${async} : ").append(String.valueOf(ep.asyncSupported)).append("\n");
            if ( ref != null ) {
                sb.append("${bundle} : ");
                sb.append("${#link:");
                sb.append(ref.getBundle().getBundleId());
                sb.append("}");
                sb.append(ref.getBundle().getSymbolicName());
                sb.append("${link#}\n");
            }

            final StringBuilder psb = new StringBuilder();
            for(final long p : ep.errorCodes) {
                psb.append(p).append('\n');
            }
            for(final String p : ep.exceptions) {
                psb.append(p).append('\n');
            }
            odd = printRow(pw, odd, psb.toString(), ep.name, sb.toString());
        }
        pw.println("</table>");
    }

    private void printFailedErrorPageDetails(final PrintWriter pw, final RuntimeDTO dto) {
        if ( dto.failedErrorPageDTOs.length == 0 ) {
            return;
        }
        pw.print("<p class=\"statline ui-state-highlight\">${Registered Error Pages}</p>");

        pw.println("<table class=\"nicetable\">");
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Path}</th>");
        pw.println("<th class=\"header\">${Name}</th>");
        pw.println("<th class=\"header\">${Info}</th>");
        pw.println("</tr></thead>");

        boolean odd = true;
        for (final FailedErrorPageDTO ep : dto.failedErrorPageDTOs) {
            final StringBuilder sb = new StringBuilder();
            sb.append("${reason} : ").append(getErrorText(ep.failureReason)).append("\n");
            final ServiceReference<?> ref = this.getServiceReference(ep.serviceId);
            sb.append("${service.id} : ").append(String.valueOf(ep.serviceId)).append("\n");
            appendServiceRanking(sb, ref);
            sb.append("${async} : ").append(String.valueOf(ep.asyncSupported)).append("\n");
            if ( ref != null ) {
                sb.append("${bundle} : ");
                sb.append("${#link:");
                sb.append(ref.getBundle().getBundleId());
                sb.append("}");
                sb.append(ref.getBundle().getSymbolicName());
                sb.append("${link#}\n");
            }

            final StringBuilder psb = new StringBuilder();
            for(final long p : ep.errorCodes) {
                psb.append(p).append('\n');
            }
            for(final String p : ep.exceptions) {
                psb.append(p).append('\n');
            }
            odd = printRow(pw, odd, psb.toString(), ep.name, sb.toString());
        }
        pw.println("</table>");
    }

    private void printListenerDetails(final PrintWriter pw, final ServletContextDTO dto) {
        if ( dto.listenerDTOs.length == 0 ) {
            return;
        }
        pw.print("<p class=\"statline ui-state-highlight\">${Servlet Context} '");
        pw.print(Encode.forHtmlContent(dto.name));
        pw.println("' ${Registered Listeners}</p>");

        pw.println("<table class=\"nicetable\">");
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Type}</th>");
        pw.println("<th class=\"header\">${Info}</th>");
        pw.println("</tr></thead>");

        boolean odd = true;
        for (final ListenerDTO ep : dto.listenerDTOs) {
            final StringBuilder sb = new StringBuilder();
            final ServiceReference<?> ref = this.getServiceReference(ep.serviceId);
            sb.append("${service.id} : ").append(String.valueOf(ep.serviceId)).append("\n");
            appendServiceRanking(sb, ref);
            if ( ref != null ) {
                sb.append("${bundle} : ");
                sb.append("${#link:");
                sb.append(ref.getBundle().getBundleId());
                sb.append("}");
                sb.append(ref.getBundle().getSymbolicName());
                sb.append("${link#}\n");
            }
            final StringBuilder tsb = new StringBuilder();
            for(final String t : ep.types) {
                tsb.append(t).append('\n');
            }
            odd = printRow(pw, odd, tsb.toString(), sb.toString());
        }
        pw.println("</table>");
    }

    private void printFailedListenerDetails(final PrintWriter pw, final RuntimeDTO dto) {
        if ( dto.failedListenerDTOs.length == 0 ) {
            return;
        }
        pw.print("<p class=\"statline ui-state-highlight\">${Failed Listeners}</p>");

        pw.println("<table class=\"nicetable\">");
        pw.println("<thead><tr>");
        pw.println("<th class=\"header\">${Type}</th>");
        pw.println("<th class=\"header\">${Info}</th>");
        pw.println("</tr></thead>");

        boolean odd = true;
        for (final FailedListenerDTO ep : dto.failedListenerDTOs) {
            final StringBuilder sb = new StringBuilder();
            sb.append("${reason} : ").append(getErrorText(ep.failureReason)).append("\n");
            final ServiceReference<?> ref = this.getServiceReference(ep.serviceId);
            sb.append("${service.id} : ").append(String.valueOf(ep.serviceId)).append("\n");
            appendServiceRanking(sb, ref);
            if ( ref != null ) {
                sb.append("${bundle} : ");
                sb.append("${#link:");
                sb.append(ref.getBundle().getBundleId());
                sb.append("}");
                sb.append(ref.getBundle().getSymbolicName());
                sb.append("${link#}\n");
            }
            final StringBuilder tsb = new StringBuilder();
            for(final String t : ep.types) {
                tsb.append(t).append('\n');
            }
            odd = printRow(pw, odd, tsb.toString(), sb.toString());
        }
        pw.println("</table>");
    }
}

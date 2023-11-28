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
package org.apache.felix.hc.webconsole.impl;

import static org.apache.felix.hc.api.FormattingResultLog.msHumanReadable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.ResultLog;
import org.apache.felix.hc.api.execution.HealthCheckExecutionOptions;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckExecutor;
import org.apache.felix.hc.api.execution.HealthCheckMetadata;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/** Webconsole plugin to execute health check services */
@Component(service=Servlet.class, property={
        org.osgi.framework.Constants.SERVICE_DESCRIPTION + "=Apache Felix Health Check Web Console Plugin",
        "felix.webconsole.label=" + HealthCheckWebconsolePlugin.LABEL,
        "felix.webconsole.title="+ HealthCheckWebconsolePlugin.TITLE,
        "felix.webconsole.category="+ HealthCheckWebconsolePlugin.CATEGORY,
        "felix.webconsole.css=/healthcheck/res/ui/healthcheck.css"
})
@SuppressWarnings("serial")
public class HealthCheckWebconsolePlugin extends HttpServlet {

    public static final String TITLE = "Health Check";
    public static final String LABEL = "healthcheck";
    public static final String CATEGORY = "Main";
    public static final String PARAM_TAGS = "tags";
    public static final String PARAM_DEBUG = "debug";
    public static final String PARAM_QUIET = "quiet";
    public static final String PARAM_SHOWLIST = "showList";
    
    
    public static final String PARAM_FORCE_INSTANT_EXECUTION = "forceInstantExecution";
    public static final String PARAM_COMBINE_TAGS_WITH_OR = "combineTagsWithOr";
    public static final String PARAM_OVERRIDE_GLOBAL_TIMEOUT = "overrideGlobalTimeout";

    public static final String HC_FILTER_OBJECT_CLASS = "(|(objectClass="+HealthCheck.class.getName()+")(objectClass=org.apache.sling.hc.api.HealthCheck))";
    
    @Reference
    private HealthCheckExecutor healthCheckExecutor;

    private BundleContext bundleContext;
    
    @Activate
    protected void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
    
    /** Serve static resource if applicable, and return true in that case */
    private boolean getStaticResource(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final String pathInfo = req.getPathInfo();
        if (pathInfo!= null && pathInfo.contains("res/ui")) {
            final String prefix = "/" + LABEL;
            final InputStream is = getClass().getResourceAsStream(pathInfo.substring(prefix.length()));
            if (is == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, pathInfo);
            } else {
                final OutputStream os = resp.getOutputStream();
                try {
                    final byte [] buffer = new byte[16384];
                    int n=0;
                    while( (n = is.read(buffer, 0, buffer.length)) > 0) {
                        os.write(buffer, 0, n);
                    }
                } finally {
                    try {
                        is.close();
                    } catch ( final IOException ignore ) {
                        // ignore
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        if (getStaticResource(req, resp)) {
            return;
        }

        final String tags = getParam(req, PARAM_TAGS, null);
        final boolean debug = Boolean.valueOf(getParam(req, PARAM_DEBUG, "false"));
        final boolean quiet = Boolean.valueOf(getParam(req, PARAM_QUIET, "false"));
        final boolean combineTagsWithOr = Boolean.valueOf(getParam(req, PARAM_COMBINE_TAGS_WITH_OR, "false"));
        final boolean forceInstantExecution = Boolean.valueOf(getParam(req, PARAM_FORCE_INSTANT_EXECUTION, "false"));
        final String overrideGlobalTimeoutStr = getParam(req, PARAM_OVERRIDE_GLOBAL_TIMEOUT, "");

        final PrintWriter pw = resp.getWriter();
        final WebConsoleHelper c = new WebConsoleHelper(pw);

        if(Boolean.valueOf(req.getParameter(PARAM_SHOWLIST))) {
            doHealthCheckList(pw);
            return;
        }
        
        doForm(pw, tags, debug, quiet, combineTagsWithOr, forceInstantExecution, overrideGlobalTimeoutStr);

        // Execute health checks only if tags are specified (even if empty)
        if (tags != null) {
            HealthCheckExecutionOptions options = new HealthCheckExecutionOptions();
            options.setCombineTagsWithOr(combineTagsWithOr);
            options.setForceInstantExecution(forceInstantExecution);
            try {
                options.setOverrideGlobalTimeout(Integer.valueOf(overrideGlobalTimeoutStr));
            } catch (NumberFormatException nfe) {
                // override not set in UI
            }

            HealthCheckSelector selector = !isBlank(tags) ? HealthCheckSelector.tags(tags.split(",")) : HealthCheckSelector.empty();
            Collection<HealthCheckExecutionResult> results = healthCheckExecutor.execute(selector, options);

            pw.println("<table class='content healthcheck' cellpadding='0' cellspacing='0' width='100%'>");
            int total = 0;
            int failed = 0;
            for (final HealthCheckExecutionResult exR : results) {

                final Result r = exR.getHealthCheckResult();
                total++;
                if (!r.isOk()) {
                    failed++;
                }
                if (!quiet || !r.isOk()) {
                    renderResult(pw, exR, debug);
                }

            }
            c.titleHtml("Summary", total + " HealthCheck executed, " + failed + " failures");
            pw.println("</table>");
            pw.println("<br/><br/>");

        }
    }
    
    private static boolean isBlank(final CharSequence cs) {
        return cs == null || cs.chars().allMatch(Character::isWhitespace);
    }

    void renderResult(final PrintWriter pw, final HealthCheckExecutionResult exResult, final boolean debug)  throws IOException {
        final Result result = exResult.getHealthCheckResult();
        final WebConsoleHelper c = new WebConsoleHelper(pw);

        final StringBuilder status = new StringBuilder();

        status.append("Tags: ").append(exResult.getHealthCheckMetadata().getTags());
        status.append(" Finished: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(exResult.getFinishedAt()) + " after "
                + msHumanReadable(exResult.getElapsedTimeInMs()));

        c.titleHtml(exResult.getHealthCheckMetadata().getTitle(), null);

        c.tr();
        c.tdContent();
        c.writer().print(c.escapeHtmlContent(status.toString()));
        c.writer().print("<br/>Result: <span class='resultOk");
        c.writer().print(result.isOk());
        c.writer().print("'>");
        c.writer().print(c.escapeHtmlContent(result.getStatus().toString()));
        c.writer().print("</span>");
        c.closeTd();
        c.closeTr();

        c.tr();
        c.tdContent();
        for(final ResultLog.Entry e : result) {
            if (!debug && e.isDebug()) {
                continue;
            }
            c.writer().print("<div class='log");
            c.writer().print(e.isDebug() ? "DEBUG" : e.getStatus().toString());
            c.writer().print("'>");
            c.writer().print(c.escapeHtmlContent(e.getStatus().toString()));
            c.writer().print(' ');
            c.writer().print(c.escapeHtmlContent(e.getMessage()));
            if (e.getException() != null) {
                c.writer().print(" ");
                c.writer().print(c.escapeHtmlContent(e.getException().toString()));
            }
            c.writer().println("</div>");
        }
        c.closeTd();
    }

    private void doHealthCheckList(final PrintWriter pw) throws IOException {
        final WebConsoleHelper c = new WebConsoleHelper(pw);    
        try {

            pw.println("<br/>");
            pw.println("<table id=\"healthCheckList\" class=\"tablesorter nicetable\">");
            pw.println("<thead><tr><th>Name</th><th>Tags</th><th>Properties</th><th>Links</th><th>Bundle</th></thead><tbody>");
            
            ServiceReference<?>[] serviceReferences = bundleContext.getServiceReferences((String) null, HC_FILTER_OBJECT_CLASS);
            for (ServiceReference<?> serviceReference : serviceReferences) {
                HealthCheckMetadata metadata = new HealthCheckMetadata(serviceReference);
                
                pw.println("<tr>");
                pw.print("<td>");
                pw.print(c.escapeHtmlContent(metadata.getTitle()));
                pw.println("</td>");

                pw.println("<td>");
                for(String tag: metadata.getTags()) {
                    final String link = LABEL+"?tags="+tag;
                    pw.print("<a href=\"");
                    pw.print(c.escapeHtmlAttr(link));
                    pw.print("\">");
                    pw.print(c.escapeHtmlContent(tag));
                    pw.println("</a><br/>");
                }
                pw.println("</td>");
                
                List<String> links = new ArrayList<>();

                pw.println("<td>");
                String[] propertyKeys = serviceReference.getPropertyKeys();
                if(propertyKeys!=null) {
                    for(String propertyKey: propertyKeys) {
                        if(propertyKey.equals(HealthCheck.NAME)
                            || propertyKey.equals(HealthCheck.TAGS)
                            || propertyKey.equals(Constants.OBJECTCLASS) 
                            || propertyKey.equals(ComponentConstants.COMPONENT_ID) 
                            ) {
                            continue;
                        }
                        Object value = serviceReference.getProperty(propertyKey);
                        if(value.getClass().isArray()) {
                            value = Arrays.asList((Object[]) value);
                        }
                        if(HealthCheck.MBEAN_NAME.equals(propertyKey)) {
                            String link = "jmx/org.apache.felix.healthcheck%3Aname%3D"+value+"%2Ctype%3DHealthCheck";
                            links.add("<a href=\"".concat(c.escapeHtmlAttr(link)).concat("\">JMX Bean ").concat(c.escapeHtmlContent(value.toString())).concat("</a>"));
                            continue;
                        }
                        if(ComponentConstants.COMPONENT_NAME.equals(propertyKey)) {
                            String link = "components/".concat(value.toString());
                            links.add("<a href=\"".concat(c.escapeHtmlAttr(link)).concat("\">Component ").concat(c.escapeHtmlContent(value.toString())).concat("</a>"));
                            continue;
                        }
                        if(Constants.SERVICE_ID.equals(propertyKey)) {
                            String link = "services/"+value;
                            links.add("<a href=\"".concat(c.escapeHtmlAttr(link)).concat("\">Service ").concat(c.escapeHtmlContent(value.toString())).concat("</a>"));
                            continue;
                        } else if(propertyKey.startsWith("service.")) {
                            continue;
                        }
                        pw.print(c.escapeHtmlContent(propertyKey+" = "+value));
                        pw.println("<br/>");
                    }
                }
                pw.println("</td>");
                pw.println("<td>"+String.join("<br/>", links)+"</td>");

                pw.println("<td>");
                String symbolicBundleName = serviceReference.getBundle().getSymbolicName();
                String link = "bundles/"+symbolicBundleName;
                pw.println("<a href=\"".concat(c.escapeHtmlAttr(link)).concat("\">").concat(c.escapeHtmlContent(symbolicBundleName)).concat("</a>"));
                pw.println("</td>");

                pw.println("</tr>");
            }

            pw.println("</thead></table>");

        } catch (InvalidSyntaxException e) {
            throw new IllegalStateException("Could not render list of health checks: "+e, e);
        }
    }

    private void doForm(final PrintWriter pw,
            final String tags,
            final boolean debug,
            final boolean quiet,
            final boolean combineTagsWithOr,
            final boolean forceInstantExecution,
            final String overrideGlobalTimeoutStr) throws IOException {
        final WebConsoleHelper c = new WebConsoleHelper(pw);
        pw.print("<form method='get'>");
        pw.println("<table class='content' cellpadding='0' cellspacing='0' width='100%'>");
        c.titleHtml(TITLE, "Enter tags to selected health checks to be executed. Leave empty to execute default checks or use '*' to execute all checks."
                + " Prefix a tag with a minus sign (-) to omit checks having that tag (can be also used in combination with '*', e.g. '*,-excludedtag').");

        c.tr();
        c.tdLabel("Health Check Tags (comma-separated)");
        c.tdContent();
        c.writer().print("<input type='text' name='" + PARAM_TAGS + "' value='");
        if ( tags != null ) {
            c.writer().print(c.escapeHtmlAttr(tags));
        }
        c.writer().println("' class='input' size='80'> <a href='"+LABEL+"?"+PARAM_SHOWLIST+"=true'>Show list</a>");
        c.closeTd();
        c.closeTr();


        c.tr();
        c.tdLabel("");
        c.tdContent();
        c.writer().print("<table id='settingsTable'>");
        c.writer().print("<tr>");
        c.writer().print("<td>");
        c.writer().print("<input type='checkbox' name='" + PARAM_COMBINE_TAGS_WITH_OR + "' class='input' value='true'");
        if (combineTagsWithOr) {
            c.writer().print(" checked=true");
        }
        c.writer().println(">");
        c.writer().print("</td>");
        c.writer().print("<td>Combine tags with logical 'OR' instead of the default 'AND'</td>");

        c.writer().print("<td>");
        c.writer().print("<input type='checkbox' name='" + PARAM_DEBUG + "' class='input' value='true'");
        if (debug) {
            c.writer().print(" checked=true");
        }
        c.writer().println(">");
        c.writer().print("</td>");
        c.writer().print("<td>Show DEBUG logs</td>");
        c.writer().print("</tr>");
        
        c.writer().print("<tr>");
        c.writer().print("<td>");
        c.writer().print("<input type='checkbox' name='" + PARAM_QUIET + "' class='input' value='true'");
        if (quiet) {
            c.writer().print(" checked=true");
        }
        c.writer().println(">");
        c.writer().print("</td>");
        c.writer().print("<td>Show failed checks only</td>");

        c.writer().print("<td>");
        c.writer().print("<input type='checkbox' name='" + PARAM_FORCE_INSTANT_EXECUTION + "' class='input' value='true'");
        if (forceInstantExecution) {
            c.writer().print(" checked=true");
        }
        c.writer().println(">");
        c.writer().print("</td>");
        c.writer().print("<td>Force instant execution (no cache, async checks are executed)</td>");
        c.writer().print("</tr>");
        
        c.writer().print("<tr>");
        c.writer().print("<td colspan='4'>Override global timeout ");
        c.writer().print("<input type='text' name='" + PARAM_OVERRIDE_GLOBAL_TIMEOUT + "' value='");
        if (overrideGlobalTimeoutStr != null) {
            c.writer().print(c.escapeHtmlAttr(overrideGlobalTimeoutStr));
        }
        c.writer().println("' class='input' size='10'> ms");
        c.writer().print("</td>");

        c.writer().print("</tr>");

        c.writer().print("</table>");
        
        c.closeTd();
        c.closeTr();

        c.tr();
        c.tdLabel("");
        c.tdContent();
        c.writer().println("<input type='submit' value=' Execute '/>  <a href='configMgr/org.apache.felix.hc.core.impl.executor.HealthCheckExecutorImpl'>Configure executor</a>");
        c.closeTd();
        c.closeTr();

        c.writer().println("</table></form>");
    }

    private String getParam(final HttpServletRequest req, final String name, final String defaultValue) {
        String result = req.getParameter(name);
        if(result == null) {
            result = defaultValue;
        }
        return result;
    }
}

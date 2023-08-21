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
package org.apache.felix.webconsole.plugins.deppack.internal;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.apache.felix.webconsole.servlet.AbstractServlet;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.apache.felix.webconsole.servlet.RequestVariableResolver;
import org.apache.felix.utils.json.JSONWriter;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.deploymentadmin.DeploymentPackage;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * DepPackServlet provides a plugin for managing deployment admin packages.
 */
class WebConsolePlugin extends AbstractServlet
{

    private static final String LABEL = "deppack";
    private static final String TITLE = "%deppack.pluginTitle";
    private static final String CSS[] = { "/" + LABEL + "/res/plugin.css" };
    private static final String CATEGORY = "OSGi";

    //
    private static final String ACTION_DEPLOY = "deploydp";
    private static final String ACTION_UNINSTALL = "uninstalldp";
    private static final String PARAMETER_PCK_FILE = "pckfile";

    // templates
    private final String TEMPLATE;

    private final DeploymentAdmin admin;

    private volatile ServiceRegistration<Servlet> serviceRegistration;

    /** Default constructor */
    WebConsolePlugin(DeploymentAdmin admin)
    {
        // load templates
        try {
        TEMPLATE = readTemplateFile("/res/plugin.html");
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to load template file");
        }
        this.admin = admin;
    }

    public String getCategory()
    {
        return CATEGORY;
    }

    public void register(final BundleContext context) {
        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(ServletConstants.PLUGIN_LABEL, LABEL);
        props.put(ServletConstants.PLUGIN_TITLE, TITLE);
        props.put(ServletConstants.PLUGIN_CATEGORY, CATEGORY);
        props.put(ServletConstants.PLUGIN_CSS_REFERENCES, CSS);

        this.serviceRegistration = context.registerService(Servlet.class, this, props);
    }

    public void unregister() {
        if ( this.serviceRegistration != null) {
            try {
                this.serviceRegistration.unregister();
            } catch ( final IllegalStateException ise) {
                // ignore
            }
            this.serviceRegistration = null;
        }
    }

    /**
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        // get the uploaded data
        final String action = req.getParameter("action");
        if (ACTION_DEPLOY.equals(action))
        {
            Collection<Part> parts = req.getParts();
            for (Part part : parts)
            {
                if (PARAMETER_PCK_FILE.equals(part.getName()))
                {
                    try
                    {
                        admin.installDeploymentPackage(part.getInputStream());
                        final String uri = req.getRequestURI();
                        resp.sendRedirect(uri);
                        return;
                    }
                    catch ( /*Deployment*/Exception e)
                    {
                        throw new ServletException("Unable to deploy package.", e);
                    }
                }
            }
        }
        else if (ACTION_UNINSTALL.equals(action))
        {
            final String pckId = req.getPathInfo().substring(
                req.getPathInfo().lastIndexOf('/') + 1);
            if (pckId != null && pckId.length() > 0)
            {
                try
                {
                    final DeploymentPackage pck = admin.getDeploymentPackage(pckId);
                    if (pck != null)
                    {
                        pck.uninstall();
                    }
                }
                catch ( /*Deployment*/Exception e)
                {
                    throw new ServletException("Unable to undeploy package.", e);
                }
            }

            final PrintWriter pw = resp.getWriter();
            pw.println("{ \"reload\":true }");
            return;
        }
        throw new ServletException("Unknown action: " + action);
    }

    /**
     * @see org.apache.felix.webconsole.AbstractWebConsolePlugin#renderContent(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public void renderContent(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        StringWriter w = new StringWriter();
        PrintWriter w2 = new PrintWriter(w);
        JSONWriter jw = new JSONWriter(w2);
        jw.object();
        if (null == admin)
        {
            jw.key("error");
            jw.value(true);
        }
        else
        {
            final DeploymentPackage[] packages = admin.listDeploymentPackages();
            jw.key("data");

            jw.array();
            for (int i = 0; i < packages.length; i++)
            {
                packageInfoJson(jw, packages[i]);
            }
            jw.endArray();

        }
        jw.endObject();


        // prepare variables
        RequestVariableResolver vars = this.getVariableResolver(request);
        vars.put("__data__", w.toString());

        response.getWriter().print(TEMPLATE);
    }

    private static final void packageInfoJson(JSONWriter jw, DeploymentPackage pack)
        throws IOException
    {
        jw.object();
        jw.key("id");
        jw.value(pack.getName());
        jw.key("name");
        jw.value(pack.getName());
        jw.key("state");
        jw.value(pack.getVersion());

        jw.key("actions");
        jw.array();

        jw.object();
        jw.key("enabled");
        jw.value(true);
        jw.key("name");
        jw.value("Uninstall");
        jw.key("link");
        jw.value(ACTION_UNINSTALL);
        jw.endObject();

        jw.endArray();

        jw.key("props");
        jw.array();
        jw.object();
        jw.key("key");
        jw.value("Package Name");
        jw.key("value");
        jw.value(pack.getName());
        jw.endObject();

        jw.object();
        jw.key("key");
        jw.value("Version");
        jw.key("value");
        jw.value(pack.getVersion());
        jw.endObject();

        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < pack.getBundleInfos().length; i++)
        {
            buffer.append(pack.getBundleInfos()[i].getSymbolicName());
            buffer.append(" - ");
            buffer.append(pack.getBundleInfos()[i].getVersion());
            buffer.append("<br/>");
        }
        jw.object();
        jw.key("key");
        jw.value("Bundles");
        jw.key("value");
        jw.value(buffer.toString());
        jw.endObject();

        jw.endArray();

        jw.endObject();
    }

}

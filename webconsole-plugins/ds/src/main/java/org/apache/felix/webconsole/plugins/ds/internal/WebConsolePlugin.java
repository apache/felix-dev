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
package org.apache.felix.webconsole.plugins.ds.internal;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.webconsole.DefaultVariableResolver;
import org.apache.felix.webconsole.SimpleWebConsolePlugin;
import org.apache.felix.webconsole.WebConsoleUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.ReferenceDTO;
import org.osgi.service.component.runtime.dto.SatisfiedReferenceDTO;
import org.osgi.util.promise.Promise;

/**
 * ComponentsServlet provides a plugin for managing Service Components Runtime.
 */
class WebConsolePlugin extends SimpleWebConsolePlugin
{

    private static final long serialVersionUID = 1L;

    private static final String LABEL = "components"; //$NON-NLS-1$
    private static final String TITLE = "%components.pluginTitle"; //$NON-NLS-1$
    private static final String CATEGORY = "OSGi"; //$NON-NLS-1$
    private static final String CSS[] = { "/res/ui/bundles.css" }; // yes, it's correct! //$NON-NLS-1$
    private static final String RES = "/" + LABEL + "/res/"; //$NON-NLS-1$ //$NON-NLS-2$

    // actions
    private static final String OPERATION = "action"; //$NON-NLS-1$
    private static final String OPERATION_ENABLE = "enable"; //$NON-NLS-1$
    private static final String OPERATION_DISABLE = "disable"; //$NON-NLS-1$
    //private static final String OPERATION_CONFIGURE = "configure";

    // templates
    private final String TEMPLATE;

    private volatile ConfigurationSupport optionalSupport;

    private final ServiceComponentRuntime runtime;

    /** Default constructor */
    WebConsolePlugin(final ServiceComponentRuntime service)
    {
        super(LABEL, TITLE, CSS);

        this.runtime = service;
        // load templates
        TEMPLATE = readTemplateFile("/res/plugin.html"); //$NON-NLS-1$
    }


    @Override
    public void deactivate() {
        if ( this.optionalSupport != null )
        {
            this.optionalSupport.close();
            this.optionalSupport = null;
        }
        super.deactivate();
    }


    @Override
    public void activate(final BundleContext bundleContext)
    {
        super.activate(bundleContext);
        this.optionalSupport = new ConfigurationSupport(bundleContext);
    }


    @Override
    public String getCategory()
    {
        return CATEGORY;
    }

    private void wait(final Promise<Void> p )
    {
        while ( !p.isDone() )
        {
            try
            {
                Thread.sleep(5);
            }
            catch (final InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        final String op = request.getParameter(OPERATION);
        RequestInfo reqInfo = new RequestInfo(request, true);
        if (reqInfo.componentRequested)
        {
            boolean found = false;
            if ( reqInfo.component != null )
            {
                if (OPERATION_ENABLE.equals(op))
                {
                    wait(this.runtime.enableComponent(reqInfo.component.description));
                    reqInfo = new RequestInfo(request, false);
                    found = true;
                }
                else if ( OPERATION_DISABLE.equals(op) )
                {
                    wait(this.runtime.disableComponent(reqInfo.component.description));
                    found = true;
                }
            }
            if ( !found )
            {
                response.sendError(404);
                return;
            }
        }
        else
        {
            response.sendError(500);
            return;
        }

        final PrintWriter pw = response.getWriter();
        response.setContentType("application/json"); //$NON-NLS-1$
        response.setCharacterEncoding("UTF-8"); //$NON-NLS-1$
        renderResult(pw, reqInfo, null);
    }

    /**
     * @see org.apache.felix.webconsole.AbstractWebConsolePlugin#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        String path = request.getPathInfo();
        // don't process if this is request to load a resource
        if (!path.startsWith(RES))
        {
            final RequestInfo reqInfo = new RequestInfo(request, true);
            if (reqInfo.component == null && reqInfo.componentRequested)
            {
                response.sendError(404);
                return;
            }
            if (reqInfo.extension.equals("json")) //$NON-NLS-1$
            {
                response.setContentType("application/json"); //$NON-NLS-1$
                response.setCharacterEncoding("UTF-8"); //$NON-NLS-1$

                this.renderResult(response.getWriter(), reqInfo, reqInfo.component);

                // nothing more to do
                return;
            }
        }
        super.doGet(request, response);
    }

    /**
     * @see org.apache.felix.webconsole.AbstractWebConsolePlugin#renderContent(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void renderContent(HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        // get request info from request attribute
        final RequestInfo reqInfo = getRequestInfo(request);

        StringWriter w = new StringWriter();
        PrintWriter w2 = new PrintWriter(w);
        renderResult(w2, reqInfo, reqInfo.component);

        // prepare variables
        DefaultVariableResolver vars = ((DefaultVariableResolver) WebConsoleUtil.getVariableResolver(request));
        vars.put("__drawDetails__", reqInfo.componentRequested ? Boolean.TRUE : Boolean.FALSE); //$NON-NLS-1$
        vars.put("__data__", w.toString()); //$NON-NLS-1$

        response.getWriter().print(TEMPLATE);

    }

    private void renderResult(final PrintWriter pw, RequestInfo info, final ComponentConfigurationDTO component)
            throws IOException
    {
        final JSONWriter jw = new JSONWriter(pw);
        jw.object();

        jw.key("status"); //$NON-NLS-1$
        jw.value(info.configurations.size());
        if ( !info.configurations.isEmpty() )
        {
            // render components
            jw.key("data"); //$NON-NLS-1$
            jw.array();
            if (component != null)
            {
                if ( component.state == -1 )
                {
                    component(jw, component.description, null, true);
                }
                else
                {
                    component(jw, component.description, component, true);
                }
            }
            else
            {
                for( final ComponentDescriptionDTO cd : info.disabled )
                {
                    component(jw, cd, null, false);
                }
                for (final ComponentConfigurationDTO cfg : info.configurations)
                {
                    component(jw, cfg.description, cfg, false);
                }
            }
            jw.endArray();
        }

        jw.endObject();
    }

    void writePid(final JSONWriter jw, final ComponentDescriptionDTO desc) throws IOException
    {
        final String configurationPid = desc.configurationPid[0];
        final String pid;
        if (desc.configurationPid.length == 1) {
            pid = configurationPid;
        } else {
            pid = Arrays.toString(desc.configurationPid);
        }
        jw.key("pid"); //$NON-NLS-1$
        jw.value(pid);
        if (this.optionalSupport.isConfigurable(
                this.getBundleContext().getBundle(0).getBundleContext().getBundle(desc.bundle.id),
                configurationPid))
        {
            jw.key("configurable"); //$NON-NLS-1$
            jw.value(configurationPid);
        }
    }

    void component(JSONWriter jw,
            final ComponentDescriptionDTO desc,
            final ComponentConfigurationDTO config, boolean details) throws IOException
    {
        String id = config == null ? "" : String.valueOf(config.id);
        String name = desc.name;

        jw.object();

        // component information
        jw.key("id"); //$NON-NLS-1$
        jw.value(id);
        jw.key("bundleId"); //$NON-NLS-1$
        jw.value(desc.bundle.id);
        jw.key("name"); //$NON-NLS-1$
        jw.value(name);
        jw.key("state"); //$NON-NLS-1$
        if ( config != null )
        {
            jw.value(ComponentConfigurationPrinter.toStateString(config.state));
            jw.key("stateRaw"); //$NON-NLS-1$
            jw.value(config.state);
        }
        else
        {
            if ( desc.defaultEnabled && "require".equals(desc.configurationPolicy))
            {
                jw.value("no config");
            }
            else
            {
                jw.value("disabled"); //$NON-NLS-1$
            }
            jw.key("stateRaw"); //$NON-NLS-1$
            jw.value(-1);
        }

        writePid(jw, desc);

        // component details
        if (details)
        {
            gatherComponentDetails(jw, desc, config);
        }

        jw.endObject();
    }

    private void gatherComponentDetails(JSONWriter jw,
            ComponentDescriptionDTO desc,
            ComponentConfigurationDTO component) throws IOException
    {
        final Bundle bundle = this.getBundleContext().getBundle(0).getBundleContext().getBundle(desc.bundle.id);

        jw.key("props"); //$NON-NLS-1$
        jw.array();

        keyVal(jw, "Bundle", bundle.getSymbolicName() + " ("
                + bundle.getBundleId() + ")");
        keyVal(jw, "Implementation Class", desc.implementationClass);
        if (desc.factory != null)
        {
            keyVal(jw, "Component Factory Name", desc.factory);
        }
        keyVal(jw, "Default State", desc.defaultEnabled ? "enabled" : "disabled");
        keyVal(jw, "Activation", desc.immediate ? "immediate" : "delayed");

        keyVal(jw, "Configuration Policy", desc.configurationPolicy);

        if ( component != null && component.state == ComponentConfigurationDTO.FAILED_ACTIVATION && component.failure != null ) {
            keyVal(jw, "failure", component.failure);
        }
        if ( component != null && component.service != null ) {
            keyVal(jw, "serviceId", component.service.id);
        }
        listServices(jw, desc);
        if (desc.configurationPid.length == 1) {
            keyVal(jw, "PID", desc.configurationPid[0]);
        } else {
            keyVal(jw, "PIDs", Arrays.toString(desc.configurationPid));
        }
        listReferences(jw, desc, component);
        listProperties(jw, desc, component);

        jw.endArray();
    }

    private void listServices(JSONWriter jw, ComponentDescriptionDTO desc) throws IOException
    {
        String[] services = desc.serviceInterfaces;
        if (services == null)
        {
            return;
        }

        if ( desc.scope != null ) {
            keyVal(jw, "Service Type", desc.scope);
        }
        jw.object();
        jw.key("key");
        jw.value("Services");
        jw.key("value");
        jw.array();
        for (int i = 0; i < services.length; i++)
        {
            jw.value(services[i]);
        }
        jw.endArray();
        jw.endObject();
    }

    private SatisfiedReferenceDTO findReference(final ComponentConfigurationDTO component, final String name)
    {
        for(final SatisfiedReferenceDTO dto : component.satisfiedReferences)
        {
            if ( dto.name.equals(name))
            {
                return dto;
            }
        }
        return null;
    }

    private void listReferences(JSONWriter jw, ComponentDescriptionDTO desc, ComponentConfigurationDTO config) throws IOException
    {
        for(final ReferenceDTO dto : desc.references)
        {
            jw.object();
            jw.key("key");
            jw.value("Reference " + dto.name);
            jw.key("value");
            jw.array();
            final SatisfiedReferenceDTO satisfiedRef;
            if ( config != null )
            {
                satisfiedRef = findReference(config, dto.name);

                jw.value(satisfiedRef != null ? "Satisfied" : "Unsatisfied");
            }
            else
            {
                satisfiedRef = null;
            }
            jw.value("Service Name: " + dto.interfaceName);
            if (dto.target != null)
            {
                jw.value("Target Filter: " + dto.target);
            }
            jw.value("Cardinality: " + dto.cardinality);
            jw.value("Policy: " + dto.policy);
            jw.value("Policy Option: " + dto.policyOption);

            // list bound services
            if ( satisfiedRef != null )
            {
                for (int j = 0; j < satisfiedRef.boundServices.length; j++)
                {
                    final StringBuffer b = new StringBuffer();
                    b.append("Bound Service ID ");
                    b.append(satisfiedRef.boundServices[j].id);

                    String name = (String) satisfiedRef.boundServices[j].properties.get(ComponentConstants.COMPONENT_NAME);
                    if (name == null)
                    {
                        name = (String) satisfiedRef.boundServices[j].properties.get(Constants.SERVICE_PID);
                        if (name == null)
                        {
                            name = (String) satisfiedRef.boundServices[j].properties.get(Constants.SERVICE_DESCRIPTION);
                        }
                    }
                    if (name != null)
                    {
                        b.append(" (");
                        b.append(name);
                        b.append(")");
                    }
                    jw.value(b.toString());
                }
            }
            else if ( config != null )
            {
                jw.value("No Services bound");
            }

            jw.endArray();
            jw.endObject();
        }
    }

    private void listProperties(JSONWriter jw, ComponentDescriptionDTO desc, ComponentConfigurationDTO component) throws IOException
    {
        Map<String, Object> props = component != null ? component.properties : desc.properties;

        // Is this the right way to get bundle and configuration PID?
        Bundle bundle = this.getBundleContext().getBundle(0).getBundleContext().getBundle(desc.bundle.id);
        String[] configurationPids = desc.configurationPid;

        Collection<String> passwordPropertyIds =
                this.optionalSupport.getPasswordAttributeDefinitionIds(bundle, configurationPids);

        if (props != null)
        {
            jw.object();
            jw.key("key");
            jw.value("Properties");
            jw.key("value");
            jw.array();
            TreeSet<String> keys = new TreeSet<String>(props.keySet());
            for (Iterator<String> ki = keys.iterator(); ki.hasNext();)
            {
                final String key = ki.next();
                final StringBuilder b = new StringBuilder();
                b.append(key).append(" = ");

                if (passwordPropertyIds.contains(key)) {
                    b.append("********");
                } else {
                    Object prop = props.get(key);
                    prop = WebConsoleUtil.toString(prop);
                    b.append(prop);
                }

                jw.value(b.toString());
            }
            jw.endArray();
            jw.endObject();
        }
        if ( component == null && desc.factoryProperties != null ) {
            jw.object();
            jw.key("key");
            jw.value("FactoryProperties");
            jw.key("value");
            jw.array();
            TreeSet<String> keys = new TreeSet<String>(desc.factoryProperties.keySet());
            for (Iterator<String> ki = keys.iterator(); ki.hasNext();)
            {
                final String key = ki.next();
                final StringBuilder b = new StringBuilder();
                b.append(key).append(" = ");

                if (passwordPropertyIds.contains(key)) {
                    b.append("********");
                } else {
                    Object prop = props.get(key);
                    prop = WebConsoleUtil.toString(prop);
                    b.append(prop);
                }

                jw.value(b.toString());
            }
            jw.endArray();
            jw.endObject();
        }
    }

    private void keyVal(JSONWriter jw, String key, Object value) throws IOException
    {
        if (key != null && value != null)
        {
            jw.object();
            jw.key("key"); //$NON-NLS-1$
            jw.value(key);
            jw.key("value"); //$NON-NLS-1$
            jw.value(value);
            jw.endObject();
        }
    }



    private final class RequestInfo
    {
        public final String extension;
        public final ComponentConfigurationDTO component;
        public final boolean componentRequested;
        public final List<ComponentDescriptionDTO> descriptions = new ArrayList<ComponentDescriptionDTO>();
        public final List<ComponentConfigurationDTO> configurations = new ArrayList<ComponentConfigurationDTO>();
        public final List<ComponentDescriptionDTO> disabled = new ArrayList<ComponentDescriptionDTO>();

        protected RequestInfo(final HttpServletRequest request, final boolean checkPathInfo)
        {
            String info = request.getPathInfo();
            // remove label and starting slash
            info = info.substring(getLabel().length() + 1);

            // get extension
            if (info.endsWith(".json")) //$NON-NLS-1$
            {
                extension = "json"; //$NON-NLS-1$
                info = info.substring(0, info.length() - 5);
            }
            else
            {
                extension = "html"; //$NON-NLS-1$
            }

            this.descriptions.addAll(runtime.getComponentDescriptionDTOs());
            if (checkPathInfo && info.length() > 1 && info.startsWith("/")) //$NON-NLS-1$
            {
                this.componentRequested = true;
                info = info.substring(1);
                ComponentConfigurationDTO component = getComponentId(info);
                if (component == null)
                {
                    component = getComponentByName(info);
                }
                this.component = component;
                if ( this.component != null )
                {
                    this.configurations.add(this.component);
                }
            }
            else
            {
                this.componentRequested = false;
                this.component = null;

                for(final ComponentDescriptionDTO d : this.descriptions)
                {
                    if ( !runtime.isComponentEnabled(d) )
                    {
                        disabled.add(d);
                    }
                    else
                    {
                        final Collection<ComponentConfigurationDTO> configs = runtime.getComponentConfigurationDTOs(d);
                        if ( configs.isEmpty() )
                        {
                            disabled.add(d);
                        }
                        else
                        {
                            configurations.addAll(configs);
                        }
                    }
                }
                Collections.sort(configurations, Util.COMPONENT_COMPARATOR);
            }

            request.setAttribute(WebConsolePlugin.this.getClass().getName(), this);
        }

        protected ComponentConfigurationDTO getComponentId(final String componentIdPar)
        {
            try
            {
                final long componentId = Long.parseLong(componentIdPar);
                for(final ComponentDescriptionDTO desc : this.descriptions)
                {
                    for(final ComponentConfigurationDTO cfg : runtime.getComponentConfigurationDTOs(desc))
                    {
                        if ( cfg.id == componentId )
                        {
                            return cfg;
                        }
                    }
                }
            }
            catch (NumberFormatException nfe)
            {
                // don't care
            }

            return null;
        }

        protected ComponentConfigurationDTO getComponentByName(final String names)
        {
            if (names.length() > 0)
            {
                final int slash = names.lastIndexOf('/');
                final String componentName;
                final String pid;
                long bundleId = -1;
                if (slash > 0)
                {
                    pid = names.substring(slash + 1);
                    final String firstPart = names.substring(0, slash);
                    final int bundleIndex = firstPart.indexOf('/');
                    if ( bundleIndex == -1 )
                    {
                        componentName = firstPart;
                    }
                    else
                    {
                        componentName = firstPart.substring(bundleIndex + 1);
                        try
                        {
                            bundleId = Long.valueOf(firstPart.substring(0, bundleIndex));
                        }
                        catch ( final NumberFormatException nfe)
                        {
                            // wrong format
                            return null;
                        }
                    }
                }
                else
                {
                    componentName = names;
                    pid = null;
                }

                Collection<ComponentConfigurationDTO> components = null;
                for(final ComponentDescriptionDTO d : this.descriptions)
                {
                    if ( d.name.equals(componentName) && (bundleId == -1 || d.bundle.id == bundleId))
                    {
                        components = runtime.getComponentConfigurationDTOs(d);
                        if ( components.isEmpty() )
                        {
                            final ComponentConfigurationDTO cfg = new ComponentConfigurationDTO();
                            cfg.description = d;
                            cfg.state = -1;
                            return cfg;
                        }
                        else
                        {
                            if (pid != null)
                            {
                                final Iterator<ComponentConfigurationDTO> i = components.iterator();
                                while ( i.hasNext() )
                                {
                                    ComponentConfigurationDTO c = i.next();
                                    if ( pid.equals(c.description.configurationPid[0]))
                                    {
                                        return c;
                                    }

                                }
                            }
                            else if (components.size() > 0)
                            {
                                return components.iterator().next();
                            }

                        }
                    }
                }
            }

            return null;
        }
    }

    static RequestInfo getRequestInfo(final HttpServletRequest request)
    {
        return (RequestInfo) request.getAttribute(WebConsolePlugin.class.getName());
    }
}

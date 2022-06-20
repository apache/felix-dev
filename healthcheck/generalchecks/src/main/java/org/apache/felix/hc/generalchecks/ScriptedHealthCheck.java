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
package org.apache.felix.hc.generalchecks;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.script.ScriptEngine;

import org.apache.felix.hc.api.FormattingResultLog;
import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.core.impl.util.lang.StringUtils;
import org.apache.felix.hc.generalchecks.util.ScriptEnginesTracker;
import org.apache.felix.hc.generalchecks.util.ScriptHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link HealthCheck} that runs an arbitrary script. */
@Component(service = HealthCheck.class, configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
@Designate(ocd = ScriptedHealthCheck.Config.class, factory = true)
public class ScriptedHealthCheck implements HealthCheck {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptedHealthCheck.class);

    public static final String HC_LABEL = "Health Check: Script";
    public static final String JCR_FILE_URL_PREFIX = "jcr:";

    @ObjectClassDefinition(name = HC_LABEL, description = "Runs an arbitrary script in given scripting language (via javax.script). "
            + "The script has the following default bindings available: 'log', 'scriptHelper' and 'bundleContext'. "
            + "'log' is an instance of org.apache.felix.hc.api.FormattingResultLog and is used to define the result of the HC. "
            + "'scriptHelper.getService(classObj)' can be used as shortcut to retrieve a service."
            + "'scriptHelper.getServices(classObj, filter)' used to retrieve multiple services for a class using given filter. "
            + "For all services retrieved via scriptHelper, unget() is called automatically at the end of the script execution."
            + "If a Sling repository is available, the bindings 'resourceResolver' and 'session' are available automatically ("
            + "for this case a serivce user mapping for 'org.apache.felix.healthcheck.generalchecks:scripted' is required). "
            + "'bundleContext' is available for advanced use cases. The script does not need to return any value, but if it does and it is "
            + "a org.apache.felix.hc.api.Result, that result and entries in 'log' are combined then).")
    @interface Config {

        @AttributeDefinition(name = "Name", description = "Name of this health check.")
        String hc_name() default "Scripted Health Check";

        @AttributeDefinition(name = "Tags", description = "List of tags for this health check, used to select subsets of health checks for execution e.g. by a composite health check.")
        String[] hc_tags() default {};

        @AttributeDefinition(name = "Language", description = "The language the script is written in. To use e.g. 'groovy', ensure osgi bundle 'groovy-all' is available.")
        String language() default "groovy";

        @AttributeDefinition(name = "Script", description = "The script itself (either use 'script' or 'scriptUrl').")
        String script() default "log.info('ok'); log.warn('not so good'); log.critical('bad') // minimal example";

        @AttributeDefinition(name = "Script Url", description = "Url to the script to be used as alternative source (either use 'script' or 'scriptUrl'). Supported schemes are file: and jcr: (if a JCR repository is available)")
        String scriptUrl() default "";

        @AttributeDefinition
        String webconsole_configurationFactory_nameHint() default "Scripted HC: {hc.name} (tags: {hc.tags}) {scriptUrl} language: {language}";
    }

    private String language;
    private String script;
    private String scriptUrl;

    private BundleContext bundleContext;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ScriptEnginesTracker scriptEnginesTracker;

    private ScriptHelper scriptHelper = new ScriptHelper();

    @Activate
    protected void activate(BundleContext context, Config config) {
        this.bundleContext = context;
        this.language = config.language().toLowerCase();
        this.script = config.script();
        this.scriptUrl = config.scriptUrl();

        if(StringUtils.isNotBlank(script) && StringUtils.isNotBlank(scriptUrl)) {
            LOG.info("Both 'script' and 'scriptUrl' (=()) are configured, ignoring 'scriptUrl'", scriptUrl);
            scriptUrl = null;
        }

        LOG.debug("Activated Scripted HC "+config.hc_name()+" with "+ (StringUtils.isNotBlank(script)?"script "+script: "script url "+scriptUrl));

    }

    @Override
    public Result execute() {
        FormattingResultLog log = new FormattingResultLog();

        try(OptionalSlingContext optionalSlingContext = new OptionalSlingContext(bundleContext)) {

            boolean urlIsUsed = StringUtils.isBlank(script);
            String scriptToExecute;
            if (urlIsUsed) {
                if (scriptUrl.startsWith(JCR_FILE_URL_PREFIX)) {
                    String jcrPath = scriptUrl.substring(JCR_FILE_URL_PREFIX.length());
                    scriptToExecute = optionalSlingContext.getScriptFromRepository(jcrPath);
                } else {
                    scriptToExecute = scriptHelper.getFileContents(scriptUrl);
                }
            } else {
                scriptToExecute = script;
            }

            log.info("Executing script {} ({} lines)...", (urlIsUsed?scriptUrl:" as configured"), scriptToExecute.split("\n").length);
            
            ScriptEngine scriptEngine = scriptHelper.getScriptEngine(scriptEnginesTracker, language);
            scriptHelper.evalScript(bundleContext, scriptEngine, scriptToExecute, log, optionalSlingContext.getAdditionalBindings(), true);
        } catch(IllegalStateException e) {
            log.temporarilyUnavailable(e.getMessage()); // e.g. due to missing service during startup
        }  catch (Exception e) {
            log.healthCheckError("Exception while executing script: "+e, e);
        }

        return new Result(log);
    }

    // Provides an optional Sling context to use jcr: urls and to provide the bindings resourceResolver and session
    // Using reflection to ensure this bundle can start at an early start level (e.g. 5) and the Sling bundles can start at a later start level (e.g. 20)
    private static class OptionalSlingContext implements AutoCloseable {

        private static final String JCR_CONTENT_SUFFIX = "/jcr:content";

        private static final String BINDING_KEY_RESOURCE_RESOLVER = "resourceResolver";
        private static final String BINDING_KEY_SESSION = "session";

        private static final String CLASS_SESSION = "javax.jcr.Session";
        private static final String CLASS_LOGIN_EXCEPTION = "org.apache.sling.api.resource.LoginException";
        private static final String CLASS_RESOURCE_RESOLVER_FACTORY = "org.apache.sling.api.resource.ResourceResolverFactory";
        private static final String METHOD_GET_RESOURCE = "getResource";
        private static final String METHOD_GET_SERVICE_RESOURCE_RESOLVER = "getServiceResourceResolver";
        private static final String METHOD_CLOSE = "close";
        private static final String CLASS_ADAPTABLE = "org.apache.sling.api.adapter.Adaptable";
        private static final String METHOD_ADAPT_TO = "adaptTo";

        // ResourceResolverFactory.SUBSERVICE, but as copy here to not introduce dependency on maven level
        private static final String SUBSERVICE = "sling.service.subservice";
        private static final String SUBSERVICE_NAME = "scripted";
        
        private BundleContext bundleContext;
        private ServiceReference<?> resourceResolverFactoryReference;
        private Object resourceResolver;

        private boolean serviceUserMappingMissing = false;

        OptionalSlingContext(BundleContext bundleContext) {
            this.bundleContext = bundleContext;

            resourceResolverFactoryReference = bundleContext.getServiceReference(CLASS_RESOURCE_RESOLVER_FACTORY);

            if (resourceResolverFactoryReference != null) {
                try {
                    Object resourceResolverFactory = bundleContext.getService(resourceResolverFactoryReference);
                    resourceResolver = resourceResolverFactory.getClass()
                            .getMethod(METHOD_GET_SERVICE_RESOURCE_RESOLVER, Map.class)
                            .invoke(resourceResolverFactory, Collections.singletonMap(SUBSERVICE, SUBSERVICE_NAME));
                } catch (Exception e) {
                    if (e instanceof InvocationTargetException && ((InvocationTargetException) e).getTargetException()
                            .getClass().getName().equals(CLASS_LOGIN_EXCEPTION)) {
                        serviceUserMappingMissing = true;
                    } else {
                        LOG.warn("Could not get resourceResolver via reflection: " + e, e);
                    }
                }
            }
        }

        String getScriptFromRepository(String jcrPath) {

            if (resourceResolver == null) {
                throw new IllegalStateException("Script URL with scheme " + JCR_FILE_URL_PREFIX + jcrPath
                        + (serviceUserMappingMissing
                                ? " require a service user mapping for bundle "
                                        + bundleContext.getBundle().getSymbolicName() + ":" + SUBSERVICE_NAME
                                : " cannot be used as resource resolver factory is not available"));
            }

            try {
                Object jcrFileResource = resourceResolver.getClass().getMethod(METHOD_GET_RESOURCE, String.class)
                        .invoke(resourceResolver, jcrPath + JCR_CONTENT_SUFFIX);
                if (jcrFileResource == null) {
                    throw new IllegalStateException("JCR Path " + jcrPath + " does not exist");
                }
                InputStream jcrFileInputStream = (InputStream) Class.forName(CLASS_ADAPTABLE).getMethod(METHOD_ADAPT_TO, Class.class)
                        .invoke(jcrFileResource, InputStream.class);
                return new BufferedReader(new InputStreamReader(jcrFileInputStream)).lines().collect(Collectors.joining("\n"));
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Could not load script from path " + jcrPath + ": " + e, e);
            }
        }

        Map<String, Object> getAdditionalBindings() {
            if (resourceResolver != null) {
                Map<String, Object> additionalBindings = new HashMap<>();
                additionalBindings.put(BINDING_KEY_RESOURCE_RESOLVER, resourceResolver);
                try {
                    Object session = resourceResolver.getClass().getMethod(METHOD_ADAPT_TO, Class.class)
                            .invoke(resourceResolver, Class.forName(CLASS_SESSION));
                    additionalBindings.put(BINDING_KEY_SESSION, session);
                } catch (Exception e) {
                    throw new IllegalStateException("Could not add jcr session to bindings " + e, e);
                }
                return additionalBindings;
            } else {
                return null;
            }
        }

        public void close() throws Exception {
            if (resourceResolver != null) {
                resourceResolver.getClass().getMethod(METHOD_CLOSE).invoke(resourceResolver);
            }
            if (resourceResolverFactoryReference != null) {
                bundleContext.ungetService(resourceResolverFactoryReference);
            }
        }

    }

}

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

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;

@ObjectClassDefinition(name = "Apache Felix Health Check Executor Servlet", description = "Serializes health check results into html, json or txt format")
@interface HealthCheckExecutorServletConfiguration {

    String SERVLET_PATH_DEFAULT = "/system/health";

	@AttributeDefinition(name = "Servlet Context Name", description = "Servlet Context Name to register the servlet with. If not specified, the default context is used.")
    String servletContextName();

    @AttributeDefinition(name = "Servlet Path", description = "Servlet path (defaults to " + SERVLET_PATH_DEFAULT + ")")
    String servletPath() default SERVLET_PATH_DEFAULT;

    @AttributeDefinition(name = "Http Status Mapping", description = "Maps HC result status values to http response codes. Can be overwritten via request parameter 'httpStatus'")
    String httpStatusMapping() default "OK:200,WARN:200,CRITICAL:503,TEMPORARILY_UNAVAILABLE:503,HEALTH_CHECK_ERROR:500";

    @AttributeDefinition(name = "Timeout", description = "Timeout for health check executor. If not configured (left to -1), the default from health check executor's configuration is taken. The setting can always be overwritten by request parameter 'timeout'")
    long timeout() default -1;

    @AttributeDefinition(name = "Default Tags", description = "Default tags if no tags are provided in URL.")
    String[] tags() default {};

    @AttributeDefinition(name = "Combine Tags with OR", description = "If true, will execute checks that have any of the given tags. If false, will only execute checks that have *all* of the given tags.")
    boolean combineTagsWithOr() default true;
    
    @AttributeDefinition(name = "Default Format", description = "Default format if format is not provided in URL",
        options = {
            @Option(label = "HTML", value = HealthCheckExecutorServlet.FORMAT_HTML),
            @Option(label = "JSON", value = HealthCheckExecutorServlet.FORMAT_JSON),
            @Option(label = "JSONP", value = HealthCheckExecutorServlet.FORMAT_JSONP),
            @Option(label = "TXT", value = HealthCheckExecutorServlet.FORMAT_TXT),
            @Option(label = "VERBOSE TXT", value = HealthCheckExecutorServlet.FORMAT_VERBOSE_TXT)
        })
    String format() default HealthCheckExecutorServlet.FORMAT_HTML;

    @AttributeDefinition(name = "Allowed Formats", description = "Allow list for formats passed in via the URL",
        options = {
            @Option(label = "HTML", value = HealthCheckExecutorServlet.FORMAT_HTML),
            @Option(label = "JSON", value = HealthCheckExecutorServlet.FORMAT_JSON),
            @Option(label = "JSONP", value = HealthCheckExecutorServlet.FORMAT_JSONP),
            @Option(label = "TXT", value = HealthCheckExecutorServlet.FORMAT_TXT),
            @Option(label = "VERBOSE TXT", value = HealthCheckExecutorServlet.FORMAT_VERBOSE_TXT)
        })
    String[] allowed_formats() default {
        HealthCheckExecutorServlet.FORMAT_HTML, 
        HealthCheckExecutorServlet.FORMAT_JSON, 
        HealthCheckExecutorServlet.FORMAT_JSONP, 
        HealthCheckExecutorServlet.FORMAT_TXT,
        HealthCheckExecutorServlet.FORMAT_VERBOSE_TXT
    };

    @AttributeDefinition(name = "Disabled", description = "Allows to disable the servlet if required for security reasons")
    boolean disabled() default false;

    @AttributeDefinition(name = "CORS Access-Control-Allow-Origin", description = "Sets the Access-Control-Allow-Origin CORS header. If blank no header is sent.")
    String cors_accessControlAllowOrigin() default "*";

    @AttributeDefinition(name = "Disable Request Configuration", description = "If set, parameters passed in via the request are ignored (except for format)")
    boolean disable_request_configuration() default false;

    @AttributeDefinition
    String webconsole_configurationFactory_nameHint() default "{servletPath} default format:{format} default tags:{tags} ";
}

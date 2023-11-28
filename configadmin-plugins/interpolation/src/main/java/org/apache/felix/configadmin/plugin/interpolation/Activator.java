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
package org.apache.felix.configadmin.plugin.interpolation;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Header(name=Constants.BUNDLE_ACTIVATOR, value="${@class}")
public class Activator implements BundleActivator {
    static final String DEPRECATED_DIR_PROPERTY = "org.apache.felix.configadmin.plugin.interpolation.dir";
    static final String DIR_PROPERTY = "org.apache.felix.configadmin.plugin.interpolation.secretsdir";
    static final String ENCODING_PROPERTY = "org.apache.felix.configadmin.plugin.interpolation.file.encoding";

    static final String PLUGIN_ID = "org.apache.felix.configadmin.plugin.interpolation";

    static final int PLUGIN_RANKING = 500;

    static final Logger LOG = LoggerFactory.getLogger(InterpolationConfigurationPlugin.class);

    @Override
    public void start(BundleContext context) throws Exception {
        String directory = context.getProperty(DIR_PROPERTY);
        if (directory == null) {
            directory = context.getProperty(DEPRECATED_DIR_PROPERTY);
            if (directory != null) {
                LOG.warn("Deprecated property is used for configuration, switch from using '" + DEPRECATED_DIR_PROPERTY
                        + "' to the new '" + DIR_PROPERTY + "'.");
            }
        }
        String encoding = context.getProperty(ENCODING_PROPERTY);

        ConfigurationPlugin plugin = new InterpolationConfigurationPlugin(context::getProperty, directory, encoding);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(ConfigurationPlugin.CM_RANKING, PLUGIN_RANKING);
        props.put("config.plugin.id", PLUGIN_ID);

        if (directory != null)
            props.put(DIR_PROPERTY, directory);
        else
            props.put(DIR_PROPERTY, "<not configured>");

        if (encoding != null)
            props.put(ENCODING_PROPERTY, encoding);
        else
            props.put(ENCODING_PROPERTY, "<not configured>");

        context.registerService(ConfigurationPlugin.class, plugin, props);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        // Service is automatically unregistered when bundle is stopped.
    }
}

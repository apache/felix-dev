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

import java.io.File;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Map;
import java.util.stream.Collectors;

import org.osgi.framework.Constants;

/**
 * Entrypoint into the interpolator independent of the OSGi API, so it can be used from outside of an
 * OSGi Configuration Admin environment.
 */
public class StandaloneInterpolator {
    final InterpolationConfigurationPlugin plugin;

    /**
     * Constructor.
     *
     * @param frameworkProperties Properties to use for framework property substitutions.
     * @param secretsLocations The directories where secrets files can be found. The platform default
     * encoding will be used for these files.
     */
    public StandaloneInterpolator(Map<String,String> frameworkProperties, File ... secretsLocations) {
        this(frameworkProperties, null, secretsLocations);
    }

    /**
     * Constructor.
     *
     * @param frameworkProperties Properties to use for framework property substitutions.
     * @param encoding The file encoding to be used for the files in the secrets locations.
     * @param secretsLocations The directories where secrets files can be found.
     */
    public StandaloneInterpolator(Map<String,String> frameworkProperties, String encoding, File ... secretsLocations) {
        if (secretsLocations == null)
            secretsLocations = new File[] {};

        String locations = Arrays.asList(secretsLocations).stream()
                .map(File::toString)
                .collect(Collectors.joining(","));
        plugin = new InterpolationConfigurationPlugin(frameworkProperties::get, locations, encoding);
    }

    /**
     * Perform configuration interpolations.
     *
     * @param pid The PID of the configuration.
     * @param dict The dictionary containing the configuration properties. The dictionary will be updated
     * by the interpolation substitutions.
     */
    public void interpolate(String pid, Dictionary<String, Object> dict) {
        boolean pidAdded = false;
        try {
            if (dict.get(Constants.SERVICE_PID) == null) {
                dict.put(Constants.SERVICE_PID, pid);
                pidAdded = true;
            }

            plugin.modifyConfiguration(null, dict);
        } finally {
            if (pidAdded)
                dict.remove(Constants.SERVICE_PID);
        }
    }
}

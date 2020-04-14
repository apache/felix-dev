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
package org.apache.felix.configurator.impl.json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.felix.cm.json.ConfigurationReader;
import org.apache.felix.cm.json.ConfigurationResource;
import org.apache.felix.cm.json.Configurations;
import org.apache.felix.configurator.impl.model.BundleState;
import org.apache.felix.configurator.impl.model.Config;
import org.apache.felix.configurator.impl.model.ConfigPolicy;
import org.apache.felix.configurator.impl.model.ConfigurationFile;
import org.osgi.util.converter.Converters;

public class JSONUtil {

    private static final String PROP_RANKING = "ranking";

    private static final String PROP_POLICY = "policy";

    public static final class Report {

        public final List<String> warnings = new ArrayList<>();

        public final List<String> errors = new ArrayList<>();
    }

    /**
     * Read all configurations from a bundle
     * @param provider The bundle provider
     * @param paths The paths to read from
     * @param report The report for errors and warnings
     * @return The bundle state.
     */
    public static BundleState readConfigurationsFromBundle(final BinUtil.ResourceProvider provider,
            final Set<String> paths,
            final Report report) {
        final BundleState config = new BundleState();

        final List<ConfigurationFile> allFiles = new ArrayList<>();
        for(final String path : paths) {
            final List<ConfigurationFile> files = readJSON(provider, path, report);
            allFiles.addAll(files);
        }
        Collections.sort(allFiles);

        config.addFiles(allFiles);

        return config;
    }

    /**
     * Read all json files from a given path in the bundle
     *
     * @param provider The bundle provider
     * @param path The path
     * @param report The report for errors and warnings
     * @return A list of configuration files - sorted by url, might be empty.
     */
    private static List<ConfigurationFile> readJSON(final BinUtil.ResourceProvider provider,
            final String path,
            final Report report) {
        final List<ConfigurationFile> result = new ArrayList<>();
        final Enumeration<URL> urls = provider.findEntries(path, "*.json");
        if ( urls != null ) {
            while ( urls.hasMoreElements() ) {
                final URL url = urls.nextElement();

                final String filePath = url.getPath();
                final int pos = filePath.lastIndexOf('/');
                final String name = path + filePath.substring(pos);

                try {
                    final String contents = getResource(name, url);
                    boolean done = false;
                    final BinaryManager binaryManager = new BinaryManager(provider, report);
                    try {
                        final ConfigurationFile file = readJSON(binaryManager, name, url, provider.getBundleId(), contents, report);
                        if ( file != null ) {
                            result.add(file);
                            done = true;
                        }
                    } finally {
                        if ( !done ) {
                            binaryManager.cleanupFiles();
                        }
                    }
                } catch ( final IOException ioe ) {
                    report.errors.add("Unable to read " + name + " : " + ioe.getMessage());
                }
            }
            Collections.sort(result);
        } else {
            report.errors.add("No configurations found at path " + path);
        }
        return result;
    }

    /**
     * Read a single JSON file
     *
     * @param binaryManager The binary manager
     * @param name      The name of the file
     * @param url       The url to that file or {@code null}
     * @param bundleId  The bundle id of the bundle containing the file
     * @param contents  The contents of the file
     * @param report    The report for errors and warnings
     * @return The configuration file or {@code null}.
     */
    public static ConfigurationFile readJSON(
            final BinaryManager binaryManager,
            final String name,
            final URL url,
            final long bundleId,
            final String contents,
            final Report report) {
        final String identifier = (url == null ? name : url.toString());
        try (final Reader reader = new StringReader(contents)) {

            final Map<String, Integer> rankingMap = new HashMap<>();
            final Map<String, ConfigPolicy> policyMap = new HashMap<>();

            final ConfigurationReader cfgReader = Configurations.buildReader()
                    .verifyAsBundleResource(url != null)
                    .withIdentifier(identifier)
                    .withBinaryHandler( binaryManager )
                    .withConfiguratorPropertyHandler( (pid, key, value) -> {
                        if (key.equals(PROP_RANKING)) {
                            final Integer intObj = Converters.standardConverter().convert(value).defaultValue(null)
                                    .to(Integer.class);
                            if (intObj == null) {
                                report.warnings.add(identifier.concat(" : PID ").concat(pid).concat(" : Invalid ranking ")
                                        .concat(value.toString()));
                            } else {
                                rankingMap.put(pid, intObj);
                            }
                        } else if (key.equals(PROP_POLICY)) {
                            final String stringVal = Converters.standardConverter().convert(value).defaultValue(null)
                                    .to(String.class);
                            if (stringVal == null) {
                                report.errors.add(identifier.concat(" : PID ").concat(pid)
                                        .concat(" : Invalid policy for configuration : ").concat(value.toString()));
                            } else {
                                if (value.equals("default") || value.equals("force")) {
                                    policyMap.put(pid, ConfigPolicy.valueOf(stringVal.toUpperCase()));
                                } else {
                                    report.errors.add(identifier.concat(" : PID ").concat(pid)
                                            .concat(" : Invalid policy for configuration : ").concat(value.toString()));
                                }
                            }
                        }

                    })
                    .build(reader);
            final ConfigurationResource rsrc = cfgReader.readConfigurationResource();
            report.errors.addAll(cfgReader.getIgnoredErrors());

            final List<Config> list = createModel(binaryManager, bundleId, rsrc, rankingMap, policyMap);
            if ( !list.isEmpty() ) {
                final ConfigurationFile file = new ConfigurationFile(url, list);

                return file;
            }
        } catch (final IOException ioe) {
            report.errors.add("Invalid JSON from " + identifier);
        }
        return null;
    }

    /**
     * Create the model
     * @param binaryManager The binary manager
     * @param bundleId The bundle id
     * @param rsrc The map containing the configurations
     * @param rankingMap The map with ranking information
     * @param policyMap The map with policy information
     * @return The list of {@code Config}s
     */
    public static List<Config> createModel(final BinaryManager binaryManager,
            final long bundleId,
            final ConfigurationResource rsrc,
            final Map<String, Integer> rankingMap,
            final Map<String, ConfigPolicy> policyMap) {
        final List<Config> configurations = new ArrayList<>();
        for (final Map.Entry<String, Hashtable<String, Object>> entry : rsrc.getConfigurations().entrySet()) {
            final String pid = entry.getKey();
            final Hashtable<String, Object> properties = entry.getValue();

            final Config c = new Config(pid, properties, bundleId, rankingMap.computeIfAbsent(pid, id -> 0), policyMap.computeIfAbsent(pid, id -> ConfigPolicy.DEFAULT));
            // TODO this is per config
            c.setFiles(binaryManager.flushFiles(pid));
            configurations.add(c);
        }
        return configurations;
    }

    /**
     * Read the contents of a resource, encoded as UTF-8
     * @param name The resource name
     * @param url The resource URL
     * @return The contents
     * @throws IOException If anything goes wrong
     */
    public static String getResource(final String name, final URL url)
    throws IOException {
        final URLConnection connection = url.openConnection();

        try(final BufferedReader in = new BufferedReader(
                new InputStreamReader(
                    connection.getInputStream(), "UTF-8"))) {

            final StringBuilder sb = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }

            return sb.toString();
        }
    }
}

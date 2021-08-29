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
package org.apache.felix.webconsole.internal.configuration;


import java.io.IOException;
import java.util.Dictionary;
import java.util.List;

import org.apache.felix.webconsole.spi.ConfigurationHandler;
import org.apache.felix.webconsole.spi.ValidationException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;


public class ConfigurationUtil {

    private static final String PLACEHOLDER_PID = "[Temporary PID replaced by real PID upon save]"; //$NON-NLS-1$

    public static Configuration findConfiguration( final ConfigurationAdmin service, final String pid ) {
        if ( pid != null ) {
            try {
                // we use listConfigurations to not create configuration
                // objects persistently without the user providing actual
                // configuration
                String filter = '(' + Constants.SERVICE_PID + '=' + pid + ')';
                Configuration[] configs = service.listConfigurations( filter );
                if ( configs != null && configs.length > 0 ) {
                    return configs[0];
                }
            } catch ( final InvalidSyntaxException | IOException e ) {
                // should print message
            }
        }

        // fallback to no configuration at all
        return null;
    }


    public static Configuration getOrCreateConfiguration( final ConfigurationAdmin service, 
            final List<ConfigurationHandler> handlers,
            final String pid, 
            final String factoryPid ) throws ValidationException, IOException {
        Configuration cfg = null;
        if ( !PLACEHOLDER_PID.equals(pid) ) {
            cfg = findConfiguration(service, pid);
        }
        if ( cfg == null ) {
            if ( factoryPid != null  ) {
                for(final ConfigurationHandler handler : handlers) {
                    handler.createFactoryConfiguration(factoryPid, null);
                }
                cfg = service.createFactoryConfiguration( factoryPid, null );
            } else {
                for(final ConfigurationHandler handler : handlers) {
                    handler.createConfiguration(pid);
                }        
                cfg = service.getConfiguration( pid, null );
            }
        }
        return cfg;
    }

    public static final boolean isAllowedPid(final String pid) {
        for(int i = 0; i < pid.length(); i++) {
            final char c = pid.charAt(i);
            if ( c == '&' || c == '<' || c == '>' || c == '"' || c == '\'' ) {
                return false;
            }
        }
        return true;
    }

 
    public static Configuration getPlaceholderConfiguration( final String factoryPid ) {
        return new PlaceholderConfiguration( factoryPid );
    }

    public static String getPlaceholderPid() {
        return PLACEHOLDER_PID;
    }

    private static class PlaceholderConfiguration implements Configuration {

        private final String factoryPid;
        private String bundleLocation;


        PlaceholderConfiguration( final String factoryPid ) {
            this.factoryPid = factoryPid;
        }


        @Override
        public String getPid() {
            return PLACEHOLDER_PID;
        }


        @Override
        public String getFactoryPid() {
            return factoryPid;
        }

        @Override
        public void setBundleLocation( final String bundleLocation ) {
            this.bundleLocation = bundleLocation;
        }

        @Override
        public String getBundleLocation() {
            return bundleLocation;
        }

        @Override
        public Dictionary<String, Object> getProperties() {
            // dummy configuration has no properties
            return null;
        }

        @Override
        public void update() {
            // dummy configuration cannot be updated
        }

        @Override
        public void update( Dictionary<String, ?> properties ) {
            // dummy configuration cannot be updated
        }

        @Override
        public void delete() {
            // dummy configuration cannot be deleted
        }

        @Override
        public long getChangeCount() {
            // dummy configuration always returns 0
            return 0;
        }
    }
}

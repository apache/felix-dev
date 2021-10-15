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
package org.apache.felix.webconsole.internal.misc;


import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;

import org.apache.felix.webconsole.internal.AbstractConfigurationPrinter;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.namespace.NativeNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;


public class FrameworkPropertiesPrinter extends AbstractConfigurationPrinter
{

    private static final String TITLE = "Framework Properties";

    public String getTitle()
    {
        return TITLE;
    }

    public void printConfiguration( PrintWriter printWriter )
    {
        getFrameworkProperties().entrySet().stream().forEach( e -> ConfigurationRender.infoLine( printWriter, null, e.getKey(), e.getValue() ));
    }

    private Map<String,String> getFrameworkProperties() 
    {
        Bundle systemBundle = getBundleContext().getBundle( Constants.SYSTEM_BUNDLE_LOCATION );
        BundleWiring bundleWiring = systemBundle.adapt(BundleWiring.class);
        // https://docs.osgi.org/specification/osgi.core/7.0.0/framework.namespaces.html#framework.namespaces.osgi.native
        Map<String, String> frameworkProperties = new TreeMap<>();
        for ( BundleCapability capability : bundleWiring.getCapabilities( NativeNamespace.NATIVE_NAMESPACE ) ) 
        {
            capability.getAttributes().entrySet().stream()
                // filter out everything starting with osgi.native.
                .filter( e -> !e.getKey().startsWith( NativeNamespace.NATIVE_NAMESPACE + "." ) )
                .forEach( e -> frameworkProperties.put( e.getKey(), e.getValue().toString() ) );
        }
        return frameworkProperties;
    }
}
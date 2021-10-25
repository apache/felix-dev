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
package org.apache.felix.webconsole.internal.core;

import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.felix.webconsole.internal.AbstractConfigurationPrinter;
import org.apache.felix.webconsole.internal.misc.ConfigurationRender;
import org.osgi.framework.Bundle;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;


public class CapabilitiesPrinter extends AbstractConfigurationPrinter
{

    private static final String TITLE = "Capabilities";

    static final List<String> EXCLUDED_NAMESPACES = Arrays.asList( PackageNamespace.PACKAGE_NAMESPACE, HostNamespace.HOST_NAMESPACE );
    static final Predicate<String> EXCLUDED_NAMESPACES_PREDICATE = new ExcludedNamespacesPredicate();

    public String getTitle()
    {
        return TITLE;
    }

    static final class ExcludedNamespacesPredicate implements Predicate<String>
    {
        @Override
        public boolean test(String namespace) {
            return !EXCLUDED_NAMESPACES.contains(namespace);
        }
    }

    public void printConfiguration( PrintWriter printWriter )
    {
        for ( Bundle bundle : getBundleContext().getBundles() )
        {
            BundleWiring wiring = bundle.adapt( BundleWiring.class );
            if ( wiring == null )
            {
                continue;
            }
            List<BundleCapability> capabilities = wiring.getCapabilities( null );
            if ( capabilities == null )
            {
                continue;
            }
            // which capabilities to filter?
            List<BundleCapability> filteredCapabilities = capabilities.stream()
                    .filter( c -> EXCLUDED_NAMESPACES_PREDICATE.test(c.getNamespace()) )
                    .collect( Collectors.toList() );
            if ( filteredCapabilities.isEmpty() )
            {
                // skip bundles not exporting capabilities
                continue;
            }
            
            ConfigurationRender.infoLine( printWriter, null,  "Bundle", bundle.getSymbolicName() + " (" + bundle.getBundleId() + ")" );
            for ( BundleCapability capability : filteredCapabilities )
            {
                ConfigurationRender.infoLine( printWriter, "  ", "Capability namespace", capability.getNamespace() );
                String attributes = dumpTypedAttributes( capability.getAttributes() );
                if ( !attributes.isEmpty() ) 
                {
                    ConfigurationRender.infoLine( printWriter, "    ", "Attributes", attributes );
                }
                String directives = dumpDirectives( capability.getDirectives() );
                if ( !directives.isEmpty() )
                {
                    ConfigurationRender.infoLine( printWriter, "    ", "Directives", directives );
                }
                List<BundleWire> wires = wiring.getRequiredWires( capability.getNamespace() );
                if ( wires == null )
                {
                    continue;
                }
                String requirerBundles = wires.stream()
                        .map( w -> w.getRequirer().getSymbolicName() + " (" + w.getRequirer().getBundle().getBundleId() + ") with directives: " + dumpDirectives( w.getRequirement().getDirectives() ) )
                        .collect( Collectors.joining( ", ") );
                if ( !requirerBundles.isEmpty() )
                {
                    ConfigurationRender.infoLine( printWriter, "    ", "Required By", requirerBundles );
                }
            }
        }
    }

    static String dumpTypedAttributes( Map<String, Object> typedAttributes )
    {
        StringBuilder attributes = new StringBuilder();
        boolean isFirst = true;
        for ( Map.Entry<String, Object> entry : typedAttributes.entrySet() )
        {
            String value;
            if ( entry.getValue().getClass().isArray() ) 
            {
                StringBuilder values = new StringBuilder( "[" );
                for ( int i=0; i<Array.getLength(entry.getValue()); i++ ) 
                {
                    if ( i > 0 )
                    {
                        values.append( ", " );
                    }
                    values.append( Array.get( entry.getValue(), i ) );
                }
                value = values.append( "]" ).toString();
            }
            else 
            {
                value = entry.getValue().toString();
            }
            if ( isFirst )
            {
                isFirst = false;
            }
            else
            {
                attributes.append( ", " );
            }
            attributes.append( entry.getKey() ).append( "=" ).append( value );
        }
        return attributes.toString();
    }

    static String dumpDirectives( Map<String, String> directives )
    {
        return directives.entrySet().stream().map( e -> e.getKey() + "=" + e.getValue() ).collect( Collectors.joining( " ," ) );
    }
}
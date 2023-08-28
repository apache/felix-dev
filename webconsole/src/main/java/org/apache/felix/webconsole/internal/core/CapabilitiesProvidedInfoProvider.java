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


import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.felix.webconsole.bundleinfo.BundleInfo;
import org.apache.felix.webconsole.bundleinfo.BundleInfoProvider;
import org.apache.felix.webconsole.bundleinfo.BundleInfoType;
import org.apache.felix.webconsole.i18n.LocalizationHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;


public class CapabilitiesProvidedInfoProvider implements BundleInfoProvider
{
    private final LocalizationHelper localization;

    // hide some namespace except if required by some other bundle
    // TODO: add more namespaces from http://docs.osgi.org/specification/osgi.cmpn/7.0.0/service.namespaces.html
    private static final List<String> STANDARD_NAMESPACES = Arrays.asList( BundleNamespace.BUNDLE_NAMESPACE, IdentityNamespace.IDENTITY_NAMESPACE );
    
    CapabilitiesProvidedInfoProvider( Bundle bundle )
    {
        localization = new LocalizationHelper( bundle );
    }

    @Override
    public String getName( Locale locale ) 
    {
        return localization.getResourceBundle( locale ).getString( "capabilities.provided.info.name" );
    }

    /**
     * Hides those capabilities which are exposed in a dedicated section and all default bundle namespaces which are not required by at least one other bundle.
     *
     */
    private static final class CapabilityFilter implements Predicate<BundleCapability>
    {
        private BundleWiring wiring;
        
        CapabilityFilter(BundleWiring wiring)
        {
            this.wiring = wiring;
        }

        @Override
        public boolean test(BundleCapability t)
        {
            return (CapabilitiesPrinter.EXCLUDED_NAMESPACES_PREDICATE.test(t.getNamespace())) && 
            !(STANDARD_NAMESPACES.contains(t.getNamespace()) && wiring.getProvidedWires(t.getNamespace()).isEmpty());
        }
    }

    @Override
    public BundleInfo[] getBundleInfo( Bundle bundle, String webConsoleRoot, Locale locale )
    {
        BundleWiring wiring = bundle.adapt( BundleWiring.class );
        if ( wiring == null )
        {
            return new BundleInfo[0];
        }
        Predicate<BundleCapability> capabilityFilter = new CapabilityFilter( wiring );
        List<BundleCapability> capabilities = wiring.getCapabilities( null );
        if ( capabilities == null )
        {
            return new BundleInfo[0];
        }
        // which capabilities to filter?
        List<BundleCapability> filteredCapabilities = capabilities.stream()
                .filter( capabilityFilter )
                .collect( Collectors.toList() );
        return filteredCapabilities.stream().map( c -> toInfo( c, wiring, webConsoleRoot, locale ) ).toArray( BundleInfo[]::new );
    }

    private BundleInfo toInfo( BundleCapability capability, BundleWiring wiring, String webConsoleRoot, Locale locale )
    {
        final String descr = localization.getResourceBundle( locale ).getString( "capabilities.provided.info.descr" );;
        String name = localization.getResourceBundle( locale ).getString( "capabilities.provided.info.key" );;
        String requirerBundles = wiring.getProvidedWires( capability.getNamespace() ).stream()
                .map( w -> w.getRequirer().getSymbolicName() + " (" + w.getRequirer().getBundle().getBundleId() + ")" )
                .collect( Collectors.joining( ", ") );
        name = MessageFormat.format( name, capability.getNamespace(), CapabilitiesPrinter.dumpTypedAttributes( capability.getAttributes() ) );
        if ( !requirerBundles.isEmpty() )
        {
            name += MessageFormat.format( localization.getResourceBundle( locale ).getString( "capabilities.provided.info.key.addition" ), requirerBundles );
        }
        // use empty link type to prevent the pattern <name>=<value> being used for printing
        return new BundleInfo( name, "/#", BundleInfoType.LINK, descr );
    }
}
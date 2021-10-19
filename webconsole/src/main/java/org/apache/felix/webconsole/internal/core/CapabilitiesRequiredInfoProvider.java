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
import java.util.Locale;
import java.util.Optional;

import org.apache.felix.webconsole.bundleinfo.BundleInfo;
import org.apache.felix.webconsole.bundleinfo.BundleInfoProvider;
import org.apache.felix.webconsole.bundleinfo.BundleInfoType;
import org.apache.felix.webconsole.i18n.LocalizationHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleWiring;


public class CapabilitiesRequiredInfoProvider implements BundleInfoProvider
{
    private final LocalizationHelper localization;

    CapabilitiesRequiredInfoProvider( Bundle bundle )
    {
        localization = new LocalizationHelper( bundle );
    }

    @Override
    public String getName( Locale locale )
    {
        return localization.getResourceBundle( locale ).getString( "capabilities.required.info.name" ); //$NON-NLS-1$;
    }

    @Override
    public BundleInfo[] getBundleInfo( Bundle bundle, String webConsoleRoot, Locale locale )
    {
        BundleWiring wiring = bundle.adapt( BundleWiring.class );
        return wiring.getRequirements( null ).stream()
            .filter( t -> CapabilitiesPrinter.EXCLUDED_NAMESPACES_PREDICATE.test(t.getNamespace()))
            .map( r -> toInfo( r, wiring, webConsoleRoot, locale ) ).toArray( BundleInfo[]::new );
    }

    private BundleInfo toInfo( BundleRequirement requirement, BundleWiring wiring, String webConsoleRoot, Locale locale )
    {
        final String descr = localization.getResourceBundle( locale ).getString( "capabilities.required.info.descr" ); //$NON-NLS-1$;
        Optional<Bundle> providerBundle = wiring.getRequiredWires( requirement.getNamespace() ).stream()
                .map( w -> w.getProvider().getBundle() ).findFirst(); // only the first one is returned
        String name = localization.getResourceBundle( locale ).getString( "capabilities.required.info.key" ); //$NON-NLS-1$;
        name = MessageFormat.format( name, requirement.getNamespace(), CapabilitiesPrinter.dumpDirectives( requirement.getDirectives() ) );
        String link = "/#"; // use empty link type to prevent the pattern <name>=<value> being used for printing
        if ( providerBundle.isPresent() )
        {
           name += MessageFormat.format( localization.getResourceBundle( locale ).getString( "capabilities.required.info.key.addition" ), providerBundle.get().getSymbolicName(), providerBundle.get().getBundleId() );
           if ( webConsoleRoot != null ) {
               link = webConsoleRoot + "/bundles/" + providerBundle.get().getBundleId(); //$NON-NLS-1$
           }
        }
        return new BundleInfo( name, link, BundleInfoType.LINK, descr );
    }
}
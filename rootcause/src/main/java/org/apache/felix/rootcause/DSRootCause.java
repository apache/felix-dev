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
package org.apache.felix.rootcause;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.ReferenceDTO;
import org.osgi.service.component.runtime.dto.UnsatisfiedReferenceDTO;

/**
 * Utility class to get the root cause for declarative services.
 */
public class DSRootCause {

    private static final int MAX_RECURSION = 10;

    private final ServiceComponentRuntime scr;

    /**
     * Create new instance
     * @param scr Service component runtime
     */
    public DSRootCause(final ServiceComponentRuntime scr) {
        this.scr = scr;
    }

    /**
     * Get the root cause based on an interface name
     * @param iface The interface name
     * @return Optional root cause
     */
    public Optional<DSComp> getRootCause(final String iface) {
        return this.getRootCause(iface, null);
    }

    /**
     * Get the root cause based on an interface name
     * @param iface The interface name
     * @param allDTOs A collection with all dtos as a cache to lookup, optional
     * @return Optional root cause
     * @since 0.2.0
     */
    public Optional<DSComp> getRootCause(final String iface, final Collection<ComponentDescriptionDTO> allDTOs) {
        final Collection<ComponentDescriptionDTO> cache = allDTOs == null ? scr.getComponentDescriptionDTOs() : allDTOs;
        return cache.stream()
            .filter(desc -> offersInterface(desc, iface))
            .map(d -> getRootCause(d, cache))
            .findFirst();
    }

    /**
     * Get the root cause for a component description
     * @param desc The description
     * @return The root cause
     */
    public DSComp getRootCause(final ComponentDescriptionDTO desc) {
        return getRootCause(desc, null);
    }

    /**
     * Get the root cause for a component description
     * @param desc The description
     * @param allDTOs A collection with all dtos as a cache to lookup, optional
     * @return The root cause
     * @since 0.2.0
     */
    public DSComp getRootCause(final ComponentDescriptionDTO desc, final Collection<ComponentDescriptionDTO> allDTOs) {
        final Set<String> visitedNames = new HashSet<>();
        return getRootCause(desc, visitedNames, 0, allDTOs == null ? new ArrayList<>() : allDTOs);
    }

    private DSComp getRootCause(final ComponentDescriptionDTO desc,
            final Set<String> visitedNames,
            final int level,
            final Collection<ComponentDescriptionDTO> cache) {
        if (level > MAX_RECURSION || visitedNames.contains(desc.name)) {
            return null;
        }
        final boolean added = visitedNames.add(desc.name);
        final DSComp dsComp = new DSComp();
        dsComp.desc = desc;

        final Collection<ComponentConfigurationDTO> instances = scr.getComponentConfigurationDTOs(desc);
        if (instances.isEmpty()) {
            return dsComp;
        }
        for (final ComponentConfigurationDTO instance : instances) {
            for (final UnsatisfiedReferenceDTO ref : instance.unsatisfiedReferences) {
                final ReferenceDTO refdef = getReference(desc, ref.name);

                final DSRef unresolvedRef = createRef(ref, refdef);
                unresolvedRef.candidates = getCandidates(ref, refdef, visitedNames, level + 1, cache);
                dsComp.unsatisfied.add(unresolvedRef);
            }
        }
        if ( added ) {
            visitedNames.remove(desc.name);
        }
        return dsComp;
    }

    private DSRef createRef(UnsatisfiedReferenceDTO unsatifiedRef, ReferenceDTO refdef) {
        final DSRef ref = new DSRef();
        ref.name = unsatifiedRef.name;
        ref.filter = unsatifiedRef.target;
        ref.iface = refdef.interfaceName;
        return ref;
    }

    private List<DSComp> getCandidates(final UnsatisfiedReferenceDTO ref,
            final ReferenceDTO refdef,
            final Set<String> visitedNames,
            final int level,
            final Collection<ComponentDescriptionDTO> cache) {
        if ( cache.isEmpty() ) {
            cache.addAll(scr.getComponentDescriptionDTOs());
        }
        final List<DSComp> result = new ArrayList<>();

        final List<ComponentDescriptionDTO> candidates = cache.stream()
                .filter(desc -> offersInterface(desc, refdef.interfaceName)).collect(Collectors.toList());
        for(final ComponentDescriptionDTO c : candidates) {
            final DSComp r = getRootCause(c, visitedNames, level, cache);
            if ( r != null ) {
                result.add(r);
            }
        }
        return result;
    }

    private boolean offersInterface(ComponentDescriptionDTO desc, String interfaceName) {
        return Arrays.asList(desc.serviceInterfaces).contains(interfaceName);
    }

    private ReferenceDTO getReference(final ComponentDescriptionDTO desc, final String name) {
        return Arrays.asList(desc.references).stream().filter(ref -> ref.name.equals(name)).findFirst().get();
    }

}

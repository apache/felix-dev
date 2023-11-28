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
package org.apache.felix.hc.core.impl.util;

import static org.apache.felix.hc.api.execution.HealthCheckSelector.empty;

import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Select from available {@link HealthCheck} services. 
 *
 * This class is not thread safe and instances shouldn't be used concurrently from different threads. */
public class HealthCheckFilter {

    private final Logger log = LoggerFactory.getLogger(getClass());

    // object class (supporting current interface and legacy)
    public static final String HC_FILTER_OBJECT_CLASS = "(|(objectClass="+HealthCheck.class.getName()+")(objectClass=org.apache.sling.hc.api.HealthCheck))";
    public static final String OMIT_PREFIX = "-";

    private final BundleContext bundleContext;

    /** Create a new filter object
     * 
     *  @param bc bundle context*/
    public HealthCheckFilter(final BundleContext bc) {
        bundleContext = bc;
    }

    public ServiceReference<HealthCheck>[] getHealthCheckServiceReferences(final HealthCheckSelector selector) {
        return getHealthCheckServiceReferences(selector, false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public ServiceReference<HealthCheck>[] getHealthCheckServiceReferences(final HealthCheckSelector selector, boolean combineTagsWithOr) {
        final CharSequence filterBuilder = selector != null ? getServiceFilter(selector, combineTagsWithOr)
                : getServiceFilter(empty(), combineTagsWithOr);

        log.trace("OSGi service filter in getHealthCheckServiceReferences(): {}", filterBuilder);

        try {
            final String filterString = filterBuilder.length() == 0 ? null : filterBuilder.toString();
            bundleContext.createFilter(filterString); // check syntax early
            final ServiceReference[] refs = bundleContext.getServiceReferences((String) null, filterString);
            if (refs == null) {
                log.debug("Found no HealthCheck services for filter: {}", filterString);
                return new ServiceReference[0];
            } else {
                log.debug("Found {} HealthCheck services for filter: {}", refs.length, filterString);
            }
            return refs;
        } catch (final InvalidSyntaxException ise) {
            // this should not happen, but we fail gracefully
            log.error("Invalid OSGi filter syntax in '" + filterBuilder + "'", ise);
            return new ServiceReference[0];
        }
    }


    CharSequence getServiceFilter(HealthCheckSelector selector, boolean combineTagsWithOr) {
        // Build service filter
        final StringBuilder filterBuilder = new StringBuilder();
        filterBuilder.append("(&"); 
        filterBuilder.append(HC_FILTER_OBJECT_CLASS);

        final int prefixLen = HealthCheckFilter.OMIT_PREFIX.length();
        final StringBuilder filterBuilderForOrOperator = new StringBuilder(); // or filters
        final StringBuilder tagsBuilder = new StringBuilder();
        int tagsAndClauses = 0;
        if (selector.tags() != null) {
            for (String tag : selector.tags()) {
                tag = tag.trim();
                if (tag.length() == 0) {
                    continue;
                }
                if (tag.startsWith(HealthCheckFilter.OMIT_PREFIX)) {
                    // ommit tags always have to be added as and-clause
                    filterBuilder.append("(!(").append(HealthCheck.TAGS).append("=").append(tag.substring(prefixLen)).append("))");
                } else {
                    // add regular tags in the list either to outer and-clause or inner or-clause
                    if (combineTagsWithOr) {
                        filterBuilderForOrOperator.append("(").append(HealthCheck.TAGS).append("=").append(tag).append(")");
                    } else {
                        tagsBuilder.append("(").append(HealthCheck.TAGS).append("=").append(tag).append(")");
                        tagsAndClauses++;
                    }
                }
            }
        }
        boolean addedNameToOrBuilder = false;
        if (selector.names() != null) {
            for (String name : selector.names()) {
                name = name.trim();
                if (name.length() == 0) {
                    continue;
                }
                if (name.startsWith(HealthCheckFilter.OMIT_PREFIX)) {
                    // ommit tags always have to be added as and-clause
                    filterBuilder.append("(!(").append(HealthCheck.NAME).append("=").append(escapeOsgiFilterLiteral(name.substring(prefixLen))).append("))");
                } else {
                    // names are always ORd
                    filterBuilderForOrOperator.append("(").append(HealthCheck.NAME).append("=").append(escapeOsgiFilterLiteral(name)).append(")");
                    addedNameToOrBuilder = true;
                }
            }
        }
        if (addedNameToOrBuilder) {
            if (tagsAndClauses > 1) {
                filterBuilderForOrOperator.append("(&").append(tagsBuilder).append(")");
            } else {
                filterBuilderForOrOperator.append(tagsBuilder);
            }
        } else {
            filterBuilder.append(tagsBuilder);
        }
        // add "or" clause if we have accumulated any
        if (filterBuilderForOrOperator.length() > 0) {
            filterBuilder.append("(|").append(filterBuilderForOrOperator).append(")");
        }
        filterBuilder.append(")");
        return filterBuilder;
    }

    private Object escapeOsgiFilterLiteral(String name) {
        return name.replace("*", "\\*").replace("(", "\\(").replace(")", "\\)");
    }
}

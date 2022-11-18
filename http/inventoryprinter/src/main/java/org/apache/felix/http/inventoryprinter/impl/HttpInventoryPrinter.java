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
package org.apache.felix.http.inventoryprinter.impl;

import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.felix.inventory.Format;
import org.apache.felix.inventory.InventoryPrinter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.runtime.dto.DTOConstants;
import org.osgi.service.servlet.runtime.dto.ErrorPageDTO;
import org.osgi.service.servlet.runtime.dto.FailedErrorPageDTO;
import org.osgi.service.servlet.runtime.dto.FailedFilterDTO;
import org.osgi.service.servlet.runtime.dto.FailedListenerDTO;
import org.osgi.service.servlet.runtime.dto.FailedResourceDTO;
import org.osgi.service.servlet.runtime.dto.FailedServletContextDTO;
import org.osgi.service.servlet.runtime.dto.FailedServletDTO;
import org.osgi.service.servlet.runtime.dto.FilterDTO;
import org.osgi.service.servlet.runtime.dto.ListenerDTO;
import org.osgi.service.servlet.runtime.dto.ResourceDTO;
import org.osgi.service.servlet.runtime.dto.RuntimeDTO;
import org.osgi.service.servlet.runtime.dto.ServletContextDTO;
import org.osgi.service.servlet.runtime.dto.ServletDTO;

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;

/**
 * This is a web console plugin.
 */
public class HttpInventoryPrinter implements InventoryPrinter {

    private final BundleContext context;

    private final HttpServiceRuntime runtime;

    public HttpInventoryPrinter(final BundleContext context, final HttpServiceRuntime runtime) {
        this.context = context;
        this.runtime = runtime;
    }

    @Override
    public void print(final PrintWriter printWriter, final Format format, final boolean isZip) {
        if ( format == Format.TEXT ) {
            this.printConfiguration(printWriter);
        } else if ( format == Format.JSON ) {
            this.printConfigurationJSON(printWriter);
        }
    }

    private String getValueAsString(final Object value) {
        if ( value.getClass().isArray() ) {
            if (value instanceof long[]) {
                return Arrays.toString((long[])value);
            } else if (value instanceof int[]) {
                return Arrays.toString((int[])value);
            } else if (value instanceof double[]) {
                return Arrays.toString((double[])value);
            } else if (value instanceof byte[]) {
                return Arrays.toString((byte[])value);
            } else if (value instanceof float[]) {
                return Arrays.toString((float[])value);
            } else if (value instanceof short[]) {
                return Arrays.toString((short[])value);
            } else if (value instanceof boolean[]) {
                return Arrays.toString((boolean[])value);
            } else if (value instanceof char[]) {
                return Arrays.toString((char[])value);
            } else {
                return Arrays.toString((Object[])value);
            }
        }
        return value.toString();
    }

    private String getContextPath(final String path) {
        if ( path.length() == 0 ) {
            return "<root>";
        }
        return path;
    }

    private String getErrorText(final int reason) {
        switch ( reason ) {
            case DTOConstants.FAILURE_REASON_EXCEPTION_ON_INIT : return "Exception on init";
            case DTOConstants.FAILURE_REASON_NO_SERVLET_CONTEXT_MATCHING : return "No match";
            case DTOConstants.FAILURE_REASON_SERVICE_IN_USE : return "In use";
            case DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE : return "Not gettable";
            case DTOConstants.FAILURE_REASON_SERVLET_CONTEXT_FAILURE : return "Context failure";
            case DTOConstants.FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE : return "Shadowed";
            case DTOConstants.FAILURE_REASON_VALIDATION_FAILED : return "Invalid";
            default: return "unknown (".concat(String.valueOf(reason)).concat(")");
        }
    }

    private ServiceReference<?> getServiceReference(final long serviceId) {
        if ( serviceId > 0 ) {
            try {
                final ServiceReference<?>[] ref = this.context.getServiceReferences((String)null, "(" + Constants.SERVICE_ID + "=" + String.valueOf(serviceId) + ")");
                if ( ref != null && ref.length > 0 ) {
                    return ref[0];
                }
            } catch (final InvalidSyntaxException e) {
                // ignore
            }
        }
        return null;
    }
    private void printServiceIdAndRanking(final PrintWriter pw, final ServiceReference<?> ref, final long serviceId) {
        pw.print("Service ID : ");
        pw.println(String.valueOf(serviceId));
        int ranking = 0;
        if ( ref != null ) {
            final Object obj = ref.getProperty(Constants.SERVICE_RANKING);
            if ( obj instanceof Integer) {
                ranking = (Integer)obj;
            }
        }
        pw.print("Ranking : ");
        pw.println(String.valueOf(ranking));
        if ( ref != null ) {
            pw.print("Bundle : ");
            pw.print(ref.getBundle().getSymbolicName());
            pw.print(" <");
            pw.print(String.valueOf(ref.getBundle().getBundleId()));
            pw.println(">");
        }
    }

    private void printConfiguration(final PrintWriter pw) {
        final RuntimeDTO dto = this.runtime.getRuntimeDTO();

        pw.println("HTTP Service Details");
        pw.println("====================");
        pw.println();
        pw.println("Runtime Properties");
        pw.println("------------------");

        for(final Map.Entry<String, Object> prop : dto.serviceDTO.properties.entrySet()) {
            pw.print(prop.getKey());
            pw.print(" : ");
            pw.println(getValueAsString(prop.getValue()));
        }
        pw.println();
        for(final ServletContextDTO ctxDto : dto.servletContextDTOs ) {
            pw.print("Servlet Context ");
            pw.println(ctxDto.name);
            pw.println("-----------------------------------------------");

            pw.print("Path : ");
            pw.println(getContextPath(ctxDto.contextPath));
            printServiceIdAndRanking(pw, this.getServiceReference(ctxDto.serviceId), ctxDto.serviceId);
            pw.println();
            if ( ctxDto.servletDTOs.length > 0 ) {
                pw.println("Servlets");
                for (final ServletDTO servlet : ctxDto.servletDTOs) {
                    pw.print("Patterns : ");
                    pw.println(getValueAsString(servlet.patterns));
                    pw.print("Name : ");
                    pw.println(servlet.name);
                    pw.print("async : ");
                    pw.println(String.valueOf(servlet.asyncSupported));
                    printServiceIdAndRanking(pw, this.getServiceReference(servlet.serviceId), servlet.serviceId);
                    pw.println();
                }
                pw.println();
            }

            if ( ctxDto.filterDTOs.length > 0 ) {
                pw.println("Filters");
                for (final FilterDTO filter : ctxDto.filterDTOs) {
                    final List<String> patterns = new ArrayList<>();
                    patterns.addAll(Arrays.asList(filter.patterns));
                    patterns.addAll(Arrays.asList(filter.regexs));
                    for(final String name : filter.servletNames) {
                        patterns.add("Servlet : " + name);
                    }
                    Collections.sort(patterns);

                    pw.print("Patterns : ");
                    pw.println(patterns);
                    pw.print("Name : ");
                    pw.println(filter.name);
                    pw.print("async : ");
                    pw.println(String.valueOf(filter.asyncSupported));
                    pw.print("dispatcher : ");
                    pw.println(getValueAsString(filter.dispatcher));
                    printServiceIdAndRanking(pw, this.getServiceReference(filter.serviceId), filter.serviceId);
                    pw.println();
                }
                pw.println();
            }
            if ( ctxDto.resourceDTOs.length > 0 ) {
                pw.println("Resources");
                for (final ResourceDTO rsrc : ctxDto.resourceDTOs) {
                    pw.print("Patterns : ");
                    pw.println(getValueAsString(rsrc.patterns));
                    pw.print("Prefix : ");
                    pw.println(rsrc.prefix);
                    printServiceIdAndRanking(pw, this.getServiceReference(rsrc.serviceId), rsrc.serviceId);
                    pw.println();
                }
                pw.println();

            }
            if ( ctxDto.errorPageDTOs.length > 0 ) {
                pw.println("Error Pages");
                for (final ErrorPageDTO ep : ctxDto.errorPageDTOs) {
                    final List<String> patterns = new ArrayList<>();
                    for(final long p : ep.errorCodes) {
                        patterns.add(String.valueOf(p));
                    }
                    for(final String p : ep.exceptions) {
                        patterns.add(p);
                    }
                    pw.print("Patterns : ");
                    pw.println(patterns);
                    pw.print("Name : ");
                    pw.println(ep.name);
                    pw.print("async : ");
                    pw.println(String.valueOf(ep.asyncSupported));
                    printServiceIdAndRanking(pw, this.getServiceReference(ep.serviceId), ep.serviceId);
                    pw.println();
                }
                pw.println();
            }

            if ( ctxDto.listenerDTOs.length > 0 ) {
                pw.println("Listeners");
                for (final ListenerDTO ep : ctxDto.listenerDTOs) {
                    pw.print("Types : ");
                    pw.println(getValueAsString(ep.types));
                    printServiceIdAndRanking(pw, this.getServiceReference(ep.serviceId), ep.serviceId);
                    pw.println();
                }
                pw.println();
            }
            pw.println();
        }

        if ( dto.failedServletContextDTOs.length > 0 ) {
            for(final FailedServletContextDTO ctxDto : dto.failedServletContextDTOs ) {
                pw.print("Failed Servlet Context ");
                pw.println(ctxDto.name);
                pw.println("-----------------------------------------------");

                pw.print("Reason : ");
                pw.println(getErrorText(ctxDto.failureReason));
                pw.print("Path : ");
                pw.println(getContextPath(ctxDto.contextPath));
                printServiceIdAndRanking(pw, this.getServiceReference(ctxDto.serviceId), ctxDto.serviceId);
                pw.println();
            }
        }
        if ( dto.failedServletDTOs.length > 0 ) {
            pw.println("Failed Servlets");
            for (final FailedServletDTO servlet : dto.failedServletDTOs) {
                pw.print("Patterns : ");
                pw.println(getValueAsString(servlet.patterns));
                pw.print("Reason : ");
                pw.println(getErrorText(servlet.failureReason));
                pw.print("Name : ");
                pw.println(servlet.name);
                pw.print("async : ");
                pw.println(String.valueOf(servlet.asyncSupported));
                printServiceIdAndRanking(pw, this.getServiceReference(servlet.serviceId), servlet.serviceId);
                pw.println();
            }
            pw.println();
        }

        if ( dto.failedFilterDTOs.length > 0 ) {
            pw.println("Failed Filters");
            for (final FailedFilterDTO filter : dto.failedFilterDTOs) {
                final List<String> patterns = new ArrayList<>();
                patterns.addAll(Arrays.asList(filter.patterns));
                patterns.addAll(Arrays.asList(filter.regexs));
                for(final String name : filter.servletNames) {
                    patterns.add("Servlet : " + name);
                }
                Collections.sort(patterns);

                pw.print("Patterns : ");
                pw.println(patterns);
                pw.print("Reason : ");
                pw.println(getErrorText(filter.failureReason));
                pw.print("Name : ");
                pw.println(filter.name);
                pw.print("async : ");
                pw.println(String.valueOf(filter.asyncSupported));
                pw.print("dispatcher : ");
                pw.println(getValueAsString(filter.dispatcher));
                printServiceIdAndRanking(pw, this.getServiceReference(filter.serviceId), filter.serviceId);
                pw.println();
            }
            pw.println();
        }
        if ( dto.failedResourceDTOs.length > 0 ) {
            pw.println("Failed Resources");
            for (final FailedResourceDTO rsrc : dto.failedResourceDTOs) {
                pw.print("Patterns : ");
                pw.println(getValueAsString(rsrc.patterns));
                pw.print("Reason : ");
                pw.println(getErrorText(rsrc.failureReason));
                pw.print("Prefix : ");
                pw.println(rsrc.prefix);
                printServiceIdAndRanking(pw, this.getServiceReference(rsrc.serviceId), rsrc.serviceId);
                pw.println();
            }
            pw.println();

        }
        if ( dto.failedErrorPageDTOs.length > 0 ) {
            pw.println("Failed Error Pages");
            for (final FailedErrorPageDTO ep : dto.failedErrorPageDTOs) {
                final List<String> patterns = new ArrayList<>();
                for(final long p : ep.errorCodes) {
                    patterns.add(String.valueOf(p));
                }
                for(final String p : ep.exceptions) {
                    patterns.add(p);
                }
                pw.print("Patterns : ");
                pw.println(patterns);
                pw.print("Reason : ");
                pw.println(getErrorText(ep.failureReason));
                pw.print("Name : ");
                pw.println(ep.name);
                pw.print("async : ");
                pw.println(String.valueOf(ep.asyncSupported));
                printServiceIdAndRanking(pw, this.getServiceReference(ep.serviceId), ep.serviceId);
                pw.println();
            }
            pw.println();
        }

        if ( dto.failedListenerDTOs.length > 0 ) {
            pw.println("Failed Listeners");
            for (final FailedListenerDTO ep : dto.failedListenerDTOs) {
                pw.print("Types : ");
                pw.println(getValueAsString(ep.types));
                pw.print("Reason : ");
                pw.println(getErrorText(ep.failureReason));
                printServiceIdAndRanking(pw, this.getServiceReference(ep.serviceId), ep.serviceId);
                pw.println();
            }
            pw.println();
        }
        pw.println();
    }

    private void writeServiceIdAndRanking(final JsonGenerator gen, final ServiceReference<?> ref, final long serviceId) {
        gen.write("service.id", serviceId);
        int ranking = 0;
        if ( ref != null ) {
            final Object obj = ref.getProperty(Constants.SERVICE_RANKING);
            if ( obj instanceof Integer) {
                ranking = (Integer)obj;
            }
        }
        gen.write("service.ranking", ranking);
        if ( ref != null ) {
            gen.write("bundle.id", ref.getBundle().getBundleId());
        }
    }

    private void writeValueAsStringOrStringArray(final JsonGenerator gen, final String name, final Object value) {
        if ( value.getClass().isArray() ) {
            gen.writeStartArray(name);
            for(int i=0; i<Array.getLength(value); i++) {
                final Object v = Array.get(value, i);
                if (v instanceof Long) {
                    gen.write((long)v);
                } else if (v instanceof Integer) {
                    gen.write((int)v);
                } else if (v instanceof Double) {
                    gen.write((double)v);
                } else if (v instanceof Byte) {
                    gen.write((byte)v);
                } else if (v instanceof Float) {
                    gen.write((float)v);
                } else if (v instanceof Short) {
                    gen.write((short)v);
                } else if (v instanceof Boolean) {
                    gen.write((boolean)v);
                } else if (v instanceof Character) {
                    gen.write((char)v);
                } else {
                    gen.write(v.toString());
                }
            }
            gen.writeEnd();
        } else {
            gen.write(name, value.toString());
        }
    }

    private void writeRuntime(final JsonGenerator gen, final RuntimeDTO dto) {
        gen.writeStartObject("runtime");
            gen.writeStartObject("properties");
            for(final Map.Entry<String, Object> prop : dto.serviceDTO.properties.entrySet()) {
                writeValueAsStringOrStringArray(gen, prop.getKey(), prop.getValue());
            }
            gen.writeEnd();
        gen.writeEnd();
    }

    private void writeContext(final JsonGenerator gen, final ServletContextDTO ctxDto) {
        gen.writeStartObject();
        gen.write("name", ctxDto.name);
        gen.write("path", ctxDto.contextPath);

        writeServiceIdAndRanking(gen, this.getServiceReference(ctxDto.serviceId), ctxDto.serviceId);

        gen.writeStartArray("servlets");
        for (final ServletDTO servlet : ctxDto.servletDTOs) {
            gen.writeStartObject();
            gen.write("name", servlet.name);
            writeValueAsStringOrStringArray(gen, "patterns", servlet.patterns);
            gen.write("asyncSupported", servlet.asyncSupported);

            writeServiceIdAndRanking(gen, this.getServiceReference(servlet.serviceId), servlet.serviceId);
            gen.writeEnd();
        }
        gen.writeEnd();
    
        gen.writeStartArray("filters");
        for (final FilterDTO filter : ctxDto.filterDTOs) {
            gen.writeStartObject();
            gen.write("name", filter.name);
            writeValueAsStringOrStringArray(gen, "patterns", filter.patterns);
            writeValueAsStringOrStringArray(gen, "regexs", filter.regexs);
            writeValueAsStringOrStringArray(gen, "servletNames", filter.servletNames);
            gen.write("asyncSupported", filter.asyncSupported);
            writeValueAsStringOrStringArray(gen, "dispatcher", filter.dispatcher);
            writeServiceIdAndRanking(gen, this.getServiceReference(filter.serviceId), filter.serviceId);
            gen.writeEnd();
        }
        gen.writeEnd();

        gen.writeStartArray("resources");
        for (final ResourceDTO rsrc : ctxDto.resourceDTOs) {
            gen.writeStartObject();
            writeValueAsStringOrStringArray(gen, "patterns", rsrc.patterns);
            gen.write("prefix", rsrc.prefix);
            writeServiceIdAndRanking(gen, this.getServiceReference(rsrc.serviceId), rsrc.serviceId);
            gen.writeEnd();
        }
        gen.writeEnd();

        gen.writeStartArray("errorPages");
        for (final ErrorPageDTO ep : ctxDto.errorPageDTOs) {
            gen.writeStartObject();
            gen.write("name", ep.name);
            writeValueAsStringOrStringArray(gen, "exceptions", ep.exceptions);
            writeValueAsStringOrStringArray(gen, "errorCodes", ep.errorCodes);
            gen.write("asyncSupported", ep.asyncSupported);
            writeServiceIdAndRanking(gen, this.getServiceReference(ep.serviceId), ep.serviceId);
            gen.writeEnd();
        }
        gen.writeEnd();

        gen.writeStartArray("listeners");
        for (final ListenerDTO l : ctxDto.listenerDTOs) {
            gen.writeStartObject();
            writeValueAsStringOrStringArray(gen, "types", l.types);
            writeServiceIdAndRanking(gen, this.getServiceReference(l.serviceId), l.serviceId);
            gen.writeEnd();
        }
        gen.writeEnd();
        gen.writeEnd();
    }

    private void printConfigurationJSON(final PrintWriter pw) {
        final RuntimeDTO dto = this.runtime.getRuntimeDTO();

        try (final JsonGenerator gen = Json.createGenerator(pw)) {
            gen.writeStartObject();

                writeRuntime(gen, dto);

                gen.writeStartArray("contexts");
                for(final ServletContextDTO ctxDto : dto.servletContextDTOs ) {
                    writeContext(gen, ctxDto);
                }
                gen.writeEnd();

                gen.writeStartArray("failedContexts");
                for(final FailedServletContextDTO ctxDto : dto.failedServletContextDTOs ) {
                    gen.writeStartObject();
                    gen.write("name", ctxDto.name);
                    gen.write("path", ctxDto.contextPath);
                    gen.write("failureReason", ctxDto.failureReason);
                    gen.write("failureReasonText", getErrorText(ctxDto.failureReason));
                    writeServiceIdAndRanking(gen, this.getServiceReference(ctxDto.serviceId), ctxDto.serviceId);
                    gen.writeEnd();
                }
                gen.writeEnd();

                gen.writeStartArray("failedServlets");
                for (final FailedServletDTO servlet : dto.failedServletDTOs) {
                    gen.writeStartObject();
                    gen.write("name", servlet.name);
                    writeValueAsStringOrStringArray(gen, "patterns", servlet.patterns);
                    gen.write("asyncSupported", servlet.asyncSupported);
                    gen.write("failureReason", servlet.failureReason);
                    gen.write("failureReasonText", getErrorText(servlet.failureReason));
                    writeServiceIdAndRanking(gen, this.getServiceReference(servlet.serviceId), servlet.serviceId);
                    gen.writeEnd();
                }
                gen.writeEnd();

                gen.writeStartArray("failedFilters");
                for (final FailedFilterDTO filter : dto.failedFilterDTOs) {
                    gen.writeStartObject();
                    gen.write("name", filter.name);
                    writeValueAsStringOrStringArray(gen, "patterns", filter.patterns);
                    writeValueAsStringOrStringArray(gen, "regexs", filter.regexs);
                    writeValueAsStringOrStringArray(gen, "servletNames", filter.servletNames);
                    gen.write("asyncSupported", filter.asyncSupported);
                    writeValueAsStringOrStringArray(gen, "dispatcher", filter.dispatcher);
                    gen.write("failureReason", filter.failureReason);
                    gen.write("failureReasonText", getErrorText(filter.failureReason));
                    writeServiceIdAndRanking(gen, this.getServiceReference(filter.serviceId), filter.serviceId);
                    gen.writeEnd();
                }
                gen.writeEnd();

                gen.writeStartArray("failedResources");
                for (final FailedResourceDTO rsrc : dto.failedResourceDTOs) {
                    gen.writeStartObject();
                    writeValueAsStringOrStringArray(gen, "patterns", rsrc.patterns);
                    gen.write("prefix", rsrc.prefix);
                    gen.write("failureReason", rsrc.failureReason);
                    gen.write("failureReasonText", getErrorText(rsrc.failureReason));
                    writeServiceIdAndRanking(gen, this.getServiceReference(rsrc.serviceId), rsrc.serviceId);
                    gen.writeEnd();
                }
                gen.writeEnd();

                gen.writeStartArray("failedErrorPages");
                for (final FailedErrorPageDTO ep : dto.failedErrorPageDTOs) {
                    gen.writeStartObject();
                    gen.write("name", ep.name);
                    writeValueAsStringOrStringArray(gen, "exceptions", ep.exceptions);
                    writeValueAsStringOrStringArray(gen, "errorCodes", ep.errorCodes);
                    gen.write("asyncSupported", ep.asyncSupported);
                    gen.write("failureReason", ep.failureReason);
                    gen.write("failureReasonText", getErrorText(ep.failureReason));
                    writeServiceIdAndRanking(gen, this.getServiceReference(ep.serviceId), ep.serviceId);
                    gen.writeEnd();
                }
                gen.writeEnd();

                gen.writeStartArray("failedListeners");
                for (final FailedListenerDTO l : dto.failedListenerDTOs) {
                    gen.writeStartObject();
                    writeValueAsStringOrStringArray(gen, "types", l.types);
                    gen.write("failureReason", l.failureReason);
                    gen.write("failureReasonText", getErrorText(l.failureReason));
                    writeServiceIdAndRanking(gen, this.getServiceReference(l.serviceId), l.serviceId);
                    gen.writeEnd();
                }
                gen.writeEnd();

            gen.writeEnd();
        }
    }
}

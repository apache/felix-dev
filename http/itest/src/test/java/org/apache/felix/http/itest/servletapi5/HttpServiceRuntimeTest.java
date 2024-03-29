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
package org.apache.felix.http.itest.servletapi5;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.osgi.framework.Constants.SERVICE_RANKING;
import static org.osgi.service.servlet.runtime.HttpServiceRuntimeConstants.HTTP_SERVICE_ENDPOINT;
import static org.osgi.service.servlet.runtime.dto.DTOConstants.FAILURE_REASON_EXCEPTION_ON_INIT;
import static org.osgi.service.servlet.runtime.dto.DTOConstants.FAILURE_REASON_NO_SERVLET_CONTEXT_MATCHING;
import static org.osgi.service.servlet.runtime.dto.DTOConstants.FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE;
import static org.osgi.service.servlet.runtime.dto.DTOConstants.FAILURE_REASON_VALIDATION_FAILED;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_NAME;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_PATTERN;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PATTERN;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PREFIX;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_ERROR_PAGE;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_INIT_PARAM_PREFIX;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_TARGET;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.servlet.context.ServletContextHelper;
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.runtime.dto.FailedErrorPageDTO;
import org.osgi.service.servlet.runtime.dto.FailedServletDTO;
import org.osgi.service.servlet.runtime.dto.RequestInfoDTO;
import org.osgi.service.servlet.runtime.dto.RuntimeDTO;
import org.osgi.service.servlet.runtime.dto.ServletContextDTO;
import org.osgi.service.servlet.runtime.dto.ServletDTO;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionListener;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class HttpServiceRuntimeTest extends Servlet5BaseIntegrationTest {

    private static final String HTTP_CONTEXT_NAME = "org.osgi.service.http";

    private void registerServlet(String name, String path) throws InterruptedException {
        registerServlet(name, path, null);
    }

    private void registerServlet(String name, String path, String context) {
        List<Object> propertyEntries = Arrays.<Object>asList(
                HTTP_WHITEBOARD_SERVLET_PATTERN, path,
                HTTP_WHITEBOARD_SERVLET_NAME, name,
                HTTP_WHITEBOARD_CONTEXT_SELECT, context);

        Dictionary<String, ?> properties = createDictionary(context == null ?
                propertyEntries.subList(0, 4).toArray() : propertyEntries.toArray());

        registrations.add(m_context.registerService(Servlet.class.getName(), new TestServlet(), properties));
    }

    private void registerFilter(String name, String path) throws InterruptedException {
        registerFilter(name, path, null);
    }

    private void registerFilter(String name, String path, String context) {
        List<Object> propertyEntries = Arrays.<Object>asList(
                HTTP_WHITEBOARD_FILTER_PATTERN, path,
                HTTP_WHITEBOARD_FILTER_NAME, name,
                HTTP_WHITEBOARD_CONTEXT_SELECT, context);

        Dictionary<String, ?> properties = createDictionary(context == null ?
                propertyEntries.subList(0, 4).toArray() : propertyEntries.toArray());

        registrations.add(m_context.registerService(Filter.class.getName(), new TestFilter(), properties));
    }

    private void registerResource(String prefix, String path) throws InterruptedException {
        registerResource(prefix, path, null);
    }

    private void registerResource(String prefix, String path, String context) throws InterruptedException {
        List<Object> propertyEntries = Arrays.<Object>asList(
                HTTP_WHITEBOARD_RESOURCE_PATTERN, path,
                HTTP_WHITEBOARD_RESOURCE_PREFIX, prefix,
                HTTP_WHITEBOARD_CONTEXT_SELECT, context);

        Dictionary<String, ?> properties = createDictionary(context == null ?
                propertyEntries.subList(0, 4).toArray() : propertyEntries.toArray());

        registrations.add(m_context.registerService(TestResource.class.getName(), new TestResource(), properties));
    }

    private void registerErrorPage(String name, List<String> errors) throws InterruptedException {
        registerErrorPage(name, errors, null);
    }

    private void registerErrorPage(String name, List<String> errors, String context) {
        List<Object> propertyEntries = Arrays.<Object>asList(
                HTTP_WHITEBOARD_SERVLET_ERROR_PAGE, errors,
                HTTP_WHITEBOARD_SERVLET_NAME, name,
                HTTP_WHITEBOARD_CONTEXT_SELECT, context);

        Dictionary<String, ?> properties = createDictionary(context == null ?
                propertyEntries.subList(0, 4).toArray() : propertyEntries.toArray());

        registrations.add(m_context.registerService(Servlet.class.getName(), new TestServlet(), properties));
    }

    private void registerListener(Class<?> listenerClass, boolean useWithWhiteboard) throws InterruptedException {
        registerListener(listenerClass, useWithWhiteboard, null);
    }

    private void registerListener(Class<?> listenerClass, boolean useWithWhiteboard, String context) throws InterruptedException {
        List<Object> propertyEntries = Arrays.<Object>asList(
                HTTP_WHITEBOARD_LISTENER, useWithWhiteboard ? "true" : "false",
                HTTP_WHITEBOARD_CONTEXT_SELECT, context);

        Dictionary<String, ?> properties = createDictionary(context == null ?
                propertyEntries.subList(0, 2).toArray() : propertyEntries.toArray());

        final Object service;
        if ( ServletContextListener.class.getName().equals(listenerClass.getName())) {
            service = new ServletContextListener() {};
        } else if ( ServletContextAttributeListener.class.getName().equals(listenerClass.getName())) {
            service = new ServletContextAttributeListener() {};
        } else if ( ServletRequestListener.class.getName().equals(listenerClass.getName())) {
            service = new ServletRequestListener() {};
        } else if ( ServletRequestAttributeListener.class.getName().equals(listenerClass.getName())) {
            service = new ServletRequestAttributeListener() {};
        } else if ( HttpSessionListener.class.getName().equals(listenerClass.getName())) {
            service = new HttpSessionListener() {};
        } else if ( HttpSessionAttributeListener.class.getName().equals(listenerClass.getName())) {
            service = new HttpSessionAttributeListener() {};
        } else {
            throw new RuntimeException("Unknown listener class " + listenerClass.getName());
        }
        registrations.add(m_context.registerService(listenerClass.getName(), service, properties));
    }

    private ServiceRegistration<?> registerContext(String name, String path) throws InterruptedException {
        Dictionary<String, ?> properties = createDictionary(
                HTTP_WHITEBOARD_CONTEXT_NAME, name,
                HTTP_WHITEBOARD_CONTEXT_PATH, path);

        ServiceRegistration<?> contextRegistration = m_context.registerService(ServletContextHelper.class.getName(), new ServletContextHelper() {}, properties);
        registrations.add(contextRegistration);
        return contextRegistration;
    }

    @Before
    public void awaitServiceRuntime() throws Exception {
        awaitService(HttpServiceRuntime.class);
    }

    @Test
    public void httpRuntimeServiceIsAvailableAfterBundleActivation() throws Exception {
        HttpServiceRuntime serviceRuntime = getService(HttpServiceRuntime.class);
        assertNotNull("HttpServiceRuntime unavailable", serviceRuntime);

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();
        ServiceReferenceDTO serviceDTO = runtimeDTO.serviceDTO;

        assertNotNull(serviceDTO);
        assertNotNull(serviceDTO.properties);
        assertTrue(serviceDTO.properties.containsKey(HTTP_SERVICE_ENDPOINT));

        assertEquals(0, runtimeDTO.failedErrorPageDTOs.length);
        assertEquals(0, runtimeDTO.failedFilterDTOs.length);
        assertEquals(0, runtimeDTO.failedListenerDTOs.length);
        assertEquals(0, runtimeDTO.failedResourceDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs.length);
        assertEquals(0, runtimeDTO.failedServletDTOs.length);

        ServletContextDTO defaultContext = assertDefaultContext(runtimeDTO);

        assertEquals(0, defaultContext.attributes.size());
        assertTrue(0 > runtimeDTO.servletContextDTOs[0].serviceId);
        assertEquals("", defaultContext.contextPath);
        assertEquals(0, defaultContext.initParams.size());

        assertEquals(0, defaultContext.filterDTOs.length);
        assertEquals(0, defaultContext.servletDTOs.length);
        assertEquals(0, defaultContext.resourceDTOs.length);
        assertEquals(0, defaultContext.errorPageDTOs.length);
        assertEquals(0, defaultContext.listenerDTOs.length);
    }

    @Test
    public void dtosForSuccesfullyRegisteredServlets() throws Exception {
        //register first servlet
        this.setupLatches(1);
        registerServlet("testServlet 1", "/servlet_1");
        this.waitForInit();
        HttpServiceRuntime serviceRuntime = getService(HttpServiceRuntime.class);
        assertNotNull("HttpServiceRuntime unavailable", serviceRuntime);

        RuntimeDTO runtimeDTOWithFirstSerlvet = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithFirstSerlvet.failedServletDTOs.length);

        ServletContextDTO contextDTO = assertDefaultContext(runtimeDTOWithFirstSerlvet);
        assertEquals(1, contextDTO.servletDTOs.length);
        assertEquals("testServlet 1", contextDTO.servletDTOs[0].name);

        //register second servlet
        this.setupLatches(1);
        registerServlet("testServlet 2", "/servlet_2");
        this.waitForInit();
        RuntimeDTO runtimeDTOWithBothSerlvets = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithBothSerlvets.failedServletDTOs.length);

        contextDTO = assertDefaultContext(runtimeDTOWithBothSerlvets);
        assertEquals(2, contextDTO.servletDTOs.length);
        final Set<String> names = new HashSet<>();
        names.add(contextDTO.servletDTOs[0].name);
        names.add(contextDTO.servletDTOs[1].name);
        assertTrue(names.contains("testServlet 1"));
        assertTrue(names.contains("testServlet 2"));
    }

    @Test
    public void dtosForSuccesfullyRegisteredFilters() throws Exception {
        //register first filter
        this.setupLatches(1);
        registerFilter("testFilter 1", "/servlet_1");
        this.waitForInit();

        HttpServiceRuntime serviceRuntime = getService(HttpServiceRuntime.class);
        assertNotNull("HttpServiceRuntime unavailable", serviceRuntime);

        RuntimeDTO runtimeDTOWithFirstFilter = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithFirstFilter.failedFilterDTOs.length);

        ServletContextDTO contextDTO = assertDefaultContext(runtimeDTOWithFirstFilter);
        assertEquals(1, contextDTO.filterDTOs.length);
        assertEquals("testFilter 1", contextDTO.filterDTOs[0].name);

        //register second filter
        this.setupLatches(1);
        registerFilter("testFilter 2", "/servlet_1");
        this.waitForInit();

        RuntimeDTO runtimeDTOWithBothFilters = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithBothFilters.failedFilterDTOs.length);

        contextDTO = assertDefaultContext(runtimeDTOWithBothFilters);
        assertEquals(2, contextDTO.filterDTOs.length);
        assertEquals("testFilter 1", contextDTO.filterDTOs[0].name);
        assertEquals("testFilter 2", contextDTO.filterDTOs[1].name);
    }

    @Test
    public void dtosForSuccesfullyRegisteredResources() throws Exception {
        // register first resource service
        registerResource("/resources", "/resource_1/*");

        HttpServiceRuntime serviceRuntime = getService(HttpServiceRuntime.class);
        assertNotNull("HttpServiceRuntime unavailable", serviceRuntime);

        RuntimeDTO runtimeDTOWithFirstResource = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithFirstResource.failedResourceDTOs.length);

        ServletContextDTO contextDTO = assertDefaultContext(runtimeDTOWithFirstResource);
        assertEquals(1, contextDTO.resourceDTOs.length);
        assertEquals("/resources", contextDTO.resourceDTOs[0].prefix);
        assertArrayEquals(new String[] { "/resource_1/*" }, contextDTO.resourceDTOs[0].patterns);

        // register second resource service
        registerResource("/resources", "/resource_2/*");

        RuntimeDTO runtimeDTOWithBothResources = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithBothResources.failedResourceDTOs.length);

        contextDTO = assertDefaultContext(runtimeDTOWithBothResources);
        assertEquals(2, contextDTO.resourceDTOs.length);
        assertEquals("/resources", contextDTO.resourceDTOs[0].prefix);
        assertEquals(1, contextDTO.resourceDTOs[0].patterns.length);
        assertEquals(1, contextDTO.resourceDTOs[1].patterns.length);
        final Set<String> patterns = new HashSet<>();
        patterns.add(contextDTO.resourceDTOs[0].patterns[0]);
        patterns.add(contextDTO.resourceDTOs[1].patterns[0]);
        assertTrue(patterns.contains("/resource_1/*"));
        assertTrue(patterns.contains("/resource_2/*"));
    }

    @Test
    public void dtosForSuccesfullyRegisteredErrorPages() throws Exception {
        // register first error page
        this.setupLatches(1);
        registerErrorPage("error page 1", asList("404", NoSuchElementException.class.getName()));
        this.waitForInit();

        HttpServiceRuntime serviceRuntime = getService(HttpServiceRuntime.class);
        assertNotNull("HttpServiceRuntime unavailable", serviceRuntime);

        RuntimeDTO runtimeDTOWithFirstErrorPage = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithFirstErrorPage.failedServletDTOs.length);
        assertEquals(0, runtimeDTOWithFirstErrorPage.failedErrorPageDTOs.length);

        ServletContextDTO contextDTO = assertDefaultContext(runtimeDTOWithFirstErrorPage);
        assertEquals(1, contextDTO.errorPageDTOs.length);
        assertEquals("error page 1", contextDTO.errorPageDTOs[0].name);
        assertArrayEquals(new String[] { NoSuchElementException.class.getName() }, contextDTO.errorPageDTOs[0].exceptions);
        assertArrayEquals(new long[] { 404 }, contextDTO.errorPageDTOs[0].errorCodes);

        // register second error page
        this.setupLatches(1);
        registerErrorPage("error page 2", asList("500", ServletException.class.getName()));
        this.waitForInit();

        RuntimeDTO runtimeDTOWithBothErrorPages = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithBothErrorPages.failedServletDTOs.length);
        assertEquals(0, runtimeDTOWithBothErrorPages.failedErrorPageDTOs.length);

        contextDTO = assertDefaultContext(runtimeDTOWithBothErrorPages);
        assertEquals(2, contextDTO.errorPageDTOs.length);
        assertEquals("error page 1", contextDTO.errorPageDTOs[0].name);
        assertEquals("error page 2", contextDTO.errorPageDTOs[1].name);
        assertArrayEquals(new String[] { ServletException.class.getName() }, contextDTO.errorPageDTOs[1].exceptions);
        assertArrayEquals(new long[] { 500 }, contextDTO.errorPageDTOs[1].errorCodes);
    }

    @Test
    public void dtosForSuccesfullyRegisteredErrorPageForClientErrorCodes() throws Exception {
        dtosForSuccesfullyRegisteredErrorPageWithWildcardErrorCode("4xx", 400);
    }

    @Test
    public void dtosForSuccesfullyRegisteredErrorPageForClientErrorCodesCaseInsensitive() throws Exception {
        dtosForSuccesfullyRegisteredErrorPageWithWildcardErrorCode("4xX", 400);
    }

    @Test
    public void dtosForSuccesfullyRegisteredErrorPageForServerErrorCodes() throws Exception {
        dtosForSuccesfullyRegisteredErrorPageWithWildcardErrorCode("5xx", 500);
    }

    @Test
    public void dtosForSuccesfullyRegisteredErrorPageForServerErrorCodesCaseInsensitive() throws Exception {
        dtosForSuccesfullyRegisteredErrorPageWithWildcardErrorCode("5XX", 500);
    }

    public void dtosForSuccesfullyRegisteredErrorPageWithWildcardErrorCode(String code, long startCode) throws Exception {
        this.setupLatches(1);
        registerErrorPage("error page 1", asList(code));
        this.waitForInit();

        HttpServiceRuntime serviceRuntime = getService(HttpServiceRuntime.class);
        assertNotNull("HttpServiceRuntime unavailable", serviceRuntime);

        RuntimeDTO runtimeDTOWithErrorPage = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithErrorPage.failedServletDTOs.length);
        assertEquals(0, runtimeDTOWithErrorPage.failedErrorPageDTOs.length);

        ServletContextDTO contextDTO = assertDefaultContext(runtimeDTOWithErrorPage);
        assertEquals(1, contextDTO.errorPageDTOs.length);
        assertEquals("error page 1", contextDTO.errorPageDTOs[0].name);
        assertContainsAllHundredFrom(startCode, contextDTO.errorPageDTOs[0].errorCodes);
    }

    private void assertContainsAllHundredFrom(Long start, long[] errorCodes) {
        assertEquals(100, errorCodes.length);
        SortedSet<Long> distinctErrorCodes = new TreeSet<>();
        for (Long code : errorCodes)
        {
            distinctErrorCodes.add(code);
        }
        assertEquals(100, distinctErrorCodes.size());
        assertEquals(start, distinctErrorCodes.first());
        assertEquals(Long.valueOf(start + 99), distinctErrorCodes.last());
    }

    @Test
    public void dtosForSuccesfullyRegisteredListeners() throws Exception {
        // register a servlet context listenere as first listener
        registerListener(ServletContextListener.class, true);
        awaitService(ServletContextListener.class);

        HttpServiceRuntime serviceRuntime = getService(HttpServiceRuntime.class);
        assertNotNull("HttpServiceRuntime unavailable", serviceRuntime);

        RuntimeDTO runtimeDTOWithFirstListener = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithFirstListener.failedListenerDTOs.length);

        ServletContextDTO contextDTO = assertDefaultContext(runtimeDTOWithFirstListener);
        assertEquals(1, contextDTO.listenerDTOs.length);
        assertEquals(ServletContextListener.class.getName(), contextDTO.listenerDTOs[0].types[0]);

        // register all other listener types
        registerListener(ServletContextAttributeListener.class, true);
        registerListener(ServletRequestListener.class, true);
        registerListener(ServletRequestAttributeListener.class, true);
        registerListener(HttpSessionListener.class, true);
        registerListener(HttpSessionAttributeListener.class, true);

        awaitService(ServletContextAttributeListener.class);
        awaitService(ServletRequestListener.class);
        awaitService(ServletRequestAttributeListener.class);
        awaitService(HttpSessionListener.class);
        awaitService(HttpSessionAttributeListener.class);

        RuntimeDTO runtimeDTOWithAllListeners = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithAllListeners.failedListenerDTOs.length);

        contextDTO = assertDefaultContext(runtimeDTOWithAllListeners);

        assertEquals(6, contextDTO.listenerDTOs.length);
        assertEquals(ServletContextListener.class.getName(), contextDTO.listenerDTOs[0].types[0]);
        assertEquals(ServletContextAttributeListener.class.getName(), contextDTO.listenerDTOs[1].types[0]);
        assertEquals(ServletRequestListener.class.getName(), contextDTO.listenerDTOs[2].types[0]);
        assertEquals(ServletRequestAttributeListener.class.getName(), contextDTO.listenerDTOs[3].types[0]);
        assertEquals(HttpSessionListener.class.getName(), contextDTO.listenerDTOs[4].types[0]);
        assertEquals(HttpSessionAttributeListener.class.getName(), contextDTO.listenerDTOs[5].types[0]);
    }

    @Test
    public void dtosForSuccesfullyRegisteredContexts() throws Exception {
        // register first additional context
        registerContext("contextA", "/contextA");

        HttpServiceRuntime serviceRuntime = getService(HttpServiceRuntime.class);
        assertNotNull("HttpServiceRuntime unavailable", serviceRuntime);

        RuntimeDTO runtimeDTOWithAdditionalContext = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithAdditionalContext.failedServletContextDTOs.length);
        assertEquals(3, runtimeDTOWithAdditionalContext.servletContextDTOs.length);

        // default context is last, as it has the lowest service ranking
        assertEquals(HTTP_CONTEXT_NAME, runtimeDTOWithAdditionalContext.servletContextDTOs[0].name);
        assertEquals("", runtimeDTOWithAdditionalContext.servletContextDTOs[0].contextPath);
        assertEquals("contextA", runtimeDTOWithAdditionalContext.servletContextDTOs[1].name);
        assertEquals("/contextA", runtimeDTOWithAdditionalContext.servletContextDTOs[1].contextPath);
        assertEquals("default", runtimeDTOWithAdditionalContext.servletContextDTOs[2].name);
        assertEquals("", runtimeDTOWithAdditionalContext.servletContextDTOs[2].contextPath);

        // register second additional context
        registerContext("contextB", "/contextB");

        RuntimeDTO runtimeDTOWithAllContexts = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTOWithAllContexts.failedServletContextDTOs.length);
        assertEquals(4, runtimeDTOWithAllContexts.servletContextDTOs.length);

        // default context is last, as it has the lowest service ranking
        assertEquals(HTTP_CONTEXT_NAME, runtimeDTOWithAdditionalContext.servletContextDTOs[0].name);
        assertEquals("", runtimeDTOWithAdditionalContext.servletContextDTOs[0].contextPath);
        assertEquals("contextA", runtimeDTOWithAllContexts.servletContextDTOs[1].name);
        assertEquals("/contextA", runtimeDTOWithAllContexts.servletContextDTOs[1].contextPath);
        assertEquals("contextB", runtimeDTOWithAllContexts.servletContextDTOs[2].name);
        assertEquals("/contextB", runtimeDTOWithAllContexts.servletContextDTOs[2].contextPath);
        assertEquals("default", runtimeDTOWithAllContexts.servletContextDTOs[3].name);
        assertEquals("", runtimeDTOWithAllContexts.servletContextDTOs[3].contextPath);
    }

    @Test
    public void successfulSetup() throws InterruptedException {
        long counter = this.getRuntimeCounter();

        registerContext("test-context", "/test-context");

        counter = this.waitForRuntime(counter);

        registerServlet("default servlet", "/default");
        counter = this.waitForRuntime(counter);

        registerFilter("default filter", "/default");
        counter = this.waitForRuntime(counter);

        registerErrorPage("default error page", asList(Exception.class.getName()));
        counter = this.waitForRuntime(counter);

        registerResource("/", "/default/resource");
        counter = this.waitForRuntime(counter);

        registerListener(ServletRequestListener.class, true);
        counter = this.waitForRuntime(counter);

        registerServlet("context servlet", "/default", "(" + HTTP_WHITEBOARD_CONTEXT_NAME + "=test-context)");
        counter = this.waitForRuntime(counter);

        registerFilter("context filter", "/default", "(" + HTTP_WHITEBOARD_CONTEXT_NAME + "=test-context)");
        counter = this.waitForRuntime(counter);

        registerErrorPage("context error page", asList("500"), "(" + HTTP_WHITEBOARD_CONTEXT_NAME + "=test-context)");
        counter = this.waitForRuntime(counter);

        registerResource("/", "/test-contextd/resource", "(" + HTTP_WHITEBOARD_CONTEXT_NAME + "=test-context)");
        counter = this.waitForRuntime(counter);

        registerListener(ServletRequestListener.class, true, "(" + HTTP_WHITEBOARD_CONTEXT_NAME + "=test-context)");
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        final RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTO.failedErrorPageDTOs.length);
        assertEquals(0, runtimeDTO.failedFilterDTOs.length);
        assertEquals(0, runtimeDTO.failedListenerDTOs.length);
        assertEquals(0, runtimeDTO.failedResourceDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs.length);
        assertEquals(0, runtimeDTO.failedServletDTOs.length);

        assertEquals(3, runtimeDTO.servletContextDTOs.length);
        assertEquals(HTTP_CONTEXT_NAME, runtimeDTO.servletContextDTOs[0].name);
        assertEquals("test-context", runtimeDTO.servletContextDTOs[1].name);
        assertEquals("default", runtimeDTO.servletContextDTOs[2].name);

        ServletContextDTO defaultContextDTO = runtimeDTO.servletContextDTOs[2];
        long contextServiceId = defaultContextDTO.serviceId;

        assertEquals(Arrays.toString(defaultContextDTO.servletDTOs), 2, defaultContextDTO.servletDTOs.length);
        assertServlet(defaultContextDTO.servletDTOs, "default servlet", contextServiceId);
        assertServlet(defaultContextDTO.servletDTOs, "default error page", contextServiceId);

        assertEquals(1, defaultContextDTO.filterDTOs.length);
        assertEquals("default filter", defaultContextDTO.filterDTOs[0].name);
        assertEquals(contextServiceId, defaultContextDTO.filterDTOs[0].servletContextId);
        assertEquals(1, defaultContextDTO.errorPageDTOs.length);
        assertEquals(Exception.class.getName(), defaultContextDTO.errorPageDTOs[0].exceptions[0]);
        assertEquals(contextServiceId, defaultContextDTO.errorPageDTOs[0].servletContextId);
        assertEquals(1, defaultContextDTO.listenerDTOs.length);
        assertEquals(ServletRequestListener.class.getName(), defaultContextDTO.listenerDTOs[0].types[0]);
        assertEquals(contextServiceId, defaultContextDTO.listenerDTOs[0].servletContextId);

        ServletContextDTO testContextDTO = runtimeDTO.servletContextDTOs[1];
        contextServiceId = testContextDTO.serviceId;

        assertEquals(2, testContextDTO.servletDTOs.length);
        assertServlet(testContextDTO.servletDTOs, "context servlet", contextServiceId);
        assertServlet(testContextDTO.servletDTOs, "context error page", contextServiceId);

        assertEquals(1, testContextDTO.filterDTOs.length);
        assertEquals("context filter", testContextDTO.filterDTOs[0].name);
        assertEquals(contextServiceId, testContextDTO.filterDTOs[0].servletContextId);
        assertEquals(1, testContextDTO.errorPageDTOs.length);
        assertEquals(500L, testContextDTO.errorPageDTOs[0].errorCodes[0]);
        assertEquals(contextServiceId, testContextDTO.errorPageDTOs[0].servletContextId);
        assertEquals(1, testContextDTO.listenerDTOs.length);
        assertEquals(ServletRequestListener.class.getName(), testContextDTO.listenerDTOs[0].types[0]);
        assertEquals(contextServiceId, testContextDTO.listenerDTOs[0].servletContextId);
    }

    private void assertServlet(final ServletDTO[] servletDTOs,
    		final String name,
    		final long contextServiceId) {
    	assertNotNull(servletDTOs);
    	for(final ServletDTO dto : servletDTOs) {
    		if ( name.equals(dto.name) && contextServiceId == dto.servletContextId ) {
    			return;
    		}
    	}
    	fail("Servlet with name " + name + " and context id " + contextServiceId + " not found in " + Arrays.toString(servletDTOs));
	}

	@Test
    public void exceptionInServletInitAppearsAsFailure() throws ServletException, InterruptedException {
        Dictionary<String, ?> properties = createDictionary(
                HTTP_WHITEBOARD_SERVLET_PATTERN, "/servlet",
                HTTP_WHITEBOARD_SERVLET_NAME, "servlet");

        long counter = this.getRuntimeCounter();

        @SuppressWarnings("serial")
        Servlet failingServlet = new TestServlet() {
            @Override
            public void init() throws ServletException {
                super.init();
                throw new ServletException();
            }
        };

        registrations.add(m_context.registerService(Servlet.class.getName(), failingServlet, properties));
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();
        assertEquals(1, runtimeDTO.failedServletDTOs.length);
        assertEquals("servlet", runtimeDTO.failedServletDTOs[0].name);
        assertEquals(FAILURE_REASON_EXCEPTION_ON_INIT, runtimeDTO.failedServletDTOs[0].failureReason);
    }

    @Test
    public void exceptionInServletInitDuringServletRemovalAppearsAsFailure() throws ServletException, InterruptedException {
        Dictionary<String, ?> properties1 = createDictionary(
            HTTP_WHITEBOARD_SERVLET_PATTERN, "/servlet1",
            HTTP_WHITEBOARD_SERVLET_NAME, "servlet1");

        long counter = this.getRuntimeCounter();

        @SuppressWarnings("serial")
        Servlet failingServlet1 = new TestServlet() {
            boolean isInit = false;
            @Override
            public void init() throws ServletException {
                //fail when initialized the second time
                if (isInit) {
                    throw new ServletException();
                }
                isInit = true;
                super.init();
            }
        };

        Dictionary<String, ?> properties2 = createDictionary(
            HTTP_WHITEBOARD_SERVLET_PATTERN, "/servlet2",
            HTTP_WHITEBOARD_SERVLET_NAME, "servlet2");

        @SuppressWarnings("serial")
        Servlet failingServlet2 = new TestServlet() {
            boolean isInit = false;
            @Override
            public void init() throws ServletException {
                //fail when initialized the second time
                if (isInit) {
                    throw new ServletException();
                }
                isInit = true;
                super.init();
            }
        };

        Dictionary<String, ?> propertiesShadowing = createDictionary(
            HTTP_WHITEBOARD_SERVLET_PATTERN, asList("/servlet1", "/servlet2"),
            HTTP_WHITEBOARD_SERVLET_NAME, "servletShadowing",
            SERVICE_RANKING, Integer.MAX_VALUE);

        Servlet servletShadowing = new TestServlet();

        registrations.add(m_context.registerService(Servlet.class.getName(), failingServlet1, properties1));
        counter = this.waitForRuntime(counter);
        registrations.add(m_context.registerService(Servlet.class.getName(), failingServlet2, properties2));
        counter = this.waitForRuntime(counter);

        ServiceRegistration<?> shadowingRegistration = m_context.registerService(Servlet.class.getName(), servletShadowing, propertiesShadowing);
        registrations.add(shadowingRegistration);
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();
        assertEquals(2, runtimeDTO.failedServletDTOs.length);
        assertEquals(FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE, runtimeDTO.failedServletDTOs[0].failureReason);
        assertEquals(FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE, runtimeDTO.failedServletDTOs[1].failureReason);

        shadowingRegistration.unregister();
        counter = this.waitForRuntime(counter);

        runtimeDTO = serviceRuntime.getRuntimeDTO();
        assertEquals(2, runtimeDTO.failedServletDTOs.length);
        assertEquals(FAILURE_REASON_EXCEPTION_ON_INIT, runtimeDTO.failedServletDTOs[0].failureReason);
        assertEquals(FAILURE_REASON_EXCEPTION_ON_INIT, runtimeDTO.failedServletDTOs[1].failureReason);
    }

    @Test
    public void exceptionInFilterInitAppearsAsFailure() throws ServletException, InterruptedException {
        Dictionary<String, ?> properties = createDictionary(
                HTTP_WHITEBOARD_FILTER_PATTERN, "/filter",
                HTTP_WHITEBOARD_FILTER_NAME, "filter");

        long counter = this.getRuntimeCounter();

        Filter failingFilter = new TestFilter() {
            @Override
            public void init(FilterConfig config) throws ServletException {
                super.init(config);
                throw new ServletException();
            }
        };

        registrations.add(m_context.registerService(Filter.class.getName(), failingFilter, properties));
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();
        assertEquals(1, runtimeDTO.failedFilterDTOs.length);
        assertEquals("filter", runtimeDTO.failedFilterDTOs[0].name);
        assertEquals(FAILURE_REASON_EXCEPTION_ON_INIT, runtimeDTO.failedFilterDTOs[0].failureReason);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.1
    @Test
    public void hiddenDefaultContextAppearsAsFailure() throws InterruptedException {
        long counter = this.getRuntimeCounter();
        registerContext("default", "");
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();
        assertEquals(1, runtimeDTO.failedServletContextDTOs.length);
        assertEquals("default", runtimeDTO.failedServletContextDTOs[0].name);
        assertDefaultContext(runtimeDTO);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.1
    @Test
    public void contextHelperWithDuplicateNameAppearsAsFailure() throws InterruptedException {
        long counter = this.getRuntimeCounter();
        ServiceRegistration<?> firstContextReg = registerContext("contextA", "/first");
        counter = this.waitForRuntime(counter);
        registerContext("contextA", "/second");
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(1, runtimeDTO.failedServletContextDTOs.length);
        assertEquals("contextA", runtimeDTO.failedServletContextDTOs[0].name);
        assertEquals("/second", runtimeDTO.failedServletContextDTOs[0].contextPath);
        assertEquals(FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE, runtimeDTO.failedServletContextDTOs[0].failureReason);

        assertEquals(3, runtimeDTO.servletContextDTOs.length);
        assertEquals(HTTP_CONTEXT_NAME, runtimeDTO.servletContextDTOs[0].name);
        assertEquals("default", runtimeDTO.servletContextDTOs[2].name);

        assertEquals("contextA", runtimeDTO.servletContextDTOs[1].name);
        assertEquals("/first", runtimeDTO.servletContextDTOs[1].contextPath);

        firstContextReg.unregister();
        counter = this.waitForRuntime(counter);

        runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTO.failedServletContextDTOs.length);

        assertEquals(3, runtimeDTO.servletContextDTOs.length);
        assertEquals(HTTP_CONTEXT_NAME, runtimeDTO.servletContextDTOs[0].name);
        assertEquals("default", runtimeDTO.servletContextDTOs[2].name);

        assertEquals("contextA", runtimeDTO.servletContextDTOs[1].name);
        assertEquals("/second", runtimeDTO.servletContextDTOs[1].contextPath);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.1
    @Test
    public void missingContextHelperNameAppearsAsFailure() {
        Dictionary<String, ?> properties = createDictionary(HTTP_WHITEBOARD_CONTEXT_PATH, "");

        long counter = this.getRuntimeCounter();
        registrations.add(m_context.registerService(ServletContextHelper.class.getName(), new ServletContextHelper() {}, properties));
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(1, runtimeDTO.failedServletContextDTOs.length);
        assertEquals(null, runtimeDTO.failedServletContextDTOs[0].name);
        assertEquals(FAILURE_REASON_VALIDATION_FAILED, runtimeDTO.failedServletContextDTOs[0].failureReason);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.1
    @Test
    public void invalidContextHelperNameAppearsAsFailure() throws InterruptedException {
        long counter = this.getRuntimeCounter();
        registerContext("context A", "");
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(1, runtimeDTO.failedServletContextDTOs.length);
        assertEquals("context A", runtimeDTO.failedServletContextDTOs[0].name);
        assertEquals(FAILURE_REASON_VALIDATION_FAILED, runtimeDTO.failedServletContextDTOs[0].failureReason);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.1
    @Test
    public void invalidContextHelperPathAppearsAsFailure() throws InterruptedException {
        long counter = this.getRuntimeCounter();
        registerContext("contextA", "#");
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(1, runtimeDTO.failedServletContextDTOs.length);
        assertEquals("#", runtimeDTO.failedServletContextDTOs[0].contextPath);
        assertEquals(FAILURE_REASON_VALIDATION_FAILED, runtimeDTO.failedServletContextDTOs[0].failureReason);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.3
    @Test
    public void selectionOfNonExistingContextHelperAppearsAsFailure() throws InterruptedException {
        long counter = this.getRuntimeCounter();
        registerServlet("servlet 1", "/", "(" + HTTP_WHITEBOARD_CONTEXT_NAME + "=contextA)");
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(1, runtimeDTO.failedServletDTOs.length);
        assertEquals("servlet 1", runtimeDTO.failedServletDTOs[0].name);
        assertEquals(FAILURE_REASON_NO_SERVLET_CONTEXT_MATCHING, runtimeDTO.failedServletDTOs[0].failureReason);

        registerContext("contextA", "/contextA");
        counter = this.waitForRuntime(counter);

        runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTO.failedServletDTOs.length);
        assertEquals(3, runtimeDTO.servletContextDTOs.length);
        assertEquals("contextA", runtimeDTO.servletContextDTOs[1].name);
        assertEquals(1, runtimeDTO.servletContextDTOs[1].servletDTOs.length);
        assertEquals("servlet 1", runtimeDTO.servletContextDTOs[1].servletDTOs[0].name);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.3
    @Test
    public void differentTargetIsIgnored() throws InterruptedException {
        Dictionary<String, ?> properties = createDictionary(
                HTTP_WHITEBOARD_SERVLET_PATTERN, "/servlet",
                HTTP_WHITEBOARD_SERVLET_NAME, "servlet",
                HTTP_WHITEBOARD_TARGET, "(org.osgi.service.http.port=8282)");

        long counter = this.getRuntimeCounter();
        registrations.add(m_context.registerService(Servlet.class.getName(), new TestServlet(), properties));
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTO.failedServletDTOs.length);

        ServletContextDTO defaultContext = assertDefaultContext(runtimeDTO);
        assertEquals(0, defaultContext.servletDTOs.length);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.4
    @Test
    public void servletWithoutNameGetsFullyQualifiedName() throws InterruptedException {
        Dictionary<String, ?> properties = createDictionary(HTTP_WHITEBOARD_SERVLET_PATTERN, "/servlet");

        long counter = this.getRuntimeCounter();
        registrations.add(m_context.registerService(Servlet.class.getName(), new TestServlet(), properties));
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTO.failedServletDTOs.length);

        ServletContextDTO defaultContext = assertDefaultContext(serviceRuntime.getRuntimeDTO());
        assertEquals(1, defaultContext.servletDTOs.length);
        assertEquals(TestServlet.class.getName(), defaultContext.servletDTOs[0].name);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.4.1
    @Test
    public void patternAndErrorPageSpecified() throws InterruptedException {
        Dictionary<String, ?> properties = createDictionary(
                HTTP_WHITEBOARD_SERVLET_PATTERN, "/servlet",
                HTTP_WHITEBOARD_SERVLET_NAME, "servlet",
                HTTP_WHITEBOARD_SERVLET_ERROR_PAGE, asList("400"));

        long counter = this.getRuntimeCounter();
        registrations.add(m_context.registerService(Servlet.class.getName(), new TestServlet(), properties));
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();
        ServletContextDTO defaultContext = assertDefaultContext(runtimeDTO);

        assertEquals(0, runtimeDTO.failedErrorPageDTOs.length);
        assertEquals(0, runtimeDTO.failedServletDTOs.length);

        assertEquals(1, defaultContext.servletDTOs.length);
        assertEquals(1, defaultContext.errorPageDTOs.length);

        assertEquals("servlet", defaultContext.servletDTOs[0].name);
        assertEquals("servlet", defaultContext.errorPageDTOs[0].name);

        assertArrayEquals(new String[] { "/servlet" }, defaultContext.servletDTOs[0].patterns);
        assertArrayEquals(new long[] { 400 }, defaultContext.errorPageDTOs[0].errorCodes);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.4.1
    @Test
    public void multipleServletsForSamePatternChoosenByServiceRankingRules() throws InterruptedException {
        long counter = this.getRuntimeCounter();
        registerServlet("servlet 1", "/pathcollision");
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTO.failedServletDTOs.length);
        ServletContextDTO defaultContext = assertDefaultContext(runtimeDTO);
        assertEquals(1, defaultContext.servletDTOs.length);

        Dictionary<String, ?> properties = createDictionary(
                HTTP_WHITEBOARD_SERVLET_PATTERN, "/pathcollision",
                HTTP_WHITEBOARD_SERVLET_NAME, "servlet 2",
                SERVICE_RANKING, Integer.MAX_VALUE);

        TestServlet testServlet = new TestServlet();
        ServiceRegistration<?> higherRankingServlet = m_context.registerService(Servlet.class.getName(), testServlet, properties);
        registrations.add(higherRankingServlet);
        counter = this.waitForRuntime(counter);

        RuntimeDTO runtimeWithShadowedServlet = serviceRuntime.getRuntimeDTO();

        defaultContext = assertDefaultContext(runtimeWithShadowedServlet);
        assertEquals(1, defaultContext.servletDTOs.length);

        assertEquals(1, runtimeWithShadowedServlet.failedServletDTOs.length);
        FailedServletDTO failedServletDTO = runtimeWithShadowedServlet.failedServletDTOs[0];
        assertEquals("servlet 1", failedServletDTO.name);
        assertEquals(FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE, failedServletDTO.failureReason);

        higherRankingServlet.unregister();
        counter = this.waitForRuntime(counter);

        runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTO.failedServletDTOs.length);
        defaultContext = assertDefaultContext(runtimeDTO);
        assertEquals(1, defaultContext.servletDTOs.length);
        assertEquals("servlet 1", defaultContext.servletDTOs[0].name);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.4.1
    @Test
    public void multipleErrorPagesForSameErrorCodeChoosenByServiceRankingRules() throws InterruptedException {
        long counter = this.getRuntimeCounter();
        registerErrorPage("error page 1", asList(NullPointerException.class.getName(), "500"));
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        ServletContextDTO defaultContext = assertDefaultContext(runtimeDTO);

        assertEquals(0, runtimeDTO.failedErrorPageDTOs.length);
        assertEquals(1, defaultContext.errorPageDTOs.length);

        Dictionary<String, ?> properties = createDictionary(
                HTTP_WHITEBOARD_SERVLET_ERROR_PAGE, asList("500", IllegalArgumentException.class.getName()),
                HTTP_WHITEBOARD_SERVLET_NAME, "error page 2",
                SERVICE_RANKING, Integer.MAX_VALUE);

        TestServlet testServlet = new TestServlet();
        ServiceRegistration<?> higherRankingServlet = m_context.registerService(Servlet.class.getName(), testServlet, properties);
        registrations.add(higherRankingServlet);
        counter = this.waitForRuntime(counter);

        RuntimeDTO runtimeWithShadowedErrorPage = serviceRuntime.getRuntimeDTO();

        defaultContext = assertDefaultContext(runtimeWithShadowedErrorPage);

        assertEquals(2, defaultContext.errorPageDTOs.length);
        assertEquals("error page 2", defaultContext.errorPageDTOs[0].name);
        assertArrayEquals(new long[] { 500 }, defaultContext.errorPageDTOs[0].errorCodes);
        assertArrayEquals(new String[] { IllegalArgumentException.class.getName() }, defaultContext.errorPageDTOs[0].exceptions);
        assertEquals("error page 1", defaultContext.errorPageDTOs[1].name);
        assertEquals(0, defaultContext.errorPageDTOs[1].errorCodes.length);
        assertArrayEquals(new String[] { NullPointerException.class.getName() }, defaultContext.errorPageDTOs[1].exceptions);

        assertEquals(1, runtimeWithShadowedErrorPage.failedErrorPageDTOs.length);
        FailedErrorPageDTO failedErrorPageDTO = runtimeWithShadowedErrorPage.failedErrorPageDTOs[0];
        assertEquals("error page 1", failedErrorPageDTO.name);
        assertArrayEquals(new long[] { 500 }, failedErrorPageDTO.errorCodes);
        assertEquals(0, failedErrorPageDTO.exceptions.length);
        assertEquals(FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE, failedErrorPageDTO.failureReason);

        higherRankingServlet.unregister();
        counter = this.waitForRuntime(counter);

        runtimeDTO = serviceRuntime.getRuntimeDTO();

        defaultContext = assertDefaultContext(runtimeDTO);

        assertEquals(0, runtimeDTO.failedErrorPageDTOs.length);
        assertEquals(1, defaultContext.errorPageDTOs.length);
        assertEquals("error page 1", defaultContext.errorPageDTOs[0].name);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.7
    @Test
    public void invalidListenerPopertyValueAppearsAsFailure() throws Exception {
        Dictionary<String, ?> properties = createDictionary(HTTP_WHITEBOARD_LISTENER, "invalid");

        registrations.add(m_context.registerService(ServletRequestListener.class.getName(), new ServletRequestListener() {}, properties));

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(1, runtimeDTO.failedListenerDTOs.length);
        assertEquals(FAILURE_REASON_VALIDATION_FAILED, runtimeDTO.failedListenerDTOs[0].failureReason);
    }

    // As specified in OSGi Compendium Release 6, Chapter 140.8
    @Test
    public void contextReplacedWithHigherRankingContext() throws Exception {
        long counter = this.getRuntimeCounter();
        ServiceRegistration<?> firstContext = registerContext("test-context", "/first");
        Long firstContextId = (Long) firstContext.getReference().getProperty(Constants.SERVICE_ID);
        counter = this.waitForRuntime(counter);

        registerServlet("servlet", "/servlet", "(" + HTTP_WHITEBOARD_CONTEXT_NAME + "=test-context)");
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTO.failedServletContextDTOs.length);
        assertEquals(3, runtimeDTO.servletContextDTOs.length);
        assertEquals(firstContextId.longValue(), runtimeDTO.servletContextDTOs[1].serviceId);
        assertEquals("test-context", runtimeDTO.servletContextDTOs[1].name);
        assertEquals("/first", runtimeDTO.servletContextDTOs[1].contextPath);
        assertEquals("default", runtimeDTO.servletContextDTOs[2].name);
        assertEquals(HTTP_CONTEXT_NAME, runtimeDTO.servletContextDTOs[0].name);

        assertEquals(1, runtimeDTO.servletContextDTOs[1].servletDTOs.length);
        assertEquals("servlet", runtimeDTO.servletContextDTOs[1].servletDTOs[0].name);

        Dictionary<String, ?> properties = createDictionary(
                HTTP_WHITEBOARD_CONTEXT_NAME, "test-context",
                HTTP_WHITEBOARD_CONTEXT_PATH, "/second",
                SERVICE_RANKING, Integer.MAX_VALUE);

        ServiceRegistration<?> secondContext = m_context.registerService(ServletContextHelper.class.getName(), new ServletContextHelper() {}, properties);
        registrations.add(secondContext);
        Long secondContextId = (Long) secondContext.getReference().getProperty(Constants.SERVICE_ID);
        counter = this.waitForRuntime(counter);

        runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(1, runtimeDTO.failedServletContextDTOs.length);
        assertEquals(firstContextId.longValue(), runtimeDTO.failedServletContextDTOs[0].serviceId);
        assertEquals("test-context", runtimeDTO.failedServletContextDTOs[0].name);
        assertEquals("/first", runtimeDTO.failedServletContextDTOs[0].contextPath);

        assertEquals(3, runtimeDTO.servletContextDTOs.length);

        final List<String> names = new ArrayList<>();
        for(final ServletContextDTO dto : runtimeDTO.servletContextDTOs)
        {
            names.add(dto.name);
        }
        final int httpContextIndex = names.indexOf(HTTP_CONTEXT_NAME);
        final int secondContextIndex = names.indexOf("test-context");
        final int defaultContextIndex = names.indexOf("default");
        assertEquals(secondContextId.longValue(), runtimeDTO.servletContextDTOs[secondContextIndex].serviceId);
        assertEquals("test-context", runtimeDTO.servletContextDTOs[secondContextIndex].name);
        assertEquals("/second", runtimeDTO.servletContextDTOs[secondContextIndex].contextPath);
        assertEquals("default", runtimeDTO.servletContextDTOs[defaultContextIndex].name);
        assertEquals(HTTP_CONTEXT_NAME, runtimeDTO.servletContextDTOs[httpContextIndex].name);

        assertEquals(1, runtimeDTO.servletContextDTOs[secondContextIndex].servletDTOs.length);
        assertEquals("servlet", runtimeDTO.servletContextDTOs[secondContextIndex].servletDTOs[0].name);

        secondContext.unregister();
        counter = this.waitForRuntime(counter);

        runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTO.failedServletContextDTOs.length);
        assertEquals(3, runtimeDTO.servletContextDTOs.length);
        assertEquals(firstContextId.longValue(), runtimeDTO.servletContextDTOs[1].serviceId);
        assertEquals("test-context", runtimeDTO.servletContextDTOs[1].name);
        assertEquals("/first", runtimeDTO.servletContextDTOs[1].contextPath);
        assertEquals("default", runtimeDTO.servletContextDTOs[2].name);
        assertEquals(HTTP_CONTEXT_NAME, runtimeDTO.servletContextDTOs[0].name);

        assertEquals(1, runtimeDTO.servletContextDTOs[1].servletDTOs.length);
        assertEquals("servlet", runtimeDTO.servletContextDTOs[1].servletDTOs[0].name);
    }

    @Test
    public void namedServletIsNotIgnored() throws InterruptedException {
        // Neither pattern nor error page specified
        Dictionary<String, ?> properties = createDictionary(HTTP_WHITEBOARD_SERVLET_NAME, "servlet");

        long counter = this.getRuntimeCounter();
        registrations.add(m_context.registerService(Servlet.class.getName(), new TestServlet(), properties));
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTO.failedServletContextDTOs.length);
        ServletContextDTO defaultContext = assertDefaultContext(runtimeDTO);
        assertEquals(1, defaultContext.servletDTOs.length);
        assertEquals(0, defaultContext.servletDTOs[0].patterns.length);
        assertEquals("servlet", defaultContext.servletDTOs[0].name);
    }

    @Test
    public void dtosAreIndependentCopies() throws Exception {
        //register first servlet
        Dictionary<String, ?> properties = createDictionary(
                HTTP_WHITEBOARD_SERVLET_PATTERN, "/test",
                HTTP_WHITEBOARD_SERVLET_NAME, "servlet 1",
                HTTP_WHITEBOARD_SERVLET_INIT_PARAM_PREFIX + "test", "testValue");

        long counter = this.getRuntimeCounter();
        registrations.add(m_context.registerService(Servlet.class.getName(), new TestServlet(), properties));
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTOWithFirstSerlvet = serviceRuntime.getRuntimeDTO();

        //register second servlet
        registerServlet("testServlet 2", "/servlet_2");
        counter = this.waitForRuntime(counter);

        RuntimeDTO runtimeDTOWithTwoSerlvets = serviceRuntime.getRuntimeDTO();

        assertNotSame(runtimeDTOWithFirstSerlvet, runtimeDTOWithTwoSerlvets);

        ServletContextDTO defaultContextFirstServlet = assertDefaultContext(runtimeDTOWithFirstSerlvet);
        ServletContextDTO defaultContextTwoServlets = assertDefaultContext(runtimeDTOWithTwoSerlvets);
        assertNotSame(defaultContextFirstServlet.servletDTOs[0].patterns,
                defaultContextTwoServlets.servletDTOs[0].patterns);

        boolean mapsModifiable = true;
        try {
            defaultContextTwoServlets.servletDTOs[0].initParams.clear();
        } catch (UnsupportedOperationException e) {
            mapsModifiable = false;
        }

        if (mapsModifiable) {
            assertNotSame(defaultContextFirstServlet.servletDTOs[0].initParams,
                    defaultContextTwoServlets.servletDTOs[0].initParams);
        }
    }

    @Test
    public void requestInfoDTO() throws Exception {
        long counter = this.getRuntimeCounter();
        registerServlet("servlet", "/default");
        counter = this.waitForRuntime(counter);
        registerFilter("filter1", "/default");
        counter = this.waitForRuntime(counter);
        registerFilter("filter2", "/default");
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        ServletContextDTO defaultContext = assertDefaultContext(serviceRuntime.getRuntimeDTO());
        long defaultContextId = defaultContext.serviceId;

        RequestInfoDTO requestInfoDTO = serviceRuntime.calculateRequestInfoDTO("/default");
        assertEquals("/default", requestInfoDTO.path);
        assertEquals(defaultContextId, requestInfoDTO.servletContextId);
        assertEquals("servlet", requestInfoDTO.servletDTO.name);
        assertEquals(2, requestInfoDTO.filterDTOs.length);
        assertEquals("filter1", requestInfoDTO.filterDTOs[0].name);
        assertEquals("filter2", requestInfoDTO.filterDTOs[1].name);
    }

    @Test
    public void serviceEndpointPropertyIsSet() {
        // if there is more than one network interface, there might be more than one endpoint!
        final String[] endpoint = (String[]) m_context.getServiceReference(HttpServiceRuntime.class).getProperty(HTTP_SERVICE_ENDPOINT);
        assertNotNull(endpoint);
        assertTrue(Arrays.toString(endpoint), endpoint.length > 0);
        assertTrue(endpoint[0].startsWith("http://"));
        assertTrue(endpoint[0].endsWith(":8080/"));
    }

    /**
     * Test for FELIX-5319
     * @throws Exception
     */
    @Test
    public void testCombinedServletAndResourceRegistration() throws Exception {
        long counter = this.getRuntimeCounter();
        // register single component as Servlet and Resource
        final String servletPath = "/hello/sayHello";
        final String servletName = "Hello World";
        final String rsrcPattern = "/hello/static/*";
        final String rsrcPrefix = "/static";

        List<Object> propertyEntries = Arrays.<Object>asList(
                HTTP_WHITEBOARD_SERVLET_PATTERN, servletPath,
                HTTP_WHITEBOARD_SERVLET_NAME, servletName,
                HTTP_WHITEBOARD_RESOURCE_PATTERN, rsrcPattern,
                HTTP_WHITEBOARD_RESOURCE_PREFIX, rsrcPrefix);

        Dictionary<String, ?> properties = createDictionary(propertyEntries.toArray());

        registrations.add(m_context.registerService(Servlet.class.getName(), new TestServlet(), properties));
        counter = this.waitForRuntime(counter);

        final HttpServiceRuntime serviceRuntime = this.getHttpServiceRuntime();

        RuntimeDTO runtimeDTO = serviceRuntime.getRuntimeDTO();

        assertEquals(0, runtimeDTO.failedServletDTOs.length);
        assertEquals(0, runtimeDTO.failedResourceDTOs.length);

        // check servlet registration
        ServletContextDTO contextDTO = assertDefaultContext(runtimeDTO);
        assertEquals(1, contextDTO.servletDTOs.length);
        assertEquals(servletName, contextDTO.servletDTOs[0].name);
        assertEquals(1, contextDTO.servletDTOs[0].patterns.length);
        assertEquals(servletPath, contextDTO.servletDTOs[0].patterns[0]);

        // check resource registration
        assertEquals(1, contextDTO.resourceDTOs.length);
        assertEquals(1, contextDTO.resourceDTOs[0].patterns.length);
        assertEquals(rsrcPattern, contextDTO.resourceDTOs[0].patterns[0]);
        assertEquals(rsrcPrefix, contextDTO.resourceDTOs[0].prefix);
    }

    private ServletContextDTO assertDefaultContext(RuntimeDTO runtimeDTO) {
        assertTrue(1 < runtimeDTO.servletContextDTOs.length);
        assertEquals(HTTP_CONTEXT_NAME, runtimeDTO.servletContextDTOs[0].name);
        assertEquals("default", runtimeDTO.servletContextDTOs[1].name);
        return runtimeDTO.servletContextDTOs[1];
    }

    public static class TestResource {
        // Tagging class
    }
}

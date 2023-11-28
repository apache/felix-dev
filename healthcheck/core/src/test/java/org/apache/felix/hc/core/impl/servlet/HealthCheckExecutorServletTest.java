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
package org.apache.felix.hc.core.impl.servlet;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.Result.Status;
import org.apache.felix.hc.api.execution.HealthCheckExecutionOptions;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckExecutor;
import org.apache.felix.hc.api.execution.HealthCheckMetadata;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.apache.felix.hc.core.impl.executor.ExecutionResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

public class HealthCheckExecutorServletTest {

    @InjectMocks
    private HealthCheckExecutorServlet healthCheckExecutorServlet = new HealthCheckExecutorServlet();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HealthCheckExecutor healthCheckExecutor;

    @Mock
    private ResultHtmlSerializer htmlSerializer;

    @Mock
    private ResultJsonSerializer jsonSerializer;

    @Mock
    private ResultTxtSerializer txtSerializer;

    @Mock
    private ResultTxtVerboseSerializer verboseTxtSerializer;

    @Mock
    private ServiceReference hcServiceRef;
    
    @Mock
    private BundleContext bundleContext;

    @Mock
    private PrintWriter writer;

    @Mock
    private HealthCheckExecutorServletConfiguration healthCheckExecutorServletConfig;
    
    @Before
    public void setup() throws IOException {
        initMocks(this);

        doReturn(500L).when(hcServiceRef).getProperty(Constants.SERVICE_ID);
        doReturn(writer).when(response).getWriter();
        
        doReturn(true).when(healthCheckExecutorServletConfig).disabled();
        doReturn("OK:200").when(healthCheckExecutorServletConfig).httpStatusMapping();
        doReturn(new String[0]).when(healthCheckExecutorServletConfig).tags();
        doReturn(HealthCheckExecutorServlet.FORMAT_HTML).when(healthCheckExecutorServletConfig).format();
        doReturn(new String[] {HealthCheckExecutorServlet.FORMAT_HTML,
            HealthCheckExecutorServlet.FORMAT_JSON,
            HealthCheckExecutorServlet.FORMAT_JSONP,
            HealthCheckExecutorServlet.FORMAT_TXT,
            HealthCheckExecutorServlet.FORMAT_VERBOSE_TXT}).when(healthCheckExecutorServletConfig).allowed_formats();
        doReturn("/hc").when(healthCheckExecutorServletConfig).servletPath();
        healthCheckExecutorServlet.activate(healthCheckExecutorServletConfig, bundleContext);
    }

    @Test
    public void testDoGetHtml() throws ServletException, IOException {

        final String testTag = "testTag";
        doReturn(testTag).when(request).getParameter(HealthCheckExecutorServlet.PARAM_TAGS.name);
        doReturn("false").when(request).getParameter(HealthCheckExecutorServlet.PARAM_COMBINE_TAGS_WITH_OR.name);
        final List<HealthCheckExecutionResult> executionResults = getExecutionResults(Result.Status.CRITICAL);
        doReturn(executionResults).when(healthCheckExecutor).execute(selector(new String[] { testTag }, new String[0]),
                eq(new HealthCheckExecutionOptions()));

        healthCheckExecutorServlet.doGet(request, response);

        verifyNoInteractions(jsonSerializer);
        verifyNoInteractions(txtSerializer);
        verifyNoInteractions(verboseTxtSerializer);
        verify(htmlSerializer)
                .serialize(resultEquals(new Result(Result.Status.CRITICAL, "Overall Status CRITICAL")), eq(executionResults),
                        any(List.class), eq(false));
    }

    @Test
    public void testDoGetNameAndTagInPath() throws ServletException, IOException {

        final String testTag = "testTag";
        final String testName = "test name";

        doReturn(testTag + "," + testName).when(request).getPathInfo();
        doReturn("false").when(request).getParameter(HealthCheckExecutorServlet.PARAM_COMBINE_TAGS_WITH_OR.name);
        final List<HealthCheckExecutionResult> executionResults = getExecutionResults(Result.Status.CRITICAL);
        doReturn(executionResults).when(healthCheckExecutor).execute(selector(new String[] { testTag }, new String[] { testName }),
                eq(new HealthCheckExecutionOptions()));

        healthCheckExecutorServlet.doGet(request, response);

        verify(request, never()).getParameter(HealthCheckExecutorServlet.PARAM_TAGS.name);
        verify(request, never()).getParameter(HealthCheckExecutorServlet.PARAM_NAMES.name);
        verifyNoInteractions(jsonSerializer);
        verifyNoInteractions(txtSerializer);
        verifyNoInteractions(verboseTxtSerializer);
        verify(htmlSerializer)
                .serialize(resultEquals(new Result(Result.Status.CRITICAL, "Overall Status CRITICAL")), eq(executionResults),
                        any(List.class), eq(false));
    }

    @Test
    public void testDoGetJson() throws ServletException, IOException {

        final String testTag = "testTag";
        doReturn("true").when(request).getParameter(HealthCheckExecutorServlet.PARAM_COMBINE_TAGS_WITH_OR.name);
        int timeout = 5000;
        doReturn(timeout + "").when(request).getParameter(HealthCheckExecutorServlet.PARAM_OVERRIDE_GLOBAL_TIMEOUT.name);
        doReturn("/" + testTag + ".json").when(request).getPathInfo();
        final List<HealthCheckExecutionResult> executionResults = getExecutionResults(Result.Status.WARN);
        HealthCheckExecutionOptions options = new HealthCheckExecutionOptions();
        options.setCombineTagsWithOr(true);
        options.setOverrideGlobalTimeout(timeout);
        doReturn(executionResults).when(healthCheckExecutor).execute(selector(new String[] { testTag }, new String[0]), eq(options));

        healthCheckExecutorServlet.doGet(request, response);

        verifyNoInteractions(htmlSerializer);
        verifyNoInteractions(txtSerializer);
        verifyNoInteractions(verboseTxtSerializer);
        verify(jsonSerializer).serialize(resultEquals(new Result(Result.Status.WARN, "Overall Status WARN")), eq(executionResults),
                any(),
                eq(false));

    }

    @Test
    public void testDoGetTxt() throws ServletException, IOException {

        final String testTag = "testTag";
        doReturn(testTag).when(request).getParameter(HealthCheckExecutorServlet.PARAM_TAGS.name);
        doReturn(HealthCheckExecutorServlet.FORMAT_TXT).when(request).getParameter(HealthCheckExecutorServlet.PARAM_FORMAT.name);
        doReturn("true").when(request).getParameter(HealthCheckExecutorServlet.PARAM_COMBINE_TAGS_WITH_OR.name);
        int timeout = 5000;
        doReturn(timeout + "").when(request).getParameter(HealthCheckExecutorServlet.PARAM_OVERRIDE_GLOBAL_TIMEOUT.name);
        final List<HealthCheckExecutionResult> executionResults = getExecutionResults(Result.Status.WARN);
        HealthCheckExecutionOptions options = new HealthCheckExecutionOptions();
        options.setCombineTagsWithOr(true);
        options.setOverrideGlobalTimeout(timeout);

        doReturn(executionResults).when(healthCheckExecutor).execute(selector(new String[] { testTag }, new String[0]), eq(options));

        healthCheckExecutorServlet.doGet(request, response);

        verifyNoInteractions(htmlSerializer);
        verifyNoInteractions(jsonSerializer);
        verifyNoInteractions(verboseTxtSerializer);
        verify(txtSerializer).serialize(resultEquals(new Result(Result.Status.WARN, "Overall Status WARN")));

    }

    @Test
    public void testDoGetVerboseTxt() throws ServletException, IOException {

        String testTag = "testTag";
        doReturn(testTag).when(request).getParameter(HealthCheckExecutorServlet.PARAM_TAGS.name);
        doReturn(HealthCheckExecutorServlet.FORMAT_VERBOSE_TXT).when(request).getParameter(HealthCheckExecutorServlet.PARAM_FORMAT.name);

        List<HealthCheckExecutionResult> executionResults = getExecutionResults(Result.Status.WARN);
        doReturn(executionResults).when(healthCheckExecutor).execute(selector(new String[] { testTag }, new String[0]),
                any(HealthCheckExecutionOptions.class));

        healthCheckExecutorServlet.doGet(request, response);

        verifyNoInteractions(htmlSerializer);
        verifyNoInteractions(jsonSerializer);
        verifyNoInteractions(txtSerializer);
        verify(verboseTxtSerializer).serialize(resultEquals(new Result(Result.Status.WARN, "Overall Status WARN")), eq(executionResults),
                eq(false));

    }

    private List<HealthCheckExecutionResult> getExecutionResults(Result.Status worstStatus) {
        List<HealthCheckExecutionResult> results = new ArrayList<HealthCheckExecutionResult>();
        results.add(new ExecutionResult(new HealthCheckMetadata(hcServiceRef), new Result(worstStatus, worstStatus.name()), 100));
        results.add(new ExecutionResult(new HealthCheckMetadata(hcServiceRef), new Result(Result.Status.OK, "OK"), 100));
        return results;
    }

    @Test
    public void testGetStatusMapping() throws ServletException {

        Map<Status, Integer> statusMapping = healthCheckExecutorServlet.getStatusMapping("CRITICAL:500");
        assertEquals(statusMapping.get(Result.Status.OK), (Integer) 200);
        assertEquals(statusMapping.get(Result.Status.WARN), (Integer) 200);
        assertEquals(statusMapping.get(Result.Status.TEMPORARILY_UNAVAILABLE), (Integer) 503);
        assertEquals(statusMapping.get(Result.Status.CRITICAL), (Integer) 500);
        assertEquals(statusMapping.get(Result.Status.HEALTH_CHECK_ERROR), (Integer) 500);

        statusMapping = healthCheckExecutorServlet.getStatusMapping("OK:333");
        assertEquals(statusMapping.get(Result.Status.OK), (Integer) 333);
        assertEquals(statusMapping.get(Result.Status.WARN), (Integer) 333);
        assertEquals(statusMapping.get(Result.Status.TEMPORARILY_UNAVAILABLE), (Integer) 503);
        assertEquals(statusMapping.get(Result.Status.CRITICAL), (Integer) 503);
        assertEquals(statusMapping.get(Result.Status.HEALTH_CHECK_ERROR), (Integer) 500);

        statusMapping = healthCheckExecutorServlet.getStatusMapping("OK:200,WARN:418,CRITICAL:503,TEMPORARILY_UNAVAILABLE:503,HEALTH_CHECK_ERROR:500");
        assertEquals(statusMapping.get(Result.Status.OK), (Integer) 200);
        assertEquals(statusMapping.get(Result.Status.WARN), (Integer) 418);
        assertEquals(statusMapping.get(Result.Status.TEMPORARILY_UNAVAILABLE), (Integer) 503);
        assertEquals(statusMapping.get(Result.Status.CRITICAL), (Integer) 503);
        assertEquals(statusMapping.get(Result.Status.HEALTH_CHECK_ERROR), (Integer) 500);

        statusMapping = healthCheckExecutorServlet.getStatusMapping("WARN:418,HEALTH_CHECK_ERROR:503");
        assertEquals(statusMapping.get(Result.Status.OK), (Integer) 200);
        assertEquals(statusMapping.get(Result.Status.WARN), (Integer) 418);
        assertEquals(statusMapping.get(Result.Status.TEMPORARILY_UNAVAILABLE), (Integer) 503);
        assertEquals(statusMapping.get(Result.Status.CRITICAL), (Integer) 503);
        assertEquals(statusMapping.get(Result.Status.HEALTH_CHECK_ERROR), (Integer) 503);

    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetStatusMappingInvalidToken() throws ServletException {
        healthCheckExecutorServlet.getStatusMapping("CRITICAL");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetStatusMappingInvalidStatus() throws ServletException {
        healthCheckExecutorServlet.getStatusMapping("INVALID:200");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetStatusMappingInvalidStatusCode() throws ServletException {
        healthCheckExecutorServlet.getStatusMapping("CRITICAL:xxx");
    }

    static Result resultEquals(Result expected) {
        return argThat(new ResultMatcher(expected));
    }

    static class ResultMatcher implements ArgumentMatcher<Result> {

        private final Result expectedResult;

        public ResultMatcher(Result expected) {
            this.expectedResult = expected;
        }

        public boolean matches(Result actualResult) {
            return actualResult.getStatus().equals(expectedResult.getStatus()); // simple status matching only sufficient for this test case
        }

        @Override
        public String toString() {
            return expectedResult == null ? null : expectedResult.toString();
        }
    }

    HealthCheckSelector selector(final String[] tags, final String[] names) {
        return argThat(new ArgumentMatcher<HealthCheckSelector>() {
            @Override
            public boolean matches(HealthCheckSelector actual) {
                if (actual instanceof HealthCheckSelector) {
                    HealthCheckSelector actualSelector = (HealthCheckSelector) actual;
                    return Arrays.equals(actualSelector.tags(), tags.length == 0 ? new String[] { "" } : tags) &&
                            Arrays.equals(actualSelector.names(), names.length == 0 ? new String[] { "" } : names);
                } else {
                    return false;
                }
            }
        });
    }

}

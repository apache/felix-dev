/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.http.base.internal.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.felix.http.base.internal.HttpConfig;
import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.handler.ServletHandler;
import org.apache.felix.http.base.internal.handler.WhiteboardServletHandler;
import org.apache.felix.http.base.internal.runtime.ServletContextHelperInfo;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.apache.felix.http.base.internal.runtime.dto.FailedDTOHolder;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.service.servlet.runtime.dto.ServletContextDTO;
import org.osgi.service.servlet.runtime.dto.ServletDTO;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.Servlet;

public class HandlerRegistryTest {
    private HandlerRegistry registry;

    @Before
    public void setUp() {
        // @formatter:off
        final ServletContextHelperInfo servletContextHelperInfo = new ServletContextHelperInfo(
                Integer.MIN_VALUE, 
                HttpConfig.DEFAULT_CONTEXT_SERVICE_ID, 
                HttpWhiteboardConstants.HTTP_WHITEBOARD_DEFAULT_CONTEXT_NAME, 
                "/", 
                null) {
            // nothing to override
        };
        // @formatter:on

        registry = new HandlerRegistry(new HttpConfig());

        registry.add(new PerContextHandlerRegistry(servletContextHelperInfo, registry.getConfig()));
    }

    @Test
    public void testInitialSetup() {
        final FailedDTOHolder holder = new FailedDTOHolder();
        final ServletContextDTO dto = new ServletContextDTO();
        dto.serviceId = HttpConfig.DEFAULT_CONTEXT_SERVICE_ID;

        assertTrue(registry.getRuntimeInfo(dto, holder));

        registry.shutdown();

        assertFalse(registry.getRuntimeInfo(dto, holder));
    }

    @Test
    public void testAddRemoveServlet() throws Exception {
        final FailedDTOHolder holder = new FailedDTOHolder();
        final ServletContextDTO dto = new ServletContextDTO();
        dto.serviceId = HttpConfig.DEFAULT_CONTEXT_SERVICE_ID;
        dto.servletDTOs = new ServletDTO[0];

        final ServletHandler handler = createServletHandler(1L, "foo", "/foo");

        assertTrue(registry.getRuntimeInfo(dto, holder));
        assertEquals("Precondition", 0, dto.servletDTOs.length);

        registry.getRegistry(HttpConfig.DEFAULT_CONTEXT_SERVICE_ID).registerServlet(handler);

        assertTrue(registry.getRuntimeInfo(dto, holder));
        assertEquals(1, dto.servletDTOs.length);

        final ServletHandler handler2 = createServletHandler(2L, "bar", "/bar");

        registry.getRegistry(HttpConfig.DEFAULT_CONTEXT_SERVICE_ID).registerServlet(handler2);
        assertTrue(registry.getRuntimeInfo(dto, holder));
        assertEquals(2, dto.servletDTOs.length);

        final ServletHandler handler3 = createServletHandler(3L, "zar", "/foo");

        registry.getRegistry(HttpConfig.DEFAULT_CONTEXT_SERVICE_ID).registerServlet(handler3);
        assertTrue(registry.getRuntimeInfo(dto, holder));
        assertEquals(2, dto.servletDTOs.length);
        assertEquals(1, holder.failedServletDTOs.size());

        registry.shutdown();
    }

    private static ServletInfo createServletInfo(final long id, final String name, final String... paths)
            throws InvalidSyntaxException {
        final BundleContext bCtx = mock(BundleContext.class);
        final Bundle bundle = mock(Bundle.class);
        when(bundle.getBundleContext()).thenReturn(bCtx);

        @SuppressWarnings("unchecked")
        final ServiceReference<Servlet> ref = mock(ServiceReference.class);
        when(ref.getBundle()).thenReturn(bundle);
        when(ref.getProperty(Constants.SERVICE_ID)).thenReturn(id);
        when(ref.getProperty(Constants.SERVICE_RANKING)).thenReturn(Integer.MAX_VALUE);
        when(ref.getProperty(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME)).thenReturn(name);
        when(ref.getProperty(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN)).thenReturn(paths);
        when(ref.getPropertyKeys()).thenReturn(new String[0]);
        final ServletInfo si = new ServletInfo(ref);

        return si;
    }

    private static ServletHandler createServletHandler(final long id, final String name, final String... paths)
            throws InvalidSyntaxException {
        final ServletInfo si = createServletInfo(id, name, paths);

        @SuppressWarnings("unchecked")
        final ServiceObjects<Servlet> so = mock(ServiceObjects.class);
        final BundleContext bundleContext = mock(BundleContext.class);
        when(bundleContext.getServiceObjects(si.getServiceReference())).thenReturn(so);

        final Servlet servlet = mock(Servlet.class);
        when(so.getService()).thenReturn(servlet);

        final ExtServletContext servletContext = mock(ExtServletContext.class);

        return new WhiteboardServletHandler(id, servletContext, si, bundleContext, servlet);
    }

/*
    @Test
    public void testAddServletWhileSameServletAddedDuringInit() throws Exception
    {
        final HandlerRegistry hr = new HandlerRegistry();

        Servlet servlet = Mockito.mock(Servlet.class);
        final ServletInfo info = new ServletInfo("bar", "/bar", 0, null, servlet, null);
        final ServletHandler otherHandler = new ServletHandler(null, null, info, info.getServlet());

        Mockito.doAnswer(new Answer<Void>()
        {
            boolean registered = false;
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                if (!registered)
                {
                    registered = true;
                    // sneakily register another handler with this servlet before this
                    // one has finished calling init()
                    hr.addServlet(null, otherHandler);
                }
                return null;
            }
        }).when(servlet).init(Mockito.any(ServletConfig.class));

        final ServletInfo info2 = new ServletInfo("foo", "/foo", 0, null, servlet, null);
        ServletHandler handler = new ServletHandler(null, null, info2, info2.getServlet());
        try
        {
            hr.addServlet(null, handler);

//            fail("Should not have allowed the servlet to be added as it was already "
//                    + "added before init was finished");

        }
        catch (ServletException ne)
        {
            // good
        }
        assertArrayEquals(new ServletHandler[] {otherHandler, handler}, hr.getServlets());
    }

    @Test
    public void testAddServletWhileSameAliasAddedDuringInit() throws Exception
    {
        final HandlerRegistry hr = new HandlerRegistry();

        Servlet otherServlet = Mockito.mock(Servlet.class);
        final ServletInfo info = new ServletInfo("bar", "/foo", 0, null, otherServlet, null);
        final ServletHandler otherHandler = new ServletHandler(null, null, info, info.getServlet());

        Servlet servlet = Mockito.mock(Servlet.class);
        Mockito.doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                // sneakily register another servlet before this one has finished calling init()
                hr.addServlet(null, otherHandler);
                return null;
            }
        }).when(servlet).init(Mockito.any(ServletConfig.class));

        final ServletInfo info2 = new ServletInfo("foo", "/foo", 0, null, servlet, null);
        ServletHandler handler = new ServletHandler(null, null, info2, info2.getServlet());

        try
        {
            hr.addServlet(null, handler);
            fail("Should not have allowed the servlet to be added as another one got in there with the same alias");
        }
        catch (NamespaceException ne)
        {
            // good
        }
        assertArrayEquals(new ServletHandler[] {otherHandler}, hr.getServlets());
        Mockito.verify(servlet, Mockito.times(1)).destroy();

        assertSame(otherServlet, hr.getServletByAlias("/foo"));
    }

    @Test
    public void testAddRemoveFilter() throws Exception
    {
        HandlerRegistry hr = new HandlerRegistry();

        Filter filter = Mockito.mock(Filter.class);
        final FilterInfo info = new FilterInfo("oho", "/aha", 1, null, filter, null);

        FilterHandler handler = new FilterHandler(null, filter, info);
        assertEquals("Precondition", 0, hr.getFilters().length);
        hr.addFilter(handler);
        Mockito.verify(filter, Mockito.times(1)).init(Mockito.any(FilterConfig.class));
        assertEquals(1, hr.getFilters().length);
        assertSame(handler, hr.getFilters()[0]);

        final FilterInfo info2 = new FilterInfo("haha", "/hihi", 2, null, filter, null);
        FilterHandler handler2 = new FilterHandler(null, filter, info2);
        try
        {
            hr.addFilter(handler2);
            fail("Should not have allowed the same filter to be added twice");
        }
        catch(ServletException se)
        {
            // good
        }
        assertArrayEquals(new FilterHandler[] {handler}, hr.getFilters());

        Mockito.verify(filter, Mockito.never()).destroy();
        hr.removeFilter(filter, true);
        Mockito.verify(filter, Mockito.times(1)).destroy();
        assertEquals(0, hr.getServlets().length);
    }

    @Test
    public void testAddFilterWhileSameFilterAddedDuringInit() throws Exception
    {
        final HandlerRegistry hr = new HandlerRegistry();

        Filter filter = Mockito.mock(Filter.class);
        final FilterInfo info = new FilterInfo("two", "/two", 99, null, filter, null);
        final FilterHandler otherHandler = new FilterHandler(null, filter, info);

        Mockito.doAnswer(new Answer<Void>()
        {
            boolean registered = false;
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                if (!registered)
                {
                    registered = true;
                    // sneakily register another handler with this filter before this
                    // one has finished calling init()
                    hr.addFilter(otherHandler);
                }
                return null;
            }
        }).when(filter).init(Mockito.any(FilterConfig.class));

        final FilterInfo info2 = new FilterInfo("one", "/one", 1, null, filter, null);
        FilterHandler handler = new FilterHandler(null, filter, info2);

        try
        {
            hr.addFilter(handler);
            fail("Should not have allowed the filter to be added as it was already "
                    + "added before init was finished");
        }
        catch (ServletException se)
        {
            // good
        }
        assertArrayEquals(new FilterHandler[] {otherHandler}, hr.getFilters());
    }

    @Test
    public void testRemoveAll() throws Exception
    {
        HandlerRegistry hr = new HandlerRegistry();

        Servlet servlet = Mockito.mock(Servlet.class);
        final ServletInfo info = new ServletInfo("f", "/f", 0, null, servlet, null);
        ServletHandler servletHandler = new ServletHandler(null, null, info, info.getServlet());
        hr.addServlet(null, servletHandler);
        Servlet servlet2 = Mockito.mock(Servlet.class);
        final ServletInfo info2 = new ServletInfo("ff", "/ff", 0, null, servlet2, null);
        ServletHandler servletHandler2 = new ServletHandler(null, null, info2, info2.getServlet());
        hr.addServlet(null, servletHandler2);
        Filter filter = Mockito.mock(Filter.class);
        final FilterInfo fi = new FilterInfo("f", "/f", 0, null, filter, null);
        FilterHandler filterHandler = new FilterHandler(null, filter, fi);
        hr.addFilter(filterHandler);

        assertEquals(2, hr.getServlets().length);
        assertEquals("Most specific Alias should come first",
                "/ff", hr.getServlets()[0].getAlias());
        assertEquals("/f", hr.getServlets()[1].getAlias());
        assertEquals(1, hr.getFilters().length);
        assertSame(filter, hr.getFilters()[0].getFilter());

        Mockito.verify(servlet, Mockito.never()).destroy();
        Mockito.verify(servlet2, Mockito.never()).destroy();
        Mockito.verify(filter, Mockito.never()).destroy();
        hr.removeAll();
        Mockito.verify(servlet, Mockito.times(1)).destroy();
        Mockito.verify(servlet2, Mockito.times(1)).destroy();
        Mockito.verify(filter, Mockito.times(1)).destroy();

        assertEquals(0, hr.getServlets().length);
        assertEquals(0, hr.getFilters().length);
    }
    */
}

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

package org.apache.felix.http.base.internal.handler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionListener;

import org.apache.felix.http.base.internal.HttpConfig;
import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Test cases for {@link HttpSessionWrapper}.
 */
public class HttpSessionWrapperTest
{

    /**
     * FELIX-5175 - sessions are incorrectly destroyed / destroyed too soon.
     */
    @Test
    public void testSessionTimeout() throws Exception
    {
        Set<String> names;

        String contextName = "default";
        long now = System.currentTimeMillis();

        HttpSession session = createMockSession(contextName, now, 1);

        names = HttpSessionWrapper.getExpiredSessionContextNames(session);
        assertTrue("Session should NOT be destroyed!", names.isEmpty());

        // Pretend we've accessed this session two seconds ago, which should imply it is timed out...
        session = createMockSession(contextName, now - 2000L, 1);

        names = HttpSessionWrapper.getExpiredSessionContextNames(session);
        assertFalse("Session should be destroyed!", names.isEmpty());
        assertTrue(names.contains(contextName));
    }

    private HttpSession createMockSession(String sessionName, long lastAccessed, int maxInactive)
    {
        String attrLastAccessed = String.format("org.apache.felix.http.session.context.lastaccessed.%s", sessionName);
        String attrMaxInactive = String.format("org.apache.felix.http.session.context.maxinactive.%s", sessionName);

        HttpSession session = mock(HttpSession.class);
        when(session.getAttributeNames()).thenReturn(Collections.enumeration(Arrays.asList(attrLastAccessed)));
        when(session.getAttribute(eq(attrLastAccessed))).thenReturn(lastAccessed);
        when(session.getAttribute(eq(attrMaxInactive))).thenReturn(maxInactive);

        return session;
    }

    /**
     * FELIX-5819 : Container session should not be invalidated
     */
    @Test
    public void testContainerSessionInvalidation()
    {
        // create container session
        final Map<String, Object> attributes = new HashMap<>();
        final HttpSession containerSession = mock(HttpSession.class);
        when(containerSession.getAttributeNames()).thenReturn(Collections.enumeration(attributes.keySet()));
        when(containerSession.getAttribute(Mockito.anyString())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return attributes.get(invocation.getArgument(0));
            }
        });
        when(containerSession.getAttribute(Mockito.anyString())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return attributes.get(invocation.getArgument(0));
            }
        });
        Mockito.doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                attributes.put((String)invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }
        }).when(containerSession).setAttribute(Mockito.anyString(), Mockito.any());
        Mockito.doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                attributes.remove(invocation.getArgument(0));
                return null;
            }
        }).when(containerSession).removeAttribute(Mockito.anyString());

        final HttpSessionListener listener = mock(HttpSessionListener.class);

        // create context session
        final ExtServletContext context = mock(ExtServletContext.class);
        when(context.getServletContextName()).thenReturn("default");
        when(context.getHttpSessionListener()).thenReturn(listener);

        final HttpConfig config = new HttpConfig();
        config.setInvalidateContainerSession(false);
        final HttpSession contextSession = new HttpSessionWrapper(containerSession, context, config, false);
        // invalidate context session and verify that invalidate is not called on the container session
        contextSession.invalidate();
        assertTrue(attributes.isEmpty());
        Mockito.verify(containerSession, Mockito.never()).invalidate();

        config.setInvalidateContainerSession(true);
        final HttpSession newSession = new HttpSessionWrapper(containerSession, context, config, false);
        // invalidate context session and verify that invalidate is called on the container session
        newSession.invalidate();
        assertTrue(attributes.isEmpty());
        Mockito.verify(containerSession).invalidate();
    }

    @Test
    public void testContainerSessionInvalidationWithContainerAddedAttribute()
    {
        // create container session
        final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();
        attributes.put("org.eclipse.jetty.security.sessionCreatedSecure",true);
        final HttpSession containerSession = mock(HttpSession.class);
        when(containerSession.getAttributeNames()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Map<String, Object> clonedAttributes = new ConcurrentHashMap<>(attributes);
                return Collections.enumeration(clonedAttributes.keySet());
            }
        });
        when(containerSession.getAttribute(Mockito.anyString())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return attributes.get(invocation.getArgument(0));
            }
        });
        when(containerSession.getAttribute(Mockito.anyString())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return attributes.get(invocation.getArgument(0));
            }
        });
        Mockito.doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                attributes.put((String)invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }
        }).when(containerSession).setAttribute(Mockito.anyString(), Mockito.any());
        Mockito.doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                attributes.remove(invocation.getArgument(0));
                return null;
            }
        }).when(containerSession).removeAttribute(Mockito.anyString());

        final HttpSessionListener listener = mock(HttpSessionListener.class);

        // create context session
        final ExtServletContext context = mock(ExtServletContext.class);
        when(context.getServletContextName()).thenReturn("default");
        when(context.getHttpSessionListener()).thenReturn(listener);

        final HttpConfig config = new HttpConfig();
        config.setInvalidateContainerSession(false);
        Set<String> continerAddedAttributesset = new HashSet<>();
        continerAddedAttributesset.add("org.eclipse.jetty.security.sessionCreatedSecure") ;
        config.setContainerAddedAttribueSet(continerAddedAttributesset);
        final HttpSession contextSession = new HttpSessionWrapper(containerSession, context, config, false);
        // invalidate context session and verify that invalidate is not called on the container session
        contextSession.invalidate();
        Mockito.verify(containerSession, Mockito.never()).invalidate();

        config.setInvalidateContainerSession(true);
        final HttpSession newSession = new HttpSessionWrapper(containerSession, context, config, false);
        // invalidate context session and verify that invalidate is called on the container session
        newSession.invalidate();
        Mockito.verify(containerSession).invalidate();
    }

}

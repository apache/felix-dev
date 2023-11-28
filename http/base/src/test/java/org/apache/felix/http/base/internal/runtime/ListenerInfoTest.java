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
package org.apache.felix.http.base.internal.runtime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EventListener;

import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.ServletContextListener;

public class ListenerInfoTest
{
    @Test
    public void testEnabled() throws InvalidSyntaxException {
        Bundle b = mock(Bundle.class);
        BundleContext bc = mock(BundleContext.class);

        @SuppressWarnings("unchecked")
        ServiceReference<EventListener> ref = mock(ServiceReference.class);
        when(ref.getProperty(Constants.SERVICE_ID)).thenReturn(1L);
        when(ref.getProperty(Constants.OBJECTCLASS))
                .thenReturn(new String[] { ServletContextListener.class.getName() });
        when(ref.getBundle()).thenReturn(b);
        when(b.getBundleContext()).thenReturn(bc);
        when(bc.createFilter(ArgumentMatchers.any(String.class))).thenReturn(mock(Filter.class));

        // string true
        when(ref.getProperty(HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER)).thenReturn("true");
        assertTrue(new ListenerInfo(ref).isValid());

        // string false
        when(ref.getProperty(HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER)).thenReturn("false");
        assertFalse(new ListenerInfo(ref).isValid());

        // string some text
        when(ref.getProperty(HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER)).thenReturn("text");
        assertFalse(new ListenerInfo(ref).isValid());

        // string true
        when(ref.getProperty(HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER)).thenReturn(Boolean.TRUE);
        assertTrue(new ListenerInfo(ref).isValid());

        // string false
        when(ref.getProperty(HttpWhiteboardConstants.HTTP_WHITEBOARD_LISTENER)).thenReturn(Boolean.FALSE);
        assertFalse(new ListenerInfo(ref).isValid());
    }

    @Test
    public void testIsSame() {
        final ServiceReference<EventListener> refOld = mock(ServiceReference.class);
        when(refOld.getProperty(Constants.SERVICE_ID)).thenReturn(1L);
        when(refOld.getProperty(Constants.OBJECTCLASS))
                .thenReturn(new String[] { ServletContextListener.class.getName() });

        final ServiceReference<EventListener> refNew = mock(ServiceReference.class);
        when(refNew.getProperty(Constants.SERVICE_ID)).thenReturn(1L);
        when(refNew.getProperty(Constants.OBJECTCLASS))
                .thenReturn(new String[] { ServletContextListener.class.getName() });
        when(refNew.getProperty("foo")).thenReturn("bar");

        assertTrue(new ListenerInfo(refNew).isSame(new ListenerInfo(refOld)));
        assertTrue(new ListenerInfo(refOld).isSame(new ListenerInfo(refNew)));

        when(refNew.getProperty(Constants.SERVICE_RANKING)).thenReturn(1);

        assertFalse(new ListenerInfo(refNew).isSame(new ListenerInfo(refOld)));
        assertFalse(new ListenerInfo(refOld).isSame(new ListenerInfo(refNew)));
    }
}

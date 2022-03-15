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
package org.apache.felix.http.base.internal.jakartawrappers;

import java.util.EventListener;
import java.util.Set;

import org.apache.felix.http.base.internal.javaxwrappers.HttpSessionWrapper;
import org.apache.felix.http.base.internal.javaxwrappers.ServletContextWrapper;
import org.apache.felix.http.base.internal.javaxwrappers.ServletRequestWrapper;
import org.jetbrains.annotations.NotNull;

import jakarta.servlet.ServletContextAttributeEvent;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestAttributeEvent;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

/**
 * Wrapper for all listeners
 */
public class EventListenerWrapper implements HttpSessionAttributeListener,
    HttpSessionIdListener,
    HttpSessionListener,
    ServletContextListener,
    ServletContextAttributeListener,
    ServletRequestListener,
    ServletRequestAttributeListener {

    private final javax.servlet.http.HttpSessionAttributeListener httpSessionAttributeListener;
    private final javax.servlet.http.HttpSessionIdListener httpSessionIdListener;
    private final javax.servlet.http.HttpSessionListener httpSessionListener;
    private final javax.servlet.ServletContextListener servletContextListener;
    private final javax.servlet.ServletContextAttributeListener servletContextAttributeListener;
    private final javax.servlet.ServletRequestListener servletRequestListener;
    private final javax.servlet.ServletRequestAttributeListener servletRequestAttributeListener;
    private final EventListener listener;

    /**
     * Create new wrapper
     * @param listener Wrapped listener
     * @param listenerTypes Service interfaces
     */
    public EventListenerWrapper(@NotNull final EventListener listener,
            @NotNull final Set<String> listenerTypes) {
        this.listener = listener;
        if ( listenerTypes.contains(HttpSessionAttributeListener.class.getName()) ) {
            this.httpSessionAttributeListener = (javax.servlet.http.HttpSessionAttributeListener) listener;
        } else {
            this.httpSessionAttributeListener = null;
        }
        if ( listenerTypes.contains(HttpSessionIdListener.class.getName()) ) {
            this.httpSessionIdListener = (javax.servlet.http.HttpSessionIdListener) listener;
        } else {
            this.httpSessionIdListener = null;
        }
        if ( listenerTypes.contains(HttpSessionListener.class.getName()) ) {
            this.httpSessionListener = (javax.servlet.http.HttpSessionListener) listener;
        } else {
            this.httpSessionListener = null;
        }
        if ( listenerTypes.contains(ServletContextListener.class.getName()) ) {
            this.servletContextListener = (javax.servlet.ServletContextListener) listener;
        } else {
            this.servletContextListener = null;
        }
        if ( listenerTypes.contains(ServletContextAttributeListener.class.getName()) ) {
            this.servletContextAttributeListener = (javax.servlet.ServletContextAttributeListener) listener;
        } else {
            this.servletContextAttributeListener = null;
        }
        if ( listenerTypes.contains(ServletRequestListener.class.getName()) ) {
            this.servletRequestListener = (javax.servlet.ServletRequestListener) listener;
        } else {
            this.servletRequestListener = null;
        }
        if ( listenerTypes.contains(ServletRequestAttributeListener.class.getName()) ) {
            this.servletRequestAttributeListener = (javax.servlet.ServletRequestAttributeListener) listener;
        } else {
            this.servletRequestAttributeListener = null;
        }
    }

    @Override
    public void attributeAdded(final ServletRequestAttributeEvent srae) {
        if ( this.servletRequestAttributeListener != null ) {
            this.servletRequestAttributeListener.attributeAdded(new javax.servlet.ServletRequestAttributeEvent(new ServletContextWrapper(srae.getServletContext()),
                    ServletRequestWrapper.getWrapper(srae.getServletRequest()), srae.getName(), srae.getValue()));
        }
    }
    @Override
    public void attributeRemoved(final ServletRequestAttributeEvent srae) {
        if ( this.servletRequestAttributeListener != null ) {
            this.servletRequestAttributeListener.attributeRemoved(new javax.servlet.ServletRequestAttributeEvent(new ServletContextWrapper(srae.getServletContext()),
                    ServletRequestWrapper.getWrapper(srae.getServletRequest()), srae.getName(), srae.getValue()));
        }
    }

    @Override
    public void attributeReplaced(final ServletRequestAttributeEvent srae) {
        if ( this.servletRequestAttributeListener != null ) {
            this.servletRequestAttributeListener.attributeReplaced(new javax.servlet.ServletRequestAttributeEvent(new ServletContextWrapper(srae.getServletContext()),
                    ServletRequestWrapper.getWrapper(srae.getServletRequest()), srae.getName(), srae.getValue()));
        }
    }

    @Override
    public void requestDestroyed(final ServletRequestEvent sre) {
        if ( this.servletRequestListener != null ) {
            this.servletRequestListener.requestDestroyed(new javax.servlet.ServletRequestEvent(new ServletContextWrapper(sre.getServletContext()),
                    ServletRequestWrapper.getWrapper(sre.getServletRequest())));
        }
    }

    @Override
    public void requestInitialized(final ServletRequestEvent sre) {
        if ( this.servletRequestListener != null ) {
            this.servletRequestListener.requestInitialized(new javax.servlet.ServletRequestEvent(new ServletContextWrapper(sre.getServletContext()),
                    ServletRequestWrapper.getWrapper(sre.getServletRequest())));
        }
    }

    @Override
    public void attributeAdded(final ServletContextAttributeEvent event) {
        if ( this.servletContextAttributeListener != null ) {
            this.servletContextAttributeListener.attributeAdded(new javax.servlet.ServletContextAttributeEvent(new ServletContextWrapper(event.getServletContext()), event.getName(), event.getValue()));
        }
    }

    @Override
    public void attributeRemoved(final ServletContextAttributeEvent event) {
        if ( this.servletContextAttributeListener != null ) {
            this.servletContextAttributeListener.attributeRemoved(new javax.servlet.ServletContextAttributeEvent(new ServletContextWrapper(event.getServletContext()), event.getName(), event.getValue()));
        }
    }

    @Override
    public void attributeReplaced(final ServletContextAttributeEvent event) {
        if ( this.servletContextAttributeListener != null ) {
            this.servletContextAttributeListener.attributeReplaced(new javax.servlet.ServletContextAttributeEvent(new ServletContextWrapper(event.getServletContext()), event.getName(), event.getValue()));
        }
    }

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        if ( this.servletContextListener != null ) {
            this.servletContextListener.contextInitialized(new javax.servlet.ServletContextEvent(new ServletContextWrapper(sce.getServletContext())));
        }
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
        if ( this.servletContextListener != null ) {
            this.servletContextListener.contextDestroyed(new javax.servlet.ServletContextEvent(new ServletContextWrapper(sce.getServletContext())));
        }
    }

    @Override
    public void sessionCreated(final HttpSessionEvent se) {
        if ( this.httpSessionListener != null ) {
            this.httpSessionListener.sessionCreated(new javax.servlet.http.HttpSessionEvent(new HttpSessionWrapper(se.getSession())));
        }
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent se) {
        if ( this.httpSessionListener != null ) {
            this.httpSessionListener.sessionDestroyed(new javax.servlet.http.HttpSessionEvent(new HttpSessionWrapper(se.getSession())));
        }
    }

    @Override
    public void sessionIdChanged(final HttpSessionEvent event, final String oldSessionId) {
        if ( this.httpSessionIdListener != null ) {
            this.httpSessionIdListener.sessionIdChanged(new javax.servlet.http.HttpSessionEvent(new HttpSessionWrapper(event.getSession())), oldSessionId);
        }
    }

    @Override
    public void attributeAdded(final HttpSessionBindingEvent event) {
        if ( this.httpSessionAttributeListener != null ) {
            this.httpSessionAttributeListener.attributeAdded(new javax.servlet.http.HttpSessionBindingEvent(new HttpSessionWrapper(event.getSession()), event.getName(), event.getValue()));
        }
    }

    @Override
    public void attributeRemoved(final HttpSessionBindingEvent event) {
        if ( this.httpSessionAttributeListener != null ) {
            this.httpSessionAttributeListener.attributeRemoved(new javax.servlet.http.HttpSessionBindingEvent(new HttpSessionWrapper(event.getSession()), event.getName(), event.getValue()));
        }
    }

    @Override
    public void attributeReplaced(final HttpSessionBindingEvent event) {
        if ( this.httpSessionAttributeListener != null ) {
            this.httpSessionAttributeListener.attributeReplaced(new javax.servlet.http.HttpSessionBindingEvent(new HttpSessionWrapper(event.getSession()), event.getName(), event.getValue()));
        }
    }

    /**
     * Get the listener
     * @return The listener
     */
    public @NotNull EventListener getListener() {
        return this.listener;
    }
}

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
package org.apache.felix.http.base.internal.whiteboard;

import static org.osgi.service.servlet.runtime.dto.DTOConstants.FAILURE_REASON_NO_SERVLET_CONTEXT_MATCHING;
import static org.osgi.service.servlet.runtime.dto.DTOConstants.FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE;
import static org.osgi.service.servlet.runtime.dto.DTOConstants.FAILURE_REASON_UNKNOWN;
import static org.osgi.service.servlet.runtime.dto.DTOConstants.FAILURE_REASON_VALIDATION_FAILED;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.handler.FilterHandler;
import org.apache.felix.http.base.internal.handler.HttpSessionWrapper;
import org.apache.felix.http.base.internal.handler.ListenerHandler;
import org.apache.felix.http.base.internal.handler.PreprocessorHandler;
import org.apache.felix.http.base.internal.handler.ServletHandler;
import org.apache.felix.http.base.internal.handler.WhiteboardServletHandler;
import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.apache.felix.http.base.internal.registry.EventListenerRegistry;
import org.apache.felix.http.base.internal.registry.HandlerRegistry;
import org.apache.felix.http.base.internal.runtime.AbstractInfo;
import org.apache.felix.http.base.internal.runtime.DefaultServletContextHelperInfo;
import org.apache.felix.http.base.internal.runtime.FilterInfo;
import org.apache.felix.http.base.internal.runtime.ListenerInfo;
import org.apache.felix.http.base.internal.runtime.PreprocessorInfo;
import org.apache.felix.http.base.internal.runtime.ResourceInfo;
import org.apache.felix.http.base.internal.runtime.ServletContextHelperInfo;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.apache.felix.http.base.internal.runtime.WhiteboardServiceInfo;
import org.apache.felix.http.base.internal.runtime.dto.FailedDTOHolder;
import org.apache.felix.http.base.internal.runtime.dto.PreprocessorDTOBuilder;
import org.apache.felix.http.base.internal.runtime.dto.RegistryRuntime;
import org.apache.felix.http.base.internal.runtime.dto.ServletContextDTOBuilder;
import org.apache.felix.http.base.internal.service.HttpServiceRuntimeImpl;
import org.apache.felix.http.base.internal.whiteboard.tracker.FilterTracker;
import org.apache.felix.http.base.internal.whiteboard.tracker.ListenersTracker;
import org.apache.felix.http.base.internal.whiteboard.tracker.PreprocessorTracker;
import org.apache.felix.http.base.internal.whiteboard.tracker.ResourceTracker;
import org.apache.felix.http.base.internal.whiteboard.tracker.ServletContextHelperTracker;
import org.apache.felix.http.base.internal.whiteboard.tracker.ServletTracker;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.servlet.runtime.dto.DTOConstants;
import org.osgi.service.servlet.runtime.dto.PreprocessorDTO;
import org.osgi.service.servlet.runtime.dto.ServletContextDTO;
import org.osgi.service.servlet.whiteboard.Preprocessor;
import org.osgi.util.tracker.ServiceTracker;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;

public final class WhiteboardManager
{
    /** The bundle context of the http bundle. */
    private final BundleContext httpBundleContext;

    private final HttpServiceRuntimeImpl serviceRuntime;

    private final List<ServiceTracker<?, ?>> trackers = new ArrayList<>();

    /** A map containing all servlet context registrations. Mapped by context name */
    private final Map<String, List<WhiteboardContextHandler>> contextMap = new HashMap<>();

    /** A map with all servlet/filter registrations, mapped by abstract info. */
    private final Map<WhiteboardServiceInfo<?>, List<WhiteboardContextHandler>> servicesMap = new HashMap<>();

    private volatile List<PreprocessorHandler> preprocessorHandlers = Collections.emptyList();

    private final HandlerRegistry registry;

    private final FailureStateHandler failureStateHandler = new FailureStateHandler();

    private volatile ServletContext webContext;

    /**
     * Create a new whiteboard http manager
     *
     * @param bundleContext The bundle context of the http bundle
     * @param registry The handler registry
     */
    public WhiteboardManager(final BundleContext bundleContext,
            final HandlerRegistry registry)
    {
        this.httpBundleContext = bundleContext;
        this.registry = registry;
        this.serviceRuntime = new HttpServiceRuntimeImpl(registry, this, bundleContext);
    }

    /**
     * Start the whiteboard manager
     * @param containerContext The servlet context
     */
    public void start(final ServletContext containerContext, @NotNull final Dictionary<String, Object> httpServiceProps)
    {
        // runtime service gets the same props for now
        this.serviceRuntime.setAllAttributes(httpServiceProps);

        this.serviceRuntime.register(this.httpBundleContext);

        this.webContext = containerContext;

        // Add default context
        this.addContextHelper(new DefaultServletContextHelperInfo());

        // Start tracker
        addTracker(new FilterTracker(this.httpBundleContext, this));
        addTracker(new ListenersTracker(this.httpBundleContext, this));
        addTracker(new PreprocessorTracker(this.httpBundleContext, this));
        addTracker(new ServletTracker(this.httpBundleContext, this));
        addTracker(new ResourceTracker(this.httpBundleContext, this));
        addTracker(new ServletContextHelperTracker(this.httpBundleContext, this));
    }

    /**
     * Add a tracker and start it
     * @param tracker The tracker instance
     */
    private void addTracker(ServiceTracker<?, ?> tracker)
    {
        this.trackers.add(tracker);
        tracker.open();
    }

    /**
     * Stop the instance
     */
    public void stop()
    {
        for(final ServiceTracker<?, ?> t : this.trackers)
        {
            t.close();
        }
        this.trackers.clear();

        this.serviceRuntime.unregister();

        this.preprocessorHandlers = Collections.emptyList();
        this.contextMap.clear();
        this.servicesMap.clear();
        this.failureStateHandler.clear();
        this.registry.reset();

        this.webContext = null;
    }

    public void sessionDestroyed(@NotNull final HttpSession session, final Set<String> contextNames)
    {
        for(final String contextName : contextNames)
        {
            final WhiteboardContextHandler handler = this.getContextHandler(contextName);
            if ( handler != null )
            {
                final ExtServletContext context = handler.getServletContext(this.httpBundleContext.getBundle());
                new HttpSessionWrapper(session, context, this.registry.getConfig(), true).invalidate();
                handler.ungetServletContext(this.httpBundleContext.getBundle());
            }
        }
    }

    /**
     * Handle session id changes
     * @param session The session where the id changed
     * @param oldSessionId The old session id
     * @param contextIds The context ids using that session
     */
    public void sessionIdChanged(@NotNull final HttpSessionEvent event, final String oldSessionId, final Set<String> contextNames)
    {
        for(final String contextName : contextNames)
        {
            final WhiteboardContextHandler handler = this.getContextHandler(contextName);
            if ( handler != null )
            {
                handler.getRegistry().getEventListenerRegistry().sessionIdChanged(event, oldSessionId);
            }
        }
    }

    /**
     * Activate a servlet context helper.
     *
     * @param handler The context handler
     * @return {@code true} if activation succeeded.
     */
    private boolean activate(final WhiteboardContextHandler handler)
    {
        if ( !handler.activate(this.registry) )
        {
            return false;
        }

        final List<WhiteboardServiceInfo<?>> services = new ArrayList<>();
        for(final Map.Entry<WhiteboardServiceInfo<?>, List<WhiteboardContextHandler>> entry : this.servicesMap.entrySet())
        {
            final WhiteboardServiceInfo<?> info = entry.getKey();

            if ( info.getContextSelectionFilter().match(handler.getContextInfo().getServiceReference()) )
            {
                entry.getValue().add(handler);
                if ( entry.getValue().size() == 1 )
                {
                    this.failureStateHandler.remove(info);
                }
                if ( info instanceof ListenerInfo && ((ListenerInfo)info).isListenerType(ServletContextListener.class.getName()) )
                {
                    // servlet context listeners will be registered directly
                    this.registerWhiteboardService(handler, info);
                }
                else
                {
                    // registration of other services will be delayed
                    services.add(info);
                }
            }
        }
        // notify context listeners first
        handler.getRegistry().getEventListenerRegistry().contextInitialized();

        // register services
        for(final WhiteboardServiceInfo<?> info : services)
        {
            this.registerWhiteboardService(handler, info);
        }

        return true;
    }

    /**
     * Deactivate a servlet context.
     *
     * @param handler A context handler
     */
    private void deactivate(final WhiteboardContextHandler handler)
    {
        // services except context listeners first
        final List<WhiteboardServiceInfo<?>> listeners = new ArrayList<>();
        final Iterator<Map.Entry<WhiteboardServiceInfo<?>, List<WhiteboardContextHandler>>> i = this.servicesMap.entrySet().iterator();
        while ( i.hasNext() )
        {
            final Map.Entry<WhiteboardServiceInfo<?>, List<WhiteboardContextHandler>> entry = i.next();
            if ( entry.getValue().remove(handler) )
            {
                if ( !this.failureStateHandler.remove(entry.getKey(), handler.getContextInfo().getServiceId()) )
                {
                    if ( entry.getKey() instanceof ListenerInfo && ((ListenerInfo)entry.getKey()).isListenerType(ServletContextListener.class.getName()) )
                    {
                        listeners.add(entry.getKey());
                    }
                    else
                    {
                        this.unregisterWhiteboardService(handler, entry.getKey());
                    }
                }
                if ( entry.getValue().isEmpty() )
                {
                    this.failureStateHandler.addFailure(entry.getKey(), FAILURE_REASON_NO_SERVLET_CONTEXT_MATCHING);
                }
            }
        }
        // context listeners last
        handler.getRegistry().getEventListenerRegistry().contextDestroyed();
        for(final WhiteboardServiceInfo<?> info : listeners)
        {
            this.unregisterWhiteboardService(handler, info);
        }

        handler.deactivate(this.registry);
    }

    /**
     * Add a servlet context helper.
     *
     * @param info The servlet context helper info
     * @return {@code true} if the service matches this http whiteboard service
     */
    public boolean addContextHelper(final ServletContextHelperInfo info)
    {
        // no failure DTO and no logging if not matching
        if ( isMatchingService(info) )
        {
            if ( info.isValid() )
            {
                synchronized ( this.contextMap )
                {
                    final WhiteboardContextHandler handler = new WhiteboardContextHandler(info,
                            this.webContext,
                            this.httpBundleContext.getBundle());

                    // check for activate/deactivate
                    List<WhiteboardContextHandler> handlerList = this.contextMap.get(info.getName());
                    if ( handlerList == null )
                    {
                        handlerList = new ArrayList<>();
                    }
                    final boolean activate = handlerList.isEmpty() || handlerList.get(0).compareTo(handler) > 0;
                    if ( activate )
                    {
                        // try to activate
                        if ( this.activate(handler) )
                        {
                            handlerList.add(handler);
                            Collections.sort(handlerList);
                            this.contextMap.put(info.getName(), handlerList);

                            // check for deactivate
                            if ( handlerList.size() > 1 )
                            {
                                final WhiteboardContextHandler oldHead = handlerList.get(1);
                                this.deactivate(oldHead);

                                this.failureStateHandler.addFailure(oldHead.getContextInfo(), FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE);
                            }
                        }
                        else
                        {
                            this.failureStateHandler.addFailure(info, DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE);
                        }
                    }
                    else
                    {
                        handlerList.add(handler);
                        Collections.sort(handlerList);
                        this.contextMap.put(info.getName(), handlerList);

                        this.failureStateHandler.addFailure(info, FAILURE_REASON_SHADOWED_BY_OTHER_SERVICE);
                    }
                }
            }
            else
            {
                this.failureStateHandler.addFailure(info, FAILURE_REASON_VALIDATION_FAILED);
            }
            updateRuntimeChangeCount();
            return true;
        }
        return false;
    }

    /**
     * Remove a servlet context helper
     *
     * @param The servlet context helper info
     */
    public void removeContextHelper(final ServletContextHelperInfo info)
    {
        if ( info.isValid() )
        {
            synchronized ( this.contextMap )
            {
                final List<WhiteboardContextHandler> handlerList = this.contextMap.get(info.getName());
                if ( handlerList != null )
                {
                    final Iterator<WhiteboardContextHandler> i = handlerList.iterator();
                    boolean first = true;
                    boolean activateNext = false;
                    while ( i.hasNext() )
                    {
                        final WhiteboardContextHandler handler = i.next();
                        if ( handler.getContextInfo().equals(info) )
                        {
                            i.remove();
                            // check for deactivate
                            if ( first )
                            {
                                this.deactivate(handler);
                                activateNext = true;
                            }
                            break;
                        }
                        first = false;
                    }
                    if ( handlerList.isEmpty() )
                    {
                        this.contextMap.remove(info.getName());
                    }
                    else if ( activateNext )
                    {
                        // Try to activate next
                        boolean done = false;
                        while ( !handlerList.isEmpty() && !done)
                        {
                            final WhiteboardContextHandler newHead = handlerList.get(0);
                            this.failureStateHandler.removeAll(newHead.getContextInfo());

                            if ( this.activate(newHead) )
                            {
                                done = true;
                            }
                            else
                            {
                                handlerList.remove(0);

                                this.failureStateHandler.addFailure(newHead.getContextInfo(), DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE);
                            }
                        }
                    }
                }
            }
        }
        this.failureStateHandler.removeAll(info);
        updateRuntimeChangeCount();
    }

    /**
     * Find the list of matching contexts for the whiteboard service
     */
    private List<WhiteboardContextHandler> getMatchingContexts(final WhiteboardServiceInfo<?> info)
    {
        final List<WhiteboardContextHandler> result = new ArrayList<>();
        for(final List<WhiteboardContextHandler> handlerList : this.contextMap.values())
        {
            final WhiteboardContextHandler h = handlerList.get(0);
            // check whether the servlet context helper is visible to the whiteboard bundle
            // see chapter 140.2
            boolean visible = h.getContextInfo().getServiceId() < 0; // internal ones are always visible
            if ( !visible )
            {
                final String filterString = "(" + Constants.SERVICE_ID + "=" + String.valueOf(h.getContextInfo().getServiceId()) + ")";
                try
                {
                    final ServiceReference<?>[] col = info.getServiceReference().getBundle().getBundleContext().getServiceReferences(h.getContextInfo().getServiceType(), filterString);
                    if ( col !=null && col.length > 0 )
                    {
                        visible = true;
                    }
                }
                catch ( final InvalidSyntaxException ise )
                {
                    // we ignore this and treat it as an invisible service
                }
            }
            if ( visible && h.getContextInfo().match(info) )
            {
                result.add(h);
            }
        }
        return result;
    }

    /**
     * Add new whiteboard service to the registry
     *
     * @param info Whiteboard service info
     * @return {@code true} if it matches this http service runtime
     */
    public boolean addWhiteboardService(@NotNull final WhiteboardServiceInfo<?> info)
    {
        // no logging and no DTO if other target service
        if ( isMatchingService(info) )
        {
            if ( info.isValid() )
            {
                if ( info instanceof PreprocessorInfo )
                {
                    final PreprocessorHandler handler = new PreprocessorHandler(this.httpBundleContext,
                            this.webContext, ((PreprocessorInfo)info));
                    final int result = handler.init();
                    if ( result == -1 )
                    {
                        synchronized ( this.preprocessorHandlers )
                        {
                            final List<PreprocessorHandler> newList = new ArrayList<>(this.preprocessorHandlers);
                            newList.add(handler);
                            Collections.sort(newList);
                            this.preprocessorHandlers = newList;
                        }
                    }
                    else
                    {
                        this.failureStateHandler.addFailure(info, FAILURE_REASON_VALIDATION_FAILED);
                    }
                    updateRuntimeChangeCount();
                    return true;
                }
                synchronized ( this.contextMap )
                {
                    final List<WhiteboardContextHandler> handlerList = this.getMatchingContexts(info);
                    this.servicesMap.put(info, handlerList);
                    if (handlerList.isEmpty())
                    {
                        this.failureStateHandler.addFailure(info, FAILURE_REASON_NO_SERVLET_CONTEXT_MATCHING);
                    }
                    else
                    {
                        for(final WhiteboardContextHandler h : handlerList)
                        {
                            this.registerWhiteboardService(h, info);
                            if ( info instanceof ListenerInfo && ((ListenerInfo)info).isListenerType(ServletContextListener.class.getName()) )
                            {
                                final ListenerHandler handler = h.getRegistry().getEventListenerRegistry().getServletContextListener((ListenerInfo)info);
                                if ( handler != null )
                                {
                                    final ServletContextListener listener = (ServletContextListener)handler.getListener();
                                    if ( listener != null )
                                    {
                                        EventListenerRegistry.contextInitialized(handler.getListenerInfo(), listener, new ServletContextEvent(handler.getContext()));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else
            {
                this.failureStateHandler.addFailure(info, FAILURE_REASON_VALIDATION_FAILED);
            }
            updateRuntimeChangeCount();
            return true;
        }
        return false;
    }

    /**
     * Remove whiteboard service from the registry.
     *
     * @param info The service id of the whiteboard service
     */
    public void removeWhiteboardService(final WhiteboardServiceInfo<?> info )
    {
        synchronized ( this.contextMap )
        {
            if ( !failureStateHandler.remove(info) )
            {
                if ( info instanceof PreprocessorInfo )
                {
                    synchronized ( this.preprocessorHandlers )
                    {
                        final List<PreprocessorHandler> newList = new ArrayList<>(this.preprocessorHandlers);
                        final Iterator<PreprocessorHandler> iter = newList.iterator();
                        while ( iter.hasNext() )
                        {
                            final PreprocessorHandler handler = iter.next();
                            if ( handler.getPreprocessorInfo().compareTo((PreprocessorInfo)info) == 0 )
                            {
                                iter.remove();
                                this.preprocessorHandlers = newList;
                                updateRuntimeChangeCount();
                                return;
                            }
                        }
                        // not found, nothing to do
                    }
                    return;
                }
                final List<WhiteboardContextHandler> handlerList = this.servicesMap.remove(info);
                if ( handlerList != null )
                {
                    for(final WhiteboardContextHandler h : handlerList)
                    {
                        if ( !failureStateHandler.remove(info, h.getContextInfo().getServiceId()) )
                        {
                            if ( info instanceof ListenerInfo && ((ListenerInfo)info).isListenerType(ServletContextListener.class.getName()) )
                            {
                                final ListenerHandler handler = h.getRegistry().getEventListenerRegistry().getServletContextListener((ListenerInfo)info);
                                if ( handler != null )
                                {
                                    final ServletContextListener listener = (ServletContextListener) handler.getListener();
                                    if ( listener != null )
                                    {
                                        EventListenerRegistry.contextDestroyed(handler.getListenerInfo(), listener, new ServletContextEvent(handler.getContext()));
                                    }
                                }
                            }
                            this.unregisterWhiteboardService(h, info);
                        }
                    }
                }
            }
            this.failureStateHandler.removeAll(info);
        }
        updateRuntimeChangeCount();
    }

    /**
     * Register whiteboard service in the http service
     * @param handler Context handler
     * @param info Whiteboard service info
     */
    private void registerWhiteboardService(final WhiteboardContextHandler handler, final WhiteboardServiceInfo<?> info)
    {
        try
        {
            int failureCode = -1;
            if ( info instanceof ServletInfo )
            {
                final ExtServletContext servletContext = handler.getServletContext(info.getServiceReference().getBundle());
                if ( servletContext == null )
                {
                    failureCode = DTOConstants.FAILURE_REASON_SERVLET_CONTEXT_FAILURE;
                }
                else
                {
                    // @formatter:off
                    final ServletHandler servletHandler = new WhiteboardServletHandler(
                        handler.getContextInfo().getServiceId(),
                        servletContext,
                        (ServletInfo)info,
                        handler.getBundleContext(),
                        info.getServiceReference().getBundle(),
                        this.httpBundleContext.getBundle());
                    // @formatter:on
                    handler.getRegistry().registerServlet(servletHandler);
                }
            }
            else if ( info instanceof FilterInfo )
            {
                final ExtServletContext servletContext = handler.getServletContext(info.getServiceReference().getBundle());
                if ( servletContext == null )
                {
                    failureCode = DTOConstants.FAILURE_REASON_SERVLET_CONTEXT_FAILURE;
                }
                else
                {
                    // @formatter:off
                    final FilterHandler filterHandler = new FilterHandler(
                            handler.getContextInfo().getServiceId(),
                            servletContext,
                            (FilterInfo)info,
                            handler.getBundleContext());
                    // @formatter:on
                    handler.getRegistry().registerFilter(filterHandler);
                }
            }
            else if ( info instanceof ResourceInfo )
            {
                final ServletInfo servletInfo = ((ResourceInfo)info).getServletInfo();
                final ExtServletContext servletContext = handler.getServletContext(info.getServiceReference().getBundle());
                if ( servletContext == null )
                {
                    failureCode = DTOConstants.FAILURE_REASON_SERVLET_CONTEXT_FAILURE;
                }
                else
                {
                    // @formatter:off
                    final ServletHandler servletHandler = new WhiteboardServletHandler(
                            handler.getContextInfo().getServiceId(),
                            servletContext,
                            servletInfo,
                            handler.getBundleContext(), 
                            new ResourceServlet(servletInfo.getPrefix()));
                    // @formatter:on
                    handler.getRegistry().registerServlet(servletHandler);
                }
            }

            else if ( info instanceof ListenerInfo )
            {
                final ExtServletContext servletContext = handler.getServletContext(info.getServiceReference().getBundle());
                if ( servletContext == null )
                {
                    failureCode = DTOConstants.FAILURE_REASON_SERVLET_CONTEXT_FAILURE;
                }
                else
                {
                    // @formatter:off
                    final ListenerHandler listenerHandler = new ListenerHandler(
                            handler.getContextInfo().getServiceId(),
                            servletContext,
                            (ListenerInfo)info,
                            handler.getBundleContext());
                    // @formatter:on
                    handler.getRegistry().registerListeners(listenerHandler);
                }
            }
            else
            {
                // This should never happen, but we log anyway
                SystemLogger.LOGGER.error("Unknown whiteboard service {}", info.getServiceReference());
            }
            if ( failureCode != -1 )
            {
                this.failureStateHandler.addFailure(info, handler.getContextInfo().getServiceId(), failureCode);
            }
        }
        catch (final Exception e)
        {
            this.failureStateHandler.addFailure(info, handler.getContextInfo().getServiceId(), FAILURE_REASON_UNKNOWN, e);
        }
    }

    /**
     * Unregister whiteboard service from the http service
     * @param handler Context handler
     * @param info Whiteboard service info
     */
    private void unregisterWhiteboardService(final WhiteboardContextHandler handler, final WhiteboardServiceInfo<?> info)
    {
        try
        {
            if ( info instanceof ServletInfo )
            {
                handler.getRegistry().unregisterServlet((ServletInfo)info, true);
                handler.ungetServletContext(info.getServiceReference().getBundle());
            }
            else if ( info instanceof FilterInfo )
            {
                handler.getRegistry().unregisterFilter((FilterInfo)info, true);
                handler.ungetServletContext(info.getServiceReference().getBundle());
            }
            else if ( info instanceof ResourceInfo )
            {
                handler.getRegistry().unregisterServlet(((ResourceInfo)info).getServletInfo(), true);
                handler.ungetServletContext(info.getServiceReference().getBundle());
            }

            else if ( info instanceof ListenerInfo )
            {
                handler.getRegistry().unregisterListeners((ListenerInfo) info);
                handler.ungetServletContext(info.getServiceReference().getBundle());
            }
        }
        catch (final Exception e)
        {
            SystemLogger.LOGGER.error("Exception while unregistering whiteboard service {}", info.getServiceReference(), e);
        }

    }

    /**
     * Check whether the service is specifying a target http service runtime
     * and if so if that is matching this runtime
     */
    private boolean isMatchingService(final AbstractInfo<?> info)
    {
        final String target = info.getTarget();
        if ( target != null )
        {
            try
            {
                final Filter f = this.httpBundleContext.createFilter(target);
                return f.match(this.serviceRuntime.getServiceReference());
            }
            catch ( final InvalidSyntaxException ise)
            {
                // log and ignore service
                SystemLogger.LOGGER.error("Invalid target filter expression for {} : {}", info.getServiceReference(), target, ise);
                return false;
            }
        }
        return true;
    }

    private WhiteboardContextHandler getContextHandler(final String name)
    {
        synchronized ( this.contextMap )
        {
            for(final List<WhiteboardContextHandler> handlerList : this.contextMap.values())
            {
                final WhiteboardContextHandler h = handlerList.get(0);
                if ( h.getContextInfo().getName().equals(name) )
                {
                    return h;
                }
            }
        }
        return null;
    }

    public RegistryRuntime getRuntimeInfo()
    {
        final FailedDTOHolder failedDTOHolder = new FailedDTOHolder();

        final Collection<ServletContextDTO> contextDTOs = new ArrayList<>();

        // get sort list of context handlers
        final List<WhiteboardContextHandler> contextHandlerList = new ArrayList<>();
        synchronized ( this.contextMap )
        {
            for (final List<WhiteboardContextHandler> list : this.contextMap.values())
            {
                if ( !list.isEmpty() )
                {
                    contextHandlerList.add(list.get(0));
                }
            }
            this.failureStateHandler.getRuntimeInfo(failedDTOHolder);
        }
        Collections.sort(contextHandlerList);

        for (final WhiteboardContextHandler handler : contextHandlerList)
        {
            final ServletContextDTO scDTO = ServletContextDTOBuilder.build(handler.getContextInfo(), handler.getSharedContext(), -1);

            if ( registry.getRuntimeInfo(scDTO, failedDTOHolder) )
            {
                contextDTOs.add(scDTO);
            }
        }

        final List<PreprocessorDTO> preprocessorDTOs = new ArrayList<>();
        final List<PreprocessorHandler> localHandlers = this.preprocessorHandlers;
        for(final PreprocessorHandler handler : localHandlers)
        {
            preprocessorDTOs.add(PreprocessorDTOBuilder.build(handler.getPreprocessorInfo(), -1));
        }

        return new RegistryRuntime(failedDTOHolder, contextDTOs, preprocessorDTOs);
    }

    /**
     * Invoke all preprocessors
     *
     * @param req The request
     * @param res The response
     * @return {@code true} to continue with dispatching, {@code false} to terminate the request.
     * @throws IOException
     * @throws ServletException
     */
    public void invokePreprocessors(final HttpServletRequest req,
    		final HttpServletResponse res,
    		final Preprocessor dispatcher)
    throws ServletException, IOException
    {
        final List<PreprocessorHandler> localHandlers = this.preprocessorHandlers;
        if ( localHandlers.isEmpty() )
        {
        	// no preprocessors, we can directly execute
            dispatcher.doFilter(req, res, null);
        }
        else
        {
	        final FilterChain chain = new FilterChain()
	        {
	        	private int index = 0;

	            @Override
	            public void doFilter(final ServletRequest request, final ServletResponse response)
	            throws IOException, ServletException
	            {
	            	if ( index == localHandlers.size() )
	            	{
	            		dispatcher.doFilter(request, response, null);
	            	}
	            	else
	            	{
	            		final PreprocessorHandler handler = localHandlers.get(index);
	            		index++;
	            		handler.handle(request, response, this);
	            	}
	            }
	        };
	        chain.doFilter(req, res);
        }
    }

    private void updateRuntimeChangeCount()
    {
        this.serviceRuntime.updateChangeCount();
    }
}

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
package org.apache.felix.scr.impl.manager;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.IdentityHashMap;

import org.apache.felix.scr.impl.inject.ComponentMethods;
import org.apache.felix.scr.impl.inject.LifecycleMethod;
import org.apache.felix.scr.impl.inject.MethodResult;
import org.apache.felix.scr.impl.inject.RefPair;
import org.apache.felix.scr.impl.logger.InternalLogger.Level;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.ComponentInstance;


/**
 * The <code>ServiceFactoryComponentManager</code> for components specified with &lt;service serviceFactory='true'/&gt;
 * in the xml metadata. The component must be delayed, not immediate or factory.
 */
public class ServiceFactoryComponentManager<S> extends SingleComponentManager<S>
{

    @Override
    public void setServiceProperties(Dictionary<String, ?> serviceProperties)
    {
        throw new IllegalStateException( "Bundle or instance scoped service properties are immutable" );
    }


    @Override
    void postRegister()
    {
        // do nothing
    }

    // maintain the map of ComponentContext objects created for the
    // service instances
    private IdentityHashMap<S, ComponentContextImpl<S>> serviceContexts = new IdentityHashMap<>();

    /**
     * @param container ComponentHolder for configuration management
     * @param componentMethods
     */
    public ServiceFactoryComponentManager( ComponentContainer<S> container, ComponentMethods<S> componentMethods )
    {
        super( container, componentMethods );
    }


    /* (non-Javadoc)
     * @see org.apache.felix.scr.AbstractComponentManager#deleteComponent()
     */
    @Override
    protected void deleteComponent( int reason )
    {
        if ( !isStateLocked() )
        {
            throw new IllegalStateException( "need write lock (deleteComponent)" );
        }
        for (ComponentContextImpl<S> componentContext: getComponentContexts() )
        {
            disposeImplementationObject( componentContext, reason );
            getLogger().log(Level.DEBUG,
                "Unset implementation object for component in deleteComponent for reason {0}",
                    null, REASONS[ reason ] );
        }
        synchronized (serviceContexts)
        {
            serviceContexts.clear();
        }
        clearServiceProperties();
    }


    /* (non-Javadoc)
     * @see org.osgi.framework.ServiceFactory#getService(org.osgi.framework.Bundle, org.osgi.framework.ServiceRegistration)
     */
    @Override
    public S getService( Bundle bundle, ServiceRegistration<S> serviceRegistration )
    {
        getLogger().log(Level.DEBUG, "ServiceFactory.getService()", null);

        // When the getServiceMethod is called, the implementation object must be created

        ComponentContextImpl<S> componentContext = new ComponentContextImpl<>(this, bundle, serviceRegistration);
        if (collectDependencies(componentContext) )
        {
            getLogger().log(Level.DEBUG,
                    "getService (ServiceFactory) dependencies collected.",
                    null );

        }
        else
        {
            //cannot obtain service from a required reference
            return null;
        }
        State previousState = getState();
        // private ComponentContext and implementation instances
        S service = createImplementationObject( bundle, new SetImplementationObject<S>()
        {
            @Override
            public void presetComponentContext( ComponentContextImpl<S> componentContext )
            {
                synchronized ( serviceContexts )
                {
                    serviceContexts.put( componentContext.getImplementationObject( false ), componentContext );
                }
            }

            @Override
            public void resetImplementationObject( S implementationObject )
            {
                synchronized ( serviceContexts )
                {
                    serviceContexts.remove( implementationObject );
                }
            }

        }, componentContext );

        // register the components component context if successful
        if ( service == null )
        {
            // log that the service factory component cannot be created (we don't
            // know why at this moment; this should already have been logged)
            getLogger().log(Level.DEBUG,
                "Failed creating the component instance; see log for reason", null);
        }
        else
        {
            setState(previousState, State.active);
        }

        return service;
    }


    /* (non-Javadoc)
     * @see org.osgi.framework.ServiceFactory#ungetService(org.osgi.framework.Bundle, org.osgi.framework.ServiceRegistration, java.lang.Object)
     */
    @Override
    public void ungetService( Bundle bundle, ServiceRegistration<S> registration, S service )
    {
        getLogger().log(Level.DEBUG, "ServiceFactory.ungetService()", null);

        // When the ungetServiceMethod is called, the implementation object must be deactivated
        // private ComponentContext and implementation instances
        final ComponentContextImpl<S> serviceContext;
        synchronized ( serviceContexts )
        {
            serviceContext = serviceContexts.get( service );
        }
        if (serviceContext != null) {
            disposeImplementationObject( serviceContext, ComponentConstants.DEACTIVATION_REASON_DISPOSED );
            synchronized ( serviceContexts )
            {
                if (serviceContexts.remove( service ) != null) {
                    // if this was the last use of the component, go back to REGISTERED state
                    State previousState;
                    if (serviceContexts.isEmpty()
                        && (previousState = getState()) == State.active)
                    {
                        setState(previousState, State.satisfied);
                    }
                }
            }
        }
    }

    private Collection<ComponentContextImpl<S>> getComponentContexts()
    {
        synchronized ( serviceContexts )
        {
            return new ArrayList<>( serviceContexts.values() );
        }
    }

    @Override
    <T> void invokeBindMethod( DependencyManager<S, T> dependencyManager, RefPair<S, T> refPair, int trackingCount )
    {
        for ( ComponentContextImpl<S> cc : getComponentContexts() )
        {
            dependencyManager.invokeBindMethod( cc, refPair, trackingCount, cc.getEdgeInfo( dependencyManager ) );
        }
    }

    @Override
    <T> boolean invokeUpdatedMethod( DependencyManager<S, T> dependencyManager, RefPair<S, T> refPair, int trackingCount )
    {
        // as all instances are treated the same == have the same updated signatures for methods/fields
        // we just need one result
        boolean reactivate = false;
        for ( ComponentContextImpl<S> cc : getComponentContexts() )
        {
            if ( dependencyManager.invokeUpdatedMethod( cc, refPair, trackingCount, cc.getEdgeInfo( dependencyManager ) ) )
            {
                reactivate = true;
            }
        }
        return reactivate;
    }

    @Override
    <T> void invokeUnbindMethod( DependencyManager<S, T> dependencyManager, RefPair<S, T> oldRefPair, int trackingCount )
    {
        for ( ComponentContextImpl<S> cc : getComponentContexts() )
        {
            dependencyManager.invokeUnbindMethod( cc, oldRefPair, trackingCount, cc.getEdgeInfo( dependencyManager ) );
        }
    }

    @Override
    protected MethodResult invokeModifiedMethod()
    {
        LifecycleMethod modifiedMethod = getComponentMethods().getModifiedMethod();
        MethodResult result = MethodResult.VOID;
        for ( ComponentContextImpl<S> componentContext : getComponentContexts() )
        {
            S instance = componentContext.getImplementationObject(true);
            result = modifiedMethod.invoke( instance,
                    componentContext, -1, MethodResult.VOID );

        }
        return result;
    }

    //---------- Component interface

    @Override
    public ComponentInstance<S> getComponentInstance()
    {
        // TODO: should return the component instance corresponding to the
        // bundle owning ScrService
        return super.getComponentInstance();
    }

}

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
package org.apache.felix.scr.impl.inject.methods;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.felix.scr.impl.inject.ActivatorParameter;
import org.apache.felix.scr.impl.inject.LifecycleMethod;
import org.apache.felix.scr.impl.inject.MethodResult;
import org.apache.felix.scr.impl.inject.ScrComponentContext;
import org.apache.felix.scr.impl.inject.internal.Annotations;
import org.apache.felix.scr.impl.inject.internal.ClassUtils;
import org.apache.felix.scr.impl.logger.ComponentLogger;
import org.apache.felix.scr.impl.logger.InternalLogger.Level;
import org.apache.felix.scr.impl.metadata.DSVersion;


public class ActivateMethod extends BaseMethod<ActivatorParameter, Object> implements LifecycleMethod
{

    protected final boolean m_supportsInterfaces;

    public ActivateMethod( final String methodName,
            final boolean methodRequired,
            final Class<?> componentClass,
            final DSVersion dsVersion,
            final boolean configurableServiceProperties,
            final boolean supportsInterfaces)
    {
        super( methodName, methodRequired, componentClass, dsVersion, configurableServiceProperties );
        m_supportsInterfaces = supportsInterfaces;
    }


    @Override
    protected MethodInfo<Object> doFindMethod( final Class<?> targetClass,
            final boolean acceptPrivate,
            final boolean acceptPackage,
            final ComponentLogger logger )
        throws SuitableMethodNotAccessibleException, InvocationTargetException
    {

        boolean suitableMethodNotAccessible = false;

        try
        {
            // find the declared method in this class
            final Method method = getMethod( targetClass, getMethodName(), new Class[]
                { ClassUtils.COMPONENT_CONTEXT_CLASS }, acceptPrivate, acceptPackage, logger );
            if ( method != null )
            {
                return new MethodInfo<>(method);
            }
        }
        catch ( SuitableMethodNotAccessibleException thrown )
        {
            logger.log(Level.DEBUG, "SuitableMethodNotAccessible", thrown);
            suitableMethodNotAccessible = true;
        }
        if (getDSVersion().isDS11())
        {
            List<Method> methods = getSortedMethods( targetClass);
            for (Method m: methods)
            {
                final Class<?>[] parameterTypes = m.getParameterTypes();
                if (parameterTypes.length == 1)
                {
                    Class<?> type = parameterTypes[0];
                    //single parameter method with parameter ComponentContext will already have been found.
                    if (type == ClassUtils.BUNDLE_CONTEXT_CLASS)
                    {
                        if ( accept( m, acceptPrivate, acceptPackage, returnValue() ) )
                        {
                            return new MethodInfo<>(m);
                        }
                        suitableMethodNotAccessible = true;
                    }
                    if (getDSVersion().isDS13() && isAnnotation(type))
                    {
                        if ( accept( m, acceptPrivate, acceptPackage, returnValue() ) )
                        {
                            return new MethodInfo<>(m);
                        }
                        suitableMethodNotAccessible = true;
                    }
                    if (type == ClassUtils.MAP_CLASS)
                    {
                        if ( accept( m, acceptPrivate, acceptPackage, returnValue() ) )
                        {
                            return new MethodInfo<>(m);
                        }
                        suitableMethodNotAccessible = true;
                    }
                    if (type == int.class)
                    {
                        if ( accept( m, acceptPrivate, acceptPackage, returnValue() ) )
                        {
                            return new MethodInfo<>(m);
                        }
                        suitableMethodNotAccessible = true;
                    }
                    if (type == Integer.class)
                    {
                        if ( accept( m, acceptPrivate, acceptPackage, returnValue() ) )
                        {
                            return new MethodInfo<>(m);
                        }
                        suitableMethodNotAccessible = true;
                    }

                }
                else if (parameterTypes.length > 1)
                {
                    boolean accept = true;
                    for (Class<?> type: parameterTypes)
                    {
                        accept = type == ClassUtils.COMPONENT_CONTEXT_CLASS
                            || type == ClassUtils.BUNDLE_CONTEXT_CLASS
                            || type == ClassUtils.MAP_CLASS
                            || ( isDeactivate() && ( type == int.class || type == Integer.class))
                            || ( getDSVersion().isDS13() && isAnnotation(type));
                        if ( !accept )
                        {
                            break;
                        }

                    }
                    if (accept)
                    {
                        if ( accept( m, acceptPrivate, acceptPackage, returnValue() ) )
                        {
                            return new MethodInfo<>(m);
                        }
                        suitableMethodNotAccessible = true;
                    }

                }
                else //no parameters
                {
                    if ( accept( m, acceptPrivate, acceptPackage, returnValue() ) )
                    {
                        return new MethodInfo<>(m);
                    }
                    suitableMethodNotAccessible = true;
                }

            }
        }

        if ( suitableMethodNotAccessible )
        {
            throw new SuitableMethodNotAccessibleException();
        }

        return null;
    }

    @Override
    protected void setTypes(Object types)
    {
        // Don't care about types
    }

    boolean isDeactivate()
    {
        return false;
    }


    /**
     * returns the declared methods of the target class, with the correct name, sorted by number of parameters ( no parameters last)
     * @param targetClass class to examine methods of
     * @return sorted methods of correct name;
     */
    List<Method> getSortedMethods(Class<?> targetClass)
    {
        List<Method> result = new ArrayList<>();
        Method[] methods = targetClass.getDeclaredMethods();
        for (Method m: methods)
        {
            if (m.getName().equals(getMethodName()))
            {
                result.add(m);
            }
        }
        Collections.sort(result, new Comparator<Method>(){

            @Override
            public int compare(Method m1, Method m2)
            {
                final int l1 = m1.getParameterTypes().length;
                final int l2 = m2.getParameterTypes().length;
                if ( l1 == 0)
                {
                    return l2;
                }
                if ( l2 == 0)
                {
                    return -l1;
                }
                if (l1 == 1 && l2 == 1)
                {
                    final Class<?> t1 = m1.getParameterTypes()[0];
                    final Class<?> t2 = m2.getParameterTypes()[0];
                    //t1, t2 can't be equal
                    if (t1 == ClassUtils.COMPONENT_CONTEXT_CLASS) return -1;
                    if (t2 == ClassUtils.COMPONENT_CONTEXT_CLASS) return 1;
                    if (t1 == ClassUtils.BUNDLE_CONTEXT_CLASS) return -1;
                    if (t2 == ClassUtils.BUNDLE_CONTEXT_CLASS) return 1;
                    if (isAnnotation(t1)) return isAnnotation(t2)? 0: -1;
                    if (isAnnotation(t2)) return 1;
                    if (t1 == ClassUtils.MAP_CLASS) return -1;
                    if (t2 == ClassUtils.MAP_CLASS) return 1;
                    if (t1 == int.class) return -1;
                    if (t2 == int.class) return 1;
                    if (t1 == Integer.class) return -1;
                    if (t2 == Integer.class) return 1;
                    return 0;
                }
                return l1 - l2;
            }

        });
        return result;
    }

    private boolean isAnnotation(final Class<?> t1)
    {
        return t1.isAnnotation() || (m_supportsInterfaces && t1.isInterface() && !(t1 == ClassUtils.MAP_CLASS));
    }


    @Override
    protected Object[] getParameters( Method method, ActivatorParameter rawParameter )
    {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        final ActivatorParameter ap = rawParameter;
        final Object[] param = new Object[parameterTypes.length];
        for ( int i = 0; i < param.length; i++ )
        {
            if ( parameterTypes[i] == ClassUtils.COMPONENT_CONTEXT_CLASS )
            {
                param[i] = ap.getComponentContext();
            }
            else if ( parameterTypes[i] == ClassUtils.BUNDLE_CONTEXT_CLASS )
            {
                param[i] = ap.getComponentContext().getBundleContext();
            }
            else if ( parameterTypes[i] == ClassUtils.MAP_CLASS )
            {
                // note: getProperties() returns a ReadOnlyDictionary which is a Map
                param[i] = ap.getComponentContext().getProperties();
            }
            else if ( parameterTypes[i] == ClassUtils.INTEGER_CLASS || parameterTypes[i] == Integer.TYPE )
            {
                param[i] = ap.getReason();
            }
            else
            {
                param[i] = Annotations.toObject(parameterTypes[i],
                    ap.getComponentContext().getPropertiesMap(),
                    ap.getComponentContext().getBundleContext().getBundle(), m_supportsInterfaces);
            }
        }

        return param;
    }


    @Override
    protected String getMethodNamePrefix()
    {
        return "activate";
    }

    /**
     * @see org.apache.felix.scr.impl.inject.LifecycleMethod#invoke(Object,
     *      ScrComponentContext, int, MethodResult)
     */
    @Override
    public MethodResult invoke(final Object componentInstance,
            final ScrComponentContext componentContext,
    		final int reason,
    		final MethodResult methodCallFailureResult) {
        return invoke(componentInstance, new ActivatorParameter(componentContext, reason), methodCallFailureResult);
    }

    @Override
    public MethodResult invoke(final  Object componentInstance,
    		final ActivatorParameter rawParameter,
    		final MethodResult methodCallFailureResult)
    {
        if (methodExists( rawParameter.getComponentContext().getLogger() ))
        {
            return super.invoke(componentInstance, rawParameter, methodCallFailureResult );
        }
        return null;
    }

}

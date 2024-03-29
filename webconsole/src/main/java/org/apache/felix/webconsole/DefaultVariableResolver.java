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
package org.apache.felix.webconsole;

import java.util.HashMap;
import java.util.Map;

import org.apache.felix.webconsole.servlet.RequestVariableResolver;

/**
 * The <code>DefaultVariableResolver</code> is a <code>HashMap</code> based
 * default implementation of the {@link VariableResolver} interface. It may
 * be used by plugins to implement the interface for the request and is also
 * used by the
 * {@link WebConsoleUtil#getVariableResolver(javax.servlet.ServletRequest)}
 * as the variable resolver if none has yet been assigned to the request.
 * @deprecated Use {@link RequestVariableResolver} instead.
 */
@SuppressWarnings({ "rawtypes" })
@Deprecated
public class DefaultVariableResolver extends RequestVariableResolver implements VariableResolver {

    private static final long serialVersionUID = 4148807223433047780L;

    /**
     * Creates a new variable resolver with default capacity.
     */
    public DefaultVariableResolver() {
        super();
    }

    /**
     * Creates a new variable resolver and initializes both - capacity &amp; load factor
     * 
     * @param initialCapacity  the initial capacity of the variable container
     * @param loadFactor the load factor of the variable container
     * @see HashMap#HashMap(int, float)
     */
    public DefaultVariableResolver( final int initialCapacity, final float loadFactor ) {
        this();
    }

    /**
     * Creates a new variable resolver with specified initial capacity
     * 
     * @param initialCapacity  the initial capacity of the variable container
     * @see HashMap#HashMap(int)
     */
    public DefaultVariableResolver( final int initialCapacity ) {
        this();
    }

    /**
     * Creates a new variable resolver copying the variables from the given map.
     * 
     * @param source  the map whose variables are to be placed in this resolver.
     * @see HashMap#HashMap(Map)
     */
    @SuppressWarnings({"unchecked"})
    public DefaultVariableResolver( final Map source ) {
        this.putAll(source);
    }
}

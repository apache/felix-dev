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
package org.apache.felix.scr.integration.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnyServiceComponent
{
    public final Object constructorInject;
    public volatile Object activateInject = null;
    public volatile Object bindMethodInject = null;
    public volatile Object fieldInject = null;

    public final List<Object> constructorInjectList;
    public volatile List<Object> activateInjectList = null;
    public final List<Object> fieldInjectList = new ArrayList<>();

    public final Map<String, Object> constructorInjectMap;
    public volatile Map<String, Object> activateInjectMap = null;
    public volatile Map<String, Object> bindMethodInjectMap = null;
    public volatile Map<String, Object> fieldInjectMap = null;

    public volatile String fieldInjectInvalid = null;

    public AnyServiceComponent()
    {
        this(null);
    }

    public AnyServiceComponent(Object anyService)
    {
        this(anyService, null, null);
    }

    protected AnyServiceComponent(Object anyService, List<Object> anyServices, Map<String, Object> anyServiceMap)
    {
        constructorInject = anyService;
        constructorInjectList = anyServices;
        constructorInjectMap = anyServiceMap;
    }

    void simpleActivate()
    {
    }

    void injectAnyServiceActivate(Object anyService)
    {
        this.activateInject = anyService;
    }

    void setAnyService(Object anyService)
    {
        this.bindMethodInject = anyService;
    }

    void unsetAnyService(Object anyService)
    {
        if (this.bindMethodInject == anyService)
        {
            this.bindMethodInject = null;
        }
    }

    void setAnyServiceInvalid(String anyService)
    {
        this.bindMethodInject = anyService;
    }

    void unsetAnyServiceInvalid(String anyService)
    {
        if (this.bindMethodInject == anyService)
        {
            this.bindMethodInject = null;
        }
    }

    void setAnyServiceMap(Map<String, Object> anyService)
    {
        this.bindMethodInjectMap = anyService;
    }

    void unsetAnyServiceMap(Map<String, Object> anyService)
    {
        if (this.bindMethodInjectMap == anyService)
        {
            this.bindMethodInjectMap = null;
        }
    }
}


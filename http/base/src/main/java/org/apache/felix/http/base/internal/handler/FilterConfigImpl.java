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
package org.apache.felix.http.base.internal.handler;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;

/**
 * Implementation of the filter configuration.
 */
public final class FilterConfigImpl implements FilterConfig
{
    private final String name;
    private final ServletContext context;
    private final Map<String, String> initParams;

    public FilterConfigImpl(final String name, final ServletContext context, final Map<String, String> initParams)
    {
        this.name = name;
        this.context = context;
        this.initParams = initParams;
    }

    @Override
    public String getFilterName()
    {
        return this.name;
    }

    @Override
    public ServletContext getServletContext()
    {
        return this.context;
    }

    @Override
    public String getInitParameter(final String name)
    {
        return this.initParams.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames()
    {
        return Collections.enumeration(this.initParams.keySet());
    }
}

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

import java.io.IOException;

import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.apache.felix.http.base.internal.runtime.PreprocessorInfo;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;
import org.osgi.service.servlet.runtime.dto.DTOConstants;
import org.osgi.service.servlet.whiteboard.Preprocessor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * The preprocessor handler handles the initialization and destruction of preprocessor
 * objects.
 */
public class PreprocessorHandler implements Comparable<PreprocessorHandler>
{
    private final PreprocessorInfo info;

    private final ServletContext context;

    private final BundleContext bundleContext;

    private volatile Preprocessor preprocessor;

    public PreprocessorHandler(final BundleContext bundleContext,
            final ServletContext context,
            final PreprocessorInfo info)
    {
        this.bundleContext = bundleContext;
        this.context = context;
        this.info = info;
    }

    @Override
    public int compareTo(final PreprocessorHandler other)
    {
        return this.info.compareTo(other.info);
    }

    public ServletContext getContext()
    {
        return this.context;
    }

    public PreprocessorInfo getPreprocessorInfo()
    {
        return this.info;
    }

    public int init()
    {
        this.preprocessor = this.getPreprocessorInfo().getService(this.bundleContext);

        if (this.preprocessor == null)
        {
            return DTOConstants.FAILURE_REASON_SERVICE_NOT_GETTABLE;
        }

        try
        {
            this.preprocessor.init(new FilterConfigImpl(this.preprocessor.getClass().getName(),
                    getContext(),
                    getPreprocessorInfo().getInitParameters()));
        }
        catch (final Exception e)
        {
            SystemLogger.LOGGER.error(SystemLogger.formatMessage(this.getPreprocessorInfo().getServiceReference(),
                    "Error during calling init() on preprocessor ".concat(this.info.getClassName(this.preprocessor))), e);

            this.getPreprocessorInfo().ungetService(this.bundleContext, this.preprocessor);
            this.preprocessor = null;

            return DTOConstants.FAILURE_REASON_EXCEPTION_ON_INIT;
        }

        return -1;
    }

    public boolean destroy()
    {
        if (this.preprocessor == null)
        {
            return false;
        }

        try
        {
            preprocessor.destroy();
        }
        catch ( final Exception ignore )
        {
            // we ignore this
            SystemLogger.LOGGER.error(SystemLogger.formatMessage(this.getPreprocessorInfo().getServiceReference(),
                    "Error during calling destroy() on preprocessor ".concat(this.info.getClassName(this.preprocessor))), ignore);
        }
        this.getPreprocessorInfo().ungetService(this.bundleContext, this.preprocessor);
        this.preprocessor = null;

        return true;
    }

    public void handle(@NotNull final ServletRequest req,
            @NotNull final ServletResponse res,
            @NotNull final FilterChain chain) throws ServletException, IOException
    {
        final Preprocessor local = this.preprocessor;
        if ( local != null )
        {
            local.doFilter(req, res, chain);
        }
        else
        {
            throw new ServletException("Preprocessor has been unregistered");
        }
    }

    public boolean dispose()
    {
        // fully destroy the preprocessor
        return this.destroy();
    }

    @Override
    public int hashCode()
    {
        return 31 + info.hashCode();
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || getClass() != obj.getClass() )
        {
            return false;
        }
        final PreprocessorHandler other = (PreprocessorHandler) obj;
        return info.equals(other.info);
    }
}

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
package org.apache.felix.resolver;

import org.osgi.resource.Resource;
import org.osgi.service.log.FormatterLogger;

/**
 * A {@link Logger} that redirects all messages to the {@link FormatterLogger}
 * provided by the resolve context.
 */
class ContextLogger extends Logger
{
    private final FormatterLogger m_delegate;
    private final Logger m_fallback;

    ContextLogger(FormatterLogger delegate, Logger fallback)
    {
        // the delegate decides which levels are enabled
        super(LOG_DEBUG);
        m_delegate = delegate;
        m_fallback = fallback;
    }

    @Override
    public boolean isDebugEnabled()
    {
        return m_delegate.isDebugEnabled();
    }

    @Override
    public void debug(String format, Object... args)
    {
        m_delegate.debug(format, args);
    }

    @Override
    protected void doLog(int level, String msg, Throwable throwable)
    {
        switch (level)
        {
            case LOG_ERROR:
                m_delegate.error("%s", msg, throwable);
                break;
            case LOG_WARNING:
                m_delegate.warn("%s", msg, throwable);
                break;
            case LOG_INFO:
                m_delegate.info("%s", msg, throwable);
                break;
            default:
                m_delegate.debug("%s", msg, throwable);
        }
    }

    @Override
    public void logUsesConstraintViolation(Resource resource, ResolutionError error)
    {
        m_fallback.logUsesConstraintViolation(resource, error);
        m_delegate.debug("Uses constraint violation for %s: %s", resource,
            error.getMessage());
    }
}

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
package org.apache.felix.scr.impl.logger;

import org.osgi.service.log.Logger;

final class OSGiLogger implements InternalLogger
{

    private final Logger logger;

    public OSGiLogger(Logger logger)
    {
        this.logger = logger;
    }

    public void log(Level level, String message, Throwable ex)
    {
        if ( ex == null )
        {
            switch ( level )
            {
                case AUDIT : logger.audit(message); break;
                case ERROR : logger.error(message); break;
                case WARN : logger.warn(message); break;
                case INFO : logger.info(message); break;
                case DEBUG : logger.debug(message); break;
                case TRACE : logger.trace(message); break;
                default : logger.debug(message);
            }
        }
        else
        {
            switch ( level )
            {
                case AUDIT : logger.audit(message, ex); break;
                case ERROR : logger.error(message, ex); break;
                case WARN : logger.warn(message, ex); break;
                case INFO : logger.info(message, ex); break;
                case DEBUG : logger.debug(message, ex); break;
                case TRACE : logger.trace(message, ex); break;
                default : logger.debug(message, ex);
            }
        }
    }

    public boolean isLogEnabled(Level level)
    {
        switch (level)
        {
            case AUDIT: return true;
            case ERROR: return logger.isErrorEnabled();
            case WARN: return logger.isWarnEnabled();
            case INFO: return logger.isInfoEnabled();
            case DEBUG: return logger.isDebugEnabled();
            case TRACE: return logger.isTraceEnabled();
            default: return logger.isDebugEnabled();
        }
    }
}

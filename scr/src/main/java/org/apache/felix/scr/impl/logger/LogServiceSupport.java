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

import org.osgi.framework.Bundle;
import org.osgi.service.log.LogService;

/**
 * This is a logger based on the LogService.
 * It supports both R6 and R7 logging
 *
 */
class LogServiceSupport
{
    private final boolean r7Enabled;

    private final Object logService;

    private final Bundle bundle;

    private static boolean checkForLoggerFactory(Class<?> clazz)
    {
        while ( clazz != null )
        {
            final Class<?>[] is = clazz.getInterfaces();
            for(final Class<?> c : is)
            {
                if ( "org.osgi.service.log.LoggerFactory".equals(c.getName()) )
                {
                    return true;
                }
                if ( checkForLoggerFactory(c) )
                {
                    return true;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    public LogServiceSupport(final Bundle bundle, final Object logService)
    {
        this.logService = logService;
        this.bundle = bundle;
        this.r7Enabled = checkForLoggerFactory(this.logService.getClass());
    }

    InternalLogger getLogger()
    {
    	try 
    	{
            if ( r7Enabled )
            {
                return LogService.class.isInstance( logService ) ? 
            	    	new R7LogServiceLogger( this.bundle, ( LogService ) this.logService, null ) :
            	        new ReflectiveR7LogServiceLogger( this.bundle, logService, null );
            }
            return LogService.class.isInstance( logService ) ? new R6LogServiceLogger( ( LogService ) this.logService ) :
        	    new ReflectiveR6LogServiceLogger(logService);
    	} catch ( Throwable t ) {
    		InternalLogger logger = new StdOutLogger();
    		logger.log(1, "An error occurred creating the logger ", t);
    		return logger;
    	}
    }

    InternalLogger getLogger(final String className)
    {
    	try {
    		if ( r7Enabled )
    		{
    			return LogService.class.isInstance( logService ) ? 
    					new R7LogServiceLogger( this.bundle, ( LogService ) this.logService, className ) :
    						new ReflectiveR7LogServiceLogger( this.bundle, logService, className );
    		}
    		return LogService.class.isInstance( logService ) ? new R6LogServiceLogger( ( LogService ) this.logService ) :
    			new ReflectiveR6LogServiceLogger(logService);
    	} catch ( Throwable t ) {
    		InternalLogger logger = new StdOutLogger();
    		logger.log(1, "An error occurred creating the logger for " + className, t);
    		return logger;
    	}
    }
}
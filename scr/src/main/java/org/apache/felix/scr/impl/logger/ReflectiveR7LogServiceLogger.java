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

import java.lang.reflect.Method;

import org.osgi.framework.Bundle;
import org.osgi.service.log.Logger;

/**
 * This is a logger based on the R7 LogService/LoggerFactory
 */
class ReflectiveR7LogServiceLogger implements InternalLogger
{
    private final Object logger;
    private final Method isErrorEnabled;
    private final Method isWarnEnabled;
    private final Method isInfoEnabled;
    private final Method isDebugEnabled;

    private final Method errorString;
    private final Method warnString;
    private final Method infoString;
    private final Method debugString;

    private final Method errorStringEx;
    private final Method warnStringEx;
    private final Method infoStringEx;
    private final Method debugStringEx;

    public ReflectiveR7LogServiceLogger(final Bundle bundle, final Object loggerFactory, final String name) throws Throwable
    {
        Class<?> loggerClazz = loggerFactory.getClass().getClassLoader().loadClass( "org.osgi.service.log.LoggerFactory" );

        
        logger = loggerFactory.getClass().getMethod( "getLogger",  String.class, Class.class )
        		.invoke( loggerFactory, name == null ? Logger.ROOT_LOGGER_NAME : name, loggerClazz );
        
        
        isErrorEnabled = loggerClazz.getMethod( "isErrorEnabled");
        isWarnEnabled = loggerClazz.getMethod( "isWarnEnabled" );
        isInfoEnabled = loggerClazz.getMethod( "isInfoEnabled" );
        isDebugEnabled = loggerClazz.getMethod( "isDebugEnabled" );
        
        errorString = loggerClazz.getMethod( "error", String.class );
        warnString = loggerClazz.getMethod( "warn", String.class );
        infoString = loggerClazz.getMethod( "info", String.class );
        debugString = loggerClazz.getMethod( "debug", String.class );

        errorStringEx = loggerClazz.getMethod( "error", String.class, Throwable.class );
        warnStringEx = loggerClazz.getMethod( "warn", String.class, Throwable.class );
        infoStringEx = loggerClazz.getMethod( "info", String.class, Throwable.class );
        debugStringEx = loggerClazz.getMethod( "debug", String.class, Throwable.class );
        
    }

    @Override
    public boolean checkScrConfig() {
        return false;
    }

    @Override
    public boolean isLogEnabled(final int level)
    {
        try 
        {
            switch ( level )
            {
                case 1 : return (boolean) isErrorEnabled.invoke( logger );
                case 2 : return (boolean) isWarnEnabled.invoke( logger );
                case 3 : return (boolean) isInfoEnabled.invoke( logger );
                default : return (boolean) isDebugEnabled.invoke( logger );
            }
        } catch ( Throwable e ) {
            new StdOutLogger().log( 1, "An error occurred attmepting to determine the enabled log level", e );
            return false;
        }
    }

    @Override
    public void log(final int level, final String message, final Throwable ex)
    {
    	try {
            if ( ex == null )
            {
                switch ( level )
                {
                    case 1 : errorString.invoke( logger, message ); break;
                    case 2 : warnString.invoke( logger, message ); break;
                    case 3 : infoString.invoke( logger, message ); break;
                    default : debugString.invoke( logger, message );
                }
            }
            else
            {
                switch ( level )
                {
                    case 1 : errorStringEx.invoke( logger, message, ex ); break;
                    case 2 : warnStringEx.invoke( logger, message, ex ); break;
                    case 3 : infoStringEx.invoke( logger, message, ex ); break;
                    default : debugStringEx.invoke( logger, message, ex );
                }
            }
    	} catch ( Throwable t ) {
    		new StdOutLogger().log( 1, "An error occurred attmepting to log a message", t );
    	}
    }
}
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

import org.osgi.framework.BundleContext;

/**
 * This is used to retrieve the appropriate logger instance based on
 * a specific log configuration
 */
public final class ScrLoggerFactory 
{
    /** Non-instantiable */
    private ScrLoggerFactory() 
    {
        throw new IllegalAccessError("Cannot be instantiated");
    }
    
    /**
     * Retrieves the logger based on the provided log configuration
     * 
     * <ul>
     * <li>If the logging is disabled, the {@link NoOpLogger} is used</li>
     * <li>If the logging is enabled but the log extension is disabled, use {@link ScrLogManager}</li>
     * <li>If the logging is enabled and the log extension is also enabled, use {@link ExtLogManager}</li>
     * </ul>
     * 
     * @param context the bundle context of the SCR bundle
     * @param config the log configuration
     * 
     * @return the logger
     */
    public static ScrLogger create(BundleContext context, LogConfiguration config) 
    {
        if (!config.isLogEnabled()) 
        {
            return new NoOpLogger();
        } 
        else 
        {
            ScrLogManager manager = null;
            if (config.isLogExtensionEnabled()) 
            {
                manager = new ExtLogManager(context, config);
            } 
            else 
            {
                manager = new ScrLogManager(context, config);
            }
            manager.init();
            return manager.scr();
        }
    }

}

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
package org.apache.felix.scr.impl.config;


import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.scr.impl.Activator;
import org.apache.felix.scr.impl.ComponentCommands;
import org.apache.felix.scr.impl.logger.InternalLogger.Level;
import org.apache.felix.scr.impl.manager.ScrConfiguration;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.metatype.MetaTypeProvider;


/**
 * The <code>ScrConfiguration</code> class conveys configuration for the
 * Felix DS implementation bundle.
 * <p>
 * <b>Configuration Source</b>
 * <p>
 * <ol>
 * <li>Framework properties: These are read when the Declarative Services
 * implementation is first started.</li>
 * <li>Configuration Admin Service: Properties are provided by means of a
 * <code>ManagedService</code> with Service PID
 * <code>org.apache.felix.scr.ScrService</code>. This class uses an OSGi
 * Service Factory ({@link ScrManagedServiceServiceFactory}) to register the
 * managed service without requiring the Configuration Admin Service API to be
 * required upfront.
 * </li>
 * </ol>
 * <p>
 * See the <i>Configuration</i> section of the
 * <a href="http://felix.apache.org/site/apache-felix-service-component-runtime.html">Apache Felix Service Component Runtime</a>
 * documentation page for detailed information.
 */
public class ScrConfigurationImpl implements ScrConfiguration
{

    private static final String VALUE_TRUE = Boolean.TRUE.toString();

    private static final String LOG_LEVEL_DEBUG = "debug";

    private static final String LOG_LEVEL_INFO = "info";

    private static final String LOG_LEVEL_WARN = "warn";

    private static final String LOG_LEVEL_ERROR = "error";

    private static final String PROP_SHOWTRACE = "ds.showtrace";

    private static final String PROP_SHOWERRORS = "ds.showerrors";

    private final Activator activator;

    private Level logLevel;

    private boolean factoryEnabled;

    private boolean keepInstances;

    private boolean infoAsService;

    private boolean cacheMetadata;

    private boolean isLogEnabled;

    private boolean isLogExtensionEnabled;

    private long lockTimeout = DEFAULT_LOCK_TIMEOUT_MILLISECONDS;

    private long stopTimeout = DEFAULT_STOP_TIMEOUT_MILLISECONDS;

    private long serviceChangecountTimeout = DEFAULT_SERVICE_CHANGECOUNT_TIMEOUT_MILLISECONDS;

    private Boolean globalExtender;

    private volatile BundleContext bundleContext;

    private volatile ServiceRegistration<?> managedServiceRef;

    private volatile ServiceRegistration<?> metatypeProviderRef;

    private ComponentCommands scrCommand;

    public ScrConfigurationImpl(Activator activator )
    {
        this.activator = activator;
    }

    public void start(final BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;

        // listen for Configuration Admin configuration
        final Dictionary<String, Object> msProps = new Hashtable<>();
        msProps.put(Constants.SERVICE_PID, PID);
        msProps.put(Constants.SERVICE_DESCRIPTION, "SCR Configurator");
        msProps.put(Constants.SERVICE_VENDOR, "The Apache Software Foundation");


        // Process configure from bundle context properties so they can be predictably
        // overriden by configuration admin later.
        // Note that if the managed service is registered first then it is random which will win since
        // configuration may be delivered asynchronously
        configure( null, true );

        managedServiceRef = bundleContext.registerService("org.osgi.service.cm.ManagedService", new ScrManagedServiceServiceFactory(this),
                msProps);

        final Dictionary<String, Object> mtProps = new Hashtable<>();
        mtProps.put(MetaTypeProvider.METATYPE_PID, PID);
        mtProps.put(Constants.SERVICE_DESCRIPTION, "SCR Configurator MetaTypeProvider");
        mtProps.put(Constants.SERVICE_VENDOR, "The Apache Software Foundation");

        metatypeProviderRef = bundleContext.registerService("org.osgi.service.metatype.MetaTypeProvider", new ScrMetaTypeProviderServiceFactory(this),
                mtProps);
    }

    public void stop()
    {
        if (this.managedServiceRef != null)
        {
            this.managedServiceRef.unregister();
            this.managedServiceRef = null;
        }

        if (this.metatypeProviderRef != null)
        {
            this.metatypeProviderRef.unregister();
            this.metatypeProviderRef = null;
        }

        this.bundleContext = null;
    }

    public void setScrCommand(ComponentCommands scrCommand)
    {
        this.scrCommand = scrCommand;
        scrCommand.updateProvideScrInfoService(infoAsService());
    }

    // Called from the ScrManagedService.updated method to reconfigure
    void configure( Dictionary<String, ?> config, boolean initialStart )
    {
        Boolean newGlobalExtender;
        Boolean oldGlobalExtender;
        synchronized (this)
        {
            if ( config == null )
            {
                if (initialStart)
                {
                    if (this.bundleContext == null)
                    {
                        logLevel = Level.ERROR;
                        factoryEnabled = false;
                        keepInstances = false;
                        infoAsService = false;
                        lockTimeout = DEFAULT_LOCK_TIMEOUT_MILLISECONDS;
                        stopTimeout = DEFAULT_STOP_TIMEOUT_MILLISECONDS;
                        serviceChangecountTimeout = DEFAULT_SERVICE_CHANGECOUNT_TIMEOUT_MILLISECONDS;
                        newGlobalExtender = false;
                        cacheMetadata = false;
                        isLogEnabled = true;
                        isLogExtensionEnabled = false;
                    }
                    else
                    {
                        logLevel = getDefaultLogLevel();
                        factoryEnabled = getDefaultFactoryEnabled();
                        keepInstances = getDefaultKeepInstances();
                        infoAsService = getDefaultInfoAsService();
                        lockTimeout = getDefaultLockTimeout();
                        stopTimeout = getDefaultStopTimeout();
                        serviceChangecountTimeout = getServiceChangecountTimeout();
                        newGlobalExtender = getDefaultGlobalExtender();
                        cacheMetadata = getDefaultCacheMetadata();
                        isLogEnabled = getDefaultLogEnabled();
                        isLogExtensionEnabled = getDefaultLogExtension();
                    }
                }
                else
                {
                    newGlobalExtender = this.globalExtender;
                }
            }
            else
            {
                logLevel = getLogLevel( config.get( PROP_LOGLEVEL ) );
                factoryEnabled = VALUE_TRUE.equalsIgnoreCase( String.valueOf( config.get( PROP_FACTORY_ENABLED ) ) );
                keepInstances = VALUE_TRUE.equalsIgnoreCase( String.valueOf( config.get( PROP_DELAYED_KEEP_INSTANCES ) ) );
                infoAsService = VALUE_TRUE.equalsIgnoreCase( String.valueOf( config.get( PROP_INFO_SERVICE) ) );
                Long timeout = ( Long ) config.get( PROP_LOCK_TIMEOUT );
                lockTimeout = timeout == null? DEFAULT_LOCK_TIMEOUT_MILLISECONDS: timeout;
                timeout = ( Long ) config.get( PROP_STOP_TIMEOUT );
                stopTimeout = timeout == null? DEFAULT_STOP_TIMEOUT_MILLISECONDS: timeout;
                newGlobalExtender = VALUE_TRUE.equalsIgnoreCase( String.valueOf( config.get( PROP_GLOBAL_EXTENDER) ) );
                cacheMetadata = VALUE_TRUE.equalsIgnoreCase(
                    String.valueOf(config.get(PROP_CACHE_METADATA)));
                isLogEnabled = checkIfLogEnabled(config);
                isLogExtensionEnabled = VALUE_TRUE.equalsIgnoreCase(String.valueOf(config.get(PROP_LOG_EXTENSION)));
            }
            if ( scrCommand != null )
            {
                scrCommand.updateProvideScrInfoService( infoAsService() );
            }
            oldGlobalExtender = this.globalExtender;
            this.globalExtender = newGlobalExtender;
        }
        activator.setLogger();
        if ( newGlobalExtender != oldGlobalExtender )
        {
            activator.restart( newGlobalExtender, initialStart );
        }
    }

	/**
     * Returns the current log level.
     * Note that this log level is not used with an R7 LogService implementation.
     * @return
     */
    @Override
    public Level getLogLevel()
    {
        return logLevel;
    }


    @Override
    public boolean isFactoryEnabled()
    {
        return factoryEnabled;
    }


    @Override
    public boolean keepInstances()
    {
        return keepInstances;
    }

	@Override
    public boolean infoAsService()
    {
        return infoAsService;
    }

    @Override
    public long lockTimeout()
    {
        return lockTimeout;
    }

    @Override
    public long stopTimeout()
    {
        return stopTimeout;
    }

    @Override
    public boolean globalExtender()
    {
        return globalExtender;
    }

    @Override
    public boolean cacheMetadata()
    {
        return cacheMetadata;
    }

    @Override
    public long serviceChangecountTimeout()
    {
        return serviceChangecountTimeout;
    }

    private boolean getDefaultFactoryEnabled()
    {
        return VALUE_TRUE.equals( bundleContext.getProperty( PROP_FACTORY_ENABLED ) );
    }


    private boolean getDefaultKeepInstances()
    {
        return VALUE_TRUE.equals( bundleContext.getProperty( PROP_DELAYED_KEEP_INSTANCES ) );
    }


    private Level getDefaultLogLevel()
    {
        return getLogLevel( bundleContext.getProperty( PROP_LOGLEVEL ) );
    }

    private boolean getDefaultInfoAsService()
    {
        return VALUE_TRUE.equalsIgnoreCase( bundleContext.getProperty( PROP_INFO_SERVICE) );
    }

    private long getDefaultLockTimeout()
    {
        String val = bundleContext.getProperty( PROP_LOCK_TIMEOUT);
        if ( val == null)
        {
            return DEFAULT_LOCK_TIMEOUT_MILLISECONDS;
        }
        return Long.parseLong( val );
    }

    private long getDefaultStopTimeout()
    {
        String val = bundleContext.getProperty( PROP_STOP_TIMEOUT);
        if ( val == null)
        {
            return DEFAULT_STOP_TIMEOUT_MILLISECONDS;
        }
        return Long.parseLong( val );
    }

    private long getServiceChangecountTimeout()
    {
        String val = bundleContext.getProperty( PROP_SERVICE_CHANGECOUNT_TIMEOUT );
        if ( val == null)
        {
            return DEFAULT_SERVICE_CHANGECOUNT_TIMEOUT_MILLISECONDS;
        }
        return Long.parseLong( val );
    }

    private boolean getDefaultGlobalExtender()
    {
        return VALUE_TRUE.equalsIgnoreCase( bundleContext.getProperty( PROP_GLOBAL_EXTENDER) );
    }

    private boolean getDefaultCacheMetadata()
    {
        return VALUE_TRUE.equalsIgnoreCase(
            bundleContext.getProperty(PROP_CACHE_METADATA));
    }

    private Level getLogLevel(final Object levelObject)
    {
        if ( levelObject != null )
        {
            if ( levelObject instanceof Number )
            {
                int ordinal = ((Number) levelObject).intValue();
                return Level.values()[ordinal];
            }

            String levelString = levelObject.toString();
            try
            {
                int ordinal = Integer.parseInt(levelString);
                return Level.values()[ordinal];
            }
            catch ( NumberFormatException nfe )
            {
                // might be a descriptive name
            }

            if ( LOG_LEVEL_DEBUG.equalsIgnoreCase( levelString ) )
            {
                return Level.DEBUG;
            }
            else if ( LOG_LEVEL_INFO.equalsIgnoreCase( levelString ) )
            {
                return Level.INFO;
            }
            else if ( LOG_LEVEL_WARN.equalsIgnoreCase( levelString ) )
            {
                return Level.WARN;
            }
            else if ( LOG_LEVEL_ERROR.equalsIgnoreCase( levelString ) )
            {
                return Level.ERROR;
            }
        }

        // check ds.showtrace property
        if ( VALUE_TRUE.equalsIgnoreCase( bundleContext.getProperty( PROP_SHOWTRACE ) ) )
        {
            return Level.DEBUG;
        }

        // next check ds.showerrors property
        if ( "false".equalsIgnoreCase( bundleContext.getProperty( PROP_SHOWERRORS ) ) )
        {
            return Level.AUDIT; // no logging at all !!
        }

        // default log level (errors only)
        return Level.ERROR;
    }

    @Override
    public boolean isLogEnabled() 
    {
        return isLogEnabled;
    }

    @Override
    public boolean isLogExtensionEnabled() 
    {
        return isLogExtensionEnabled;
    }
    
    private boolean getDefaultLogExtension() 
    {
        return VALUE_TRUE.equalsIgnoreCase(bundleContext.getProperty(PROP_LOG_EXTENSION));
    }
    
    private boolean getDefaultLogEnabled() 
    {
        String isLogEnabled = bundleContext.getProperty(PROP_LOG_ENABLED);
        return isLogEnabled == null ? true : VALUE_TRUE.equalsIgnoreCase(isLogEnabled);
    }
    
    private boolean checkIfLogEnabled(Dictionary<String, ?> properties) 
    {
        Object isLogEnabled = properties.get(PROP_LOG_ENABLED);
        if (isLogEnabled == null) 
        {
            return true;
        }
        return isLogEnabled == null ? true : Boolean.parseBoolean(isLogEnabled.toString());
    }
    

}

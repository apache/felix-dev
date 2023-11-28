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
package org.apache.felix.scr.impl.manager;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.felix.scr.impl.ComponentRegistry;
import org.apache.felix.scr.impl.logger.InternalLogger.Level;
import org.apache.felix.scr.impl.logger.ScrLogger;
import org.apache.felix.scr.impl.metadata.TargetedPID;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.cm.ConfigurationPermission;
import org.osgi.service.cm.ManagedService;

public abstract class RegionConfigurationSupport
{

    private final ScrLogger logger;
    private final ServiceReference<ConfigurationAdmin> caReference;
    private final BundleContext caBundleContext;
    private final Long bundleId;

    private final AtomicInteger referenceCount = new AtomicInteger( 1 );

    // the service registration of the ConfigurationListener service
    private volatile ServiceRegistration<ConfigurationListener> m_registration;

    /**
     *
     * @param bundleContext of the ConfigurationAdmin we are tracking
     * @param registry
     */
    public RegionConfigurationSupport(final ScrLogger logger, final ServiceReference<ConfigurationAdmin> reference, Bundle bundle)
    {
        this.logger = logger;
        this.caReference = reference;
        this.bundleId = bundle.getBundleId();
        this.caBundleContext = bundle.getBundleContext();
    }

    public void start()
    {
        // register as listener for configurations
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put( Constants.SERVICE_DESCRIPTION, "Declarative Services Configuration Support Listener" );
        props.put( Constants.SERVICE_VENDOR, "The Apache Software Foundation" );

        // If RegionConfigurationSupport *directly* implements ConfigurationListener then we get NoClassDefFoundError
        // when SCR is started without a wiring to an exporter of Config Admin API. This construction allows the
        // class loading exception to be caught and confined.
        final ConfigurationListener serviceDelegator;
        if ( System.getSecurityManager() != null ) {
            serviceDelegator = new ConfigurationListener()
            {
                @Override
                public void configurationEvent(final ConfigurationEvent event)
                {
                    AccessController.doPrivileged(
                        new PrivilegedAction<Object>()
                        {
                            @Override
                            public Void run()
                            {
                                RegionConfigurationSupport.this.configurationEvent(event);
                                return null;
                            }
                        });
                }
            };
        }
        else
        {
            serviceDelegator = new ConfigurationListener()
            {

                @Override
                public void configurationEvent(final ConfigurationEvent event)
                {
                    RegionConfigurationSupport.this.configurationEvent(event);
                }
            };
        }
        this.m_registration = caBundleContext.registerService(ConfigurationListener.class, serviceDelegator, props );
    }

    public Long getBundleId()
    {
        return bundleId;
    }

    public boolean reference()
    {
        if ( referenceCount.get() == 0 )
        {
            return false;
        }
        referenceCount.incrementAndGet();
        return true;
    }

    public boolean dereference()
    {
        if ( referenceCount.decrementAndGet() == 0 )
        {
            try
            {
                this.m_registration.unregister();
            }
            catch (IllegalStateException e)
            {
                // ignore; just trying to clean up
            }
            this.m_registration = null;
            return true;
        }
        return false;
    }

    /**
     * The return value is only relevant for the call from {@link #configurationEvent(ConfigurationEvent)}
     * in the case of a deleted configuration which is not a factory configuration!
     */
    public boolean configureComponentHolder(final ComponentHolder<?> holder)
    {

        // 112.7 configure unless configuration not required
        if ( !holder.getComponentMetadata().isConfigurationIgnored() )
        {
            final BundleContext bundleContext = holder.getActivator().getBundleContext();
            if ( bundleContext == null )
            {
                return false;// bundle was stopped concurrently with configuration deletion
            }
            final List<String> confPids = holder.getComponentMetadata().getConfigurationPid();

            final ConfigurationAdmin ca = getConfigAdmin( bundleContext );
            if (ca == null)
            {
                return false; // bundle was stopped concurrently
            }
            try
            {
                for ( final String confPid : confPids )
                {
                    final Collection<Configuration> factory = findFactoryConfigurations( ca, confPid,
                        bundleContext.getBundle() );
                    if ( !factory.isEmpty() )
                    {
                        boolean created = false;
                        for (Configuration config : factory)
                        {
                            try
                            {
                                logger.log(Level.DEBUG,
                                    "Configuring holder {0} with change count {1}", null,
                                    holder, config.getChangeCount());
                                if (checkBundleLocation(config,
                                    bundleContext.getBundle()))
                                {
                                    long changeCount = config.getChangeCount();
                                    ServiceReference<ManagedService> ref = getManagedServiceReference(
                                        bundleContext);
                                    created |= holder.configurationUpdated(
                                        new TargetedPID(config.getPid()),
                                        new TargetedPID(config.getFactoryPid()),
                                        config.getProcessedProperties(ref), changeCount);
                                }
                            }
                            catch (IllegalStateException e)
                            {
                                continue;
                            }

                        }
                        if ( !created )
                        {
                            return false;
                        }
                    }
                    else
                    {
                        // check for configuration and configure the holder
                        Configuration singleton = findSingletonConfiguration( ca, confPid, bundleContext.getBundle() );
                        if (singleton != null)
                        {
                            try
                            {
                                logger.log(Level.DEBUG,
                                    "Configuring holder {0} with change count {1}", null,
                                    holder, singleton.getChangeCount());
                                if (singleton != null && checkBundleLocation(singleton,
                                    bundleContext.getBundle()))
                                {
                                    long changeCount = singleton.getChangeCount();
                                    ServiceReference<ManagedService> ref = getManagedServiceReference(
                                        bundleContext);
                                    holder.configurationUpdated(
                                        new TargetedPID(singleton.getPid()), null,
                                        singleton.getProcessedProperties(ref),
                                        changeCount);
                                }
                                else
                                {
                                    return false;
                                }
                            }
                            catch (IllegalStateException e)
                            {
                                return false;
                            }
                        }
                        else
                        {
                            return false;
                        }
                    }
                }
                return !confPids.isEmpty();
            }
            finally
            {
                try
                {
                    bundleContext.ungetService( caReference );
                }
                catch ( IllegalStateException e )
                {
                    // ignore, bundle context was shut down during the above.
                }
            }
        }
        return false;
    }

    // ---------- ConfigurationListener

    /**
     * Called by the Configuration Admin service if a configuration is updated
     * or removed.
     * <p>
     * This method is really only called upon configuration changes; it is not
     * called for existing configurations upon startup of the Configuration
     * Admin service. To bridge this gap, the
     * {@link ComponentRegistry#serviceChanged(org.osgi.framework.ServiceEvent)} method called when the
     * Configuration Admin service is registered calls #configureComponentHolders which calls this method for all
     * existing configurations to be able to forward existing configurations to
     * components.
     *
     * @param event The configuration change event
     */
    public void configurationEvent(ConfigurationEvent event)
    {
        final TargetedPID pid = new TargetedPID( event.getPid() );
        String rawFactoryPid = event.getFactoryPid();
        final TargetedPID factoryPid = rawFactoryPid == null? null: new TargetedPID( rawFactoryPid );

        // iterate over all components which must be configured with this pid
        // (since DS 1.2, components may specify a specific configuration PID (112.4.4 configuration-pid)
        Collection<ComponentHolder<?>> holders = getComponentHolders( factoryPid != null? factoryPid: pid );

        logger.log(Level.DEBUG,
            "configurationEvent: Handling {0} of Configuration PID={1} for component holders {2}", null,
            getEventType( event ), pid, holders );

        for ( ComponentHolder<?> componentHolder : holders )
        {
            if ( !componentHolder.getComponentMetadata().isConfigurationIgnored() )
            {
                switch (event.getType())
                {
                    case ConfigurationEvent.CM_DELETED:
                        if ( factoryPid != null || !configureComponentHolder( componentHolder ) )
                        {
                            componentHolder.configurationDeleted( pid, factoryPid );
                        }
                        break;

                    case ConfigurationEvent.CM_UPDATED:
                    {
                        final ComponentActivator activator = componentHolder.getActivator();
                        if ( activator == null )
                        {
                            break;
                        }

                        final BundleContext bundleContext = activator.getBundleContext();
                        if ( bundleContext == null )
                        {
                            break;
                        }

                        TargetedPID targetedPid = factoryPid == null? pid: factoryPid;
                        TargetedPID oldTargetedPID = componentHolder.getConfigurationTargetedPID( pid, factoryPid );
                        if ( factoryPid != null || targetedPid.equals( oldTargetedPID )
                            || targetedPid.bindsStronger( oldTargetedPID ) )
                        {
                            final ConfigurationInfo configInfo = getConfigurationInfo( pid, targetedPid,
                                componentHolder, bundleContext );
                            if ( configInfo != null )
                            {
                                if ( checkBundleLocation( configInfo.getBundleLocation(), bundleContext.getBundle() ) )
                                {
                                    // The below seems to be unnecessary - and if put in, the behaviour is not spec compliant anymore:
                                    // if a component has a required configuration and a modified method, the component must not be
                                    // reactivated
                                    // If this is replacing a weaker targetedPID delete the old one.
                                    // if ( factoryPid == null && !targetedPid.equals(oldTargetedPID) && oldTargetedPID != null)
                                    //{
                                    //componentHolder.configurationDeleted( pid, factoryPid );
                                    //}
                                    componentHolder.configurationUpdated( pid, factoryPid, configInfo.getProps(),
                                        configInfo.getChangeCount() );
                                }
                            }
                        }

                        break;
                    }
                    case ConfigurationEvent.CM_LOCATION_CHANGED:
                    {
                        //TODO is this logic correct for factory pids????
                        final ComponentActivator activator = componentHolder.getActivator();
                        if ( activator == null )
                        {
                            break;
                        }

                        final BundleContext bundleContext = activator.getBundleContext();
                        if ( bundleContext == null )
                        {
                            break;
                        }

                        TargetedPID targetedPid = factoryPid == null? pid: factoryPid;
                        TargetedPID oldTargetedPID = componentHolder.getConfigurationTargetedPID( pid, factoryPid );
                        if ( targetedPid.equals( oldTargetedPID ) )
                        {
                            //this sets the location to this component's bundle if not already set.  OK here
                            //since it used to be set to this bundle, ok to reset it
                            final ConfigurationInfo configInfo = getConfigurationInfo( pid, targetedPid,
                                componentHolder, bundleContext );
                            if ( configInfo != null )
                            {
                                logger.log(Level.DEBUG,
                                    "LocationChanged event, same targetedPID {0}, location now {1}, change count {2}", null,
                                    targetedPid, configInfo.getBundleLocation(),
                                            configInfo.getChangeCount() );
                                if ( configInfo.getProps() == null )
                                {
                                    throw new IllegalStateException( "Existing Configuration with pid " + pid
                                        + " has had its properties set to null and location changed.  We expected a delete event first." );
                                }
                                //this config was used on this component.  Does it still match?
                                if ( !checkBundleLocation( configInfo.getBundleLocation(), bundleContext.getBundle() ) )
                                {
                                    //no, delete it
                                    componentHolder.configurationDeleted( pid, factoryPid );
                                    //maybe there's another match
                                    configureComponentHolder( componentHolder );

                                }
                                //else still matches
                            }
                            break;
                        }
                        boolean better = targetedPid.bindsStronger( oldTargetedPID );
                        if ( better )
                        {
                            //this sets the location to this component's bundle if not already set.  OK here
                            //because if it is set to this bundle we will use it.
                            final ConfigurationInfo configInfo = getConfigurationInfo( pid, targetedPid,
                                componentHolder, bundleContext );
                            if ( configInfo != null )
                            {
                                logger.log(Level.DEBUG,
                                    "LocationChanged event, better targetedPID {0} compared to {1}, location now {2}, change count {3}", null,
                                     targetedPid, oldTargetedPID, configInfo.getBundleLocation(),
                                            configInfo.getChangeCount());
                                if ( configInfo.getProps() == null )
                                {
                                    //location has been changed before any properties are set.  We don't care.  Wait for an updated event with the properties
                                    break;
                                }
                                //this component was not configured with this config.  Should it be now?
                                if ( checkBundleLocation( configInfo.getBundleLocation(), bundleContext.getBundle() ) )
                                {
                                    if ( oldTargetedPID != null )
                                    {
                                        //this is a better match, delete old before setting new
                                        componentHolder.configurationDeleted( pid, factoryPid );
                                    }
                                    componentHolder.configurationUpdated( pid, factoryPid, configInfo.getProps(),
                                        configInfo.getChangeCount() );
                                }
                            }
                        }
                        //else worse match, do nothing
                        else
                        {
                            logger.log(Level.DEBUG,
                                "LocationChanged event, worse targetedPID {0} compared to {1}, do nothing", null,
                                targetedPid, oldTargetedPID  );
                        }
                        break;
                    }
                    default:
                        logger.log(Level.WARN,
                            "Unknown ConfigurationEvent type {0}", null,
                            event.getType() );
                }
            }
        }
    }

    protected abstract Collection<ComponentHolder<?>> getComponentHolders(TargetedPID pid);

    private String getEventType(ConfigurationEvent event)
    {
        switch (event.getType())
        {
            case ConfigurationEvent.CM_UPDATED:
                return "UPDATED";
            case ConfigurationEvent.CM_DELETED:
                return "DELETED";
            case ConfigurationEvent.CM_LOCATION_CHANGED:
                return "LOCATION CHANGED";
            default:
                return "Unkown event type: " + event.getType();
        }

    }

    private static class ConfigurationInfo
    {
        private final Dictionary<String, Object> props;
        private final String bundleLocation;
        private final long changeCount;

        public ConfigurationInfo(Dictionary<String, Object> props, String bundleLocation, long changeCount)
        {
            this.props = props;
            this.bundleLocation = bundleLocation;
            this.changeCount = changeCount;
        }

        public long getChangeCount()
        {
            return changeCount;
        }

        public Dictionary<String, Object> getProps()
        {
            return props;
        }

        public String getBundleLocation()
        {
            return bundleLocation;
        }

    }

    /**
     * This gets config admin, gets the requested configuration, extracts the info we need from it, and ungets config admin.
     * Some versions of felix config admin do not allow access to configurations after the config admin instance they were obtained from
     * are ungot.  Extracting the info we need into "configInfo" solves this problem.
     *
     * @param pid TargetedPID for the desired configuration
     * @param targetedPid the targeted factory pid for a factory configuration or the pid for a singleton configuration
     * @param componentHolder ComponentHolder that holds the old change count (for r4 fake change counting)
     * @param bundleContext BundleContext to get the CA from
     * @return ConfigurationInfo object containing the info we need from the configuration.
     */
    private ConfigurationInfo getConfigurationInfo(final TargetedPID pid, TargetedPID targetedPid,
        ComponentHolder<?> componentHolder, final BundleContext bundleContext)
    {
        try
        {
            final ConfigurationAdmin ca = getConfigAdmin( bundleContext );
            if (ca == null)
            {
                return null;
            }
            try
            {
                Configuration[] configs = ca.listConfigurations( filter( pid.getRawPid() ) );
                if ( configs != null && configs.length > 0 )
                {
                    for (Configuration config : configs)
                    {
                        try
                        {
                            ServiceReference<ManagedService> ref = getManagedServiceReference(
                                bundleContext);
                            return new ConfigurationInfo(
                                config.getProcessedProperties(ref),
                                config.getBundleLocation(), config.getChangeCount());
                        }
                        catch (IllegalStateException e)
                        {
                            continue;
                        }
                    }
                }
            }
            catch ( IOException e )
            {
                logger.log(Level.WARN, "Failed reading configuration for pid={0}",
                    e, pid);
            }
            catch ( InvalidSyntaxException e )
            {
                logger.log(Level.WARN, "Failed reading configuration for pid={0}",
                    e, pid);
            }
            finally
            {
                bundleContext.ungetService( caReference );
            }
        }
        catch ( IllegalStateException ise )
        {
            // If the bundle has been stopped concurrently
            logger.log(Level.DEBUG, "Bundle in unexpected state", ise);
        }
        return null;
    }

    private ServiceReference<ManagedService> getManagedServiceReference(BundleContext bundleContext)
    {
        try {
            Collection<ServiceReference<ManagedService>> refs = bundleContext.getServiceReferences(ManagedService.class,
                    "(&(service.bundleid=" + String.valueOf(bundleContext.getBundle().getBundleId()) + ")(!(service.pid=*)))");
            if ( !refs.isEmpty() ) {
                return refs.iterator().next();
            }
        } catch (InvalidSyntaxException e) {
            // this should never happen,
        }
        return bundleContext.registerService(ManagedService.class, new ManagedService() {

            @Override
            public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
                // nothing to do

            }
        }, null).getReference();
    }

    private String filter(String rawPid)
    {
        return "(service.pid=" + rawPid + ")";
    }

    /**
     * Returns the configuration whose PID equals the given pid. If no such
     * configuration exists, <code>null</code> is returned.
     *
     * @param ca Configuration Admin service
     * @param pid Pid for desired configuration
     * @param bundle bundle of the component we are configuring (used in targeted pids)
     * @return configuration with the specified Pid
     */
    public Configuration findSingletonConfiguration(final ConfigurationAdmin ca, final String pid, Bundle bundle)
    {
        final String filter = getTargetedPidFilter( pid, bundle, Constants.SERVICE_PID );
        final Configuration[] cfg = findConfigurations( ca, filter );
        if ( cfg == null )
        {
            return null;
        }
        String longest = null;
        Configuration best = null;
        for ( Configuration config : cfg )
        {
            if (checkBundleLocation(config, bundle))
            {
                try
                {
                    String testPid = config.getPid();
                    if (longest == null || testPid.length() > longest.length())
                    {
                        longest = testPid;
                        best = config;
                    }
                }
                catch (IllegalStateException e)
                {
                    continue;
                }
            }

        }
        return best;
    }

    /**
     * Returns all configurations whose factory PID equals the given factory PID
     * or <code>null</code> if no such configurations exist
     *
     * @param ca ConfigurationAdmin service
     * @param factoryPid factory Pid we want the configurations for
     * @param bundle bundle we're working for (for location and location permission)
     * @return the configurations specifying the supplied factory Pid.
     */
    private Collection<Configuration> findFactoryConfigurations(final ConfigurationAdmin ca, final String factoryPid,
        Bundle bundle)
    {
        final String filter = getTargetedPidFilter( factoryPid, bundle, ConfigurationAdmin.SERVICE_FACTORYPID );
        Configuration[] configs = findConfigurations( ca, filter );
        if ( configs == null )
        {
            return Collections.emptyList();
        }
        Map<String, Configuration> configsByPid = new HashMap<>();
        for ( Configuration config : configs )
        {
            if (checkBundleLocation(config, bundle))
            {
                try
                {
                    Configuration oldConfig = configsByPid.get(config.getPid());
                    if (oldConfig == null)
                    {
                        configsByPid.put(config.getPid(), config);
                    }
                    else
                    {
                        String newPid = config.getFactoryPid();
                        String oldPid = oldConfig.getFactoryPid();
                        if (newPid.length() > oldPid.length())
                        {
                            configsByPid.put(config.getPid(), config);
                        }
                    }
                }
                catch (IllegalStateException e)
                {
                    continue;
                }

            }
        }
        return configsByPid.values();
    }

    private boolean checkBundleLocation(Configuration config, Bundle bundle)
    {
        if ( config == null )
        {
            return false;
        }

        String configBundleLocation = null;
        try
        {
            configBundleLocation = config.getBundleLocation();
        }
        catch (IllegalStateException e)
        {
            return false;
        }

        return checkBundleLocation( configBundleLocation, bundle );
    }

    private boolean checkBundleLocation(String configBundleLocation, Bundle bundle)
    {
        boolean result;
        if ( configBundleLocation == null )
        {
            result = true;
        }
        else if ( configBundleLocation.startsWith( "?" ) )
        {
            //multilocation
            if ( System.getSecurityManager() != null )
            {
                result = bundle.hasPermission(
                        new ConfigurationPermission(configBundleLocation, ConfigurationPermission.TARGET));
            }
            else
            {
                result = true;
            }
        }
        else
        {
            result = configBundleLocation.equals( bundle.getLocation() );
        }
        logger.log(Level.DEBUG, "checkBundleLocation: location {0}, returning {1}",
            null,
            configBundleLocation, result );
        return result;
    }

    private Configuration[] findConfigurations(final ConfigurationAdmin ca, final String filter)
    {
        try
        {
            return ca.listConfigurations( filter );
        }
        catch ( IOException ioe )
        {
            logger.log(Level.WARN, "Problem listing configurations for filter={0}",
                ioe,
                filter  );
        }
        catch ( InvalidSyntaxException ise )
        {
            logger.log(Level.ERROR, "Invalid Configuration selection filter {0}", ise,
                filter);
        }

        // no factories in case of problems
        return null;
    }

    private String getTargetedPidFilter(String pid, Bundle bundle, String key)
    {
        String bsn = bundle.getSymbolicName();
        String version = bundle.getVersion().toString();
        String location = escape( bundle.getLocation() );

        StringBuilder sb = new StringBuilder();

        sb.append("(|(");

        sb.append(key);
        sb.append('=');
        sb.append(pid);

        sb.append(")(");

        sb.append(key);
        sb.append('=');
        sb.append(pid);
        sb.append('|');
        sb.append(bsn);

        sb.append(")(");

        sb.append(key);
        sb.append('=');
        sb.append(pid);
        sb.append('|');
        sb.append(bsn);
        sb.append('|');
        sb.append(version);

        sb.append(")(");

        sb.append(key);
        sb.append('=');
        sb.append(pid);
        sb.append('|');
        sb.append(bsn);
        sb.append('|');
        sb.append(version);
        sb.append('|');
        sb.append(location);

        sb.append("))");

        return sb.toString();
    }

    /**
     * see core spec 3.2.7.  Escape \*() with preceding \
     * @param value
     * @return escaped string
     */
    static final String escape(String value)
    {
        StringBuilder sb = null;

        int index = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '\\':
                case '*':
                case '(':
                case ')':
                    if (sb == null) {
                        sb = new StringBuilder();
                    }

                    sb.append(value, index, i);
                    sb.append('\\');
                    sb.append(c);

                    index = i + 1;

                    break;
            }
        }

        if (sb == null) {
            return value;
        }

        if (index < value.length()) {
            sb.append(value, index, value.length());
        }

        return sb.toString();
    }

    private ConfigurationAdmin getConfigAdmin(BundleContext bundleContext)
    {
        try
        {
            return bundleContext.getService(caReference);
        }
        catch (IllegalStateException e)
        {
            return null;
        }
    }
}

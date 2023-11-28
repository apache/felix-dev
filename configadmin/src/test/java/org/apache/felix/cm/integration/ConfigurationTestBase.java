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
package org.apache.felix.cm.integration;


import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.cleanCaches;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.CoreOptions.workingDirectory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.inject.Inject;

import org.apache.felix.cm.integration.helper.BaseTestActivator;
import org.apache.felix.cm.integration.helper.ManagedServiceTestActivator;
import org.apache.felix.cm.integration.helper.UpdateThreadSignalTask;
import org.junit.After;
import org.junit.Before;
import org.ops4j.pax.exam.ExamFactory;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.OptionUtils;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.forked.ForkedTestContainer;
import org.ops4j.pax.exam.forked.ForkedTestContainerFactory;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;


/**
 * The common integration test support class
 * 
 * The default is always to use the {@link NativeTestContainer} as it is much
 * faster. Tests that need more isolation should use the {@link ForkedTestContainer}. 
 */
@ExamFactory(ForkedTestContainerFactory.class)
public abstract class ConfigurationTestBase
{

    // the name of the system property providing the bundle file to be installed and tested
    protected static final String BUNDLE_JAR_SYS_PROP = "project.bundle.file";

    // the default bundle jar file name
    protected static final String BUNDLE_JAR_DEFAULT = "target/configadmin.jar";

    // the JVM option to set to enable remote debugging
    protected static final String DEBUG_VM_OPTION = "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=30303";

    // the actual JVM option set, extensions may implement a static
    // initializer overwriting this value to have the configuration()
    // method include it when starting the OSGi framework JVM
    protected static String paxRunnerVmOption = null;

    @Inject
    protected BundleContext bundleContext;

    protected Bundle bundle;

    protected ServiceTracker<ConfigurationAdmin, ConfigurationAdmin> configAdminTracker;

    private Set<String> configurations = new HashSet<>();

    protected static final String PROP_NAME = "theValue";
    protected static final Dictionary<String, String> theConfig;

    static
    {
        theConfig = new Hashtable<>();
        theConfig.put( PROP_NAME, PROP_NAME );
    }


    @org.ops4j.pax.exam.Configuration
    public Option[] configuration()
    {
        final String bundleFileName = System.getProperty( BUNDLE_JAR_SYS_PROP, BUNDLE_JAR_DEFAULT );
        final File bundleFile = new File( bundleFileName );
        if ( !bundleFile.canRead() )
        {
            throw new IllegalArgumentException( "Cannot read from bundle file " + bundleFileName + " specified in the "
                + BUNDLE_JAR_SYS_PROP + " system property" );
        }

        final Option[] base = options(
                workingDirectory("target/paxexam/"),
                cleanCaches(true),
                junitBundles(),
                mavenBundle("org.ops4j.pax.tinybundles", "tinybundles", "1.0.0"),
                bundle(bundleFile.toURI().toString())
        );
        final Option option = ( paxRunnerVmOption != null ) ? vmOption( paxRunnerVmOption ) : null;
        return OptionUtils.combine(OptionUtils.combine( base, option ), additionalConfiguration());
    }
    
    protected Option[] additionalConfiguration() {
    	return null;
    }


    @Before
    public void setUp()
    {
        configAdminTracker = new ServiceTracker<>( bundleContext, ConfigurationAdmin.class, null );
        configAdminTracker.open();
    }


    @After
    public void tearDown() throws BundleException
    {
        if ( bundle != null )
        {
            bundle.uninstall();
        }

        for ( String pid : configurations )
        {
            deleteConfig( pid );
        }

        configAdminTracker.close();
        configAdminTracker = null;
    }


    protected Bundle installBundle( final String pid ) throws BundleException
    {
        return installBundle( pid, ManagedServiceTestActivator.class );
    }


    protected Bundle installBundle( final String pid, final Class<?> activatorClass ) throws BundleException
    {
        return installBundle( pid, activatorClass, activatorClass.getName() );
    }


    @ProbeBuilder
    public TestProbeBuilder buildProbe( TestProbeBuilder builder ) {
        return builder.setHeader(Constants.EXPORT_PACKAGE, "org.apache.felix.cm.integration.helper");
    }

    protected Bundle installBundle( final String pid, final Class<?> activatorClass, final String location )
        throws BundleException
    {
        final String activatorClassName = activatorClass.getName();
        final InputStream bundleStream = TinyBundles.bundle()
                .set(Constants.BUNDLE_SYMBOLICNAME, activatorClassName)
                .set( Constants.BUNDLE_VERSION, "0.0.11" )
                .set( Constants.IMPORT_PACKAGE, "org.apache.felix.cm.integration.helper" )
                .set( Constants.BUNDLE_ACTIVATOR, activatorClassName )
                .set( BaseTestActivator.HEADER_PID, pid )
                .build( TinyBundles.withBnd() );

        try
        {
            return bundleContext.installBundle( location, bundleStream );
        }
        finally
        {
            try
            {
                bundleStream.close();
            }
            catch ( IOException ioe )
            {
            }
        }
    }


    protected void delay()
    {
        Object ca = configAdminTracker.getService();
        if ( ca != null )
        {
            try
            {

                Field caf = ca.getClass().getDeclaredField( "configurationManager" );
                caf.setAccessible( true );
                Object cm = caf.get( ca );

                Field cmf = cm.getClass().getDeclaredField( "updateThread" );
                cmf.setAccessible( true );
                Object ut = cmf.get( cm );

                Method utm = ut.getClass().getDeclaredMethod( "schedule" );
                utm.setAccessible( true );

                UpdateThreadSignalTask signalTask = new UpdateThreadSignalTask();
                utm.invoke( ut, signalTask );
                signalTask.waitSignal();

                return;
            }
            catch ( AssertionFailedError afe )
            {
                throw afe;
            }
            catch ( Throwable t )
            {
                // ignore any problem and revert to timed delay (might log this)
            }
        }

        // no configadmin or failure while setting up task
        try
        {
            Thread.sleep( 300 );
        }
        catch ( InterruptedException ie )
        {
            // dont care
        }
    }


    protected Bundle getCmBundle()
    {
        final ServiceReference<ConfigurationAdmin> caref = configAdminTracker.getServiceReference();
        return ( caref == null ) ? null : caref.getBundle();
    }


    protected ConfigurationAdmin getConfigurationAdmin()
    {
        ConfigurationAdmin ca = null;
        try {
            ca = configAdminTracker.waitForService(5000L);
        } catch (InterruptedException e) {
            // ignore
        }
        if ( ca == null )
        {
            TestCase.fail( "Missing ConfigurationAdmin service" );
        }
        return ca;
    }


    protected Configuration configure( final String pid )
    {
        return configure( pid, null, true );
    }


    protected Configuration configure( final String pid, final String location, final boolean withProps )
    {
        final ConfigurationAdmin ca = getConfigurationAdmin();
        try
        {
            final Configuration config = ca.getConfiguration( pid, location );
            if ( withProps )
            {
                config.update( theConfig );
            }
            return config;
        }
        catch ( IOException ioe )
        {
            TestCase.fail( "Failed updating configuration " + pid + ": " + ioe.toString() );
            return null; // keep the compiler quiet
        }
    }


    protected Configuration createFactoryConfiguration( final String factoryPid )
    {
        return createFactoryConfiguration( factoryPid, null, true );
    }


    protected Configuration createFactoryConfiguration( final String factoryPid, final String location,
        final boolean withProps )
    {
        final ConfigurationAdmin ca = getConfigurationAdmin();
        try
        {
            final Configuration config = ca.createFactoryConfiguration( factoryPid, location );
            if ( withProps )
            {
                config.update( theConfig );
            }
            return config;
        }
        catch ( IOException ioe )
        {
            TestCase.fail( "Failed updating factory configuration " + factoryPid + ": " + ioe.toString() );
            return null; // keep the compiler quiet
        }
    }


    protected Configuration getConfiguration( final String pid )
    {
        final ConfigurationAdmin ca = getConfigurationAdmin();
        try
        {
            final String filter = "(" + Constants.SERVICE_PID + "=" + pid + ")";
            final Configuration[] configs = ca.listConfigurations( filter );
            if ( configs != null && configs.length > 0 )
            {
                return configs[0];
            }
        }
        catch ( InvalidSyntaxException ise )
        {
            // unexpected
        }
        catch ( IOException ioe )
        {
            TestCase.fail( "Failed listing configurations " + pid + ": " + ioe.toString() );
        }

        TestCase.fail( "No Configuration " + pid + " found" );
        return null;
    }


    protected void deleteConfig( final String pid )
    {
        final ConfigurationAdmin ca = getConfigurationAdmin();
        try
        {
            configurations.remove( pid );
            final Configuration config = ca.getConfiguration( pid );
            config.delete();
        }
        catch ( IOException ioe )
        {
            TestCase.fail( "Failed deleting configuration " + pid + ": " + ioe.toString() );
        }
    }


    protected void deleteFactoryConfigurations( String factoryPid )
    {
        ConfigurationAdmin ca = getConfigurationAdmin();
        try
        {
            final String filter = "(service.factoryPid=" + factoryPid + ")";
            Configuration[] configs = ca.listConfigurations( filter );
            if ( configs != null )
            {
                for ( Configuration configuration : configs )
                {
                    configurations.remove( configuration.getPid() );
                    configuration.delete();
                }
            }
        }
        catch ( InvalidSyntaxException ise )
        {
            // unexpected
        }
        catch ( IOException ioe )
        {
            TestCase.fail( "Failed deleting configurations " + factoryPid + ": " + ioe.toString() );
        }
    }
}

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


import org.apache.felix.cm.integration.helper.ManagedServiceFactoryTestActivator;
import org.apache.felix.cm.integration.helper.ManagedServiceFactoryTestActivator2;
import org.apache.felix.cm.integration.helper.MultiManagedServiceFactoryTestActivator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.service.cm.Configuration;

import junit.framework.TestCase;


/**
 * The <code>MultiServicePIDTest</code> tests the case of multiple services
 * bound with the same PID
 */
@RunWith(PaxExam.class)
public class MultiServiceFactoryPIDTest extends ConfigurationTestBase
{
    static
    {
        // uncomment to enable debugging of this test class
        // paxRunnerVmOption = DEBUG_VM_OPTION;
    }

    @Test
    public void test_two_services_same_pid_in_same_bundle_configure_before_registration() throws BundleException
    {
        final String factoryPid = "test.pid";

        final Configuration config = createFactoryConfiguration( factoryPid );
        final String pid = config.getPid();
        TestCase.assertEquals( factoryPid, config.getFactoryPid() );
        TestCase.assertNull( config.getBundleLocation() );

        bundle = installBundle( factoryPid, MultiManagedServiceFactoryTestActivator.class );
        bundle.start();

        // give cm time for distribution
        delay();

        final MultiManagedServiceFactoryTestActivator tester = MultiManagedServiceFactoryTestActivator.INSTANCE;
        TestCase.assertNotNull( "Activator not started !!", tester );

        // assert activater has configuration (two calls, one per pid)
        TestCase.assertNotNull( "Expect Properties after Service Registration", tester.configs.get( pid ) );
        TestCase.assertEquals( "Expect two update calls", 2, tester.numManagedServiceFactoryUpdatedCalls );

        TestCase.assertEquals( bundle.getLocation(), config.getBundleLocation() );

        bundle.uninstall();
        bundle = null;

        delay();

        TestCase.assertNull( config.getBundleLocation() );

        // remove the configuration for good
        deleteConfig( pid );
    }


    @Test
    public void test_two_services_same_pid_in_same_bundle_configure_after_registration() throws BundleException
    {
        final String factoryPid = "test.pid";

        bundle = installBundle( factoryPid, MultiManagedServiceFactoryTestActivator.class );
        bundle.start();

        // give cm time for distribution
        delay();

        final MultiManagedServiceFactoryTestActivator tester = MultiManagedServiceFactoryTestActivator.INSTANCE;
        TestCase.assertNotNull( "Activator not started !!", tester );

        // no configuration yet
        TestCase.assertTrue( "Expect Properties after Service Registration", tester.configs.isEmpty() );
        TestCase.assertEquals( "Expect two update calls", 0, tester.numManagedServiceFactoryUpdatedCalls );

        final Configuration config = createFactoryConfiguration( factoryPid );
        final String pid = config.getPid();

        delay();

        TestCase.assertEquals( factoryPid, config.getFactoryPid() );
        TestCase.assertEquals( bundle.getLocation(), config.getBundleLocation() );

        // assert activater has configuration (two calls, one per pid)
        TestCase.assertNotNull( "Expect Properties after Service Registration", tester.configs.get( pid ) );
        TestCase.assertEquals( "Expect another two single update call", 2, tester.numManagedServiceFactoryUpdatedCalls );

        bundle.uninstall();
        bundle = null;

        delay();

        TestCase.assertNull( config.getBundleLocation() );

        // remove the configuration for good
        deleteConfig( pid );
    }


    @Test
    public void test_two_services_same_pid_in_two_bundle_configure_before_registration() throws BundleException
    {
        Bundle bundle2 = null;
        try
        {
            final String factoryPid = "test.pid";
            final Configuration config = createFactoryConfiguration( factoryPid );
            final String pid = config.getPid();

            TestCase.assertEquals( factoryPid, config.getFactoryPid() );
            TestCase.assertNull( config.getBundleLocation() );

            bundle = installBundle( factoryPid, ManagedServiceFactoryTestActivator.class );
            bundle.start();

            bundle2 = installBundle( factoryPid, ManagedServiceFactoryTestActivator2.class );
            bundle2.start();

            // give cm time for distribution
            delay();

            final ManagedServiceFactoryTestActivator tester = ManagedServiceFactoryTestActivator.INSTANCE;
            TestCase.assertNotNull( "Activator not started !!", tester );

            final ManagedServiceFactoryTestActivator2 tester2 = ManagedServiceFactoryTestActivator2.INSTANCE;
            TestCase.assertNotNull( "Activator 2 not started !!", tester2 );

            // expect first activator to have received properties

            // assert first bundle has configuration (two calls, one per srv)
            TestCase.assertNotNull( "Expect Properties after Service Registration", tester.configs.get( pid ) );
            TestCase.assertEquals( "Expect a single update call", 1, tester.numManagedServiceFactoryUpdatedCalls );

            // assert second bundle has no configuration
            TestCase.assertTrue( tester2.configs.isEmpty() );
            TestCase.assertEquals( 0, tester2.numManagedServiceFactoryUpdatedCalls );

            // expect configuration bound to first bundle
            TestCase.assertEquals( bundle.getLocation(), config.getBundleLocation() );

            bundle.uninstall();
            bundle = null;

            delay();

            // expect configuration reassigned
            TestCase.assertEquals( bundle2.getLocation(), config.getBundleLocation() );
            TestCase.assertNotNull( "Expect Properties after Configuration Redispatch", tester2.configs.get( pid ) );
            TestCase.assertEquals( "Expect update call after Configuration Redispatch", 1, tester2.numManagedServiceFactoryUpdatedCalls );

            // remove the configuration for good
            deleteConfig( pid );
        }
        finally
        {
            if ( bundle2 != null )
            {
                bundle2.uninstall();
            }
        }
    }


    @Test
    public void test_two_services_same_pid_in_two_bundle_configure_after_registration() throws BundleException
    {
        Bundle bundle2 = null;
        try
        {
            final String factoryPid = "test.pid";

            bundle = installBundle( factoryPid, ManagedServiceFactoryTestActivator.class );
            bundle.start();

            bundle2 = installBundle( factoryPid, ManagedServiceFactoryTestActivator2.class );
            bundle2.start();

            final ManagedServiceFactoryTestActivator tester = ManagedServiceFactoryTestActivator.INSTANCE;
            TestCase.assertNotNull( "Activator not started !!", tester );

            final ManagedServiceFactoryTestActivator2 tester2 = ManagedServiceFactoryTestActivator2.INSTANCE;
            TestCase.assertNotNull( "Activator not started !!", tester2 );

            delay();

            // expect no configuration but a call in each service
            TestCase.assertTrue( "Expect Properties after Service Registration", tester.configs.isEmpty() );
            TestCase.assertEquals( "Expect a single update call", 0, tester.numManagedServiceFactoryUpdatedCalls );
            TestCase.assertTrue( "Expect Properties after Service Registration", tester2.configs.isEmpty() );
            TestCase.assertEquals( "Expect a single update call", 0, tester2.numManagedServiceFactoryUpdatedCalls );

            final Configuration config = createFactoryConfiguration( factoryPid );
            final String pid = config.getPid();

            delay();

            TestCase.assertEquals( factoryPid, config.getFactoryPid() );

            TestCase.assertEquals(
                "Configuration must be bound to second bundle because the service has higher ranking",
                bundle.getLocation(), config.getBundleLocation() );

            // configuration assigned to the first bundle
            TestCase.assertNotNull( "Expect Properties after Service Registration", tester.configs.get( pid ) );
            TestCase.assertEquals( "Expect a single update call", 1, tester.numManagedServiceFactoryUpdatedCalls );

            TestCase.assertTrue( "Expect Properties after Service Registration", tester2.configs.isEmpty() );
            TestCase.assertEquals( "Expect a single update call", 0, tester2.numManagedServiceFactoryUpdatedCalls );

            bundle.uninstall();
            bundle = null;

            delay();

            // expect configuration reassigned
            TestCase.assertEquals( bundle2.getLocation(), config.getBundleLocation() );
            TestCase.assertNotNull( "Expect Properties after Configuration Redispatch", tester2.configs.get( pid ) );
            TestCase.assertEquals( "Expect a single update call after Configuration Redispatch", 1, tester2.numManagedServiceFactoryUpdatedCalls );

            // remove the configuration for good
            deleteConfig( pid );
        }
        finally
        {
            if ( bundle2 != null )
            {
                bundle2.uninstall();
            }
        }
    }

}

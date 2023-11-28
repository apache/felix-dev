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
package org.apache.felix.scr.integration;


import java.util.Hashtable;

import org.apache.felix.scr.integration.components.SimpleComponent;
import org.apache.felix.scr.integration.components.SimpleComponent2;
import org.apache.felix.scr.integration.components.SimpleService2Impl;
import org.apache.felix.scr.integration.components.SimpleServiceImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;

import junit.framework.TestCase;


@RunWith(PaxExam.class)
public class ServiceBindTest extends ComponentTestBase
{

    private static final String PROP_NAME_FACTORY = ComponentTestBase.PROP_NAME + ".factory";

    static
    {
        // uncomment to enable debugging of this test class
        // paxRunnerVmOption = DEBUG_VM_OPTION;

        descriptorFile = "/integration_test_simple_components_service_binding.xml";
    }


    @Test
    public void test_optional_single_dynamic() throws Exception
    {
        final SimpleServiceImpl srv1 = SimpleServiceImpl.create( bundleContext, "srv1" );

        String name ="test_optional_single_dynamic";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp10 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp10 );
        TestCase.assertEquals( srv1, comp10.m_singleRef );
        TestCase.assertTrue( comp10.m_multiRef.isEmpty() );

        srv1.drop();
        // no delay, should be immediate

        final SimpleComponent comp11 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp10, comp11 );
        TestCase.assertNull( comp11.m_singleRef );
        TestCase.assertTrue( comp11.m_multiRef.isEmpty() );

        final SimpleServiceImpl srv2 = SimpleServiceImpl.create( bundleContext, "srv2" );
        delay(); // async binding

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp12 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp10, comp12 );
        TestCase.assertEquals( srv2, comp12.m_singleRef );
        TestCase.assertTrue( comp12.m_multiRef.isEmpty() );

        disableAndCheck(cc);

        final SimpleServiceImpl srv3 = SimpleServiceImpl.create( bundleContext, "srv3" );

        // enable component with two services available, expect srv2 bind
        // async enabling
        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp20 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp20 );
        TestCase.assertNotSame( comp10, comp20 );
        TestCase.assertEquals( srv2, comp20.m_singleRef );
        TestCase.assertTrue( comp20.m_multiRef.isEmpty() );

        // drop srv2, expect rebind to srv3 (synchronously)
        srv2.drop();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp21 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp21 );
        TestCase.assertEquals( srv3, comp21.m_singleRef );
        TestCase.assertTrue( comp21.m_multiRef.isEmpty() );

        // create srv4, expect no rebind
        final SimpleServiceImpl srv4 = SimpleServiceImpl.create( bundleContext, "srv4" );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp22 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp22 );
        TestCase.assertEquals( srv3, comp22.m_singleRef );
        TestCase.assertTrue( comp22.m_multiRef.isEmpty() );

        // drop srv4 again, expect no rebind
        srv4.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp23 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp23 );
        TestCase.assertEquals( srv3, comp23.m_singleRef );
        TestCase.assertTrue( comp23.m_multiRef.isEmpty() );

        // "reset"
        disableAndCheck(cc);
        srv3.drop();
        delay();

        // two services with service ranking (srv6 > srv5)
        @SuppressWarnings("unused")
        final SimpleServiceImpl srv5 = SimpleServiceImpl.create( bundleContext, "srv5", 10 );
        final SimpleServiceImpl srv6 = SimpleServiceImpl.create( bundleContext, "srv6", 20 );

        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp30 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp30 );
        TestCase.assertEquals( srv6, comp30.m_singleRef );
        TestCase.assertTrue( comp30.m_multiRef.isEmpty() );

        // another service with higher ranking -- no rebind !
        final SimpleServiceImpl srv7 = SimpleServiceImpl.create( bundleContext, "srv7", 30 );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp31 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp31 );
        TestCase.assertEquals( srv6, comp31.m_singleRef );
        TestCase.assertTrue( comp31.m_multiRef.isEmpty() );

        // srv6 goes, rebind to srv7
        srv6.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp32 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp32 );
        TestCase.assertEquals( srv7, comp32.m_singleRef );
        TestCase.assertTrue( comp32.m_multiRef.isEmpty() );
    }


    @Test
    public void test_required_single_dynamic() throws Exception
    {
        final SimpleServiceImpl srv1 = SimpleServiceImpl.create( bundleContext, "srv1" );

        String name ="test_required_single_dynamic";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp10 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp10 );
        TestCase.assertEquals( srv1, comp10.m_singleRef );
        TestCase.assertTrue( comp10.m_multiRef.isEmpty() );

        srv1.drop();
        // no delay, should be immediate

        findComponentConfigurationByName(name, ComponentConfigurationDTO.UNSATISFIED_REFERENCE);
        final SimpleComponent comp11 = SimpleComponent.INSTANCE;
        TestCase.assertNull( comp11 );

        final SimpleServiceImpl srv2 = SimpleServiceImpl.create( bundleContext, "srv2" );
        delay(); // async binding

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp12 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp10, comp12 );
        TestCase.assertEquals( srv2, comp12.m_singleRef );
        TestCase.assertTrue( comp12.m_multiRef.isEmpty() );

        disableAndCheck(cc);
        delay(); // async disabling

        final SimpleServiceImpl srv3 = SimpleServiceImpl.create( bundleContext, "srv3" );

        // enable component with two services available, expect srv2 bind
        // async enabling
        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp20 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp20 );
        TestCase.assertNotSame( comp10, comp20 );
        TestCase.assertEquals( srv2, comp20.m_singleRef );
        TestCase.assertTrue( comp20.m_multiRef.isEmpty() );

        // drop srv2, expect rebind to srv3 (synchronously)
        srv2.drop();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp21 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp21 );
        TestCase.assertEquals( srv3, comp21.m_singleRef );
        TestCase.assertTrue( comp21.m_multiRef.isEmpty() );

        // create srv4, expect no rebind
        final SimpleServiceImpl srv4 = SimpleServiceImpl.create( bundleContext, "srv4" );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp22 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp22 );
        TestCase.assertEquals( srv3, comp22.m_singleRef );
        TestCase.assertTrue( comp22.m_multiRef.isEmpty() );

        // drop srv4 again, expect no rebind
        srv4.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp23 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp23 );
        TestCase.assertEquals( srv3, comp23.m_singleRef );
        TestCase.assertTrue( comp23.m_multiRef.isEmpty() );

        // "reset"
        disableAndCheck(cc);
        srv3.drop();
        delay();

        // two services with service ranking (srv6 > srv5)
        @SuppressWarnings("unused")
        final SimpleServiceImpl srv5 = SimpleServiceImpl.create( bundleContext, "srv5", 10 );
        final SimpleServiceImpl srv6 = SimpleServiceImpl.create( bundleContext, "srv6", 20 );

        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp30 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp30 );
        TestCase.assertEquals( srv6, comp30.m_singleRef );
        TestCase.assertTrue( comp30.m_multiRef.isEmpty() );

        // another service with higher ranking -- no rebind !
        final SimpleServiceImpl srv7 = SimpleServiceImpl.create( bundleContext, "srv7", 30 );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp31 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp31 );
        TestCase.assertEquals( srv6, comp31.m_singleRef );
        TestCase.assertTrue( comp31.m_multiRef.isEmpty() );

        // srv6 goes, rebind to srv7
        srv6.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp32 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp32 );
        TestCase.assertEquals( srv7, comp32.m_singleRef );
        TestCase.assertTrue( comp32.m_multiRef.isEmpty() );
    }


    @Test
    public void test_optional_multiple_dynamic() throws Exception
    {
        final SimpleServiceImpl srv1 = SimpleServiceImpl.create( bundleContext, "srv1" );

        String name ="test_optional_multiple_dynamic";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp10 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp10 );
        TestCase.assertNull( comp10.m_singleRef );
        TestCase.assertTrue( comp10.m_multiRef.contains( srv1 ) );

        srv1.drop();
        // no delay, should be immediate

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp11 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp10, comp11 );
        TestCase.assertNull( comp11.m_singleRef );
        TestCase.assertTrue( comp11.m_multiRef.isEmpty() );

        final SimpleServiceImpl srv2 = SimpleServiceImpl.create( bundleContext, "srv2" );
        delay(); // async binding

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp12 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp10, comp12 );
        TestCase.assertNull( comp12.m_singleRef );
        TestCase.assertTrue( comp12.m_multiRef.contains( srv2 ) );

        disableAndCheck(cc);
        delay(); // async disabling

        final SimpleServiceImpl srv3 = SimpleServiceImpl.create( bundleContext, "srv3" );

        // enable component with two services available, expect both bind
        // async enabling
        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp20 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp20 );
        TestCase.assertNotSame( comp10, comp20 );
        TestCase.assertNull( comp20.m_singleRef );
        TestCase.assertTrue( comp20.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp20.m_multiRef.contains( srv3 ) );

        srv2.drop();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp21 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp21 );
        TestCase.assertNull( comp21.m_singleRef );
        TestCase.assertFalse( comp21.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp21.m_multiRef.contains( srv3 ) );

        // create srv4, expect bind
        final SimpleServiceImpl srv4 = SimpleServiceImpl.create( bundleContext, "srv4" );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp22 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp22 );
        TestCase.assertNull( comp22.m_singleRef );
        TestCase.assertFalse( comp22.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp22.m_multiRef.contains( srv3 ) );
        TestCase.assertTrue( comp22.m_multiRef.contains( srv4 ) );

        // drop srv4 again, expect no rebind
        srv4.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp23 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp23 );
        TestCase.assertNull( comp23.m_singleRef );
        TestCase.assertFalse( comp23.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp23.m_multiRef.contains( srv3 ) );
        TestCase.assertFalse( comp23.m_multiRef.contains( srv4 ) );

        // "reset"
        disableAndCheck(cc);
        srv3.drop();
        delay();

        // two services with service ranking (srv6 > srv5)
        final SimpleServiceImpl srv5 = SimpleServiceImpl.create( bundleContext, "srv5", 10 );
        final SimpleServiceImpl srv6 = SimpleServiceImpl.create( bundleContext, "srv6", 20 );

        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);
        final SimpleComponent comp30 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp30 );
        TestCase.assertNull( comp30.m_singleRef );
        TestCase.assertTrue( comp30.m_multiRef.contains( srv5 ) );
        TestCase.assertTrue( comp30.m_multiRef.contains( srv6 ) );

        // another service with higher ranking -- no rebind !
        final SimpleServiceImpl srv7 = SimpleServiceImpl.create( bundleContext, "srv7", 30 );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp31 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp31 );
        TestCase.assertNull( comp31.m_singleRef );
        TestCase.assertTrue( comp31.m_multiRef.contains( srv5 ) );
        TestCase.assertTrue( comp31.m_multiRef.contains( srv6 ) );
        TestCase.assertTrue( comp31.m_multiRef.contains( srv7 ) );

        // srv6 goes, rebind to srv7
        srv6.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp32 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp32 );
        TestCase.assertNull( comp32.m_singleRef );
        TestCase.assertTrue( comp32.m_multiRef.contains( srv5 ) );
        TestCase.assertFalse( comp32.m_multiRef.contains( srv6 ) );
        TestCase.assertTrue( comp32.m_multiRef.contains( srv7 ) );
    }


    @Test
    public void test_required_multiple_dynamic() throws Exception
    {
        final SimpleServiceImpl srv1 = SimpleServiceImpl.create( bundleContext, "srv1" );

        String name ="test_required_multiple_dynamic";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp10 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp10 );
        TestCase.assertNull( comp10.m_singleRef );
        TestCase.assertTrue( comp10.m_multiRef.contains( srv1 ) );

        srv1.drop();
        // no delay, should be immediate

        findComponentConfigurationByName(name, ComponentConfigurationDTO.UNSATISFIED_REFERENCE);
        final SimpleComponent comp11 = SimpleComponent.INSTANCE;
        TestCase.assertNull( comp11 );

        final SimpleServiceImpl srv2 = SimpleServiceImpl.create( bundleContext, "srv2" );
        delay(); // async binding

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp12 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp10, comp12 );
        TestCase.assertNull( comp12.m_singleRef );
        TestCase.assertTrue( comp12.m_multiRef.contains( srv2 ) );

        disableAndCheck(cc);
        delay(); // async disabling

        final SimpleServiceImpl srv3 = SimpleServiceImpl.create( bundleContext, "srv3" );

        // enable component with two services available, expect both bind
        // async enabling
        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp20 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp20 );
        TestCase.assertNotSame( comp10, comp20 );
        TestCase.assertNull( comp20.m_singleRef );
        TestCase.assertTrue( comp20.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp20.m_multiRef.contains( srv3 ) );

        srv2.drop();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp21 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp21 );
        TestCase.assertNull( comp21.m_singleRef );
        TestCase.assertFalse( comp21.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp21.m_multiRef.contains( srv3 ) );

        // create srv4, expect bind
        final SimpleServiceImpl srv4 = SimpleServiceImpl.create( bundleContext, "srv4" );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp22 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp22 );
        TestCase.assertNull( comp22.m_singleRef );
        TestCase.assertFalse( comp22.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp22.m_multiRef.contains( srv3 ) );
        TestCase.assertTrue( comp22.m_multiRef.contains( srv4 ) );

        // drop srv4 again, expect no rebind
        srv4.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp23 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp20, comp23 );
        TestCase.assertNull( comp23.m_singleRef );
        TestCase.assertFalse( comp23.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp23.m_multiRef.contains( srv3 ) );
        TestCase.assertFalse( comp23.m_multiRef.contains( srv4 ) );

        // "reset"
        disableAndCheck(cc);
        srv3.drop();
        delay();

        // two services with service ranking (srv6 > srv5)
        final SimpleServiceImpl srv5 = SimpleServiceImpl.create( bundleContext, "srv5", 10 );
        final SimpleServiceImpl srv6 = SimpleServiceImpl.create( bundleContext, "srv6", 20 );

        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp30 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp30 );
        TestCase.assertNull( comp30.m_singleRef );
        TestCase.assertTrue( comp30.m_multiRef.contains( srv5 ) );
        TestCase.assertTrue( comp30.m_multiRef.contains( srv6 ) );

        // another service with higher ranking -- no rebind !
        final SimpleServiceImpl srv7 = SimpleServiceImpl.create( bundleContext, "srv7", 30 );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp31 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp31 );
        TestCase.assertNull( comp31.m_singleRef );
        TestCase.assertTrue( comp31.m_multiRef.contains( srv5 ) );
        TestCase.assertTrue( comp31.m_multiRef.contains( srv6 ) );
        TestCase.assertTrue( comp31.m_multiRef.contains( srv7 ) );

        // srv6 goes, rebind to srv7
        srv6.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp32 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp32 );
        TestCase.assertNull( comp32.m_singleRef );
        TestCase.assertTrue( comp32.m_multiRef.contains( srv5 ) );
        TestCase.assertFalse( comp32.m_multiRef.contains( srv6 ) );
        TestCase.assertTrue( comp32.m_multiRef.contains( srv7 ) );
    }


    @Test
    public void test_required_multiple_dynamic_factory() throws Exception
    {
        String name ="test_required_multiple_dynamic_factory"; //also pid
        final String factoryPid = "factory_" + name;
        getConfigurationsDisabledThenEnable(name, 0, ComponentConfigurationDTO.UNSATISFIED_REFERENCE);
        //        final String pid = "test_required_multiple_dynamic_factory";
        //
        //        final Component component = findComponentDescriptorByName( pid );
        //        TestCase.assertNotNull( component );
        //        TestCase.assertEquals( Component.STATE_DISABLED, component.getState() );
        //
        //        // async enabling (unsatisfied)
        //        enableAndCheck(cc.description);
        //        delay();
        //        findComponentConfigurationByName(name, ComponentConfigurationDTO.UNSATISFIED_REFERENCE);

        // register service, satisfying
        final SimpleServiceImpl srv1 = SimpleServiceImpl.create( bundleContext, "srv1" );
        delay();
        //        TestCase.assertEquals( Component.STATE_FACTORY, component.getState() );

        // create a component instance
        final ServiceReference<?>[] refs = bundleContext.getServiceReferences(
            ComponentFactory.class.getName(), "("
                + ComponentConstants.COMPONENT_FACTORY + "=" + factoryPid + ")" );
        TestCase.assertNotNull( refs );
        TestCase.assertEquals( 1, refs.length );
        final ComponentFactory<?> factory = (ComponentFactory<?>) bundleContext.getService(
            refs[0]);
        TestCase.assertNotNull( factory );

        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put( PROP_NAME_FACTORY, PROP_NAME_FACTORY );
        final ComponentInstance<?> instance = factory.newInstance(props);
        TestCase.assertNotNull( instance );
        TestCase.assertNotNull( instance.getInstance() );
        TestCase.assertEquals( SimpleComponent.INSTANCE, instance.getInstance() );

        // ensure instance is bound
        final SimpleComponent sc = SimpleComponent.INSTANCE;
        TestCase.assertEquals( 1, sc.m_multiRef.size() );
        TestCase.assertTrue( sc.m_multiRef.contains( srv1 ) );

        // ensure factory is not bound
        //        TestCase.assertNull( component.getReferences()[0].getServiceReferences() );

        // assert two components managed
        checkConfigurationCount(name, 1, ComponentConfigurationDTO.ACTIVE);
        //        final Component[] allFactoryComponents = findComponentConfigurationsByName( pid, -1 );
        //        TestCase.assertNotNull( allFactoryComponents );
        //        TestCase.assertEquals( 2, allFactoryComponents.length );
        //        for ( int i = 0; i < allFactoryComponents.length; i++ )
        //        {
        //            final Component c = allFactoryComponents[i];
        //            if ( c.getId() == component.getId() )
        //            {
        //                TestCase.assertEquals( Component.STATE_FACTORY, c.getState() );
        //            }
        //            else if ( c.getId() == SimpleComponent.INSTANCE.m_id )
        //            {
        //                TestCase.assertEquals( Component.STATE_ACTIVE, c.getState() );
        //            }
        //            else
        //            {
        //                TestCase.fail( "Unexpected Component " + c );
        //            }
        //        }

        // register second service
        final SimpleServiceImpl srv11 = SimpleServiceImpl.create( bundleContext, "srv11" );
        delay();

        // ensure instance is bound
        TestCase.assertEquals( 2, sc.m_multiRef.size() );
        TestCase.assertTrue( sc.m_multiRef.contains( srv1 ) );
        TestCase.assertTrue( sc.m_multiRef.contains( srv11 ) );

        // ensure factory is not bound
        //        TestCase.assertNull( component.getReferences()[0].getServiceReferences() );

        // drop second service and ensure unbound (and active)
        srv11.drop();
        delay();
        TestCase.assertNotNull( instance.getInstance() );
        TestCase.assertEquals( SimpleComponent.INSTANCE, instance.getInstance() );
        TestCase.assertEquals( 1, sc.m_multiRef.size() );
        TestCase.assertTrue( sc.m_multiRef.contains( srv1 ) );
        //        TestCase.assertNull( component.getReferences()[0].getServiceReferences() );


        // remove the service, expect factory to deactivate and instance to dispose
        srv1.drop();
        delay();

        checkConfigurationCount(name, 0, -1);
        TestCase.assertNull( instance.getInstance() );

        // assert component factory only managed
        //TODO this check should be whether the service is registered.
        //        final Component[] allFactoryComponents2 = findComponentConfigurationsByName( pid, -1 );
        //        TestCase.assertNotNull( allFactoryComponents2 );
        //        TestCase.assertEquals( 1, allFactoryComponents2.length );
        //        for ( int i = 0; i < allFactoryComponents2.length; i++ )
        //        {
        //            final Component c = allFactoryComponents2[i];
        //            if ( c.getId() == component.getId() )
        //            {
        //                TestCase.assertEquals( Component.STATE_UNSATISFIED, c.getState() );
        //            }
        //            else
        //            {
        //                TestCase.fail( "Unexpected Component " + c );
        //            }
        //        }

        // registeranother service, factory must come back, instance not
        @SuppressWarnings("unused")
        final SimpleServiceImpl srv2 = SimpleServiceImpl.create( bundleContext, "srv2" );
        delay();

        //        TestCase.assertEquals( Component.STATE_FACTORY, component.getState() );
        TestCase.assertNull( instance.getInstance() );

        // assert component factory only managed
        checkConfigurationCount(name, 0, -1);
        //        final Component[] allFactoryComponents3 = findComponentConfigurationsByName( pid, -1 );
        //        TestCase.assertNotNull( allFactoryComponents3 );
        //        TestCase.assertEquals( 1, allFactoryComponents3.length );
        //        for ( int i = 0; i < allFactoryComponents3.length; i++ )
        //        {
        //            final Component c = allFactoryComponents3[i];
        //            if ( c.getId() == component.getId() )
        //            {
        //                TestCase.assertEquals( Component.STATE_FACTORY, c.getState() );
        //            }
        //            else
        //            {
        //                TestCase.fail( "Unexpected Component " + c );
        //            }
        //        }
    }


    @Test
    public void test_optional_single_static() throws Exception
    {
        final SimpleServiceImpl srv1 = SimpleServiceImpl.create( bundleContext, "srv1" );

        String name ="test_optional_single_static";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp10 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp10 );
        TestCase.assertEquals( srv1, comp10.m_singleRef );
        TestCase.assertTrue( comp10.m_multiRef.isEmpty() );

        srv1.drop();
        delay(); // async reactivate

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp11 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp10, comp11 );
        TestCase.assertNull( comp11.m_singleRef );
        TestCase.assertTrue( comp11.m_multiRef.isEmpty() );

        final SimpleServiceImpl srv2 = SimpleServiceImpl.create( bundleContext, "srv2" );
        delay(); // async binding

        // static reference does not rebind unless component is cycled for other reasons !!
        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp12 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp10, comp12 );
        TestCase.assertSame( comp11, comp12 );
        TestCase.assertNull( comp12.m_singleRef );
        TestCase.assertTrue( comp12.m_multiRef.isEmpty() );

        disableAndCheck(cc);
        delay(); // async disabling

        final SimpleServiceImpl srv3 = SimpleServiceImpl.create( bundleContext, "srv3" );

        // enable component with two services available, expect srv2 bind
        // async enabling
        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp20 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp20 );
        TestCase.assertNotSame( comp10, comp20 );
        TestCase.assertEquals( srv2, comp20.m_singleRef );
        TestCase.assertTrue( comp20.m_multiRef.isEmpty() );

        // drop srv2, expect rebind to srv3 (synchronously)
        srv2.drop();
        delay(); // async reactivate

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp21 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp21 );
        TestCase.assertEquals( srv3, comp21.m_singleRef );
        TestCase.assertTrue( comp21.m_multiRef.isEmpty() );

        // create srv4, expect no rebind
        final SimpleServiceImpl srv4 = SimpleServiceImpl.create( bundleContext, "srv4" );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp22 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp22 );
        TestCase.assertSame( comp21, comp22 );
        TestCase.assertEquals( srv3, comp22.m_singleRef );
        TestCase.assertTrue( comp22.m_multiRef.isEmpty() );

        // drop srv4 again, expect no rebind
        srv4.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp23 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp23 );
        TestCase.assertSame( comp21, comp23 );
        TestCase.assertSame( comp22, comp23 );
        TestCase.assertEquals( srv3, comp23.m_singleRef );
        TestCase.assertTrue( comp23.m_multiRef.isEmpty() );

        // "reset"
        disableAndCheck(cc);
        srv3.drop();
        delay();

        // two services with service ranking (srv6 > srv5)
        @SuppressWarnings("unused")
        final SimpleServiceImpl srv5 = SimpleServiceImpl.create( bundleContext, "srv5", 10 );
        final SimpleServiceImpl srv6 = SimpleServiceImpl.create( bundleContext, "srv6", 20 );

        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp30 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp23, comp30 );
        TestCase.assertEquals( srv6, comp30.m_singleRef );
        TestCase.assertTrue( comp30.m_multiRef.isEmpty() );

        // another service with higher ranking -- no rebind !
        final SimpleServiceImpl srv7 = SimpleServiceImpl.create( bundleContext, "srv7", 30 );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp31 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp31 );
        TestCase.assertEquals( srv6, comp31.m_singleRef );
        TestCase.assertTrue( comp31.m_multiRef.isEmpty() );

        // srv6 goes, rebind to srv7
        srv6.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp32 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp30, comp32 );
        TestCase.assertNotSame( comp31, comp32 );
        TestCase.assertEquals( srv7, comp32.m_singleRef );
        TestCase.assertTrue( comp32.m_multiRef.isEmpty() );
    }


    @Test
    public void test_required_single_static() throws Exception
    {
        final SimpleServiceImpl srv1 = SimpleServiceImpl.create( bundleContext, "srv1" );

        String name ="test_required_single_static";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp10 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp10 );
        TestCase.assertEquals( srv1, comp10.m_singleRef );
        TestCase.assertTrue( comp10.m_multiRef.isEmpty() );

        srv1.drop();
        delay(); // async reactivate

        findComponentConfigurationByName(name, ComponentConfigurationDTO.UNSATISFIED_REFERENCE);
        final SimpleComponent comp11 = SimpleComponent.INSTANCE;
        TestCase.assertNull( comp11 );

        final SimpleServiceImpl srv2 = SimpleServiceImpl.create( bundleContext, "srv2" );
        delay(); // async binding

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp12 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp10, comp12 );
        TestCase.assertEquals( srv2, comp12.m_singleRef );
        TestCase.assertTrue( comp12.m_multiRef.isEmpty() );

        disableAndCheck(cc);
        delay(); // async disabling

        final SimpleServiceImpl srv3 = SimpleServiceImpl.create( bundleContext, "srv3" );

        // enable component with two services available, expect srv2 bind
        // async enabling
        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp20 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp20 );
        TestCase.assertNotSame( comp10, comp20 );
        TestCase.assertEquals( srv2, comp20.m_singleRef );
        TestCase.assertTrue( comp20.m_multiRef.isEmpty() );

        // drop srv2, expect rebind to srv3
        srv2.drop();
        delay(); // async reactivate

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp21 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp21 );
        TestCase.assertEquals( srv3, comp21.m_singleRef );
        TestCase.assertTrue( comp21.m_multiRef.isEmpty() );

        // create srv4, expect no rebind
        final SimpleServiceImpl srv4 = SimpleServiceImpl.create( bundleContext, "srv4" );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp22 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp22 );
        TestCase.assertSame( comp21, comp22 );
        TestCase.assertEquals( srv3, comp22.m_singleRef );
        TestCase.assertTrue( comp22.m_multiRef.isEmpty() );

        // drop srv4 again, expect no rebind
        srv4.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp23 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp23 );
        TestCase.assertSame( comp21, comp23 );
        TestCase.assertSame( comp22, comp23 );
        TestCase.assertEquals( srv3, comp23.m_singleRef );
        TestCase.assertTrue( comp23.m_multiRef.isEmpty() );

        // "reset"
        disableAndCheck(cc);
        srv3.drop();
        delay();

        // two services with service ranking (srv6 > srv5)
        @SuppressWarnings("unused")
        final SimpleServiceImpl srv5 = SimpleServiceImpl.create( bundleContext, "srv5", 10 );
        final SimpleServiceImpl srv6 = SimpleServiceImpl.create( bundleContext, "srv6", 20 );

        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp30 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp23, comp30 );
        TestCase.assertEquals( srv6, comp30.m_singleRef );
        TestCase.assertTrue( comp30.m_multiRef.isEmpty() );

        // another service with higher ranking -- no rebind !
        final SimpleServiceImpl srv7 = SimpleServiceImpl.create( bundleContext, "srv7", 30 );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp31 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp31 );
        TestCase.assertEquals( srv6, comp31.m_singleRef );
        TestCase.assertTrue( comp31.m_multiRef.isEmpty() );

        // srv6 goes, rebind to srv7
        srv6.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp32 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp30, comp32 );
        TestCase.assertNotSame( comp31, comp32 );
        TestCase.assertEquals( srv7, comp32.m_singleRef );
        TestCase.assertTrue( comp32.m_multiRef.isEmpty() );
    }


    @Test
    public void test_optional_multiple_static() throws Exception
    {
        final SimpleServiceImpl srv1 = SimpleServiceImpl.create( bundleContext, "srv1" );

        String name ="test_optional_multiple_static";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp10 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp10 );
        TestCase.assertNull( comp10.m_singleRef );
        TestCase.assertTrue( comp10.m_multiRef.contains( srv1 ) );

        srv1.drop();
        delay(); // async reactivate

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp11 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp10, comp11 );
        TestCase.assertNull( comp11.m_singleRef );
        TestCase.assertTrue( comp11.m_multiRef.isEmpty() );

        final SimpleServiceImpl srv2 = SimpleServiceImpl.create( bundleContext, "srv2" );
        delay(); // async binding

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp12 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp10, comp12 );
        TestCase.assertSame( comp11, comp12 );
        TestCase.assertNull( comp12.m_singleRef );
        TestCase.assertTrue( comp12.m_multiRef.isEmpty() );

        disableAndCheck(cc);
        delay(); // async disabling

        final SimpleServiceImpl srv3 = SimpleServiceImpl.create( bundleContext, "srv3" );

        // enable component with two services available, expect both bind
        // async enabling
        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp20 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp20 );
        TestCase.assertNotSame( comp10, comp20 );
        TestCase.assertNotSame( comp11, comp20 );
        TestCase.assertNotSame( comp12, comp20 );
        TestCase.assertNull( comp20.m_singleRef );
        TestCase.assertTrue( comp20.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp20.m_multiRef.contains( srv3 ) );

        srv2.drop();
        delay(); // async reactivate

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp21 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp21 );
        TestCase.assertNull( comp21.m_singleRef );
        TestCase.assertFalse( comp21.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp21.m_multiRef.contains( srv3 ) );

        // create srv4, expect not bind (static case)
        final SimpleServiceImpl srv4 = SimpleServiceImpl.create( bundleContext, "srv4" );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp22 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp22 );
        TestCase.assertSame( comp21, comp22 );
        TestCase.assertNull( comp22.m_singleRef );
        TestCase.assertFalse( comp22.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp22.m_multiRef.contains( srv3 ) );
        TestCase.assertFalse( comp22.m_multiRef.contains( srv4 ) );

        // drop srv4 again, expect no rebind
        srv4.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp23 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp23 );
        TestCase.assertSame( comp21, comp23 );
        TestCase.assertSame( comp22, comp23 );
        TestCase.assertNull( comp23.m_singleRef );
        TestCase.assertFalse( comp23.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp23.m_multiRef.contains( srv3 ) );
        TestCase.assertFalse( comp23.m_multiRef.contains( srv4 ) );

        // "reset"
        disableAndCheck(cc);
        srv3.drop();
        delay();

        // two services with service ranking (srv6 > srv5)
        final SimpleServiceImpl srv5 = SimpleServiceImpl.create( bundleContext, "srv5", 10 );
        final SimpleServiceImpl srv6 = SimpleServiceImpl.create( bundleContext, "srv6", 20 );

        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp30 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp23, comp30 );
        TestCase.assertNull( comp30.m_singleRef );
        TestCase.assertTrue( comp30.m_multiRef.contains( srv5 ) );
        TestCase.assertTrue( comp30.m_multiRef.contains( srv6 ) );

        // another service with higher ranking -- no rebind !
        final SimpleServiceImpl srv7 = SimpleServiceImpl.create( bundleContext, "srv7", 30 );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp31 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp31 );
        TestCase.assertNull( comp31.m_singleRef );
        TestCase.assertTrue( comp31.m_multiRef.contains( srv5 ) );
        TestCase.assertTrue( comp31.m_multiRef.contains( srv6 ) );
        TestCase.assertFalse( comp31.m_multiRef.contains( srv7 ) );

        // srv6 goes, rebind to srv7
        srv6.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp32 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp30, comp32 );
        TestCase.assertNotSame( comp31, comp32 );
        TestCase.assertNull( comp32.m_singleRef );
        TestCase.assertTrue( comp32.m_multiRef.contains( srv5 ) );
        TestCase.assertFalse( comp32.m_multiRef.contains( srv6 ) );
        TestCase.assertTrue( comp32.m_multiRef.contains( srv7 ) );
    }


    @Test
    public void test_required_multiple_static() throws Exception
    {
        final SimpleServiceImpl srv1 = SimpleServiceImpl.create( bundleContext, "srv1" );

        String name ="test_required_multiple_static";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp10 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp10 );
        TestCase.assertNull( comp10.m_singleRef );
        TestCase.assertTrue( comp10.m_multiRef.contains( srv1 ) );

        srv1.drop();
        delay(); // async reactivate

        findComponentConfigurationByName(name, ComponentConfigurationDTO.UNSATISFIED_REFERENCE);
        final SimpleComponent comp11 = SimpleComponent.INSTANCE;
        TestCase.assertNull( comp11 );

        final SimpleServiceImpl srv2 = SimpleServiceImpl.create( bundleContext, "srv2" );
        delay(); // async binding

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp12 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp10, comp12 );
        TestCase.assertNull( comp12.m_singleRef );
        TestCase.assertTrue( comp12.m_multiRef.contains( srv2 ) );

        disableAndCheck(cc);
        delay(); // async disabling

        final SimpleServiceImpl srv3 = SimpleServiceImpl.create( bundleContext, "srv3" );

        // enable component with two services available, expect both bind
        // async enabling
        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp20 = SimpleComponent.INSTANCE;
        TestCase.assertNotNull( comp20 );
        TestCase.assertNotSame( comp10, comp20 );
        TestCase.assertNotSame( comp12, comp20 );
        TestCase.assertNull( comp20.m_singleRef );
        TestCase.assertTrue( comp20.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp20.m_multiRef.contains( srv3 ) );

        srv2.drop();
        delay(); // async reactivate

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp21 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp21 );
        TestCase.assertNull( comp21.m_singleRef );
        TestCase.assertFalse( comp21.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp21.m_multiRef.contains( srv3 ) );

        // create srv4, expect bind
        final SimpleServiceImpl srv4 = SimpleServiceImpl.create( bundleContext, "srv4" );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp22 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp22 );
        TestCase.assertSame( comp21, comp22 );
        TestCase.assertNull( comp22.m_singleRef );
        TestCase.assertFalse( comp22.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp22.m_multiRef.contains( srv3 ) );
        TestCase.assertFalse( comp22.m_multiRef.contains( srv4 ) );

        // drop srv4 again, expect no rebind
        srv4.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp23 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp20, comp23 );
        TestCase.assertSame( comp21, comp23 );
        TestCase.assertSame( comp22, comp23 );
        TestCase.assertNull( comp23.m_singleRef );
        TestCase.assertFalse( comp23.m_multiRef.contains( srv2 ) );
        TestCase.assertTrue( comp23.m_multiRef.contains( srv3 ) );
        TestCase.assertFalse( comp23.m_multiRef.contains( srv4 ) );

        // "reset"
        disableAndCheck(cc);
        srv3.drop();
        delay();

        // two services with service ranking (srv6 > srv5)
        final SimpleServiceImpl srv5 = SimpleServiceImpl.create( bundleContext, "srv5", 10 );
        final SimpleServiceImpl srv6 = SimpleServiceImpl.create( bundleContext, "srv6", 20 );

        enableAndCheck(cc.description);
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp30 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp23, comp30 );
        TestCase.assertNull( comp30.m_singleRef );
        TestCase.assertTrue( comp30.m_multiRef.contains( srv5 ) );
        TestCase.assertTrue( comp30.m_multiRef.contains( srv6 ) );

        // another service with higher ranking -- no rebind !
        final SimpleServiceImpl srv7 = SimpleServiceImpl.create( bundleContext, "srv7", 30 );
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp31 = SimpleComponent.INSTANCE;
        TestCase.assertSame( comp30, comp31 );
        TestCase.assertNull( comp31.m_singleRef );
        TestCase.assertTrue( comp31.m_multiRef.contains( srv5 ) );
        TestCase.assertTrue( comp31.m_multiRef.contains( srv6 ) );
        TestCase.assertFalse( comp31.m_multiRef.contains( srv7 ) );

        // srv6 goes, rebind to srv7
        srv6.drop();
        delay();

        findComponentConfigurationByName(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent comp32 = SimpleComponent.INSTANCE;
        TestCase.assertNotSame( comp30, comp32 );
        TestCase.assertNotSame( comp31, comp32 );
        TestCase.assertNull( comp32.m_singleRef );
        TestCase.assertTrue( comp32.m_multiRef.contains( srv5 ) );
        TestCase.assertFalse( comp32.m_multiRef.contains( srv6 ) );
        TestCase.assertTrue( comp32.m_multiRef.contains( srv7 ) );
    }

    @Test
    public void test_multi_service_bind_unbind_order() throws Exception
    {
        final SimpleServiceImpl srv1 = SimpleServiceImpl.create( bundleContext, "srv1" );
        final SimpleService2Impl srv2 = SimpleService2Impl.create( bundleContext, "srv2" );

        String name ="test_multi_service_bind_unbind_order";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(name, ComponentConfigurationDTO.ACTIVE);

        final SimpleComponent2 comp10 = SimpleComponent2.INSTANCE;
        TestCase.assertNotNull( comp10 );
        TestCase.assertEquals( 2, comp10.getBindings().size() );
        TestCase.assertEquals( "bindSimpleService", comp10.getBindings().get( 0 ) );
        TestCase.assertEquals( "bindSimpleService2", comp10.getBindings().get( 1 ) );

        disableAndCheck(cc);
        delay();

        TestCase.assertEquals( 4, comp10.getBindings().size() );
        TestCase.assertEquals( "bindSimpleService", comp10.getBindings().get( 0 ) );
        TestCase.assertEquals( "bindSimpleService2", comp10.getBindings().get( 1 ) );
        TestCase.assertEquals( "unbindSimpleService2", comp10.getBindings().get( 2 ) );
        TestCase.assertEquals( "unbindSimpleService", comp10.getBindings().get( 3 ) );

        srv1.drop();
        srv2.drop();
    }
}
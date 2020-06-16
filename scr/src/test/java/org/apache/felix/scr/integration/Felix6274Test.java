/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.scr.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.tinybundles.core.TinyBundles.bundle;
import static org.ops4j.pax.tinybundles.core.TinyBundles.withClassicBuilder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.namespace.extender.ExtenderNamespace;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.log.LogService;

@RunWith(PaxExam.class)
public class Felix6274Test extends ComponentTestBase
{
    static
    {
        // This test creates its own component bundles
        descriptorFile = null;
        DS_LOGLEVEL = "debug";
        // paxRunnerVmOption = DEBUG_VM_OPTION;
    }

    private final List<Bundle> installedBundles = new ArrayList<>();
    private Bundle log_1_4_bundle;
    private Bundle log_1_3_bundle;
    
    @Before
    public void installBundles() throws BundleException {
        String log101 = mavenBundle( "org.apache.felix", "org.apache.felix.log", "1.0.1" ).getURL();

        // install and start the resolver hook first
        Bundle hookBundle = bundleContext.installBundle("integration.test.6274_hook", createHookBundle());
        installedBundles.add(hookBundle);
        hookBundle.start();

        // Install an older version of the log service 
        installedBundles.add( bundleContext.installBundle( log101 ) );

        
        log_1_4_bundle = bundleContext.installBundle( "integration.test.6274", 
                createStream( "6274", "[1.4,1.5)" ) );
        installedBundles.add( log_1_4_bundle );

        log_1_3_bundle = bundleContext.installBundle( "integration.test.6274_2", 
                createStream( "6274_2", "[1.3,1.4)" ) );
        installedBundles.add( log_1_3_bundle );

        for( Bundle b : installedBundles ) {
            b.start();
        }
    }
    
    @After
    public void cleanUpBundles() {
        for( Bundle b : installedBundles ) {
            try {
                b.uninstall();
            } catch ( BundleException be ) {
                // Just swallow this and keep going
            }
        }
    }

    private InputStream createStream(String testNumber, String logImportVersionRange) {
        String classFilePath = "org/apache/felix/scr/integration/components/felix" + testNumber + "/Component.class";
        String componentXML = "integration_test_FELIX_" + testNumber + ".xml";
        
        System.out.println("Located descriptor " + getClass().getResource( "/" + componentXML ) );
        
        return bundle().add("OSGI-INF/components.xml", getClass().getResource( "/" + componentXML ) )
                .add(classFilePath, getClass().getResource( "/" + classFilePath ) )

                .set( Constants.BUNDLE_MANIFESTVERSION, "2" )
                .set( Constants.BUNDLE_SYMBOLICNAME, "integration.test." + testNumber )
                .set( Constants.BUNDLE_VERSION, "1.0.0" )
                .set( Constants.IMPORT_PACKAGE, "org.osgi.service.log;version=\"" + logImportVersionRange + "\"" )
                .set( "Service-Component", "OSGI-INF/components.xml" )
                .set( Constants.REQUIRE_CAPABILITY, ExtenderNamespace.EXTENDER_NAMESPACE
                                + ";filter:=\"(&(osgi.extender=osgi.component)(version>=1.4)(!(version>=2.0)))\"" )
                .build( withClassicBuilder() );
    }

    private InputStream createHookBundle()
    {
        String activatorClass = "org.apache.felix.scr.integration.components.felix6274_hook.Activator";
        String activatorFilePath = activatorClass.replace('.', '/') + ".class";
        return bundle().add(activatorFilePath, getClass().getResource("/" + activatorFilePath))
            .set( Constants.BUNDLE_MANIFESTVERSION, "2" )
            .set( Constants.BUNDLE_SYMBOLICNAME, "integration.test.6274_hook" )
            .set( Constants.BUNDLE_VERSION, "1.0.0" )
            .set( Constants.BUNDLE_ACTIVATOR, activatorClass)
            .set( Constants.IMPORT_PACKAGE, "org.osgi.framework, org.osgi.framework.hooks.resolver, org.osgi.framework.namespace, org.osgi.framework.wiring" )
            .build( withClassicBuilder() );
    }

    @Test
    public void test_incompatible_log_service_version() throws Exception
    {
        
        delay();

        // Locate the components
        ComponentDescriptionDTO log_1_4_dto = scrTracker.getService().getComponentDescriptionDTO(
                log_1_4_bundle, "R7LoggerComponent" );

        ComponentDescriptionDTO log_1_3_dto = scrTracker.getService().getComponentDescriptionDTO(
                log_1_3_bundle, "LogServiceComponent" );

        assertNotNull( "No Log 1.4 Component DTO", log_1_4_dto );
        assertNotNull( "No Log 1.3 Component DTO", log_1_3_dto );

        // Get the runtime instance data
        Collection<ComponentConfigurationDTO> running_1_4_components = scrTracker.getService()
                .getComponentConfigurationDTOs( log_1_4_dto );
        
        Collection<ComponentConfigurationDTO> running_1_3_components = scrTracker.getService()
                .getComponentConfigurationDTOs( log_1_3_dto );
        
        assertEquals( 1, running_1_4_components.size() );
        assertEquals( 1, running_1_3_components.size() );

        // Check the components are active
        assertEquals( "1.4 Log Service Component failed to activate", 
                ComponentConfigurationDTO.ACTIVE, running_1_4_components.iterator().next().state );
        log.log( LogService.LOG_INFO, "R7LoggerComponent checked active" );
        
        assertEquals("1.3 Log Service Component failed to activate",
                ComponentConfigurationDTO.ACTIVE, running_1_3_components.iterator().next().state);
        log.log(LogService.LOG_INFO, "LogServiceComponent checked active");

    }
}

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
package org.apache.felix.scr.impl.truecondition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.tinybundles.core.TinyBundles.bundle;
import static org.ops4j.pax.tinybundles.core.TinyBundles.withClassicBuilder;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.felix.scr.impl.Activator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.ops4j.pax.tinybundles.core.TinyBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.ReferenceDTO;
import org.osgi.service.component.runtime.dto.SatisfiedReferenceDTO;
import org.osgi.service.component.runtime.dto.UnsatisfiedReferenceDTO;
import org.osgi.service.condition.Condition;

import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.JarResource;
import aQute.lib.io.IO;

public class TrueConditionTest
{

    private static Framework framework;
    private static Bundle scr;
    private BundleActivator scrActivator;
    private ServiceComponentRuntime scRuntime;
    private Collection<Bundle> installed = new ArrayList<>();

    @BeforeClass
    public static void setup() throws Exception
    {
        File tmp = IO.getFile("target/tmp/loggertest");
        Map<String, String> configuration = new HashMap<>();
        configuration.put(Constants.FRAMEWORK_STORAGE,
            tmp.getAbsolutePath());
        configuration.put(Constants.FRAMEWORK_STORAGE_CLEAN,
            Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        configuration.put(Constants.FRAMEWORK_BUNDLE_PARENT,
            Constants.FRAMEWORK_BUNDLE_PARENT_FRAMEWORK);
        configuration.put(Constants.FRAMEWORK_BOOTDELEGATION, "*");
        framework = ServiceLoader.load(
            org.osgi.framework.launch.FrameworkFactory.class).iterator().next().newFramework(
                configuration);
        framework.init();
        framework.start();

        // fake out a true condition for an R8 framework impl
        Hashtable<String, String> trueConditionProps = new Hashtable<>();
        trueConditionProps.put(Condition.CONDITION_ID, Condition.CONDITION_ID_TRUE);
        framework.getBundleContext().registerService(Condition.class, Condition.INSTANCE,
            trueConditionProps);

        scr = framework.getBundleContext().installBundle("scr",
            makeBundle("scr").openInputStream());
        scr.start();
    }

    @AfterClass
    public static void after() throws Exception
    {
        framework.stop();
        framework.waitForStop(100000);
    }

    @Before
    public void startScr() throws Exception
    {
        scrActivator = new Activator();
        scrActivator.start(scr.getBundleContext());
        scRuntime = getServiceComponentRuntime();
    }

    @After
    public void cleanup() throws Exception
    {
        for (Bundle b : installed)
        {
            b.uninstall();
        }
        scrActivator.stop(scr.getBundleContext());
    }

    private static JarResource makeBundle(String bsn) throws Exception
    {
        @SuppressWarnings("resource")
        Builder b = new Builder();
        b.setBundleSymbolicName(bsn);
        Jar jar = b.build();
        b.removeClose(jar);
        return new JarResource(jar);
    }

    private Bundle installComponentBundle(String bsn,
        String componentXML) throws BundleException, Exception
    {
        Bundle b = framework.getBundleContext().installBundle(bsn,
            makeComponent(bsn, componentXML));
        installed.add(b);
        return b;
    }

    private InputStream makeComponent(String bsn, String componentXML) throws Exception
    {


        TinyBundle bundle = bundle() //
            .add("OSGI-INF/components.xml", getClass().getResource("/" + componentXML)) //
            .set(Constants.BUNDLE_MANIFESTVERSION, "2") //
            .set(Constants.BUNDLE_SYMBOLICNAME, bsn) //
            .set(Constants.BUNDLE_VERSION, "1.0.0") //
            .set("Service-Component", "OSGI-INF/components.xml");
        return bundle.build(withClassicBuilder());
    }

    @Test
    public void testDefaultSatisfyingCondition() throws BundleException, Exception
    {
        Bundle componentBundle = installComponentBundle("component.test.bundle", //
            "satisfying_condition_test1.xml");
        componentBundle.start();
        ComponentDescriptionDTO descriptionDTO = scRuntime.getComponentDescriptionDTO(
            componentBundle, "satisfying-condition-test1");
        assertNotNull("No component DTO found.", descriptionDTO);
        assertEquals("Wrong number of references.", 1, descriptionDTO.references.length);

        ReferenceDTO trueReference = descriptionDTO.references[0];
        assertEquals("Wrong name.", "osgi.ds.satisfying.condition", trueReference.name);
        assertEquals("Wrong interface.", "org.osgi.service.condition.Condition",
            trueReference.interfaceName);
        assertEquals("Wrong policy.", "dynamic", trueReference.policy);
        assertEquals("Wrong target.", "(osgi.condition.id=true)", trueReference.target);

        ComponentConfigurationDTO configDTO = scRuntime.getComponentConfigurationDTOs(
            descriptionDTO).iterator().next();
        SatisfiedReferenceDTO satisfiedDTO = configDTO.satisfiedReferences[0];
        assertEquals("Wrong target.", "(osgi.condition.id=true)", satisfiedDTO.target);
    }

    @Test
    public void testTargetPropSatisfyingCondition() throws BundleException, Exception
    {
        Bundle componentBundle = installComponentBundle("component.test.bundle", //
            "satisfying_condition_test2.xml");
        componentBundle.start();
        ComponentDescriptionDTO descriptionDTO = scRuntime.getComponentDescriptionDTO(
            componentBundle, "satisfying-condition-test2");
        assertNotNull("No component DTO found.", descriptionDTO);
        assertEquals("Wrong number of references.", 1, descriptionDTO.references.length);

        ReferenceDTO trueReference = descriptionDTO.references[0];
        assertEquals("Wrong name.", "osgi.ds.satisfying.condition", trueReference.name);
        assertEquals("Wrong interface.", "org.osgi.service.condition.Condition",
            trueReference.interfaceName);
        assertEquals("Wrong policy.", "dynamic", trueReference.policy);
        assertEquals("Wrong target.", "(osgi.condition.id=true)", trueReference.target);

        ComponentConfigurationDTO configDTO = scRuntime.getComponentConfigurationDTOs(
            descriptionDTO).iterator().next();
        UnsatisfiedReferenceDTO unsatisfiedDTO = configDTO.unsatisfiedReferences[0];
        assertEquals("Wrong target.", "(foo=bar)", unsatisfiedDTO.target);
    }

    private ServiceComponentRuntime getServiceComponentRuntime()
    {
        ServiceReference<ServiceComponentRuntime> ref = framework.getBundleContext().getServiceReference(
            ServiceComponentRuntime.class);
        assertNotNull("No registered ServiceComponentRuntime.", ref);
        ServiceComponentRuntime scRuntime = framework.getBundleContext().getService(ref);
        assertNotNull("No registered SerivceComponentRuntime.", scRuntime);
        return scRuntime;
    }
}

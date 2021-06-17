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


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.ReferenceDTO;
import org.osgi.service.component.runtime.dto.SatisfiedReferenceDTO;
import org.osgi.service.component.runtime.dto.UnsatisfiedReferenceDTO;
import org.osgi.service.condition.Condition;


@RunWith(PaxExam.class)
public class SatisfyingConditionTest extends ComponentTestBase
{
    static
    {
        // uncomment to enable debugging of this test class
        // paxRunnerVmOption = DEBUG_VM_OPTION;
        descriptorFile = "/integration_test_satisfying_condition.xml";
    }

    @Before
    public void registerTrueCondition() throws BundleException, InvalidSyntaxException
    {
        ServiceReference<ServiceComponentRuntime> ref = scrTracker.getServiceReference();
        Bundle scrBundle = ref.getBundle();
        File f = scrBundle.getDataFile("componentMetadataStore");
        assertFalse("Cache " + f.getAbsolutePath() + " should not exist", f.exists());

        // fake out a true condition for an R8 framework impl
        if (bundleContext.getServiceReferences(Condition.class,
            "(&(service.bundleid=0)(osgi.condition.id=true))").isEmpty())
        {
            // stop SCR first
            scrBundle.stop();
            // register true condition
            Hashtable<String, String> trueConditionProps = new Hashtable<>();
            trueConditionProps.put(Condition.CONDITION_ID, Condition.CONDITION_ID_TRUE);
            // use system bundle to register true condition
            bundleContext.getBundle(
                Constants.SYSTEM_BUNDLE_LOCATION).getBundleContext().registerService(
                    Condition.class, Condition.INSTANCE,
                trueConditionProps);
            // restart SCR to have it recognize the true condition
            scrBundle.start();

        }
    }

    @Test
    public void test_default_satisfying_condition() throws Exception
    {
        doTargetTrueCondition("satisfying.condition.default");
    }

    @Test
    public void test_specified_satisfying_condition() throws Exception
    {
        doTargetTrueCondition("satisfying.condition.reference.specified");
    }

    void doTargetTrueCondition(final String componentname) throws Exception
    {
        ComponentConfigurationDTO configDTO = getDisabledConfigurationAndEnable(
            componentname,
            ComponentConfigurationDTO.ACTIVE);

        assertEquals("Wrong number of references.", 2,
            configDTO.satisfiedReferences.length);
        SatisfiedReferenceDTO satisfiedDTO = configDTO.satisfiedReferences[1];
        assertEquals("Wrong target.", "(osgi.condition.id=true)", satisfiedDTO.target);

        ComponentDescriptionDTO descriptionDTO = configDTO.description;
        assertEquals("Wrong number of references.", 2, descriptionDTO.references.length);

        ReferenceDTO trueReference = descriptionDTO.references[1];
        assertEquals("Wrong name.", "osgi.ds.satisfying.condition", trueReference.name);
        assertEquals("Wrong interface.", "org.osgi.service.condition.Condition",
            trueReference.interfaceName);
        assertEquals("Wrong policy.", "dynamic", trueReference.policy);
        assertEquals("Wrong target.", "(osgi.condition.id=true)", trueReference.target);

        Dictionary<String, Object> configProps = new Hashtable<>();
        configProps.put("osgi.ds.satisfying.condition.target", "(foo=baz)");
        configure(componentname, null, configProps);
        delay();

        configDTO = findComponentConfigurationByName(componentname,
            ComponentConfigurationDTO.UNSATISFIED_REFERENCE);

        assertEquals("Wrong number of unsatisfied references.", 1,
            configDTO.unsatisfiedReferences.length);
        UnsatisfiedReferenceDTO unsatisfiedDTO = configDTO.unsatisfiedReferences[0];
        assertEquals("Wrong target.", "(foo=baz)", unsatisfiedDTO.target);
    }

    @Test
    public void test_default_satisfying_condition_target() throws Exception
    {
        doTestTargetCustomCondition("satisfying.condition.target.specified");
    }

    @Test
    public void test_specified_satisfying_condition_target() throws Exception
    {
        doTestTargetCustomCondition("satisfying.condition.reference.target.specified");
    }

    void doTestTargetCustomCondition(final String componentname) throws Exception
    {
        ComponentConfigurationDTO configDTO = getDisabledConfigurationAndEnable(
            componentname, ComponentConfigurationDTO.UNSATISFIED_REFERENCE);
        ComponentDescriptionDTO descriptionDTO = configDTO.description;

        assertEquals("Wrong number of unsatisfied references.", 1,
            configDTO.unsatisfiedReferences.length);
        UnsatisfiedReferenceDTO unsatisfiedDTO = configDTO.unsatisfiedReferences[0];
        assertEquals("Wrong target.", "(foo=bar)", unsatisfiedDTO.target);

        assertNotNull("No component DTO found.", descriptionDTO);
        assertEquals("Wrong number of references.", 2, descriptionDTO.references.length);

        ReferenceDTO trueReference = descriptionDTO.references[1];
        assertEquals("Wrong name.", "osgi.ds.satisfying.condition", trueReference.name);
        assertEquals("Wrong interface.", "org.osgi.service.condition.Condition",
            trueReference.interfaceName);
        assertEquals("Wrong policy.", "dynamic", trueReference.policy);
        assertEquals("Wrong target.", "(osgi.condition.id=true)", trueReference.target);

        // register condition to satisfy component
        Hashtable<String, String> testConditionProps = new Hashtable<>();
        testConditionProps.put(Condition.CONDITION_ID, "test");
        testConditionProps.put("foo", "bar");
        // use system bundle to register true condition
        bundleContext.registerService(Condition.class, Condition.INSTANCE,
            testConditionProps);

        configDTO = findComponentConfigurationByName(componentname,
            ComponentConfigurationDTO.ACTIVE);
        assertEquals("Wrong number of references.", 2,
            configDTO.satisfiedReferences.length);
        SatisfiedReferenceDTO satisfiedDTO = configDTO.satisfiedReferences[1];
        assertEquals("Wrong target.", "(foo=bar)", satisfiedDTO.target);
    }

}

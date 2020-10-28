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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.scr.integration.components.AnyServiceComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.UnsatisfiedReferenceDTO;


@RunWith(PaxExam.class)
public class AnyServiceReferenceTest extends ComponentTestBase
{
    static
    {
        // uncomment to enable debugging of this test class
        //paxRunnerVmOption = DEBUG_VM_OPTION;
        descriptorFile = "/integration_test_any_service.xml";
    }
    static final String anyServiceObject = "anyServiceObject";

    @Test
    public void test_any_service_bind() throws Exception
    {
        final String componentName = "any.service.bind";
        final String serviceProp = "bind";

        checkUnsatisfiedAnyReference(componentName, serviceProp);

        registerAnyService(serviceProp);

        AnyServiceComponent anyServiceComponent = checkSatisfiedAnyServiceComponent(
            componentName);

        assertEquals("Should call bind method.", anyServiceObject,
            anyServiceComponent.bindMethodInject);
    }

    @Test
    public void test_any_service_bind_map() throws Exception
    {
        final String componentName = "any.service.bind.map";
        final String serviceProp = "bind";

        checkUnsatisfiedAnyReference(componentName, serviceProp);

        registerAnyService(serviceProp);

        AnyServiceComponent anyServiceComponent = checkSatisfiedAnyServiceComponent(
            componentName);

        assertNotNull("Should call bind method.",
            anyServiceComponent.bindMethodInjectMap);
    }

    @Test
    public void test_any_service_bind_invalid() throws Exception
    {
        final String componentName = "any.service.bind.invalid";
        final String serviceProp = "bind";

        checkUnsatisfiedAnyReference(componentName, serviceProp);
        registerAnyService(serviceProp);

        AnyServiceComponent anyServiceComponent = checkSatisfiedAnyServiceComponent(
            componentName);

        assertNull("Should not call invalid bind method.",
            anyServiceComponent.bindMethodInject);
    }

    @Test
    public void test_any_service_constructor_object() throws Exception
    {
        final String componentName = "any.service.constructor.object";
        final String serviceProp = "constructor";

        checkUnsatisfiedAnyReference(componentName, serviceProp);

        registerAnyService(serviceProp);

        AnyServiceComponent anyServiceComponent = checkSatisfiedAnyServiceComponent(
            componentName);

        assertEquals("Should call constructor", anyServiceObject,
            anyServiceComponent.constructorInject);
    }

    @Test
    public void test_any_service_constructor_map() throws Exception
    {
        final String componentName = "any.service.constructor.map";
        final String serviceProp = "constructor";

        checkUnsatisfiedAnyReference(componentName, serviceProp);

        registerAnyService(serviceProp);

        AnyServiceComponent anyServiceComponent = checkSatisfiedAnyServiceComponent(
            componentName);

        assertNotNull("Should call constructor",
            anyServiceComponent.constructorInjectMap);
    }

    @Test
    public void test_any_service_constructor_list() throws Exception
    {
        final String componentName = "any.service.constructor.list";
        final String serviceProp = "constructor";

        checkUnsatisfiedAnyReference(componentName, serviceProp);

        registerAnyService(serviceProp);

        AnyServiceComponent anyServiceComponent = checkSatisfiedAnyServiceComponent(
            componentName);

        assertNotNull("Should call constructor",
            anyServiceComponent.constructorInjectList);
    }

    @Test
    public void test_any_service_constructor_invalid() throws Exception
    {
        final String componentName = "any.service.constructor.invalid";
        final String serviceProp = "constructor";

        checkUnsatisfiedAnyReference(componentName, serviceProp);

        registerAnyService(serviceProp);

        findComponentConfigurationByName(componentName,
            ComponentConfigurationDTO.FAILED_ACTIVATION);
    }

    @Test
    public void test_any_service_field() throws Exception
    {
        final String componentName = "any.service.field";
        final String serviceProp = "field";

        checkUnsatisfiedAnyReference(componentName, serviceProp);

        registerAnyService(serviceProp);

        AnyServiceComponent anyServiceComponent = checkSatisfiedAnyServiceComponent(
            componentName);

        assertEquals("Should set field.", anyServiceObject,
            anyServiceComponent.fieldInject);
    }

    @Test
    public void test_any_service_field_map() throws Exception
    {
        final String componentName = "any.service.field.map";
        final String serviceProp = "field";

        checkUnsatisfiedAnyReference(componentName, serviceProp);

        registerAnyService(serviceProp);

        AnyServiceComponent anyServiceComponent = checkSatisfiedAnyServiceComponent(
            componentName);

        assertNotNull("Should call constructor", anyServiceComponent.fieldInjectMap);
    }

    @Test
    public void test_any_service_field_List() throws Exception
    {
        final String componentName = "any.service.field.list";
        final String serviceProp = "field";

        checkUnsatisfiedAnyReference(componentName, serviceProp);

        registerAnyService(serviceProp);

        AnyServiceComponent anyServiceComponent = checkSatisfiedAnyServiceComponent(
            componentName);

        assertNotNull("Should call constructor", anyServiceComponent.fieldInjectList);
    }

    @Test
    public void test_any_service_field_invalid() throws Exception
    {
        final String componentName = "any.service.field.invalid";
        final String serviceProp = "field";

        checkUnsatisfiedAnyReference(componentName, serviceProp);

        registerAnyService(serviceProp);

        AnyServiceComponent anyServiceComponent = checkSatisfiedAnyServiceComponent(
            componentName);

        assertNull("Should not set field.", anyServiceComponent.fieldInjectInvalid);
    }

    private void checkUnsatisfiedAnyReference(String componentName, String serviceProp)
        throws InvocationTargetException, InterruptedException
    {
        ComponentConfigurationDTO configDTO = getDisabledConfigurationAndEnable(
            componentName, ComponentConfigurationDTO.UNSATISFIED_REFERENCE);
        UnsatisfiedReferenceDTO unsatisfiedDTO = configDTO.unsatisfiedReferences[0];
        assertEquals("Wrong target.", "(test.any.service=" + serviceProp + ")",
            unsatisfiedDTO.target);
    }

    private void registerAnyService(String serviceProp)
    {
        Dictionary<String, Object> serviceProps = new Hashtable<>(
            Collections.singletonMap("test.any.service", (Object) serviceProp));
        bundleContext.registerService(String.class, anyServiceObject, serviceProps);
    }

    private AnyServiceComponent checkSatisfiedAnyServiceComponent(String componentName)
    {
        ComponentConfigurationDTO configDTO = findComponentConfigurationByName(
            componentName, ComponentConfigurationDTO.ACTIVE);

        return getServiceFromConfiguration(configDTO, AnyServiceComponent.class);
    }
}

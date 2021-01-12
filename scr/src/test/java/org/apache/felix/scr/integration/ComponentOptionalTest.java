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

import org.apache.felix.scr.integration.components.ConstructorSingleReference;
import org.apache.felix.scr.integration.components.InjectOptionalComponent;
import org.apache.felix.scr.integration.components.InjectOptionalComponent.Mode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;


@RunWith(PaxExam.class)
public class ComponentOptionalTest extends ComponentTestBase
{

    static
    {
        // use different components
        descriptorFile = "/integration_test_inject_optional.xml";

        // uncomment to enable debugging of this test class
        // paxRunnerVmOption = DEBUG_VM_OPTION;
    }

    static final String SINGLE_REFERENCE1 = "SingleReference1";
    static final String SINGLE_REFERENCE2 = "SingleReference2";

    private void doTest(Mode mode) throws Exception
    {
        final String componentname = mode.name();
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(componentname,
            mode.getInitialState());
        assertEquals(mode.getInitCount(), cc.description.init);

        InjectOptionalComponent cmp1 = null;
        if (!mode.isMandatory())
        {
            cmp1 = this.getServiceFromConfiguration(cc, InjectOptionalComponent.class);
            cmp1.checkMode(mode, null);
        }

        ComponentConfigurationDTO ref1DTO = getDisabledConfigurationAndEnable(
            SINGLE_REFERENCE1,
            mode.isDynamic() && !mode.isMandatory()//
                ? ComponentConfigurationDTO.ACTIVE
                : ComponentConfigurationDTO.SATISFIED);
        ConstructorSingleReference ref1Service = getServiceFromConfiguration(ref1DTO,
            ConstructorSingleReference.class);

        cc = findComponentConfigurationByName(componentname, mode.getSecondState());
        InjectOptionalComponent cmp2 = this.getServiceFromConfiguration(cc,
            InjectOptionalComponent.class);

        if (!mode.isMandatory())
        {
            assertEquals(cmp1, cmp2);
        }

        if (mode.isMandatory() || mode.isDynamic())
        {
            cmp2.checkMode(mode, ref1Service);
        }
        else
        {
            cmp2.checkMode(mode, null);
        }

        ComponentConfigurationDTO ref2DTO = getDisabledConfigurationAndEnable(
            SINGLE_REFERENCE2, ComponentConfigurationDTO.SATISFIED);

        ConstructorSingleReference ref2Service = getServiceFromConfiguration(ref2DTO,
            ConstructorSingleReference.class);

        if (mode.isMandatory() || mode.isDynamic())
        {
            cmp2.checkMode(mode, ref1Service);
        }
        else
        {
            cmp2.checkMode(mode, null);
        }

        disableAndCheck(ref1DTO);

        if (mode.isDynamic())
        {
            cmp2.checkMode(mode, ref2Service);
        }
        else if (mode.isMandatory())
        {
            cc = findComponentConfigurationByName(componentname,
                ComponentConfigurationDTO.SATISFIED);
            InjectOptionalComponent cmp3 = this.getServiceFromConfiguration(cc,
                InjectOptionalComponent.class);
            cmp3.checkMode(mode, ref2Service);
        }
        else // is optional
        {
            cmp2.checkMode(mode, null);
        }

        disableAndCheck(cc);

        cc = getDisabledConfigurationAndEnable(componentname,
            ComponentConfigurationDTO.SATISFIED);
        assertEquals(mode.getInitCount(), cc.description.init);
        cmp1 = this.getServiceFromConfiguration(cc, InjectOptionalComponent.class);

        cmp1.checkMode(mode, ref2Service);

        disableAndCheck(cc);
    }

    @Test
    public void test_field_static_optional() throws Exception
    {
        doTest(Mode.FIELD_STATIC_OPTIONAL);
    }

    @Test
    public void test_field_dynamic_optional() throws Exception
    {
        doTest(Mode.FIELD_DYNAMIC_OPTIONAL);
    }

    @Test
    public void test_field_static_mandatory() throws Exception
    {
        doTest(Mode.FIELD_STATIC_MANDATORY);
    }

    @Test
    public void test_field_dynamic_mandatory() throws Exception
    {
        doTest(Mode.FIELD_DYNAMIC_MANDATORY);
    }

    @Test
    public void test_constructor_mandatory() throws Exception
    {
        doTest(Mode.CONSTRUCTOR_MANDATORY);
    }

    @Test
    public void test_constructor_optional() throws Exception
    {
        doTest(Mode.CONSTRUCTOR_OPTIONAL);
    }
}

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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.apache.felix.scr.integration.components.ConstructorComponent;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;

import junit.framework.TestCase;


@RunWith(PaxExam.class)
public class ComponentConstructorTest extends ComponentTestBase
{

    static
    {
        // use different components
        descriptorFile = "/integration_test_constructor.xml";

        // uncomment to enable debugging of this test class
        // paxRunnerVmOption = DEBUG_VM_OPTION;
    }

    static final String SINGLE_REFERENCE = "SingleReference";
    static final String MULTI_REFERENCE_A = "MultiReferenceA";
    static final String MULTI_REFERENCE_B = "MultiReferenceB";
    static final String MULTI_REFERENCE_C = "MultiReferenceC";

    @Test
    public void test_constructor_success() throws Exception
    {
        final String componentname = "ConstructorComponent.satisfied";

        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(componentname, ComponentConfigurationDTO.SATISFIED);
        assertEquals(4, cc.description.init);

        ConstructorComponent cmp = this.getServiceFromConfiguration(cc, ConstructorComponent.class);

        final String msg = cmp.test();
        assertNull(msg);
        disableAndCheck( cc );
    }

    protected <S> void failGetServiceFromConfiguration(ComponentConfigurationDTO dto, Class<S> clazz)
    {
        long id = dto.id;
        String filter = "(component.id=" + id + ")";
        Collection<ServiceReference<S>> srs;
        try
        {
            srs = bundleContext.getServiceReferences( clazz, filter );
            Assert.assertEquals( "Nothing for filter: " + filter, 1, srs.size() );
            ServiceReference<S> sr = srs.iterator().next();
            assertNull(bundleContext.getService( sr ));
        }
        catch ( InvalidSyntaxException e )
        {
            TestCase.fail( e.getMessage() );
        }
    }

    @Test
    public void test_constructor_failure() throws Exception
    {
        final String componentname = "ConstructorComponent.unsatisfied";

        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(componentname, ComponentConfigurationDTO.SATISFIED);
        assertEquals(0, cc.description.init);

        this.failGetServiceFromConfiguration(cc, ConstructorComponent.class);

        disableAndCheck( cc );
    }

    @Test
    public void test_constructor_singleRef() throws Exception
    {
        final String componentname = "ConstructorComponent.refsingle";

        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(componentname,
            ComponentConfigurationDTO.UNSATISFIED_REFERENCE);
        assertEquals(1, cc.description.init);

        getDisabledConfigurationAndEnable(SINGLE_REFERENCE,
            ComponentConfigurationDTO.SATISFIED);

        cc = findComponentConfigurationByName(componentname,
            ComponentConfigurationDTO.SATISFIED);
        assertEquals(1, cc.description.init);
        ConstructorComponent cmp = this.getServiceFromConfiguration(cc, ConstructorComponent.class);

        final String msg = cmp.test();
        assertNull(msg);
        disableAndCheck( cc );
    }

    @Test
    public void test_constructor_singleRefOptional() throws Exception
    {

        final String componentname = "ConstructorComponent.refsingleoptional";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(componentname,
            ComponentConfigurationDTO.SATISFIED);
        assertEquals(1, cc.description.init);

        ConstructorComponent cmp1 = this.getServiceFromConfiguration(cc,
            ConstructorComponent.class);
        final String msg1 = cmp1.test();
        assertEquals("Wrong activate message.", "ref is null", msg1);

        getDisabledConfigurationAndEnable(SINGLE_REFERENCE,
            ComponentConfigurationDTO.SATISFIED);

        cc = findComponentConfigurationByName(componentname,
            ComponentConfigurationDTO.SATISFIED);
        assertEquals(1, cc.description.init);

        ConstructorComponent cmp2 = this.getServiceFromConfiguration(cc,
            ConstructorComponent.class);

        final String msg2 = cmp2.test();
        assertNull(msg2);
        disableAndCheck(cc);
    }

    @Test
    public void test_constructor_multiRef() throws Exception
    {
        final String componentname = "ConstructorComponent.refmulti";

        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(componentname,
            ComponentConfigurationDTO.UNSATISFIED_REFERENCE);
        assertEquals(1, cc.description.init);

        // disable in order to get all the refs enabled
        disableAndCheck(cc);
        getDisabledConfigurationAndEnable(MULTI_REFERENCE_A,
            ComponentConfigurationDTO.SATISFIED);
        getDisabledConfigurationAndEnable(MULTI_REFERENCE_B,
            ComponentConfigurationDTO.SATISFIED);
        getDisabledConfigurationAndEnable(MULTI_REFERENCE_C,
            ComponentConfigurationDTO.SATISFIED);

        cc = getDisabledConfigurationAndEnable(componentname,
            ComponentConfigurationDTO.SATISFIED);
        assertEquals(1, cc.description.init);

        ConstructorComponent cmp = this.getServiceFromConfiguration(cc,
            ConstructorComponent.class);

        final String msg = cmp.test();
        assertNull(msg);
        disableAndCheck( cc );
    }

    @Test
    public void test_constructor_multiRefOptional() throws Exception
    {
        final String componentname = "ConstructorComponent.refmultioptional";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(componentname,
            ComponentConfigurationDTO.SATISFIED);
        assertEquals(1, cc.description.init);

        ConstructorComponent cmp1 = this.getServiceFromConfiguration(cc,
            ConstructorComponent.class);

        final String msg1 = cmp1.test();
        assertEquals("Wrong refs size.", "ref has size: 0", msg1);

        getDisabledConfigurationAndEnable(MULTI_REFERENCE_A,
            ComponentConfigurationDTO.SATISFIED);
        getDisabledConfigurationAndEnable(MULTI_REFERENCE_B,
            ComponentConfigurationDTO.SATISFIED);
        getDisabledConfigurationAndEnable(MULTI_REFERENCE_C,
            ComponentConfigurationDTO.SATISFIED);

        ConstructorComponent cmp2 = this.getServiceFromConfiguration(cc,
            ConstructorComponent.class);
        assertTrue("Different component instances.", cmp1 == cmp2);

        // not greedy so the collection of refs does not get updated.
        final String msg2 = cmp2.test();
        assertEquals("Wrong refs size.", "ref has size: 0", msg2);

        disableAndCheck(cc);
    }

    @Test
    public void test_constructor_multiRefOptionalGreedy() throws Exception
    {
        final String componentname = "ConstructorComponent.refmultioptionalgreedy";
        ComponentConfigurationDTO cc = getDisabledConfigurationAndEnable(componentname,
            ComponentConfigurationDTO.SATISFIED);
        assertEquals(1, cc.description.init);

        ConstructorComponent cmp1 = this.getServiceFromConfiguration(cc,
            ConstructorComponent.class);

        final String msg1 = cmp1.test();
        assertEquals("Wrong refs size.", "ref has size: 0", msg1);

        getDisabledConfigurationAndEnable(MULTI_REFERENCE_A,
            ComponentConfigurationDTO.SATISFIED);
        getDisabledConfigurationAndEnable(MULTI_REFERENCE_B,
            ComponentConfigurationDTO.SATISFIED);
        getDisabledConfigurationAndEnable(MULTI_REFERENCE_C,
            ComponentConfigurationDTO.SATISFIED);

        ConstructorComponent cmp2 = this.getServiceFromConfiguration(cc,
            ConstructorComponent.class);

        assertFalse("Same component instances.", cmp1 == cmp2);

        // greedy so the collection of refs does should be updated.
        final String msg2 = cmp2.test();
        assertNull(msg2);

        disableAndCheck(cc);
    }
}

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
package org.apache.felix.scr.impl.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.felix.scr.impl.logger.MockBundleLogger;
import org.apache.felix.scr.impl.metadata.ComponentMetadata;
import org.apache.felix.scr.impl.metadata.ReferenceMetadata;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

public class XmlHandlerTest {

    @Test
    public void testPropertiesWithoutValue() throws Exception {
        final URL url = getClass().getResource("/parsertest-nopropvalue.xml");
        final List<ComponentMetadata> components = parse(url, null);
        assertEquals(1, components.size());

        final ComponentMetadata cm = components.get(0);
        cm.validate();
        // the xml has four properties, two of them with no value, so they should not be part of the
        // component metadata
        assertEquals(2, cm.getProperties().size());
        assertNotNull(cm.getProperties().get("service.vendor"));
        assertNotNull(cm.getProperties().get("jmx.objectname"));
    }

    @Test
    public void testNoTrueCondition() throws Exception
    {
        final URL url = getClass().getResource("/parsertest-nopropvalue.xml");
        final List<ComponentMetadata> components = parse(url, null);
        assertEquals(1, components.size());

        final ComponentMetadata cm = components.get(0);
        cm.validate();
        List<ReferenceMetadata> dependencies = cm.getDependencies();
        assertEquals("Wrong number of dependencies.", 4, dependencies.size());
    }

    @Test
    public void testAvailableTrueCondition() throws Exception
    {
        final URL url = getClass().getResource("/parsertest-nopropvalue.xml");
        final List<ComponentMetadata> components = parse(url,
            Mockito.mock(ServiceReference.class));
        assertEquals(1, components.size());

        final ComponentMetadata cm = components.get(0);
        cm.validate();
        List<ReferenceMetadata> dependencies = cm.getDependencies();
        assertEquals("Wrong number of dependencies.", 5, dependencies.size());
        ReferenceMetadata trueDependency = dependencies.get(dependencies.size() - 1);
        assertEquals("Wrong name.", "osgi.ds.satisfying.condition",
            trueDependency.getName());
        assertEquals("Wrong interface.", "org.osgi.service.condition.Condition",
            trueDependency.getInterface());
        assertEquals("Wrong policy.", "dynamic", trueDependency.getPolicy());
        assertEquals("Wrong target.", "(osgi.condition.id=true)",
            trueDependency.getTarget());
    }

    @Test
    public void testSatisfyingConditionSpecified() throws Exception
    {
        final URL url = getClass().getResource("/satisfying-condition-specified.xml");
        final List<ComponentMetadata> components = parse(url,
            Mockito.mock(ServiceReference.class));
        assertEquals(1, components.size());

        final ComponentMetadata cm = components.get(0);
        cm.validate();
        List<ReferenceMetadata> dependencies = cm.getDependencies();
        assertEquals("Wrong number of dependencies.", 1, dependencies.size());
        ReferenceMetadata trueDependency = dependencies.get(dependencies.size() - 1);
        assertEquals("Wrong name.", "osgi.ds.satisfying.condition",
            trueDependency.getName());
        assertEquals("Wrong interface.", "org.osgi.service.condition.Condition",
            trueDependency.getInterface());
        assertEquals("Wrong policy.", "dynamic", trueDependency.getPolicy());
        assertEquals("Wrong target.", "(foo=bar)",
            trueDependency.getTarget());
    }

    private List<ComponentMetadata> parse(final URL descriptorURL,
        ServiceReference<?> trueCondition) throws Exception
    {
        final Bundle bundle = Mockito.mock(Bundle.class);
        Mockito.when(bundle.getLocation()).thenReturn("bundle");

        InputStream stream = null;
        try {
            stream = descriptorURL.openStream();

            XmlHandler handler = new XmlHandler(bundle, new MockBundleLogger(), false,
                false, trueCondition);
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            final SAXParser parser = factory.newSAXParser();

            parser.parse(stream, handler);

            return handler.getComponentMetadataList();
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignore) {
                }
            }
        }

    }

    @Test
    public void testRetentionPolicyRetain() throws Exception
    {
        final URL url = getClass().getResource("/retention-policy-retain.xml");
        final List<ComponentMetadata> components = parse(url, null);
        assertEquals(1, components.size());

        final ComponentMetadata cm = components.get(0);
        cm.validate();
        assertEquals("Component should have delayedKeepInstances set to true for retention-policy=retain",
                     true, cm.isDelayedKeepInstances());
    }

    @Test
    public void testRetentionPolicyDiscard() throws Exception
    {
        final URL url = getClass().getResource("/retention-policy-discard.xml");
        final List<ComponentMetadata> components = parse(url, null);
        assertEquals(1, components.size());

        final ComponentMetadata cm = components.get(0);
        cm.validate();
        assertEquals("Component should have delayedKeepInstances set to false for retention-policy=discard",
                     false, cm.isDelayedKeepInstances());
    }
}

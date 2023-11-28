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
package org.apache.felix.configurator.impl;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Vector;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

public class ConfiguratorTest {

    private Configurator configurator;

    private BundleContext bundleContext;

    private Bundle bundle;

    private ConfigurationAdmin configurationAdmin;

    private ServiceReference<ConfigurationAdmin> caRef;

    @SuppressWarnings("unchecked")
    @Before public void setup() throws IOException {
        bundle = mock(Bundle.class);
        when(bundle.getBundleId()).thenReturn(42L);
        when(bundle.getState()).thenReturn(Bundle.ACTIVE);
        bundleContext = mock(BundleContext.class);
        when(bundle.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(bundleContext.getBundle(Constants.SYSTEM_BUNDLE_LOCATION)).thenReturn(bundle);
        when(bundleContext.getBundle(42)).thenReturn(bundle);
        when(bundleContext.getBundles()).thenReturn(new Bundle[0]);
        when(bundleContext.getDataFile("binaries" + File.separatorChar + ".check")).thenReturn(Files.createTempDirectory("test").toFile());
        caRef = mock(ServiceReference.class);
        when(caRef.getBundle()).thenReturn(bundle);

        configurationAdmin = mock(ConfigurationAdmin.class);
        when(bundleContext.getService(caRef)).thenReturn(configurationAdmin);

        configurator = new Configurator(bundleContext, Collections.singletonList(caRef));
    }

    private Bundle setupBundle(final long id) throws Exception {
        return setupBundle(id, String.valueOf(id));
    }

    private long lastModified = 1;

    private Bundle setupBundle(final long id, final String jsonName) throws Exception {
        final Bundle b = mock(Bundle.class);
        when(b.getBundleId()).thenReturn(id);
        when(b.getLastModified()).thenReturn(lastModified);
        lastModified++;
        when(b.getState()).thenReturn(Bundle.ACTIVE);
        final BundleWiring wiring = mock(BundleWiring.class);
        when(b.adapt(BundleWiring.class)).thenReturn(wiring);
        final BundleRequirement req = mock(BundleRequirement.class);
        when(wiring.getRequirements(Util.NS_OSGI_EXTENDER)).thenReturn(Collections.singletonList(req));
        final BundleWire wire = mock(BundleWire.class);
        when(wire.getProviderWiring()).thenReturn(wiring);
        when(wire.getRequirement()).thenReturn(req);
        when(wiring.getBundle()).thenReturn(bundle);
        when(wiring.getRequiredWires(Util.NS_OSGI_EXTENDER)).thenReturn(Collections.singletonList(wire));
        final Vector<URL> urls = new Vector<>();
        urls.add(this.getClass().getResource("/bundles/" + jsonName + ".json"));
        when(b.findEntries("OSGI-INF/configurator", "*.json", false)).thenReturn(urls.elements());

        final BundleContext bContext = mock(BundleContext.class);
        when(b.getBundleContext()).thenReturn(bContext);
        when(bContext.getServiceReferences(ConfigurationAdmin.class, null)).thenReturn(Collections.singleton(caRef));
        when(bundleContext.getBundle(id)).thenReturn(b);
        return b;
    }
    
    @Test public void testUpdateForceWithoutBundleChanges() throws Exception {
        final Bundle bV1 = setupBundle(3, "3");
        final Bundle bV2 = setupBundle(3, "3");
        when(bV2.getLastModified()).thenReturn(lastModified);
        lastModified++;

        Configuration a = mock(Configuration.class);
        when(configurationAdmin.getConfiguration("a", "?")).thenReturn(a);

        when(a.getChangeCount()).thenReturn(1L);
        configurator.processAddBundle(bV1);
        configurator.process();

        when(configurationAdmin.listConfigurations("(" + Constants.SERVICE_PID + "=a)"))
                .thenReturn(new Configuration[] { a });

        final Dictionary<String, Object> aProps = new Hashtable<>();
        aProps.put("foo", "foo");
        verify(a).updateIfDifferent(aProps);

        final Dictionary<String, Object> modifiedOutside = new Hashtable<>();
        modifiedOutside.put("foo", "bar");
        when(a.getProperties()).thenReturn(modifiedOutside);

        when(a.getChangeCount()).thenReturn(2L);

        verify(a, times(1)).updateIfDifferent(aProps);

        configurator.processAddBundle(bV2);
        configurator.process();
        verify(a, times(2)).updateIfDifferent(aProps);
    }

    @Test public void testSimpleAddRemove() throws Exception {
        final Bundle b = setupBundle(1);

        Configuration c1 = mock(Configuration.class);
        Configuration c2 = mock(Configuration.class);
        when(configurationAdmin.getConfiguration("a", "?")).thenReturn(c1);
        when(configurationAdmin.getConfiguration("b", "?")).thenReturn(c2);

        when(c1.getChangeCount()).thenReturn(1L);
        when(c2.getChangeCount()).thenReturn(1L);
        configurator.processAddBundle(b);

        configurator.process();

        when(configurationAdmin.listConfigurations("(" + Constants.SERVICE_PID + "=a)")).thenReturn(new Configuration[] {c1});
        when(configurationAdmin.listConfigurations("(" + Constants.SERVICE_PID + "=b)")).thenReturn(new Configuration[] {c2});

        final Dictionary<String, Object> props1 = new Hashtable<>();
        props1.put("foo", "bar");
        verify(c1).updateIfDifferent(props1);
        final Dictionary<String, Object> props2 = new Hashtable<>();
        props2.put("x", "y");
        verify(c2).updateIfDifferent(props2);

        configurator.processRemoveBundle(1);
        configurator.process();

        verify(c1).delete();
        verify(c2).delete();
    }

    @Test public void testSimpleAddUpdate() throws Exception {
        final Bundle bV1 = setupBundle(2);
        final Bundle bV2 = setupBundle(2, "2a");

        // initial - no configuration
        Configuration c1 = mock(Configuration.class);
        Configuration c2 = mock(Configuration.class);
        Configuration c3 = mock(Configuration.class);
        when(c1.getChangeCount()).thenReturn(1L);
        when(c2.getChangeCount()).thenReturn(1L);
        when(c3.getChangeCount()).thenReturn(1L);
        when(configurationAdmin.getConfiguration("a", "?")).thenReturn(c1);
        when(configurationAdmin.getConfiguration("b", "?")).thenReturn(c2);
        when(configurationAdmin.getConfiguration("c", "?")).thenReturn(c3);

        configurator.processAddBundle(bV1);
        configurator.process();

        final Dictionary<String, Object> props1a = new Hashtable<>();
        props1a.put("foo", "bar2");
        final Dictionary<String, Object> props1b = new Hashtable<>();
        props1b.put("x", "y2");
        final Dictionary<String, Object> props1c = new Hashtable<>();
        props1c.put("c", "1");

        InOrder inorder = inOrder(c1,c2,c3);
        inorder.verify(c1).updateIfDifferent(props1a);
        inorder.verify(c1).getChangeCount();
        inorder.verify(c2).updateIfDifferent(props1b);
        inorder.verify(c2).getChangeCount();
        inorder.verify(c3).updateIfDifferent(props1c);
        inorder.verify(c3).getChangeCount();
        inorder.verifyNoMoreInteractions();

        // c1 and c2 are changed manually -> increase change count, c3 is not changed manually
        when(c1.getChangeCount()).thenReturn(2L);
        when(c2.getChangeCount()).thenReturn(2L);
        when(configurationAdmin.listConfigurations("(service.pid=a)")).thenReturn(new Configuration[] {c1});
        when(configurationAdmin.listConfigurations("(service.pid=b)")).thenReturn(new Configuration[] {c2});
        when(configurationAdmin.listConfigurations("(service.pid=c)")).thenReturn(new Configuration[] {c3});

        configurator.processAddBundle(bV2);
        configurator.process();

        final Dictionary<String, Object> props2a = new Hashtable<>();
        props2a.put("foo", "bar3");
        final Dictionary<String, Object> props2c = new Hashtable<>();
        props2c.put("c", "2");

        inorder = inOrder(c1, c2, c3);
        inorder.verify(c1).updateIfDifferent(props2a);
        inorder.verify(c1).getChangeCount();
        inorder.verify(c2).getChangeCount();
        inorder.verify(c3).updateIfDifferent(props2c);
        inorder.verify(c3).getChangeCount();
        inorder.verifyNoMoreInteractions();
    }

    @Test public void testSimpleRankingRemove() throws Exception {
        final Bundle b1 = setupBundle(1);
        final Bundle b2 = setupBundle(2);

        Configuration c1 = mock(Configuration.class);
        Configuration c2 = mock(Configuration.class);
        Configuration c3 = mock(Configuration.class);
        when(configurationAdmin.getConfiguration("a", "?")).thenReturn(c1);
        when(configurationAdmin.getConfiguration("b", "?")).thenReturn(c2);
        when(configurationAdmin.getConfiguration("c", "?")).thenReturn(c3);

        when(c1.getChangeCount()).thenReturn(1L);
        when(c2.getChangeCount()).thenReturn(1L);
        when(c3.getChangeCount()).thenReturn(1L);
        configurator.processAddBundle(b2);
        configurator.process();

        when(configurationAdmin.listConfigurations("(" + Constants.SERVICE_PID + "=a)")).thenReturn(new Configuration[] {c1});
        when(configurationAdmin.listConfigurations("(" + Constants.SERVICE_PID + "=b)")).thenReturn(new Configuration[] {c2});
        when(configurationAdmin.listConfigurations("(" + Constants.SERVICE_PID + "=c)")).thenReturn(new Configuration[] {c3});

        final Dictionary<String, Object> props1 = new Hashtable<>();
        props1.put("foo", "bar2");
        final Dictionary<String, Object> props2 = new Hashtable<>();
        props2.put("x", "y2");

        configurator.processAddBundle(b1);
        configurator.process();

        final Dictionary<String, Object> props3 = new Hashtable<>();
        props3.put("foo", "bar");
        final Dictionary<String, Object> props4 = new Hashtable<>();
        props4.put("x", "y");

        configurator.processRemoveBundle(1);
        configurator.process();

        configurator.processRemoveBundle(2);
        configurator.process();

        InOrder inorder = inOrder(c1, c2);
        inorder.verify(c1).updateIfDifferent(props1);
        inorder.verify(c2).updateIfDifferent(props2);
        inorder.verify(c1).updateIfDifferent(props3);
        inorder.verify(c2).updateIfDifferent(props4);
        inorder.verify(c1).updateIfDifferent(props1);
        inorder.verify(c2).updateIfDifferent(props2);
        inorder.verify(c1).delete();
        inorder.verify(c2).delete();
        inorder.verifyNoMoreInteractions();
    }
}

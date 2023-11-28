/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.felix.hc.generalchecks.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;

public class ScriptEnginesTrackerTest {

    private static Class<?> SCRIPT_ENGINE_FACTORY = DummyScriptEngineFactory.class;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testBundledScriptEngineFactory() throws Exception {
        final URL url = createFactoryFile().toURI().toURL();
        Bundle bundle = mock(Bundle.class);
        when(bundle.getBundleId()).thenReturn(1L);
        when(bundle.loadClass(SCRIPT_ENGINE_FACTORY.getName())).thenReturn((Class)SCRIPT_ENGINE_FACTORY);
        when(bundle.findEntries(ScriptEnginesTracker.META_INF_SERVICES, ScriptEnginesTracker.FACTORY_NAME, false)).thenReturn(Collections.enumeration(Collections.singleton(url)));

        // simulate a bundle starting that declares a new ScriptEngineFactory
        BundleEvent bundleEvent = new BundleEvent(BundleEvent.STARTED, bundle);
        ScriptEnginesTracker scriptEngineTracker = new ScriptEnginesTracker(); // context.getService(ScriptEnginesTracker.class);
        assertNotNull("Expected that the ScriptEnginesTracker would already be registered.", scriptEngineTracker);
        scriptEngineTracker.bundleChanged(bundleEvent);
        Map<Bundle, List<String>> languagesByBundle = scriptEngineTracker.getLanguagesByBundle();
        AtomicInteger factoriesSize = new AtomicInteger(0);
        languagesByBundle.values().stream()
            .forEach(l -> factoriesSize.addAndGet(l.size()));
        int expectedScriptEngineFactories = 1;
        assertEquals("Expected " + expectedScriptEngineFactories + " ScriptEngineFactories.", expectedScriptEngineFactories, factoriesSize.intValue());
        List<String> factoriesForBundle = languagesByBundle.get(bundle);
        assertEquals(1, factoriesForBundle.size());
        assertEquals("dummy", factoriesForBundle.get(0));
        assertEquals("Dummy Scripting Engine", scriptEngineTracker.getEngineByLanguage("dummy").getFactory().getEngineName());

        // simulate a bundle stopping that previously declared a new ScriptEngineFactory
        bundleEvent = new BundleEvent(BundleEvent.STOPPED, bundle);
        scriptEngineTracker.bundleChanged(bundleEvent);
        expectedScriptEngineFactories--;

        factoriesSize.set(0);
        languagesByBundle = scriptEngineTracker.getLanguagesByBundle();
        assertNotNull(languagesByBundle);
        languagesByBundle.values().stream()
            .forEach(l -> factoriesSize.addAndGet(l.size()));
        assertFalse(languagesByBundle.containsKey(bundle));
        assertEquals("Expected " + expectedScriptEngineFactories + " ScriptEngineFactory.", expectedScriptEngineFactories, factoriesSize.intValue());
        assertNull("Did not expect references to the already unregistered DummyScriptEngineFactory", 
                scriptEngineTracker.getEngineByLanguage("dummy"));
    }

    private File createFactoryFile() throws IOException {
        File tempFile = File.createTempFile("scriptEngine", "tmp");
        tempFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write("#I'm a test-comment\n".getBytes());
            fos.write(SCRIPT_ENGINE_FACTORY.getName().getBytes());
        }
        return tempFile;
    }

}

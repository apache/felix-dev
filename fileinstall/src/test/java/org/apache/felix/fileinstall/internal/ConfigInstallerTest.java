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
package org.apache.felix.fileinstall.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicReference;

import junit.framework.TestCase;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IArgumentMatcher;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.Configuration.ConfigurationAttribute;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;

/**
 * Tests for ConfigInstaller
 */
public class ConfigInstallerTest extends TestCase {

    BundleContext mockBundleContext;
    Bundle mockBundle;
    ConfigurationAdmin mockConfigurationAdmin;
    Configuration mockConfiguration;

    protected void setUp() throws Exception
    {
        super.setUp();
        mockBundleContext = EasyMock.createMock(BundleContext.class);
        mockBundle = EasyMock.createMock(Bundle.class);
        mockConfigurationAdmin = EasyMock.createMock(ConfigurationAdmin.class);
        mockConfiguration = EasyMock.createMock(Configuration.class);
    }


    public void testParsePidWithoutFactoryPid()
    {
        ConfigInstaller ci = new ConfigInstaller(null, null, null);

        String path = "pid.cfg";
        assertEquals( "Pid without Factory Pid calculated", "pid", ci.parsePid( path )[0] );
        assertEquals( "Pid without Factory Pid calculated", null, ci.parsePid( path )[1] );
    }


    public void testParsePidWithFactoryPid()
    {
        ConfigInstaller ci = new ConfigInstaller(null, null, null);

        String path = "factory-pid.cfg";
        assertEquals( "Pid with Factory Pid calculated", "factory", ci.parsePid( path )[0] );
        assertEquals( "Pid with Factory Pid calculated", "pid", ci.parsePid( path )[1] );
    }

    public void testTypedConfiguration() throws Exception
    {
        File file = File.createTempFile("test", ".config");
        try (OutputStream os = new FileOutputStream(file)) {
            os.write("networkInterface=\"wlp3s0\"\n".getBytes("UTF-8"));
        }
        String pid = file.getName().substring(0, file.getName().indexOf(".config"));

        Capture<Dictionary<String, Object>> props = new Capture<>();
        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                .andReturn(null);
        EasyMock.expect(mockConfigurationAdmin.getConfiguration(pid, "?"))
                .andReturn(mockConfiguration);
        EasyMock.expect(mockConfiguration.getAttributes()).andReturn(Collections.emptySet()).times(2);
        EasyMock.expect(mockConfiguration.getPid()).andReturn(pid);
        EasyMock.expect(mockConfiguration.getProperties())
                .andReturn(null);
        EasyMock.expect(mockBundleContext.getProperty((String) EasyMock.anyObject()))
                .andReturn(null)
                .anyTimes();
        EasyMock.expect(mockConfiguration.updateIfDifferent(EasyMock.capture(props))).andReturn(true);
        EasyMock.expectLastCall();
        EasyMock.replay(mockConfiguration, mockConfigurationAdmin, mockBundleContext, mockBundle);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );
        ci.install(file);

        EasyMock.verify(mockConfiguration, mockConfigurationAdmin, mockBundleContext);
        Dictionary<String, Object> loaded = props.getValue();
        assertNotNull(loaded);
        assertEquals("wlp3s0", loaded.get("networkInterface"));
    }

    public void testTypedConfigurationFloat() throws Exception
    {
        File file = File.createTempFile("test", ".config");
        try (OutputStream os = new FileOutputStream(file)) {
            os.write("key=F\"1137191584\"\n".getBytes("UTF-8"));
        }
        String pid = file.getName().substring(0, file.getName().indexOf(".config"));

        Capture<Dictionary<String, Object>> props = new Capture<>();
        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                .andReturn(null);
        EasyMock.expect(mockConfigurationAdmin.getConfiguration(pid, "?"))
                .andReturn(mockConfiguration);
        EasyMock.expect(mockConfiguration.getAttributes())
            .andReturn(Collections.emptySet()).times(2);
        EasyMock.expect(mockConfiguration.getProperties())
                .andReturn(null);
        EasyMock.expect(mockConfiguration.getPid()).andReturn(pid);
        EasyMock.expect(mockBundleContext.getProperty((String) EasyMock.anyObject()))
                .andReturn(null)
                .anyTimes();
        EasyMock.expect(mockConfiguration.updateIfDifferent(EasyMock.capture(props))).andReturn(true);
        EasyMock.expectLastCall();
        EasyMock.replay(mockConfiguration, mockConfigurationAdmin, mockBundleContext, mockBundle);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );
        ci.install(file);

        EasyMock.verify(mockConfiguration, mockConfigurationAdmin, mockBundleContext);
        Dictionary<String, Object> loaded = props.getValue();
        assertNotNull(loaded);
        assertEquals(400.333F, loaded.get("key"));
    }

    public void testGetNewFactoryConfiguration() throws Exception
    {
        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                    .andReturn(null);
        EasyMock.expect(mockConfigurationAdmin.getFactoryConfiguration( "pid", "factoryPid", "?" ))
                    .andReturn(mockConfiguration);
        EasyMock.replay(mockConfiguration, mockConfigurationAdmin, mockBundleContext, mockBundle);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );

        assertEquals( "Factory configuration retrieved", mockConfiguration, ci.getConfiguration( "pid-factoryPid.cfg", "pid", "factoryPid" ) );

        EasyMock.verify(mockConfiguration, mockConfigurationAdmin, mockBundleContext);
    }


    public void testGetExistentFactoryConfiguration() throws Exception
    {
        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                        .andReturn(null);
        EasyMock.expect(mockConfigurationAdmin.getFactoryConfiguration( "pid", "factoryPid", "?" ))
                        .andReturn(mockConfiguration);
        EasyMock.replay(mockConfiguration, mockConfigurationAdmin, mockBundleContext, mockBundle);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );

        assertEquals( "Factory configuration retrieved", mockConfiguration, ci.getConfiguration( "pid-factoryPid.cfg","pid", "factoryPid" ) );

        EasyMock.verify(mockConfiguration, mockConfigurationAdmin, mockBundleContext);
    }


    public void testGetExistentNoFactoryConfiguration() throws Exception
    {
        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                        .andReturn(null);
        EasyMock.expect(mockConfigurationAdmin.getConfiguration( "pid", "?" ))
                        .andReturn(mockConfiguration);
        EasyMock.replay(mockConfiguration, mockConfigurationAdmin, mockBundleContext, mockBundle);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );

        assertEquals( "Factory configuration retrieved", mockConfiguration, ci.getConfiguration( "pid.cfg", "pid", null ) );

        EasyMock.verify(mockConfiguration, mockConfigurationAdmin, mockBundleContext);
    }


    public void testDeleteConfig() throws Exception
    {
        mockConfiguration.delete();
        EasyMock.expect(mockConfiguration.getPid()).andReturn("pid");
        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockBundleContext.getProperty((String) EasyMock.anyObject()))
            .andReturn(null)
            .anyTimes();
        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                        .andReturn(null);
        EasyMock.expect(mockConfigurationAdmin.getConfiguration("pid", "?" ))
                        .andReturn(mockConfiguration);
        EasyMock.replay(mockConfiguration, mockConfigurationAdmin, mockBundleContext, mockBundle);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );

        assertTrue( ci.deleteConfig( new File( "pid.cfg" ) ) );

        EasyMock.verify(mockConfiguration, mockConfigurationAdmin, mockBundleContext);
    }

    public void testCreateConfigAndObserveCMDeleted() throws Exception
    {
        File file = File.createTempFile("test", ".config");
        try (OutputStream os = new FileOutputStream(file)) {
            os.write("key=F\"1137191584\"\n".getBytes("UTF-8"));
        }
        String pid = file.getName().substring(0, file.getName().indexOf(".config"));

        final Capture<Dictionary<String, Object>> props = new Capture<>();

        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockConfiguration.getAttributes()).andReturn(Collections.emptySet()).times(2);
        EasyMock.expect(mockConfiguration.getProperties())
                .andReturn(null);
        EasyMock.expect(mockBundleContext.getProperty((String) EasyMock.anyObject()))
                .andReturn(null)
                .anyTimes();
        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                .andReturn(null);
        EasyMock.expect(mockConfigurationAdmin.getConfiguration(pid, "?"))
                .andReturn(mockConfiguration);

        ServiceReference<ConfigurationAdmin> sr = EasyMock.createMock(ServiceReference.class);
        EasyMock.expect(mockConfiguration.updateIfDifferent(EasyMock.capture(props))).andReturn(true);
        EasyMock.expectLastCall();

        EasyMock.expect(mockConfigurationAdmin.getConfiguration(pid, "?"))
                .andReturn(mockConfiguration);
        EasyMock.expect(mockConfiguration.getProperties())
                .andAnswer(new IAnswer<Dictionary<String, Object>>() {
                    @Override
                    public Dictionary<String, Object> answer() throws Throwable {
                        return props.getValue();
                    }
                });
        EasyMock.expect(mockConfiguration.getPid())
                .andReturn(pid).times(2);

        EasyMock.replay(mockConfiguration, mockConfigurationAdmin, mockBundleContext, mockBundle, sr);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );
        ci.install(file);

        ci.doConfigurationEvent( new ConfigurationEvent(sr , ConfigurationEvent.CM_UPDATED, null, pid ) );

        ci.doConfigurationEvent( new ConfigurationEvent(sr , ConfigurationEvent.CM_DELETED, null, pid ) );

        assertFalse("Configuration file should be deleted", file.isFile());
    }

    public void testUseExistingConfigAndObserveCMDeleted() throws Exception
    {
        String pid = "test";

        Capture<Dictionary<String, Object>> props = new Capture<>();

        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockConfiguration.getProperties())
                .andReturn(null);
        EasyMock.expect(mockBundleContext.getProperty((String) EasyMock.anyObject()))
                .andReturn(null)
                .anyTimes();
        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                .andReturn(null);
        EasyMock.expect(mockConfigurationAdmin.getConfiguration(pid, "?"))
                .andReturn(mockConfiguration);

        ServiceReference<ConfigurationAdmin> sr = EasyMock.createMock(ServiceReference.class);
        mockConfiguration.update(EasyMock.capture(props));
        EasyMock.expectLastCall();

        EasyMock.replay(mockConfiguration, mockConfigurationAdmin, mockBundleContext, mockBundle, sr);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );

        ci.doConfigurationEvent( new ConfigurationEvent(sr , ConfigurationEvent.CM_UPDATED, null, pid ) );
        ci.doConfigurationEvent( new ConfigurationEvent(sr , ConfigurationEvent.CM_DELETED, null, pid ) );
    }

    public void testUseExistingConfigWithFileinstallFilenameAndObserveCMDeleted() throws Exception
    {
        File file = File.createTempFile("test", ".config");
        try (OutputStream os = new FileOutputStream(file)) {
            os.write("key=F\"1137191584\"\n".getBytes("UTF-8"));
        }
        String pid = "test";

        Capture<Dictionary<String, Object>> propsCapture = new Capture<>();
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(DirectoryWatcher.FILENAME, file.toURI().toString());

        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockConfiguration.getProperties())
                .andReturn(props);
        EasyMock.expect(mockBundleContext.getProperty((String) EasyMock.anyObject()))
                .andReturn(null)
                .anyTimes();
        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                .andReturn(null);
        EasyMock.expect(mockConfigurationAdmin.getConfiguration(pid, "?"))
                .andReturn(mockConfiguration);
        EasyMock.expect(mockConfiguration.getPid())
                .andReturn(pid);

        ServiceReference<ConfigurationAdmin> sr = EasyMock.createMock(ServiceReference.class);
        mockConfiguration.update(EasyMock.capture(propsCapture));
        EasyMock.expectLastCall();

        EasyMock.replay(mockConfiguration, mockConfigurationAdmin, mockBundleContext, mockBundle, sr);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );

        ci.doConfigurationEvent( new ConfigurationEvent(sr , ConfigurationEvent.CM_UPDATED, null, pid ) );
        ci.doConfigurationEvent( new ConfigurationEvent(sr , ConfigurationEvent.CM_DELETED, null, pid ) );

        assertFalse("Configuration file should be deleted", file.isFile());
    }

    public void testDoConfigurationEventSavesUpdatedConfigurationWhenUsingCachingPersistence() throws Exception
    {
        final File file = File.createTempFile("test", ".config");
        try (OutputStream os = new FileOutputStream(file)) {
            os.write("networkInterface = \"wlp3s1\"\n".getBytes("UTF-8"));
        }

        String pid = file.getName().substring(0, file.getName().indexOf(".config"));

        ServiceReference<ConfigurationAdmin> sr = EasyMock.createMock(ServiceReference.class);

        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockBundleContext.getProperty((String) EasyMock.anyObject()))
                .andReturn(null)
                .anyTimes();

        final Configuration cachingPersistenceConfiguration = EasyMock.createMock(Configuration.class);
        EasyMock.expect(cachingPersistenceConfiguration.getAttributes()).andReturn(Collections.emptySet()).times(2);

        final Dictionary<String, Object> cachedProps = new Hashtable<String, Object>() {
            {
                put("networkInterface", "wlp3s0");
                put(DirectoryWatcher.FILENAME, file.toURI().toString());
            }
        };

        EasyMock.expect(cachingPersistenceConfiguration.getProperties())
                .andReturn(cachedProps)
                .anyTimes();

        EasyMock.expect(cachingPersistenceConfiguration.getPid())
                .andReturn(pid).times(2);

        EasyMock.expect(mockConfigurationAdmin.getConfiguration(pid, "?"))
                .andReturn(cachingPersistenceConfiguration)
                .anyTimes();

        EasyMock.expect(mockConfigurationAdmin.getConfiguration(pid, null))
                .andReturn(cachingPersistenceConfiguration)
                .anyTimes();

        final Configuration newConfiguration = EasyMock.createMock(Configuration.class);
        EasyMock.expect(newConfiguration.getAttributes()).andReturn(Collections.emptySet()).times(2);

        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                .andReturn(new Configuration[] { newConfiguration });

        EasyMock.expect(newConfiguration.getProperties())
                .andReturn(null);

        EasyMock.expect(newConfiguration.getPid())
                .andReturn(pid);

        final Capture<Dictionary<String, Object>> props = new Capture<>();
        EasyMock.expect(newConfiguration.updateIfDifferent(EasyMock.capture(props))).andReturn(true);

        EasyMock.expect(cachingPersistenceConfiguration.updateIfDifferent(EasyMock.capture(props)));
        EasyMock.expectLastCall()
                .andAnswer(new IAnswer<Boolean>() {
                    @Override
                    public Boolean answer() throws Throwable {

                        cachedProps.put("networkInterface", props.getValue().get("networkInterface"));

                        return Boolean.TRUE;
                   }
               }).anyTimes();

        EasyMock.replay(mockBundleContext, mockConfigurationAdmin, mockBundle, cachingPersistenceConfiguration, newConfiguration, sr);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );

        ci.install(file);

        Dictionary<String, Object> loaded = props.getValue();
        assertNotNull(loaded);
        assertEquals("wlp3s1", loaded.get("networkInterface"));

        ci.doConfigurationEvent( new ConfigurationEvent(sr , ConfigurationEvent.CM_UPDATED, null, pid ) );

        assertEquals("networkInterface = \"wlp3s1\"", Files.readAllLines( Paths.get(file.toURI()) ).get(0));
    }

    public void testSetConfiguration() throws Exception
    {
        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockBundleContext.getProperty(DirectoryWatcher.CONFIG_ENCODING)).andReturn(null);
        EasyMock.expect(mockBundleContext.getProperty(DirectoryWatcher.LOG_DEFAULT)).andReturn(null);
        EasyMock.expect(mockBundleContext.getProperty(DirectoryWatcher.LOG_LEVEL)).andReturn(null);
        EasyMock.expect(mockConfiguration.getProperties()).andReturn(new Hashtable<String, Object>());
        EasyMock.expect(mockConfiguration.getAttributes()).andReturn(Collections.emptySet()).times(2);
        EasyMock.expect(mockConfiguration.getPid()).andReturn("firstcfg");
        EasyMock.reportMatcher(new IArgumentMatcher()
        {
            public boolean matches( Object argument )
            {
                return ((Dictionary) argument).get("testkey").equals("testvalue");
            }

            public void appendTo(StringBuffer buffer)
            {
                buffer.append("<Dictionary check: testkey present?>");
            }
        } );
        EasyMock.expect(mockConfiguration.updateIfDifferent(new Hashtable<String, Object>())).andReturn(true);
        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                        .andReturn(null);
        EasyMock.expect(mockConfigurationAdmin.getConfiguration("firstcfg", "?"))
                        .andReturn(mockConfiguration);
        EasyMock.replay(mockConfiguration, mockConfigurationAdmin, mockBundleContext, mockBundle);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );

        assertTrue( ci.setConfig( new File( "src/test/resources/watched/firstcfg.cfg" ) ) );

        EasyMock.verify(mockConfiguration, mockConfigurationAdmin, mockBundleContext);
    }

    public void testShouldSaveConfig() throws Exception
    {
        final AtomicReference<Boolean> disable = new AtomicReference<Boolean>();
        final AtomicReference<Boolean> enable = new AtomicReference<Boolean>();

        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockBundleContext.getProperty(DirectoryWatcher.DISABLE_CONFIG_SAVE)).andAnswer(
                new IAnswer<String>() {
                    public String answer() throws Throwable {
                        return disable.get() != null ? disable.get().toString() : null;
                    }
                }
        ).anyTimes();
        EasyMock.expect(mockBundleContext.getProperty(DirectoryWatcher.ENABLE_CONFIG_SAVE)).andAnswer(
                new IAnswer<String>() {
                    public String answer() throws Throwable {
                        return enable.get() != null ? enable.get().toString() : null;
                    }
                }
        ).anyTimes();
        EasyMock.replay(mockConfiguration, mockConfigurationAdmin, mockBundleContext, mockBundle);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );

        disable.set(null);
        enable.set(null);
        assertTrue( ci.shouldSaveConfig() );

        disable.set(Boolean.FALSE);
        enable.set(null);
        assertFalse( ci.shouldSaveConfig() );

        disable.set(Boolean.TRUE);
        enable.set(null);
        assertTrue( ci.shouldSaveConfig() );

        disable.set(null);
        enable.set(Boolean.FALSE);
        assertFalse( ci.shouldSaveConfig() );

        disable.set(Boolean.FALSE);
        enable.set(Boolean.FALSE);
        assertFalse( ci.shouldSaveConfig() );

        disable.set(Boolean.TRUE);
        enable.set(Boolean.FALSE);
        assertFalse( ci.shouldSaveConfig() );

        disable.set(null);
        enable.set(Boolean.TRUE);
        assertTrue( ci.shouldSaveConfig() );

        disable.set(Boolean.FALSE);
        enable.set(Boolean.TRUE);
        assertTrue( ci.shouldSaveConfig() );

        disable.set(Boolean.TRUE);
        enable.set(Boolean.TRUE);
        assertTrue( ci.shouldSaveConfig() );

        EasyMock.verify(mockConfiguration, mockConfigurationAdmin, mockBundleContext);
    }

    public void testSetREAD_ONLYFromFilePermissions() throws Exception
    {
        final File file = File.createTempFile("test", ".config");
        try (OutputStream os = new FileOutputStream(file)) {
            os.write("networkInterface = \"wlp3s1\"\n".getBytes("UTF-8"));
        }
        file.setReadOnly();

        String pid = file.getName().substring(0, file.getName().indexOf(".config"));

        ServiceReference<ConfigurationAdmin> sr = EasyMock.createMock(ServiceReference.class);

        EasyMock.expect(mockBundleContext.getBundle()).andReturn(mockBundle).anyTimes();
        EasyMock.expect(mockBundle.loadClass(ConfigurationAttribute.class.getName())).andReturn((Class)ConfigurationAttribute.class).anyTimes();
        EasyMock.expect(mockBundleContext.getProperty((String) EasyMock.anyObject()))
                .andReturn(null)
                .anyTimes();

        final Configuration cachingPersistenceConfiguration = EasyMock.createMock(Configuration.class);
        EasyMock.expect(cachingPersistenceConfiguration.getAttributes()).andReturn(
            Collections.singleton(ConfigurationAttribute.READ_ONLY)).anyTimes();
        cachingPersistenceConfiguration.addAttributes(new ConfigurationAttribute[] {ConfigurationAttribute.READ_ONLY});
        cachingPersistenceConfiguration.removeAttributes(EasyMock.anyObject(ConfigurationAttribute[].class));
        EasyMock.expectLastCall().times(2);

        final Dictionary<String, Object> cachedProps = new Hashtable<String, Object>() {
            {
                put("networkInterface", "wlp3s0");
                put(DirectoryWatcher.FILENAME, file.toURI().toString());
            }
        };

        EasyMock.expect(cachingPersistenceConfiguration.getPid())
                .andReturn(pid).times(4);
        EasyMock.expect(cachingPersistenceConfiguration.getProperties())
                .andReturn(cachedProps)
                .anyTimes();

        EasyMock.expect(mockConfigurationAdmin.getConfiguration(pid, "?"))
                .andReturn(cachingPersistenceConfiguration)
                .anyTimes();

        EasyMock.expect(mockConfigurationAdmin.getConfiguration(pid, null))
                .andReturn(cachingPersistenceConfiguration)
                .anyTimes();

        final Configuration newConfiguration = EasyMock.createMock(Configuration.class);
        EasyMock.expect(mockConfigurationAdmin.listConfigurations((String) EasyMock.anyObject()))
                .andReturn(new Configuration[] { newConfiguration }).times(2);
        EasyMock.expect(newConfiguration.getPid())
                .andReturn(pid).times(2);

        final Capture<Dictionary<String, Object>> props = new Capture<>();
        EasyMock.expect(cachingPersistenceConfiguration.updateIfDifferent(EasyMock.capture(props)));
        EasyMock.expectLastCall()
                .andAnswer(new IAnswer<Boolean>() {
                    @Override
                    public Boolean answer() throws Throwable {

                        cachedProps.put("networkInterface", props.getValue().get("networkInterface"));

                        return Boolean.TRUE;
                   }
               }).anyTimes();

        EasyMock.replay(mockBundleContext, mockConfigurationAdmin, mockBundle, cachingPersistenceConfiguration, newConfiguration, sr);

        ConfigInstaller ci = new ConfigInstaller( mockBundleContext, mockConfigurationAdmin, new FileInstall() );

        ci.install(file);

        file.setWritable(true);

        ci.install(file);

        EasyMock.verify(mockBundleContext, mockConfigurationAdmin, mockBundle, cachingPersistenceConfiguration, newConfiguration, sr);
    }

}

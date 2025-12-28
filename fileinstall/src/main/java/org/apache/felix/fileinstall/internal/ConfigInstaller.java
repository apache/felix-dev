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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.felix.fileinstall.ArtifactInstaller;
import org.apache.felix.fileinstall.ArtifactListener;
import org.apache.felix.fileinstall.internal.Util.Logger;
import org.apache.felix.utils.collections.DictionaryAsMap;
import org.apache.felix.utils.properties.InterpolationHelper;
import org.apache.felix.utils.properties.TypedProperties;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;

/**
 * ArtifactInstaller for configurations.
 * TODO: This service lifecycle should be bound to the ConfigurationAdmin service lifecycle.
 */
public class ConfigInstaller implements ArtifactInstaller, ConfigurationListener
{
    private final BundleContext context;
    private final ConfigurationAdmin configAdmin;
    private final FileInstall fileInstall;
    private final Map<String, String> pidToFile = new HashMap<>();
    private final Method getFactoryConfigurationMethod;
    private final Method addAttributesMethod;
    private final Method getAttributesMethod;
    private final Method removeAttributesMethod;
    private final Method updateIfDifferentMethod;
    private final Object READ_ONLY_ATTRIBUTE_ARRAY;
    private ServiceRegistration<?> registration;

    @SuppressWarnings("unchecked")
    ConfigInstaller(BundleContext context, ConfigurationAdmin configAdmin, FileInstall fileInstall)
    {
        this.context = context;
        this.configAdmin = configAdmin;
        this.fileInstall = fileInstall;

        Method gfcMethod = null;
        Method aaMethod = null;
        Method gaMethod = null;
        Method raMethod = null;
        Method uidMethod = null;

        if (this.configAdmin != null) {
            for (Method method : ConfigurationAdmin.class.getDeclaredMethods())
            {
                if ("getFactoryConfiguration".equals(method.getName()) && (method.getParameterCount() == 3))
                {
                    gfcMethod = method;
                }
            }
            for (Method method : Configuration.class.getDeclaredMethods())
            {
                if ("addAttributes".equals(method.getName()))
                {
                    aaMethod = method;
                }
                else if ("getAttributes".equals(method.getName()))
                {
                    gaMethod = method;
                }
                else if ("removeAttributes".equals(method.getName()))
                {
                    raMethod = method;
                }
                else if ("updateIfDifferent".equals(method.getName()))
                {
                    uidMethod = method;
                }
            }
        }

        this.getFactoryConfigurationMethod = gfcMethod;
        this.addAttributesMethod = aaMethod;
        this.getAttributesMethod = gaMethod;
        this.removeAttributesMethod = raMethod;
        this.updateIfDifferentMethod = uidMethod;

        Object roAttributesArray = null;
        if (this.addAttributesMethod != null)
        {
            try {
                @SuppressWarnings("rawtypes")
                Class<? extends Enum> attr = (Class<? extends Enum>)context.getBundle()
                    .loadClass("org.osgi.service.cm.Configuration$ConfigurationAttribute");

                Object attrArray = Array.newInstance(attr, 1);
                Array.set(attrArray, 0, Enum.valueOf(attr, "READ_ONLY"));

                roAttributesArray = attrArray;
            }
            catch (ClassNotFoundException cnfe) {
                throw new IllegalStateException(
                    "Cannot find the enum org.osgi.service.cm.Configuration.ConfigurationAttribute", cnfe);
            }
        }

        this.READ_ONLY_ATTRIBUTE_ARRAY = roAttributesArray;
    }

    public void init()
    {
        registration = this.context.registerService(
                new String[] {
                    ConfigurationListener.class.getName(),
                    ArtifactListener.class.getName(),
                    ArtifactInstaller.class.getName()
                },
                this, null);
        try
        {
            if (getConfigurationAdmin() != null) {
                Configuration[] configs = getConfigurationAdmin().listConfigurations(null);
                if (configs != null) {
                    for (Configuration config : configs) {
                        Dictionary<?, ?> dict = config.getProperties();
                        String fileName = dict != null ? (String) dict.get(DirectoryWatcher.FILENAME) : null;
                        if (fileName != null) {
                            pidToFile.put(config.getPid(), fileName);
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            Util.log( context, Logger.LOG_INFO, "Unable to initialize configurations list", e );
        }
    }

    public void destroy()
    {
        registration.unregister();
    }

    public boolean canHandle(File artifact)
    {
        return artifact.getName().endsWith(".cfg")
            || artifact.getName().endsWith(".config");
    }

    public void install(File artifact) throws Exception
    {
        setConfig(artifact);
    }

    public void update(File artifact) throws Exception
    {
        setConfig(artifact);
    }

    public void uninstall(File artifact) throws Exception
    {
        deleteConfig(artifact);
    }

    public void configurationEvent(final ConfigurationEvent configurationEvent)
    {
        if (System.getSecurityManager() != null)
        {
            AccessController.doPrivileged(
                    new PrivilegedAction<Void>()
                    {
                        public Void run()
                        {
                            doConfigurationEvent(configurationEvent);
                            return null;
                        }
                    }
            );
        }
        else
        {
            doConfigurationEvent(configurationEvent);
        }
    }

    public void doConfigurationEvent(ConfigurationEvent configurationEvent)
    {
        // Check if writing back configurations has been disabled.
        {
            if (!shouldSaveConfig())
            {
                return;
            }
        }

        if (configurationEvent.getType() == ConfigurationEvent.CM_UPDATED)
        {
            try
            {
                Configuration config = getConfigurationAdmin().getConfiguration(
                                            configurationEvent.getPid(),
                                            "?");
                Dictionary<?,?> dict = config.getProperties();
                String fileName = dict != null ? (String) dict.get( DirectoryWatcher.FILENAME ) : null;
                File file = fileName != null ? fromConfigKey(fileName) : null;
                if( file != null && file.isFile() && canHandle( file ) ) {
                    pidToFile.put(config.getPid(), fileName);
                    if (!file.canWrite())
                    {
                        Util.log(context, Logger.LOG_WARNING, "File is not writeable: " + fileName, null);
                        return;
                    }

                    TypedProperties props = new TypedProperties( bundleSubstitution() );
                    try (Reader r = new InputStreamReader(new FileInputStream(file), encoding()))
                    {
                        props.load(r);
                    }
                    // remove "removed" properties from the cfg file
                    List<String> propertiesToRemove = new ArrayList<>();
                    for( String key : props.keySet() )
                    {
                        if( dict.get(key) == null
                                && !Constants.SERVICE_PID.equals(key)
                                && !ConfigurationAdmin.SERVICE_FACTORYPID.equals(key)
                                && !DirectoryWatcher.FILENAME.equals(key) ) {
                            propertiesToRemove.add(key);
                        }
                    }
                    for( Enumeration<?> e  = dict.keys(); e.hasMoreElements(); )
                    {
                        String key = e.nextElement().toString();
                        if( !Constants.SERVICE_PID.equals(key)
                                && !ConfigurationAdmin.SERVICE_FACTORYPID.equals(key)
                                && !DirectoryWatcher.FILENAME.equals(key) )
                        {
                            Object val = dict.get( key );
                            props.put( key, val );
                        }
                    }
                    for( String key : propertiesToRemove )
                    {
                        props.remove(key);
                    }

                    // re-writing a config file must be guarded by a write lock as otherwise DirectoryWatcher
                    // might pick up an empty file and as a result, write this file to disk in a follow-up event
                    fileInstall.lock.writeLock().lockInterruptibly();
                    try
                    {
                        try (Writer fw = new OutputStreamWriter(new FileOutputStream(file), encoding()))
                        {
                            props.save( fw );
                        }
                        // we're just writing out what's already loaded into ConfigAdmin, so
                        // update file checksum since lastModified gets updated when writing
                        fileInstall.updateChecksum(file);
                    }
                    finally
                    {
                        fileInstall.lock.writeLock().unlock();
                    }
                }
            }
            catch (Exception e)
            {
                Util.log( context, Logger.LOG_ERROR, "Unable to save configuration", e );
            }
        }

        if (configurationEvent.getType() == ConfigurationEvent.CM_DELETED)
        {
            try {
                String fileName = pidToFile.remove(configurationEvent.getPid());
                File file = fileName != null ? fromConfigKey(fileName) : null;
                if (file != null && file.isFile()) {
                    if (!file.delete()) {
                        throw new IOException("Unable to delete file: " + file);
                    }
                }
            }
            catch (Exception e)
            {
                Util.log( context, Logger.LOG_INFO, "Unable to delete configuration file", e );
            }
        }
    }

    boolean shouldSaveConfig()
    {
        String str = this.context.getProperty( DirectoryWatcher.ENABLE_CONFIG_SAVE );
        if (str == null)
        {
            str = this.context.getProperty( DirectoryWatcher.DISABLE_CONFIG_SAVE );
        }
        if (str != null)
        {
            return Boolean.valueOf(str);
        }
        return true;
    }

    String encoding()
    {
        String str = this.context.getProperty( DirectoryWatcher.CONFIG_ENCODING );
        return str != null ? str : "ISO-8859-1";
    }

    ConfigurationAdmin getConfigurationAdmin()
    {
        return configAdmin;
    }

    /**
     * Set the configuration based on the config file.
     *
     * @param f
     *            Configuration file
     * @return <code>true</code> if the configuration has been updated
     * @throws Exception
     */
    boolean setConfig(final File f) throws Exception
    {
        final Hashtable<String, Object> ht = new Hashtable<>();
        final InputStream in = new BufferedInputStream(new FileInputStream(f));
        try
        {
            in.mark(1);
            boolean isXml = in.read() == '<';
            in.reset();
            if (isXml) {
                final Properties p = new Properties();
                p.loadFromXML(in);
                Map<String, String> strMap = new HashMap<>();
                for (Object k : p.keySet()) {
                    strMap.put(k.toString(), p.getProperty(k.toString()));
                }
                InterpolationHelper.performSubstitution(strMap, context);
                ht.putAll(strMap);
            } else {
                TypedProperties p = new TypedProperties( bundleSubstitution() );
                try (Reader r = new InputStreamReader(in, encoding()))
                {
                    p.load(r);
                }
                for (String k : p.keySet()) {
                    ht.put(k, p.get(k));
                }
            }
        }
        finally
        {
            in.close();
        }

        String pid[] = parsePid(f.getName());
        Configuration config = getConfiguration(toConfigKey(f), pid[0], pid[1]);

        clearReadOnlyIfWritable(pid, config, f);

        Dictionary<String, Object> props = config.getProperties();
        Hashtable<String, Object> old = props != null ? new Hashtable<String, Object>(new DictionaryAsMap<>(props)) : null;
        if (old != null) {
            old.remove( DirectoryWatcher.FILENAME );
            old.remove( Constants.SERVICE_PID );
            old.remove( ConfigurationAdmin.SERVICE_FACTORYPID );
        }

        try {
            if( !ht.equals( old ) )
            {
                ht.put(DirectoryWatcher.FILENAME, toConfigKey(f));
                if (old == null) {
                    Util.log(context, Logger.LOG_INFO, "Creating configuration {"
                            + config.getPid()
                            + "} from " + f.getAbsolutePath(), null);
                } else {
                    Util.log(context, Logger.LOG_INFO, "Updating configuration {"
                            + config.getPid()
                            + "} from " + f.getAbsolutePath(), null);
                }
                update0(pid, config, ht, f);
                return true;
            }
            else
            {
                return false;
            }
        }
        finally {
            setReadOnlyInNotWritable(pid, config, f);
        }
    }

    /**
     * Remove the configuration.
     *
     * @param f
     *            File where the configuration in was defined.
     * @return <code>true</code>
     * @throws Exception
     */
    boolean deleteConfig(File f) throws Exception
    {
        String pid[] = parsePid(f.getName());
        Configuration config = getConfiguration(toConfigKey(f), pid[0], pid[1]);
        Util.log(context, Logger.LOG_INFO, "Deleting configuration {"
                + config.getPid()
                + "} from " + f.getAbsolutePath(), null);
        config.delete();
        return true;
    }

    String toConfigKey(File f) {
        return f.getAbsoluteFile().toURI().toString();
    }

    File fromConfigKey(String key) {
        return new File(URI.create(key));
    }

    String[] parsePid(String path)
    {
        String pid = path.substring(0, path.lastIndexOf('.'));
        int n = pid.indexOf('-');
        if (n > 0)
        {
            String factoryPid = pid.substring(n + 1);
            pid = pid.substring(0, n);
            return new String[]
                {
                    pid, factoryPid
                };
        }
        else
        {
            return new String[]
                {
                    pid, null
                };
        }
    }

    Configuration getConfiguration(String fileName, String pid, String factoryPid)
        throws Exception
    {
        Configuration oldConfiguration = findExistingConfiguration(fileName);
        Configuration cachedConfiguration = oldConfiguration != null ?
                getConfigurationAdmin().getConfiguration(oldConfiguration.getPid(), null) : null;
        if (cachedConfiguration != null)
        {
            return cachedConfiguration;
        }
        else
        {
            Configuration newConfiguration;
            if (factoryPid != null)
            {
                if (getFactoryConfigurationMethod != null) {
                    newConfiguration = (Configuration)getFactoryConfigurationMethod.invoke(getConfigurationAdmin(), pid, factoryPid, "?");
                }
                else {
                    newConfiguration = getConfigurationAdmin().createFactoryConfiguration(pid, "?");
                }
            }
            else
            {
                newConfiguration = getConfigurationAdmin().getConfiguration(pid, "?");
            }
            return newConfiguration;
        }
    }

    Configuration findExistingConfiguration(String fileName) throws Exception
    {
        String filter = "(" + DirectoryWatcher.FILENAME + "=" + escapeFilterValue(fileName) + ")";
        Configuration[] configurations = getConfigurationAdmin().listConfigurations(filter);
        if (configurations != null && configurations.length > 0)
        {
            return configurations[0];
        }
        else
        {
            return null;
        }
    }

    TypedProperties.SubstitutionCallback bundleSubstitution() {
        final InterpolationHelper.SubstitutionCallback cb = new InterpolationHelper.BundleContextSubstitutionCallback(context);
        return new TypedProperties.SubstitutionCallback() {
            @Override
            public String getValue(String name, String key, String value) {
                return cb.getValue(value);
            }
        };
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    boolean isReadOnly(Configuration configuration) throws IOException {
        if (getAttributesMethod == null) {
            return false;
        }

        try {
            Set<? extends Enum> attributes = (Set<? extends Enum>)getAttributesMethod.invoke(configuration);

            if (attributes.isEmpty()) {
                return false;
            }

            return attributes.iterator().next().name().equals("READ_ONLY");
        }
        catch (InvocationTargetException ite) {
            Throwable targetException = ite.getTargetException();
            if (targetException instanceof IOException) {
                throw (IOException)targetException;
            }

            throw new RuntimeException(targetException);
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    void clearReadOnlyIfWritable(String[] pid, Configuration configuration, File f) throws IOException {
        if (removeAttributesMethod == null) {
            return;
        }

        try {
            if (Util.canWrite(f) && isReadOnly(configuration)) {
                Util.log(context, Logger.LOG_INFO, "Removing  READ_ONLY attribute from configuration {"
                        + configuration.getPid()
                        + "} from " + f.getAbsolutePath(), null);
                removeAttributesMethod.invoke(configuration, READ_ONLY_ATTRIBUTE_ARRAY);
            }
        }
        catch (InvocationTargetException ite) {
            Throwable targetException = ite.getTargetException();
            if (targetException instanceof IOException) {
                throw (IOException)targetException;
            }

            throw new RuntimeException(targetException);
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    void clearReadOnlyToUpdate(String[] pid, Configuration configuration, File f) throws IOException {
        if (removeAttributesMethod == null) {
            return;
        }

        try {
            if (isReadOnly(configuration)) {
                Util.log(context, Logger.LOG_INFO, "Temporarily removing  READ_ONLY attribute from configuration for external update {"
                        + configuration.getPid()
                        + "} from " + f.getAbsolutePath(), null);
                removeAttributesMethod.invoke(configuration, READ_ONLY_ATTRIBUTE_ARRAY);
            }
        }
        catch (InvocationTargetException ite) {
            Throwable targetException = ite.getTargetException();
            if (targetException instanceof IOException) {
                throw (IOException)targetException;
            }

            throw new RuntimeException(targetException);
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    boolean setReadOnlyInNotWritable(String[] pid, Configuration configuration, File f) throws IOException {
        if (addAttributesMethod == null) {
            return false;
        }

        try {
            if (!Util.canWrite(f)) {
                Util.log(context, Logger.LOG_INFO, "Adding  READ_ONLY attribute to configuration {"
                        + configuration.getPid()
                        + "} from " + f.getAbsolutePath(), null);
                addAttributesMethod.invoke(configuration, READ_ONLY_ATTRIBUTE_ARRAY);

                return true;
            }
        }
        catch (InvocationTargetException ite) {
            Throwable targetException = ite.getTargetException();
            if (targetException instanceof IOException) {
                throw (IOException)targetException;
            }

            throw new RuntimeException(targetException);
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }

        return false;
    }

    void update0(String[] pid, final Configuration config, final Hashtable<String, Object> ht, File f) throws IOException {
        clearReadOnlyToUpdate(pid, config, f);
        if (updateIfDifferentMethod != null) {
            try {
                updateIfDifferentMethod.invoke(config, ht);
            }
            catch (InvocationTargetException ite) {
                Throwable targetException = ite.getTargetException();
                if (targetException instanceof IOException) {
                    throw (IOException)targetException;
                }

                throw new RuntimeException(targetException);
            }
            catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        else {
            config.update(ht);
        }
    }

    private String escapeFilterValue(String s) {
        return s.replaceAll("[(]", "\\\\(").
                replaceAll("[)]", "\\\\)").
                replaceAll("[=]", "\\\\=").
                replaceAll("[\\*]", "\\\\*");
    }

}

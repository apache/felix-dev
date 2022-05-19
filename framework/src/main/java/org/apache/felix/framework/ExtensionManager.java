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
package org.apache.felix.framework;

import org.apache.felix.framework.cache.ConnectContentContent;
import org.apache.felix.framework.cache.Content;
import org.apache.felix.framework.cache.DirectoryContent;
import org.apache.felix.framework.cache.JarContent;
import org.apache.felix.framework.ext.ClassPathExtenderFactory;
import org.apache.felix.framework.util.ClassParser;
import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.framework.util.Util;
import org.apache.felix.framework.util.manifestparser.ManifestParser;
import org.apache.felix.framework.util.manifestparser.NativeLibrary;
import org.apache.felix.framework.util.manifestparser.NativeLibraryClause;
import org.apache.felix.framework.wiring.BundleCapabilityImpl;
import org.apache.felix.framework.wiring.BundleRequirementImpl;
import org.apache.felix.framework.wiring.BundleWireImpl;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.ExecutionEnvironmentNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.NativeNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URL;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The ExtensionManager class is used as content loader of the systembundle. Added extension
 * bundles exports will be available via this loader.
 */
class ExtensionManager implements Content
{
    static final ClassPathExtenderFactory.ClassPathExtender m_extenderFramework;
    static final ClassPathExtenderFactory.ClassPathExtender m_extenderBoot;
    private static final Set<String> IDENTITY = new HashSet<String>(Arrays.asList(
        BundleNamespace.BUNDLE_NAMESPACE,
        HostNamespace.HOST_NAMESPACE,
        IdentityNamespace.IDENTITY_NAMESPACE));

    static
    {
        ClassPathExtenderFactory.ClassPathExtender extenderFramework = null;
        ClassPathExtenderFactory.ClassPathExtender extenderBoot = null;

        if (!"true".equalsIgnoreCase(Felix.m_secureAction.getSystemProperty(FelixConstants.FELIX_EXTENSIONS_DISABLE, "false")))
        {
            ServiceLoader<ClassPathExtenderFactory> loader = ServiceLoader.load(ClassPathExtenderFactory.class,
                ExtensionManager.class.getClassLoader());


            for (Iterator<ClassPathExtenderFactory> iter = loader.iterator();
                 iter.hasNext() && (extenderFramework == null || extenderBoot == null); )
            {
                try
                {
                    ClassPathExtenderFactory factory = iter.next();

                    if (extenderFramework == null)
                    {
                        try
                        {
                            extenderFramework = factory.getExtender(ExtensionManager.class.getClassLoader());
                        }
                        catch (Throwable t)
                        {
                            // Ignore
                        }
                    }
                    if (extenderBoot == null)
                    {
                        try
                        {
                            extenderBoot = factory.getExtender(null);
                        }
                        catch (Throwable t)
                        {
                            // Ignore
                        }
                    }
                }
                catch (Throwable t)
                {
                    // Ignore
                }
            }

            try
            {
                if (extenderFramework == null)
                {
                    extenderFramework = new ClassPathExtenderFactory.DefaultClassLoaderExtender()
                            .getExtender(ExtensionManager.class.getClassLoader());
                }
            }
            catch (Throwable t) {
                // Ignore
            }
        }

        m_extenderFramework = extenderFramework;
        m_extenderBoot = extenderBoot;
    }

    private final Logger m_logger;
    private volatile ExtensionManagerRevision m_systemBundleRevision;

    private final List<ExtensionTuple> m_extensionTuples = Collections.synchronizedList(new ArrayList<ExtensionTuple>());

    private final List<BundleRevisionImpl> m_resolvedExtensions = new CopyOnWriteArrayList<BundleRevisionImpl>();
    private final List<BundleRevisionImpl> m_unresolvedExtensions = new CopyOnWriteArrayList<BundleRevisionImpl>();
    private final List<BundleRevisionImpl> m_failedExtensions = new CopyOnWriteArrayList<BundleRevisionImpl>();

    private static class ExtensionTuple
    {
        private final BundleActivator m_activator;
        private final Bundle m_bundle;
        private volatile boolean m_failed;
        private volatile boolean m_started;

        public ExtensionTuple(BundleActivator activator, Bundle bundle)
        {
            m_activator = activator;
            m_bundle = bundle;
        }
    }

    /**
     * This constructor is used to create one instance per framework instance.
     * The general approach is to have one private static instance that we register
     * with the parent classloader and one instance per framework instance that
     * keeps track of extension bundles and systembundle exports for that framework
     * instance.
     *
     * @param logger the logger to use.
     */
    ExtensionManager(Logger logger, Map configMap, Felix felix)
    {
        m_logger = logger;

        m_systemBundleRevision = new ExtensionManagerRevision(configMap, felix);
    }

    protected BundleCapability buildNativeCapabilites(BundleRevisionImpl revision, Map configMap) {
        String osArchitecture = (String) configMap.get(FelixConstants.FRAMEWORK_PROCESSOR);
        String osName = (String) configMap.get(FelixConstants.FRAMEWORK_OS_NAME);
        String osVersion = (String) configMap.get(FelixConstants.FRAMEWORK_OS_VERSION);
        String userLang = (String) configMap.get(FelixConstants.FRAMEWORK_LANGUAGE);
        Map<String, Object> attributes = new HashMap<String, Object>();

        //Add all startup properties so we can match selection-filters
        attributes.putAll(configMap);

        if( osArchitecture != null )
        {
            attributes.put(NativeNamespace.CAPABILITY_PROCESSOR_ATTRIBUTE, NativeLibraryClause.getProcessorWithAliases(osArchitecture));
        }

        if( osName != null)
        {
            attributes.put(NativeNamespace.CAPABILITY_OSNAME_ATTRIBUTE, NativeLibraryClause.getOsNameWithAliases(osName));
        }

        if( osVersion != null)
        {
            attributes.put(NativeNamespace.CAPABILITY_OSVERSION_ATTRIBUTE, VersionConverter.toOsgiVersion(osVersion));
        }

        if( userLang != null)
        {
            attributes.put(NativeNamespace.CAPABILITY_LANGUAGE_ATTRIBUTE, userLang);
        }

        return new BundleCapabilityImpl(revision, NativeNamespace.NATIVE_NAMESPACE, Collections.<String, String> emptyMap(), attributes);
    }

    @IgnoreJRERequirement
    void updateRevision(Felix felix, Map configMap)
    {
        Map config = new HashMap(configMap);
        Properties defaultProperties = Util.loadDefaultProperties(m_logger);

        Util.initializeJPMSEE(felix._getProperty("java.specification.version"), defaultProperties, m_logger);

        String sysprops = felix._getProperty(Constants.FRAMEWORK_SYSTEMPACKAGES);


        boolean subst = "true".equalsIgnoreCase(felix._getProperty(FelixConstants.USE_PROPERTY_SUBSTITUTION_IN_SYSTEMPACKAGES));

        if (sysprops != null && sysprops.isEmpty())
        {
            if (felix.hasConnectFramework())
            {
                subst = true;
                sysprops = "${osgi-exports}";
                config.put(Constants.FRAMEWORK_SYSTEMPACKAGES, sysprops);
            }
        }

        final Map<String, Set<String>> exports = Util.initializeJPMS(defaultProperties);

        if (exports != null && (sysprops == null || "true".equalsIgnoreCase(felix._getProperty(FelixConstants.USE_PROPERTY_SUBSTITUTION_IN_SYSTEMPACKAGES))))
        {
            final ClassParser classParser = new ClassParser();
            final Set<String> imports = new HashSet<String>();
            for (Set<String> moduleImport : exports.values())
            {
                for (String pkg : moduleImport)
                {
                    if (!pkg.startsWith("java."))
                    {
                        imports.add(pkg);
                    }
                }
            }
            for (final String moduleKey : exports.keySet())
            {
                int idx = moduleKey.indexOf("@");
                String module = idx == -1 ? moduleKey : moduleKey.substring(0, idx);
                if (felix._getProperty(module) == null && !exports.get(moduleKey).isEmpty() && defaultProperties.getProperty(module) == null)
                {
                    final SortedMap<String, SortedSet<String>> referred = new TreeMap<String, SortedSet<String>>();
                    if ("true".equalsIgnoreCase(felix._getProperty(FelixConstants.CALCULATE_SYSTEMPACKAGES_USES)))
                    {
                        java.nio.file.FileSystem fs = java.nio.file.FileSystems.getFileSystem(URI.create("jrt:/"));
                        try
                        {
                            Properties cachedProps = new Properties();
                            File modulesDir = felix.getDataFile(felix, "modules");
                            Felix.m_secureAction.mkdirs(modulesDir);
                            File cached = new File(modulesDir, moduleKey + ".properties");
                            if (Felix.m_secureAction.isFile(cached))
                            {
                                InputStream input = Felix.m_secureAction.getInputStream(cached);
                                cachedProps.load(new InputStreamReader(input, "UTF-8"));
                                input.close();
                                for (Enumeration<?> keys = cachedProps.propertyNames(); keys.hasMoreElements();)
                                {
                                    String pkg = (String) keys.nextElement();
                                    referred.put(pkg, new TreeSet<String>(Arrays.asList(cachedProps.getProperty(pkg).split(","))));
                                }
                            }
                            else
                            {
                                java.nio.file.Path path = fs.getPath("modules", module.substring("felix.jpms.".length()));
                                java.nio.file.Files.walkFileTree(path, (java.nio.file.FileVisitor) Felix.class.getClassLoader().loadClass("org.apache.felix.framework.util.ClassFileVisitor")
                                    .getConstructor(Set.class, Set.class, ClassParser.class, SortedMap.class).newInstance(imports, exports.get(moduleKey), classParser, referred));
                                for (String pkg : referred.keySet())
                                {
                                    SortedSet<String> uses = referred.get(pkg);
                                    if (uses != null && !uses.isEmpty())
                                    {
                                        cachedProps.setProperty(pkg, String.join(",", uses));
                                    }
                                }
                                OutputStream output = Felix.m_secureAction.getOutputStream(cached);
                                cachedProps.store(new OutputStreamWriter(output, "UTF-8"), null);
                                output.close();
                            }
                        }
                        catch (Throwable e)
                        {
                            m_logger.log(Logger.LOG_WARNING, "Exception calculating JPMS module exports", e);
                        }
                    }

                    String pkgs = "";

                    for (String pkg : exports.get(moduleKey))
                    {
                        pkgs += "," + pkg;
                        SortedSet<String> uses = referred.get(pkg);
                        if (uses != null && !uses.isEmpty())
                        {
                            pkgs += ";uses:=\"";
                            String sep = "";
                            for (String u : uses)
                            {
                                pkgs += sep + u;
                                sep = ",";
                            }
                            pkgs += "\"";
                        }
                        pkgs += ";version=\"" + defaultProperties.getProperty("felix.detect.java.version") + "\"";
                    }
                    defaultProperties.put(module, pkgs);
                }
            }
        }

        for (Map.Entry entry : defaultProperties.entrySet())
        {
            if (!config.containsKey(entry.getKey()))
            {
                config.put(entry.getKey(), entry.getValue());
            }
        }

        if(sysprops != null && subst)
        {
            config.put(Constants.FRAMEWORK_SYSTEMPACKAGES, Util.getPropertyWithSubs(Util.toProperties(config), Constants.FRAMEWORK_SYSTEMPACKAGES));
        }
        else if (sysprops == null)
        {
            config.put(Constants.FRAMEWORK_SYSTEMPACKAGES, Util.getPropertyWithSubs(Util.toProperties(config), Constants.FRAMEWORK_SYSTEMPACKAGES));
        }

        String syspropsExtra = felix._getProperty(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA);
        if (syspropsExtra != null && "true".equalsIgnoreCase(felix._getProperty(FelixConstants.USE_PROPERTY_SUBSTITUTION_IN_SYSTEMPACKAGES)))
        {
            config.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, Util.getPropertyWithSubs(Util.toProperties(config), Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA));
        }

        String syscaps = felix._getProperty(Constants.FRAMEWORK_SYSTEMCAPABILITIES);
        if(syscaps != null && "true".equalsIgnoreCase(felix._getProperty(FelixConstants.USE_PROPERTY_SUBSTITUTION_IN_SYSTEMPACKAGES)))
        {
            config.put(Constants.FRAMEWORK_SYSTEMCAPABILITIES, Util.getPropertyWithSubs(Util.toProperties(config), Constants.FRAMEWORK_SYSTEMCAPABILITIES));
        }
        else if(syscaps == null)
        {
            config.put(Constants.FRAMEWORK_SYSTEMCAPABILITIES, Util.getPropertyWithSubs(Util.toProperties(config), Constants.FRAMEWORK_SYSTEMCAPABILITIES));
        }

        String syscapsExtra = felix._getProperty(Constants.FRAMEWORK_SYSTEMCAPABILITIES_EXTRA);
        if (syscapsExtra != null && "true".equalsIgnoreCase(felix._getProperty(FelixConstants.USE_PROPERTY_SUBSTITUTION_IN_SYSTEMPACKAGES)))
        {
            config.put(Constants.FRAMEWORK_SYSTEMCAPABILITIES_EXTRA, Util.getPropertyWithSubs(Util.toProperties(config), Constants.FRAMEWORK_SYSTEMCAPABILITIES_EXTRA));
        }

        m_systemBundleRevision.update(config);
    }

    public BundleRevisionImpl getRevision()
    {
        return m_systemBundleRevision;
    }

    /**
     * Add an extension bundle. The bundle will be added to the parent classloader
     * and it's exported packages will be added to the module definition
     * exports of this instance. Subsequently, they are available form the
     * instance in it's role as content loader.
     *
     * @param bundle the extension bundle to add.
     * @throws BundleException if extension bundles are not supported or this is
     *          not a framework extension.
     * @throws SecurityException if the caller does not have the needed
     *          AdminPermission.EXTENSIONLIFECYCLE and security is enabled.
     * @throws Exception in case something goes wrong.
     */
    void addExtensionBundle(BundleImpl bundle) throws Exception
    {
        Object sm = System.getSecurityManager();
        if (sm != null)
        {
            ((SecurityManager) sm).checkPermission(
                new AdminPermission(bundle, AdminPermission.EXTENSIONLIFECYCLE));

            if (!((BundleProtectionDomain) bundle.getProtectionDomain()).impliesDirect(new AllPermission()))
            {
                throw new SecurityException("Extension Bundles must have AllPermission");
            }
        }

        String directive = ManifestParser.parseExtensionBundleHeader((String)
            ((BundleRevisionImpl) bundle.adapt(BundleRevision.class))
                .getHeaders().get(Constants.FRAGMENT_HOST));

        Content content = bundle.adapt(BundleRevisionImpl.class).getContent();
        final File file;
        if (content instanceof JarContent)
        {
            file = ((JarContent) content).getFile();
        }
        else if (content instanceof DirectoryContent)
        {
            file = ((DirectoryContent) content).getFile();
        }
        else
        {
            file = null;
        }
        if (file == null && !(content instanceof ConnectContentContent))
        {
            // We don't support revision type for extension
            m_logger.log(bundle, Logger.LOG_WARNING,
                "Unable to add extension bundle - wrong revision type?");

            throw new UnsupportedOperationException(
                "Unable to add extension bundle.");
        }

        if (!Constants.EXTENSION_FRAMEWORK.equals(directive))
        {
           throw new BundleException("Unsupported Extension Bundle type: " +
                directive, new UnsupportedOperationException(
                "Unsupported Extension Bundle type!"));
        }
        else if (m_extenderFramework == null && file != null)
        {
            // We don't support extensions
            m_logger.log(bundle, Logger.LOG_WARNING,
                "Unable to add extension bundle - Maybe ClassLoader is not supported " +
                        "(on java9, try --add-opens=java.base/jdk.internal.loader=ALL-UNNAMED)?");

            throw new UnsupportedOperationException(
                "Unable to add extension bundle.");
        }

        BundleRevisionImpl bri = bundle.adapt(BundleRevisionImpl.class);

        bri.resolve(null);

        // we have to try again for all previously failed extensions because maybe they can now resolve.
        m_unresolvedExtensions.addAll(m_failedExtensions);
        m_failedExtensions.clear();
        m_unresolvedExtensions.add(bri);
    }

    public synchronized List<Bundle> resolveExtensionBundles(Felix felix)
    {
        if (m_unresolvedExtensions.isEmpty())
        {
            return Collections.emptyList();
        }

        // Collect the highest version of unresolved that are not already resolved by bsn
        List<BundleRevisionImpl> extensions = new ArrayList<BundleRevisionImpl>();
        // Collect the unresolved that where filtered out as alternatives in case the highest version doesn't resolve
        List<BundleRevisionImpl> alt = new ArrayList<BundleRevisionImpl>();

        outer : for (BundleRevisionImpl revision : m_unresolvedExtensions)
        {
            // Already resolved by bsn?
            for (BundleRevisionImpl existing : m_resolvedExtensions)
            {
                if (existing.getSymbolicName().equals(revision.getSymbolicName()))
                {
                    // Then ignore it
                    continue outer;
                }
            }
            // Otherwise, does a higher version exist by bsn?
            for (BundleRevisionImpl other : m_unresolvedExtensions)
            {
                if ((revision != other) && (revision.getSymbolicName().equals(other.getSymbolicName())) &&
                    revision.getVersion().compareTo(other.getVersion()) < 0)
                {
                    // Add this one to alternatives and filter it
                    alt.add(revision);
                    continue outer;
                }
            }

            // no higher version and not resolved yet by bsn - try to resolve it
            extensions.add(revision);
        }

        // This will return all resolvable revisions with the wires they need
        Map<BundleRevisionImpl, List<BundleWire>> wirings = findResolvableExtensions(extensions, alt);

        List<Bundle> result = new ArrayList<Bundle>();

        for (Map.Entry<BundleRevisionImpl, List<BundleWire>> entry : wirings.entrySet())
        {
            BundleRevisionImpl revision = entry.getKey();

            // move this revision from unresolved to resolved
            m_unresolvedExtensions.remove(revision);
            m_resolvedExtensions.add(revision);

            BundleWire wire = new BundleWireImpl(revision,
                revision.getDeclaredRequirements(BundleRevision.HOST_NAMESPACE).get(0),
                m_systemBundleRevision,  m_systemBundleRevision.getWiring().getCapabilities(BundleRevision.HOST_NAMESPACE).get(0));

            try
            {
                revision.resolve(new BundleWiringImpl(m_logger, m_systemBundleRevision.m_configMap, null, revision, null,
                    Collections.singletonList(wire), Collections.EMPTY_MAP, Collections.EMPTY_MAP));
            }
            catch (Exception ex)
            {
                m_logger.log(revision.getBundle(), Logger.LOG_ERROR,
                        "Error resolving extension bundle : " + revision.getBundle(), ex);
            }

            felix.getDependencies().addDependent(wire);

            List<BundleCapability> caps = new ArrayList<BundleCapability>();
            for (BundleCapability cap : entry.getKey().getDeclaredCapabilities(null))
            {
                if (!IDENTITY.contains(cap.getNamespace()))
                {
                    caps.add(cap);
                }
            }
            m_systemBundleRevision.appendCapabilities(caps);
            for (BundleWire w : entry.getValue())
            {
                if (!w.getRequirement().getNamespace().equals(BundleRevision.HOST_NAMESPACE) &&
                    !w.getRequirement().getNamespace().equals(BundleRevision.PACKAGE_NAMESPACE))
                {
                    ((BundleWiringImpl) w.getRequirer().getWiring()).addDynamicWire(w);
                    felix.getDependencies().addDependent(w);
                }
            }

            final File f;
            Content revisionContent = revision.getContent();
            if (revisionContent instanceof JarContent)
            {
                f = ((JarContent) revisionContent).getFile();
            }
            else if (revisionContent instanceof DirectoryContent)
            {
                f = ((DirectoryContent) revisionContent).getFile();
            }
            else
            {
                f = null;
            }
            if (f != null)
            {
                try
                {
                    AccessController.doPrivileged(new PrivilegedExceptionAction<Void>()
                    {
                        @Override
                        public Void run() throws Exception
                        {
                            m_extenderFramework.add(f);
                            return null;
                        }
                    });
                }
                catch (Exception ex)
                {
                    m_logger.log(revision.getBundle(), Logger.LOG_ERROR,
                        "Error adding extension bundle to framework classloader: " + revision.getBundle(), ex);
                }
            }

            felix.setBundleStateAndNotify(revision.getBundle(), Bundle.RESOLVED);
            result.add(revision.getBundle());
        }

        // at this point, all revisions left in unresolved are not resolvable
        m_failedExtensions.addAll(m_unresolvedExtensions);
        m_unresolvedExtensions.clear();

        return result;
    }

    /**
     * Start extension bundle if it has an activator
     *
     * @param felix the framework instance the extension bundle is installed in.
     * @param bundle the extension bundle to start if it has a an extension bundle activator.
     */
    void startExtensionBundle(Felix felix, BundleImpl bundle)
    {
        Map<?,?> headers = bundle.adapt(BundleRevisionImpl.class).getHeaders();
        String activatorClass = (String) headers.get(Constants.EXTENSION_BUNDLE_ACTIVATOR);
        boolean felixExtension = false;
        if (activatorClass == null)
        {
            felixExtension = true;
            activatorClass = (String) headers.get(FelixConstants.FELIX_EXTENSION_ACTIVATOR);
        }

        if (activatorClass != null)
        {
            ExtensionTuple tuple = null;
            try
            {
// TODO: SECURITY - Should this consider security?
                BundleActivator activator = (BundleActivator)
                    Felix.m_secureAction.getClassLoader(felix.getClass()).loadClass(
                        activatorClass.trim()).newInstance();

                BundleContext context = felix._getBundleContext();

                bundle.setBundleContext(context);

// TODO: EXTENSIONMANAGER - This is kind of hacky, can we improve it?
                if (!felixExtension)
                {
                    tuple = new ExtensionTuple(activator, bundle);
                    m_extensionTuples.add(tuple);
                }
                else
                {
                    felix.m_activatorList.add(activator);
                }

                if ((felix.getState() == Bundle.ACTIVE) || (felix.getState() == Bundle.STARTING))
                {
                    if (tuple != null)
                    {
                        tuple.m_started = true;
                    }
                    Felix.m_secureAction.startActivator(activator, context);
                }
            }
            catch (Throwable ex)
            {
                if (tuple != null)
                {
                    tuple.m_failed = true;
                }
                felix.fireFrameworkEvent(FrameworkEvent.ERROR, bundle,
                            new BundleException("Unable to start Bundle", ex));

                m_logger.log(bundle, Logger.LOG_WARNING,
                    "Unable to start Extension Activator", ex);
            }
        }
    }

    void startPendingExtensionBundles(Felix felix)
    {
        for (int i = 0;i < m_extensionTuples.size();i++)
        {
            if (!m_extensionTuples.get(i).m_started)
            {
                m_extensionTuples.get(i).m_started = true;
                try
                {
                    Felix.m_secureAction.startActivator(m_extensionTuples.get(i).m_activator, felix._getBundleContext());
                }
                catch (Throwable ex)
                {
                    m_extensionTuples.get(i).m_failed = true;

                    felix.fireFrameworkEvent(FrameworkEvent.ERROR, m_extensionTuples.get(i).m_bundle,
                                new BundleException("Unable to start Bundle", BundleException.ACTIVATOR_ERROR, ex));

                    m_logger.log(m_extensionTuples.get(i).m_bundle, Logger.LOG_WARNING,
                        "Unable to start Extension Activator", ex);
                }
            }
        }
    }

    void stopExtensionBundles(Felix felix)
    {
        for (int i = m_extensionTuples.size() - 1; i >= 0;i--)
        {
            if (m_extensionTuples.get(i).m_started && !m_extensionTuples.get(i).m_failed)
            {
                try
                {
                    Felix.m_secureAction.stopActivator(m_extensionTuples.get(i).m_activator, felix._getBundleContext());
                }
                catch (Throwable ex)
                {
                    felix.fireFrameworkEvent(FrameworkEvent.ERROR, m_extensionTuples.get(i).m_bundle,
                                new BundleException("Unable to stop Bundle", BundleException.ACTIVATOR_ERROR, ex));

                    m_logger.log(m_extensionTuples.get(i).m_bundle, Logger.LOG_WARNING,
                        "Unable to stop Extension Activator", ex);
                }
            }
        }
        m_extensionTuples.clear();
    }

    public synchronized void removeExtensionBundles()
    {
        m_resolvedExtensions.clear();
        m_unresolvedExtensions.clear();
        m_failedExtensions.clear();
    }

    private Map<BundleRevisionImpl, List<BundleWire>> findResolvableExtensions(List<BundleRevisionImpl> extensions, List<BundleRevisionImpl> alt)
    {
        // The idea is to loop through the extensions and try to resolve all unresolved extension. If we can't resolve
        // a given extension, we will call the method again with the extension in question removed or replaced if there
        // is a replacement for it in alt.
        // This resolve doesn't take into account that maybe a revision could be resolved with a revision from alt but
        // not with the current extensions. In that case, it will be removed (assuming the current extension can be resolved)
        // in other words, it will prefer to resolve the highest version of each extension over install order
        Map<BundleRevisionImpl, List<BundleWire>> wires = new LinkedHashMap<BundleRevisionImpl, List<BundleWire>>();

        for (BundleRevisionImpl bri : extensions)
        {
            List<BundleWire> wi = new ArrayList<BundleWire>();
            boolean resolved = true;
            outer: for (BundleRequirement req : bri.getDeclaredRequirements(null))
            {
                // first see if we can resolve from the system bundle
                for (BundleCapability cap : m_systemBundleRevision.getWiring().getCapabilities(req.getNamespace()))
                {
                    if (req.matches(cap))
                    {
                        // we can, create the wire but in the case of an ee requirement, make it from the extension
                        wi.add(new BundleWireImpl(
                                req.getNamespace().equals(ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE) ?
                                        bri : m_systemBundleRevision, req, m_systemBundleRevision, cap));

                        continue outer;
                    }
                }

                // now loop through the resolved extensions
                for (BundleRevisionImpl extension : m_resolvedExtensions)
                {
                    // and check the caps that will not be lifted (i.e., identity)
                    for (BundleCapability cap : extension.getDeclaredCapabilities(req.getNamespace()))
                    {
                        if (req.matches(cap))
                        {
                            // it was identity - hence, use the extension itself as provider
                            wi.add(new BundleWireImpl(m_systemBundleRevision, req, extension, cap));
                            continue outer;
                        }
                    }
                }
                // now loop through the other extensions
                for (BundleRevisionImpl extension : extensions)
                {
                    for (BundleCapability cap : extension.getDeclaredCapabilities(req.getNamespace()))
                    {
                        if (req.matches(cap))
                        {
                            // we can use a yet unresolved extension (resolved one are implicitly checked by the
                            // system bundle loop above as they would be attached.
                            wi.add(new BundleWireImpl(m_systemBundleRevision, req,
                                // lift identity
                                IDENTITY.contains(cap.getNamespace()) ?
                                extension : m_systemBundleRevision, cap));
                            continue outer;
                        }
                    }
                    // and check the caps that will not be lifted (i.e., identity)
                    for (BundleCapability cap : extension.getDeclaredCapabilities(req.getNamespace()))
                    {
                        if (req.matches(cap))
                        {
                            // it was identity - hence, use the extension itself as provider
                            wi.add(new BundleWireImpl(m_systemBundleRevision, req, extension, cap));
                            continue outer;
                        }
                    }
                }
                // we couldn't find a provider - was it optional?
                if (!((BundleRequirementImpl)req).isOptional())
                {
                    resolved = false;
                    break;
                }
            }
            if(resolved)
            {
                wires.put(bri, wi);
            }
            else
            {
                // we failed to resolve this extension - try again without it. Yes, this throws away the work done
                // up to this point
                List<BundleRevisionImpl> next = new ArrayList<BundleRevisionImpl>(extensions);
                List<BundleRevisionImpl> nextAlt = new ArrayList<BundleRevisionImpl>();

                outer : for (BundleRevisionImpl replacement : alt)
                {
                    if (bri.getSymbolicName().equals(replacement.getSymbolicName()))
                    {
                        for (BundleRevisionImpl other : alt)
                        {
                            if ((replacement != other) && (replacement.getSymbolicName().equals(other.getSymbolicName())) &&
                                    replacement.getVersion().compareTo(other.getVersion()) < 0)
                            {
                                nextAlt.add(replacement);
                                continue outer;
                            }
                        }
                        next.set(next.indexOf(bri), replacement);
                        break;
                    }
                    nextAlt.add(replacement);
                }
                
                next.remove(bri);

                return next.isEmpty() ? Collections.EMPTY_MAP : findResolvableExtensions(next, nextAlt);
            }
        }
        return wires;
    }

    public void close()
    {
        // Do nothing on close, since we have nothing open.
    }

    public Enumeration getEntries()
    {
        return new Enumeration()
        {
            public boolean hasMoreElements()
            {
                return false;
            }

            public Object nextElement() throws NoSuchElementException
            {
                throw new NoSuchElementException();
            }
        };
    }

    public boolean hasEntry(String name)
    {
        return false;
    }

    @Override
    public boolean isDirectory(String name)
    {
        return false;
    }

    public byte[] getEntryAsBytes(String name)
    {
        return null;
    }

    public InputStream getEntryAsStream(String name) throws IOException
    {
        return null;
    }

    public Content getEntryAsContent(String name)
    {
        return null;
    }

    public String getEntryAsNativeLibrary(String name)
    {
        return null;
    }

    public URL getEntryAsURL(String name)
    {
        return null;
    }

    @Override
    public long getContentTime(String name)
    {
        return -1L;
    }

    //
    // Utility methods.
    //

    class ExtensionManagerRevision extends BundleRevisionImpl
    {
        private volatile Map m_configMap;
        private final Map m_headerMap = new StringMap();
        private volatile List<BundleCapability> m_capabilities = Collections.EMPTY_LIST;
        private volatile Version m_version;
        private volatile BundleWiring m_wiring;

        ExtensionManagerRevision(Map configMap, Felix felix)
        {
            super(felix, "0");

            m_configMap = configMap;

// TODO: FRAMEWORK - Not all of this stuff really belongs here
            // Populate system bundle header map.
            m_headerMap.put(FelixConstants.BUNDLE_VERSION,
                m_configMap.get(FelixConstants.FELIX_VERSION_PROPERTY));
            m_headerMap.put(FelixConstants.BUNDLE_SYMBOLICNAME,
                FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME);
            m_headerMap.put(FelixConstants.BUNDLE_NAME, "System Bundle");
            m_headerMap.put(FelixConstants.BUNDLE_DESCRIPTION,
                "This bundle is system specific; it implements various system services.");
            m_headerMap.put(FelixConstants.EXPORT_SERVICE,
                "org.osgi.service.packageadmin.PackageAdmin," +
                    "org.osgi.service.startlevel.StartLevel," +
                    "org.osgi.service.url.URLHandlers");


            m_headerMap.put(FelixConstants.BUNDLE_MANIFESTVERSION, "2");


            m_version = new Version((String) m_configMap.get(FelixConstants.FELIX_VERSION_PROPERTY));

            try
            {
                ManifestParser mp = new ManifestParser(
                    m_logger, m_configMap, this, m_headerMap);
                List<BundleCapability> caps = ManifestParser.aliasSymbolicName(mp.getCapabilities(), this);
                caps.add(buildNativeCapabilites(this, m_configMap));
                appendCapabilities(caps);
                m_headerMap.put(Constants.EXPORT_PACKAGE, convertCapabilitiesToHeaders(caps));
            }
            catch (Exception ex)
            {
                m_capabilities = Collections.EMPTY_LIST;
                m_logger.log(
                    Logger.LOG_ERROR,
                    "Error parsing system bundle statement", ex);
            }
        }

        private void update(Map configMap)
        {
            Properties configProps = Util.toProperties(configMap);
            // The system bundle exports framework packages as well as
            // arbitrary user-defined packages from the system class path.
            // We must construct the system bundle's export metadata.
            // Get configuration property that specifies which class path
            // packages should be exported by the system bundle.
            String syspkgs = configProps.getProperty(FelixConstants.FRAMEWORK_SYSTEMPACKAGES);

            syspkgs = (syspkgs == null) ? "" : syspkgs;

            // If any extra packages are specified, then append them.
            String pkgextra = configProps.getProperty(FelixConstants.FRAMEWORK_SYSTEMPACKAGES_EXTRA);

            syspkgs = ((pkgextra == null) || (pkgextra.trim().length() == 0))
                ? syspkgs : syspkgs + (pkgextra.trim().startsWith(",") ? pkgextra : "," + pkgextra);

            if (syspkgs.startsWith(","))
            {
                syspkgs = syspkgs.substring(1);
            }
            
            m_headerMap.put(FelixConstants.EXPORT_PACKAGE, syspkgs);

            // The system bundle alsp provides framework generic capabilities
            // as well as arbitrary user-defined generic capabilities. We must
            // construct the system bundle's capabilities metadata. Get the
            // configuration property that specifies which capabilities should
            // be provided by the system bundle.
            String syscaps = configProps.getProperty(Constants.FRAMEWORK_SYSTEMCAPABILITIES);

            syscaps = (syscaps == null) ? "" : syscaps;

            // If any extra capabilities are specified, then append them.
            String capextra = configProps.getProperty(Constants.FRAMEWORK_SYSTEMCAPABILITIES_EXTRA);

            syscaps = ((capextra == null) || (capextra.trim().length() == 0))
                ? syscaps : syscaps + (capextra.trim().startsWith(",") ? capextra : "," + capextra);

            m_headerMap.put(FelixConstants.PROVIDE_CAPABILITY, syscaps);

            try
            {
                ManifestParser mp = new ManifestParser(
                    m_logger, m_configMap, this, m_headerMap);
                List<BundleCapability> caps = ManifestParser.aliasSymbolicName(mp.getCapabilities(), this);
                caps.add(buildNativeCapabilites(this, m_configMap));
                m_capabilities = Collections.EMPTY_LIST;
                appendCapabilities(caps);
                m_headerMap.put(Constants.EXPORT_PACKAGE, convertCapabilitiesToHeaders(caps));
            }
            catch (Exception ex)
            {
                m_capabilities = Collections.EMPTY_LIST;
                m_logger.log(
                    Logger.LOG_ERROR,
                    "Error parsing system bundle statement.", ex);
            }
        }

        private void appendCapabilities(List<BundleCapability> caps)
        {
            List<BundleCapability> newCaps = new ArrayList<BundleCapability>(m_capabilities.size() + caps.size());
            newCaps.addAll(m_capabilities);
            newCaps.addAll(caps);
            m_capabilities = Util.newImmutableList(newCaps);
        }

        private String convertCapabilitiesToHeaders(List<BundleCapability> caps)
        {
            StringBuilder exportSB = new StringBuilder();

            for (BundleCapability cap : caps)
            {
                if (cap.getNamespace().equals(BundleRevision.PACKAGE_NAMESPACE))
                {
                    // Add a comma separate if there is an existing package.
                    if (exportSB.length() > 0)
                    {
                        exportSB.append(", ");
                    }

                    // Append exported package information.
                    exportSB.append(cap.getAttributes().get(BundleRevision.PACKAGE_NAMESPACE));
                    for (Entry<String, String> entry : cap.getDirectives().entrySet())
                    {
                        exportSB.append("; ");
                        exportSB.append(entry.getKey());
                        exportSB.append(":=\"");
                        exportSB.append(entry.getValue());
                        exportSB.append("\"");
                    }
                    for (Entry<String, Object> entry : cap.getAttributes().entrySet())
                    {
                        if (!entry.getKey().equals(BundleRevision.PACKAGE_NAMESPACE)
                            && !entry.getKey().equals(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE)
                            && !entry.getKey().equals(Constants.BUNDLE_VERSION_ATTRIBUTE))
                        {
                            exportSB.append("; ");
                            exportSB.append(entry.getKey());
                            exportSB.append("=\"");
                            exportSB.append(entry.getValue());
                            exportSB.append("\"");
                        }
                    }
                }
            }

            return exportSB.toString();
        }

        @Override
        public Map getHeaders()
        {
            return Util.newImmutableMap(m_headerMap);
        }

        @Override
        public List<BundleCapability> getDeclaredCapabilities(String namespace)
        {
            List<BundleCapability> caps = m_capabilities;
            List<BundleCapability> result;
            if (namespace != null)
            {
                result = new ArrayList<BundleCapability>();
                for (BundleCapability cap : caps)
                {
                    if (cap.getNamespace().equals(namespace))
                    {
                        result.add(cap);
                    }
                }
            }
            else
            {
                result = new ArrayList<BundleCapability>(m_capabilities);
            }
            return result;
        }

        @Override
        public String getSymbolicName()
        {
            return FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME;
        }

        @Override
        public Version getVersion()
        {
            return m_version;
        }

        @Override
        public void close()
        {
            // Nothing needed here.
        }

        @Override
        public Content getContent()
        {
            return ExtensionManager.this;
        }

        @Override
        public URL getEntry(String name)
        {
            // There is no content for the system bundle, so return null.
            return null;
        }

        @Override
        public boolean hasInputStream(int index, String urlPath)
        {
            return (getClass().getClassLoader().getResource(urlPath) != null);
        }

        @Override
        public InputStream getInputStream(int index, String urlPath)
        {
            return getClass().getClassLoader().getResourceAsStream(urlPath);
        }

        @Override
        public URL getLocalURL(int index, String urlPath)
        {
            return getClass().getClassLoader().getResource(urlPath);
        }

        @Override
        public void resolve(BundleWiringImpl wire)
        {
            try
            {
                m_wiring = new ExtensionManagerWiring(
                    m_logger, m_configMap, this);
            }
            catch (Exception ex)
            {
                // This should never happen.
            }
        }

        @Override
        public BundleWiring getWiring()
        {
            return m_wiring;
        }
    }

    class ExtensionManagerWiring extends BundleWiringImpl
    {
        ExtensionManagerWiring(
            Logger logger, Map configMap, BundleRevisionImpl revision)
            throws Exception
        {
            super(logger, configMap, null, revision,
                null, Collections.EMPTY_LIST, null, null);
        }

        @Override
        public ClassLoader getClassLoader()
        {
            return getClass().getClassLoader();
        }

        @Override
        public List<BundleCapability> getCapabilities(String namespace)
        {
            return m_systemBundleRevision.getDeclaredCapabilities(namespace);
        }

        @Override
        public List<NativeLibrary> getNativeLibraries()
        {
            return Collections.EMPTY_LIST;
        }

        @Override
        public Class getClassByDelegation(String name) throws ClassNotFoundException
        {
            return getClass().getClassLoader().loadClass(name);
        }

        @Override
        public URL getResourceByDelegation(String name)
        {
            return getClass().getClassLoader().getResource(name);
        }

        @Override
        public Enumeration getResourcesByDelegation(String name)
        {
           try
           {
               return getClass().getClassLoader().getResources(name);
           }
           catch (IOException ex)
           {
               return null;
           }
        }

        @Override
        public void dispose()
        {
            // Nothing needed here.
        }
    }
}

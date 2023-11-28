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
package org.apache.felix.framework.util.manifestparser;

import org.apache.felix.framework.BundleRevisionImpl;
import org.apache.felix.framework.Logger;
import org.apache.felix.framework.cache.ConnectContentContent;
import org.apache.felix.framework.capabilityset.SimpleFilter;
import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.wiring.BundleCapabilityImpl;
import org.apache.felix.framework.wiring.BundleRequirementImpl;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.ExecutionEnvironmentNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.NativeNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ManifestParser
{
    private static final String BUNDLE_LICENSE_HEADER = "Bundle-License"; // No constant defined by OSGi...

    private final Logger m_logger;
    private final Map<String, Object> m_configMap;
    private final Map<String, Object> m_headerMap;
    private volatile int m_activationPolicy = BundleRevisionImpl.EAGER_ACTIVATION;
    private volatile String m_activationIncludeDir;
    private volatile String m_activationExcludeDir;
    private volatile boolean m_isExtension = false;
    private volatile String m_bundleSymbolicName;
    private volatile Version m_bundleVersion;
    private volatile List<BundleCapability> m_capabilities;
    private volatile List<BundleRequirement> m_requirements;
    private volatile List<NativeLibraryClause> m_libraryClauses;
    private volatile boolean m_libraryHeadersOptional = false;

    private static final Map<Object, WeakReference<Object>> objectCache = new WeakHashMap<>();
    private static final Function<Object, Object> cache = (foo) ->
    {
        if (foo instanceof String)
        {
            return ((String) foo).intern();
        }
        else if (foo != null)
        {
            synchronized (objectCache)
            {
                WeakReference<Object> ref = objectCache.get(foo);
                if (ref != null)
                {
                    Object refValue = ref.get();
                    if (refValue != null)
                    {
                        return refValue;
                    }
                }
                objectCache.put(foo, new WeakReference<>(foo));
            }
        }
        return foo;
    };

    public ManifestParser(Logger logger, Map<String, Object> configMap, BundleRevision owner, Map<String, Object> headerMap)
        throws BundleException
    {
        m_logger = logger;
        m_configMap = configMap;
        m_headerMap = headerMap;

        // Verify that only manifest version 2 is specified.
        String manifestVersion = getManifestVersion(m_headerMap);
        if ((manifestVersion != null) && !manifestVersion.equals("2"))
        {
            throw new BundleException(
                "Unknown 'Bundle-ManifestVersion' value: " + manifestVersion);
        }

        // Create lists to hold capabilities and requirements.
        List<BundleCapabilityImpl> capList = new ArrayList<BundleCapabilityImpl>();

        //
        // Parse bundle version.
        //

        m_bundleVersion = Version.emptyVersion;
        if (headerMap.get(Constants.BUNDLE_VERSION) != null)
        {
            try
            {
                m_bundleVersion = Version.parseVersion(
                    (String) headerMap.get(Constants.BUNDLE_VERSION));
            }
            catch (RuntimeException ex)
            {
                // R4 bundle versions must parse, R3 bundle version may not.
                if (getManifestVersion().equals("2"))
                {
                    throw ex;
                }
                m_bundleVersion = Version.emptyVersion;
            }
        }

        m_bundleVersion = (Version) cache.apply(m_bundleVersion);

        //
        // Parse bundle symbolic name.
        //

        BundleCapabilityImpl bundleCap = parseBundleSymbolicName(logger, owner, m_headerMap);
        if (bundleCap != null)
        {
            m_bundleSymbolicName = (String)
                bundleCap.getAttributes().get(BundleRevision.BUNDLE_NAMESPACE);

            // Add a bundle capability and a host capability to all
            // non-fragment bundles. A host capability is the same
            // as a require capability, but with a different capability
            // namespace. Bundle capabilities resolve required-bundle
            // dependencies, while host capabilities resolve fragment-host
            // dependencies.
            if (headerMap.get(Constants.FRAGMENT_HOST) == null)
            {
                // All non-fragment bundles have host capabilities.
                capList.add(bundleCap);
                // A non-fragment bundle can choose to not have a host capability.
                String attachment =
                    bundleCap.getDirectives().get(Constants.FRAGMENT_ATTACHMENT_DIRECTIVE);
                attachment = (attachment == null)
                    ? Constants.FRAGMENT_ATTACHMENT_RESOLVETIME
                    : attachment;
                if (!attachment.equalsIgnoreCase(Constants.FRAGMENT_ATTACHMENT_NEVER))
                {
                    Map<String, Object> hostAttrs =
                        new HashMap<String, Object>(bundleCap.getAttributes());
                    Object value = hostAttrs.remove(BundleRevision.BUNDLE_NAMESPACE);
                    hostAttrs.put(BundleRevision.HOST_NAMESPACE, value);
                    capList.add(new BundleCapabilityImpl(
                        owner, BundleRevision.HOST_NAMESPACE,
                        bundleCap.getDirectives(),
                        hostAttrs));
                }
            }

            //
            // Add the osgi.identity capability.
            //
            capList.add(addIdentityCapability(owner, headerMap, bundleCap));
        }

        // Verify that bundle symbolic name is specified.
        if (getManifestVersion().equals("2") && (m_bundleSymbolicName == null))
        {
            throw new BundleException(
                "R4 bundle manifests must include bundle symbolic name.");
        }

        m_isExtension = checkExtensionBundle(headerMap);

        //
        // Parse Fragment-Host.
        //

        List<BundleRequirementImpl> hostReqs = parseFragmentHost(m_logger, owner, m_headerMap);

        //
        // Parse Require-Bundle
        //

        List<ParsedHeaderClause> rbClauses =
            parseStandardHeader((String) headerMap.get(Constants.REQUIRE_BUNDLE));
        rbClauses = normalizeRequireClauses(m_logger, rbClauses, getManifestVersion());
        List<BundleRequirementImpl> rbReqs = convertRequires(rbClauses, owner);

        //
        // Parse Import-Package.
        //

        List<ParsedHeaderClause> importClauses =
            parseStandardHeader((String) headerMap.get(Constants.IMPORT_PACKAGE));
        importClauses = normalizeImportClauses(m_logger, importClauses, getManifestVersion());
        List<BundleRequirement> importReqs = convertImports(importClauses, owner);

        //
        // Parse DynamicImport-Package.
        //

        List<ParsedHeaderClause> dynamicClauses =
            parseStandardHeader((String) headerMap.get(Constants.DYNAMICIMPORT_PACKAGE));
        dynamicClauses = normalizeDynamicImportClauses(m_logger, dynamicClauses, getManifestVersion());
        List<BundleRequirement> dynamicReqs = convertImports(dynamicClauses, owner);

        //
        // Parse Require-Capability.
        //

        List<ParsedHeaderClause> requireClauses =
            parseStandardHeader((String) headerMap.get(Constants.REQUIRE_CAPABILITY));
        importClauses = normalizeCapabilityClauses(
            m_logger, requireClauses, getManifestVersion());
        List<BundleRequirement> requireReqs = convertRequireCapabilities(importClauses, owner);

        //
        // Parse Bundle-RequiredExecutionEnvironment.
        //
        List<BundleRequirement> breeReqs =
            parseBreeHeader((String) headerMap.get(Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT), owner);

        //
        // Parse Export-Package.
        //

        List<ParsedHeaderClause> exportClauses =
            parseStandardHeader((String) headerMap.get(Constants.EXPORT_PACKAGE));
        exportClauses = normalizeExportClauses(logger, exportClauses,
            getManifestVersion(), m_bundleSymbolicName, m_bundleVersion, owner instanceof BundleRevisionImpl && ((BundleRevisionImpl) owner).getContent() instanceof ConnectContentContent);
        List<BundleCapability> exportCaps = convertExports(exportClauses, owner);

        //
        // Parse Provide-Capability.
        //

        List<ParsedHeaderClause> provideClauses =
            parseStandardHeader((String) headerMap.get(Constants.PROVIDE_CAPABILITY));
        provideClauses = normalizeCapabilityClauses(
            logger, provideClauses, getManifestVersion());
        List<BundleCapability> provideCaps = convertProvideCapabilities(provideClauses, owner);

        //
        // Calculate implicit imports.
        //

        if (!getManifestVersion().equals("2"))
        {
            List<ParsedHeaderClause> implicitClauses =
                calculateImplicitImports(exportCaps, importClauses);
            importReqs.addAll(convertImports(implicitClauses, owner));

            List<ParsedHeaderClause> allImportClauses =
                new ArrayList<ParsedHeaderClause>(implicitClauses.size() + importClauses.size());
            allImportClauses.addAll(importClauses);
            allImportClauses.addAll(implicitClauses);

            exportCaps = calculateImplicitUses(exportCaps, allImportClauses);
        }

        //
        // Parse Bundle-NativeCode.
        //

        // Parse native library clauses.
        m_libraryClauses =
            parseLibraryStrings(
                m_logger,
                parseDelimitedString((String) m_headerMap.get(Constants.BUNDLE_NATIVECODE), ","));

        // Check to see if there was an optional native library clause, which is
        // represented by a null library header; if so, record it and remove it.
        if (!m_libraryClauses.isEmpty() &&
            (m_libraryClauses.get(m_libraryClauses.size() - 1).getLibraryEntries() == null))
        {
            m_libraryHeadersOptional = true;
            m_libraryClauses.remove(m_libraryClauses.size() - 1);
        }
        
        List<BundleRequirement> nativeCodeReqs = convertNativeCode(owner, m_libraryClauses, m_libraryHeadersOptional);

        // Combine all requirements.
        m_requirements = new ArrayList<BundleRequirement>(
            hostReqs.size() + importReqs.size() + rbReqs.size()
            + requireReqs.size() + dynamicReqs.size() + breeReqs.size());
        m_requirements.addAll(hostReqs.stream().map(req -> BundleRequirementImpl.createFrom((BundleRequirementImpl) req, cache)).collect(Collectors.toList()));
        m_requirements.addAll(importReqs.stream().map(req -> BundleRequirementImpl.createFrom((BundleRequirementImpl) req, cache)).collect(Collectors.toList()));
        m_requirements.addAll(rbReqs.stream().map(req -> BundleRequirementImpl.createFrom((BundleRequirementImpl) req, cache)).collect(Collectors.toList()));
        m_requirements.addAll(requireReqs.stream().map(req -> BundleRequirementImpl.createFrom((BundleRequirementImpl) req, cache)).collect(Collectors.toList()));
        m_requirements.addAll(dynamicReqs.stream().map(req -> BundleRequirementImpl.createFrom((BundleRequirementImpl) req, cache)).collect(Collectors.toList()));
        m_requirements.addAll(breeReqs.stream().map(req -> BundleRequirementImpl.createFrom((BundleRequirementImpl) req, cache)).collect(Collectors.toList()));
        m_requirements.addAll(nativeCodeReqs.stream().map(req -> BundleRequirementImpl.createFrom((BundleRequirementImpl) req, cache)).collect(Collectors.toList()));
        
        // Combine all capabilities.
        m_capabilities = new ArrayList<BundleCapability>(
             capList.size() + exportCaps.size() + provideCaps.size());
        m_capabilities.addAll(capList.stream().map(cap -> BundleCapabilityImpl.createFrom((BundleCapabilityImpl) cap, cache)).collect(Collectors.toList()));
        m_capabilities.addAll(exportCaps.stream().map(cap -> BundleCapabilityImpl.createFrom((BundleCapabilityImpl) cap, cache)).collect(Collectors.toList()));
        m_capabilities.addAll(provideCaps.stream().map(cap -> BundleCapabilityImpl.createFrom((BundleCapabilityImpl) cap, cache)).collect(Collectors.toList()));

        //
        // Parse activation policy.
        //

        // This sets m_activationPolicy, m_includedPolicyClasses, and
        // m_excludedPolicyClasses.
        parseActivationPolicy(headerMap);
    }

    private static List<ParsedHeaderClause> normalizeImportClauses(
        Logger logger, List<ParsedHeaderClause> clauses, String mv)
        throws BundleException
    {
        // Verify that the values are equals if the package specifies
        // both version and specification-version attributes.
        Set<String> dupeSet = new HashSet<String>();
        for (ParsedHeaderClause clause : clauses)
        {
            // Check for "version" and "specification-version" attributes
            // and verify they are the same if both are specified.
            Object v = clause.m_attrs.get(Constants.VERSION_ATTRIBUTE);
            Object sv = clause.m_attrs.get(Constants.PACKAGE_SPECIFICATION_VERSION);
            if ((v != null) && (sv != null))
            {
                // Verify they are equal.
                if (!((String) v).trim().equals(((String) sv).trim()))
                {
                    throw new IllegalArgumentException(
                        "Both version and specification-version are specified, but they are not equal.");
                }
            }

            // Ensure that only the "version" attribute is used and convert
            // it to the VersionRange type.
            if ((v != null) || (sv != null))
            {
                clause.m_attrs.remove(Constants.PACKAGE_SPECIFICATION_VERSION);
                v = (v == null) ? sv : v;
                clause.m_attrs.put(
                    Constants.VERSION_ATTRIBUTE,
                    new VersionRange(v.toString()));
            }

            // If bundle version is specified, then convert its type to VersionRange.
            v = clause.m_attrs.get(Constants.BUNDLE_VERSION_ATTRIBUTE);
            if (v != null)
            {
                clause.m_attrs.put(
                    Constants.BUNDLE_VERSION_ATTRIBUTE,
                    new VersionRange(v.toString()));
            }

            // Verify no duplicate imports.
            for (String pkgName : clause.m_paths)
            {
                if (!dupeSet.contains(pkgName))
                {

                    // The character "." has no meaning in the OSGi spec except
                    // when placed on the bundle class path. Some people, however,
                    // mistakenly think it means the default package when imported
                    // or exported. This is not correct. It is invalid.
                    if (pkgName.equals("."))
                    {
                        throw new BundleException("Imporing '.' is invalid.");
                    }
                    // Make sure a package name was specified.
                    else if (pkgName.length() == 0)
                    {
                        throw new BundleException(
                            "Imported package names cannot be zero length.");
                    }
                    dupeSet.add(pkgName);
                }
                else
                {
                    throw new BundleException("Duplicate import: " + pkgName);
                }
            }

            if (!mv.equals("2"))
            {
                // R3 bundles cannot have directives on their imports.
                if (!clause.m_dirs.isEmpty())
                {
                    throw new BundleException("R3 imports cannot contain directives.");
                }

                // Remove and ignore all attributes other than version.
                // NOTE: This is checking for "version" rather than "specification-version"
                // because the package class normalizes to "version" to avoid having
                // future special cases. This could be changed if more strict behavior
                // is required.
                if (!clause.m_attrs.isEmpty())
                {
                    // R3 package requirements should only have version attributes.
                    Object pkgVersion = clause.m_attrs.get(BundleCapabilityImpl.VERSION_ATTR);
                    pkgVersion = (pkgVersion == null)
                        ? new VersionRange(VersionRange.LEFT_CLOSED, Version.emptyVersion, null, VersionRange.RIGHT_CLOSED)
                        : pkgVersion;
                    for (Entry<String, Object> entry : clause.m_attrs.entrySet())
                    {
                        if (!entry.getKey().equals(BundleCapabilityImpl.VERSION_ATTR))
                        {
                            logger.log(Logger.LOG_WARNING,
                                "Unknown R3 import attribute: "
                                    + entry.getKey());
                        }
                    }

                    // Remove all other attributes except package version.
                    clause.m_attrs.clear();
                    clause.m_attrs.put(BundleCapabilityImpl.VERSION_ATTR, pkgVersion);
                }
            }
        }

        return clauses;
    }

    public static List<BundleRequirement> parseDynamicImportHeader(
        Logger logger, BundleRevision owner, String header)
        throws BundleException
    {

        List<ParsedHeaderClause> importClauses = parseStandardHeader(header);
        importClauses = normalizeDynamicImportClauses(logger, importClauses, "2");
        List<BundleRequirement> reqs = convertImports(importClauses, owner);
        return reqs;
    }

    private static List<BundleRequirement> convertImports(
        List<ParsedHeaderClause> clauses, BundleRevision owner)
    {
        // Now convert generic header clauses into requirements.
        List<BundleRequirement> reqList = new ArrayList<BundleRequirement>();
        for (ParsedHeaderClause clause : clauses)
        {
            for (String path : clause.m_paths)
            {
                // Prepend the package name to the array of attributes.
                Map<String, Object> attrs = clause.m_attrs;
                // Note that we use a linked hash map here to ensure the
                // package attribute is first, which will make indexing
                // more efficient.
// TODO: OSGi R4.3 - This is ordering is kind of hacky.
                // Prepend the package name to the array of attributes.
                Map<String, Object> newAttrs = new LinkedHashMap<String, Object>(attrs.size() + 1);
                // We want this first from an indexing perspective.
                newAttrs.put(
                    BundleRevision.PACKAGE_NAMESPACE,
                    path);
                newAttrs.putAll(attrs);
                // But we need to put it again to make sure it wasn't overwritten.
                newAttrs.put(
                    BundleRevision.PACKAGE_NAMESPACE,
                    path);

                // Create filter now so we can inject filter directive.
                SimpleFilter sf = SimpleFilter.convert(newAttrs);

                // Inject filter directive.
// TODO: OSGi R4.3 - Can we insert this on demand somehow?
                Map<String, String> dirs = clause.m_dirs;
                Map<String, String> newDirs = new HashMap<String, String>(dirs.size() + 1);
                newDirs.putAll(dirs);
                newDirs.put(
                    Constants.FILTER_DIRECTIVE,
                    sf.toString());

                // Create package requirement and add to requirement list.
                reqList.add(
                    new BundleRequirementImpl(
                        owner,
                        BundleRevision.PACKAGE_NAMESPACE,
                        newDirs,
                        Collections.EMPTY_MAP,
                        sf));
            }
        }

        return reqList;
    }

    private static List<ParsedHeaderClause> normalizeDynamicImportClauses(
        Logger logger, List<ParsedHeaderClause> clauses, String mv)
        throws BundleException
    {
        // Verify that the values are equals if the package specifies
        // both version and specification-version attributes.
        for (ParsedHeaderClause clause : clauses)
        {
            if (!mv.equals("2"))
            {
                // R3 bundles cannot have directives on their imports.
                if (!clause.m_dirs.isEmpty())
                {
                    throw new BundleException("R3 imports cannot contain directives.");
                }
            }

            // Add the resolution directive to indicate that these are
            // dynamic imports.
            clause.m_dirs.put(Constants.RESOLUTION_DIRECTIVE,
                FelixConstants.RESOLUTION_DYNAMIC);

            // Check for "version" and "specification-version" attributes
            // and verify they are the same if both are specified.
            Object v = clause.m_attrs.get(Constants.VERSION_ATTRIBUTE);
            Object sv = clause.m_attrs.get(Constants.PACKAGE_SPECIFICATION_VERSION);
            if ((v != null) && (sv != null))
            {
                // Verify they are equal.
                if (!((String) v).trim().equals(((String) sv).trim()))
                {
                    throw new IllegalArgumentException(
                        "Both version and specification-version are specified, but they are not equal.");
                }
            }

            // Ensure that only the "version" attribute is used and convert
            // it to the VersionRange type.
            if ((v != null) || (sv != null))
            {
                clause.m_attrs.remove(Constants.PACKAGE_SPECIFICATION_VERSION);
                v = (v == null) ? sv : v;
                clause.m_attrs.put(
                    Constants.VERSION_ATTRIBUTE,
                    new VersionRange(v.toString()));
            }

            // If bundle version is specified, then convert its type to VersionRange.
            v = clause.m_attrs.get(Constants.BUNDLE_VERSION_ATTRIBUTE);
            if (v != null)
            {
                clause.m_attrs.put(
                    Constants.BUNDLE_VERSION_ATTRIBUTE,
                    new VersionRange(v.toString()));
            }

            // Dynamic imports can have duplicates, verify that no partial package name wild carding is used
            for (String pkgName : clause.m_paths)
            {
                if (!pkgName.equals("*") && pkgName.endsWith("*") && !pkgName.endsWith(".*"))
                {
                    throw new BundleException(
                        "Partial package name wild carding is not allowed: " + pkgName);
                }
            }
        }

        return clauses;
    }

    private static List<BundleRequirement> convertRequireCapabilities(
        List<ParsedHeaderClause> clauses, BundleRevision owner)
        throws BundleException
    {
        // Now convert generic header clauses into requirements.
        List<BundleRequirement> reqList = new ArrayList<BundleRequirement>();
        for (ParsedHeaderClause clause : clauses)
        {
            try
            {
                String filterStr = clause.m_dirs.get(Constants.FILTER_DIRECTIVE);
                SimpleFilter sf = (filterStr != null)
                    ? SimpleFilter.parse(filterStr)
                    : new SimpleFilter(null, null, SimpleFilter.MATCH_ALL);
                for (String path : clause.m_paths)
                {
                    if (path.startsWith("osgi.wiring."))
                    {
                        throw new BundleException("Manifest cannot use Require-Capability for '"
                            + path
                            + "' namespace.");
                    }

                    // Create requirement and add to requirement list.
                    reqList.add(
                        new BundleRequirementImpl(
                            owner,
                            path,
                            clause.m_dirs,
                            clause.m_attrs,
                            sf));
                }
            }
            catch (Exception ex)
            {
                throw new BundleException("Error creating requirement: " + ex);
            }
        }

        return reqList;
    }
    
    static List<BundleRequirement> convertNativeCode(BundleRevision owner, List<NativeLibraryClause> nativeLibraryClauses, boolean hasOptionalLibraryDirective)
    {
        List<BundleRequirement> result = new ArrayList<BundleRequirement>();
        
        List<SimpleFilter> nativeFilterClauseList = new ArrayList<SimpleFilter>();
        
        if(nativeLibraryClauses != null && !nativeLibraryClauses.isEmpty())
        {
            for(NativeLibraryClause clause: nativeLibraryClauses)
            {
                String[] osNameArray = clause.getOSNames();
                String[] osVersionArray = clause.getOSVersions();
                String[] processorArray = clause.getProcessors();
                String[] languageArray = clause.getLanguages();
                
                String currentSelectionFilter = clause.getSelectionFilter();
                
                List<SimpleFilter> nativeFilterList = new ArrayList<SimpleFilter>();
                if(osNameArray != null && osNameArray.length > 0)
                {
                    nativeFilterList.add(buildFilterFromArray(NativeNamespace.CAPABILITY_OSNAME_ATTRIBUTE, osNameArray, SimpleFilter.APPROX));
                }
                
                if(osVersionArray != null && osVersionArray.length > 0)
                {
                    nativeFilterList.add(buildFilterFromArray(NativeNamespace.CAPABILITY_OSVERSION_ATTRIBUTE, osVersionArray, SimpleFilter.EQ));
                }
                
                if(processorArray != null && processorArray.length > 0)
                {
                    nativeFilterList.add(buildFilterFromArray(NativeNamespace.CAPABILITY_PROCESSOR_ATTRIBUTE, processorArray, SimpleFilter.APPROX));
                }

                if(languageArray != null && languageArray.length > 0)
                {
                    nativeFilterList.add(buildFilterFromArray(NativeNamespace.CAPABILITY_LANGUAGE_ATTRIBUTE, languageArray, SimpleFilter.APPROX));
                }
                
                if(currentSelectionFilter != null)
                {
                    nativeFilterList.add(SimpleFilter.parse(currentSelectionFilter));
                }
                
                if(!nativeFilterList.isEmpty())
                {
                    SimpleFilter nativeClauseFilter = new SimpleFilter(null, nativeFilterList, SimpleFilter.AND);
                    nativeFilterClauseList.add(nativeClauseFilter);
                }
            }
            
            Map<String, String> requirementDirectives = new HashMap<String, String>();
            
            SimpleFilter consolidatedNativeFilter = null;
            
            if(hasOptionalLibraryDirective)
            {
                requirementDirectives.put(NativeNamespace.REQUIREMENT_RESOLUTION_DIRECTIVE, NativeNamespace.RESOLUTION_OPTIONAL);
            }
            
            if(nativeFilterClauseList.size() > 1)
            {
                consolidatedNativeFilter = new SimpleFilter(null, nativeFilterClauseList, SimpleFilter.OR);
                
                requirementDirectives.put(NativeNamespace.REQUIREMENT_FILTER_DIRECTIVE, consolidatedNativeFilter.toString());
            }
            else if(nativeFilterClauseList.size() == 1)
            {
                consolidatedNativeFilter = nativeFilterClauseList.get(0);
                
                requirementDirectives.put(NativeNamespace.REQUIREMENT_FILTER_DIRECTIVE, consolidatedNativeFilter.toString());
            }
            
            if(requirementDirectives.size() > 0)
            {
                result.add(new BundleRequirementImpl(owner, NativeNamespace.NATIVE_NAMESPACE, requirementDirectives,
                        Collections.<String, Object>emptyMap(),
                        consolidatedNativeFilter));
            }
            
        }
        
        return result;
    }
    
    private static SimpleFilter buildFilterFromArray(String attributeName, String[] stringArray, int operation)
    {
        SimpleFilter result = null;
        List<SimpleFilter> filterSet = new ArrayList<SimpleFilter>();
        
        if(stringArray != null)
        {
            for(String currentValue : stringArray)
            {
                filterSet.add(new SimpleFilter(attributeName, currentValue.toLowerCase(), operation));
            }
            
            if(filterSet.size() == 1)
            {
                result = filterSet.get(0);
            }
            else
            {
                result = new SimpleFilter(null, filterSet, SimpleFilter.OR);
            }
        }
        
        return result;
    }
    
    private static List<ParsedHeaderClause> normalizeCapabilityClauses(
        Logger logger, List<ParsedHeaderClause> clauses, String mv)
        throws BundleException
    {

        if (mv != null && !mv.equals("2") && !clauses.isEmpty())
        {
            // Should we error here if we are not an R4 bundle?
        }

        // Convert attributes into specified types.
        for (ParsedHeaderClause clause : clauses)
        {
            for (Entry<String, String> entry : clause.m_types.entrySet())
            {
                String type = entry.getValue();
                if (!type.equals("String"))
                {
                    if (type.equals("Double"))
                    {
                        clause.m_attrs.put(
                            entry.getKey(),
                            new Double(clause.m_attrs.get(entry.getKey()).toString().trim()));
                    }
                    else if (type.equals("Version"))
                    {
                        clause.m_attrs.put(
                            entry.getKey(),
                            new Version(clause.m_attrs.get(entry.getKey()).toString().trim()));
                    }
                    else if (type.equals("Long"))
                    {
                        clause.m_attrs.put(
                            entry.getKey(),
                            new Long(clause.m_attrs.get(entry.getKey()).toString().trim()));
                    }
                    else if (type.startsWith("List"))
                    {
                        int startIdx = type.indexOf('<');
                        int endIdx = type.indexOf('>');
                        if (((startIdx > 0) && (endIdx <= startIdx))
                            || ((startIdx < 0) && (endIdx > 0)))
                        {
                            throw new BundleException(
                                "Invalid Provide-Capability attribute list type for '"
                                + entry.getKey()
                                + "' : "
                                + type);
                        }

                        String listType = "String";
                        if (endIdx > startIdx)
                        {
                            listType = type.substring(startIdx + 1, endIdx).trim();
                        }

                        List<String> tokens = parseDelimitedString(
                            clause.m_attrs.get(entry.getKey()).toString(), ",", false);
                        List<Object> values = new ArrayList<Object>(tokens.size());
                        for (String token : tokens)
                        {
                            if (listType.equals("String"))
                            {
                                values.add(token);
                            }
                            else if (listType.equals("Double"))
                            {
                                values.add(new Double(token.trim()));
                            }
                            else if (listType.equals("Version"))
                            {
                                values.add(new Version(token.trim()));
                            }
                            else if (listType.equals("Long"))
                            {
                                values.add(new Long(token.trim()));
                            }
                            else
                            {
                                throw new BundleException(
                                    "Unknown Provide-Capability attribute list type for '"
                                    + entry.getKey()
                                    + "' : "
                                    + type);
                            }
                        }
                        clause.m_attrs.put(
                            entry.getKey(),
                            values);
                    }
                    else
                    {
                        throw new BundleException(
                            "Unknown Provide-Capability attribute type for '"
                            + entry.getKey()
                            + "' : "
                            + type);
                    }
                }
            }
        }

        return clauses;
    }

    private static List<BundleCapability> convertProvideCapabilities(
        List<ParsedHeaderClause> clauses, BundleRevision owner)
        throws BundleException
    {
        List<BundleCapability> capList = new ArrayList<BundleCapability>();
        for (ParsedHeaderClause clause : clauses)
        {
            for (String path : clause.m_paths)
            {
                if (path.startsWith("osgi.wiring."))
                {
                    throw new BundleException("Manifest cannot use Provide-Capability for '"
                        + path
                        + "' namespace.");
                }
                
                if((path.startsWith(ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE) ||
                    path.startsWith(NativeNamespace.NATIVE_NAMESPACE)) && (owner == null ||
                    !FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME.equals(owner.getSymbolicName())))
                {
                    throw new BundleException("Only System Bundle can use Provide-Capability for '"
                            + path
                            + "' namespace.", BundleException.MANIFEST_ERROR);
                }

                // Create package capability and add to capability list.
                capList.add(
                    new BundleCapabilityImpl(
                        owner,
                        path,
                        clause.m_dirs,
                        clause.m_attrs));
            }
        }

        return capList;
    }

    private static List<ParsedHeaderClause> normalizeExportClauses(
        Logger logger, List<ParsedHeaderClause> clauses,
        String mv, String bsn, Version bv, boolean connectModule)
        throws BundleException
    {
        for (ParsedHeaderClause clause : clauses)
        {
            // Verify that the named package has not already been declared.
            for (String pkgName : clause.m_paths)
            {
                // Verify that java.* packages are not exported (except from the system bundle).
                if ((!FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME.equals(bsn) && !connectModule) && pkgName.startsWith("java."))
                {
                    throw new BundleException(
                        "Exporting java.* packages not allowed: "
                        + pkgName, BundleException.MANIFEST_ERROR);
                }
                // The character "." has no meaning in the OSGi spec except
                // when placed on the bundle class path. Some people, however,
                // mistakenly think it means the default package when imported
                // or exported. This is not correct. It is invalid.
                else if (pkgName.equals("."))
                {
                    throw new BundleException("Exporing '.' is invalid.");
                }
                // Make sure a package name was specified.
                else if (pkgName.length() == 0)
                {
                    throw new BundleException(
                        "Exported package names cannot be zero length.");
                }
            }

            // Check for "version" and "specification-version" attributes
            // and verify they are the same if both are specified.
            Object v = clause.m_attrs.get(Constants.VERSION_ATTRIBUTE);
            Object sv = clause.m_attrs.get(Constants.PACKAGE_SPECIFICATION_VERSION);
            if ((v != null) && (sv != null))
            {
                // Verify they are equal.
                if (!((String) v).trim().equals(((String) sv).trim()))
                {
                    throw new IllegalArgumentException(
                        "Both version and specification-version are specified, but they are not equal.");
                }
            }

            // Always add the default version if not specified.
            if ((v == null) && (sv == null))
            {
                v = Version.emptyVersion;
            }

            // Ensure that only the "version" attribute is used and convert
            // it to the appropriate type.
            if ((v != null) || (sv != null))
            {
                // Convert version attribute to type Version.
                clause.m_attrs.remove(Constants.PACKAGE_SPECIFICATION_VERSION);
                v = (v == null) ? sv : v;
                clause.m_attrs.put(
                    Constants.VERSION_ATTRIBUTE,
                    Version.parseVersion(v.toString()));
            }

            // If this is an R4 bundle, then make sure it doesn't specify
            // bundle symbolic name or bundle version attributes.
            if (mv.equals("2"))
            {
                // Find symbolic name and version attribute, if present.
                if (clause.m_attrs.containsKey(Constants.BUNDLE_VERSION_ATTRIBUTE)
                    || clause.m_attrs.containsKey(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE))
                {
                    throw new BundleException(
                        "Exports must not specify bundle symbolic name or bundle version.");
                }

                // Now that we know that there are no bundle symbolic name and version
                // attributes, add them since the spec says they are there implicitly.
                clause.m_attrs.put(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE, bsn);
                clause.m_attrs.put(Constants.BUNDLE_VERSION_ATTRIBUTE, bv);
            }
            else if (!mv.equals("2"))
            {
                // R3 bundles cannot have directives on their exports.
                if (!clause.m_dirs.isEmpty())
                {
                    throw new BundleException("R3 exports cannot contain directives.");
                }

                // Remove and ignore all attributes other than version.
                // NOTE: This is checking for "version" rather than "specification-version"
                // because the package class normalizes to "version" to avoid having
                // future special cases. This could be changed if more strict behavior
                // is required.
                if (!clause.m_attrs.isEmpty())
                {
                    // R3 package capabilities should only have a version attribute.
                    Object pkgVersion = clause.m_attrs.get(BundleCapabilityImpl.VERSION_ATTR);
                    pkgVersion = (pkgVersion == null)
                        ? Version.emptyVersion
                        : pkgVersion;
                    for (Entry<String, Object> entry : clause.m_attrs.entrySet())
                    {
                        if (!entry.getKey().equals(BundleCapabilityImpl.VERSION_ATTR))
                        {
                            logger.log(
                                Logger.LOG_WARNING,
                                "Unknown R3 export attribute: "
                                + entry.getKey());
                        }
                    }

                    // Remove all other attributes except package version.
                    clause.m_attrs.clear();
                    clause.m_attrs.put(BundleCapabilityImpl.VERSION_ATTR, pkgVersion);
                }
            }
        }

        return clauses;
    }

    private static List<BundleCapability> convertExports(
        List<ParsedHeaderClause> clauses, BundleRevision owner)
    {
        List<BundleCapability> capList = new ArrayList<BundleCapability>();
        for (ParsedHeaderClause clause : clauses)
        {
            for (String pkgName : clause.m_paths)
            {
                // Prepend the package name to the array of attributes.
                Map<String, Object> attrs = clause.m_attrs;
                Map<String, Object> newAttrs = new HashMap<String, Object>(attrs.size() + 1);
                newAttrs.putAll(attrs);
                newAttrs.put(
                    BundleRevision.PACKAGE_NAMESPACE,
                    pkgName);

                // Create package capability and add to capability list.
                capList.add(
                    new BundleCapabilityImpl(
                        owner,
                        BundleRevision.PACKAGE_NAMESPACE,
                        clause.m_dirs,
                        newAttrs));
            }
        }

        return capList;
    }

    public String getManifestVersion()
    {
        String manifestVersion = getManifestVersion(m_headerMap);
        return (manifestVersion == null) ? "1" : manifestVersion;
    }

    private static String getManifestVersion(Map<String, Object> headerMap)
    {
        String manifestVersion = (String) headerMap.get(Constants.BUNDLE_MANIFESTVERSION);
        return (manifestVersion == null) ? null : manifestVersion.trim();
    }

    public int getActivationPolicy()
    {
        return m_activationPolicy;
    }

    public String getActivationIncludeDirective()
    {
        return m_activationIncludeDir;
    }

    public String getActivationExcludeDirective()
    {
        return m_activationExcludeDir;
    }

    public boolean isExtension()
    {
        return m_isExtension;
    }

    public String getSymbolicName()
    {
        return m_bundleSymbolicName;
    }

    public Version getBundleVersion()
    {
        return m_bundleVersion;
    }

    public List<BundleCapability> getCapabilities()
    {
        return m_capabilities;
    }

    public List<BundleRequirement> getRequirements()
    {
        return m_requirements;
    }

    /**
     * <p>
     * This method returns the selected native library metadata from
     * the manifest. The information is not the raw metadata from the
     * manifest, but is the native library clause selected according
     * to the OSGi native library clause selection policy. The metadata
     * returned by this method will be attached directly to a module and
     * used for finding its native libraries at run time. To inspect the
     * raw native library metadata refer to <tt>getLibraryClauses()</tt>.
     * </p>
     * <p>
     * This method returns one of three values:
     * </p>
     * <ul>
     * <li><tt>null</tt> - if the are no native libraries for this module;
     *     this may also indicate the native libraries are optional and
     *     did not match the current platform.</li>
     * <li>Zero-length <tt>NativeLibrary</tt> array - if no matching native library
     *     clause was found; this bundle should not resolve.</li>
     * <li>Nonzero-length <tt>NativeLibrary</tt> array - the native libraries
     *     associated with the matching native library clause.</li>
     * </ul>
     *
     * @return <tt>null</tt> if there are no native libraries, a zero-length
     *         array if no libraries matched, or an array of selected libraries.
    **/
    public List<NativeLibrary> getLibraries()
    {
        ArrayList<NativeLibrary> libs = null;
        try
        {
            NativeLibraryClause clause = getSelectedLibraryClause();
            if (clause != null)
            {
                String[] entries = clause.getLibraryEntries();
                libs = new ArrayList<NativeLibrary>(entries.length);
                int current = 0;
                for (int i = 0; i < entries.length; i++)
                {
                    String name = getName(entries[i]);
                    boolean found = false;
                    for (int j = 0; !found && (j < current); j++)
                    {
                        found = getName(entries[j]).equals(name);
                    }
                    if (!found)
                    {
                        libs.add(new NativeLibrary(
                            clause.getLibraryEntries()[i],
                            clause.getOSNames(), clause.getProcessors(), clause.getOSVersions(),
                            clause.getLanguages(), clause.getSelectionFilter()));
                    }
                }
                libs.trimToSize();
            }
        }
        catch (Exception ex)
        {
            libs = new ArrayList<NativeLibrary>(0);
        }
        return libs;
    }

    private String getName(String path)
    {
        int idx = path.lastIndexOf('/');
        if (idx > -1)
        {
            return path.substring(idx);
        }
        return path;
    }

    private NativeLibraryClause getSelectedLibraryClause() throws BundleException
    {
        if ((m_libraryClauses != null) && (m_libraryClauses.size() > 0))
        {
            List<NativeLibraryClause> clauseList = new ArrayList<NativeLibraryClause>();

            // Search for matching native clauses.
            for (NativeLibraryClause libraryClause : m_libraryClauses)
            {
                if (libraryClause.match(m_configMap))
                {
                    clauseList.add(libraryClause);
                }
            }

            // Select the matching native clause.
            int selected = 0;
            if (clauseList.isEmpty())
            {
                // If optional clause exists, no error thrown.
                if (m_libraryHeadersOptional)
                {
                    return null;
                }
                else
                {
                    throw new BundleException("Unable to select a native library clause.");
                }
            }
            else if (clauseList.size() == 1)
            {
                selected = 0;
            }
            else if (clauseList.size() > 1)
            {
                selected = firstSortedClause(clauseList);
            }
            return ((NativeLibraryClause) clauseList.get(selected));
        }

        return null;
    }

    private int firstSortedClause(List<NativeLibraryClause> clauseList)
    {
        ArrayList<String> indexList = new ArrayList<String>();
        ArrayList<String> selection = new ArrayList<String>();

        // Init index list
        for (int i = 0; i < clauseList.size(); i++)
        {
            indexList.add("" + i);
        }

        // Select clause with 'osversion' range declared
        // and get back the max floor of 'osversion' ranges.
        Version osVersionRangeMaxFloor = new Version(0, 0, 0);
        for (int i = 0; i < indexList.size(); i++)
        {
            int index = Integer.parseInt(indexList.get(i).toString());
            String[] osversions = ((NativeLibraryClause) clauseList.get(index)).getOSVersions();
            if (osversions != null)
            {
                selection.add("" + indexList.get(i));
            }
            for (int k = 0; (osversions != null) && (k < osversions.length); k++)
            {
                VersionRange range = new VersionRange(osversions[k]);
                if ((range.getLeft()).compareTo(osVersionRangeMaxFloor) >= 0)
                {
                    osVersionRangeMaxFloor = range.getLeft();
                }
            }
        }

        if (selection.size() == 1)
        {
            return Integer.parseInt(selection.get(0).toString());
        }
        else if (selection.size() > 1)
        {
            // Keep only selected clauses with an 'osversion'
            // equal to the max floor of 'osversion' ranges.
            indexList = selection;
            selection = new ArrayList<String>();
            for (int i = 0; i < indexList.size(); i++)
            {
                int index = Integer.parseInt(indexList.get(i).toString());
                String[] osversions = ((NativeLibraryClause) clauseList.get(index)).getOSVersions();
                for (int k = 0; k < osversions.length; k++)
                {
                    VersionRange range = new VersionRange(osversions[k]);
                    if ((range.getLeft()).compareTo(osVersionRangeMaxFloor) >= 0)
                    {
                        selection.add("" + indexList.get(i));
                    }
                }
            }
        }

        if (selection.isEmpty())
        {
            // Re-init index list.
            selection.clear();
            indexList.clear();
            for (int i = 0; i < clauseList.size(); i++)
            {
                indexList.add("" + i);
            }
        }
        else if (selection.size() == 1)
        {
            return Integer.parseInt(selection.get(0).toString());
        }
        else
        {
            indexList = selection;
            selection.clear();
        }

        // Keep only clauses with 'language' declared.
        for (int i = 0; i < indexList.size(); i++)
        {
            int index = Integer.parseInt(indexList.get(i).toString());
            if (((NativeLibraryClause) clauseList.get(index)).getLanguages() != null)
            {
                selection.add("" + indexList.get(i));
            }
        }

        // Return the first sorted clause
        if (selection.isEmpty())
        {
            return 0;
        }
        else
        {
            return Integer.parseInt(selection.get(0).toString());
        }
    }

    private static List<ParsedHeaderClause> calculateImplicitImports(
        List<BundleCapability> exports, List<ParsedHeaderClause> imports)
        throws BundleException
    {
        List<ParsedHeaderClause> clauseList = new ArrayList<ParsedHeaderClause>();

        // Since all R3 exports imply an import, add a corresponding
        // requirement for each existing export capability. Do not
        // duplicate imports.
        Map<String, String> map =  new HashMap<String, String>();
        // Add existing imports.
        for (int impIdx = 0; impIdx < imports.size(); impIdx++)
        {
            for (int pathIdx = 0; pathIdx < imports.get(impIdx).m_paths.size(); pathIdx++)
            {
                map.put(
                    imports.get(impIdx).m_paths.get(pathIdx),
                    imports.get(impIdx).m_paths.get(pathIdx));
            }
        }
        // Add import requirement for each export capability.
        for (int i = 0; i < exports.size(); i++)
        {
            if (map.get(exports.get(i).getAttributes()
                .get(BundleRevision.PACKAGE_NAMESPACE)) == null)
            {
                // Convert Version to VersionRange.
                Map<String, Object> attrs = new HashMap<String, Object>();
                Object version = exports.get(i).getAttributes().get(Constants.VERSION_ATTRIBUTE);
                if (version != null)
                {
                    attrs.put(
                        Constants.VERSION_ATTRIBUTE,
                        new VersionRange(version.toString()));
                }

                List<String> paths = new ArrayList<String>();
                paths.add((String)
                    exports.get(i).getAttributes().get(BundleRevision.PACKAGE_NAMESPACE));
                clauseList.add(
                    new ParsedHeaderClause(
                        paths, Collections.EMPTY_MAP, attrs, Collections.EMPTY_MAP));
            }
        }

        return clauseList;
    }

    private static List<BundleCapability> calculateImplicitUses(
        List<BundleCapability> exports, List<ParsedHeaderClause> imports)
        throws BundleException
    {
        // Add a "uses" directive onto each export of R3 bundles
        // that references every other import (which will include
        // exports, since export implies import); this is
        // necessary since R3 bundles assumed a single class space,
        // but R4 allows for multiple class spaces.
        String usesValue = "";
        for (int i = 0; i < imports.size(); i++)
        {
            for (int pathIdx = 0; pathIdx < imports.get(i).m_paths.size(); pathIdx++)
            {
                usesValue = usesValue
                    + ((usesValue.length() > 0) ? "," : "")
                    + imports.get(i).m_paths.get(pathIdx);
            }
        }
        for (int i = 0; i < exports.size(); i++)
        {
            Map<String, String> dirs = new HashMap<String, String>(1);
            dirs.put(Constants.USES_DIRECTIVE, usesValue);
            exports.set(i, new BundleCapabilityImpl(
                exports.get(i).getRevision(),
                BundleRevision.PACKAGE_NAMESPACE,
                dirs,
                exports.get(i).getAttributes()));
        }

        return exports;
    }

    private static boolean checkExtensionBundle(Map<String, Object> headerMap) throws BundleException
    {
        Object extension = parseExtensionBundleHeader(
            (String) headerMap.get(Constants.FRAGMENT_HOST));

        if (extension != null)
        {
            if (!(Constants.EXTENSION_FRAMEWORK.equals(extension) ||
                Constants.EXTENSION_BOOTCLASSPATH.equals(extension)))
            {
                throw new BundleException(
                    "Extension bundle must have either 'extension:=framework' or 'extension:=bootclasspath'");
            }
            if (headerMap.containsKey(Constants.REQUIRE_BUNDLE) ||
                headerMap.containsKey(Constants.BUNDLE_NATIVECODE) ||
                headerMap.containsKey(Constants.DYNAMICIMPORT_PACKAGE) ||
                headerMap.containsKey(Constants.BUNDLE_ACTIVATOR))
            {
                throw new BundleException("Invalid extension bundle manifest");
            }
            return true;
        }
        return false;
    }

    private static BundleCapabilityImpl parseBundleSymbolicName(Logger logger,
        BundleRevision owner, Map<String, Object> headerMap)
        throws BundleException
    {
        List<ParsedHeaderClause> clauses = normalizeCapabilityClauses(logger, parseStandardHeader(
            (String) headerMap.get(Constants.BUNDLE_SYMBOLICNAME)), getManifestVersion(headerMap));
        if (clauses.size() > 0)
        {
            if (clauses.size() > 1)
            {
                throw new BundleException(
                    "Cannot have multiple symbolic names: "
                        + headerMap.get(Constants.BUNDLE_SYMBOLICNAME));
            }
            else if (clauses.get(0).m_paths.size() > 1)
            {
                throw new BundleException(
                    "Cannot have multiple symbolic names: "
                        + headerMap.get(Constants.BUNDLE_SYMBOLICNAME));
            }
            else if (clauses.get(0).m_attrs.containsKey(Constants.BUNDLE_VERSION))
            {
                throw new BundleException(
                    "Cannot have a bundle version: " + headerMap.get(Constants.BUNDLE_VERSION));
            }

            // Get bundle version.
            Version bundleVersion = Version.emptyVersion;
            if (headerMap.get(Constants.BUNDLE_VERSION) != null)
            {
                try
                {
                    bundleVersion = Version.parseVersion(
                        (String) headerMap.get(Constants.BUNDLE_VERSION));
                }
                catch (RuntimeException ex)
                {
                    // R4 bundle versions must parse, R3 bundle version may not.
                    String mv = getManifestVersion(headerMap);
                    if (mv != null)
                    {
                        throw ex;
                    }
                    bundleVersion = Version.emptyVersion;
                }
            }

            Object tagList = clauses.get(0).m_attrs.get(IdentityNamespace.CAPABILITY_TAGS_ATTRIBUTE);
            LinkedHashSet<String> tags = new LinkedHashSet<>();
            if (tagList != null)
            {
                if (tagList instanceof List)
                {
                    for (Object member : ((List) tagList))
                    {
                        if (member instanceof String)
                        {
                            tags.add((String) member);
                        }
                        else
                        {
                            throw new BundleException("Invalid tags list: " + headerMap.get(Constants.BUNDLE_SYMBOLICNAME));
                        }
                    }
                }
                else if (tagList instanceof String)
                {
                    tags.add((String) tagList);
                }
                else
                {
                    throw new BundleException("Invalid tags list: " + headerMap.get(Constants.BUNDLE_SYMBOLICNAME));
                }
            }

            if (tags.contains(ConnectContent.TAG_OSGI_CONNECT))
            {
                throw new BundleException("Invalid tags list: " + headerMap.get(Constants.BUNDLE_SYMBOLICNAME));
            }
            if (owner != null && ((BundleRevisionImpl) owner).getContent() instanceof ConnectContentContent)
            {
                tags.add(ConnectContent.TAG_OSGI_CONNECT);
            }

            if (!tags.isEmpty())
            {
                clauses.get(0).m_attrs.put(IdentityNamespace.CAPABILITY_TAGS_ATTRIBUTE, new ArrayList<>(tags));
            }

            // Create a require capability and return it.
            String symName = (String) clauses.get(0).m_paths.get(0);
            clauses.get(0).m_attrs.put(BundleRevision.BUNDLE_NAMESPACE, symName);
            clauses.get(0).m_attrs.put(Constants.BUNDLE_VERSION_ATTRIBUTE, bundleVersion);
            return new BundleCapabilityImpl(
                owner,
                BundleRevision.BUNDLE_NAMESPACE,
                clauses.get(0).m_dirs,
                clauses.get(0).m_attrs);
        }

        return null;
    }

    private static BundleCapabilityImpl addIdentityCapability(BundleRevision owner,
        Map<String, Object> headerMap, BundleCapabilityImpl bundleCap) throws BundleException
    {
        Map<String, Object> attrs = new HashMap<String, Object>(bundleCap.getAttributes());

        attrs.put(IdentityNamespace.IDENTITY_NAMESPACE,
            bundleCap.getAttributes().get(BundleNamespace.BUNDLE_NAMESPACE));
        attrs.put(IdentityNamespace.CAPABILITY_TYPE_ATTRIBUTE,
            headerMap.get(Constants.FRAGMENT_HOST) == null
            ? IdentityNamespace.TYPE_BUNDLE
            : IdentityNamespace.TYPE_FRAGMENT);
        attrs.put(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE,
            bundleCap.getAttributes().get(Constants.BUNDLE_VERSION_ATTRIBUTE));

        if (headerMap.get(Constants.BUNDLE_COPYRIGHT) != null)
        {
            attrs.put(IdentityNamespace.CAPABILITY_COPYRIGHT_ATTRIBUTE,
                headerMap.get(Constants.BUNDLE_COPYRIGHT));
        }

        if (headerMap.get(Constants.BUNDLE_DESCRIPTION) != null)
        {
            attrs.put(IdentityNamespace.CAPABILITY_DESCRIPTION_ATTRIBUTE,
                headerMap.get(Constants.BUNDLE_DESCRIPTION));
        }
        if (headerMap.get(Constants.BUNDLE_DOCURL) != null)
        {
            attrs.put(IdentityNamespace.CAPABILITY_DOCUMENTATION_ATTRIBUTE,
                headerMap.get(Constants.BUNDLE_DOCURL));
        }
        if (headerMap.get(BUNDLE_LICENSE_HEADER) != null)
        {
            attrs.put(IdentityNamespace.CAPABILITY_LICENSE_ATTRIBUTE,
                headerMap.get(BUNDLE_LICENSE_HEADER));
        }

        Map<String, String> dirs;
        if (bundleCap.getDirectives().get(Constants.SINGLETON_DIRECTIVE) != null)
        {
            dirs = Collections.singletonMap(IdentityNamespace.CAPABILITY_SINGLETON_DIRECTIVE,
                    bundleCap.getDirectives().get(Constants.SINGLETON_DIRECTIVE));
        }
        else
        {
            dirs = Collections.emptyMap();
        }
        return new BundleCapabilityImpl(owner, IdentityNamespace.IDENTITY_NAMESPACE, dirs, attrs);
    }

    private static List<BundleRequirementImpl> parseFragmentHost(
        Logger logger, BundleRevision owner, Map<String, Object> headerMap)
        throws BundleException
    {
        List<BundleRequirementImpl> reqs = new ArrayList<BundleRequirementImpl>();

        String mv = getManifestVersion(headerMap);
        if ((mv != null) && mv.equals("2"))
        {
            List<ParsedHeaderClause> clauses = parseStandardHeader(
                (String) headerMap.get(Constants.FRAGMENT_HOST));
            if (clauses.size() > 0)
            {
                // Make sure that only one fragment host symbolic name is specified.
                if (clauses.size() > 1)
                {
                    throw new BundleException(
                        "Fragments cannot have multiple hosts: "
                            + headerMap.get(Constants.FRAGMENT_HOST));
                }
                else if (clauses.get(0).m_paths.size() > 1)
                {
                    throw new BundleException(
                        "Fragments cannot have multiple hosts: "
                            + headerMap.get(Constants.FRAGMENT_HOST));
                }

                // If the bundle-version attribute is specified, then convert
                // it to the proper type.
                Object value = clauses.get(0).m_attrs.get(Constants.BUNDLE_VERSION_ATTRIBUTE);
                value = (value == null) ? "0.0.0" : value;
                if (value != null)
                {
                    clauses.get(0).m_attrs.put(
                        Constants.BUNDLE_VERSION_ATTRIBUTE,
                        new VersionRange(value.toString()));
                }

                // Note that we use a linked hash map here to ensure the
                // host symbolic name is first, which will make indexing
                // more efficient.
// TODO: OSGi R4.3 - This is ordering is kind of hacky.
                // Prepend the host symbolic name to the map of attributes.
                Map<String, Object> attrs = clauses.get(0).m_attrs;
                Map<String, Object> newAttrs = new LinkedHashMap<String, Object>(attrs.size() + 1);
                // We want this first from an indexing perspective.
                newAttrs.put(
                    BundleRevision.HOST_NAMESPACE,
                    clauses.get(0).m_paths.get(0));
                newAttrs.putAll(attrs);
                // But we need to put it again to make sure it wasn't overwritten.
                newAttrs.put(
                    BundleRevision.HOST_NAMESPACE,
                    clauses.get(0).m_paths.get(0));

                // Create filter now so we can inject filter directive.
                SimpleFilter sf = SimpleFilter.convert(newAttrs);

                // Inject filter directive.
// TODO: OSGi R4.3 - Can we insert this on demand somehow?
                Map<String, String> dirs = clauses.get(0).m_dirs;
                Map<String, String> newDirs = new HashMap<String, String>(dirs.size() + 1);
                newDirs.putAll(dirs);
                newDirs.put(
                    Constants.FILTER_DIRECTIVE,
                    sf.toString());

                reqs.add(new BundleRequirementImpl(
                    owner, BundleRevision.HOST_NAMESPACE,
                    newDirs,
                    newAttrs));
            }
        }
        else if (headerMap.get(Constants.FRAGMENT_HOST) != null)
        {
            String s = (String) headerMap.get(Constants.BUNDLE_SYMBOLICNAME);
            s = (s == null) ? (String) headerMap.get(Constants.BUNDLE_NAME) : s;
            s = (s == null) ? headerMap.toString() : s;
            logger.log(
                Logger.LOG_WARNING,
                "Only R4 bundles can be fragments: " + s);
        }

        return reqs;
    }

    private static List<BundleRequirement> parseBreeHeader(String header, BundleRevision owner)
    {
        List<String> filters = new ArrayList<String>();
        for (String entry : parseDelimitedString(header, ","))
        {
            List<String> names = parseDelimitedString(entry, "/");
            List<String> left = parseDelimitedString(names.get(0), "-");

            String lName = left.get(0);
            Version lVer;
            try
            {
                lVer = Version.parseVersion(left.get(1));
            }
            catch (Exception ex)
            {
                // Version doesn't parse. Make it part of the name.
                lName = names.get(0);
                lVer = null;
            }

            String rName = null;
            Version rVer = null;
            if (names.size() > 1)
            {
                List<String> right = parseDelimitedString(names.get(1), "-");
                rName = right.get(0);
                try
                {
                    rVer = Version.parseVersion(right.get(1));
                }
                catch (Exception ex)
                {
                    rName = names.get(1);
                    rVer = null;
                }
            }

            String versionClause;
            if (lVer != null)
            {
                if ((rVer != null) && (!rVer.equals(lVer)))
                {
                    // Both versions are defined, but different. Make each of them part of the name
                    lName = names.get(0);
                    rName = names.get(1);
                    versionClause = null;
                }
                else
                {
                    versionClause = getBreeVersionClause(lVer);
                }
            }
            else
            {
                versionClause = getBreeVersionClause(rVer);
            }

            if ("J2SE".equals(lName))
            {
                // J2SE is not used in the Capability variant of BREE, use JavaSE here
                // This can only happen with the lName part...
                lName = "JavaSE";
            }

            String nameClause;
            if (rName != null)
                nameClause = "(" + ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE + "=" + lName + "/" + rName + ")";
            else
                nameClause = "(" + ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE + "=" + lName + ")";

            String filter;
            if (versionClause != null)
                filter = "(&" + nameClause + versionClause + ")";
            else
                filter = nameClause;

            filters.add(filter);
        }

        if (filters.size() == 0)
        {
            return Collections.emptyList();
        }
        else
        {
            String reqFilter;
            if (filters.size() == 1)
            {
                reqFilter = filters.get(0);
            }
            else
            {
                // If there are more BREE filters, we need to or them together
                StringBuilder sb = new StringBuilder("(|");
                for (String f : filters)
                {
                    sb.append(f);
                }
                sb.append(")");
                reqFilter = sb.toString();
            }

            SimpleFilter sf = SimpleFilter.parse(reqFilter);
            return Collections.<BundleRequirement>singletonList(new BundleRequirementImpl(
                owner,
                ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE,
                Collections.singletonMap(ExecutionEnvironmentNamespace.REQUIREMENT_FILTER_DIRECTIVE, reqFilter),
                Collections.<String, Object>emptyMap(),
                sf));
        }
    }

    private static String getBreeVersionClause(Version ver)
    {
        if (ver == null)
            return null;

        return "(" + ExecutionEnvironmentNamespace.CAPABILITY_VERSION_ATTRIBUTE + "=" + ver + ")";
    }

    private static List<ParsedHeaderClause> normalizeRequireClauses(
        Logger logger, List<ParsedHeaderClause> clauses, String mv)
    {
        // R3 bundles cannot require other bundles.
        if (!mv.equals("2"))
        {
            clauses.clear();
        }
        else
        {
            // Convert bundle version attribute to VersionRange type.
            for (ParsedHeaderClause clause : clauses)
            {
                Object value = clause.m_attrs.get(Constants.BUNDLE_VERSION_ATTRIBUTE);
                if (value != null)
                {
                    clause.m_attrs.put(
                        Constants.BUNDLE_VERSION_ATTRIBUTE,
                        new VersionRange(value.toString()));
                }
            }
        }

        return clauses;
    }

    private static List<BundleRequirementImpl> convertRequires(
        List<ParsedHeaderClause> clauses, BundleRevision owner)
    {
        List<BundleRequirementImpl> reqList = new ArrayList<BundleRequirementImpl>();
        for (ParsedHeaderClause clause : clauses)
        {
            for (String path : clause.m_paths)
            {
                // Prepend the bundle symbolic name to the array of attributes.
                Map<String, Object> attrs = clause.m_attrs;
                // Note that we use a linked hash map here to ensure the
                // symbolic name attribute is first, which will make indexing
                // more efficient.
// TODO: OSGi R4.3 - This is ordering is kind of hacky.
                // Prepend the symbolic name to the array of attributes.
                Map<String, Object> newAttrs = new LinkedHashMap<String, Object>(attrs.size() + 1);
                // We want this first from an indexing perspective.
                newAttrs.put(
                    BundleRevision.BUNDLE_NAMESPACE,
                    path);
                newAttrs.putAll(attrs);
                // But we need to put it again to make sure it wasn't overwritten.
                newAttrs.put(
                    BundleRevision.BUNDLE_NAMESPACE,
                    path);

                // Create filter now so we can inject filter directive.
                SimpleFilter sf = SimpleFilter.convert(newAttrs);

                // Inject filter directive.
// TODO: OSGi R4.3 - Can we insert this on demand somehow?
                Map<String, String> dirs = clause.m_dirs;
                Map<String, String> newDirs = new HashMap<String, String>(dirs.size() + 1);
                newDirs.putAll(dirs);
                newDirs.put(
                    Constants.FILTER_DIRECTIVE,
                    sf.toString());

                // Create package requirement and add to requirement list.
                reqList.add(
                    new BundleRequirementImpl(
                        owner,
                        BundleRevision.BUNDLE_NAMESPACE,
                        newDirs,
                        newAttrs));
            }
        }

        return reqList;
    }

    public static String parseExtensionBundleHeader(String header)
        throws BundleException
    {
        List<ParsedHeaderClause> clauses = parseStandardHeader(header);

        String result = null;

        if (clauses.size() == 1)
        {
            for (Entry<String, String> entry : clauses.get(0).m_dirs.entrySet())
            {
                if (Constants.EXTENSION_DIRECTIVE.equals(entry.getKey()))
                {
                    result = entry.getValue();
                }
            }

            if (FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME.equals(clauses.get(0).m_paths.get(0)) ||
                Constants.SYSTEM_BUNDLE_SYMBOLICNAME.equals(clauses.get(0).m_paths.get(0)))
            {
                result = (result == null) ? Constants.EXTENSION_FRAMEWORK : result;
            }
            else if (result != null)
            {
                throw new BundleException(
                    "Only the system bundle can have extension bundles.");
            }
        }

        return result;
    }

    private void parseActivationPolicy(Map<String, Object> headerMap)
    {
        m_activationPolicy = BundleRevisionImpl.EAGER_ACTIVATION;

        List<ParsedHeaderClause> clauses = parseStandardHeader(
            (String) headerMap.get(Constants.BUNDLE_ACTIVATIONPOLICY));

        if (clauses.size() > 0)
        {
            // Just look for a "path" matching the lazy policy, ignore
            // everything else.
            for (String path : clauses.get(0).m_paths)
            {
                if (path.equals(Constants.ACTIVATION_LAZY))
                {
                    m_activationPolicy = BundleRevisionImpl.LAZY_ACTIVATION;
                    for (Entry<String, String> entry : clauses.get(0).m_dirs.entrySet())
                    {
                        if (entry.getKey().equalsIgnoreCase(Constants.INCLUDE_DIRECTIVE))
                        {
                            m_activationIncludeDir = entry.getValue();
                        }
                        else if (entry.getKey().equalsIgnoreCase(Constants.EXCLUDE_DIRECTIVE))
                        {
                            m_activationExcludeDir = entry.getValue();
                        }
                    }
                    break;
                }
            }
        }
    }

    // Like this: path; path; dir1:=dirval1; dir2:=dirval2; attr1=attrval1; attr2=attrval2,
    //            path; path; dir1:=dirval1; dir2:=dirval2; attr1=attrval1; attr2=attrval2
    public static void main(String[] headers)
    {
        String header = headers[0];
        if (header != null)
        {
            if (header.length() == 0)
            {
                throw new IllegalArgumentException(
                    "A header cannot be an empty string.");
            }
            List<ParsedHeaderClause> clauses = parseStandardHeader(header);

            for (ParsedHeaderClause clause : clauses)
            {
                System.out.println("PATHS " + clause.m_paths);
                System.out.println("    DIRS  " + clause.m_dirs);
                System.out.println("    ATTRS " + clause.m_attrs);
                System.out.println("    TYPES " + clause.m_types);
            }

        }
    }

    private static final char EOF = (char) -1;

    private static char charAt(int pos, String headers, int length)
    {
        if (pos >= length)
        {
            return EOF;
        }
        return headers.charAt(pos);
    }

    private static final int CLAUSE_START = 0;
    private static final int PARAMETER_START = 1;
    private static final int KEY = 2;
    private static final int DIRECTIVE_OR_TYPEDATTRIBUTE = 4;
    private static final int ARGUMENT = 8;
    private static final int VALUE = 16;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static List<ParsedHeaderClause> parseStandardHeader(String header)
    {
        List<ParsedHeaderClause> clauses = new ArrayList<ParsedHeaderClause>();
        if (header == null)
        {
            return clauses;
        }
        ParsedHeaderClause clause = null;
        String key = null;
        Map targetMap = null;
        int state = CLAUSE_START;
        int currentPosition = 0;
        int startPosition = 0;
        int length = header.length();
        boolean quoted = false;
        boolean escaped = false;

        char currentChar = EOF;
        do
        {
            currentChar = charAt(currentPosition, header, length);
            switch (state)
            {
                case CLAUSE_START:
                    clause = new ParsedHeaderClause(
                            new ArrayList<String>(),
                            new HashMap<String, String>(),
                            new HashMap<String, Object>(),
                            new HashMap<String, String>());
                    clauses.add(clause);
                    state = PARAMETER_START;
                case PARAMETER_START:
                    startPosition = currentPosition;
                    state = KEY;
                case KEY:
                    switch (currentChar)
                    {
                        case ':':
                        case '=':
                            key = header.substring(startPosition, currentPosition).trim();
                            startPosition = currentPosition + 1;
                            targetMap = clause.m_attrs;
                            state = currentChar == ':' ? DIRECTIVE_OR_TYPEDATTRIBUTE : ARGUMENT;
                            break;
                        case EOF:
                        case ',':
                        case ';':
                            clause.m_paths.add(header.substring(startPosition, currentPosition).trim());
                            state = currentChar == ',' ? CLAUSE_START : PARAMETER_START;
                            break;
                        default:
                            break;
                    }
                    currentPosition++;
                    break;
                case DIRECTIVE_OR_TYPEDATTRIBUTE:
                    switch(currentChar)
                    {
                        case '=':
                            if (startPosition != currentPosition)
                            {
                                clause.m_types.put(key, header.substring(startPosition, currentPosition).trim());
                            }
                            else
                            {
                                targetMap = clause.m_dirs;
                            }
                            state = ARGUMENT;
                            startPosition = currentPosition + 1;
                            break;
                        default:
                            break;
                    }
                    currentPosition++;
                    break;
                case ARGUMENT:
                    if (currentChar == '\"')
                    {
                        quoted = true;
                        currentPosition++;
                    }
                    else
                    {
                        quoted = false;
                    }
                    if (!Character.isWhitespace(currentChar)) {
                    	state = VALUE;
                    }
                    else {
                    	currentPosition++;
                    }
                    break;
                case VALUE:
                    if (escaped)
                    {
                        escaped = false;
                    }
                    else
                    {
                        if (currentChar == '\\' )
                        {
                            escaped = true;
                        }
                        else if (quoted && currentChar == '\"')
                        {
                            quoted = false;
                        }
                        else if (!quoted)
                        {
                            String value = null;
                            switch(currentChar)
                            {
                                case EOF:
                                case ';':
                                case ',':
                                    value = header.substring(startPosition, currentPosition).trim();
                                    if (value.startsWith("\"") && value.endsWith("\""))
                                    {
                                        value = value.substring(1, value.length() - 1);
                                    }
                                    if (targetMap.put(key, value) != null)
                                    {
                                        throw new IllegalArgumentException(
                                                "Duplicate '" + key + "' in: " + header);
                                    }
                                    state = currentChar == ';' ? PARAMETER_START : CLAUSE_START;
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                    currentPosition++;
                    break;
                default:
                    break;
            }
        } while ( currentChar != EOF);

        if (state > PARAMETER_START)
        {
            throw new IllegalArgumentException("Unable to parse header: " + header);
        }
        return clauses;
    }

    public static List<String> parseDelimitedString(String value, String delim)
    {
        return parseDelimitedString(value, delim, true);
    }

    /**
     * Parses delimited string and returns an array containing the tokens. This
     * parser obeys quotes, so the delimiter character will be ignored if it is
     * inside of a quote. This method assumes that the quote character is not
     * included in the set of delimiter characters.
     * @param value the delimited string to parse.
     * @param delim the characters delimiting the tokens.
     * @return a list of string or an empty list if there are none.
    **/
    public static List<String> parseDelimitedString(String value, String delim, boolean trim)
    {
        if (value == null)
        {
           value = "";
        }

        List<String> list = new ArrayList<String>();

        int CHAR = 1;
        int DELIMITER = 2;
        int STARTQUOTE = 4;
        int ENDQUOTE = 8;

        StringBuilder sb = new StringBuilder();

        int expecting = (CHAR | DELIMITER | STARTQUOTE);

        boolean isEscaped = false;
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);

            boolean isDelimiter = (delim.indexOf(c) >= 0);

            if (!isEscaped && (c == '\\'))
            {
                isEscaped = true;
                continue;
            }

            if (isEscaped)
            {
                sb.append(c);
            }
            else if (isDelimiter && ((expecting & DELIMITER) > 0))
            {
                if (trim)
                {
                    list.add(sb.toString().trim());
                }
                else
                {
                    list.add(sb.toString());
                }
                sb.delete(0, sb.length());
                expecting = (CHAR | DELIMITER | STARTQUOTE);
            }
            else if ((c == '"') && ((expecting & STARTQUOTE) > 0))
            {
                sb.append(c);
                expecting = CHAR | ENDQUOTE;
            }
            else if ((c == '"') && ((expecting & ENDQUOTE) > 0))
            {
                sb.append(c);
                expecting = (CHAR | STARTQUOTE | DELIMITER);
            }
            else if ((expecting & CHAR) > 0)
            {
                sb.append(c);
            }
            else
            {
                throw new IllegalArgumentException("Invalid delimited string: " + value);
            }

            isEscaped = false;
        }

        if (sb.length() > 0)
        {
            if (trim)
            {
                list.add(sb.toString().trim());
            }
            else
            {
                list.add(sb.toString());
            }
        }

        return list;
    }

    /**
     * Parses native code manifest headers.
     * @param libStrs an array of native library manifest header
     *        strings from the bundle manifest.
     * @return an array of <tt>LibraryInfo</tt> objects for the
     *         passed in strings.
    **/
    private static List<NativeLibraryClause> parseLibraryStrings(
        Logger logger, List<String> libStrs)
        throws IllegalArgumentException
    {
        if (libStrs == null)
        {
            return new ArrayList<NativeLibraryClause>(0);
        }

        List<NativeLibraryClause> libList = new ArrayList<NativeLibraryClause>(libStrs.size());

        for (int i = 0; i < libStrs.size(); i++)
        {
            NativeLibraryClause clause = NativeLibraryClause.parse(logger, libStrs.get(i));
            libList.add(clause);
        }

        return libList;
    }

    public static List<BundleCapability> aliasSymbolicName(List<BundleCapability> caps, BundleRevision owner)
    {
        if (caps == null)
        {
            return new ArrayList<BundleCapability>(0);
        }

        List<BundleCapability> aliasCaps = new ArrayList<BundleCapability>(caps);

        String[] aliases = {
                FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME,
                Constants.SYSTEM_BUNDLE_SYMBOLICNAME };

        for (int capIdx = 0; capIdx < aliasCaps.size(); capIdx++)
        {
            BundleCapability cap = aliasCaps.get(capIdx);

            // Need to alias bundle and host capabilities.
            if (cap.getNamespace().equals(BundleRevision.BUNDLE_NAMESPACE)
                    || cap.getNamespace().equals(BundleRevision.HOST_NAMESPACE))
            {
                // Make a copy of the attribute array.
                Map<String, Object> aliasAttrs =
                        new HashMap<String, Object>(cap.getAttributes());
                // Add the aliased value.
                aliasAttrs.put(cap.getNamespace(), aliases);
                // Create the aliased capability to replace the old capability.
                cap = new BundleCapabilityImpl(
                        owner,
                        cap.getNamespace(),
                        cap.getDirectives(),
                        aliasAttrs);
                aliasCaps.set(capIdx, cap);
            }

            // Further, search attributes for bundle symbolic name and alias it too.
            for (Entry<String, Object> entry : cap.getAttributes().entrySet())
            {
                // If there is a bundle symbolic name attribute, add the
                // standard alias as a value.
                if (entry.getKey().equalsIgnoreCase(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE))
                {
                    // Make a copy of the attribute array.
                    Map<String, Object> aliasAttrs =
                            new HashMap<String, Object>(cap.getAttributes());
                    // Add the aliased value.
                    aliasAttrs.put(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE, aliases);
                    // Create the aliased capability to replace the old capability.
                    aliasCaps.set(capIdx, new BundleCapabilityImpl(
                            owner,
                            cap.getNamespace(),
                            cap.getDirectives(),
                            aliasAttrs));
                    // Continue with the next capability.
                    break;
                }
            }
        }

        return aliasCaps;
    }
}

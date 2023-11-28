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
package org.apache.felix.framework.util;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.Logger;
import org.apache.felix.framework.cache.BundleArchiveRevision;
import org.apache.felix.framework.cache.BundleCache;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Resource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class Util
{
    /**
     * The default name used for the default configuration properties file.
     **/
    private static final String DEFAULT_PROPERTIES_FILE = "/default.properties";

    private static final Properties DEFAULTS;

    private static final IOException DEFAULT_EX;

    private static final Map<String, Set<String>> MODULES_MAP;

    static
    {
        Properties defaults = null;
        IOException defaultEX = null;
        try
        {
            defaults = loadDefaultProperties();
        }
        catch (IOException ex)
        {
            defaultEX = ex;
        }
        DEFAULTS = defaults;
        DEFAULT_EX = defaultEX;

        MODULES_MAP = calculateModulesMap();
    }

    public static Properties loadDefaultProperties(Logger logger)
    {
        if (DEFAULTS.isEmpty())
        {
            logger.log(Logger.LOG_ERROR, "Unable to load any configuration properties.", DEFAULT_EX);
        }
        return DEFAULTS;
    }

    private static Properties loadDefaultProperties() throws IOException
    {
        Properties defaultProperties = new Properties();
        URL propURL = Util.class.getResource(DEFAULT_PROPERTIES_FILE);
        if (propURL != null)
        {
            try (InputStream is = propURL.openConnection().getInputStream())
            {
                defaultProperties.load(is);
            }
        }
        return defaultProperties;
    }

    public static void initializeJPMSEE(String javaVersion, Properties properties, Logger logger)
    {
        try
        {
            Version version = new Version(javaVersion);

            if (version.getMajor() >= 9)
            {
                StringBuilder eecap = new StringBuilder(", osgi.ee; osgi.ee=\"OSGi/Minimum\"; version:List<Version>=\"1.0,1.1,1.2\",osgi.ee; osgi.ee=\"JavaSE\"; version:List<Version>=\"1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,9");
                for (int i = 10; i <= version.getMajor();i++)
                {
                    eecap.append(',').append(Integer.toString(i));
                }
                eecap.append("\",osgi.ee; osgi.ee=\"JavaSE/compact1\"; version:List<Version>=\"1.8,9");
                for (int i = 10; i <= version.getMajor();i++)
                {
                    eecap.append(',').append(Integer.toString(i));
                }
                eecap.append("\",osgi.ee; osgi.ee=\"JavaSE/compact2\"; version:List<Version>=\"1.8,9");
                for (int i = 10; i <= version.getMajor();i++)
                {
                    eecap.append(',').append(Integer.toString(i));
                }
                eecap.append("\",osgi.ee; osgi.ee=\"JavaSE/compact3\"; version:List<Version>=\"1.8,9");
                for (int i = 10; i <= version.getMajor();i++)
                {
                    eecap.append(',').append(Integer.toString(i));
                }
                eecap.append("\"");

                StringBuilder ee = new StringBuilder();

                for (int i = version.getMajor(); i > 9;i--)
                {
                    ee.append("JavaSE-").append(Integer.toString(i)).append(',');
                }

                ee.append("JavaSE-9,JavaSE-1.8,JavaSE-1.7,JavaSE-1.6,J2SE-1.5,J2SE-1.4,J2SE-1.3,J2SE-1.2,JRE-1.1,JRE-1.0,OSGi/Minimum-1.2,OSGi/Minimum-1.1,OSGi/Minimum-1.0");

                properties.put("ee-jpms", ee.toString());

                properties.put("eecap-jpms", eecap.toString());

                properties.put("felix.detect.jpms", "jpms");
            }

            properties.put("felix.detect.java.specification.version", version.getMajor() < 9 ? ("1." + (version.getMinor() > 6 ? version.getMinor() < 8 ? version.getMinor() : 8 : 6)) : Integer.toString(version.getMajor()));

            if (version.getMajor() < 9)
            {
                properties.put("felix.detect.java.version", String.format("0.0.0.JavaSE_001_%03d", version.getMinor() > 6 ? version.getMinor() < 8 ? version.getMinor() : 8 : 6));
            }
            else
            {
                properties.put("felix.detect.java.version", String.format("0.0.0.JavaSE_%03d", version.getMajor()));
            }
        }
        catch (Exception ex)
        {
            logger.log(Logger.LOG_ERROR, "Exception parsing java version", ex);
        }
    }

    private static Map<String, Set<String>> calculateModulesMap()
    {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        try
        {
            Class<?> c_ModuleLayer = Felix.class.getClassLoader().loadClass("java.lang.ModuleLayer");
            Class<?> c_Module = Felix.class.getClassLoader().loadClass("java.lang.Module");
            Class<?> c_Descriptor = Felix.class.getClassLoader().loadClass("java.lang.module.ModuleDescriptor");
            Class<?> c_Exports = Felix.class.getClassLoader().loadClass("java.lang.module.ModuleDescriptor$Exports");
            Method m_getLayer = c_Module.getMethod("getLayer");
            Method m_getModule = Class.class.getMethod("getModule");
            Method m_getName = c_Module.getMethod("getName");
            Method isAutomatic = c_Descriptor.getMethod("isAutomatic");
            Method packagesMethod = c_Descriptor.getMethod("packages");

            Object self = m_getModule.invoke(Felix.class);
            Object moduleLayer = m_getLayer.invoke(self);

            if (moduleLayer == null)
            {
                moduleLayer = c_ModuleLayer.getMethod("boot").invoke(null);
            }

            for (Object module : ((Iterable) c_ModuleLayer.getMethod("modules").invoke(moduleLayer)))
            {
                if (!self.equals(module))
                {
                    String name = (String) m_getName.invoke(module);

                    Set<String> pkgs = new LinkedHashSet<>();

                    Object descriptor = c_Module.getMethod("getDescriptor").invoke(module);

                    if (!((Boolean) isAutomatic.invoke(descriptor)))
                    {
                        for (Object export : ((Set) c_Descriptor.getMethod("exports").invoke(descriptor)))
                        {
                            if (((Set) c_Exports.getMethod("targets").invoke(export)).isEmpty())
                            {
                                pkgs.add((String) c_Exports.getMethod("source").invoke(export));
                            }
                        }
                    }
                    else
                    {
                        pkgs.addAll((Set<String>) packagesMethod.invoke(descriptor));
                    }
                    result.put(name, pkgs);
                }
            }
        }
        catch (Exception ex)
        {
            //ex.printStackTrace();
            // Not much we can do - probably not on java9
        }
        return result;
    }

    public static Map<String, Set<String>> initializeJPMS(Properties properties)
    {
        Map<String,Set<String>> exports = null;
        if (!MODULES_MAP.isEmpty())
        {
            Set<String> modules = new TreeSet<String>();
            exports = new HashMap<String, Set<String>>();
            for (Map.Entry<String, Set<String>> module : MODULES_MAP.entrySet())
            {
                Object name = module.getKey();
                properties.put("felix.detect.jpms." + name, name);
                modules.add("felix.jpms." + name);
                Set<String> pkgs = module.getValue();

                if (!pkgs.isEmpty())
                {
                    exports.put("felix.jpms." + name, pkgs);
                }
            }

            String modulesString = "";
            for (String module : modules) {
                modulesString += "${" + module + "}";
            }
            properties.put("jre-jpms", modulesString);
        }

        return exports;
    }

    public static String getPropertyWithSubs(Properties props, String name)
    {
        // Perform variable substitution for property.
        String value = props.getProperty(name);
        value = (value != null)
            ? Util.substVars(value, name, null, props): null;
        return value;
    }

    public static Map<String, String> getPropertiesWithPrefix(Properties props, String prefix)
    {
        Map<String, String> result = new HashMap<String, String>();

        Set<String> propertySet = props.stringPropertyNames();

        for(String currentPropertyKey: propertySet)
        {
            if(currentPropertyKey.startsWith(prefix))
            {
                String value = props.getProperty(currentPropertyKey);
                // Perform variable substitution for property.
                value = (value != null)
                    ? Util.substVars(value, currentPropertyKey, null, props): null;
                result.put(currentPropertyKey, value);
            }
        }
        return result;
    }

    public static Properties toProperties(Map map)
    {
        Properties result = new Properties();
        for (Iterator iter = map.entrySet().iterator(); iter.hasNext();)
        {
            Entry entry = (Entry) iter.next();
            if (entry.getKey() != null && entry.getValue() != null)
            {
                result.setProperty(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return result;
    }

    /**
     * Converts a revision identifier to a bundle identifier. Revision IDs
     * are typically <tt>&lt;bundle-id&gt;.&lt;revision&gt;</tt>; this
     * method returns only the portion corresponding to the bundle ID.
    **/
    public static long getBundleIdFromRevisionId(String id)
    {
        try
        {
            String bundleId = (id.indexOf('.') >= 0)
                ? id.substring(0, id.indexOf('.')) : id;
            return Long.parseLong(bundleId);
        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    /**
     * Converts a module identifier to a bundle identifier. Module IDs
     * are typically <tt>&lt;bundle-id&gt;.&lt;revision&gt;</tt>; this
     * method returns only the portion corresponding to the revision.
    **/
    public static int getModuleRevisionFromModuleId(String id)
    {
        try
        {
            int index = id.indexOf('.');
            if (index >= 0)
            {
                return Integer.parseInt(id.substring(index + 1));
            }
        }
        catch (NumberFormatException ex)
        {
        }
        return -1;
    }

    public static String getClassName(String className)
    {
        if (className == null)
        {
            className = "";
        }
        return (className.lastIndexOf('.') < 0)
            ? "" : className.substring(className.lastIndexOf('.') + 1);
    }

    public static String getClassPackage(String className)
    {
        if (className == null)
        {
            className = "";
        }
        return (className.lastIndexOf('.') < 0)
            ? "" : className.substring(0, className.lastIndexOf('.'));
    }

    public static String getResourcePackage(String resource)
    {
        if (resource == null)
        {
            resource = "";
        }
        // NOTE: The package of a resource is tricky to determine since
        // resources do not follow the same naming conventions as classes.
        // This code is pessimistic and assumes that the package of a
        // resource is everything up to the last '/' character. By making
        // this choice, it will not be possible to load resources from
        // imports using relative resource names. For example, if a
        // bundle exports "foo" and an importer of "foo" tries to load
        // "/foo/bar/myresource.txt", this will not be found in the exporter
        // because the following algorithm assumes the package name is
        // "foo.bar", not just "foo". This only affects imported resources,
        // local resources will work as expected.
        String pkgName = (resource.startsWith("/")) ? resource.substring(1) : resource;
        pkgName = (pkgName.lastIndexOf('/') < 0)
            ? "" : pkgName.substring(0, pkgName.lastIndexOf('/'));
        pkgName = pkgName.replace('/', '.');
        return pkgName;
    }

    /**
     * <p>
     * This is a simple utility class that attempts to load the named
     * class using the class loader of the supplied class or
     * the class loader of one of its super classes or their implemented
     * interfaces. This is necessary during service registration to test
     * whether a given service object implements its declared service
     * interfaces.
     * </p>
     * <p>
     * To perform this test, the framework must try to load
     * the classes associated with the declared service interfaces, so
     * it must choose a class loader. The class loader of the registering
     * bundle cannot be used, since this disallows third parties to
     * register service on behalf of another bundle. Consequently, the
     * class loader of the service object must be used. However, this is
     * also not sufficient since the class loader of the service object
     * may not have direct access to the class in question.
     * </p>
     * <p>
     * The service object's class loader may not have direct access to
     * its service interface if it extends a super class from another
     * bundle which implements the service interface from an imported
     * bundle or if it implements an extension of the service interface
     * from another bundle which imports the base interface from another
     * bundle. In these cases, the service object's class loader only has
     * access to the super class's class or the extended service interface,
     * respectively, but not to the actual service interface.
     * </p>
     * <p>
     * Thus, it is necessary to not only try to load the service interface
     * class from the service object's class loader, but from the class
     * loaders of any interfaces it implements and the class loaders of
     * all super classes.
     * </p>
     * @param clazz the class that is the root of the search.
     * @param name the name of the class to load.
     * @return the loaded class or <tt>null</tt> if it could not be
     *         loaded.
    **/
    public static Class loadClassUsingClass(Class clazz, String name, SecureAction action)
    {
        Class loadedClass = null;

        while (clazz != null)
        {
            // Get the class loader of the current class object.
            ClassLoader loader = action.getClassLoader(clazz);
            // A null class loader represents the system class loader.
            loader = (loader == null) ? action.getSystemClassLoader() : loader;
            try
            {
                return loader.loadClass(name);
            }
            catch (ClassNotFoundException ex)
            {
                // Ignore and try interface class loaders.
            }

            // Try to see if we can load the class from
            // one of the class's implemented interface
            // class loaders.
            Class[] ifcs = clazz.getInterfaces();
            for (int i = 0; i < ifcs.length; i++)
            {
                loadedClass = loadClassUsingClass(ifcs[i], name, action);
                if (loadedClass != null)
                {
                    return loadedClass;
                }
            }

            // Try to see if we can load the class from
            // the super class class loader.
            clazz = clazz.getSuperclass();
        }

        return null;
    }

    public static boolean checkImplementsWithName(Class<?> clazz, String name)
    {
        while (clazz != null)
        {
            if (clazz.getName().equals(name))
            {
                return true;
            }
            // Try to see if
            // one of the class's implemented interface
            // is the name.
            Class[] ifcs = clazz.getInterfaces();
            for (int i = 0; i < ifcs.length; i++)
            {
                if (checkImplementsWithName(ifcs[i], name)) {
                    return true;
                }
            }

            // Try the super class class loader.
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    /**
     * This method determines if the requesting bundle is able to cast
     * the specified service reference based on class visibility rules
     * of the underlying modules.
     * @param requester The bundle requesting the service.
     * @param ref The service in question.
     * @return <tt>true</tt> if the requesting bundle is able to case
     *         the service object to a known type.
    **/
    public static boolean isServiceAssignable(Bundle requester, ServiceReference ref)
    {
        // Boolean flag.
        boolean allow = true;
        // Get the service's objectClass property.
        String[] objectClass = (String[]) ref.getProperty(FelixConstants.OBJECTCLASS);

        // The the service reference is not assignable when the requesting
        // bundle is wired to a different version of the service object.
        // NOTE: We are pessimistic here, if any class in the service's
        // objectClass is not usable by the requesting bundle, then we
        // disallow the service reference.
        for (int classIdx = 0; (allow) && (classIdx < objectClass.length); classIdx++)
        {
            if (!ref.isAssignableTo(requester, objectClass[classIdx]))
            {
                allow = false;
            }
        }
        return allow;
    }

    public static List<BundleRequirement> getDynamicRequirements(
        List<BundleRequirement> reqs)
    {
        List<BundleRequirement> result = null;
        if (reqs != null)
        {
            for (BundleRequirement req : reqs)
            {
                String resolution = req.getDirectives().get(Constants.RESOLUTION_DIRECTIVE);
                if ((resolution != null) && resolution.equals("dynamic"))
                {
                    if (result == null)
                    {
                        result = new ArrayList<BundleRequirement>();
                    }
                    result.add(req);
                }
            }
        }
        return result;
    }

    public static BundleWire getWire(BundleRevision br, String name)
    {
        if (br.getWiring() != null)
        {
            List<BundleWire> wires = br.getWiring().getRequiredWires(null);
            if (wires != null)
            {
                for (BundleWire w : wires)
                {
                    if (w.getCapability().getNamespace()
                            .equals(BundleRevision.PACKAGE_NAMESPACE) &&
                            w.getCapability().getAttributes()
                                    .get(BundleRevision.PACKAGE_NAMESPACE).equals(name))
                    {
                        return w;
                    }
                }
            }
        }
        return null;
    }

    public static BundleCapability getPackageCapability(BundleRevision br, String name)
    {
        if (br.getWiring() != null)
        {
            List<BundleCapability> capabilities = br.getWiring().getCapabilities(null);
            if (capabilities != null)
            {
                for (BundleCapability c : capabilities)
                {
                    if (c.getNamespace().equals(BundleRevision.PACKAGE_NAMESPACE)
                        && c.getAttributes().get(BundleRevision.PACKAGE_NAMESPACE).equals(name))
                    {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    private static final byte encTab[] = { 0x41, 0x42, 0x43, 0x44, 0x45, 0x46,
        0x47, 0x48, 0x49, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x52,
        0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x61, 0x62, 0x63, 0x64,
        0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70,
        0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x30, 0x31,
        0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x2b, 0x2f };

    private static final byte decTab[] = { -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1,
        -1, -1, 63, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1,
        -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1, -1, 26, 27, 28, 29,
        30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
        48, 49, 50, 51, -1, -1, -1, -1, -1 };

    public static String base64Encode(String s) throws IOException
    {
        return encode(s.getBytes(), 0);
    }

    /**
     * Encode a raw byte array to a Base64 String.
     *
     * @param in Byte array to encode.
     * @param len Length of Base64 lines. 0 means no line breaks.
    **/
    public static String encode(byte[] in, int len) throws IOException
    {
        ByteArrayOutputStream baos = null;
        ByteArrayInputStream bais = null;
        try
        {
            baos = new ByteArrayOutputStream();
            bais = new ByteArrayInputStream(in);
            encode(bais, baos, len);
            // ASCII byte array to String
            return (new String(baos.toByteArray()));
        }
        finally
        {
            if (baos != null)
            {
                baos.close();
            }
            if (bais != null)
            {
                bais.close();
            }
        }
    }

    public static void encode(InputStream in, OutputStream out, int len)
        throws IOException
    {

        // Check that length is a multiple of 4 bytes
        if (len % 4 != 0)
        {
            throw new IllegalArgumentException("Length must be a multiple of 4");
        }

        // Read input stream until end of file
        int bits = 0;
        int nbits = 0;
        int nbytes = 0;
        int b;

        while ((b = in.read()) != -1)
        {
            bits = (bits << 8) | b;
            nbits += 8;
            while (nbits >= 6)
            {
                nbits -= 6;
                out.write(encTab[0x3f & (bits >> nbits)]);
                nbytes++;
                // New line
                if (len != 0 && nbytes >= len)
                {
                    out.write(0x0d);
                    out.write(0x0a);
                    nbytes -= len;
                }
            }
        }

        switch (nbits)
        {
            case 2:
                out.write(encTab[0x3f & (bits << 4)]);
                out.write(0x3d); // 0x3d = '='
                out.write(0x3d);
                break;
            case 4:
                out.write(encTab[0x3f & (bits << 2)]);
                out.write(0x3d);
                break;
        }

        if (len != 0)
        {
            if (nbytes != 0)
            {
                out.write(0x0d);
                out.write(0x0a);
            }
            out.write(0x0d);
            out.write(0x0a);
        }
    }


    private static final String DELIM_START = "${";
    private static final String DELIM_STOP  = "}";

    /**
     * <p>
     * This method performs property variable substitution on the
     * specified value. If the specified value contains the syntax
     * <tt>${&lt;prop-name&gt;}</tt>, where <tt>&lt;prop-name&gt;</tt>
     * refers to either a configuration property or a system property,
     * then the corresponding property value is substituted for the variable
     * placeholder. Multiple variable placeholders may exist in the
     * specified value as well as nested variable placeholders, which
     * are substituted from inner most to outer most. Configuration
     * properties override system properties.
     * </p>
     * @param val The string on which to perform property substitution.
     * @param currentKey The key of the property being evaluated used to
     *        detect cycles.
     * @param cycleMap Map of variable references used to detect nested cycles.
     * @param configProps Set of configuration properties.
     * @return The value of the specified string after system property substitution.
     * @throws IllegalArgumentException If there was a syntax error in the
     *         property placeholder syntax or a recursive variable reference.
    **/
    public static String substVars(String val, String currentKey,
        Map cycleMap, Properties configProps)
        throws IllegalArgumentException
    {
        // If there is currently no cycle map, then create
        // one for detecting cycles for this invocation.
        if (cycleMap == null)
        {
            cycleMap = new HashMap();
        }

        // Put the current key in the cycle map.
        cycleMap.put(currentKey, currentKey);

        // Assume we have a value that is something like:
        // "leading ${foo.${bar}} middle ${baz} trailing"

        // Find the first ending '}' variable delimiter, which
        // will correspond to the first deepest nested variable
        // placeholder.
        int stopDelim = -1;
        int startDelim = -1;

        do
        {
            stopDelim = val.indexOf(DELIM_STOP, stopDelim + 1);
            // If there is no stopping delimiter, then just return
            // the value since there is no variable declared.
            if (stopDelim < 0)
            {
                return val;
            }
            // Try to find the matching start delimiter by
            // looping until we find a start delimiter that is
            // greater than the stop delimiter we have found.
            startDelim = val.indexOf(DELIM_START);
            // If there is no starting delimiter, then just return
            // the value since there is no variable declared.
            if (startDelim < 0)
            {
                return val;
            }
            while (stopDelim >= 0)
            {
                int idx = val.indexOf(DELIM_START, startDelim + DELIM_START.length());
                if ((idx < 0) || (idx > stopDelim))
                {
                    break;
                }
                else if (idx < stopDelim)
                {
                    startDelim = idx;
                }
            }
        }
        while ((startDelim > stopDelim) && (stopDelim >= 0));

        // At this point, we have found a variable placeholder so
        // we must perform a variable substitution on it.
        // Using the start and stop delimiter indices, extract
        // the first, deepest nested variable placeholder.
        String variable =
            val.substring(startDelim + DELIM_START.length(), stopDelim);

        // Verify that this is not a recursive variable reference.
        if (cycleMap.get(variable) != null)
        {
            throw new IllegalArgumentException(
                "recursive variable reference: " + variable);
        }

        // Get the value of the deepest nested variable placeholder.
        // Try to configuration properties first.
        String substValue = (configProps != null)
            ? configProps.getProperty(variable, null)
            : null;
        if (substValue == null)
        {
            // Ignore unknown property values.
            substValue = System.getProperty(variable, "");
        }

        // Remove the found variable from the cycle map, since
        // it may appear more than once in the value and we don't
        // want such situations to appear as a recursive reference.
        cycleMap.remove(variable);

        // Append the leading characters, the substituted value of
        // the variable, and the trailing characters to get the new
        // value.
        val = val.substring(0, startDelim)
            + substValue
            + val.substring(stopDelim + DELIM_STOP.length(), val.length());

        // Now perform substitution again, since there could still
        // be substitutions to make.
        val = substVars(val, currentKey, cycleMap, configProps);

        // Return the value.
        return val;
    }

    /**
     * Returns true if the specified bundle revision is a singleton
     * (i.e., directive singleton:=true in Bundle-SymbolicName).
     *
     * @param revision the revision to check for singleton status.
     * @return true if the revision is a singleton, false otherwise.
    **/
    public static boolean isSingleton(BundleRevision revision)
    {
        final List<BundleCapability> caps = revision.getDeclaredCapabilities(null);
        for (BundleCapability cap : caps)
        {
            // Find the bundle capability and check its directives.
            if (cap.getNamespace().equals(BundleRevision.BUNDLE_NAMESPACE))
            {
                for (Entry<String, String> entry : cap.getDirectives().entrySet())
                {
                    if (entry.getKey().equalsIgnoreCase(Constants.SINGLETON_DIRECTIVE))
                    {
                        return Boolean.valueOf(entry.getValue());
                    }
                }
                // Can only have one bundle capability, so break.
                break;
            }
        }
        return false;
    }

    /**
     * Checks if the provided module definition declares a fragment host.
     *
     * @param revision the module to check
     * @return <code>true</code> if the module declares a fragment host, <code>false</code>
     *      otherwise.
     */
    public static boolean isFragment(BundleRevision revision)
    {
        return ((revision.getTypes() & BundleRevision.TYPE_FRAGMENT) > 0);
    }

    public static boolean isFragment(Resource resource)
    {
        if (resource instanceof BundleRevision)
            return isFragment((BundleRevision) resource);
        else
            return false;
    }

    public static List<BundleRevision> getFragments(BundleWiring wiring)
    {
        List<BundleRevision> fragments = Collections.EMPTY_LIST;
        if (wiring != null)
        {
            List<BundleWire> wires = wiring.getProvidedWires(null);
            if (wires != null)
            {
                for (BundleWire w : wires)
                {
                    if (w.getCapability().getNamespace()
                        .equals(BundleRevision.HOST_NAMESPACE))
                    {
                        // Create array list if needed.
                        if (fragments.isEmpty())
                        {
                            fragments = new ArrayList<BundleRevision>();
                        }
                        fragments.add(w.getRequirerWiring().getRevision());
                    }
                }
            }
        }
        return fragments;
    }

    //
    // UUID code copied from Apache Harmony java.util.UUID
    //

    /**
     * <p>
     * Generates a variant 2, version 4 (randomly generated number) UUID as per
     * <a href="http://www.ietf.org/rfc/rfc4122.txt">RFC 4122</a>.
     *
     * @return an UUID instance.
     */
    public static String randomUUID(boolean secure) {
        byte[] data = new byte[16];
        if (secure)
        {
            SecureRandom rng = new SecureRandom();
            rng.nextBytes(data);
        }
        else
        {
            Random rng = new Random();
            rng.nextBytes(data);
        }
        long mostSigBits = (data[0] & 0xFFL) << 56;
        mostSigBits |= (data[1] & 0xFFL) << 48;
        mostSigBits |= (data[2] & 0xFFL) << 40;
        mostSigBits |= (data[3] & 0xFFL) << 32;
        mostSigBits |= (data[4] & 0xFFL) << 24;
        mostSigBits |= (data[5] & 0xFFL) << 16;
        mostSigBits |= (data[6] & 0x0FL) << 8;
        mostSigBits |= (0x4L << 12); // set the version to 4
        mostSigBits |= (data[7] & 0xFFL);

        long leastSigBits = (data[8] & 0x3FL) << 56;
        leastSigBits |= (0x2L << 62); // set the variant to bits 01
        leastSigBits |= (data[9] & 0xFFL) << 48;
        leastSigBits |= (data[10] & 0xFFL) << 40;
        leastSigBits |= (data[11] & 0xFFL) << 32;
        leastSigBits |= (data[12] & 0xFFL) << 24;
        leastSigBits |= (data[13] & 0xFFL) << 16;
        leastSigBits |= (data[14] & 0xFFL) << 8;
        leastSigBits |= (data[15] & 0xFFL);

        //
        // UUID.init()
        //

        int variant;
        int version;
        long timestamp;
        int clockSequence;
        long node;
        int hash;

        // setup hash field
        int msbHash = (int) (mostSigBits ^ (mostSigBits >>> 32));
        int lsbHash = (int) (leastSigBits ^ (leastSigBits >>> 32));
        hash = msbHash ^ lsbHash;

        // setup variant field
        if ((leastSigBits & 0x8000000000000000L) == 0) {
            // MSB0 not set, NCS backwards compatibility variant
            variant = 0;
        } else if ((leastSigBits & 0x4000000000000000L) != 0) {
            // MSB1 set, either MS reserved or future reserved
            variant = (int) ((leastSigBits & 0xE000000000000000L) >>> 61);
        } else {
            // MSB1 not set, RFC 4122 variant
            variant = 2;
        }

        // setup version field
        version = (int) ((mostSigBits & 0x000000000000F000) >>> 12);

        if (!(variant != 2 && version != 1)) {
            // setup timestamp field
            long timeLow = (mostSigBits & 0xFFFFFFFF00000000L) >>> 32;
            long timeMid = (mostSigBits & 0x00000000FFFF0000L) << 16;
            long timeHigh = (mostSigBits & 0x0000000000000FFFL) << 48;
            timestamp = timeLow | timeMid | timeHigh;

            // setup clock sequence field
            clockSequence = (int) ((leastSigBits & 0x3FFF000000000000L) >>> 48);

            // setup node field
            node = (leastSigBits & 0x0000FFFFFFFFFFFFL);
        }

        //
        // UUID.toString()
        //

        StringBuilder builder = new StringBuilder(36);
        String msbStr = Long.toHexString(mostSigBits);
        if (msbStr.length() < 16) {
            int diff = 16 - msbStr.length();
            for (int i = 0; i < diff; i++) {
                builder.append('0');
            }
        }
        builder.append(msbStr);
        builder.insert(8, '-');
        builder.insert(13, '-');
        builder.append('-');
        String lsbStr = Long.toHexString(leastSigBits);
        if (lsbStr.length() < 16) {
            int diff = 16 - lsbStr.length();
            for (int i = 0; i < diff; i++) {
                builder.append('0');
            }
        }
        builder.append(lsbStr);
        builder.insert(23, '-');
        return builder.toString();
    }

    public static <K,V> V putIfAbsentAndReturn(ConcurrentHashMap<K,V> map, K key, V value)
    {
        V result = map.putIfAbsent(key, value);
        return result != null ? result : value;
    }

    public static String getFrameworkUUIDFromURL(String host)
    {
        if (host != null)
        {
            int idx = host.indexOf('_');
            if (idx > 0)
            {
                return host.substring(0, idx);
            }
        }
        return null;
    }

    public static String getRevisionIdFromURL(String host)
    {
        if (host != null)
        {
            int idx = host.indexOf('_');
            if (idx > 0 && idx < host.length())
            {
                return host.substring(idx + 1);
            }
            else
            {
                return host;
            }
        }
        return null;
    }

    public static Map<String, Object> getMultiReleaseAwareManifestHeaders(String version, BundleArchiveRevision revision) throws Exception
    {
        Map<String, Object> manifest = revision.getManifestHeader();
        if (manifest == null)
        {
            throw new FileNotFoundException("META-INF/MANIFEST.MF");
        }
        if ("true".equals(manifest.get("Multi-Release")))
        {
            for (int major = Version.parseVersion(version).getMajor(); major >= 9; major--)
            {
                byte[] versionManifestInput = revision.getContent()
                    .getEntryAsBytes("META-INF/versions/" + major + "/OSGI-INF/MANIFEST.MF");

                if (versionManifestInput != null)
                {
                    Map<String, Object> versionManifest = BundleCache.getMainAttributes(
                        new StringMap(), new ByteArrayInputStream(versionManifestInput), versionManifestInput.length);

                    if (versionManifest.get(Constants.IMPORT_PACKAGE) != null)
                    {
                        manifest.put(Constants.IMPORT_PACKAGE, versionManifest.get(Constants.IMPORT_PACKAGE));
                    }
                    if (versionManifest.get(Constants.REQUIRE_CAPABILITY) != null)
                    {
                        manifest.put(Constants.REQUIRE_CAPABILITY, versionManifest.get(Constants.REQUIRE_CAPABILITY));
                    }
                    break;
                }
            }
        }
        return manifest;
    }

    private static final List EMPTY_LIST = Collections.unmodifiableList(Collections.EMPTY_LIST);
    private static final Map EMPTY_MAP = Collections.unmodifiableMap(Collections.EMPTY_MAP);

    public static <T> List<T> newImmutableList(List<T> list)
    {
        return list == null || list.isEmpty() ? EMPTY_LIST : Collections.unmodifiableList(list);
    }

    public static <K,V> Map<K,V> newImmutableMap(Map<K,V> map)
    {
        return map == null || map.isEmpty() ? EMPTY_MAP : Collections.unmodifiableMap(map);
    }
}

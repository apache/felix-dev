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
package org.apache.felix.scr.impl.inject.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.felix.scr.impl.logger.ComponentLogger;
import org.apache.felix.scr.impl.logger.InternalLogger.Level;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentServiceObjects;
import org.osgi.util.tracker.ServiceTracker;


/**
 * Utility methods for class handling used by method and field references.
 */
@SuppressWarnings("deprecation")
public class ClassUtils
{
    private static final Class<?> OBJECT_CLASS = Object.class;

    public static final Class<?> SERVICE_REFERENCE_CLASS = ServiceReference.class;

    public static final Class<?> COMPONENTS_SERVICE_OBJECTS_CLASS = ComponentServiceObjects.class;

    public static final Class<?> MAP_CLASS = Map.class;
    public static final Class<?> MAP_ENTRY_CLASS = Map.Entry.class;

    public static final Class<?> COLLECTION_CLASS = Collection.class;
    public static final Class<?> LIST_CLASS = List.class;

    public static final Class<?> OPTIONAL_CLASS = Optional.class;

    public static final Class<?> COMPONENT_CONTEXT_CLASS = ComponentContext.class;
    public static final Class<?> BUNDLE_CONTEXT_CLASS = BundleContext.class;
    public static final Class<?> INTEGER_CLASS = Integer.class;

    public static final String LOGGER_CLASS = "org.osgi.service.log.Logger";
    public static final String FORMATTER_LOGGER_CLASS = "org.osgi.service.log.FormatterLogger";
    public static final String LOGGER_FACTORY_CLASS = "org.osgi.service.log.LoggerFactory";

    public static FrameworkWiring m_fwkWiring;

    /**
     * Returns the class object representing the class of the field reference
     * The class loader of the component class is used to load the service class.
     * <p>
     * It may well be possible, that the class loader of the target class cannot
     * see the service object class, for example if the service reference is
     * inherited from a component class of another bundle.
     *
     * @return The class object for the referred to service or <code>null</code>
     *      if the class loader of the <code>targetClass</code> cannot see that
     *      class.
     */
    public static Class<?> getClassFromComponentClassLoader(
            final Class<?> componentClass,
            final String className,
            final ComponentLogger logger )
    {
        if (logger.isLogEnabled(Level.DEBUG))
        {
            logger.log(
                Level.DEBUG,
                "getClassFromComponentClassLoader: Looking for interface class {0} through loader of {1}", null,
                    className, componentClass.getName() );
        }

        try
        {
            // need the class loader of the target class, which may be the
            // system classloader, which case getClassLoader may return null
            ClassLoader loader = componentClass.getClassLoader();
            if ( loader == null )
            {
                loader = ClassLoader.getSystemClassLoader();
            }

            final Class<?> referenceClass = loader.loadClass( className );
            if (logger.isLogEnabled(Level.DEBUG))
            {
                logger.log(Level.DEBUG,
                    "getClassFromComponentClassLoader: Found class {0}", null, referenceClass.getName() );
            }
            return referenceClass;
        }
        catch ( final ClassNotFoundException cnfe )
        {
            // if we can't load the class, perhaps the method is declared in a
            // super class so we try this class next
        }

        if (logger.isLogEnabled(Level.DEBUG))
        {
            logger.log(Level.DEBUG,
                "getClassFromComponentClassLoader: Not found through component class, using FrameworkWiring", null );
        }

        // try to load the class with the help of the FrameworkWiring
        Bundle exportingHost = getExporter(className, logger);
        if ( exportingHost != null )
        {
            try
            {
                if (logger.isLogEnabled(Level.DEBUG))
                {
                    logger.log(Level.DEBUG, "getClassFromComponentClassLoader: Checking Bundle {0}/{1}", null,
                            exportingHost.getSymbolicName(), exportingHost.getBundleId());
                }

                Class<?> referenceClass = exportingHost.loadClass(className);
                if (logger.isLogEnabled(Level.DEBUG))
                {
                    logger.log(Level.DEBUG, "getClassFromComponentClassLoader: Found class {0}", null,
                            referenceClass.getName());
                }
                return referenceClass;
            } catch (ClassNotFoundException cnfe)
            {
                // exported package does not provide the interface !!!!
            }
        }
        else if (logger.isLogEnabled(Level.DEBUG))
        {
            logger.log(Level.DEBUG,
                    "getClassFromComponentClassLoader: No bundles exporting package {0} found", null, className );
        }

        // class cannot be found, neither through the component nor from an
        // export, so we fall back to assuming Object
        if (logger.isLogEnabled(Level.DEBUG))
        {
            logger.log(Level.DEBUG,
                "getClassFromComponentClassLoader: No class found, falling back to class Object", null );
        }
        return OBJECT_CLASS;
    }

    private static Bundle getExporter(String className, ComponentLogger logger) {
        FrameworkWiring currentFwkWiring = m_fwkWiring;
        if (currentFwkWiring != null)
        {
            String referenceClassPackage = className.substring(0, className.lastIndexOf( '.' ) );
            Collection<BundleCapability> providers = currentFwkWiring.findProviders(getRequirement(referenceClassPackage));
            for (BundleCapability provider : providers)
            {
                BundleWiring wiring = provider.getRevision().getWiring();
                if (wiring != null)
                {
                    if ((provider.getRevision().getTypes() & BundleRevision.TYPE_FRAGMENT) != 0) {
                        // for fragments just use the first host bundle
                        List<BundleWire> hostWires = wiring.getRequiredWires(HostNamespace.HOST_NAMESPACE);
                        if (hostWires != null && !hostWires.isEmpty()) {
                            return hostWires.get(0).getProvider().getBundle();
                        }
                    }
                    else
                    {
                        return wiring.getBundle();
                    }
                }
            }
        }
        else if (logger.isLogEnabled(Level.DEBUG))
        {
            logger.log(Level.DEBUG,
                "getClassFromComponentClassLoader: FrameworkWiring not available, cannot find class", null );
        }
        return null;
	}

	private static Requirement getRequirement(final String pkgName)
    {
        return new Requirement()
        {
            @Override
            public Resource getResource()
            {
                return null;
            }

            @Override
            public String getNamespace()
            {
                return PackageNamespace.PACKAGE_NAMESPACE;
            }

            @Override
            public Map<String, String> getDirectives()
            {
                String filter = "(" + PackageNamespace.PACKAGE_NAMESPACE + "=" + pkgName + ")";
                return Collections.singletonMap(Namespace.REQUIREMENT_FILTER_DIRECTIVE, filter);
            }

            @Override
            public Map<String, Object> getAttributes()
            {
                return Collections.emptyMap();
            }
        };
    }

    public static void setFrameworkWiring( FrameworkWiring fwkWiring )
    {
        ClassUtils.m_fwkWiring = fwkWiring;
    }

    /**
     * Returns the name of the package to which the class belongs or an
     * empty string if the class is in the default package.
     */
    public static String getPackageName( final Class<?> clazz )
    {
        String name = clazz.getName();
        int dot = name.lastIndexOf( '.' );
        return ( dot > 0 ) ? name.substring( 0, dot ) : "";
    }

}

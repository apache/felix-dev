/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.webconsole.internal.configuration;


import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeInformation;
import org.osgi.service.metatype.MetaTypeService;
import org.osgi.service.metatype.ObjectClassDefinition;


/**
 * The <code>ConfigManagerBase</code> is the base class for the
 * ConfigurationAdmin support in the web console. It provides various helper
 * methods mostly with respect to using the MetaTypeService to access
 * configuration descriptions.
 */
class MetaTypeServiceSupport extends MetaTypeSupport {

    private final BundleContext bundleContext;

    private final MetaTypeService service;

    /**
     * Create a new support object
     * @param bundleContext The bundle context
     * @param service The metatype service
     *
     * @throws ClassCastException if {@code service} is not a MetaTypeService instances
     */
    MetaTypeServiceSupport( final BundleContext bundleContext, final Object service ) {
        this.bundleContext = bundleContext;
        this.service = ( MetaTypeService ) service;
    }

    /**
     * Returns a map of PIDs and providing bundles of MetaType information. The
     * map is indexed by PID and the value of each entry is the bundle providing
     * the MetaType information for that PID.
     *
     * @param locale The name of the locale to get the meta data for.
     * @return see the method description
     */
    Map<String, ObjectClassDefinition> getPidObjectClasses( final String locale ) {
        return getObjectClassDefinitions( PID_GETTER, locale );
    }


    /**
     * Returns a map of factory PIDs and providing bundles of MetaType
     * information. The map is indexed by factory PID and the value of each
     * entry is the bundle providing the MetaType information for that factory
     * PID.
     *
     * @param locale The name of the locale to get the meta data for.
     * @return see the method description
     */
    Map<String, ObjectClassDefinition> getFactoryPidObjectClasses( final String locale ) {
        return getObjectClassDefinitions( FACTORY_PID_GETTER, locale );
    }


    /**
     * Returns the <code>ObjectClassDefinition</code> objects for the IDs
     * returned by the <code>idGetter</code>. Depending on the
     * <code>idGetter</code> implementation this will be for factory PIDs or
     * plain PIDs.
     *
     * @param idGetter The {@link IdGetter} used to get the list of factory PIDs
     *          or PIDs from <code>MetaTypeInformation</code> objects.
     * @param locale The name of the locale to get the object class definitions
     *          for.
     * @return Map of <code>ObjectClassDefinition</code> objects indexed by the
     *      PID (or factory PID) to which they pertain
     */
    private Map<String, ObjectClassDefinition> getObjectClassDefinitions( final IdGetter idGetter, final String locale ) {
        final Map<String, ObjectClassDefinition> objectClassesDefinitions = new HashMap<>();
        for ( final Bundle bundle : this.bundleContext.getBundles() ) {
            final MetaTypeInformation mti = this.service.getMetaTypeInformation( bundle );
            if ( mti != null ) {
                final String[] idList = idGetter.getIds( mti );
                for ( int j = 0; idList != null && j < idList.length; j++ ) {
                    // After getting the list of PIDs, a configuration  might be
                    // removed. So the getObjectClassDefinition will throw
                    // an exception, and this will prevent ALL configuration from
                    // being displayed. By catching it, the configurations will be
                    // visible
                    ObjectClassDefinition ocd = null;
                    try {
                        ocd = mti.getObjectClassDefinition( idList[j], locale );
                    } catch ( IllegalArgumentException ignore ) {
                        // ignore - just don't show this configuration
                    }
                    if ( ocd != null ) {
                        objectClassesDefinitions.put( idList[j], ocd );
                    }
                }
            }
        }
        return objectClassesDefinitions;
    }

    ObjectClassDefinition getObjectClassDefinition( Configuration config, String locale ) {
        // if the configuration is bound, try to get the object class
        // definition from the bundle installed from the given location
        if ( config.getBundleLocation() != null ) {
            Bundle bundle = getBundle( this.bundleContext, config.getBundleLocation() );
            if ( bundle != null ) {
                String id = config.getFactoryPid();
                if ( null == id ) {
                    id = config.getPid();
                }
                return getObjectClassDefinition( bundle, id, locale );
            }
        }

        // get here if the configuration is not bound or if no
        // bundle with the bound location is installed. We search
        // all bundles for a matching [factory] PID
        // if the configuration is a factory one, use the factory PID
        if ( config.getFactoryPid() != null ) {
            return this.getObjectClassDefinition( config.getFactoryPid(), locale );
        }

        // otherwise use the configuration PID
        return this.getObjectClassDefinition( config.getPid(), locale );
    }


    ObjectClassDefinition getObjectClassDefinition( Bundle bundle, String pid, String locale ) {
        if ( bundle != null ) {
            final MetaTypeInformation mti = this.service.getMetaTypeInformation( bundle );
            if ( mti != null ) {
                // see #getObjectClasses( final IdGetter idGetter, final String locale )
                try {
                    return mti.getObjectClassDefinition( pid, locale );
                } catch ( IllegalArgumentException e ) {
                    // MetaTypeProvider.getObjectClassDefinition might throw illegal
                    // argument exception. So we must catch it here, otherwise the
                    // other configurations will not be shown
                    // See https://issues.apache.org/jira/browse/FELIX-2390
                    // https://issues.apache.org/jira/browse/FELIX-3694
                }
            }
        }

        // fallback to nothing found
        return null;
    }


    ObjectClassDefinition getObjectClassDefinition( String pid, String locale ) {
        Bundle[] bundles = bundleContext.getBundles();
        for ( final Bundle bundle : bundles) {
            final ObjectClassDefinition ocd = this.getObjectClassDefinition( bundle, pid, locale );
            if ( ocd != null ) {
                return ocd;
            }
        }
        return null;
    }


    Map<String, MetatypePropertyDescriptor> getAttributeDefinitionMap( Configuration config, String locale ) {
        Map<String, MetatypePropertyDescriptor> adMap = new HashMap<>();
        ObjectClassDefinition ocd = this.getObjectClassDefinition( config, locale );
        if ( ocd != null ) {
            AttributeDefinition[] ad = ocd.getAttributeDefinitions( ObjectClassDefinition.ALL );
            if ( ad != null ) {
                for ( int i = 0; i < ad.length; i++ ) {
                    adMap.put( ad[i].getID(), new MetatypePropertyDescriptor( ad[i], false ) );
                }
            }
        }
        return adMap;
    }

    /**
     * The <code>IdGetter</code> interface is an internal helper to abstract
     * retrieving object class definitions from all bundles for either
     * pids or factory pids.
     *
     * @see #PID_GETTER
     * @see #FACTORY_PID_GETTER
     */
    private static interface IdGetter {
        String[] getIds( MetaTypeInformation metaTypeInformation );
    }

    /**
     * The implementation of the {@link IdGetter} interface returning the PIDs
     * listed in the meta type information.
     *
     * @see #getPidObjectClasses(String)
     */
    private static final IdGetter PID_GETTER = new IdGetter() {
        @Override
        public String[] getIds( MetaTypeInformation metaTypeInformation ) {
            return metaTypeInformation.getPids();
        }
    };

    /**
     * The implementation of the {@link IdGetter} interface returning the
     * factory PIDs listed in the meta type information.
     *
     * @see #getFactoryPidObjectClasses(String)
     */
    private static final IdGetter FACTORY_PID_GETTER = new IdGetter() {
        @Override
        public String[] getIds( MetaTypeInformation metaTypeInformation ) {
            return metaTypeInformation.getFactoryPids();
        }
    };

}

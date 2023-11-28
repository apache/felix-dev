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
package org.apache.felix.webconsole.internal.servlet;


import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;

import org.apache.felix.webconsole.internal.Util;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeProvider;
import org.osgi.service.metatype.ObjectClassDefinition;


class ConfigurationMetatypeSupport extends ConfigurationSupport implements MetaTypeProvider
{
    private static final String[] CONF_PROPS = new String[]
        { OsgiManager.PROP_MANAGER_ROOT, OsgiManager.DEFAULT_MANAGER_ROOT,
            OsgiManager.PROP_DEFAULT_RENDER, OsgiManager.DEFAULT_PAGE,
            OsgiManager.PROP_REALM, OsgiManager.DEFAULT_REALM,
            OsgiManager.PROP_USER_NAME, OsgiManager.DEFAULT_USER_NAME,
            OsgiManager.PROP_CATEGORY, OsgiManager.DEFAULT_CATEGORY,
            OsgiManager.PROP_LOCALE, "",
        };

    private final Object ocdLock = new Object();
    private String ocdLocale;
    private ObjectClassDefinition ocd;


    ConfigurationMetatypeSupport( OsgiManager osgiManager )
    {
        super( osgiManager );
    }


    //---------- MetaTypeProvider

    @Override
    public String[] getLocales()
    {
        // there is no locale support here
        return null;
    }


    @Override
    public ObjectClassDefinition getObjectClassDefinition( String id, String locale )
    {
        if ( !osgiManager.getConfigurationPid().equals( id ) )
        {
            return null;
        }

        if ( locale == null )
            locale = Locale.ENGLISH.getLanguage();

        // check if OCD is already initialized and it's locale is the same as the requested one
        synchronized ( ocdLock )
        {
            if ( ocd != null && ocdLocale != null && ocdLocale.equals( locale ) )
            {
                return ocd;
            }
        }

        ObjectClassDefinition xocd = null;
        final Locale localeObj = Util.parseLocaleString( locale );
        final ResourceBundle rb = osgiManager.resourceBundleManager.getResourceBundle( osgiManager.getBundleContext()
            .getBundle(), localeObj );
        final Map<String, Object> defaultConfig = osgiManager.getDefaultConfiguration();

        // simple configuration properties
        final ArrayList<AttributeDefinition> adList = new ArrayList<>();
        for ( int i = 0; i < CONF_PROPS.length; i++ )
        {
            final String key = CONF_PROPS[i++];
            final String defaultValue = ConfigurationUtil.getProperty( defaultConfig, key, CONF_PROPS[i] );
            final String name = getString( rb, "metadata." + key + ".name", key );
            final String descr = getString( rb, "metadata." + key + ".description", key );
            adList.add( new AttributeDefinitionImpl( key, name, descr, defaultValue ) );
        }

        // password is special
        final String pwKey = OsgiManager.PROP_PASSWORD;
        adList.add( new AttributeDefinitionImpl( pwKey, getString( rb, "metadata." + pwKey + ".name", pwKey ),
                getString( rb, "metadata." + pwKey + ".description", pwKey ) ) );

        // boolean props
        final String propKey = OsgiManager.PROP_ENABLE_SECRET_HEURISTIC;
        adList.add( new AttributeDefinitionImpl( propKey, getString( rb, "metadata." + propKey + ".name", propKey ),
                getString( rb, "metadata." + propKey + ".description", propKey ), OsgiManager.DEFAULT_ENABLE_SECRET_HEURISTIC ) );

        // list plugins - requires localized plugin titles
        final TreeMap<String, String> namesByClassName = new TreeMap<>();
        final String[] defaultPluginsClasses = PluginHolder.PLUGIN_MAP;
        for ( int i = 0; i < defaultPluginsClasses.length; i++ )
        {
            final String clazz = defaultPluginsClasses[i++];
            final String label = defaultPluginsClasses[i];
            final String name = getString( rb, label + ".pluginTitle", label );
            namesByClassName.put( clazz, name );
        }
        final String[] classes = ( String[] ) namesByClassName.keySet().toArray( new String[namesByClassName.size()] );
        final String[] names = ( String[] ) namesByClassName.values().toArray( new String[namesByClassName.size()] );

        adList.add( new AttributeDefinitionImpl( OsgiManager.PROP_ENABLED_PLUGINS, getString( rb,
            "metadata.plugins.name", OsgiManager.PROP_ENABLED_PLUGINS ),
            getString( rb, "metadata.plugins.description", OsgiManager.PROP_ENABLED_PLUGINS ),
            AttributeDefinition.STRING, classes, Integer.MIN_VALUE, names, classes ) );

        xocd = new ObjectClassDefinition()
        {

            private final AttributeDefinition[] attrs = ( AttributeDefinition[] ) adList
                .toArray( new AttributeDefinition[adList.size()] );


            @Override
            public String getName()
            {
                return getString( rb, "metadata.name", "Apache Felix OSGi Management Console" );
            }


            @Override
            public InputStream getIcon( int arg0 )
            {
                return null;
            }


            @Override
            public String getID()
            {
                return osgiManager.getConfigurationPid();
            }


            @Override
            public String getDescription()
            {
                return getString( rb,
                    "metadata.description", "Configuration of the Apache Felix OSGi Management Console." );
            }


            @Override
            public AttributeDefinition[] getAttributeDefinitions( int filter )
            {
                return ( filter == OPTIONAL ) ? null : attrs;
            }
        };

        synchronized ( ocdLock )
        {
            this.ocd = xocd;
            this.ocdLocale = locale;
        }

        return ocd;
    }


    private static final String getString( ResourceBundle rb, String key, String def )
    {
        try
        {
            return rb.getString( key );
        }
        catch ( Throwable t )
        {
            return def;
        }
    }

    private static class AttributeDefinitionImpl implements AttributeDefinition
    {

        private final String id;
        private final String name;
        private final String description;
        private final int type;
        private final String[] defaultValues;
        private final int cardinality;
        private final String[] optionLabels;
        private final String[] optionValues;


        AttributeDefinitionImpl( final String id, final String name, final String description )
        {
            this( id, name, description, PASSWORD, null, 0, null, null );
        }

        AttributeDefinitionImpl( final String id, final String name, final String description, final boolean defaultValue )
        {
            this( id, name, description, BOOLEAN, new String[]
                { String.valueOf(defaultValue) }, 0, null, null );
        }

        AttributeDefinitionImpl( final String id, final String name, final String description, final String defaultValue )
        {
            this( id, name, description, STRING, new String[]
                { defaultValue }, 0, null, null );
        }


        AttributeDefinitionImpl( final String id, final String name, final String description, final int type,
            final String[] defaultValues, final int cardinality, final String[] optionLabels,
            final String[] optionValues )
        {
            this.id = id;
            this.name = name;
            this.description = description;
            this.type = type;
            this.defaultValues = defaultValues;
            this.cardinality = cardinality;
            this.optionLabels = optionLabels;
            this.optionValues = optionValues;
        }


        @Override
        public int getCardinality()
        {
            return cardinality;
        }


        @Override
        public String[] getDefaultValue()
        {
            return defaultValues;
        }


        @Override
        public String getDescription()
        {
            return description;
        }


        @Override
        public String getID()
        {
            return id;
        }


        @Override
        public String getName()
        {
            return name;
        }


        @Override
        public String[] getOptionLabels()
        {
            return optionLabels;
        }


        @Override
        public String[] getOptionValues()
        {
            return optionValues;
        }


        @Override
        public int getType()
        {
            return type;
        }


        @Override
        public String validate( String arg0 )
        {
            return null;
        }
    }

}
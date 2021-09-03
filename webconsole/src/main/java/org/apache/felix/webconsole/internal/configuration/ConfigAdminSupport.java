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
package org.apache.felix.webconsole.internal.configuration;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.felix.webconsole.internal.misc.ServletSupport;
import org.apache.felix.webconsole.spi.ConfigurationHandler;
import org.apache.felix.webconsole.spi.ValidationException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.metatype.AttributeDefinition;


class ConfigAdminSupport {

    public static final String PROPERTY_FACTORYCONFIG_NAMEHINT = "webconsole.configurationFactory.nameHint";
    public static final Set<String> CONFIG_PROPERTIES_HIDE = new HashSet<>();
    static {
        CONFIG_PROPERTIES_HIDE.add(PROPERTY_FACTORYCONFIG_NAMEHINT);
        CONFIG_PROPERTIES_HIDE.add(ConfigurationAdmin.SERVICE_BUNDLELOCATION);
        CONFIG_PROPERTIES_HIDE.add(ConfigurationAdmin.SERVICE_FACTORYPID);
        CONFIG_PROPERTIES_HIDE.add(Constants.SERVICE_PID);
    }
    public static final Pattern NAMEHINT_PLACEHOLER_REGEXP = Pattern.compile("\\{([^\\{\\}]*)}");

    private final ConfigurationAdmin service;

    private final ServletSupport servletSupport;

    private final List<ConfigurationHandler> configurationHandlers;

    /**
     * Create a new support instance
     * @param support The servlet support for logging and bundle context
     * @param service The configuration admin service
     *
     * @throws ClassCastException if {@code service} is not a ConfigurationAdmin instances
     */
    ConfigAdminSupport( final ServletSupport support,
            final Object service,
            final List<ConfigurationHandler> handlers ) {
        this.servletSupport = support;
        this.service = ( ConfigurationAdmin ) service;
        this.configurationHandlers = handlers;
    }

    public ConfigJsonSupport getJsonSupport() {
        return new ConfigJsonSupport(this.servletSupport, getMetaTypeSupport(), this.service);
    }

    MetaTypeServiceSupport getMetaTypeSupport() {
        Object metaTypeService = servletSupport.getService( ConfigManager.META_TYPE_NAME );
        if ( metaTypeService != null ) {
            return new MetaTypeServiceSupport( servletSupport.getBundleContext(), metaTypeService );
        }

        return null;
    }

    boolean shouldSet(final PropertyDescriptor ad, final String value, final boolean isUpdate) 
    {
        if ( this.configurationHandlers.isEmpty() && ad.hasMetatype() && !isUpdate )
        {
            if ( value.isEmpty() && ad.getDefaultValue() == null )
            {
                return false;
            }
            if ( ad.getDefaultValue() != null && value.equals(ad.getDefaultValue()[0]) )
            {
                return false;
            }
        }
        return true;
    }

    boolean shouldSet(final PropertyDescriptor ad, final String[] values, final boolean isUpdate) 
    {
        if ( this.configurationHandlers.isEmpty() && ad.hasMetatype() && !isUpdate )
        {
            if ( ad.getDefaultValue() == null )
            {
                if ( values.length == 0 || (values.length == 1 && values[0].isEmpty() ) )
                {
                    return false;
                }
            }
            if ( ad.getDefaultValue() != null && Arrays.equals(ad.getDefaultValue(), values) )
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Apply the update to the configuration
     * @param request The request
     * @param pid The pid 
     * @param propertyList The list of properties
     * @param isUpdate {@code true} if this is a rest call, false if it is done via the webconsole UI
     * @throws IOException
     */
    void applyConfiguration( final HttpServletRequest request, final String pid, final String[] propertyList, final boolean isUpdate )
            throws ValidationException, IOException
    {
        final String factoryPid = request.getParameter( ConfigManager.FACTORY_PID );
        final Configuration config = ConfigurationUtil.getOrCreateConfiguration( this.service, this.configurationHandlers, pid, factoryPid );

        Dictionary<String, Object> props = config.getProperties();
        if ( props == null ) {
            props = new Hashtable<>();
        }

        final MetaTypeServiceSupport mtss = getMetaTypeSupport();
        final Map<String, MetatypePropertyDescriptor> adMap = ( mtss != null ) ? mtss.getAttributeDefinitionMap( config, null ) : new HashMap<>();
        final List<String> propsToKeep = new ArrayList<>();
        for(final String propName : propertyList)
        {
            final String paramName = "action".equals(propName) //$NON-NLS-1$
                    || ConfigManager.ACTION_DELETE.equals(propName)
                    || ConfigManager.ACTION_APPLY.equals(propName)
                    || ConfigManager.PROPERTY_LIST.equals(propName)
                    ? '$' + propName : propName;
            propsToKeep.add(propName);

            PropertyDescriptor ad = adMap.get( propName );

            // try to derive from current value
            if (ad == null) {
                Object currentValue = props.get( propName );
                ad = MetaTypeSupport.createAttributeDefinition( propName, currentValue );
            }

            final int attributeType = MetaTypeSupport.getAttributeType( ad );

            if ( ad.getCardinality() == 0 && ( attributeType == AttributeDefinition.STRING || attributeType == AttributeDefinition.PASSWORD ) )
            {
                final String value = request.getParameter( paramName );
                if ( value != null
                    && ( attributeType != AttributeDefinition.PASSWORD || !MetaTypeSupport.PASSWORD_PLACEHOLDER_VALUE.equals( value ) ) )
                {
                    if ( shouldSet(ad, value, isUpdate) ) 
                    {
                        props.put( propName, value );
                    }
                    else
                    {
                        props.remove( propName );
                    }
                }
            }
            else if ( ad.getCardinality() == 0 )
            {
                // scalar of non-string
                final String value = request.getParameter( paramName );
                if ( value != null )
                {
                    if ( shouldSet(ad, value, isUpdate) ) 
                    {
                        try
                        {
                            props.put( propName, MetaTypeSupport.toType( attributeType, value ) );
                        }
                        catch ( final NumberFormatException nfe )
                        {
                            // the value is put as a string, for example this could be a placeholder etc
                            props.put( propName, value);
                        }
                    }
                    else
                    {
                        props.remove( propName );
                    }
                }
            }
            else
            {
                // array or vector of any type
                Vector<Object> vec = new Vector<>();
                boolean formatError = false;

                final String[] values = request.getParameterValues( paramName );
                if ( values != null )
                {
                    if ( attributeType == AttributeDefinition.PASSWORD )
                    {
                        MetaTypeSupport.setPasswordProps( vec, values, props.get( propName ) );
                    }
                    else
                    {
                        for ( int i = 0; i < values.length; i++ )
                        {
                            try
                            {
                                vec.add( MetaTypeSupport.toType( attributeType, values[i] ) );
                            }
                            catch ( NumberFormatException nfe )
                            {
                                // the value is put as a string, for example this could be a placeholder etc
                                vec.add( values[i] );
                                formatError = true;
                            }
                        }
                    }
                }

                // if a format error occurred revert to String!
                if ( formatError )
                {
                    Vector<Object> newVec = new Vector<Object>();
                    for(final Object v : vec)
                    {
                        newVec.add(v.toString());
                    }
                    vec = newVec;
                }

                // but ensure size (check for positive value since
                // abs(Integer.MIN_VALUE) is still INTEGER.MIN_VALUE)
                int maxSize = Math.abs( ad.getCardinality() );
                if ( vec.size() > maxSize && maxSize > 0 )
                {
                    vec.setSize( maxSize );
                }

                // create array to compare
                final String[] valueArray = new String[vec.size()];
                for(int i=0; i<vec.size();i++) 
                {
                    valueArray[i] = vec.get(i).toString();
                }
                
                final boolean shouldSet = shouldSet(ad, valueArray, isUpdate);

                if ( ad.getCardinality() < 0 )
                {
                    // keep the vector, but only add if not empty
                    if ( !shouldSet || vec.isEmpty() )
                    {
                        props.remove( propName );
                    }
                    else
                    {
                        if ( shouldSet )
                        {
                            props.put( propName, vec );
                        }                        
                    }
                }
                else
                {
                    if ( shouldSet )
                    {
                        // convert to an array
                        props.put( propName, MetaTypeSupport.toArray( formatError ? AttributeDefinition.STRING : attributeType, vec ) );
                    }
                    else
                    {
                        props.remove( propName );
                    }
                }
            }
        }

        if ( !isUpdate ) 
        {
            // remove the properties that are not specified in the request
            final Dictionary<String, Object> updateProps = new Hashtable<>(props.size());
            for ( Enumeration<String> e = props.keys(); e.hasMoreElements(); )
            {
                final String key = e.nextElement();
                if ( propsToKeep.contains(key) && props.get(key) != null )
                {
                    updateProps.put(key, props.get(key));
                }
            }
            props = updateProps;
        }

        // call update handlers
        for(final ConfigurationHandler h : this.configurationHandlers) {
            h.updateConfiguration(factoryPid, pid, props);
        }
  
        final String location = request.getParameter(ConfigManager.LOCATION);
        if ( location == null || location.trim().length() == 0 || ConfigManager.UNBOUND_LOCATION.equals(location) )
        {
            if ( config.getBundleLocation() != null )
            {
                config.setBundleLocation(null);
                // workaround for Felix Config Admin 1.2.8 not clearing dynamic
                // bundle location when clearing static bundle location. In
                // this case we first set the static bundle location to the
                // dynamic bundle location and then try to set both to null
                if ( config.getBundleLocation() != null )
                {
                    config.setBundleLocation( "??invalid:bundle/location" ); //$NON-NLS-1$
                    config.setBundleLocation( null );
                }
            }
        } 
        else
        {
            if ( config.getBundleLocation() == null || !config.getBundleLocation().equals(location) )
            {
                config.setBundleLocation(location);
            }
        }
        config.update( props );
    }

    public void deleteConfiguration(final String pid) throws ValidationException, IOException {
        // only delete if the PID is not our place holder
        if ( !ConfigurationUtil.getPlaceholderPid().equals( pid ) ) {
            final Configuration config = ConfigurationUtil.findConfiguration(this.service, pid);
            if ( config != null ) {
                for(final ConfigurationHandler h : this.configurationHandlers) {
                    h.deleteConfiguration(config.getFactoryPid(), config.getPid());
                }
                config.delete();
            }
        }            
    }

    public Configuration findConfiguration(final String pid) {
        return ConfigurationUtil.findConfiguration(this.service, pid);
    }

    public Configuration[] listConfigurations(final String filter) throws IOException, InvalidSyntaxException {
        return this.service.listConfigurations(filter);
    }
}

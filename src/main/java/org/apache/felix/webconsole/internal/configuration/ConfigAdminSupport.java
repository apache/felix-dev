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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import org.apache.felix.webconsole.internal.Util;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.ObjectClassDefinition;


class ConfigAdminSupport
{

    private static final String PROPERTY_FACTORYCONFIG_NAMEHINT = "webconsole.configurationFactory.nameHint";
    private static final Set CONFIG_PROPERTIES_HIDE = new HashSet();
    static {
        CONFIG_PROPERTIES_HIDE.add(PROPERTY_FACTORYCONFIG_NAMEHINT);
    }

    private final BundleContext bundleContext;
    private final ConfigurationAdmin service;

    private final ConfigManager configManager;


    /**
     *
     * @param bundleContext
     * @param service
     *
     * @throws ClassCastException if {@code service} is not a MetaTypeService instances
     */
    ConfigAdminSupport( final ConfigManager configManager, final BundleContext bundleContext, final Object service )
    {
        this.configManager = configManager;
        this.bundleContext = bundleContext;
        this.service = ( ConfigurationAdmin ) service;
    }


    public BundleContext getBundleContext()
    {
        return bundleContext;
    }

    private MetaTypeServiceSupport getMetaTypeSupport()
    {
        Object metaTypeService = configManager.getService( ConfigManager.META_TYPE_NAME );
        if ( metaTypeService != null )
        {
            return new MetaTypeServiceSupport( this.getBundleContext(), metaTypeService );
        }

        return null;
    }


    final Configuration getConfiguration( String pid )
    {
        if ( pid != null )
        {
            try
            {
                // we use listConfigurations to not create configuration
                // objects persistently without the user providing actual
                // configuration
                String filter = '(' + Constants.SERVICE_PID + '=' + pid + ')';
                Configuration[] configs = this.service.listConfigurations( filter );
                if ( configs != null && configs.length > 0 )
                {
                    return configs[0];
                }
            }
            catch ( InvalidSyntaxException ise )
            {
                // should print message
            }
            catch ( IOException ioe )
            {
                // should print message
            }
        }

        // fallback to no configuration at all
        return null;
    }


    final Configuration getConfiguration( String pid, String factoryPid ) throws IOException
    {
        if ( factoryPid != null && ( pid == null || pid.equals( ConfigManager.PLACEHOLDER_PID ) ) )
        {
            return this.service.createFactoryConfiguration( factoryPid, null );
        }

        return this.service.getConfiguration( pid, null );
    }


    Configuration getPlaceholderConfiguration( final String factoryPid )
    {
        return new PlaceholderConfiguration( factoryPid );
    }

    String getPlaceholderPid() {
        return ConfigManager.PLACEHOLDER_PID;
    }

    String applyConfiguration( HttpServletRequest request, String pid )
        throws IOException
    {
        if ( request.getParameter( ConfigManager.ACTION_DELETE ) != null ) //$NON-NLS-1$
        {
            // only delete if the PID is not our place holder
            if ( !ConfigManager.PLACEHOLDER_PID.equals( pid ) )
            {
                configManager.log( "applyConfiguration: Deleting configuration " + pid );
                Configuration config = service.getConfiguration( pid, null );
                config.delete();
            }
            return null; // return request.getHeader( "Referer" );
        }

        String factoryPid = request.getParameter( ConfigManager.FACTORY_PID );
        Configuration config = null;

        String propertyList = request.getParameter( ConfigManager.PROPERTY_LIST ); //$NON-NLS-1$
        if ( propertyList == null )
        {
            // FIXME: this would be a bug !!
        }
        else
        {
            config = getConfiguration( pid, factoryPid );

            Dictionary props = config.getProperties();
            if ( props == null )
            {
                props = new Hashtable();
            }

            final MetaTypeServiceSupport mtss = getMetaTypeSupport();
            final Map adMap = ( mtss != null ) ? mtss.getAttributeDefinitionMap( config, null ) : new HashMap();
            final StringTokenizer propTokens = new StringTokenizer( propertyList, "," ); //$NON-NLS-1$
            final List propsToKeep = new ArrayList();
            while ( propTokens.hasMoreTokens() )
            {
                String propName = propTokens.nextToken();
                String paramName = "action".equals(propName) //$NON-NLS-1$
                    || ConfigManager.ACTION_DELETE.equals(propName)
                    || ConfigManager.ACTION_APPLY.equals(propName)
                    || ConfigManager.PROPERTY_LIST.equals(propName)
                    ? '$' + propName : propName;
                propsToKeep.add(propName);

                PropertyDescriptor ad = (PropertyDescriptor) adMap.get( propName );

                // try to derive from current value
                if (ad == null) {
                    Object currentValue = props.get( propName );
                    ad = MetaTypeSupport.createAttributeDefinition( propName, currentValue );
                }

                int attributeType = MetaTypeSupport.getAttributeType( ad );

                if ( ad == null
                    || ( ad.getCardinality() == 0 && ( attributeType == AttributeDefinition.STRING || attributeType == MetaTypeServiceSupport.ATTRIBUTE_TYPE_PASSWORD ) ) )
                {
                    String prop = request.getParameter( paramName );
                    if ( prop != null
                        && ( attributeType != MetaTypeSupport.ATTRIBUTE_TYPE_PASSWORD || !MetaTypeSupport.PASSWORD_PLACEHOLDER_VALUE.equals( prop ) ) )
                    {
                        props.put( propName, prop );
                    }
                }
                else if ( ad.getCardinality() == 0 )
                {
                    // scalar of non-string
                    String prop = request.getParameter( paramName );
                    if ( prop != null )
                    {
                        try
                        {
                            props.put( propName, MetaTypeSupport.toType( attributeType, prop ) );
                        }
                        catch ( NumberFormatException nfe )
                        {
                            // don't care
                        }
                    }
                }
                else
                {
                    // array or vector of any type
                    Vector vec = new Vector();

                    String[] properties = request.getParameterValues( paramName );
                    if ( properties != null )
                    {
                        if ( attributeType == MetaTypeSupport.ATTRIBUTE_TYPE_PASSWORD )
                        {
                            MetaTypeSupport.setPasswordProps( vec, properties, props.get( propName ) );
                        }
                        else
                        {
                            for ( int i = 0; i < properties.length; i++ )
                            {
                                try
                                {
                                    vec.add( MetaTypeSupport.toType( attributeType, properties[i] ) );
                                }
                                catch ( NumberFormatException nfe )
                                {
                                    // don't care
                                }
                            }
                        }
                    }

                    // but ensure size (check for positive value since
                    // abs(Integer.MIN_VALUE) is still INTEGER.MIN_VALUE)
                    int maxSize = Math.abs( ad.getCardinality() );
                    if ( vec.size() > maxSize && maxSize > 0 )
                    {
                        vec.setSize( maxSize );
                    }

                    if ( ad.getCardinality() < 0 )
                    {
                        // keep the vector, but only add if not empty
                        if ( vec.isEmpty() )
                        {
                            props.remove( propName );
                        }
                        else
                        {
                            props.put( propName, vec );
                        }
                    }
                    else
                    {
                        // convert to an array
                        props.put( propName, MetaTypeSupport.toArray( attributeType, vec ) );
                    }
                }
            }

            // remove the properties that are not specified in the request
            final Dictionary updateProps = new Hashtable(props.size());
            for ( Enumeration e = props.keys(); e.hasMoreElements(); )
            {
                final Object key = e.nextElement();
                if ( propsToKeep.contains(key) )
                {
                    updateProps.put(key, props.get(key));
                }
            }

            config.update( updateProps );
        }

        // redirect to the new configuration (if existing)
        return (config != null) ? config.getPid() : ""; //$NON-NLS-1$
    }


    void printConfigurationJson( PrintWriter pw, String pid, Configuration config, String pidFilter,
        String locale )
    {

        JSONWriter result = new JSONWriter( pw );

        if ( pid != null )
        {
            try
            {
                result.object();
                this.configForm( result, pid, config, pidFilter, locale );
                result.endObject();
            }
            catch ( Exception e )
            {
                configManager.log( "Error reading configuration PID " + pid, e );
            }
        }

    }


    void configForm( JSONWriter json, String pid, Configuration config, String pidFilter, String locale )
        throws JSONException
    {

        json.key( ConfigManager.PID );
        json.value( pid );

        if ( pidFilter != null )
        {
            json.key( ConfigManager.PID_FILTER );
            json.value( pidFilter );
        }

        Dictionary props = null;
        if ( config != null )
        {
            props = config.getProperties(); // unchecked
        }
        if ( props == null )
        {
            props = new Hashtable();
        }

        boolean doSimpleMerge = true;
        final MetaTypeServiceSupport mtss = getMetaTypeSupport();
        if ( mtss != null )
        {
            ObjectClassDefinition ocd = null;
            if ( config != null )
            {
                ocd = mtss.getObjectClassDefinition( config, locale );
            }
            if ( ocd == null )
            {
                ocd = mtss.getObjectClassDefinition( pid, locale );
            }
            if ( ocd != null )
            {
                mtss.mergeWithMetaType( props, ocd, json, CONFIG_PROPERTIES_HIDE );
                doSimpleMerge = false;
            }
        }

        if (doSimpleMerge)
        {
            json.key( "title" ).value( pid ); //$NON-NLS-1$
            json.key( "description" ).value( //$NON-NLS-1$
                "This form is automatically generated from existing properties because no property "
                    + "descriptors are available for this configuration. This may be cause by the absence "
                    + "of the OSGi Metatype Service or the absence of a MetaType descriptor for this configuration." );

            json.key( "properties" ).object(); //$NON-NLS-1$
            for ( Enumeration pe = props.keys(); pe.hasMoreElements(); )
            {
                final String id = ( String ) pe.nextElement();

                // ignore well known special properties
                if ( !id.equals( Constants.SERVICE_PID ) && !id.equals( Constants.SERVICE_DESCRIPTION )
                    && !id.equals( Constants.SERVICE_ID ) && !id.equals( Constants.SERVICE_VENDOR )
                    && !id.equals( ConfigurationAdmin.SERVICE_BUNDLELOCATION )
                    && !id.equals( ConfigurationAdmin.SERVICE_FACTORYPID ) )
                {
                    final Object value = props.get( id );
                    final PropertyDescriptor ad = MetaTypeServiceSupport.createAttributeDefinition( id, value );
                    json.key( id );
                    MetaTypeServiceSupport.attributeToJson( json, ad, value );
                }
            }
            json.endObject();
        }

        if ( config != null )
        {
            this.addConfigurationInfo( config, json, locale );
        }
    }


    void addConfigurationInfo( Configuration config, JSONWriter json, String locale ) throws JSONException
    {

        if ( config.getFactoryPid() != null )
        {
            json.key( ConfigManager.FACTORY_PID );
            json.value( config.getFactoryPid() );
        }

        String location;
        if ( config.getBundleLocation() == null )
        {
            location = ""; //$NON-NLS-1$
        }
        else
        {
            // if the configuration is bound to a bundle location which
            // is not related to an installed bundle, we just print the
            // raw bundle location binding
            Bundle bundle = MetaTypeServiceSupport.getBundle( this.getBundleContext(), config.getBundleLocation() );
            if ( bundle == null )
            {
                location = config.getBundleLocation();
            }
            else
            {
                Dictionary headers = bundle.getHeaders( locale );
                String name = ( String ) headers.get( Constants.BUNDLE_NAME );
                if ( name == null )
                {
                    location = bundle.getSymbolicName();
                }
                else
                {
                    location = name + " (" + bundle.getSymbolicName() + ')'; //$NON-NLS-1$
                }

                Version v = Version.parseVersion( ( String ) headers.get( Constants.BUNDLE_VERSION ) );
                location += ", Version " + v.toString();
            }
        }
        json.key( "bundleLocation" ); //$NON-NLS-1$
        json.value( location );
        // raw bundle location and service locations
        final String pid = config.getPid();
        String serviceLocation = ""; //$NON-NLS-1$
        try
        {
            final ServiceReference[] refs = getBundleContext().getServiceReferences(
                null,
                "(&(" + Constants.OBJECTCLASS + '=' + ManagedService.class.getName() //$NON-NLS-1$
                    + ")(" + Constants.SERVICE_PID + '=' + pid + "))"); //$NON-NLS-1$ //$NON-NLS-2$
            if ( refs != null && refs.length > 0 )
            {
                serviceLocation = refs[0].getBundle().getLocation();
            }
        }
        catch (Throwable t)
        {
            configManager.log( "Error getting service associated with configuration " + pid, t );
        }
        json.key( "bundle_location" ); //$NON-NLS-1$
        json.value ( config.getBundleLocation() );
        json.key( "service_location" ); //$NON-NLS-1$
        json.value ( serviceLocation );
    }


    private final Bundle getBoundBundle(Configuration config)
    {
        if (null == config)
            return null;
        final String location = config.getBundleLocation();
        if (null == location)
            return null;

        final Bundle bundles[] = getBundleContext().getBundles();
        for (int i = 0; bundles != null && i < bundles.length; i++)
        {
            if (bundles[i].getLocation().equals(location))
                return bundles[i];

        }
        return null;
    }


    final void listConfigurations( JSONObject json, String pidFilter, String locale, Locale loc )
    {
        try
        {
            // start with ManagedService instances
            Map optionsPlain = getServices(ManagedService.class.getName(), pidFilter,
                locale, true);

            // next are the MetaType informations without ManagedService
            final MetaTypeServiceSupport mtss = getMetaTypeSupport();
            if ( mtss != null )
            {
                addMetaTypeNames( optionsPlain, mtss.getPidObjectClasses( locale ), pidFilter, Constants.SERVICE_PID );
            }

            // add in existing configuration (not duplicating ManagedServices)
            Configuration[] cfgs = this.service.listConfigurations(pidFilter);
            for (int i = 0; cfgs != null && i < cfgs.length; i++)
            {

                // ignore configuration object if an entry already exists in the map
                // or if it is invalid
                final String pid = cfgs[i].getPid();
                if (optionsPlain.containsKey(pid) || !ConfigManager.isAllowedPid(pid) )
                {
                    continue;
                }

                // insert and entry for the PID
                if ( mtss != null )
                {
                    try
                    {
                        ObjectClassDefinition ocd = mtss.getObjectClassDefinition( cfgs[i], locale );
                        if ( ocd != null )
                        {
                            optionsPlain.put( pid, ocd.getName() );
                            continue;
                        }
                    }
                    catch ( IllegalArgumentException t )
                    {
                        // MetaTypeProvider.getObjectClassDefinition might throw illegal
                        // argument exception. So we must catch it here, otherwise the
                        // other configurations will not be shown
                        // See https://issues.apache.org/jira/browse/FELIX-2390
                    }
                }

                // no object class definition, use plain PID
                optionsPlain.put( pid, pid );
            }

            for ( Iterator ii = optionsPlain.keySet().iterator(); ii.hasNext(); )
            {
                String id = ( String ) ii.next();
                Object name = optionsPlain.get( id );

                final Configuration config = this.getConfiguration( id );
                JSONObject data = new JSONObject() //
                    .put( "id", id ) //$NON-NLS-1$
                    .put( "name", name ); //$NON-NLS-1$
                if ( null != config )
                {
                    // FELIX-3848
                    data.put ( "has_config", true ); //$NON-NLS-1$

                    final String fpid = config.getFactoryPid();
                    if ( null != fpid )
                    {
                        data.put( "fpid", fpid ); //$NON-NLS-1$
                        data.putOpt( "nameHint", getConfigurationFactoryNameHint(config, mtss) ); //$NON-NLS-1$
                    }

                    final Bundle bundle = getBoundBundle( config );
                    if ( null != bundle )
                    {
                        data.put( "bundle", bundle.getBundleId() ); //$NON-NLS-1$
                        data.put( "bundle_name", Util.getName( bundle, loc ) ); //$NON-NLS-1$
                    }
                }

                json.append( "pids", data ); //$NON-NLS-1$
            }
        }
        catch (Exception e)
        {
            configManager.log("listConfigurations: Unexpected problem encountered", e);
        }
    }

    /**
     * Builds a "name hint" for factory configuration based on other property
     * values of the config and a "name hint template" defined as hidden
     * property in the service.
     * @param props Service properties.
     * @return Name hint or null if none is defined.
     */
    private static final String getConfigurationFactoryNameHint(Configuration config, MetaTypeServiceSupport mtss)
    {
        // check for configured name hint template
        Dictionary props = config.getProperties();
        Object nameHintTemplateObject = props.get(PROPERTY_FACTORYCONFIG_NAMEHINT);
        if (nameHintTemplateObject == null || !(nameHintTemplateObject instanceof String))
        {
            // check for metatype default value for name hint template
            if (mtss != null)
            {
                Map adMap = mtss.getAttributeDefinitionMap(config, null);
                PropertyDescriptor ad = (PropertyDescriptor)adMap.get(PROPERTY_FACTORYCONFIG_NAMEHINT);
                if (ad != null && ad.getDefaultValue() != null && ad.getDefaultValue().length == 1)
                {
                    nameHintTemplateObject = ad.getDefaultValue()[0];
                }
            }
            if (nameHintTemplateObject == null)
            {
                return null;
            }
        }
        String nameHint = (String) nameHintTemplateObject;
        Enumeration keys = props.keys();
        while (keys.hasMoreElements())
        {
            String key = (String) keys.nextElement();
            Object value = props.get(key);
            if (value != null)
            {
                StringBuffer valueString = new StringBuffer();
                if (value instanceof String[]) {
                    String[] valueArray = (String[])value;
                    for (int i = 0; i < valueArray.length; i++) {
                        if (i > 0) {
                            valueString.append(",");
                        }
                        valueString.append(valueArray[i]);
                    }
                }
                else {
                    valueString.append(value.toString());
                }
                nameHint = nameHint.replaceAll(regexQuote("{" + key + "}"), valueString.toString());
            }
        }
        return nameHint;
    }

    /**
     * Replacement for Pattern.quote(), which only available in JDK 1.5 and up.
     * @param str Unquoted string
     * @return Quoted string
     */
    private static final String regexQuote(String str)
    {
        int eInd = str.indexOf("\\E");
        if (eInd < 0)
        {
            // No need to handle backslashes.
            return "\\Q" + str + "\\E";
        }

        StringBuffer sb = new StringBuffer(str.length() + 16);
        sb.append("\\Q"); // start quote

        int pos = 0;
        do
        {
            // A backslash is quoted by another backslash;
            // 'E' is not needed to be quoted.
            sb.append(str.substring(pos, eInd)).append("\\E" + "\\\\" + "E" + "\\Q");
            pos = eInd + 2;
        }
        while ((eInd = str.indexOf("\\E", pos)) >= 0);

        sb.append(str.substring(pos, str.length())).append("\\E"); // end quote
        return sb.toString();
    }

    final void listFactoryConfigurations(JSONObject json, String pidFilter,
        String locale)
    {
        try
        {
            final Map optionsFactory = getServices(ManagedServiceFactory.class.getName(),
                pidFilter, locale, true);
            final MetaTypeServiceSupport mtss = getMetaTypeSupport();
            if ( mtss != null )
            {
                addMetaTypeNames( optionsFactory, mtss.getFactoryPidObjectClasses( locale ), pidFilter,
                    ConfigurationAdmin.SERVICE_FACTORYPID );
            }
            for ( Iterator ii = optionsFactory.keySet().iterator(); ii.hasNext(); )
            {
                String id = ( String ) ii.next();
                Object name = optionsFactory.get( id );
                json.append( "fpids", new JSONObject() //$NON-NLS-1$
                    .put( "id", id ) //$NON-NLS-1$
                    .put( "name", name ) ); //$NON-NLS-1$
            }
        }
        catch (Exception e)
        {
            configManager.log("listFactoryConfigurations: Unexpected problem encountered", e);
        }
    }

    SortedMap getServices( String serviceClass, String serviceFilter, String locale,
        boolean ocdRequired ) throws InvalidSyntaxException
    {
        // sorted map of options
        SortedMap optionsFactory = new TreeMap( String.CASE_INSENSITIVE_ORDER );

        // find all ManagedServiceFactories to get the factoryPIDs
        ServiceReference[] refs = this.getBundleContext().getServiceReferences( serviceClass, serviceFilter );
        for ( int i = 0; refs != null && i < refs.length; i++ )
        {
            Object pidObject = refs[i].getProperty( Constants.SERVICE_PID );
            // only include valid PIDs
            if ( pidObject instanceof String && ConfigManager.isAllowedPid((String)pidObject) )
            {
                String pid = ( String ) pidObject;
                String name = pid;
                boolean haveOcd = !ocdRequired;
                final MetaTypeServiceSupport mtss = getMetaTypeSupport();
                if ( mtss != null )
                {
                    final ObjectClassDefinition ocd = mtss.getObjectClassDefinition( refs[i].getBundle(), pid, locale );
                    if ( ocd != null )
                    {
                        name = ocd.getName();
                        haveOcd = true;
                    }
                }

                if ( haveOcd )
                {
                    optionsFactory.put( pid, name );
                }
            }
        }

        return optionsFactory;
    }

    private void addMetaTypeNames( final Map pidMap, final Map ocdCollection, final String filterSpec, final String type )
    {
        Filter filter = null;
        if ( filterSpec != null )
        {
            try
            {
                filter = getBundleContext().createFilter( filterSpec );
            }
            catch ( InvalidSyntaxException not_expected )
            {
                /* filter is correct */
            }
        }

        for ( Iterator ei = ocdCollection.entrySet().iterator(); ei.hasNext(); )
        {
            Entry ociEntry = ( Entry ) ei.next();
            final String pid = ( String ) ociEntry.getKey();
            final ObjectClassDefinition ocd = ( ObjectClassDefinition ) ociEntry.getValue();
            if ( filter == null )
            {
                pidMap.put( pid, ocd.getName() );
            }
            else
            {
                final Dictionary props = new Hashtable();
                props.put( type, pid );
                if ( filter.match( props ) )
                {
                    pidMap.put( pid, ocd.getName() );
                }
            }
        }
    }

    private static class PlaceholderConfiguration implements Configuration
    {

        private final String factoryPid;
        private String bundleLocation;


        PlaceholderConfiguration( String factoryPid )
        {
            this.factoryPid = factoryPid;
        }


        public String getPid()
        {
            return ConfigManager.PLACEHOLDER_PID;
        }


        public String getFactoryPid()
        {
            return factoryPid;
        }


        public void setBundleLocation( String bundleLocation )
        {
            this.bundleLocation = bundleLocation;
        }


        public String getBundleLocation()
        {
            return bundleLocation;
        }


        public Dictionary getProperties()
        {
            // dummy configuration has no properties
            return null;
        }


        public void update()
        {
            // dummy configuration cannot be updated
        }


        public void update( Dictionary properties )
        {
            // dummy configuration cannot be updated
        }


        public void delete()
        {
            // dummy configuration cannot be deleted
        }

    }

    public Configuration[] listConfigurations(String filter) throws IOException, InvalidSyntaxException
    {
        return this.service.listConfigurations(filter);
    }
}

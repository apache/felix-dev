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
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;

import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.webconsole.internal.Util;
import org.apache.felix.webconsole.internal.misc.ServletSupport;
import org.apache.felix.webconsole.spi.ConfigurationHandler;
import org.osgi.framework.Bundle;
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
import org.osgi.util.tracker.ServiceTracker;


class ConfigJsonSupport {

    private final ServletSupport servletSupport;

    private final MetaTypeServiceSupport mtss;
    
    private final ConfigurationAdmin configurationAdmin;

    public ConfigJsonSupport(final ServletSupport support, 
            final MetaTypeServiceSupport mtss, 
            final ConfigurationAdmin cfgAdmin) {
        this.servletSupport = support;
        this.mtss = mtss;
        this.configurationAdmin = cfgAdmin;
    }

    public void printConfigurationJson( final PrintWriter pw, final String pid, final Configuration config, final String pidFilter,
            final String locale, ServiceTracker<ConfigurationHandler, ConfigurationHandler> serviceTracker) {

        final JSONWriter result = new JSONWriter( pw );

        if ( pid != null ) {
            try{
                result.object();
                this.configForm( result, pid, config, pidFilter, locale, serviceTracker );
                result.endObject();
            } catch ( final Exception e ) {
                this.servletSupport.log( "Error reading configuration PID " + pid, e );
            }
        }

    }

    void configForm( final JSONWriter json, final String pid, final Configuration config, final String pidFilter, final String locale, ServiceTracker<ConfigurationHandler, ConfigurationHandler> serviceTracker )
    throws IOException {
        json.key( ConfigManager.PID );
        json.value( pid );

        if ( pidFilter != null ) {
            json.key( ConfigManager.PID_FILTER );
            json.value( pidFilter );
        }

        Dictionary<String, Object> props = null;
        List<String> filteredKeys = Collections.emptyList();
        if ( config != null ) {
            props = config.getProperties();
            if (props != null && serviceTracker != null) {
                Object[] services = serviceTracker.getServices();
                if (services != null) {
                    for(final Object o : services) {
                        ConfigurationHandler handler = (ConfigurationHandler)o;
                        List<String> allKeys = Collections.list(props.keys());
                        filteredKeys = handler.filterProperties(config, allKeys);
                        allKeys.removeAll(filteredKeys);
                        for (String key : allKeys) {
                            props.remove(key);
                        }
                    }
                }
            }
        }
        final List<String> keys = filteredKeys;
        if ( props == null ) {
            props = new Hashtable<>();
        }

        boolean doSimpleMerge = true;
        if ( this.mtss != null ) {
            ObjectClassDefinition ocd = null;
            if ( config != null ) {
                ocd = mtss.getObjectClassDefinition( config, locale );
            }
            if ( ocd == null ) {
                ocd = mtss.getObjectClassDefinition( pid, locale );
            }
            ObjectClassDefinition filteredOcd = ocd;
            if ( ocd != null ) {
                final ObjectClassDefinition focd = ocd;
                filteredOcd = new ObjectClassDefinition() {
                    @Override
                    public String getName() {
                        return focd.getName();
                    }

                    @Override
                    public String getID() {
                        return focd.getID();
                    }

                    @Override
                    public String getDescription() {
                        return focd.getDescription();
                    }

                    @Override
                    public AttributeDefinition[] getAttributeDefinitions(int i) {
                        AttributeDefinition[] allDefinitions = focd.getAttributeDefinitions(i);
                        if (allDefinitions != null) {
                            ArrayList<AttributeDefinition> filteredDefinitions = new ArrayList<>();
                            for (AttributeDefinition def : allDefinitions) {
                                if (keys.contains(def.getID())) {
                                    filteredDefinitions.add(def);
                                }
                            }
                            return filteredDefinitions.toArray(new AttributeDefinition[0]);
                        }
                        return null;
                    }

                    @Override
                    public InputStream getIcon(int i) throws IOException {
                        return focd.getIcon(i);
                    }
                };
                mtss.mergeWithMetaType( props, filteredOcd, json, ConfigAdminSupport.CONFIG_PROPERTIES_HIDE );
                doSimpleMerge = false;
            }
        }

        if (doSimpleMerge) {
            json.key( "title" ).value( pid ); //$NON-NLS-1$
            json.key( "description" ).value( //$NON-NLS-1$
                    "This form is automatically generated from existing properties because no property "
                    + "descriptors are available for this configuration. This may be cause by the absence "
                    + "of the OSGi Metatype Service or the absence of a MetaType descriptor for this configuration." );

            json.key( "properties" ).object(); //$NON-NLS-1$
            for ( Enumeration<String> pe = props.keys(); pe.hasMoreElements(); ) {
                final String id = pe.nextElement();

                // ignore well known special properties
                if ( !id.equals( Constants.SERVICE_PID ) && !id.equals( Constants.SERVICE_DESCRIPTION )
                        && !id.equals( Constants.SERVICE_ID ) && !id.equals( Constants.SERVICE_VENDOR )
                        && !id.equals( ConfigurationAdmin.SERVICE_BUNDLELOCATION )
                        && !id.equals( ConfigurationAdmin.SERVICE_FACTORYPID ) ) {
                    final Object value = props.get( id );
                    final PropertyDescriptor ad = MetaTypeServiceSupport.createAttributeDefinition( id, value );
                    json.key( id );
                    MetaTypeServiceSupport.attributeToJson( json, ad, value );
                }
            }
            json.endObject();
        }

        if ( config != null ) {
            this.addConfigurationInfo( config, json, locale );
        }
    }


    void addConfigurationInfo( final Configuration config, final JSONWriter json, final String locale )
            throws IOException {

        if ( config.getFactoryPid() != null ) {
            json.key( ConfigManager.FACTORY_PID );
            json.value( config.getFactoryPid() );
        }

        String bundleLocation = config.getBundleLocation();
        if ( ConfigManager.UNBOUND_LOCATION.equals(bundleLocation) ) {
            bundleLocation = null;
        }
        String location;
        if ( bundleLocation == null ) {
            location = ""; //$NON-NLS-1$
        } else {
            // if the configuration is bound to a bundle location which
            // is not related to an installed bundle, we just print the
            // raw bundle location binding
            Bundle bundle = MetaTypeServiceSupport.getBundle( this.servletSupport.getBundleContext(), bundleLocation );
            if ( bundle == null ) {
                location = bundleLocation;
            } else {
                Dictionary<String, String> headers = bundle.getHeaders( locale );
                String name = headers.get( Constants.BUNDLE_NAME );
                if ( name == null ) {
                    location = bundle.getSymbolicName();
                } else {
                    location = name + " (" + bundle.getSymbolicName() + ')'; //$NON-NLS-1$
                }

                Version v = Version.parseVersion( headers.get( Constants.BUNDLE_VERSION ) );
                location += ", Version " + v.toString();
            }
        }
        json.key( "bundleLocation" ); //$NON-NLS-1$
        json.value( location );
        // raw bundle location and service locations
        final String pid = config.getPid();
        String serviceLocation = ""; //$NON-NLS-1$
        try {
            final ServiceReference<?>[] refs = this.servletSupport.getBundleContext().getServiceReferences(
                    (String)null,
                    "(&(" + Constants.OBJECTCLASS + '=' + ManagedService.class.getName() //$NON-NLS-1$
                    + ")(" + Constants.SERVICE_PID + '=' + pid + "))"); //$NON-NLS-1$ //$NON-NLS-2$
            if ( refs != null && refs.length > 0 ) {
                serviceLocation = refs[0].getBundle().getLocation();
            }
        } catch (final Throwable t) {
            this.servletSupport.log( "Error getting service associated with configuration " + pid, t );
        }
        json.key( "bundle_location" ); //$NON-NLS-1$
        json.value ( bundleLocation );
        json.key( "service_location" ); //$NON-NLS-1$
        json.value ( serviceLocation );
    }

    private final Bundle getBoundBundle(final Configuration config) {
        if (null == config) {
            return null;
        }
        final String location = config.getBundleLocation();
        if (null == location) {
            return null;
        }

        final Bundle bundles[] = this.servletSupport.getBundleContext().getBundles();
        for (int i = 0; bundles != null && i < bundles.length; i++) {
            if (bundles[i].getLocation().equals(location)) {
                return bundles[i];
            }
        }
        return null;
    }

    final boolean listConfigurations( final JSONWriter jw, final String pidFilter, final String locale, final Locale loc ) {
        return listConfigurations( jw, pidFilter, locale, loc, null );
    }

    final boolean listConfigurations(final JSONWriter jw, final String pidFilter, final String locale, final Locale loc , final ServiceTracker<ConfigurationHandler, ConfigurationHandler> serviceTracker ) {
        boolean hasConfigurations = false;
        try {
            // start with ManagedService instances
            Map<String, String> optionsPlain = getServices(ManagedService.class.getName(), pidFilter,
                    locale, true);

            // next are the MetaType informations without ManagedService
            if ( mtss != null ) {
                addMetaTypeNames( optionsPlain, mtss.getPidObjectClasses( locale ), pidFilter, Constants.SERVICE_PID );
            }

            // add in existing configuration (not duplicating ManagedServices)
            Configuration[] cfgs = this.configurationAdmin.listConfigurations(pidFilter);
            for (int i = 0; cfgs != null && i < cfgs.length; i++)
            {
                // ignore configuration object if an entry already exists in the map
                // or if it is invalid
                final String pid = cfgs[i].getPid();
                if (optionsPlain.containsKey(pid) || !ConfigurationUtil.isAllowedPid(pid) )
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

            jw.key("pids");//$NON-NLS-1$
            jw.array();
            for ( Iterator<String> ii = optionsPlain.keySet().iterator(); ii.hasNext(); )
            {
                hasConfigurations = true;
                String id = ii.next();
                Object name = optionsPlain.get( id );

                final Configuration c = ConfigurationUtil.findConfiguration( this.configurationAdmin, id );
                Configuration config = c;
                if (serviceTracker != null) {
                    Object[] services = serviceTracker.getServices();
                    if (services != null) {
                        for(final Object o : services) {
                            ConfigurationHandler handler = (ConfigurationHandler)o;
                            if (!handler.listConfiguration(config)) {
                                config = null;
                                break;
                            }
                        }
                    }
                }
                if ( null != config )
                {
                    jw.object();
                    jw.key("id").value( id ); //$NON-NLS-1$
                    jw.key( "name").value( name ); //$NON-NLS-1$

                    // FELIX-3848
                    jw.key("has_config").value( true ); //$NON-NLS-1$

                    final String fpid = config.getFactoryPid();
                    if ( null != fpid )
                    {
                        jw.key("fpid").value( fpid ); //$NON-NLS-1$
                        final String val = getConfigurationFactoryNameHint(config);
                        if ( val != null )
                        {
                            jw.key( "nameHint").value(val ); //$NON-NLS-1$
                        }
                    }

                    final Bundle bundle = getBoundBundle( config );
                    if ( null != bundle ) {
                        jw.key( "bundle").value( bundle.getBundleId() ); //$NON-NLS-1$
                        jw.key( "bundle_name").value( Util.getName( bundle, loc ) ); //$NON-NLS-1$
                    }
                    jw.endObject();
                }
            }
            jw.endArray();
        } catch (final Exception e) {
            this.servletSupport.log("listConfigurations: Unexpected problem encountered", e);
        }
        return hasConfigurations;
    }

    /**
     * Builds a "name hint" for factory configuration based on other property
     * values of the config and a "name hint template" defined as hidden
     * property in the service.
     * @param config The factory configuration.
     * @return Name hint or null if none is defined.
     */
    private final String getConfigurationFactoryNameHint(Configuration config) {
        Map<String, MetatypePropertyDescriptor> adMap = (mtss != null) ? mtss.getAttributeDefinitionMap(config, null) : null;
        if (null == adMap) {
            return null;
        }

        final Dictionary<String, Object> props = config.getProperties();
        String nameHint = null;
        // check for configured name hint template
        ServiceReference<?>[] refs;
        String filter = "(service.pid=" + config.getPid() + ")";
        try {
            refs = servletSupport.getBundleContext().getAllServiceReferences(null, filter);
        } catch (InvalidSyntaxException e) {
            throw new IllegalStateException("Invalid filter: " + filter);
        }
        // first try via service reference properties
        if (refs != null) {
            nameHint = getPropertyValueAsString(refs[0].getProperty(ConfigAdminSupport.PROPERTY_FACTORYCONFIG_NAMEHINT));
        } 
        // as fallback use the configuration admin properties
        if (nameHint == null) {
            nameHint = getConfigurationPropertyValueOrDefault(ConfigAdminSupport.PROPERTY_FACTORYCONFIG_NAMEHINT, props, adMap);
        }
        if (nameHint == null) {
            return null;
        }

        // search for all variable patterns in name hint and replace them with configured/default values
        Matcher matcher = ConfigAdminSupport.NAMEHINT_PLACEHOLER_REGEXP.matcher(nameHint);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String propertyName = matcher.group(1);
            String value = getConfigurationPropertyValueOrDefault(propertyName, props, adMap);
            if (value == null) {
                value = "";
            }
            matcher.appendReplacement(sb, matcherQuoteReplacement(value));
        }
        matcher.appendTail(sb);

        // make sure name hint does not only contain whitespaces
        nameHint = sb.toString().trim();
        if (nameHint.length() == 0) {
            return null;
        } else {
            return nameHint;
        }
    }

    /**
     * Gets configured service property value, or default value if no value is configured.
     * @param propertyName Property name
     * @param props Service configuration properties map
     * @param adMap Attribute definitions map
     * @return Value or null if none found
     */
    private static String getConfigurationPropertyValueOrDefault(String propertyName, Dictionary<String, Object> props, Map<String, MetatypePropertyDescriptor> adMap) {
        // get configured property value
        Object value = props.get(propertyName);

        if (value != null) {
            return getPropertyValueAsString(value);
        } else {
            // if not set try to get default value
            PropertyDescriptor ad = adMap.get(propertyName);
            if (ad != null && ad.getDefaultValue() != null && ad.getDefaultValue().length == 1) {
                return ad.getDefaultValue()[0];
            }
        }

        return null;
    }

    private static String getPropertyValueAsString(Object value) {
        if (value == null) {
            return null;
        }
        // convert array to string
        if (value.getClass().isArray()) {
            StringBuffer valueString = new StringBuffer();
            for (int i = 0; i < Array.getLength(value); i++) {
                if (i > 0) {
                    valueString.append(", ");
                }
                valueString.append(Array.get(value, i));
            }
            return valueString.toString();
        } else {
            return value.toString();
        }
    }

    /**
     * Replacement for Matcher.quoteReplacement which is only available in JDK 1.5 and up.
     * @param str Unquoted string
     * @return Quoted string
     */
    private static String matcherQuoteReplacement(String str) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '$' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    final void listFactoryConfigurations(JSONWriter jw, String pidFilter,
            String locale) {
        try {
            final Map<String, String> optionsFactory = getServices(ManagedServiceFactory.class.getName(),
                    pidFilter, locale, true);
            if ( mtss != null ) {
                addMetaTypeNames( optionsFactory, mtss.getFactoryPidObjectClasses( locale ), pidFilter,
                        ConfigurationAdmin.SERVICE_FACTORYPID );
            }
            jw.key("fpids");
            jw.array();
            for ( Iterator<String> ii = optionsFactory.keySet().iterator(); ii.hasNext(); ) {
                String id = ii.next();
                Object name = optionsFactory.get( id );
                jw.object();
                jw.key("id").value(id ); //$NON-NLS-1$
                jw.key("name").value( name ); //$NON-NLS-1$
                jw.endObject();
            }
            jw.endArray();
        } catch (final Exception e) {
            this.servletSupport.log("listFactoryConfigurations: Unexpected problem encountered", e);
        }
    }

    SortedMap<String, String> getServices( String serviceClass, String serviceFilter, String locale,
            boolean ocdRequired ) throws InvalidSyntaxException {
        // sorted map of options
        SortedMap<String, String> optionsFactory = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );

        // find all ManagedServiceFactories to get the factoryPIDs
        ServiceReference<?>[] refs = this.servletSupport.getBundleContext().getServiceReferences( serviceClass, serviceFilter );
        for ( int i = 0; refs != null && i < refs.length; i++ ) {
            Object pidObject = refs[i].getProperty( Constants.SERVICE_PID );
            // only include valid PIDs
            if ( pidObject instanceof String && ConfigurationUtil.isAllowedPid((String)pidObject) ) {
                String pid = ( String ) pidObject;
                String name = pid;
                boolean haveOcd = !ocdRequired;
                if ( mtss != null ) {
                    final ObjectClassDefinition ocd = mtss.getObjectClassDefinition( refs[i].getBundle(), pid, locale );
                    if ( ocd != null ) {
                        name = ocd.getName();
                        haveOcd = true;
                    }
                }

                if ( haveOcd ) {
                    optionsFactory.put( pid, name );
                }
            }
        }

        return optionsFactory;
    }

    private void addMetaTypeNames( final Map<String, String> pidMap, final Map<String, ObjectClassDefinition> ocdCollection, final String filterSpec, final String type ) {
        Filter filter = null;
        if ( filterSpec != null ) {
            try {
                filter = this.servletSupport.getBundleContext().createFilter( filterSpec );
            } catch ( InvalidSyntaxException not_expected ){
                // filter is correct 
            }
        }

        for ( Iterator<Map.Entry<String, ObjectClassDefinition>> ei = ocdCollection.entrySet().iterator(); ei.hasNext(); ) {
            Entry<String, ObjectClassDefinition> ociEntry = ei.next();
            final String pid = ociEntry.getKey();
            final ObjectClassDefinition ocd = ociEntry.getValue();
            if ( filter == null ) {
                pidMap.put( pid, ocd.getName() );
            } else {
                final Dictionary<String, Object> props = new Hashtable<>();
                props.put( type, pid );
                if ( filter.match( props ) ) {
                    pidMap.put( pid, ocd.getName() );
                }
            }
        }
    }
}

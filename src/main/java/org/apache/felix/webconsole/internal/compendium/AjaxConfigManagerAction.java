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
package org.apache.felix.webconsole.internal.compendium;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.webconsole.Action;
import org.apache.felix.webconsole.internal.Util;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.ObjectClassDefinition;


/**
 * The <code>AjaxConfigManagerAction</code> TODO
 */
public class AjaxConfigManagerAction extends ConfigManagerBase implements Action
{

    public static final String NAME = "ajaxConfigManager";


    public String getName()
    {
        return NAME;
    }


    public String getLabel()
    {
        return NAME;
    }


    public boolean performAction( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {

        // needed multiple times below
        String pid = request.getParameter( ConfigManager.PID );

        // should actually apply the configuration before redirecting
        if ( request.getParameter( "create" ) != null && pid != null )
        {
            ConfigurationAdmin ca = this.getConfigurationAdmin();
            if ( ca != null )
            {
                Configuration config = ca.createFactoryConfiguration( pid, null );
                pid = config.getPid();
            }
        }
        else if ( request.getParameter( "apply" ) != null )
        {
            return applyConfiguration( request );
        }

        boolean isFactory = pid == null;
        if ( isFactory )
        {
            pid = request.getParameter( "factoryPid" );
        }

        // send the result
        response.setContentType( "text/javascript" );
        response.setCharacterEncoding( "UTF-8" );

        JSONWriter result = new JSONWriter( response.getWriter() );

        if ( pid != null )
        {
            try
            {
                result.object();
                this.configForm( result, pid, isFactory, getLocale( request ) );
                result.endObject();
            }
            catch ( Exception e )
            {
                // add message
            }
        }

        return false;
    }


    private void configForm( JSONWriter json, String pid, boolean isFactory, Locale loc ) throws IOException,
        JSONException
    {
        String locale = ( loc == null ) ? null : loc.toString();

        ConfigurationAdmin ca = this.getConfigurationAdmin();
        if ( ca == null )
        {
            // should print message
            return;
        }

        Configuration config = null;
        try
        {
            Configuration[] configs = ca.listConfigurations( "(" + Constants.SERVICE_PID + "=" + pid + ")" );
            if ( configs != null && configs.length > 0 )
            {
                config = configs[0];
            }
        }
        catch ( InvalidSyntaxException ise )
        {
            // should print message
            return;
        }

        json.key( ConfigManager.PID );
        json.value( pid );
        json.key( "isFactory" );
        json.value( isFactory );

        Dictionary props = null;
        ObjectClassDefinition ocd;
        if ( config != null )
        {
            props = config.getProperties();
            ocd = this.getObjectClassDefinition( config, locale );
        }
        else
        {
            ocd = this.getObjectClassDefinition( pid, locale );
        }

        props = this.mergeWithMetaType( props, ocd, json );

        if ( props != null )
        {

            json.key( "title" );
            json.value( pid );
            json.key( "description" );
            json
                .value( "Please enter configuration properties for this configuration in the field below. This configuration has no associated description" );

            json.key( "propertylist" );
            json.value( "properties" );

            json.key( "properties" );
            json.object();
            for ( Enumeration pe = props.keys(); pe.hasMoreElements(); )
            {
                Object key = pe.nextElement();

                // ignore well known special properties
                if ( !key.equals( Constants.SERVICE_PID ) && !key.equals( Constants.SERVICE_DESCRIPTION )
                    && !key.equals( Constants.SERVICE_ID ) && !key.equals( Constants.SERVICE_RANKING )
                    && !key.equals( Constants.SERVICE_VENDOR )
                    && !key.equals( ConfigurationAdmin.SERVICE_BUNDLELOCATION )
                    && !key.equals( ConfigurationAdmin.SERVICE_FACTORYPID ) )
                {
                    json.key( String.valueOf( key ) );
                    json.value( props.get( key ) );
                }
            }
            json.endObject();

        }

        if ( config != null )
        {
            this.addConfigurationInfo( config, json, locale );
        }
    }


    private Dictionary mergeWithMetaType( Dictionary props, ObjectClassDefinition ocd, JSONWriter json )
        throws JSONException
    {

        if ( props == null )
        {
            props = new Hashtable();
        }

        if ( ocd != null )
        {

            json.key( "title" );
            json.value( ocd.getName() );

            if ( ocd.getDescription() != null )
            {
                json.key( "description" );
                json.value( ocd.getDescription() );
            }

            AttributeDefinition[] ad = ocd.getAttributeDefinitions( ObjectClassDefinition.ALL );
            if ( ad != null )
            {

                JSONArray propertyList = new JSONArray();

                for ( int i = 0; i < ad.length; i++ )
                {
                    json.key( ad[i].getID() );
                    json.object();

                    Object value = props.get( ad[i].getID() );
                    if ( value == null )
                    {
                        value = ad[i].getDefaultValue();
                        if ( value == null )
                        {
                            if ( ad[i].getCardinality() == 0 )
                            {
                                value = "";
                            }
                            else
                            {
                                value = new String[0];
                            }
                        }
                    }

                    json.key( "name" );
                    json.value( ad[i].getName() );

                    json.key( "type" );
                    if ( ad[i].getOptionLabels() != null && ad[i].getOptionLabels().length > 0 )
                    {
                        json.object();
                        json.key( "labels" );
                        json.value( Arrays.asList( ad[i].getOptionLabels() ) );
                        json.key( "values" );
                        json.value( Arrays.asList( ad[i].getOptionValues() ) );
                        json.endObject();
                    }
                    else
                    {
                        json.value( ad[i].getType() );
                    }

                    if ( ad[i].getCardinality() == 0 )
                    {
                        // scalar
                        if ( value instanceof Vector )
                        {
                            value = ( ( Vector ) value ).get( 0 );
                        }
                        else if ( value.getClass().isArray() )
                        {
                            value = Array.get( value, 0 );
                        }
                        json.key( "value" );
                        json.value( value );
                    }
                    else
                    {
                        if ( value instanceof Vector )
                        {
                            value = new JSONArray( ( Vector ) value );
                        }
                        else if ( value.getClass().isArray() )
                        {
                            value = new JSONArray( Arrays.asList( ( Object[] ) value ) );
                        }
                        else
                        {
                            JSONArray tmp = new JSONArray();
                            tmp.put( value );
                            value = tmp;
                        }
                        json.key( "values" );
                        json.value( value );
                    }

                    if ( ad[i].getDescription() != null )
                    {
                        json.key( "description" );
                        json.value( ad[i].getDescription() );
                    }

                    json.endObject();
                    propertyList.put( ad[i].getID() );
                }

                json.key( "propertylist" );
                json.value( propertyList );
            }

            // nothing more to display
            props = null;
        }

        return props;
    }


    private void addConfigurationInfo( Configuration config, JSONWriter json, String locale ) throws JSONException
    {

        if ( config.getFactoryPid() != null )
        {
            json.key( "factoryPID" );
            json.value( config.getFactoryPid() );
        }

        String location;
        if ( config.getBundleLocation() == null )
        {
            location = "None";
        }
        else
        {
            Bundle bundle = this.getBundle( config.getBundleLocation() );

            Dictionary headers = bundle.getHeaders( locale );
            String name = ( String ) headers.get( Constants.BUNDLE_NAME );
            if ( name == null )
            {
                location = bundle.getSymbolicName();
            }
            else
            {
                location = name + " (" + bundle.getSymbolicName() + ")";
            }

            Version v = Version.parseVersion( ( String ) headers.get( Constants.BUNDLE_VERSION ) );
            location += ", Version " + v.toString();
        }
        json.key( "bundleLocation" );
        json.value( location );
    }


    private boolean applyConfiguration( HttpServletRequest request ) throws IOException
    {

        ConfigurationAdmin ca = this.getConfigurationAdmin();
        if ( ca == null )
        {
            return false;
        }

        String pid = request.getParameter( "pid" );

        if ( request.getParameter( "delete" ) != null )
        {
            // TODO: should log this here !!
            Configuration config = ca.getConfiguration( pid, null );
            config.delete();
            return true;
        }
        else if ( request.getParameter( "create" ) != null )
        {
            // pid is a factory PID and we have to create a new configuration
            // we should actually also display that one !
            Configuration config = ca.createFactoryConfiguration( pid, null );

            // request.setAttribute(ATTR_REDIRECT_PARAMETERS, "pid=" +
            // config.getPid());
            return false;
        }

        String propertyList = request.getParameter( "propertylist" );
        if ( propertyList == null )
        {
            String propertiesString = request.getParameter( "properties" );

            if ( propertiesString != null )
            {
                byte[] propBytes = propertiesString.getBytes( "ISO-8859-1" );
                ByteArrayInputStream bin = new ByteArrayInputStream( propBytes );
                Properties props = new Properties();
                props.load( bin );

                Configuration config = ca.getConfiguration( pid, null );
                config.update( props );
            }
        }
        else
        {
            Configuration config = ca.getConfiguration( pid, null );
            Dictionary props = config.getProperties();
            if ( props == null )
            {
                props = new Hashtable();
            }

            Map adMap = ( Map ) this.getAttributeDefinitionMap( config, null );
            if ( adMap != null )
            {
                StringTokenizer propTokens = new StringTokenizer( propertyList, "," );
                while ( propTokens.hasMoreTokens() )
                {
                    String propName = propTokens.nextToken();
                    AttributeDefinition ad = ( AttributeDefinition ) adMap.get( propName );
                    if ( ad == null || ( ad.getCardinality() == 0 && ad.getType() == AttributeDefinition.STRING ) )
                    {
                        String prop = request.getParameter( propName );
                        if ( prop != null )
                        {
                            props.put( propName, prop );
                        }
                    }
                    else if ( ad.getCardinality() == 0 )
                    {
                        // scalar of non-string
                        String prop = request.getParameter( propName );
                        props.put( propName, this.toType( ad.getType(), prop ) );
                    }
                    else
                    {
                        // array or vector of any type
                        Vector vec = new Vector();

                        String[] properties = request.getParameterValues( propName );
                        if ( properties != null )
                        {
                            for ( int i = 0; i < properties.length; i++ )
                            {
                                vec.add( this.toType( ad.getType(), properties[i] ) );
                            }
                        }

                        // but ensure size
                        int maxSize = Math.abs( ad.getCardinality() );
                        if ( vec.size() > maxSize )
                        {
                            vec.setSize( maxSize );
                        }

                        if ( ad.getCardinality() < 0 )
                        {
                            // keep the vector
                            props.put( propName, vec );
                        }
                        else
                        {
                            // convert to an array
                            props.put( propName, this.toArray( ad.getType(), vec ) );
                        }
                    }
                }
            }

            config.update( props );
        }

        // request.setAttribute(ATTR_REDIRECT_PARAMETERS, "pid=" + pid);
        return true;
    }


    private Object toType( int type, String value )
    {
        switch ( type )
        {
            case AttributeDefinition.BOOLEAN:
                return Boolean.valueOf( value );
            case AttributeDefinition.BYTE:
                return Byte.valueOf( value );
            case AttributeDefinition.CHARACTER:
                char c = ( value.length() > 0 ) ? value.charAt( 0 ) : 0;
                return new Character( c );
            case AttributeDefinition.DOUBLE:
                return Double.valueOf( value );
            case AttributeDefinition.FLOAT:
                return Float.valueOf( value );
            case AttributeDefinition.LONG:
                return Long.valueOf( value );
            case AttributeDefinition.INTEGER:
                return Integer.valueOf( value );
            case AttributeDefinition.SHORT:
                return Short.valueOf( value );

            default:
                // includes AttributeDefinition.STRING
                return value;
        }
    }


    private Object toArray( int type, Vector values )
    {
        int size = values.size();

        // short cut for string array
        if ( type == AttributeDefinition.STRING )
        {
            return values.toArray( new String[size] );
        }

        Object array;
        switch ( type )
        {
            case AttributeDefinition.BOOLEAN:
                array = new boolean[size];
            case AttributeDefinition.BYTE:
                array = new byte[size];
            case AttributeDefinition.CHARACTER:
                array = new char[size];
            case AttributeDefinition.DOUBLE:
                array = new double[size];
            case AttributeDefinition.FLOAT:
                array = new float[size];
            case AttributeDefinition.LONG:
                array = new long[size];
            case AttributeDefinition.INTEGER:
                array = new int[size];
            case AttributeDefinition.SHORT:
                array = new short[size];
            default:
                // unexpected, but assume string
                array = new String[size];
        }

        for ( int i = 0; i < size; i++ )
        {
            Array.set( array, i, values.get( i ) );
        }

        return array;
    }
}

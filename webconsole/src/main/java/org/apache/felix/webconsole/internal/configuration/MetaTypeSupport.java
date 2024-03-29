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


import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.webconsole.internal.servlet.OsgiManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.metatype.AttributeDefinition;


/**
 * It provides various helper methods mostly with respect to using the MetaTypeService to access
 * configuration descriptions.
 */
class MetaTypeSupport
{
    /**
     * Marker value of password fields used as dummy values and
     * indicating unmodified values.
     */
    static final String PASSWORD_PLACEHOLDER_VALUE = "unmodified";

    static Bundle getBundle( final BundleContext bundleContext, final String bundleLocation ) {
        if ( bundleLocation == null ) {
            return null;
        }

        for ( final Bundle bundle : bundleContext.getBundles() ) {
            if ( bundleLocation.equals( bundle.getLocation() ) ) {
                return bundle;
            }
        }

        return null;
    }


    @SuppressWarnings("rawtypes")
    static void attributeToJson( final JSONWriter json, final PropertyDescriptor ad, final Object propValue )
            throws IOException
    {
        json.object();

        Object value;
        if ( propValue != null )
        {
            value = propValue;
        }
        else if ( ad.getDefaultValue() != null )
        {
            value = ad.getDefaultValue();
        }
        else if ( ad.getCardinality() == 0 )
        {
            value = "";
        }
        else
        {
            value = new String[0];
        }

        json.key( "name" );
        json.value( ad.getName() );
        json.key( "optional" );
        json.value( ad.isOptional() );
        json.key( "is_set" );
        json.value( propValue != null );

        // attribute type - overwrite metatype provided type
        // if the property name contains "password" and the
        // type is string
        int propertyType = getAttributeType( ad );

        json.key( "type" );
        if ( ad.getOptionLabels() != null && ad.getOptionLabels().length > 0 )
        {
            json.object();
            json.key( "labels" );
            json.value( Arrays.asList( ad.getOptionLabels() ) );
            json.key( "values" );
            json.value( Arrays.asList( ad.getOptionValues() ) );
            json.endObject();
        }
        else
        {
            json.value( propertyType );
        }

        // unless the property is of password type, send it
        final boolean isPassword = propertyType == AttributeDefinition.PASSWORD;
        if ( ad.getCardinality() == 0 )
        {
            // scalar
            if ( isPassword )
            {
                value = PASSWORD_PLACEHOLDER_VALUE;
            }
            else if ( value instanceof Vector )
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
            json.key( "values" );
            json.array();
            final List list = toList( value );
            final Iterator iter = list.iterator();
            while ( iter.hasNext() )
            {
                final Object val = iter.next();
                if ( isPassword )
                {
                    json.value(PASSWORD_PLACEHOLDER_VALUE);
                }
                else
                {
                    json.value(val);
                }
            }
            json.endArray();
        }

        if ( ad.getDescription() != null )
        {
            json.key( "description" );
            json.value( ad.getDescription() + " (" + ad.getID() + ")" );
        }

        json.endObject();
    }


    @SuppressWarnings("rawtypes")
    private static List toList( Object value )
    {
        if ( value instanceof Vector )
        {
            return ( Vector ) value;
        }
        else if ( value.getClass().isArray() )
        {
            if ( value.getClass().getComponentType().isPrimitive() )
            {
                final int len = Array.getLength( value );
                final Object[] tmp = new Object[len];
                for ( int j = 0; j < len; j++ )
                {
                    tmp[j] = Array.get( value, j );
                }
                value = tmp;
            }
            return Arrays.asList( ( Object[] ) value );
        }
        else
        {
            return Collections.singletonList( value );
        }
    }


    @SuppressWarnings("rawtypes")
    static PropertyDescriptor createAttributeDefinition( final String id, final Object value )
    {
        int attrType;
        int attrCardinality;
        Class<?> type;

        if ( value == null )
        {
            attrCardinality = 0;
            type = String.class;
        }
        else if ( value instanceof Collection )
        {
            attrCardinality = Integer.MIN_VALUE;
            Collection coll = ( Collection ) value;
            if ( coll.isEmpty() )
            {
                type = String.class;
            }
            else
            {
                type = coll.iterator().next().getClass();
            }
        }
        else if ( value.getClass().isArray() )
        {
            attrCardinality = Integer.MAX_VALUE;
            type = value.getClass().getComponentType();
        }
        else
        {
            attrCardinality = 0;
            type = value.getClass();
        }

        if ( type == Boolean.class || type == Boolean.TYPE )
        {
            attrType = AttributeDefinition.BOOLEAN;
        }
        else if ( type == Byte.class || type == Byte.TYPE )
        {
            attrType = AttributeDefinition.BYTE;
        }
        else if ( type == Character.class || type == Character.TYPE )
        {
            attrType = AttributeDefinition.CHARACTER;
        }
        else if ( type == Double.class || type == Double.TYPE )
        {
            attrType = AttributeDefinition.DOUBLE;
        }
        else if ( type == Float.class || type == Float.TYPE )
        {
            attrType = AttributeDefinition.FLOAT;
        }
        else if ( type == Long.class || type == Long.TYPE )
        {
            attrType = AttributeDefinition.LONG;
        }
        else if ( type == Integer.class || type == Integer.TYPE )
        {
            attrType = AttributeDefinition.INTEGER;
        }
        else if ( type == Short.class || type == Short.TYPE )
        {
            attrType = AttributeDefinition.SHORT;
        }
        else
        {
            attrType = AttributeDefinition.STRING;
        }

        return new PropertyDescriptor( id, attrType, attrCardinality );
    }


    public static boolean isPasswordProperty( final String name )
    {
        if ( name == null || !OsgiManager.ENABLE_SECRET_HEURISTICS )
        {
            return false;
        }
        return name.toLowerCase().indexOf( "password" ) != -1;
    }

    static int getAttributeType( final PropertyDescriptor ad )
    {
        if ( ad.getType() == AttributeDefinition.STRING && isPasswordProperty( ad.getID() ) )
        {
            return AttributeDefinition.PASSWORD;
        }
        return ad.getType();
    }


    /**
     * @throws NumberFormatException If the value cannot be converted to
     *      a number and type indicates a numeric type
     */
    static final Object toType( int type, String value )
    {
        switch ( type )
        {
        case AttributeDefinition.BOOLEAN:
            return Boolean.valueOf( value );
        case AttributeDefinition.BYTE:
            return Byte.valueOf( value );
        case AttributeDefinition.CHARACTER:
            char c = ( value.length() > 0 ) ? value.charAt( 0 ) : 0;
            return Character.valueOf( c );
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
            // includes AttributeDefinition.PASSWORD
            return value;
        }
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    static void setPasswordProps( final Vector vec, final String[] properties, Object props )
    {
        List propList = ( props == null ) ? new ArrayList() : toList( props );
        for ( int i = 0; i < properties.length; i++ )
        {
            if ( PASSWORD_PLACEHOLDER_VALUE.equals( properties[i] ) )
            {
                if ( i < propList.size() && propList.get( i ) != null )
                {
                    vec.add( propList.get( i ) );
                }
            }
            else
            {
                vec.add( properties[i] );
            }
        }
    }


    static final Object toArray( int type, Vector<Object> values )
    {
        int size = values.size();

        // short cut for string array
        if ( type == AttributeDefinition.STRING || type == AttributeDefinition.PASSWORD )
        {
            return values.toArray( new String[size] );
        }

        Object array;
        switch ( type )
        {
        case AttributeDefinition.BOOLEAN:
            array = new boolean[size];
            break;
        case AttributeDefinition.BYTE:
            array = new byte[size];
            break;
        case AttributeDefinition.CHARACTER:
            array = new char[size];
            break;
        case AttributeDefinition.DOUBLE:
            array = new double[size];
            break;
        case AttributeDefinition.FLOAT:
            array = new float[size];
            break;
        case AttributeDefinition.LONG:
            array = new long[size];
            break;
        case AttributeDefinition.INTEGER:
            array = new int[size];
            break;
        case AttributeDefinition.SHORT:
            array = new short[size];
            break;
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

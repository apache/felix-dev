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
package org.apache.felix.webconsole.internal.core;


import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.felix.webconsole.internal.AbstractConfigurationPrinter;
import org.apache.felix.webconsole.internal.misc.ConfigurationRender;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.dto.FrameworkDTO;


public class FrameworkPropertiesPrinter extends AbstractConfigurationPrinter
{

    private static final String TITLE = "Framework Properties";

    public String getTitle()
    {
        return TITLE;
    }

    public void printConfiguration( PrintWriter printWriter )
    {
        getFrameworkProperties().entrySet().stream().forEach( e -> ConfigurationRender.infoLine( printWriter, null, e.getKey(), e.getValue() ) );
    }

    private Map<String,String> getFrameworkProperties() 
    {
        Bundle systemBundle = getBundleContext().getBundle( Constants.SYSTEM_BUNDLE_ID );
        FrameworkDTO framework = systemBundle.adapt( FrameworkDTO.class );
        // sorted map of https://docs.osgi.org/javadoc/osgi.core/8.0.0/org/osgi/framework/dto/FrameworkDTO.html#properties
        return framework.properties.entrySet().stream()
            .collect( Collectors.toMap( 
                Entry::getKey, 
                e -> FrameworkPropertiesPrinter.getStringValue( e.getValue() ),
                ( key1, key2 ) -> key1,
                TreeMap::new ) );
    }

    private static final String getStringValue( Object object ) {
        // numerical type, Boolean, String, DTO or an array of any of the former
        if ( object.getClass().isArray() ) {
            StringBuilder values = new StringBuilder( "[" );
            int length = Array.getLength( object );
            for ( int i = 0; i < length; i ++ ) {
                if ( i > 0 ) {
                    values.append( " ," );
                }
                values.append( getStringValue( Array.get( object, i ) ) );
            }
            values.append( "]" );
            return values.toString();
        } 
        else if ( object instanceof String )
        {
            return object.toString();
        } 
        else
        {
            return  object.toString() + " (" + object.getClass() + ")";
        }
    }
}
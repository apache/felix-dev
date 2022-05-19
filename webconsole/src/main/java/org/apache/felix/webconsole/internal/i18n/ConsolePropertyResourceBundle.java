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
package org.apache.felix.webconsole.internal.i18n;


import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.ResourceBundle;


class ConsolePropertyResourceBundle extends ResourceBundle
{

    private final Properties props;


    ConsolePropertyResourceBundle( final ResourceBundle parent, final URL source )
    {
        setParent( parent );

        props = new Properties();
        if ( source != null ) {
            try(InputStream ins = source.openStream()) {
                props.load( ins );
            } catch ( IOException ignore ) { 
                // ignore
            }
        }
    }


    public Enumeration getKeys()
    {
        Enumeration keysEnum = null;
        if (parent == null) {
            keysEnum = props.keys();
        } else {
            keysEnum = new CombinedEnumeration( props.keys(), parent.getKeys() );
        }
        return keysEnum;
    }


    protected Object handleGetObject( String key )
    {
        return props.get( key );
    }
}

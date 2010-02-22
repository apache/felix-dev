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
package org.apache.felix.webconsole.internal;


import org.apache.felix.webconsole.internal.servlet.OsgiManager;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;


/**
 * This is the main, starting class of the Bundle. It initializes and disposes 
 * the Apache Web Console upon bundle lifecycle requests.
 */
public class OsgiManagerActivator implements BundleActivator
{

    private OsgiManager osgiManager;


    /**
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    public void start( BundleContext bundleContext )
    {
        osgiManager = new OsgiManager( bundleContext );
    }


    /**
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public void stop( BundleContext arg0 )
    {
        if ( osgiManager != null )
        {
            osgiManager.dispose();
        }
    }

}

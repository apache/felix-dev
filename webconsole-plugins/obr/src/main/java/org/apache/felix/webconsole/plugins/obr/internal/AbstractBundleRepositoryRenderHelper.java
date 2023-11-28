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
package org.apache.felix.webconsole.plugins.obr.internal;


import java.io.IOException;

import jakarta.servlet.ServletException;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


abstract class AbstractBundleRepositoryRenderHelper
{

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ServiceTracker repositoryAdmin;


    protected AbstractBundleRepositoryRenderHelper( final BundleContext bundleContext, final String serviceName )
    {
        this.repositoryAdmin = new ServiceTracker( bundleContext, serviceName, null );
        this.repositoryAdmin.open();
    }


    void dispose()
    {
        repositoryAdmin.close();
    }


    boolean hasRepositoryAdmin()
    {
        return getRepositoryAdmin() != null;
    }


    protected final Object getRepositoryAdmin()
    {
        return repositoryAdmin.getService();
    }


    abstract void doDeploy( String[] bundles, boolean start, boolean optional );


    abstract void doAction( String action, String urlParam ) throws IOException, ServletException;


    abstract String getData( final String filter, final boolean details, final Bundle[] bundles );

}
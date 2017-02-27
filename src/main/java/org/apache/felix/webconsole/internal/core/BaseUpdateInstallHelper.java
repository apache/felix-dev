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


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.felix.webconsole.SimpleWebConsolePlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.startlevel.StartLevel;


abstract class BaseUpdateInstallHelper implements Runnable
{

    private final SimpleWebConsolePlugin plugin;

    private final File bundleFile;

    private final boolean refreshPackages;

    private Thread updateThread;


    BaseUpdateInstallHelper( SimpleWebConsolePlugin plugin, String name, File bundleFile, boolean refreshPackages )
    {
        this.plugin = plugin;
        this.bundleFile = bundleFile;
        this.refreshPackages = refreshPackages;
        this.updateThread = new Thread( this, name );
        this.updateThread.setDaemon( true );
    }


    protected File getBundleFile()
    {
        return bundleFile;
    }


    protected abstract Bundle doRun( InputStream bundleStream ) throws BundleException;


    protected final Object getService( String serviceName )
    {
        return plugin.getService( serviceName );
    }


    protected final SimpleWebConsolePlugin getLog()
    {
        return plugin;
    }
    
    protected Bundle getTargetBundle()
    {
        return null;
    }


    /**
     * @return the installed bundle or <code>null</code> if no bundle was touched
     * @throws BundleException
     * @throws IOException
     */
    protected Bundle doRun() throws Exception
    {
        // now deploy the resolved bundles
        InputStream bundleStream = null;
        try
        {
            bundleStream = new FileInputStream( bundleFile );
            return doRun( bundleStream );
        }
        finally
        {
            IOUtils.closeQuietly( bundleStream );
        }
    }


    final void start()
    {
        if ( updateThread != null )
        {
            updateThread.start();
        }
    }


    public final void run()
    {
        // now deploy the resolved bundles
        try
        {
            // we need the package admin before we call the bundle
            // installation or update, since we might be updating
            // our selves in which case the bundle context will be
            // invalid by the time we want to call the update
            PackageAdmin pa = ( refreshPackages ) ? ( PackageAdmin ) getService( PackageAdmin.class.getName() ) : null;

            // same for the startlevel
            StartLevel startLevel = null;
            
            Bundle bundle = getTargetBundle();
            
            int state = pa != null && bundle != null ? bundle.getState() : 0;
            int startFlags = 0;
            
            // If the bundle has been started we want to stop it first, then update it, refresh it, and restart it
            // because otherwise, it will be stopped and started twice (once by the update and once by the refresh)
            if ((state & (Bundle.ACTIVE | Bundle.STARTING)) != 0)
            {
                // we need the StartLevel service  before we stop the bundle
                // before the update, since we might be stopping
                // our selves in which case the bundle context will be
                // invalid by the time we want to call the startlevel
                startLevel = (StartLevel) getService(StartLevel.class.getName());
                
                // We want to start the bundle afterwards without affecting the persistent state of the bundle
                // However, we can only use the transient options if the framework startlevel is not less than the 
                // bundle startlevel (in case that there is no starlevel service we assume we are good).
                if (startLevel == null || startLevel.getStartLevel() >= startLevel.getBundleStartLevel(bundle))
                {
                    startFlags |= Bundle.START_TRANSIENT;
                }
                
                // If the bundle is in the starting state it might be lazy and not started yet - hence, start it 
                // according to its policy.
                if (state == Bundle.STARTING)
                {
                    startFlags |= Bundle.START_ACTIVATION_POLICY;
                }
                
                // We stop the bundle transiently - assuming we can also start it transiently later (see above) in which
                // case we didn't mess with its persistent state at all.
                bundle.stop(Bundle.STOP_TRANSIENT);
            }
            
            // We want to catch an exception during update to be able to restart the bundle if we stopped it previously
            Exception rethrow = null;
            try
            {
                // perform the action!
                bundle = doRun();
    
                
                if ( pa != null && bundle != null )
                {
                    // refresh packages and give it at most 5 seconds to finish
                    refreshPackages( pa, plugin.getBundle().getBundleContext(), 5000L, bundle );
                }
            }
            catch (Exception ex)
            {
                rethrow = ex;
                throw ex;
            }
            finally
            {
                // If we stopped the bundle lets try to restart it (we created the correct flags above already).
                if ((state & (Bundle.ACTIVE | Bundle.STARTING)) != 0)
                {
                    try
                    {
                        bundle.start(startFlags);
                    }
                    catch (Exception ex)
                    {
                        if (rethrow == null)
                        {
                            throw ex;
                        }
                        else
                        {
                            try
                            {
                                getLog().log( LogService.LOG_ERROR, "Cannot restart bundle: " + bundle + " after exception during update!", ex);
                            }
                            catch ( Exception secondary )
                            {
                                // at the time this exception happens the log used might have
                                // been destroyed and is not available to use any longer. So
                                // we only can write to stderr at this time to at least get
                                // some message out ...
                                System.err.println( "Cannot restart bundle: " + bundle + " after exception during update!");
                                ex.printStackTrace( System.err );
                            }
                        }
                    }
                }
            }
        }
        catch ( Exception e )
        {
            try
            {
                getLog().log( LogService.LOG_ERROR, "Cannot install or update bundle from " + bundleFile, e );
            }
            catch ( Exception secondary )
            {
                // at the time this exception happens the log used might have
                // been destroyed and is not available to use any longer. So
                // we only can write to stderr at this time to at least get
                // some message out ...
                System.err.println( "Cannot install or update bundle from " + bundleFile );
                e.printStackTrace( System.err );
            }
        }
        finally
        {
            if ( bundleFile != null )
            {
                bundleFile.delete();
            }

            // release update thread for GC
            updateThread = null;
        }
    }


    /**
     * This is an utility method that issues refresh package instruction to the framework.
     *
     * @param packageAdmin is the package admin service obtained using the bundle context of the caller.
     *   If this is <code>null</code> no refresh packages is performed
     * @param bundleContext of the caller. This is needed to add a framework listener.
     * @param maxWait the maximum time to wait for the packages to be refreshed
     * @param bundle the bundle, which packages to refresh or <code>null</code> to refresh all packages.
     * @return true if refresh is succesfull within the given time frame
     */
    static boolean refreshPackages(final PackageAdmin packageAdmin,
        final BundleContext bundleContext,
        final long maxWait,
        final Bundle bundle)
    {
        return new RefreshPackageTask().refreshPackages( packageAdmin, bundleContext, maxWait, bundle );
    }

    static class RefreshPackageTask implements FrameworkListener
    {

        private volatile boolean refreshed = false;
        private final Object lock = new Object();

        boolean refreshPackages( final PackageAdmin packageAdmin,
            final BundleContext bundleContext,
            final long maxWait,
            final Bundle bundle )
        {
            if (null == packageAdmin)
            {
                return false;
            }

            if (null == bundleContext)
            {
                return false;
            }

            bundleContext.addFrameworkListener(this);

            if (null == bundle)
            {
                packageAdmin.refreshPackages(null);
            }
            else
            {
                packageAdmin.refreshPackages(new Bundle[] { bundle });
            }

            try
            {
                // check for spurious wait
                long start = System.currentTimeMillis();
                long delay = maxWait;
                while (!refreshed && delay > 0)
                {
                    synchronized (lock)
                    {
                        if ( !refreshed )
                        {
                            lock.wait(delay);
                            // remove the time we already waited for
                            delay = maxWait - (System.currentTimeMillis() - start);
                        }
                    }
                }
            }
            catch (final InterruptedException e)
            {
                // just return if the thread is interrupted
                Thread.currentThread().interrupt();
            }
            finally
            {
                bundleContext.removeFrameworkListener(this);
            }
            return refreshed;
        }

        public void frameworkEvent(final FrameworkEvent e)
        {
            if (e.getType() == FrameworkEvent.PACKAGES_REFRESHED)
            {
                synchronized (lock)
                {
                    refreshed = true;
                    lock.notifyAll();
                }
            }

        }
    }
}
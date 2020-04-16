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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.felix.webconsole.SimpleWebConsolePlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.log.LogService;
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


    @Override
    public final void run()
    {
        // now deploy the resolved bundles
        try
        {
            // we need the framework wiring before we call the bundle
            // installation or update, since we might be updating
            // our selves in which case the bundle context will be
            // invalid by the time we want to call the update
            final FrameworkWiring fw = refreshPackages ? plugin.getBundle().getBundleContext().getBundle(Constants.SYSTEM_BUNDLE_LOCATION).adapt(FrameworkWiring.class) : null;

            // same for the startlevel
            StartLevel startLevel = null;

            Bundle bundle = getTargetBundle();

            int state = fw != null && bundle != null ? bundle.getState() : 0;
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


                if ( bundle != null )
                {
                    // refresh packages and give it at most 5 seconds to finish
                    refreshPackages(fw, plugin.getBundle().getBundleContext(), 5000L, bundle );
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
     * @param fw framework wiring (might be null)
     * @param bundleContext of the caller. This is needed to add a framework listener.
     * @param maxWait the maximum time to wait for the packages to be refreshed
     * @param bundle the bundle, which packages to refresh or <code>null</code> to refresh all packages.
     * @return true if refresh is succesfull within the given time frame
     */
    static boolean refreshPackages(final FrameworkWiring fw,
        final BundleContext bundleContext,
        final long maxWait,
        final Bundle bundle)
    {
        return new RefreshPackageTask().refreshPackages(fw, bundleContext, maxWait, bundle );
    }

    static class RefreshPackageTask implements FrameworkListener
    {

        private volatile boolean refreshed = false;
        private final Object lock = new Object();

        private Bundle searchHost(final BundleContext bundleContext, final Bundle bundle) {
            final String host = bundle.getHeaders().get( Constants.FRAGMENT_HOST );
            if ( host != null && host.indexOf(Constants.EXTENSION_DIRECTIVE) == -1 ) {
                for(final Bundle i : BundleContextUtil.getWorkingBundleContext(bundleContext).getBundles()) {
                    if ( host.equals(i.getSymbolicName()) ) {
                        return i;
                    }
                }
            }
            return null;
        }

        boolean refreshPackages(final FrameworkWiring fw,
                  final BundleContext bundleContext,
                  final long maxWait,
                  Bundle bundle )
        {
            List<Bundle> refreshFragments = null;
            if (null == bundleContext || fw == null )
            {
                return false;
            }

            if (null == bundle)
            {
                // search fragment hosts
                refreshFragments = new ArrayList<>();
                for(final Bundle i : BundleContextUtil.getWorkingBundleContext(bundleContext).getBundles()) {
                    if ( i.getState() != Bundle.RESOLVED ) {
                        final Bundle hostBundle = searchHost(bundleContext, i);
                        if ( hostBundle != null ) {
                            refreshFragments.add(hostBundle);
                        }
                    }
                }
                if ( refreshFragments.isEmpty() ) {
                    refreshFragments = null;
                }
                fw.refreshBundles(null, this);
            }
            else
            {
                // For a fragment, refresh the host instead
                final Bundle hostBundle = searchHost(bundleContext, bundle);
                if ( hostBundle != null ) {
                    bundle = hostBundle;
                }

                fw.refreshBundles(Collections.singleton(bundle), this);
            }

            waitForRefresh(maxWait);

            if ( refreshFragments != null ) {
                // check for uninstalled
                final Iterator<Bundle> iter = refreshFragments.iterator();
                while ( iter.hasNext() ) {
                    if ( iter.next().getState() == Bundle.UNINSTALLED ) {
                        iter.remove();
                    }
                }
                if ( !refreshFragments.isEmpty() ) {
                    refreshed = false;
                    fw.refreshBundles(refreshFragments, this);
                    waitForRefresh(maxWait);
                }
            }
            return refreshed;
        }

        private void waitForRefresh(long maxWait) {
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
        }

        @Override
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
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
package org.apache.felix.framework;

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.service.startlevel.StartLevel;

class FrameworkStartLevelImpl implements FrameworkStartLevel, Runnable
{
    static final String THREAD_NAME = "FelixStartLevel";

    private final Felix m_felix;
    private final ServiceRegistry m_registry;
    private final List<StartLevelRequest> m_requests = new ArrayList<>();
    private ServiceRegistration<StartLevel> m_slReg;
    private Thread m_thread = null;

    FrameworkStartLevelImpl(Felix felix, ServiceRegistry registry)
    {
        m_felix = felix;
        m_registry = registry;
    }

    @SuppressWarnings("unchecked")
    void start()
    {
        m_slReg = (ServiceRegistration<StartLevel>) m_registry.registerService(m_felix,
                new String[] { StartLevel.class.getName() },
                new StartLevelImpl(m_felix),
                null);
    }

    // Should only be called hold requestList lock.
    private void startThread()
    {
        // Start a thread to perform asynchronous package refreshes.
        if (m_thread == null)
        {
            m_thread = new Thread(this, THREAD_NAME);
            m_thread.setDaemon(true);
            m_thread.start();
        }
    }

    /**
     * Stops the FelixStartLevel thread on system shutdown. Shutting down the
     * thread explicitly is required in the embedded case, where Felix may be
     * stopped without the Java VM being stopped. In this case the
     * FelixStartLevel thread must be stopped explicitly.
     * <p>
     * This method is called by the
     * {@link StartLevelActivator#stop(BundleContext)} method.
     */
    void stop()
    {
        synchronized (m_requests)
        {
            if (m_thread != null)
            {
                // Null thread variable to signal to the thread that
                // we want it to exit.
                m_thread = null;

                // Wake up the thread, if it is currently in the wait() state
                // for more work.
                m_requests.notifyAll();
            }
        }
    }

    @Override
	public Bundle getBundle()
    {
        return m_felix;
    }

    @Override
	public int getStartLevel()
    {
        return m_felix.getActiveStartLevel();
    }

    @Override
	public void setStartLevel(int startlevel, FrameworkListener... listeners)
    {
        Object sm = System.getSecurityManager();

        if (sm != null)
        {
            ((SecurityManager) sm).checkPermission(
                new AdminPermission(m_felix, AdminPermission.STARTLEVEL));
        }

        if (startlevel <= 0)
        {
            throw new IllegalArgumentException(
                "Start level must be greater than zero.");
        }

        synchronized (m_requests)
        {
            if (m_thread == null)
            {
                throw new IllegalStateException("No inital startlevel yet");
            }
            // Queue request.
            m_requests.add(new StartLevelRequest(null, startlevel, listeners));
            m_requests.notifyAll();
        }
    }

    /**
     * This method is currently only called by the by the thread that calls
     * the Felix.start() method and the shutdown thread when the
     * framework is shutting down.
     * @param startlevel
    **/
    /* package */ void setStartLevelAndWait(int startlevel)
    {
        StartLevelRequest request = new StartLevelRequest(null, startlevel);
        synchronized (request)
        {
            synchronized (m_requests)
            {
                // Start thread if necessary.
                startThread();
                // Queue request.
                m_requests.add(request);
                m_requests.notifyAll();
            }

            try
            {
                request.wait();
            }
            catch (InterruptedException ex)
            {
                // Log it and ignore since it won't cause much of an issue.
                m_felix.getLogger().log(
                    Logger.LOG_WARNING,
                    "Wait for start level change during shutdown interrupted.",
                    ex);
            }
        }
    }

    @Override
	public int getInitialBundleStartLevel()
    {
        return m_felix.getInitialBundleStartLevel();
    }

    @Override
	public void setInitialBundleStartLevel(int startlevel)
    {
        Object sm = System.getSecurityManager();

        if (sm != null)
        {
            ((SecurityManager) sm).checkPermission(
                new AdminPermission(m_felix, AdminPermission.STARTLEVEL));
        }
        m_felix.setInitialBundleStartLevel(startlevel);
    }

    BundleStartLevel createBundleStartLevel(BundleImpl bundle)
    {
        return new BundleStartLevelImpl(bundle);
    }

    class BundleStartLevelImpl implements BundleStartLevel
    {
        private BundleImpl m_bundle;

        private BundleStartLevelImpl(BundleImpl bundle)
        {
            m_bundle = bundle;
        }

        @Override
		public Bundle getBundle()
        {
            return m_bundle;
        }

        @Override
		public int getStartLevel()
        {
            return m_felix.getBundleStartLevel(m_bundle);
        }

        @Override
		public void setStartLevel(int startlevel)
        {
            Object sm = System.getSecurityManager();

            if (sm != null)
            {
                ((SecurityManager) sm).checkPermission(
                    new AdminPermission(m_bundle, AdminPermission.EXECUTE));
            }

            if (m_bundle.getBundleId() == 0)
            {
                throw new IllegalArgumentException(
                    "Cannot change system bundle start level.");
            }
            else if (startlevel <= 0)
            {
                throw new IllegalArgumentException(
                    "Start level must be greater than zero.");
            }
            synchronized (m_requests)
            {
                // Start thread if necessary.
                startThread();
                // Synchronously persists the start level.
                m_bundle.setStartLevel(startlevel);
                // Queue request.
                m_requests.add(new StartLevelRequest(m_bundle, startlevel));
                m_requests.notifyAll();
            }
        }

        @Override
		public boolean isPersistentlyStarted()
        {
            return m_felix.isBundlePersistentlyStarted(m_bundle);
        }

        @Override
		public boolean isActivationPolicyUsed()
        {
            return m_felix.isBundleActivationPolicyUsed(m_bundle);
        }
    }

    @Override
	public void run()
    {
        // This thread loops forever, thus it should
        // be a daemon thread.
        StartLevelRequest previousRequest = null;
        while (true)
        {
            StartLevelRequest request = null;
            synchronized (m_requests)
            {
                // Wait for a request.
                while (m_requests.isEmpty())
                {
                    // Terminate the thread if requested to do so (see stop()).
                    if (m_thread == null)
                    {
                        return;
                    }

                    try
                    {
                        m_requests.wait();
                    }
                    catch (InterruptedException ex)
                    {
                        // Ignore.
                    }
                }

                // Get the requested start level.
                request = m_requests.remove(0);
            }

            // If the request object has no bundle, then the request
            // is to set the framework start level, otherwise the
            // request is to set the start level for a bundle.
            // NOTE: We don't catch any exceptions here, because
            // the invoked methods shield us from exceptions by
            // catching Throwables when they invoke callbacks.
            if (request.getBundle() == null)
            {
                // Set the new framework start level.
                try
                {
                    m_felix.setActiveStartLevel(request.getStartLevel(), request.getListeners());
                }
                catch (IllegalStateException ise)
                {
                    // Thrown if global lock cannot be acquired, in which case
                    // just retry (unless we already did)
                    if (previousRequest == request)
                    {
                        m_felix.getLogger().log(Logger.LOG_ERROR,
                            "Unexpected problem setting active start level to " + request, ise);
                    }
                    else
                    {
                        synchronized (m_requests)
                        {
                            m_requests.add(0, request);
                            previousRequest = request;
                        }
                    }
                }
                catch (Exception ex)
                {
                    m_felix.getLogger().log(Logger.LOG_ERROR,
                        "Unexpected problem setting active start level to " + request, ex);
                }
            }
            else
            {
                m_felix.setBundleStartLevel(request.getBundle(), request.getStartLevel());
            }

            // Notify any waiting thread that this request is done.
            synchronized (request)
            {
                request.notifyAll();
            }
        }
    }

    /**
     * This class holds a start level request. If the bundle is null then the
     * request is to set the framework start level.
     */
    private static final class StartLevelRequest
    {
        private final Bundle m_bundle;
        private final int m_startLevel;
        private final FrameworkListener[] m_listeners;

        private StartLevelRequest(Bundle bundle, int startLevel, FrameworkListener... listeners)
        {
            m_bundle = bundle;
            m_startLevel = startLevel;
            m_listeners = listeners;
        }

        private Bundle getBundle()
        {
            return m_bundle;
        }

        private int getStartLevel()
        {
            return m_startLevel;
        }

        public FrameworkListener[] getListeners()
        {
            return m_listeners;
        }
    }
}

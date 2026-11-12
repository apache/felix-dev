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
package org.apache.felix.http.jetty.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.Hashtable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.util.thread.VirtualThreadPool;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;

/**
 * Unit test for the thread pool selection in JettyService, which depends on the
 * combination of threadpool.max, virtualthreads.enable and virtualthreads.max.
 */
public class JettyServiceThreadPoolTest
{
    JettyConfig config;
    BundleContext context;

    /**
     * The virtual thread cases only apply to Java 21 or later. Probed the same way
     * JettyService probes it, so this stays correct beyond Java 21.
     */
    private static void assumeVirtualThreads()
    {
        try
        {
            Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
        }
        catch (NoSuchMethodException e)
        {
            assumeTrue("virtual threads are not available on this JVM", false);
        }
    }

    private ThreadPool createThreadPool(final Object... keysAndValues) throws Exception
    {
        final Hashtable<String, Object> props = new Hashtable<>();
        for (int i = 0; i < keysAndValues.length; i += 2)
        {
            props.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        this.config.update(props);
        return JettyService.createThreadPool(this.config);
    }

    @Test public void testNoThreadPoolConfigured() throws Exception
    {
        // null means Jetty's own default applies, which is a QueuedThreadPool with 200 threads
        assertNull(createThreadPool());
    }

    @Test public void testPlatformThreadPoolWithMax() throws Exception
    {
        final ThreadPool threadPool = createThreadPool(JettyConfig.FELIX_JETTY_THREADPOOL_MAX, 42);
        assertTrue(threadPool instanceof QueuedThreadPool);
        assertEquals(42, ((QueuedThreadPool) threadPool).getMaxThreads());
        assertNull(((QueuedThreadPool) threadPool).getVirtualThreadsExecutor());
    }

    @Test public void testVirtualThreadsUnbounded() throws Exception
    {
        assumeVirtualThreads();
        final ThreadPool threadPool = createThreadPool(
                JettyConfig.FELIX_JETTY_USE_VIRTUAL_THREADS, Boolean.TRUE.toString());

        assertTrue(threadPool instanceof QueuedThreadPool);
        final Executor executor = ((QueuedThreadPool) threadPool).getVirtualThreadsExecutor();
        // an unbounded per task executor, not a VirtualThreadPool
        assertFalse(executor instanceof VirtualThreadPool);
    }

    @Test public void testVirtualThreadsStandalonePoolBoundedByThreadPoolMax() throws Exception
    {
        assumeVirtualThreads();
        final ThreadPool threadPool = createThreadPool(
                JettyConfig.FELIX_JETTY_USE_VIRTUAL_THREADS, Boolean.TRUE.toString(),
                JettyConfig.FELIX_JETTY_THREADPOOL_MAX, 100);

        // the pre-existing behaviour: threadpool.max bounds the concurrent tasks
        assertTrue(threadPool instanceof VirtualThreadPool);
        assertEquals(100, ((VirtualThreadPool) threadPool).getMaxConcurrentTasks());
    }

    @Test public void testVirtualThreadsBoundedExecutor() throws Exception
    {
        assumeVirtualThreads();
        final ThreadPool threadPool = createThreadPool(
                JettyConfig.FELIX_JETTY_USE_VIRTUAL_THREADS, Boolean.TRUE.toString(),
                JettyConfig.FELIX_JETTY_THREADPOOL_MAX, 100,
                JettyConfig.FELIX_JETTY_VIRTUAL_THREADS_MAX, 50);

        // Jetty's preferred setup: platform threads for the acceptors and the selectors,
        // with a bounded VirtualThreadPool as the virtual threads executor
        assertTrue(threadPool instanceof QueuedThreadPool);
        final QueuedThreadPool queuedThreadPool = (QueuedThreadPool) threadPool;
        assertEquals(100, queuedThreadPool.getMaxThreads());

        final Executor executor = queuedThreadPool.getVirtualThreadsExecutor();
        assertTrue(executor instanceof VirtualThreadPool);
        assertEquals(50, ((VirtualThreadPool) executor).getMaxConcurrentTasks());

        // added as a bean so that the QueuedThreadPool starts and stops it; an unstarted
        // VirtualThreadPool rejects every task
        assertTrue(queuedThreadPool.getBeans(VirtualThreadPool.class).contains(executor));
    }

    @Test public void testVirtualThreadsBoundedExecutorWithoutThreadPoolMax() throws Exception
    {
        assumeVirtualThreads();
        final ThreadPool threadPool = createThreadPool(
                JettyConfig.FELIX_JETTY_USE_VIRTUAL_THREADS, Boolean.TRUE.toString(),
                JettyConfig.FELIX_JETTY_VIRTUAL_THREADS_MAX, 50);

        assertTrue(threadPool instanceof QueuedThreadPool);
        final Executor executor = ((QueuedThreadPool) threadPool).getVirtualThreadsExecutor();
        assertTrue(executor instanceof VirtualThreadPool);
        assertEquals(50, ((VirtualThreadPool) executor).getMaxConcurrentTasks());
    }

    @Test public void testVirtualThreadsMaxIgnoredWhenNotPositive() throws Exception
    {
        assumeVirtualThreads();
        // Jetty treats maxConcurrentTasks <= 0 as unbounded, so such a value must not select
        // the bounded setup, and the pre-existing behaviour has to be kept
        final ThreadPool threadPool = createThreadPool(
                JettyConfig.FELIX_JETTY_USE_VIRTUAL_THREADS, Boolean.TRUE.toString(),
                JettyConfig.FELIX_JETTY_THREADPOOL_MAX, 100,
                JettyConfig.FELIX_JETTY_VIRTUAL_THREADS_MAX, 0);

        assertTrue(threadPool instanceof VirtualThreadPool);
        assertEquals(100, ((VirtualThreadPool) threadPool).getMaxConcurrentTasks());
    }

    @Test public void testVirtualThreadsMaxIgnoredWhenDisabled() throws Exception
    {
        final ThreadPool threadPool = createThreadPool(
                JettyConfig.FELIX_JETTY_VIRTUAL_THREADS_MAX, 50,
                JettyConfig.FELIX_JETTY_THREADPOOL_MAX, 42);

        assertTrue(threadPool instanceof QueuedThreadPool);
        assertNull(((QueuedThreadPool) threadPool).getVirtualThreadsExecutor());
    }

    @Before
    public void setUp()
    {
        this.context = Mockito.mock(BundleContext.class);
        this.config = new JettyConfig(this.context);
    }
}

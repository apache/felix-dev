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

import junit.framework.TestCase;
import org.apache.felix.framework.util.FelixConstants;
import org.osgi.framework.*;
import org.osgi.framework.connect.ConnectModule;
import org.osgi.framework.connect.ModuleConnector;
import org.osgi.framework.launch.Framework;
import org.osgi.service.resolver.Resolver;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LaunchTest extends TestCase
{
    public void testInit() throws Exception
    {
        Map params = new HashMap();
        File cacheDir = File.createTempFile("felix-cache", ".dir");
        cacheDir.delete();
        cacheDir.mkdirs();
        String cache = cacheDir.getPath();
        params.put("felix.cache.profiledir", cache);
        params.put("felix.cache.dir", cache);
        params.put(FelixConstants.LOG_LOGGER_PROP, new Logger()
        {
            @Override
            protected void doLogOut(int level, String s, Throwable throwable) {

            }
        });
        params.put(Constants.FRAMEWORK_STORAGE, cache);

        try
        {
            Framework f = new Felix(params, new ModuleConnector()
            {
                boolean first = true;
                @Override
                public void initialize(File storage, Map<String, String> configuration)
                {
                    if (first) {
                        first = false;
                        throw new IllegalStateException("Test");
                    }
                }

                @Override
                public Optional<ConnectModule> connect(String location)
                {
                    return Optional.empty();
                }

                @Override
                public Optional<BundleActivator> newBundleActivator()
                {
                    return Optional.empty();
                }
            }){
                boolean first = true;
                @Override
                synchronized BundleActivator getActivator()
                {
                    BundleActivator activator = super.getActivator();
                    if (first) {
                        first = false;
                        return new BundleActivator() {
                            @Override
                            public void start(BundleContext context) throws Exception
                            {
                                activator.start(context);
                                throw new IllegalStateException("TEst");
                            }

                            @Override
                            public void stop(BundleContext context) throws Exception
                            {
                                activator.stop(context);
                            }
                        };
                    }
                    return super.getActivator();
                }
            };
            try
            {
                f.init();
                fail("Excepted init to fail");
            }
            catch (Exception ex)
            {

            }
            assertEquals(Bundle.INSTALLED, f.getState());
            try
            {
                f.init();
                fail("Excepted init to fail");
            }
            catch (Exception ex)
            {

            }
            assertEquals(Bundle.INSTALLED, f.getState());
            f.init();
            assertEquals(Bundle.STARTING, f.getState());
            f.stop();
            f.waitForStop(0);
            assertEquals(Bundle.RESOLVED, f.getState());
            f.init();
            assertEquals(1,f.getBundleContext().getServiceReferences(Resolver.class, null).size());
            assertEquals(Bundle.STARTING, f.getState());
            f.start();
            assertEquals(Bundle.ACTIVE, f.getState());
            f.stop();
            f.waitForStop(0);
        }
        finally
        {
            deleteDir(cacheDir);
        }
    }

    private static void deleteDir(File root) throws IOException
    {
        if (root.isDirectory())
        {
            for (File file : root.listFiles())
            {
                deleteDir(file);
            }
        }
        assertTrue(root.delete());
    }
}

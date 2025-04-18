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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.launch.Framework;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class ServiceObjectsTest
{
    @Test
    void serviceObjects() throws Exception
    {
        Map<String,Object> params = new HashMap<>();
        File cacheDir = File.createTempFile("felix-cache", ".dir");
        cacheDir.delete();
        cacheDir.mkdirs();
        String cache = cacheDir.getPath();
        params.put("felix.cache.profiledir", cache);
        params.put("felix.cache.dir", cache);
        params.put(Constants.FRAMEWORK_STORAGE, cache);
        Framework f = new Felix(params);
        f.init();
        f.start();

        try
        {
            BundleContext context = f.getBundleContext();
            ServiceRegistration<Object> registration =
                    context.registerService(Object.class, new Object(), null);

            ServiceReference<Object> reference = registration.getReference();

            ServiceObjects<Object> serviceObjects = context.getServiceObjects(reference);

            Object service = serviceObjects.getService();

            serviceObjects.ungetService(service);

            assertThat(serviceObjects.getService()).isEqualTo(service);
            service = serviceObjects.getService();

            registration.unregister();

            serviceObjects.ungetService(service);
        }
        finally
        {
            f.stop();
            Thread.sleep(1000);
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
        assertThat(root.delete()).isTrue();
    }
}

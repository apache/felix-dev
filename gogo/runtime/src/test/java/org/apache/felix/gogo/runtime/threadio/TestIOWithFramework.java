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
package org.apache.felix.gogo.runtime.threadio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.felix.gogo.runtime.activator.Activator;
import org.apache.felix.gogo.runtime.activator.ServiceFacade;
import org.apache.felix.service.systemio.SystemIO;
import org.apache.felix.service.threadio.ThreadIO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;

public class TestIOWithFramework
{
   private Framework framework;
   private File tmp;

   @Before
   public void setup() throws Exception
   {
      tmp = Files.createTempDirectory("TestIOWithFramework").toFile();
   }

   private void fw(Map<String, String> configuration) throws BundleException
   {
      if (configuration == null)
      {
         configuration = new HashMap<>();
      }

      configuration.put(Constants.FRAMEWORK_STORAGE, tmp.getAbsolutePath());
      configuration.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
      framework = ServiceLoader.load(org.osgi.framework.launch.FrameworkFactory.class).iterator().next()
               .newFramework(configuration);
      framework.init();
      framework.start();
   }

   @After
   public void after() throws Exception
   {
      framework.stop();
      framework.waitForStop(100000);
      delete(tmp);
   }

   void delete(File f)
   {
      if (f.isFile())
      {
         f.delete();
      }
      else
      {
         for (File sub : f.listFiles())
         {
            delete(sub);
         }
      }
   }

   @Test
   public void testWithFrameworkAndDefault() throws Exception
   {
      fw(null);
      BundleContext context = framework.getBundleContext();
      Activator a = new Activator();
      a.start(context);
      assertNotNull(context.getServiceReference(SystemIO.class));
      assertNotNull(context.getServiceReference(ThreadIO.class));
      a.stop(context);
   }

   @Test
   public void testWithExternalSystemIO() throws Exception
   {
      Map<String, String> configuration = new HashMap<>();
      configuration.put("org.apache.felix.gogo.systemio.timeout", "5000");
      fw(configuration);
      BundleContext context = framework.getBundleContext();

      Closeable c = mock(Closeable.class);
      SystemIO sio = mock(SystemIO.class);
      when(sio.system(Mockito.any(InputStream.class), Mockito.any(OutputStream.class), Mockito.any(OutputStream.class)))
               .thenReturn(c);
      context.registerService(SystemIO.class, sio, null);

      Activator a = new Activator();
      a.start(context);

      ServiceReference<ThreadIO> ref = context.getServiceReference(ThreadIO.class);
      assertNotNull(ref);
      ThreadIO tio = context.getService(ref);
      assertNotNull(tio);
      tio.setStreams(null, null, null);
      a.stop(context);

      Mockito.verify(c).close();
   }

   public interface Foo
   {
      void bar();
   }

   @Test(expected = ServiceException.class)
   public void testFacadeWithoutService() throws BundleException, IOException
   {
      fw(null);
      try (ServiceFacade<Foo> sf = new ServiceFacade<>(Foo.class, framework.getBundleContext(), 500))
      {
         Foo foo = sf.get();

         foo.bar();
      }
   }

   @Test
   public void testFacadeWithService() throws BundleException, IOException
   {
      fw(null);
      final AtomicInteger ai = new AtomicInteger(0);
      framework.getBundleContext().registerService(Foo.class, new Foo() {

         @Override
         public void bar()
         {
            ai.incrementAndGet();
         }
         
      }, null);
      try (ServiceFacade<Foo> sf = new ServiceFacade<>(Foo.class, framework.getBundleContext(), 5000))
      {
         Foo foo = sf.get();

         long time = System.currentTimeMillis();
         foo.bar();
         if ( System.currentTimeMillis() > time + 4000)
            fail("Took too much time");
         
         assertEquals(1, ai.get());
      }
   }
}

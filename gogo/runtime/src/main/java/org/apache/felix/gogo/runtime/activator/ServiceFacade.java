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
package org.apache.felix.gogo.runtime.activator;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceException;
import org.osgi.util.tracker.ServiceTracker;

public class ServiceFacade<S> implements Closeable
{
   final ServiceTracker<S,S> tracker;
   final S facade;
   
   @SuppressWarnings("unchecked")
   public ServiceFacade(final Class<S> clazz, BundleContext context, final long timeout)
   {
      this.tracker = new ServiceTracker<>(context, clazz, null);
      this.tracker.open();
      this.facade = (S) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] {clazz}, new InvocationHandler() {

         @Override
         public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
         {
            try
            {
               S s = tracker.waitForService(timeout);
               if ( s == null)
                  throw new ServiceException( "No such service " + clazz.getName() + ", waited " + timeout + "ms");
               return method.invoke(s, args);
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
               throw new RuntimeException(e);
            }
         }
         
      });
   }

   public S get() {
      return facade;
   }

   @Override
   public void close() throws IOException
   {
      tracker.close();
   }
}

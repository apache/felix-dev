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
package org.apache.felix.gogo.runtime;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;


public class CommandSessionTest
{

   @Test
   public void onCloseTest()
   {
      CommandProcessorImpl processor = new CommandProcessorImpl(null);
      ByteArrayInputStream bais = new ByteArrayInputStream("".getBytes());
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      
      final AtomicInteger b = new AtomicInteger(0);
      try (CommandSessionImpl session = processor.createSession(bais, baos, baos))
      {


         Runnable runnable = new Runnable()
         {

            @Override
            public void run()
            {
               b.incrementAndGet();
            }
         };
         session.onClose(runnable);
         session.onClose(runnable);

         assertEquals(0,b.get());
         System.gc();
      }

      assertEquals(2,b.get());
   }

   @Test
   public void onCloseTestGc()
   {
      CommandProcessorImpl processor = new CommandProcessorImpl(null);
      ByteArrayInputStream bais = new ByteArrayInputStream("".getBytes());
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      
      final AtomicInteger b = new AtomicInteger(0);
      try (CommandSessionImpl session = processor.createSession(bais, baos, baos))
      {


         session.onClose(new Runnable()
         {

            @Override
            public void run()
            {
               b.incrementAndGet();
            }
         });
         session.onClose(new Runnable()
         {

            @Override
            public void run()
            {
               b.incrementAndGet();
            }
         });

         assertEquals(0,b.get());
      }

      assertEquals(2,b.get());
   }

}

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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.felix.gogo.runtime.systemio.SystemIOImpl;
import org.junit.Assert;
import org.junit.Test;

public class TestThreadIO
{

   /**
    * Test if the threadio works in a nested fashion. We first push ten markers on the stack and print a message for
    * each, capturing the output in a ByteArrayOutputStream. Then we pop them, also printing a message identifying the
    * level. Then we verify the output for each level.
    */
   @Test
   public void testNested()
   {
      SystemIOImpl systemio = new SystemIOImpl();
      systemio.start();
      ThreadIOImpl tio = new ThreadIOImpl(systemio);
      try
      {
         tio.start();
         List<ByteArrayOutputStream> list = new ArrayList<>();
         for (int i = 0; i < 10; i++)
         {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            list.add(out);
            tio.setStreams(System.in, new PrintStream(out), System.err);
            System.out.print("b" + i);
         }
         for (int i = 9; i >= 0; i--)
         {
            System.out.println("e" + i);
            tio.close();
         }
         for (int i = 0; i < 10; i++)
         {
            String message = list.get(i).toString().trim();
            Assert.assertEquals("b" + i + "e" + i, message);
         }
      }
      finally
      {
         tio.stop();
         systemio.stop();
      }
   }

   /**
    * Simple test too see if the basics work.
    * @throws IOException 
    */
   @SuppressWarnings("resource")
   @Test
   public void testSimple() throws IOException
   {
      SystemIOImpl systemio = new SystemIOImpl();
      systemio.start();
      ThreadIOImpl tio = new ThreadIOImpl(systemio);
      tio.start();
      try
      {
         System.out.println("Hello World");
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         ByteArrayOutputStream err = new ByteArrayOutputStream();
         tio.setStreams(System.in, new PrintStream(out), new PrintStream(err));

         System.out.println("Simple Normal Message");
         System.err.println("Simple Error Message");
         tio.stop();
         String normal = out.toString().trim();
         // String error = err.toString().trim();
         Assert.assertEquals("Simple Normal Message", normal);
         // assertEquals("Simple Error Message", error );
         System.out.println("Goodbye World");
      }
      finally
      {
         systemio.close();
         tio.close();
      }
   }
   
   @Test
   @SuppressWarnings("resource")
   public void testNullInputStream() throws IOException {
      SystemIOImpl systemio = new SystemIOImpl();
      systemio.start();
      ThreadIOImpl tio = new ThreadIOImpl(systemio);
      tio.start();
      try
      {
         byte[] test = "abc".getBytes(StandardCharsets.UTF_8);
         ByteArrayInputStream bin = new ByteArrayInputStream( test);
         tio.setStreams(bin, null, null);
         tio.setStreams(null, null, null);
         byte data[] = new byte[3];
         System.in.read(data);
         assertTrue(Arrays.equals(test, data));
         
         tio.close();
      }
      finally
      {
         tio.stop();
         systemio.close();
      }
   }

   @Test
   @SuppressWarnings("resource")
   public void testNoInputStreamSetInThreadIO() throws IOException {
      SystemIOImpl systemio = new SystemIOImpl();
      systemio.start();
      byte[] test = "abc".getBytes(StandardCharsets.UTF_8);
      ByteArrayInputStream bin = new ByteArrayInputStream( test);
      Closeable system = systemio.system(bin, null,null);
      ThreadIOImpl tio = new ThreadIOImpl(systemio);
      tio.start();
      try
      {
         byte data[] = new byte[3];
         System.in.read(data);
         assertTrue(Arrays.equals(test, data));
         tio.close();
      }
      finally
      {
         system.close();
         systemio.close();
         tio.stop();
      }
   }
   
   @Test
   public void testWithFrameworkService() {
      
   }
}

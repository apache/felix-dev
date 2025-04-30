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
// DWB20: ThreadIO should check and reset IO if something (e.g. jetty) overrides
package org.apache.felix.gogo.runtime.threadio;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Logger;

import org.apache.felix.service.systemio.SystemIO;
import org.apache.felix.service.threadio.ThreadIO;
import org.osgi.annotation.bundle.Capability;
import org.osgi.namespace.service.ServiceNamespace;

@Capability(namespace = ServiceNamespace.SERVICE_NAMESPACE,
         attribute = "objectClass='org.apache.felix.service.threadio.ThreadIO'")
public class ThreadIOImpl implements ThreadIO
{
   static final Logger log = Logger.getLogger(ThreadIOImpl.class.getName());
   final SystemIO systemio;
   final ThreadLocal<Streams> threadLocal = new ThreadLocal<>();
   Closeable system;
   private ThreadInputStream threadInputStream;

   class Streams
   {
      final PrintStream out;
      final PrintStream err;
      final InputStream in;
      Streams prev;

      Streams(InputStream in, PrintStream out, PrintStream err)
      {
         this.in = in;
         this.out = out;
         this.err = err;
      }

   }

   abstract class ThreadOutStream extends OutputStream
   {
      @Override
      public void write(int b) throws IOException
      {
         Streams streams = threadLocal.get();
         if (streams == null)
            return;

         get(streams).write(b);
      }

      @Override
      public void write(byte[] b) throws IOException
      {
         write(b, 0, b.length);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException
      {
         Streams streams = threadLocal.get();
         if (streams == null)
            return;

         get(streams).write(b, off, len);
      }

      abstract OutputStream get(Streams s);
   }


   public ThreadIOImpl(SystemIO systemio)
   {
      this.systemio = systemio;
      this.threadInputStream = new ThreadInputStream();
   }

   /**
    * Constructor for use in non-OSGi environments (like gogo/jline)
    */
   public ThreadIOImpl()
   {
      this.systemio = null;
      this.threadInputStream = new ThreadInputStream();
   }

   public void start()
   {}

   public void stop()
   {
      if (system != null)
         try
         {
            system.close();
         }
         catch (IOException e)
         {
            // ignore
         }
   }

   public void close()
   {
      Streams streams = threadLocal.get();
      if (streams != null)
         threadLocal.set(streams.prev);
   }

   public void setStreams(InputStream in, PrintStream out, PrintStream err)
   {
      init();
      Streams s = new Streams(in, out, err);
      s.prev = threadLocal.get();
      threadLocal.set(s);
   }

   private synchronized void init()
   {
      if (system == null && systemio != null)
      {
         system = systemio.system(threadInputStream, new ThreadOutStream()
         {

            @Override
            OutputStream get(Streams s)
            {
               return s.out;
            }

         }, new ThreadOutStream()
         {

            @Override
            OutputStream get(Streams s)
            {
               return s.err;
            }
         });
      }
   }

   /**
    * Get the thread input stream that can be used to replace System.in
    *
    * @return the thread input stream
    */
   public InputStream getThreadInputStream() {
      return threadInputStream;
   }

   /**
    * Inner class that handles the InputStream functionality
    */
   private class ThreadInputStream extends InputStream {
      @Override
      public int read() throws IOException
      {
         Streams s = threadLocal.get();
         while (s != null)
         {
            if (s.in == null)
               s = s.prev;
            else
            {
               return s.in.read();
            }
         }
         return SystemIO.NO_DATA;
      }
   }
}

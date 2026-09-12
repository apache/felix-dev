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
package org.apache.felix.gogo.runtime.systemio;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import org.apache.felix.service.systemio.SystemIO;
import org.osgi.annotation.bundle.Capability;
import org.osgi.namespace.service.ServiceNamespace;

/**
 * Implement the SystemIO API to allow the System streams to be overridden
 */
@Capability(namespace = ServiceNamespace.SERVICE_NAMESPACE,
         attribute = "objectClass='org.apache.felix.service.systemio.SystemIO'")
public class SystemIOImpl extends InputStream implements SystemIO
{
   static private final Logger log = Logger.getLogger(SystemIOImpl.class.getName());

   final List<InputStream> stdins = new CopyOnWriteArrayList<>();
   final List<OutputStream> stdouts = new CopyOnWriteArrayList<>();
   final List<OutputStream> stderrs = new CopyOnWriteArrayList<>();

   final InputStream in = System.in;
   final PrintStream out = System.out;
   final PrintStream err = System.err;

   private PrintStream rout;
   private PrintStream rerr;


   public void start()
   {
      stdins.add(in);
      stdouts.add(out);
      stderrs.add(err);
      rout = new PrintStream(new DelegateStream(stdouts), true);
      rerr = new PrintStream(new DelegateStream(stderrs), true);
      System.setOut(rout);
      System.setErr(rerr);
      System.setIn(this);
   }

   public void stop()
   {
      if (System.in == this)
      {
         System.setIn(in);
      }
      else
      {
         log.warning("conflict: the dispatching input stream was replaced");
      }
      if (System.out == rout)
      {
         System.setOut(out);
      }
      else
      {
         log.warning("conflict: the dispatching stdout stream was replaced");
      }
      if (System.err == rerr)
      {
         System.setErr(err);
      }
      else
      {
         log.warning("conflict: the dispatching stderr stream was replaced");
      }
   }

   @Override
   public Closeable system(final InputStream stdin, final OutputStream stdout, final OutputStream stderr)
   {
      if (stdin != null && stdin != System.in && stdin != in)
      {
         stdins.add(0,stdin);
      }
      if (stdout != null)
         stdouts.add(stdout);
      if (stderr != null)
         stderrs.add(stderr);
      return new Closeable()
      {
         @Override
         public void close() throws IOException
         {
            if (stdin != null && stdin != System.in && stdin != in)
            {
               int inInFront = stdins.indexOf(stdin);
               assert inInFront >= 0;
               stdins.remove(inInFront);
               assert stdins.size() > 0;
            }
            if (stdout != null)
               stdouts.remove(stdout);
            if (stderr != null)
               stderrs.remove(stderr);
         }
      };
   }

   @Override
   public int read() throws IOException
   {
      assert stdins.size() > 0;
      for ( InputStream in : stdins) {
         int b = in.read();
         if ( b != SystemIO.NO_DATA)
            return b;
      }
      return -1; // unreachable because stdin is at the end
   }

}

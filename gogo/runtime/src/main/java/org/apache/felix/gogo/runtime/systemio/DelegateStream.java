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

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

class DelegateStream extends OutputStream
{

   final List<OutputStream> outs;


   DelegateStream(List<OutputStream> outs)
   {
      this.outs = outs;
   }

   @Override
   public void write(int b) throws IOException
   {
      for ( OutputStream out : outs) try {
         out.write(b);
      } catch( Exception e) {
         // ignore
      }
   }

   @Override
   public void write(byte b[]) throws IOException
   {
      write(b, 0, b.length);
   }

   @Override
   public void write(byte b[], int off, int len) throws IOException
   {
      for ( OutputStream out : outs) try {
         out.write(b, off, len);
      } catch( Exception e) {
         // ignore
      }
   }

   
   @Override
   public void flush()
   {
      for ( OutputStream out : outs) try {
         out.flush();
      } catch( Exception e) {
         // ignore
      }

   }

}

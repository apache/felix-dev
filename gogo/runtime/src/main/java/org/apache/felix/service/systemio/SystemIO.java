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
package org.apache.felix.service.systemio;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A simple service to listen to the system streams. The System.out and System.err writes will be dispatched to all
 * registered listeners as well as the original streams. If a read is done on System.in, the last registered will handle
 * the read. If no one is registered, the original System.in is used.
 * <p>
 * The purpose of this service is to share the System.in, System.out, and System.err singletons.
 * <p>
 * Implementations must warn if someone else overrides the System.xxx streams and not reset them if this happens.
 *
 */
public interface SystemIO
{
   /**
    * A framework property signalling that Gogo should use an external SystemIO service instead of its build in one. The
    * property value must be the number of milliseconds to wait for this external service. Any value <=0 or not numeric
    * will result in using the internal implementation.
    */

   String TIMEOUT = "org.apache.felix.gogo.systemio.timeout";

   /**
    * An input stream can return this from {@link InputStream#read()} when it has no data. This should force the
    * implementation to look for another input stream.
    */
   int NO_DATA = -42;

   /**
    * Register overrides for the System streams. If a stream is null, it will not be registered. The stdin InputStream
    * can return {@link #NO_DATA} if it does not want to be used as the last input stream. This can be used to filter
    * for example by the current thread.
    * 
    * @param stdin the System.in handler or null.
    * @param stdout the System.out listener
    * @param stderr the System.err listener
    * @return a closeable that when closed will unregister the streams
    */
   Closeable system(InputStream stdin, OutputStream stdout, OutputStream stderr);
}

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
package org.apache.felix.framework.util;

import java.util.stream.Stream;

/**
 * <p>
 * Simple utility class used to obtain the current call stack as an array of
 * classes, innermost caller first.
 * </p>
 * <p>
 * This used to extend <tt>SecurityManager</tt> purely to expose its protected
 * <tt>getClassContext()</tt> method. The Security Manager is permanently
 * disabled as of Java SE 24 (JEP 486) and is marked for removal, so the same
 * information is now obtained from <tt>StackWalker</tt>, which is the supported
 * replacement and yields frames in the same order.
 * </p>
**/
public class SecurityManagerEx
{
    // On Android getClassContext() used to return null - keep tolerating an empty stack.
    private static final Class<?>[] EMPTY_CLASSES = new Class[0];

    private static final StackWalker WALKER =
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public Class<?>[] getClassContext()
    {
        Class<?>[] result = WALKER.walk(
            (Stream<StackWalker.StackFrame> frames) ->
                frames.map(StackWalker.StackFrame::getDeclaringClass).toArray(Class<?>[]::new));
        return result != null ? result : EMPTY_CLASSES;
    }
}
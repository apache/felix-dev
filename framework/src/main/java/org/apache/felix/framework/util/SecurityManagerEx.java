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

/**
 * <p>
 * Simple utility class used to provide public access to the protected
 * <tt>getClassContext()</tt> method of <tt>SecurityManager</tt>
 * </p>
**/
public class SecurityManagerEx extends SecurityManager
{
    // In Android apparently getClassContext returns null - we work around this by returning an empty array in that case.
    private static final Class[] EMPTY_CLASSES = new Class[0];

    @Override
	public Class[] getClassContext()
    {
        Class[] result = super.getClassContext();
        return result != null ? result : EMPTY_CLASSES;
    }
}
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
package org.apache.felix.proxy.client;

import java.lang.reflect.Proxy;

import org.apache.felix.proxy.api.Greeting;

/**
 * Creates a dynamic proxy for {@link Greeting}. This class only references the Greeting interface
 * (via the {@code Greeting.class} literal); it never references org.apache.felix.proxy.model
 * directly. That package therefore appears in Import-Package only because bnd (7.2.0+) inspects
 * the method signatures of interfaces passed to Proxy.newProxyInstance.
 */
public class Client {
    public Greeting create() {
        return (Greeting) Proxy.newProxyInstance(
                Client.class.getClassLoader(),
                new Class[] { Greeting.class },
                (proxy, method, args) -> null);
    }
}

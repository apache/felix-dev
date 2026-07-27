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
package org.apache.felix.proxy.api;

import org.apache.felix.proxy.model.Message;

/**
 * Proxied interface. Its single method references {@link Message}, which lives in a different
 * package. The bundle under test only references this interface via a class literal, so the
 * Message package can reach Import-Package solely through bnd's Proxy.newProxyInstance detection.
 */
public interface Greeting {
    Message greet();
}

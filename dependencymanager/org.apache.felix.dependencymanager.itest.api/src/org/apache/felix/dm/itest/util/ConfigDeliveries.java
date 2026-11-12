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
package org.apache.felix.dm.itest.util;

import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Detects that Config Admin delivers the same configuration again to a callback.
 * <p>
 * Config Admin can provide a configuration to a service twice, for example once because the service registers
 * while the configuration is updated (an update of the registration and an update of the configuration, close
 * together). The tests count the configuration updates they expect, and ignore such a repeated delivery.
 */
public class ConfigDeliveries {
    private Object m_last;

    /**
     * @return true if the configuration is the one which has been delivered the last time. A lost configuration
     *         (null) is never a repeat.
     */
    public synchronized boolean isRepeat(Dictionary<?, ?> properties) {
        if (properties == null) {
            m_last = null;
            return false;
        }
        Map<Object, Object> copy = new HashMap<>();
        for (Object key : Collections.list(properties.keys())) {
            copy.put(key, properties.get(key));
        }
        return isRepeat((Object) copy);
    }

    /**
     * @param identity describes the content of a typed configuration, for example the values of its properties.
     */
    public synchronized boolean isRepeat(Object identity) {
        boolean repeat = identity != null && Objects.equals(identity, m_last);
        m_last = identity;
        return repeat;
    }
}

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
package org.apache.felix.hc.api.condition;

/**
 * Marker service that other services can depend on to automatically 
 * activate/deactivate based on a certain health status.
 * 
 * To activate a component only upon healthiness of a certain tag/name:
 * 
 * <pre>
 *  &#64;Reference(target="(tag=systemready)")
 *  Healthy healthy;
 * 
 *  &#64;Reference(target="(name=My Health Check)")
 *  Healthy healthy;
 * </pre>
 * 
 * For this to work, the PID {@code org.apache.felix.hc.core.impl.monitor.HealthCheckMonitor} needs to configured 
 * for given tag/name.
 * 
 */
public interface Healthy {

}

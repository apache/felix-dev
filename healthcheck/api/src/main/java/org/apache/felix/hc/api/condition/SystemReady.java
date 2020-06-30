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
 * Special marker service for tag "systemready" that by convention can be used as follows:
 * 
 * <pre>
 *  &#64;Reference
 *  SystemReady systemReady;
 *  
 * 
 *  &#64;Reference(target="(tag=systemready)")  // same as SystemReady above
 *  Healthy healthy; 
 * </pre>
 * 
 * Same as for Healthy, the PID {@code org.apache.felix.hc.core.impl.monitor.HealthCheckMonitor} 
 * needs to configured for the tag "systemready" for this to work.
 */
public interface SystemReady extends Healthy {

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.metrics.osgi;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * A listener that is notified of the startup metrics
 * 
 * <p>The time of the notification can be delayed after the actual application start, as
 * the implementation may choose to delay it to ensure that the startup is not affected
 * by e.g. bouncing services.</p>
 * 
 * <p>Listeners that register after the application startup will receive a notification anyway.</p>
 *
 */
@ConsumerType
public interface StartupMetricsListener {
    
    /**
     * @param metrics the startup metrics
     */
    void onStartupComplete(StartupMetrics metrics);
}

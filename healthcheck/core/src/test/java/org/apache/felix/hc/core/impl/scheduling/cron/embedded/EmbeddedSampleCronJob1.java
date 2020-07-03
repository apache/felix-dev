/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.felix.hc.core.impl.scheduling.cron.embedded;

public class EmbeddedSampleCronJob1 implements EmbeddedCronJob {

    public static volatile int staticCounter = 0;
    public volatile int counter = 0;

    private final String name;
    private final Callback callback;

    public EmbeddedSampleCronJob1(final Callback callback) {
        this.callback = callback;
        name = "SampleEmbeddedCronJob1";
    }

    public EmbeddedSampleCronJob1(final Callback callback, final String name) {
        this.callback = callback;
        this.name = name;
    }

    @Override
    public void run() throws Exception {
        synchronized (EmbeddedSampleCronJob1.class) {
            counter++;
            staticCounter++;
        }
        callback.callback(this);
    }

    public interface Callback {
        void callback(EmbeddedSampleCronJob1 service);
    }

    @Override
    public String cron() {
        return "* * * * * *";
    }

    @Override
    public String name() {
        return name;
    }
}
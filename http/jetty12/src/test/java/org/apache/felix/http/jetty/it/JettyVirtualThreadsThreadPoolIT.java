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
package org.apache.felix.http.jetty.it;

import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class JettyVirtualThreadsThreadPoolIT extends JettyVirtualThreadsIT {
    @Override
    protected Option felixHttpConfig(int httpPort) {
        return newConfiguration("org.apache.felix.http")
                .put("org.osgi.service.http.port", httpPort)
                .put("org.apache.felix.http.jetty.threadpool.max", 100)
                .put("org.apache.felix.http.jetty.virtualthreads.enable", Boolean.TRUE.toString())
                .asOption();
    }
}

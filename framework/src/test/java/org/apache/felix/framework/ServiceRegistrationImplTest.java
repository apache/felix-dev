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
package org.apache.felix.framework;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import org.mockito.Mockito;
import org.osgi.framework.Bundle;

class ServiceRegistrationImplTest
{
    @Test
    void markCurrentThread() throws Exception
    {
        final ServiceRegistrationImpl<?> sri = new ServiceRegistrationImpl<>(
                new ServiceRegistry(null, null), Mockito.mock(Bundle.class),
                new String [] {String.class.getName()}, 1L, "foo", null);

        assertThat(sri.currentThreadMarked()).isFalse();
        sri.markCurrentThread();
        assertThat(sri.currentThreadMarked()).isTrue();

        final List<Throwable> exceptions = new ArrayList<>();
        Thread t = new TestThread(exceptions, new Runnable() {
            @Override
            public void run()
            {
                assertThat(sri.currentThreadMarked()).isFalse();
                sri.markCurrentThread();
                assertThat(sri.currentThreadMarked()).isTrue();
            }
        });
        t.start();
        t.join();
        assertThat(exceptions.size()).as("There should be no exceptions: " + exceptions).isEqualTo(0);

        sri.unmarkCurrentThread();
        assertThat(sri.currentThreadMarked()).isFalse();

        Thread t2 = new TestThread(exceptions, new Runnable() {
            @Override
            public void run()
            {
                assertThat(sri.currentThreadMarked()).isFalse();
                sri.markCurrentThread();
                assertThat(sri.currentThreadMarked()).isTrue();
                sri.unmarkCurrentThread();
                assertThat(sri.currentThreadMarked()).isFalse();
            }
        });
        t2.start();
        t2.join();
        assertThat(exceptions.size()).as("There should be no exceptions: " + exceptions).isEqualTo(0);
    }

    static class TestThread extends Thread {
        private final Runnable runnable;
        private final List<Throwable> exceptions;

        public TestThread(List<Throwable> exceptionList, Runnable runnable)
        {
            this.runnable = runnable;
            this.exceptions = exceptionList;
        }

        @Override
        public void run()
        {
            try
            {
                runnable.run();
            }
            catch (Throwable th)
            {
                exceptions.add(th);
            }
        }
    }
}
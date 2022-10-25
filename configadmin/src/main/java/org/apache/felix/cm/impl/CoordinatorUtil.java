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
package org.apache.felix.cm.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.osgi.service.coordinator.Coordination;
import org.osgi.service.coordinator.Coordinator;
import org.osgi.service.coordinator.Participant;

/**
 * Utility class for coordinations
 */
public class CoordinatorUtil {

    public static final class Notifier implements Participant {

        private final List<Runnable> runnables = new ArrayList<Runnable>();

        private final UpdateThread thread;

        public Notifier(final UpdateThread t) {
            this.thread = t;
        }

        /**
         * Compact the notification if there is more than one operation for a given pid
         * @return The compacted list
         */
        private List<Runnable> compact() {
            final List<Runnable> result = new ArrayList<>(this.runnables);
            this.runnables.clear();

            final Map<String, ConfigurationManager.ConfigurationProvider> lastOperations = new HashMap<>();
            final Map<String, Boolean> firstOperationIsCreate = new HashMap<>();

            for(int i=0;i<result.size();i++) {
                final Runnable current = result.get(i);

                if ( current instanceof ConfigurationManager.UpdateConfiguration ) {
                    final ConfigurationManager.UpdateConfiguration up = (ConfigurationManager.UpdateConfiguration)current;
                    final String pid = up.getTargetedServicePid().getServicePid();
                    if ( firstOperationIsCreate.get(pid) == null ) {
                        firstOperationIsCreate.put(pid, up.revision == 1);
                    }
                    final ConfigurationManager.ConfigurationProvider last = lastOperations.get(pid);
                    lastOperations.put(pid, up);
                    if ( last != null ) {
                        result.remove(last);
                        i--;
                    }
                } else if ( current instanceof ConfigurationManager.DeleteConfiguration ) {
                    final ConfigurationManager.DeleteConfiguration up = (ConfigurationManager.DeleteConfiguration)current;
                    final String pid = up.getTargetedServicePid().getServicePid();
                    if ( firstOperationIsCreate.get(pid) == null ) {
                        firstOperationIsCreate.put(pid, false);
                    }
                    final ConfigurationManager.ConfigurationProvider last = lastOperations.get(pid);
                    lastOperations.put(pid, up);
                    if ( last != null ) {
                        if ( !firstOperationIsCreate.get(pid) ) {
                            firstOperationIsCreate.remove(pid);
                            result.remove(i);
                            i--;
                            lastOperations.remove(pid);
                        }
                        result.remove(last);
                        i--;
                    }
                }
            }

            final Iterator<Runnable> iter = result.iterator();
            while ( iter.hasNext() ) {
                final Runnable current = iter.next();
                if ( current instanceof ConfigurationManager.ManagedServiceUpdate ) {
                    final ConfigurationManager.ManagedServiceUpdate up = (ConfigurationManager.ManagedServiceUpdate)current;
                    final Iterator<String> pidIter = up.pids.iterator();
                    while ( pidIter.hasNext() ) {
                        final String pid = pidIter.next();
                        final ConfigurationManager.ConfigurationProvider last = lastOperations.get(pid);
                        if ( last != null ) {
                            pidIter.remove();
                        }
                    }
                    if ( up.pids.isEmpty() ) {
                        iter.remove();
                    }
                }
            }

            return result;
        }

        private void execute() {
            for(final Runnable r : compact()) {
                this.thread.schedule(r);
            }
        }

        @Override
        public void ended(Coordination coordination) throws Exception {
            execute();
        }

        @Override
        public void failed(Coordination coordination) throws Exception {
            execute();
        }

        public void add(final Runnable t) {
            runnables.add(t);
        }
    }

    public static boolean addToCoordination(final Object srv, final UpdateThread thread, final Runnable task) {
        final Coordinator coordinator = (Coordinator) srv;
        Coordination c = coordinator.peek();
        if ( c != null && !c.isTerminated() ) {
            Notifier n = null;
            for(final Participant p : c.getParticipants()) {
                if ( p instanceof Notifier ) {
                    n = (Notifier) p;
                    break;
                }
            }
            if ( n == null ) {
                n = new Notifier(thread);
                c.addParticipant(n);
            }
            n.add(task);
            return true;
        }
        return false;
    }
}

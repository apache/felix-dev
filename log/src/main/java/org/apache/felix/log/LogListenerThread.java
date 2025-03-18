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
package org.apache.felix.log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;

/**
 * This class is responsible for asynchronously delivering log messages to
 * any {@link LogListener} subscribers.  A subscriber can be added using the
 * {@link org.osgi.service.log.LogReaderService#addLogListener(LogListener)}
 * method.
 */
final class LogListenerThread extends Thread
{
    // The list of entries waiting to be delivered to the log listeners.
    private final List<LogEntry> m_entriesToDeliver = new ArrayList<>();
    // The list of listeners.
    private final List<LogListener> m_listeners = new ArrayList<>();

    LogListenerThread() {
        super("FelixLogListener");
    }

    /**
     * Add an entry to the list of messages to deliver.
     * @param entry the log entry to deliver
     */
    void addEntry(final LogEntry entry)
    {
        synchronized (m_entriesToDeliver)
        {
            m_entriesToDeliver.add(entry);
            m_entriesToDeliver.notifyAll();
        }
    }

    /**
     * Add a listener to the list of listeners that are subscribed.
     * @param listener the listener to add to the list of subscribed listeners
     */
    void addListener(final LogListener listener)
    {
        synchronized (m_listeners)
        {
            m_listeners.add(listener);
        }
    }

    /**
     * Remove a listener from the list of listeners that are subscribed.
     * @param listener the listener to remove from the list of subscribed listeners
     */
    void removeListener(final LogListener listener)
    {
        synchronized (m_listeners)
        {
            m_listeners.remove(listener);
        }
    }

    /**
     * Returns the number of listeners that are currently registered.
     * @return the number of listeners that are currently registered
     */
    int getListenerCount()
    {
        synchronized (m_listeners)
        {
            return m_listeners.size();
        }
    }

    /**
     * Stop the thread.  This will happen asynchronously.
     */
    void shutdown()
    {
        synchronized (m_entriesToDeliver)
        {
            interrupt();
        }
    }

    /**
     * The main method of the thread: waits for new messages to be receieved
     * and then delivers them to any registered log listeners.
     */
    @Override
    public void run()
    {
        while (!isInterrupted())
        {
            List<LogEntry> entriesToDeliver = new ArrayList<>();
            synchronized (m_entriesToDeliver)
            {
                if (m_entriesToDeliver.isEmpty())
                {
                    try
                    {
                        m_entriesToDeliver.wait();
                    }
                    catch (InterruptedException e)
                    {
                        // the interrupt-flag is cleared; so, let's play nice and
                        // interrupt this thread again to stop it...
                        interrupt();
                    }
                }
                else
                {
                    // Copy all current entries and deliver them in a single go...
                    entriesToDeliver.addAll(m_entriesToDeliver);
                    m_entriesToDeliver.clear();
                }
            }

            if (!entriesToDeliver.isEmpty())
            {
                // Take a snapshot of all current listeners and deliver all
                // pending messages to them...
                List<LogListener> listeners;
                synchronized (m_listeners)
                {
                    listeners = new ArrayList<>(m_listeners);
                }

                Iterator<LogEntry> entriesIt = entriesToDeliver.iterator();
                while (entriesIt.hasNext())
                {
                    LogEntry entry = entriesIt.next();

                    Iterator<LogListener> listenerIt = listeners.iterator();
                    while (listenerIt.hasNext())
                    {
                        LogListener listener = listenerIt.next();

                        try
                        {
                            listener.logged(entry);
                        }
                        catch (Throwable t)
                        {
                            System.err.println("Logger failed to log with " + t.getMessage());
                            t.printStackTrace(System.err);
                        }
                    }
                }
            }
        }
    }
}
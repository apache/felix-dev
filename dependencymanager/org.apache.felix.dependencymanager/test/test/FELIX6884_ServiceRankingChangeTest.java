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
package test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.felix.dm.context.ComponentContext;
import org.apache.felix.dm.impl.ComponentImpl;
import org.apache.felix.dm.impl.ServiceEventImpl;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

/**
 * Checks that a required dependency becomes unavailable once all its services are gone, even if
 * one of them has changed its service.ranking while it was tracked.
 */
public class FELIX6884_ServiceRankingChangeTest {

    /**
     * A service reference whose ranking can change, ordered like a real framework reference:
     * higher ranking first, then lower service.id first.
     */
    @SuppressWarnings("rawtypes")
    static class RankedReference implements ServiceReference {
        final long m_id;
        volatile int m_ranking;

        RankedReference(long id, int ranking) {
            m_id = id;
            m_ranking = ranking;
        }

        @Override
        public Object getProperty(String key) {
            if (Constants.SERVICE_ID.equals(key)) {
                return m_id;
            }
            if (Constants.SERVICE_RANKING.equals(key)) {
                return m_ranking;
            }
            return null;
        }

        @Override
        public String[] getPropertyKeys() {
            return new String[] { Constants.SERVICE_ID, Constants.SERVICE_RANKING };
        }

        @Override
        public Bundle getBundle() {
            return null;
        }

        @Override
        public Bundle[] getUsingBundles() {
            return null;
        }

        @Override
        public boolean isAssignableTo(Bundle bundle, String className) {
            return false;
        }

        @Override
        public Object adapt(Class type) {
            return null;
        }

        @Override
        public java.util.Dictionary getProperties() {
            return null;
        }

        @Override
        public int compareTo(Object other) {
            RankedReference o = (RankedReference) other;
            if (m_ranking != o.m_ranking) {
                return m_ranking < o.m_ranking ? -1 : 1;
            }
            if (m_id == o.m_id) {
                return 0;
            }
            return m_id < o.m_id ? 1 : -1;
        }

        @Override
        public String toString() {
            return "ref[id=" + m_id + ", ranking=" + m_ranking + "]";
        }
    }

    @Test
    public void requiredDependencyIsUnavailableAfterRankingChangeAndRemoval() {
        BundleContext bc = mock(BundleContext.class);
        ComponentContext ctx = mock(ComponentContext.class);
        when(ctx.getBundleContext()).thenReturn(bc);
        when(bc.getService(any(ServiceReference.class))).thenReturn(new Object());

        SimpleServiceDependency d = new SimpleServiceDependency();
        d.setRequired(true);
        ComponentImpl c = new ComponentImpl();
        c.setImplementation(new Object());
        c.add(d);
        c.start();

        RankedReference refA = new RankedReference(1, 0);
        RankedReference refB = new RankedReference(2, 0);
        ServiceEventImpl a = new ServiceEventImpl(ctx, refA, new Object());
        ServiceEventImpl b = new ServiceEventImpl(ctx, refB, new Object());

        d.add(a);
        d.add(b);
        Assert.assertTrue("dependency should be available with two services", d.isAvailable());

        // A's ranking changes: the tracker hands over the same event object.
        refA.m_ranking = -10;
        d.change(a);
        Assert.assertTrue("dependency should still be available", d.isAvailable());

        // Both services go away.
        d.remove(a);
        d.remove(b);

        Assert.assertFalse("no service left: the dependency must be unavailable", d.isAvailable());
        Assert.assertFalse("no service left: the component must not be available", c.isAvailable());
        c.stop();
    }
}

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.felix.dm.impl.ComponentImpl;
import org.junit.Assert;
import org.junit.Test;

/**
 * Checks that auto-configured Collection/Map fields do not keep stale entries when the component
 * instance survives the loss of a required dependency (because another dependency needs the instance).
 */
public class FELIX6885_StaleInjectedCollectionTest {

    /** A dependency which requires the component instance to be kept while waiting for required dependencies. */
    static class NeedsInstanceDependency extends SimpleServiceDependency {
        @Override
        public boolean needsInstance() {
            return true;
        }
    }

    /** A required dependency auto-configured into a Collection or Map field of type String. */
    static class StringDependency extends SimpleServiceDependency {
        @Override
        public Class<?> getAutoConfigType() {
            return String.class;
        }
    }

    static class ListImpl {
        volatile Collection<String> foos;
        final List<Collection<String>> snapshots = new ArrayList<>();

        void start() {
            snapshots.add(new TreeSet<>(foos));
        }
    }

    static class MapImpl {
        volatile Map<String, Dictionary<?, ?>> foos;
        final List<Collection<String>> snapshots = new ArrayList<>();

        void start() {
            snapshots.add(new TreeSet<>(foos.keySet()));
        }
    }

    @Test
    public void collectionDoesNotKeepRemovedServices() {
        ListImpl impl = new ListImpl();
        impl.foos = new ArrayList<>();
        NeedsInstanceDependency config = new NeedsInstanceDependency();
        config.setRequired(true);
        StringDependency foo = new StringDependency();
        foo.setRequired(true);

        ComponentImpl c = new ComponentImpl();
        c.setImplementation(impl).add(config).add(foo);
        c.start();
        config.add(new EventImpl(1));

        EventImpl foo1 = new EventImpl(1, "Foo1");
        EventImpl foo2 = new EventImpl(2, "Foo2");
        foo.add(foo1);
        foo.remove(foo1);
        foo.add(foo2);
        c.stop();

        Assert.assertEquals(2, impl.snapshots.size());
        Assert.assertEquals(new TreeSet<>(List.of("Foo1")), impl.snapshots.get(0));
        Assert.assertEquals("removed service must not be injected anymore",
            new TreeSet<>(List.of("Foo2")), impl.snapshots.get(1));
    }

    @Test
    public void mapDoesNotKeepRemovedServices() {
        MapImpl impl = new MapImpl();
        impl.foos = new ConcurrentHashMap<>();
        NeedsInstanceDependency config = new NeedsInstanceDependency();
        config.setRequired(true);
        StringDependency foo = new StringDependency();
        foo.setRequired(true);

        ComponentImpl c = new ComponentImpl();
        c.setImplementation(impl).add(config).add(foo);
        c.start();
        config.add(new EventImpl(1));

        EventImpl foo1 = new EventImpl(1, "Foo1");
        EventImpl foo2 = new EventImpl(2, "Foo2");
        foo.add(foo1);
        foo.remove(foo1);
        foo.add(foo2);
        c.stop();

        Assert.assertEquals(2, impl.snapshots.size());
        Assert.assertEquals(new TreeSet<>(List.of("Foo1")), impl.snapshots.get(0));
        Assert.assertEquals("removed service must not be injected anymore",
            new TreeSet<>(List.of("Foo2")), impl.snapshots.get(1));
    }

    static class DuplicateCheckImpl {
        volatile Collection<String> foos;
        final List<List<String>> snapshots = new ArrayList<>();

        void start() {
            snapshots.add(new ArrayList<>(foos));
        }
    }

    @Test
    public void collectionHasNoDuplicatesWhenAnotherRequiredDependencyComesBack() {
        DuplicateCheckImpl impl = new DuplicateCheckImpl();
        impl.foos = new ArrayList<>();
        NeedsInstanceDependency config = new NeedsInstanceDependency();
        config.setRequired(true);
        SimpleServiceDependency other = new SimpleServiceDependency();
        other.setRequired(true);
        StringDependency foo = new StringDependency();
        foo.setRequired(true);

        ComponentImpl c = new ComponentImpl();
        c.setImplementation(impl).add(config).add(other).add(foo);
        c.start();
        config.add(new EventImpl(1));
        foo.add(new EventImpl(1, "Foo1"));

        EventImpl otherEvent = new EventImpl(1);
        other.add(otherEvent);
        other.remove(otherEvent);
        other.add(otherEvent);
        c.stop();

        Assert.assertEquals(2, impl.snapshots.size());
        Assert.assertEquals(List.of("Foo1"), impl.snapshots.get(0));
        Assert.assertEquals("service must be injected only once", List.of("Foo1"), impl.snapshots.get(1));
    }
}

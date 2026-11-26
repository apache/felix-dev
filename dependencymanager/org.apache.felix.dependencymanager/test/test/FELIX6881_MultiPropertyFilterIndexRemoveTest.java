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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.felix.dm.impl.index.multiproperty.MultiPropertyFilterIndex;
import org.junit.Test;
import org.osgi.framework.ServiceReference;

/**
 * Removing a service from a MultiPropertyFilterIndex used to throw a
 * ClassCastException ((ArrayList) reference) when a key was shared with other references,
 * leaving stale references in the index.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class FELIX6881_MultiPropertyFilterIndexRemoveTest {

	@Test
	public void removeServiceWithSharedKeysAndMultiValuedObjectClass() {
		MultiPropertyFilterIndex index = new MultiPropertyFilterIndex("objectClass,id");

		// r1 registered under {Bar, Foo} with id=x
		TestReference r1 = new TestReference();
		r1.addProperty("service.id", 1);
		r1.addProperty("objectclass", new String[] { "Bar", "Foo" });
		r1.addProperty("id", "x");

		// r2 registered under {Bar}, r3 under {Foo}, both with id=x so that keys are shared with r1
		TestReference r2 = new TestReference();
		r2.addProperty("service.id", 2);
		r2.addProperty("objectclass", new String[] { "Bar" });
		r2.addProperty("id", "x");

		TestReference r3 = new TestReference();
		r3.addProperty("service.id", 3);
		r3.addProperty("objectclass", new String[] { "Foo" });
		r3.addProperty("id", "x");

		index.addedService(r1, new Object());
		index.addedService(r2, new Object());
		index.addedService(r3, new Object());

		assertEquals(2, index.getAllServiceReferences("Foo", "(id=x)").size());
		assertEquals(2, index.getAllServiceReferences("Bar", "(id=x)").size());

		// Must not throw ClassCastException
		index.removedService(r1, new Object());

		List<ServiceReference> foo = index.getAllServiceReferences("Foo", "(id=x)");
		List<ServiceReference> bar = index.getAllServiceReferences("Bar", "(id=x)");
		assertEquals("Foo: " + foo, 1, foo.size());
		assertTrue(foo.contains(r3));
		assertEquals("Bar: " + bar, 1, bar.size());
		assertTrue(bar.contains(r2));
	}
}

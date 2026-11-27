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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.felix.dm.FilterIndex;
import org.apache.felix.dm.impl.index.AdapterFilterIndex;
import org.apache.felix.dm.impl.index.AspectFilterIndex;
import org.apache.felix.dm.impl.index.multiproperty.MultiPropertyFilterIndex;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;

/**
 * Filter indices only manipulate service references: they must never call
 * {@link BundleContext#getService(ServiceReference)}, otherwise lazy services (SCR delayed
 * components, service factories) get activated just because an index tracks them.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class FELIX6891_FilterIndexNoGetServiceTest {

	@Test
	public void multiPropertyIndexDoesNotGetServices() throws Exception {
		assertNoGetService(new MultiPropertyFilterIndex("objectClass"));
	}

	@Test
	public void aspectIndexDoesNotGetServices() throws Exception {
		assertNoGetService(new AspectFilterIndex());
	}

	@Test
	public void adapterIndexDoesNotGetServices() throws Exception {
		assertNoGetService(new AdapterFilterIndex());
	}

	private void assertNoGetService(FilterIndex index) throws Exception {
		TestReference ref = new TestReference();
		ref.addProperty("service.id", 4711);
		ref.addProperty("objectclass", new String[] { "java.lang.String" });

		BundleContext context = mock(BundleContext.class);
		when(context.createFilter(anyString())).thenReturn(mock(Filter.class));
		when(context.getServiceReferences(any(String.class), any())).thenReturn(new ServiceReference[] { ref });
		when(context.getAllServiceReferences(any(), any())).thenReturn(new ServiceReference[] { ref });

		index.open(context);
		try {
			verify(context, never()).getService(any(ServiceReference.class));
		} finally {
			index.close();
		}
	}
}

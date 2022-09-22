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
/*
 * Note that this test is copied from the OSGi TCK https://github.com/osgi/osgi
 * with permission of the author Stefan Bischof.
 */
package org.apache.felix.cm.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.coordinator.Coordination;
import org.osgi.service.coordinator.Coordinator;

@RunWith(JUnit4TestRunner.class)
public class CoordinatorIntegrationTest extends ConfigurationTestBase {

    static
    {
        // uncomment to enable debugging of this test class
        paxRunnerVmOption = DEBUG_VM_OPTION;
    }

	@Override
	protected Option[] additionalConfiguration() {
		return new Option [] {
			mavenBundle("org.eclipse.platform", "org.eclipse.equinox.supplement", "1.9.300"),
			mavenBundle("org.eclipse.platform", "org.eclipse.equinox.coordinator", "1.3.500")
		};
	}

	<T> T getService(Class<T> cls) {
		ServiceReference<T> sref = bundleContext.getServiceReference(cls);
		if (sref == null)
			return null;

		return bundleContext.getService(sref);
	}

	void sleep() throws InterruptedException {
		TimeUnit.MILLISECONDS.sleep(4000);
	}

	@Test
	public void test_deliver_existing_Configuration_to_later_registered_ManagedService()
			throws Exception {
		ConfigurationAdmin cm = getService(ConfigurationAdmin.class);
		Coordinator c = getService(Coordinator.class);
		Coordination coord = c.begin("cm-test1", 0);
		final List<Boolean> events = new ArrayList<>();

		String pid = getClass().getName() + ".mstestpid2";

		try {
			// create the configuration
			Dictionary<String,Object> props = new Hashtable<>();
			props.put("key", "value");

			Configuration conf = cm.getConfiguration(pid);
			conf.update(props);

			// add managed service
			Dictionary<String,Object> msProps = new Hashtable<>();
			msProps.put(Constants.SERVICE_PID, pid);

			bundleContext.registerService(ManagedService.class.getName(),
					new ManagedService() {

						@Override
						public void updated(Dictionary<String, ? > properties)
								throws ConfigurationException {
							System.out.println(properties);
							events.add(properties != null);
						}

					}, msProps);

			sleep();
			assertEquals(0, events.size());

			// update configuration
			props.put("key2", "value2");
			conf.update(props);

			sleep();
			assertEquals(0, events.size());

			// delete configuration
			conf.delete();

			sleep();
			assertEquals(0, events.size());
		} finally {
			coord.end();
		}

		// wait and verify listener
		sleep();

		for (Boolean event : events) {
			System.out.println(event);
		}
		assertEquals(3, events.size());
		assertTrue(events.get(0));// we have a Configuration -> not null
		assertTrue(events.get(1));// we update the Configuration -> not null
		assertFalse(events.get(2));// we remove the configuration -> null
	}
}

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
package org.apache.felix.dm.index.itest.refresh.bundle;

import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;

/**
 * Creates a component with a service dependency on the Runnable services having the
 * "dm.refresh.test" property. The dependency filter only uses objectClass and this property, so it
 * goes through the "objectClass,dm.refresh.test" index configured in the test.bndrun of the refresh
 * tests (with the "org.apache.felix.dependencymanager.filterindex" property).
 *
 * The service type comes from the JDK, so that the test bundle doesn't need to import any package
 * of this bundle (which is refreshed with the DependencyManager bundle). The property is needed
 * because other bundles register Runnable services (the bnd tester registers itself as a Runnable,
 * to run the tests).
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class Activator extends DependencyActivatorBase {

	@Override
	public void init(BundleContext context, DependencyManager dm) throws Exception {
		dm.add(createComponent()
			.setImplementation(RunnableConsumer.class)
			.add(createServiceDependency()
				.setService(Runnable.class, "(dm.refresh.test=true)")
				.setRequired(false)
				.setCallbacks("add", "change", null)));
	}

}

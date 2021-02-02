/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.felix.useradmin.osgi;

import org.apache.felix.useradmin.RoleRepositoryStore;
import org.apache.felix.useradmin.impl.EventDispatcher;
import org.apache.felix.useradmin.impl.RoleRepository;
import org.apache.felix.useradmin.impl.UserAdminImpl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.useradmin.UserAdmin;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Provides an OSGi service tracker for {@link RoleRepositoryStore}.
 */
class RoleRepositoryStoreHelper implements ServiceTrackerCustomizer {

	private final EventDispatcher m_eventDispatcher;
	private final BundleContext m_bundleContext;
	
	private ServiceRegistration m_userAdminRegistration = null;
	
	RoleRepositoryStoreHelper(EventDispatcher eventDispatcher, BundleContext bundleContext) {
		this.m_eventDispatcher = eventDispatcher;
		this.m_bundleContext = bundleContext;
	}
	
	@Override
	public Object addingService(ServiceReference reference) {
		
		if (m_userAdminRegistration != null) {
			// consider only first tracked store 
			return null;
		}
		
		final RoleRepositoryStore store = (RoleRepositoryStore) m_bundleContext.getService(reference);
		
		// The actual service itself...
        UserAdminImpl service = new UserAdminImpl(new RoleRepository(store), this.m_eventDispatcher);
		
		m_userAdminRegistration = m_bundleContext.registerService(UserAdmin.class.getName(), service, null);
		
		return store;
	}

	@Override
	public void modifiedService(ServiceReference reference, Object service) {
		// do nothing
	}

	@Override
	public void removedService(ServiceReference reference, Object service) {
		m_userAdminRegistration.unregister();
		m_userAdminRegistration = null;
		
		m_bundleContext.ungetService(reference);
	}
	
     
}

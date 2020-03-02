/*
 * Copyright (c) OSGi Alliance (2019). All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.osgi.framework.connect;

import java.io.File;
import java.util.Map;
import java.util.Optional;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;

/**
 * A <code>ModuleConnector</code> provides connections to instances of
 * {@link ConnectModule} that are used by a {@link Framework} instance to
 * connect installed bundles locations with content provided by the
 * <code>ModuleConnector</code>. This allows a <code>ModuleConnector</code> to
 * provide content and classes for a connected bundle installed in the
 * <code>Framework</code>. A <code>ModuleConnector</code> is provided when
 * {@link ConnectFrameworkFactory#newFramework(java.util.Map, ModuleConnector)
 * creating} a framework instance. Because a <code>ModuleConnector</code>
 * instance can participate in the initialization of the <code>Framework</code>
 * and the life cycle of a <code>Framework</code> instance the
 * <code>ModuleConnector</code> instance should only be used with a single
 * <code>Framework</code> instance at a time.
 * 
 * @ThreadSafe
 * @author $Id: 3f9a112c56a213eb81baf1fe568ce4f0ecdd1321 $
 */
public interface ModuleConnector {

	/**
	 * Initializes this module connector with the
	 * {@link Constants#FRAMEWORK_STORAGE framework persistent storage} file and
	 * framework properties configured for a {@link Framework} instance. This
	 * method is called once by a {@link Framework} instance and is called
	 * before any other methods on this module connector are called.
	 * 
	 * @param configuration The framework properties used configure the new
	 *            framework instance. An unmodifiable map of framework
	 *            configuration properties that were used to create a new
	 *            framework instance.
	 * @param storage the persistent storage area used by the {@link Framework}
	 *            or {@code null} if the platform does not have file system
	 *            support.
	 */
	void initialize(File storage, Map<String,String> configuration);

	/**
	 * Connects a bundle location with a {@link ConnectModule}. If an
	 * {@link Optional#empty() empty} optional is returned the the framework
	 * must handle reading the content of the bundle itself. If a value is
	 * {@link Optional#isPresent() present} in the returned optional then the
	 * <code>ConnectModule</code> {@link Optional#get() value} from the optional
	 * must be used to connect the bundle to the returned {@link ConnectModule}.
	 * The returned connect module is used by the framework to access the
	 * content of the bundle.
	 * 
	 * @param location the bundle location used to install a bundle
	 * @return the connect module for the specified bundle location
	 * @throws BundleException if the location cannot be handled
	 */
	Optional<ConnectModule> connect(String location) throws BundleException;

	/**
	 * Creates a new activator for this module connector. A new activator is
	 * created by the framework each time the framework is
	 * {@link Framework#init() initialized}. An activator allows the module
	 * connector to participate in the framework life cycle. When the framework
	 * is {@link Framework#init() initialized} the activator
	 * {@link BundleActivator#start(org.osgi.framework.BundleContext) start}
	 * method is called. When the framework is {@link Framework#stop() stopped}
	 * the activator
	 * {@link BundleActivator#stop(org.osgi.framework.BundleContext) stop}
	 * method is called
	 * 
	 * @return a new activator for this module connector or
	 *         {@link Optional#empty() empty} if no activator is available
	 */
	Optional<BundleActivator> createBundleActivator();
}

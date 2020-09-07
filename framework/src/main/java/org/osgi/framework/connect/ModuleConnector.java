/*
 * Copyright (c) OSGi Alliance (2019, 2020). All Rights Reserved.
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

import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.launch.Framework;

/**
 * A {@code ModuleConnector} provides connections to instances of
 * {@link ConnectModule} that are used by a {@link Framework} instance to
 * connect installed bundles locations with content provided by the
 * {@code ModuleConnector}.
 * <p>
 * This allows a {@code ModuleConnector} to provide content and classes for a
 * connected bundle installed in the {@code Framework}. A
 * {@code ModuleConnector} is provided when
 * {@link ConnectFrameworkFactory#newFramework(Map, ModuleConnector) creating} a
 * framework instance. Because a {@code ModuleConnector} instance can
 * participate in the initialization of the {@code Framework} and the life cycle
 * of a {@code Framework} instance the {@code ModuleConnector} instance should
 * only be used with a single {@code Framework} instance at a time.
 * 
 * @ThreadSafe
 * @author $Id: 5ee358acfec177e4bf92994fee8e61c9c841ec06 $
 */
@ConsumerType
public interface ModuleConnector {

	/**
	 * Initializes this {@code ModuleConnector} with the
	 * {@link Constants#FRAMEWORK_STORAGE framework persistent storage} file and
	 * framework properties configured for a {@link Framework} instance.
	 * <p>
	 * This method is called once by a {@link Framework} instance and is called
	 * before any other methods on this module connector are called.
	 * 
	 * @param storage The persistent storage area used by the {@link Framework}
	 *            or {@code null} if the platform does not have file system
	 *            support.
	 * @param configuration An unmodifiable map of framework configuration
	 *            properties that were used to configure the new framework
	 *            instance.
	 */
	void initialize(File storage, Map<String,String> configuration);

	/**
	 * Connects a bundle location with a {@link ConnectModule}.
	 * <p>
	 * When the result is empty, then the framework must handle reading the
	 * content of the bundle itself. Otherwise, the returned
	 * {@link ConnectModule} must be used by the framework to access the content
	 * of the bundle.
	 * 
	 * @param location The bundle location used to install a bundle.
	 * @return An {@code Optional} containing the {@link ConnectModule} for the
	 *         specified bundle location, or an empty {@code Optional} if the
	 *         framework must handle reading the content of the bundle itself.
	 * @throws BundleException If the location cannot be handled.
	 */
	Optional<ConnectModule> connect(String location) throws BundleException;

	/**
	 * Creates a new activator for this {@code ModuleConnector}.
	 * <p>
	 * This method is called by the framework during framework
	 * {@link Framework#init(FrameworkListener...) initialization}. Returning an
	 * activator allows this {@code ModuleConnector} to participate in the
	 * framework life cycle. If an activator is returned:
	 * <ul>
	 * <li>The framework will call the activator's
	 * {@link BundleActivator#start(BundleContext) start} method prior to
	 * activating any extension bundles.</li>
	 * <li>The framework will call the activator's
	 * {@link BundleActivator#stop(BundleContext) stop} method after
	 * deactivating any extension bundles.</li>
	 * </ul>
	 * 
	 * @return An {@code Optional} containing a new {@link BundleActivator} for
	 *         this {@code ModuleConnector}, or an empty {@code Optional} if no
	 *         {@link BundleActivator} is necessary.
	 */
	Optional<BundleActivator> newBundleActivator();
}

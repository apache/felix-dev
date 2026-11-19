/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *******************************************************************************/

package org.osgi.framework.hooks.delegation;

import java.net.URL;
import java.util.Collection;

import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.framework.wiring.BundleWiring;

/**
 * OSGi Framework Delegation Hook Service.
 *
 * <p>
 * Bundles registering this service are called by the framework as the last step
 * of the search order of a bundle class loader, to contribute classes and
 * resources the search order did not provide. A hook can only add; it cannot
 * replace or hide what the wiring of the asking bundle provides.
 * </p>
 *
 * <p>
 * The framework does not call a hook for classes and resources in
 * {@code java.*} packages, nor for packages the asking bundle imports,
 * requires or exports.
 * </p>
 *
 * <p>
 * Hooks are called in service ranking order. The framework must not hold any
 * locks while calling a hook, and must not call a hook for the same wiring and
 * name while the hook is already running on the same thread. If a hook throws
 * an exception, the framework publishes a
 * {@link org.osgi.framework.FrameworkEvent#ERROR} and continues as if the hook
 * had returned nothing.
 * </p>
 *
 * <p>
 * If a class returned by {@link #findClass(BundleWiring, String)} is defined by
 * the class loader of another bundle, the asking bundle depends on that bundle
 * for the purposes of refreshing, like it does for a dynamic import.
 * </p>
 *
 * @ThreadSafe
 * @since 1.0
 */
@ConsumerType
public interface DelegationHook {

	/**
	 * Find a class the search order of the asking bundle did not find.
	 *
	 * <p>
	 * This method is only called if the search order found no class.
	 * </p>
	 *
	 * @param wiring The wiring of the asking bundle. Not {@code null}.
	 * @param name The binary name of the class. Not {@code null}.
	 * @return The class, or {@code null} if this hook does not provide it. The
	 *         class may be defined by any class loader.
	 * @throws ClassNotFoundException If the hook knows the class but cannot load
	 *             it.
	 */
	Class<?> findClass(BundleWiring wiring, String name) throws ClassNotFoundException;

	/**
	 * Find a resource the search order of the asking bundle did not find.
	 *
	 * <p>
	 * This method is only called if the search order found no resource.
	 * </p>
	 *
	 * @param wiring The wiring of the asking bundle. Not {@code null}.
	 * @param name The resource name. Not {@code null}.
	 * @return The URL of the resource, or {@code null} if this hook does not
	 *         provide it.
	 */
	URL findResource(BundleWiring wiring, String name);

	/**
	 * Find resources to add to those the search order of the asking bundle
	 * found.
	 *
	 * <p>
	 * This method is called whether or not the search order found resources.
	 * The returned URLs are appended after those the search order found, and
	 * after those of hooks earlier in the call order.
	 * </p>
	 *
	 * @param wiring The wiring of the asking bundle. Not {@code null}.
	 * @param name The resource name. Not {@code null}.
	 * @return The URLs of the resources to add. May be {@code null} or empty.
	 */
	Collection<URL> findResources(BundleWiring wiring, String name);
}

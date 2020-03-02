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

import java.util.Map;

import org.osgi.annotation.versioning.ProviderType;
import org.osgi.framework.Bundle;
import org.osgi.framework.launch.Framework;

/**
 * A factory for creating {@link Framework} instances.
 * <p>
 * If a framework supports {@link ModuleConnector} then the implementation jar
 * must contain the following resource:
 * 
 * <pre>
 * /META-INF/services/org.osgi.framework.connect.ConnectFrameworkFactory
 * </pre>
 * 
 * This UTF-8 encoded resource must contain the name of the framework
 * implementation's ConnectFrameworkFactory implementation class. Space and tab
 * characters, including blank lines, in the resource must be ignored. The
 * number sign ({@code '#'} &#92;u0023) and all characters following it on each
 * line are a comment and must be ignored.
 * <p>
 * Launchers can find the name of the ConnectFrameworkFactory implementation
 * class in the resource and then load and construct a ConnectFrameworkFactory
 * object for the framework implementation. The ConnectFrameworkFactory
 * implementation class must have a public, no-argument constructor. Java&#8482;
 * SE 6 introduced the {@code ServiceLoader} class which can create a
 * ConnectFrameworkFactory instance from the resource.
 * 
 * @ThreadSafe
 * @author $Id: c1193dbc989c5cc0840f0b6a66a229b95d6fbc4e $
 */
@ProviderType
public interface ConnectFrameworkFactory {
	/**
	 * Create a new {@link Framework} instance using the specified
	 * {@link ModuleConnector module connector}.
	 * 
	 * @param configuration The framework properties to configure the new
	 *            framework instance. If framework properties are not provided
	 *            by the configuration argument, the created framework instance
	 *            must use some reasonable default configuration appropriate for
	 *            the current VM. For example, the system packages for the
	 *            current execution environment should be properly exported. The
	 *            specified configuration argument may be {@code null}. The
	 *            created framework instance must copy any information needed
	 *            from the specified configuration argument since the
	 *            configuration argument can be changed after the framework
	 *            instance has been created.
	 * @param moduleConnector The module connector that the new framework
	 *            instance will use. The specified module connector argument may
	 *            be {@code null}.
	 * @return A new, configured {@link Framework} instance. The framework
	 *         instance must be in the {@link Bundle#INSTALLED} state.
	 * @throws SecurityException If the caller does not have
	 *             {@code AllPermission}, and the Java Runtime Environment
	 *             supports permissions.
	 * @see ModuleConnector
	 * @since 1.3
	 */
	Framework newFramework(Map<String,String> configuration,
			ModuleConnector moduleConnector);
}

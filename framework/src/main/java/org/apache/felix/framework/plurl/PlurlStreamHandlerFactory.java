/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.apache.felix.framework.plurl;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * A {@link URLStreamHandlerFactory} that also implements {@link PlurlFactory}
 */
public interface PlurlStreamHandlerFactory extends URLStreamHandlerFactory, PlurlFactory {

	/**
	 * A factory is expected to return {@link URLStreamHandler} instances that also
	 * implement {@link PlurlStreamHandler}. If the returned handler does not
	 * implement {@link PlurlStreamHandler} then deep reflection is required and the
	 * JVM may require the "--add-opens" option in order to open the "java.net"
	 * package for reflection. For example:
	 * 
	 * <pre>
	 * --add-opens java.base/java.net=ALL-UNNAMED
	 * </pre>
	 * 
	 * @see URLStreamHandlerFactory#createURLStreamHandler(String)
	 */
	@Override
	URLStreamHandler createURLStreamHandler(String protocol);
}

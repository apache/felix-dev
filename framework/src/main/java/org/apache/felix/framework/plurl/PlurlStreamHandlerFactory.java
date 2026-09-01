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

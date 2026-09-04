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

	/**
	 * Returns true if this factory should handle the URL being parsed from the given
	 * spec. This is consulted before the call stack is examined, and lets a factory
	 * claim a URL that only it can own.
	 * <p>
	 * Several parties may share one protocol and be distinguishable only by the URL
	 * itself, for example multiple instances of the same framework where the owner is
	 * identified by an id in the URL host. Such a URL may also be used by a caller
	 * that no factory recognises from the call stack, leaving nothing else to select
	 * on.
	 * <p>
	 * The spec is used rather than a {@code URL}, because selection happens while the
	 * URL is still being parsed: its host and path are not populated yet, and the
	 * handler is pinned to the URL as soon as parsing begins. Implementations must not
	 * call back into URL handling, so this method is given only strings.
	 * <p>
	 * The spec may be relative and carry neither protocol nor host, in which case a
	 * factory that selects on the URL has nothing to decide with and should return
	 * false; a relative URL resolved against a context URL keeps the context's handler
	 * and does not reach this method.
	 *
	 * @param protocol the protocol of the URL being parsed
	 * @param spec     the spec the URL is being parsed from, which may be relative
	 * @return true if this factory should handle the URL
	 * @see Plurl#PLURL_CAPABILITY_SELECT_BY_SPEC
	 */
	default boolean shouldHandleURL(String protocol, String spec) {
		return false;
	}
}

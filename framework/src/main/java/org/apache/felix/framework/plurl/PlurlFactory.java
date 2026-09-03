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

/**
 * A plural factory that can be added to a plurl implementation. A plurl
 * implementation uses {@code PlurlFactory} objects to locate a factory to
 * provider a handler.
 * 
 * @see Plurl#add(PlurlContentHandlerFactory)
 * @see Plurl#add(PlurlStreamHandlerFactory)
 */
public interface PlurlFactory {
	/**
	 * Returns true if this factory should handle the given URL. This is consulted
	 * before the call stack is examined, and lets a factory claim a URL that only it
	 * can own, for cases where no class on the call stack identifies the owner. For
	 * example several instances of the same framework may share a protocol and be
	 * distinguished only by information in the URL itself.
	 * <p>
	 * This is only consulted for URLs that carry enough information to decide, that
	 * is once the URL has been parsed. A factory that cannot tell from the URL alone
	 * must return false so that call stack selection is used instead.
	 *
	 * @param url the URL a handler is required for
	 * @return true if this factory should handle the URL
	 */
	default boolean shouldHandle(java.net.URL url) {
		return false;
	}

	/**
	 * A plurl implementation will call this method with the classes in the call
	 * stack which are using the java.net APIs to create URL objects for a specific
	 * type. For example, a protocol or content type.
	 * 
	 * @param clazz a class in the call stack using the java.net APIs
	 * @return true if this factory should be used to handle the request
	 */
	boolean shouldHandle(Class<?> clazz);
}

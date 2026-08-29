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
	 * A plurl implementation will call this method with the classes in the call
	 * stack which are using the java.net APIs to create URL objects for a specific
	 * type. For example, a protocol or content type.
	 * 
	 * @param clazz a class in the call stack using the java.net APIs
	 * @return true if this factory should be used to handle the request
	 */
	boolean shouldHandle(Class<?> clazz);
}

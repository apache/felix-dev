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

package org.osgi.service.servlet.runtime;

/**
 * Defines standard names for Http Runtime Service constants.
 * 
 * @author $Id: 88307ef555f5ca25bee6dc678ca40a7d6c5ee254 $
 */
public final class HttpServiceRuntimeConstants {
	private HttpServiceRuntimeConstants() {
		// non-instantiable
	}

	/**
	 * Http Runtime Service service property specifying the endpoints upon which
	 * the Servlet Whiteboard implementation is listening.
	 * <p>
	 * An endpoint value is a URL or a relative path, to which the Servlet
	 * Whiteboard implementation is listening. For example,
	 * {@code http://192.168.1.10:8080/} or {@code /myapp/}. A relative path may
	 * be used if the scheme and authority parts of the URL are not known, e.g.
	 * in a bridged Servlet Whiteboard implementation. If the Servlet Whiteboard
	 * implementation is serving the root context and neither scheme nor
	 * authority is known, the value of the property is "/". Both, a URL and a
	 * relative path, must end with a slash.
	 * <p>
	 * An Servlet Whiteboard implementation can be listening on multiple
	 * endpoints.
	 * <p>
	 * The value of this service property must be of type {@code String},
	 * {@code String[]}, or {@code Collection<String>}.
	 */
	public static final String	HTTP_SERVICE_ENDPOINT	= "osgi.http.endpoint";
}

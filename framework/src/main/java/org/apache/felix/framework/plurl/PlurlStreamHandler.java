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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

/**
 * The {@code PlurlStreamHandler} interface has public versions of the protected
 * {@link java.net.URLStreamHandler} methods.
 * <p>
 * The important differences between this interface and the
 * {@code URLStreamHandler} class are that the {@code setURL} method is absent
 * and the {@code parseURL} method takes a {@link PlurlSetter} object as the
 * first argument. Classes implementing this interface must call the
 * {@code setURL} method on the {@code PlurlSetter} object received in the
 * {@code parseURL} method instead of {@code URLStreamHandler.setURL} to avoid a
 * {@code SecurityException}.
 * 
 * @see PlurlStreamHandlerBase
 * 
 */
public interface PlurlStreamHandler {
	/**
	 * Interface used by {@code PlurlStreamHandler} objects to call the
	 * {@code setURL} method on the plurl proxy {@code URLStreamHandler} object.
	 * 
	 * <p>
	 * Objects of this type are passed to the
	 * {@link PlurlStreamHandler#parseURL(PlurlSetter, URL, String, int, int)}
	 * method. Invoking the {@code setURL} method on the
	 * {@code URLStreamHandlerSetter} object will invoke the {@code setURL} method
	 * on the plurl proxy {@code URLStreamHandler} object that is actually
	 * registered with {@code java.net.URL} for the protocol.
	 * 
	 */
	public interface PlurlSetter {
		/**
		 * @see "java.net.URLStreamHandler.setURL(URL,String,String,int,String,String,String,String)"
		 */
		public void setURL(URL u, String protocol, String host, int port, String authority, String userInfo,
				String path, String query, String ref);
	}

	/**
	 * @see "java.net.URLStreamHandler.equals(URL, URL)"
	 */
	public boolean equals(URL u1, URL u2);

	/**
	 * @see "java.net.URLStreamHandler.hashCode(URL)"
	 */
	public int hashCode(URL u);

	/**
	 * @see "java.net.URLStreamHandler.hostsEqual(URL, URL)"
	 */
	public boolean hostsEqual(URL u1, URL u2);

	/**
	 * @see "java.net.URLStreamHandler.getDefaultPort"
	 */
	public int getDefaultPort();

	/**
	 * @see "java.net.URLStreamHandler.getHostAddress(URL)"
	 */
	public InetAddress getHostAddress(URL u);

	/**
	 * @see "java.net.URLStreamHandler.openConnection(URL)"
	 */
	public URLConnection openConnection(URL u) throws IOException;

	/**
	 * @see "java.net.URLStreamHandler.openConnection(URL, Proxy)"
	 */
	public URLConnection openConnection(URL u, Proxy p) throws IOException;

	/**
	 * @see "java.net.URLStreamHandler.sameFile(URL, URL)"
	 */
	public boolean sameFile(URL u1, URL u2);

	/**
	 * @see "java.net.URLStreamHandler.toExternalForm(URL)"
	 */
	public String toExternalForm(URL u);

	/**
	 * Parse a URL. This method is called by the {@code URLStreamHandler} proxy
	 * implemented by plurl, instead of {@code java.net.URLStreamHandler.parseURL},
	 * passing a {@code PlurlSetter} object.
	 * 
	 * @param plurlSetter The object on which {@code setURL} must be invoked for
	 *                    this URL. If the setter is {@code null} then the
	 *                    {@link PlurlStreamHandler#setURL(URL, String, String, int, String, String, String, String, String)}
	 *                    method can be called directly.
	 * @see "java.net.URLStreamHandler.parseURL"
	 */
	public void parseURL(PlurlSetter plurlSetter, URL u, String spec, int start, int limit);

	/**
	 * If the plurlSetter is not {@code null} from the
	 * {@link #parseURL(PlurlSetter, URL, String, int, int)} then call the
	 * {@link PlurlSetter#setURL(URL, String, String, int, String, String, String, String, String)}
	 * method. Otherwise call {@code super.setURL}.
	 * 
	 * @see "java.net.URLStreamHandler.setURL"
	 */
	public void setURL(URL u, String proto, String host, int port, String file, String ref);

	/**
	 * If the plurlSetter is not {@code null} from the
	 * {@link #parseURL(PlurlSetter, URL, String, int, int)} then call the
	 * {@link PlurlSetter#setURL(URL, String, String, int, String, String, String, String, String)}
	 * method. Otherwise call {@code super.setURL}.
	 * 
	 * @see "java.net.URLStreamHandler.setURL"
	 */
	public void setURL(URL u, String proto, String host, int port, String auth, String user, String path,
			String query, String ref);
}

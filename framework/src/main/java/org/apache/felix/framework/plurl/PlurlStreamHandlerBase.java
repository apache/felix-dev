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
import java.net.URLStreamHandler;

/**
 * Abstract implementation of the {@code PlurlStreamHandler} interface. All
 * the methods simply invoke the corresponding methods on
 * {@code java.net.URLStreamHandler} except for {@code parseURL} and
 * {@code setURL}, which use the {@code PlurlSetter} parameter.
 * Subclasses of this abstract class should not need to override the
 * {@code setURL} and {@code parseURL(URLStreamHandlerSetter,...)} methods.

 */
public abstract class PlurlStreamHandlerBase extends URLStreamHandler implements PlurlStreamHandler {
	private volatile PlurlSetter plurlSetter;

	/**
	 * @see "java.net.URLStreamHandler.openConnection(URL)"
	 */
	@Override
	public abstract URLConnection openConnection(URL u) throws IOException;

	/**
	 * Parse a URL using the {@code PlurlSetter} object. This method sets the
	 * {@code plurlSetter} field with the specified {@code PlurlSetter} object and
	 * then calls {@code parseURL(URL,String,int,int)}.
	 * 
	 * @param setter The object on which the {@code setURL} method must be invoked
	 *               for the specified URL.
	 * @see "java.net.URLStreamHandler.parseURL"
	 */
	@Override
	public void parseURL(PlurlSetter setter, URL u, String spec, int start, int limit) {
		this.plurlSetter = setter;
		parseURL(u, spec, start, limit);
	}

	/**
	 * This method calls {@code super.openConnection(URL, Proxy)}
	 * 
	 * @see "java.net.URLStreamHandler.openConnection(URL, Proxy)"
	 */
	@Override
	public URLConnection openConnection(URL u, Proxy p) throws IOException {
		return super.openConnection(u, p);
	}

	/**
	 * This method calls {@code super.toExternalForm}.
	 * 
	 * @see "java.net.URLStreamHandler.toExternalForm"
	 */
	@Override
	public String toExternalForm(URL u) {
		return super.toExternalForm(u);
	}

	/**
	 * This method calls {@code super.equals(URL,URL)}.
	 * 
	 * @see "java.net.URLStreamHandler.equals(URL,URL)"
	 */
	@Override
	public boolean equals(URL u1, URL u2) {
		return super.equals(u1, u2);
	}

	/**
	 * This method calls {@code super.getDefaultPort}.
	 * 
	 * @see "java.net.URLStreamHandler.getDefaultPort"
	 */
	@Override
	public int getDefaultPort() {
		return super.getDefaultPort();
	}

	/**
	 * This method calls {@code super.getHostAddress}.
	 * 
	 * @see "java.net.URLStreamHandler.getHostAddress"
	 */
	@Override
	public InetAddress getHostAddress(URL u) {
		return super.getHostAddress(u);
	}

	/**
	 * This method calls {@code super.hashCode(URL)}.
	 * 
	 * @see "java.net.URLStreamHandler.hashCode(URL)"
	 */
	@Override
	public int hashCode(URL u) {
		return super.hashCode(u);
	}

	/**
	 * This method calls {@code super.hostsEqual}.
	 * 
	 * @see "java.net.URLStreamHandler.hostsEqual"
	 */
	@Override
	public boolean hostsEqual(URL u1, URL u2) {
		return super.hostsEqual(u1, u2);
	}

	/**
	 * This method calls {@code super.sameFile}.
	 * 
	 * @see "java.net.URLStreamHandler.sameFile"
	 */
	@Override
	public boolean sameFile(URL u1, URL u2) {
		return super.sameFile(u1, u2);
	}

	/**
	 * This method calls
	 * {@code plurlSetter.setURL(URL,String,String,int,String,String,String,String)}.
	 * 
	 * @see "java.net.URLStreamHandler.setURL(URL,String,String,int,String,String)"
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void setURL(URL u, String proto, String host, int port, String file, String ref) {
		PlurlSetter current = plurlSetter;
		if (current == null) {
			// something is calling the handler directly, probably passed it to URL directly
			super.setURL(u, proto, host, port, null, null, file, null, ref);
		} else {
			current.setURL(u, proto, host, port, null, null, file, null, ref);
		}
	}

	/**
	 * This method calls
	 * {@code realHandler.setURL(URL,String,String,int,String,String,String,String)}
	 * .
	 * 
	 * @see "java.net.URLStreamHandler.setURL(URL,String,String,int,String,String,String,String)"
	 */
	@Override
	public void setURL(URL u, String proto, String host, int port, String auth, String user, String path,
			String query, String ref) {
		PlurlSetter current = plurlSetter;
		if (current == null) {
			// something is calling the handler directly, probably passed it to URL directly
			super.setURL(u, proto, host, port, auth, user, path, query, ref);
		} else {
			current.setURL(u, proto, host, port, auth, user, path, query, ref);
		}
	}

}

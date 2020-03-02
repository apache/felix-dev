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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import org.osgi.framework.Bundle;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.wiring.BundleRevisions;

/**
 * A connect content provides a {@link Framework framework} access to the
 * content of a connect {@link ConnectModule module}. A framework may
 * {@link #open() open} and {@link #close() close} the content for a connect
 * module multiple times while the connect content is in use by the framework
 * instance. The framework must close the connect content once the connect
 * content is no longer used as the content of a current bundle revision or an
 * in use bundle revision.
 * <p>
 * An entry in a connect content is identified by a path name that is a
 * '{@code /}'-separated path. A connect content may treat directories as
 * entries. A directory entry path name will end with a slash ('/'). A directory
 * entry may be located using a path name that drops the trailing slash.
 * 
 * @see BundleRevisions
 * @ThreadSafe
 * @author $Id: 44ec66031f9460c48453c7113e4871472a7c475c $
 */
public interface ConnectContent {
	/**
	 * The {@code osgi.identity}
	 * {@link IdentityNamespace#CAPABILITY_TAGS_ATTRIBUTE tags} attribute value
	 * used by the framework to tag connect bundle revisions.
	 */
	public static final String TAG_OSGI_CONNECT = "osgi.connect";

	/**
	 * Returns this connect content Manifest headers and values. The
	 * {@link Optional#empty() empty} value is returned if the framework should
	 * handle parsing the Manifest of the content itself.
	 * 
	 * @return This connect content Manifest headers and values.
	 * @throws IllegalStateException if the connect content has been closed
	 */
	Optional<Map<String,String>> getHeaders();

	/**
	 * Returns an iterable with all the entry names available in this
	 * ConnectContent
	 * 
	 * @return the entry names
	 * @throws IOException if an error occurs reading the ConnectContent
	 * @throws IllegalStateException if the connect content has been closed
	 */
	Iterable<String> getEntries() throws IOException;

	/**
	 * Returns the connect entry for the specified path name in this content.
	 * The {@link Optional#empty() empty} value is returned if an entry with the
	 * specified path name does not exist. The path must not start with a
	 * &quot;/&quot; and is relative to the root of this content. A connect
	 * entry for a directory will have a path name that ends with a slash ('/').
	 * 
	 * @param path the path name of the entry
	 * @return the connect entry, or {@link Optional#empty() empty} if not
	 *         found.
	 * @throws IllegalStateException if the connect content has been closed
	 */
	Optional<ConnectEntry> getEntry(String path);

	/**
	 * Returns a class loader for this connect content. The
	 * {@link Optional#empty() empty} value is returned if the framework should
	 * handle creating a class loader for the bundle revision associated with
	 * this connect content.
	 * <p>
	 * This method is called by the framework for {@link Bundle#RESOLVED
	 * resolved} bundles only and will be called at most once while a bundle is
	 * resolved. If a bundle associated with a connect module is refreshed and
	 * resolved again the framework will ask the content for the class loader
	 * again. This allows for a connect content to reuse or create a new class
	 * loader each time the bundle revision is resolved.
	 * 
	 * @return a class loader for the module.
	 */
	Optional<ClassLoader> getClassLoader();

	/**
	 * Opens this connect content. The framework will open the content when it
	 * needs to access the content for a bundle revision associated with the
	 * connect content. The framework may lazily postpone to open the content
	 * until right before requests to access the bundle revision content are
	 * made.
	 * 
	 * @throws IOException if an error occurred opening the content
	 */
	void open() throws IOException;

	/**
	 * Closes this connect content.
	 * 
	 * @throws IOException if an error occurred closing the connect content
	 */
	void close() throws IOException;

	/**
	 * Represents the entry of a connect module
	 */
	public interface ConnectEntry {
		/**
		 * Returns the path name of the entry
		 * 
		 * @return the path name of the entry
		 */
		String getName();

		/**
		 * Returns the size of the entry. The value {@code -1} is returned if
		 * the content length is not known.
		 * 
		 * @return the size of the entry, or {@code -1} if the content length is
		 *         not known.
		 */
		public long getContentLength();

		/**
		 * Returns the last modification time of the entry
		 * 
		 * @return the last modification time of the entry
		 */
		public long getLastModified();

		/**
		 * Returns the content of the entry as a byte array.
		 * 
		 * @return the content bytes
		 * @throws IOException if an error occurs reading the content
		 */
		default byte[] getBytes() throws IOException {
			long longLength = getContentLength();
			if (longLength > Integer.MAX_VALUE - 8) {
				throw new IOException(
						"Entry is to big to fit into a byte[]: " + getName());
			}

			try (InputStream in = getInputStream()) {
				int length = (int) longLength;
				if (length > 0) {
					int bytesread = 0;
					byte[] result = new byte[length];
					int readcount = 0;
					while (bytesread < length) {
						readcount = in.read(result, bytesread,
								length - bytesread);
						bytesread += readcount;
						if (readcount <= 0) {
							break;
						}
					}
					return result;
				} else {
					ByteArrayOutputStream buffer = new ByteArrayOutputStream();
					int nRead;
					byte[] data = new byte[1024];
					while ((nRead = in.read(data, 0, data.length)) > 0) {
						buffer.write(data, 0, nRead);
					}
					buffer.flush();
					return buffer.toByteArray();
				}
			}
		}

		/**
		 * Returns the content of the entry as an input stream.
		 * 
		 * @return the content input stream
		 * @throws IOException if an error occurs reading the content
		 */
		InputStream getInputStream() throws IOException;
	}
}

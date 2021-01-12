/*
 * Copyright (c) OSGi Alliance (2019, 2020). All Rights Reserved.
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

import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.framework.Bundle;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.wiring.BundleRevisions;

/**
 * A {@code ConnectContent} provides a {@link Framework} instance access to the
 * content of a {@link ConnectModule}.
 * <p>
 * A framework may {@link #open() open} and {@link #close() close} the content
 * for a {@link ConnectModule} multiple times while the {@code ConnectContent}
 * is in use by the framework. The framework must close the
 * {@code ConnectContent} once the {@code ConnectContent} is no longer used as
 * the content of a current bundle revision or an in use bundle revision.
 * <p>
 * An entry in a {@code ConnectContent} is identified by a path name that is a
 * solidus (<code>'/' \u002F</code>) separated path. A {@code ConnectContent}
 * may treat directories as entries. A directory entry path name will end with a
 * solidus. A directory entry may be located using a path name that omits the
 * trailing solidus.
 * 
 * @see BundleRevisions
 * @ThreadSafe
 * @author $Id: 9e455f9d467f0e38daea0ea52a59a5ccb8c81257 $
 */
@ConsumerType
public interface ConnectContent {
	/**
	 * The {@code osgi.identity}
	 * {@link IdentityNamespace#CAPABILITY_TAGS_ATTRIBUTE tags} attribute value
	 * used by the framework to tag connect bundle revisions.
	 */
	String TAG_OSGI_CONNECT = "osgi.connect";

	/**
	 * Returns the Manifest headers and values of this {@code ConnectContent}.
	 * 
	 * @return An {@code Optional} containing the Manifest headers and values
	 *         for this {@code ConnectContent}, or an empty {@code Optional} if
	 *         the framework should handle parsing the Manifest of the content
	 *         itself.
	 * @throws IllegalStateException If this {@code ConnectContent} has been
	 *             closed.
	 */
	Optional<Map<String,String>> getHeaders();

	/**
	 * Returns the entry names available in this {@code ConnectContent}.
	 * 
	 * @return An {@code Iterable} which can supply the available entry names.
	 * @throws IOException If an error occurs reading this
	 *             {@code ConnectContent}.
	 * @throws IllegalStateException If this {@code ConnectContent} has been
	 *             closed.
	 */
	Iterable<String> getEntries() throws IOException;

	/**
	 * Returns the {@link ConnectEntry} for the specified path name in this
	 * content.
	 * <p>
	 * The {@link Optional#empty() empty} value is returned if an entry with the
	 * specified path name does not exist. The path must not start with a
	 * &quot;/&quot; and is relative to the root of this content. A connect
	 * entry for a directory will have a path name that ends with a slash ('/').
	 * 
	 * @param path The path name of the entry.
	 * @return An {@code Optional} containing the {@link ConnectEntry} for the
	 *         specified path, or an empty {@code Optional} if no entry for
	 *         specified path can be found.
	 * @throws IllegalStateException If this {@code ConnectContent} has been
	 *             closed.
	 */
	Optional<ConnectEntry> getEntry(String path);

	/**
	 * Returns a class loader for this {@code ConnectContent}.
	 * <p>
	 * This method is called by the framework for {@link Bundle#RESOLVED
	 * resolved} bundles only and will be called at most once while a bundle is
	 * resolved. If a bundle associated with a {@link ConnectModule} is
	 * refreshed and resolved again, the framework will ask the
	 * {@code ConnectContent} for the class loader again. This allows for a
	 * {@code ConnectContent} to reuse or create a new class loader each time
	 * the bundle revision is resolved.
	 * 
	 * @return An {@code Optional} containing the class loader for this
	 *         {@code ConnectContent}, or an empty {@code Optional} if framework
	 *         should handle creating a class loader for the bundle revision
	 *         associated with this {@code ConnectContent}.
	 * @throws IllegalStateException If this {@code ConnectContent} has been
	 *             closed.
	 */
	Optional<ClassLoader> getClassLoader();

	/**
	 * Opens this {@code ConnectContent}.
	 * <p>
	 * The framework will open the content when it needs to access the content
	 * for a bundle revision associated with this {@code ConnectContent}. The
	 * framework may defer calling this method until requests to access the
	 * bundle revision content are made.
	 * 
	 * @throws IOException If an error occurred opening this
	 *             {@code ConnectContent}.
	 */
	void open() throws IOException;

	/**
	 * Closes this {@code ConnectContent}.
	 * 
	 * @throws IOException If an error occurred closing this
	 *             {@code ConnectContent}.
	 */
	void close() throws IOException;

	/**
	 * Represents the entry of a {@code ConnectContent}.
	 */
	@ConsumerType
	public interface ConnectEntry {
		/**
		 * Returns the path name of this entry.
		 * 
		 * @return The path name of this entry.
		 */
		String getName();

		/**
		 * Returns the content length of this entry.
		 * 
		 * @return The content length of the entry, or {@code -1} if the content
		 *         length is not known.
		 */
		public long getContentLength();

		/**
		 * Returns the last modification time of this entry.
		 * 
		 * @return The last modification time of this entry measured in
		 *         milliseconds since the epoch (00:00:00 GMT, January 1, 1970).
		 */
		public long getLastModified();

		/**
		 * Returns the content of this entry.
		 * 
		 * @return The content of this entry.
		 * @throws IOException If an error occurs reading the content.
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
		 * Returns an input stream for the content of this entry.
		 * 
		 * @return An input stream for the content of this entry.
		 * @throws IOException If an error occurs reading the content.
		 */
		InputStream getInputStream() throws IOException;
	}
}

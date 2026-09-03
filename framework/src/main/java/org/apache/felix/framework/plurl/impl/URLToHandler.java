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

package org.apache.felix.framework.plurl.impl;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.felix.framework.plurl.PlurlStreamHandler;

public class URLToHandler {
	class WeakURL extends WeakReference<URL> {
		private final int hashcode;

		public WeakURL(URL u, ReferenceQueue<Object> q) {
			super(u, q);
			this.hashcode = System.identityHashCode(u);
		}
		@Override
		public int hashCode() {
			return hashcode;
		}
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof WeakURL) {
				return get() == ((WeakURL) obj).get();
			}
			return false;
		}
	}

	final ReferenceQueue<Object> queue = new ReferenceQueue<>();

	Map<WeakURL, PlurlStreamHandler> entries = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Replaces the handler recorded for the given URL. Used when the handler first
	 * recorded was chosen before the URL was populated, and its owner has since
	 * claimed it.
	 */
	void replace(URL u, PlurlStreamHandler handler) {
		synchronized (entries) {
			entries.put(new WeakURL(u, queue), handler);
			Object x;
			while ((x = queue.poll()) != null) {
				entries.remove(x);
			}
		}
	}

	PlurlStreamHandler get(URL u, Supplier<PlurlStreamHandler> h) {
		WeakURL lookup = new WeakURL(u, null);
		PlurlStreamHandler existing = entries.get(lookup);
		if (existing != null) {
			return existing;
		}

		PlurlStreamHandler result = h == null ? null : h.get();
		if (result != null) {
			synchronized (entries) {
				PlurlStreamHandler recheck = entries.get(lookup);
				if (recheck != null) {
					return recheck;
				}
				entries.put(new WeakURL(u, queue), result);
				Object x;
				while ((x = queue.poll()) != null) {
					entries.remove(x);
				}
			}
		}
		return result;
	}
}

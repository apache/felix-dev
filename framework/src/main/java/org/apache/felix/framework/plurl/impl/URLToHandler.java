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

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

import java.io.IOException;

import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleRevision;

/**
 * A connect module instance is used by a {@link Framework framework} when a
 * bundle location is connected to connect module. The connected bundle must use
 * the connect module to load content for the bundle revisions installed in the
 * framework for the connected bundle.
 * 
 * @ThreadSafe
 * @author $Id: 421e5c4762caa3798def32c52c3c1347648a5606 $
 */
public interface ConnectModule {
	/**
	 * Returns the current content of this connect module. The framework will
	 * call this method when it needs to access the content for the current
	 * {@link BundleRevision bundle revision} that is connected to this connect
	 * module. The framework may lazily postpone to open the content until right
	 * before requests to access the bundle revision content are made.
	 * 
	 * @return the current content of this connect module
	 * @throws IOException if an error occurred getting the content
	 * @see ModuleConnector#connect(String)
	 */
	ConnectContent getContent() throws IOException;
}

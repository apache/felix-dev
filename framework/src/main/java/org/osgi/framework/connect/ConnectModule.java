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

import java.io.IOException;

import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleRevision;

/**
 * A {@code ConnectModule} is used by a {@link Framework} instance to access the
 * content of the connected bundle.
 * 
 * @ThreadSafe
 * @author $Id: d81245bffb9c6de8e3d2e9515f1443b0f6b47189 $
 */
@ConsumerType
public interface ConnectModule {
	/**
	 * Returns the current content of this connect module.
	 * <p>
	 * The framework must call this method when it needs to access the content
	 * for the current {@link BundleRevision bundle revision} of this
	 * {@code ConnectModule}. The framework may defer opening the returned
	 * {@link ConnectContent} until requests to access the bundle revision
	 * content are made.
	 * 
	 * @return The current {@link ConnectContent} of this {@code ConnectModule}.
	 * @throws IOException If an error occurred getting the content.
	 * @see ModuleConnector#connect(String)
	 */
	ConnectContent getContent() throws IOException;
}

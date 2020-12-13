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

import java.util.Optional;

import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * A helper for the {@link FrameworkUtil} class.
 * <p>
 * This helper provides alternative implementations for methods on
 * {@link FrameworkUtil}.
 */
@ConsumerType
public interface FrameworkUtilHelper {
	/**
	 * Returns the {@link Bundle} associated with the specified class.
	 * <p>
	 * This helper method is called by {@link FrameworkUtil#getBundle(Class)} if
	 * the standard implementation of {@link FrameworkUtil} is unable to find
	 * the bundle.
	 * 
	 * @param classFromBundle A class associated with a bundle.
	 * @return An {@code Optional} containing the {@link Bundle} for the
	 *         specified class, or an empty {@code Optional} if the specified
	 *         class is not from a bundle.
	 */
	default Optional<Bundle> getBundle(Class< ? > classFromBundle) {
		return Optional.empty();
	}
}

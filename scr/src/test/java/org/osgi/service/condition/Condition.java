/*
 * Copyright (c) OSGi Alliance (2020). All Rights Reserved.
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

package org.osgi.service.condition;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Condition Service interface.
 * <p>
 * In dynamic systems, such as OSGi, one of the more challenging problems can be
 * to define when a system or part of it is ready to do work. The answer can
 * change depending on the individual perspective. The developer of a web server
 * might say, the system is ready when the server starts listening on port 80.
 * An application developer however would define the system as ready when the
 * database connection is up and all servlets are registered. Taking the
 * application developers view, the web server should start listening on port 80
 * when the application is ready and not beforehand.
 * <p>
 * The {@code Condition} service interface is a marker interface designed to
 * address this issue. Its role is to provide a dependency that can be tracked.
 * It acts as a defined signal to other services.
 * <p>
 * A {@code Condition} service must be registered with the
 * {@link Condition#CONDITION_ID} service property.
 * 
 * @ThreadSafe
 * @author $Id: 9736e5e1c38c45254f733d73ed7ae2c0e253f544 $
 */
@ConsumerType
public interface Condition {

	/**
	 * Service property identifying a condition's unique identifier.
	 * <p>
	 * Since a {@code Condition} service can potentially describe more then one
	 * condition, the type of this service property is {@code String+}.
	 */
	String		CONDITION_ID		= "osgi.condition.id";

	/**
	 * The unique identifier for the default True condition.
	 * <p>
	 * The default True condition is registered by the framework during
	 * framework initialization and therefore can always be relied upon.
	 * 
	 * @see Condition#CONDITION_ID
	 */
	String		CONDITION_ID_TRUE	= "true";

	/**
	 * A condition instance that can be used to register {@code Condition}
	 * services.
	 * <p>
	 * This can be helpful to avoid a bundle having to implement this interface
	 * to register a {@code Condition} service
	 */
	Condition	INSTANCE			= new ConditionImpl();
}

final class ConditionImpl implements Condition {
	ConditionImpl() {
	}
}

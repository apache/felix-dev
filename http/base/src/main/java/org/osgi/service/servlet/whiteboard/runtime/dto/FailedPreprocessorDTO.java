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

package org.osgi.service.servlet.whiteboard.runtime.dto;

/**
 * Represents a preprocessor service which is currently not being used due to a
 * problem.
 * 
 * @NotThreadSafe
 * @author $Id: ab753c468cc54608ed9ff7df6df98601d50b761c $
 * @since 1.1
 */
public class FailedPreprocessorDTO extends PreprocessorDTO {

	/**
	 * The reason why the preprocessor represented by this DTO is not used.
	 * 
	 * @see DTOConstants#FAILURE_REASON_UNKNOWN
	 * @see DTOConstants#FAILURE_REASON_EXCEPTION_ON_INIT
	 * @see DTOConstants#FAILURE_REASON_SERVICE_NOT_GETTABLE
	 */
	public int	failureReason;

}

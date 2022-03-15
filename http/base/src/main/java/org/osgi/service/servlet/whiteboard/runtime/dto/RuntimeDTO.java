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

import org.osgi.dto.DTO;
import org.osgi.framework.dto.ServiceReferenceDTO;

/**
 * Represents the state of a Http Service Runtime.
 * 
 * @NotThreadSafe
 * @author $Id: 2d4dff10956db843a122d231cff2f1fbceb568b3 $
 */
public class RuntimeDTO extends DTO {

	/**
	 * The DTO for the corresponding
	 * {@code org.osgi.service.servlet.whiteboard.runtime.HttpServiceRuntime}.
	 * This value is never {@code null}.
	 */
	public ServiceReferenceDTO			serviceDTO;

	/**
	 * Returns the representations of the
	 * {@code org.osgi.service.servlet.whiteboard.Preprocessor} objects used by
	 * the Http Service Runtime. The returned array may be empty if the Http
	 * Service Runtime is currently not using any
	 * {@code  org.osgi.service.servlet.whiteboard.Preprocessor} objects.
	 * 
	 * @since 1.1
	 */
	public PreprocessorDTO[]			preprocessorDTOs;

	/**
	 * Returns the representations of the {@code jakarta.servlet.ServletContext}
	 * objects used by the Http Service Runtime. The returned array may be empty
	 * if the Http Service Runtime is currently not using any
	 * {@code jakarta.servlet.ServletContext} objects.
	 */
	public ServletContextDTO[]			servletContextDTOs;

	/**
	 * Returns the representations of the {@code jakarta.servlet.ServletContext}
	 * objects currently not used by the Http service runtime due to some
	 * problem. The returned array may be empty.
	 */
	public FailedServletContextDTO[] failedServletContextDTOs;

	/**
	 * Returns the representations of the {@code jakarta.servlet.Servlet} services
	 * associated with this runtime but currently not used due to some problem.
	 * The returned array may be empty.
	 */
	public FailedServletDTO[] failedServletDTOs;

	/**
	 * Returns the representations of the resources associated with this runtime
	 * but currently not used due to some problem. The returned array may be
	 * empty.
	 */
	public FailedResourceDTO[] failedResourceDTOs;

	/**
	 * Returns the representations of the servlet
	 * {@code org.osgi.service.servlet.whiteboard.Preprocessor} services
	 * associated with this runtime but currently not used due to some problem.
	 * The returned array may be empty.
	 *
	 * @since 1.1
	 */
	public FailedPreprocessorDTO[]		failedPreprocessorDTOs;

	/**
	 * Returns the representations of the {@code jakarta.servlet.Filter} services
	 * associated with this runtime but currently not used due to some problem.
	 * The returned array may be empty.
	 */
	public FailedFilterDTO[] failedFilterDTOs;

	/**
	 * Returns the representations of the error page
	 * {@code jakarta.servlet.Servlet} services associated with this runtime but
	 * currently not used due to some problem. The returned array may be empty.
	 */
	public FailedErrorPageDTO[] failedErrorPageDTOs;

	/**
	 * Returns the representations of the listeners associated with this runtime
	 * but currently not used due to some problem. The returned array may be
	 * empty.
	 */
	public FailedListenerDTO[] failedListenerDTOs;
}

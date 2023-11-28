/*
 * Copyright (c) OSGi Alliance (2012, 2014). All Rights Reserved.
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

package org.osgi.resource.dto;

import java.util.List;
import org.osgi.dto.DTO;
import org.osgi.resource.Resource;

/**
 * Data Transfer Object for a Resource.
 * 
 * @author $Id: 377b7aff2a66f6691accf7af7017a68634fde3c4 $
 * @NotThreadSafe
 */
public class ResourceDTO extends DTO {
    /**
     * The unique identifier of the resource.
     * 
     * <p>
     * This identifier is transiently assigned and may vary across restarts.
     */
    public int                  id;

    /**
	 * The capabilities of the resource.
	 * 
	 * @see Resource#getCapabilities(String)
	 */
    public List<CapabilityDTO>  capabilities;

    /**
	 * The requirements of the resource.
	 * 
	 * @see Resource#getRequirements(String)
	 */
    public List<RequirementDTO> requirements;
}

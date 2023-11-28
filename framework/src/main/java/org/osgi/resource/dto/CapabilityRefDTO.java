/*
 * Copyright (c) OSGi Alliance (2014). All Rights Reserved.
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

import org.osgi.dto.DTO;

/**
 * Data Transfer Object for a reference to a Capability.
 * 
 * @author $Id: 81d5b85fdd9ffd67ef2729e107114530924054ac $
 * @NotThreadSafe
 */
public class CapabilityRefDTO extends DTO {
    /**
     * The identifier of the capability in the resource.
     * 
     * @see CapabilityDTO#id
     */
    public int capability;

    /**
     * The identifier of the resource declaring the capability.
     * 
     * @see ResourceDTO#id
     */
    public int resource;
}

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
 * Data Transfer Object for a reference to a Requirement.
 * 
 * @author $Id: 8f913a72d9d97ccc0a86bea2c85352018331fe8e $
 * @NotThreadSafe
 */
public class RequirementRefDTO extends DTO {
    /**
     * The identifier of the requirement in the resource.
     * 
     * @see RequirementDTO#id
     */
    public int requirement;

    /**
     * The identifier of the resource declaring the requirement.
     * 
     * @see ResourceDTO#id
     */
    public int resource;
}

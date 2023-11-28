/*
 * Copyright (c) OSGi Alliance (2010, 2020). All Rights Reserved.
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

/**
 * Package Admin Package Version 1.2.
 * 
 * <p>
 * <b>Deprecated.</b>
 * <i>This package is deprecated and has been replaced by the
 * {@code org.osgi.framework.wiring} package.</i>
 *
 * <p>
 * Bundles wishing to use this package must list the package in the
 * Import-Package header of the bundle's manifest.
 * 
 * <p>
 * Example import for consumers using the API in this package:
 * <p>
 * {@code  Import-Package: org.osgi.service.packageadmin; version="[1.2,2.0)"}
 * 
 * @author $Id: d4d6bc68fcfd8cb43a6cee0f81ad172f37ecd3b8 $
 */

@Version("1.2.1")
package org.osgi.service.packageadmin;

import org.osgi.annotation.versioning.Version;

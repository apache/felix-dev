/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.webconsole;

import org.apache.felix.webconsole.internal.servlet.BrandingPluginImpl;

/**
 * The <code>DefaultBrandingPlugin</code> class is the default implementation
 * of the {@link BrandingPlugin} interface. The singleton instance of this
 * class is used as branding plugin if no BrandingPlugin service is registered
 * in the system.
 * <p>
 * This default implementation provides Apache Felix based default branding
 * as follows:
 * <table>
 * <caption>Web Console Branding Properties</caption>
 * <tr><th>Name</th><th>Property Name</th><th>Default Value</th></tr>
 * <tr>
 *  <td>Brand Name</td>
 *  <td>webconsole.brand.name</td>
 *  <td>Apache Felix Web Console</td>
 * </tr>
 * <tr>
 *  <td>Product Name</td>
 *  <td>webconsole.product.name</td>
 *  <td>Apache Felix</td>
 * </tr>
 * <tr>
 *  <td>Product URL</td>
 *  <td>webconsole.product.url</td>
 *  <td>https://felix.apache.org</td>
 * </tr>
 * <tr>
 *  <td>Product Image</td>
 *  <td>webconsole.product.image</td>
 *  <td>/res/imgs/logo.png</td>
 * </tr>
 * <tr>
 *  <td>Vendor Name</td>
 *  <td>webconsole.vendor.name</td>
 *  <td>The Apache Software Foundation</td>
 * </tr>
 * <tr>
 *  <td>Vendor URL</td>
 *  <td>webconsole.vendor.url</td>
 *  <td>https://www.apache.org</td>
 * </tr>
 * <tr>
 *  <td>Vendor Image</td>
 *  <td>webconsole.vendor.image</td>
 *  <td>/res/imgs/logo.png</td>
 * </tr>
 * <tr>
 *  <td>Favourite Icon</td>
 *  <td>webconsole.favicon</td>
 *  <td>/res/imgs/favicon.ico</td>
 * </tr>
 * <tr>
 *  <td>Main Stylesheet</td>
 *  <td>webconsole.stylesheet</td>
 *  <td>/res/ui/admin.css</td>
 * </tr>
 * </table>
 * <p>
 * If a properties file <code>META-INF/webconsole.properties</code> is available
 * through the class loader of this class, the properties overwrite the default
 * settings according to the property names listed above. The easiest way to
 * add such a properties file is to provide a fragment bundle with the file.
 *
 * @deprecated Plugins should never use the branding plugin directly
 */
@Deprecated
public class DefaultBrandingPlugin extends BrandingPluginImpl implements BrandingPlugin {

    private static volatile DefaultBrandingPlugin instance;

    private DefaultBrandingPlugin() {
        super();
    }

    /**
     * Retrieves the shared instance
     *
     * @return the singleton instance of the object
     */
    public static DefaultBrandingPlugin getInstance() {
        if ( instance == null ) {
            instance = new DefaultBrandingPlugin();
        }
        return instance;
    }
}

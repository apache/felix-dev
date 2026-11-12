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
import java.util.jar.Manifest

// Parse with java.util.jar.Manifest so that 72-column line folding is handled for us.
Manifest manifest = new File(basedir, "bundle/target/classes/META-INF/MANIFEST.MF").withInputStream { new Manifest(it) }
String importPackage = manifest.mainAttributes.getValue("Import-Package")
assert importPackage != null : "Bundle has no Import-Package header"

// Sanity check: the interface's own package is imported (referenced via the Greeting.class literal).
assert importPackage.contains("org.apache.felix.proxy.api") :
        "Expected the proxied interface's package to be imported. Import-Package was: " + importPackage

// The actual assertion: the package used only by Greeting's method signature is imported because
// bnd (7.2.0+) inspects the methods of interfaces passed to Proxy.newProxyInstance.
// See https://github.com/bndtools/bnd/wiki/Changes-in-7.2.0 (-noproxyinterfaces disables this).
assert importPackage.contains("org.apache.felix.proxy.model") :
        "Expected the proxy interface's method-signature package (org.apache.felix.proxy.model) " +
        "to be imported via Proxy.newProxyInstance detection. Import-Package was: " + importPackage

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
String manifest = new File( basedir, "target/classes/META-INF/MANIFEST.MF" ).text
assert !manifest.isEmpty()

manifest.eachLine() { line ->
    if (line.contains("Import-Package") && !line.contains("org.apache.felix.test1")) {
        // See https://github.com/bndtools/bnd/pull/6270
        // bnd 7.1.0 by default would not add this to the Import-Package, as the version range is not specified
        // the maven-bundle-plugin doesn't have this issue, as a default version is added to the Export-Package statement
        // automatically if missing
        throw new Exception("Unversioned Export-Package statements should also be added to Import-Package statements, as maven-bundle-plugin adds a default version (1.0.0)");
    }
    if (line.contains("Import-Package") && !line.contains("org.apache.felix.test2;version=")) {
        throw new Exception("Versioned Export-Package should be part of the Import-Package, as it contains an explicit version range");
    }
}



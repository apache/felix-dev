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

// FELIX-6857: reaching this script means the build succeeded (before the fix, embedding the
// junit-bom .pom made bnd fail the build). Assert the pom was excluded but the jar was embedded.

import java.util.jar.Manifest

File manifestFile = new File(basedir, "target/classes/META-INF/MANIFEST.MF")
assert manifestFile.exists() : "MANIFEST.MF was not generated"

Manifest manifest = manifestFile.withInputStream { new Manifest(it) }
def attributes = manifest.getMainAttributes()

String bundleClassPath = attributes.getValue("Bundle-ClassPath") ?: ""
String embeddedArtifacts = attributes.getValue("Embedded-Artifacts") ?: ""

// the pom dependency must never be embedded
assert !bundleClassPath.contains("junit-bom") : "pom dependency leaked onto Bundle-ClassPath: " + bundleClassPath
assert !bundleClassPath.contains(".pom") : "a .pom leaked onto Bundle-ClassPath: " + bundleClassPath
assert !embeddedArtifacts.contains("junit-bom") : "pom dependency was embedded: " + embeddedArtifacts

// the regular jar dependency must still be embedded
assert bundleClassPath.contains("commons-io") : "expected commons-io jar to be embedded, Bundle-ClassPath: " + bundleClassPath

return true

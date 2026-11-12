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
import java.util.zip.ZipFile

File bundle = new File( basedir, "target/module-info-no-alien-resources-1-SNAPSHOT.jar" )
assert bundle.exists() : "bundle was not built: " + bundle

List<String> names = new ZipFile( bundle ).withCloseable { zip ->
    zip.entries().collect { it.name }
}

// the module descriptor must still be packaged
assert names.contains( "module-info.class" ) : "module-info.class is missing from the bundle: " + names

// the project's own class must be packaged
assert names.contains( "org/apache/felix/bundleits/moduleinfo/Sample.class" ) : "own class missing from the bundle: " + names

// alien root-level resources from classpath dependencies (e.g. beans_*.xsd from cdi-api) must NOT leak in
List<String> alien = names.findAll { it.toLowerCase().endsWith( ".xsd" ) }
assert alien.isEmpty() : "alien resources leaked into the bundle: " + alien

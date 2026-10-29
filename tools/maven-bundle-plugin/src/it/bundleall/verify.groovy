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

import java.util.jar.JarFile

// bundleall wraps the project dependencies, including transitive ones, into target/classes.
// commons-compress 1.26.0 depends on commons-io and commons-lang3; commons-io is the version
// selected by dependency mediation, not the one declared by any POM along the way.
def expected = [
    "org.apache.commons.commons-compress_1.26.0.jar",
    "org.apache.commons.lang3_3.14.0.jar",
    "org.apache.commons.commons-io_2.15.1.jar"
] as Set

def bundles = [:]
new File( basedir, "target/classes" ).eachFileMatch( ~/.*\.jar/ ) { f ->
    bundles[f.name] = f
}
assert bundles.keySet() == expected : "Unexpected bundles ${bundles.keySet()}"

bundles.each { name, file ->
    def jar = new JarFile( file )
    try {
        assert jar.manifest.mainAttributes.getValue( "Bundle-SymbolicName" ) : "${name} is not an OSGi bundle"
    } finally {
        jar.close()
    }
}

def log = new File( basedir, "build.log" ).text
assert log.contains( "The bundleall goal is no longer supported" )

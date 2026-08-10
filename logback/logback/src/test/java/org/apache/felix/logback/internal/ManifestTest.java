/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.felix.logback.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Manifest;

import org.junit.Test;

public class ManifestTest {

    @Test
    public void frameworkImportSupportsOSGiR7() throws Exception {
        String imports = getManifestHeader("Import-Package");

        assertTrue(imports, imports.contains(
            "org.osgi.framework;version=\"[1.9,2)\""));
    }

    @Test
    public void importsJULBridgeBundle() throws Exception {
        String imports = getManifestHeader("Import-Package");
        String privatePackages = getManifestHeader("Private-Package");

        assertTrue(imports, imports.contains(
            "org.slf4j.bridge;version=\"[2.0,3)\""));
        assertFalse(privatePackages, privatePackages.contains(
            "org.slf4j.bridge"));
    }

    @Test
    public void scmPointsToGitHubRepository() throws Exception {
        String scm = getManifestHeader("Bundle-SCM");

        assertTrue(scm, scm.contains(
            "url=\"https://github.com/apache/felix-dev\""));
    }

    private static String getManifestHeader(String name) throws Exception {
        Path classes = Paths.get(Activator.class.getProtectionDomain(
            ).getCodeSource().getLocation().toURI());

        try (InputStream input = Files.newInputStream(
            classes.resolve("META-INF/MANIFEST.MF"))) {

            return new Manifest(input).getMainAttributes().getValue(name);
        }
    }

}

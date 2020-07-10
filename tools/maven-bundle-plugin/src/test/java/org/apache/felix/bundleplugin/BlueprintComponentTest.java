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
package org.apache.felix.bundleplugin;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Manifest;

import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Parser;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.osgi.framework.Constants;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Verifier;
import aQute.libg.generics.Create;


public class BlueprintComponentTest extends AbstractMojoTestCase
{

    public void testBlueprintServices() throws Exception
    {
        test( "service" );
    }

    public void testBlueprintGeneric() throws Exception
    {
        test( "generic" );
    }

    protected void test(String mode) throws Exception
    {
        MavenProjectStub project = new MavenProjectStub()
        {
            private final List resources = new ArrayList();


            @Override
            public void addResource( Resource resource )
            {
                resources.add( resource );
            }


            @Override
            public List getResources()
            {
                return resources;
            }


            @Override
            public File getBasedir()
            {
                return new File( "target/tmp/basedir" );
            }
        };
        project.setGroupId("group");
        project.setArtifactId( "artifact" );
        project.setVersion( "1.1.0.0" );
        VersionRange versionRange = VersionRange.createFromVersion(project.getVersion());
        ArtifactHandler artifactHandler = new DefaultArtifactHandler( "jar" );
        Artifact artifact = new DefaultArtifact(project.getGroupId(),project.getArtifactId(),versionRange, null, "jar", null, artifactHandler);
        project.setArtifact(artifact);

        ProjectBuildingRequest projectBuilderConfiguration = new DefaultProjectBuildingRequest();
        projectBuilderConfiguration.setLocalRepository(null);
        project.setProjectBuildingRequest(projectBuilderConfiguration);

        Resource r = new Resource();
        r.setDirectory( new File( "src/test/resources" ).getAbsoluteFile().getCanonicalPath() );
        r.setIncludes(Arrays.asList("**/*.*"));
        project.addResource(r);
        project.addCompileSourceRoot(new File("src/test/resources").getAbsoluteFile().getCanonicalPath());

        ManifestPlugin plugin = new ManifestPlugin();
        plugin.setBuildDirectory( "target/tmp/basedir/target" );
        plugin.setOutputDirectory(new File("target/tmp/basedir/target/classes"));

        Map instructions = new HashMap();
        instructions.put( "service_mode", mode );
        instructions.put( "Test", "Foo" );

        instructions.put( "nsh_interface", "foo.bar.Namespace" );
        instructions.put( "nsh_namespace", "ns" );

        instructions.put( "Export-Service", "p7.Foo;mk=mv" );
        instructions.put( "Import-Service", "org.osgi.service.cm.ConfigurationAdmin;availability:=optional" );

        Properties props = new Properties();
        Builder builder = plugin.buildOSGiBundle( project, instructions, plugin.getClasspath( project) );

        Manifest manifest = builder.getJar().getManifest();
        String impSvc = manifest.getMainAttributes().getValue( Constants.IMPORT_SERVICE );
        if ("service".equals(mode)) {
            String expSvc = manifest.getMainAttributes().getValue( Constants.EXPORT_SERVICE );
            assertNotNull( expSvc );
            assertTrue( expSvc.contains("beanRef.Foo;osgi.service.blueprint.compname=myBean") );
        } else {
            String prvCap = manifest.getMainAttributes().getValue( Constants.PROVIDE_CAPABILITY );
            assertNotNull( prvCap );
            assertTrue( prvCap.contains("osgi.service;effective:=active;objectClass=\"beanRef.Foo\";osgi.service.blueprint.compname=myBean") );
        }

        assertNotNull( impSvc );

        String impPkg = manifest.getMainAttributes().getValue( Constants.IMPORT_PACKAGE );
        List<String> pkgs = Create.list();
        for (Clause clause : Parser.parseHeader(impPkg))
        {
            pkgs.add(clause.getName());
        }
        for ( int i = 1; i <= 14; i++ )
        {
            assertTrue( pkgs.contains( "p" + i ) );
        }

        try (Verifier verifier = new Verifier(builder)) {
            verifier.verify();
        }
    }

    public void testAnalyzer() throws Exception
    {
        Analyzer analyzer = new Analyzer();
        Manifest manifest = new Manifest();
        manifest.read(getClass().getResourceAsStream("/test.mf"));
        Jar jar = new Jar("name");
        jar.setManifest(manifest);
        analyzer.setJar(jar);
        analyzer.analyze();

        try (Verifier verifier = new Verifier(analyzer)) {
            verifier.verify();
        }
    }

}

package org.apache.felix.bundleplugin;


/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.Collections;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.project.MavenProject;


/**
 * Test for {@link BundleAllPlugin}
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class BundleAllPluginTest extends AbstractBundlePluginTest
{

    private BundleAllPlugin plugin;


    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        init();
    }


    private void init() throws Exception
    {
        plugin = new BundleAllPlugin();
        File baseDirectory = new File( getBasedir() );
        File buildDirectory = new File( baseDirectory, "target" );
        plugin.setBuildDirectory( buildDirectory.getPath() );
        File outputDirectory = new File( buildDirectory, "test-classes" );
        plugin.setOutputDirectory( outputDirectory );
    }


    public void testSnapshotMatch()
    {
        ArtifactStub artifact = getArtifactStub();
        String bundleName;

        artifact.setVersion( "2.1-SNAPSHOT" );
        bundleName = "group.artifact_2.1.0.20070207_193904_2.jar";

        assertTrue( plugin.snapshotMatch( artifact, bundleName ) );

        artifact.setVersion( "2-SNAPSHOT" );
        assertFalse( plugin.snapshotMatch( artifact, bundleName ) );

        artifact.setArtifactId( "artifactx" );
        artifact.setVersion( "2.1-SNAPSHOT" );
        assertFalse( plugin.snapshotMatch( artifact, bundleName ) );
    }


    public void testNoReBundling() throws Exception
    {
        File testFile = getTestFile( "target/test-classes/org.apache.maven.maven-model_1.0.0.0.jar" );
        if ( testFile.exists() )
        {
            testFile.delete();
        }

        VersionRange versionRange = VersionRange.createFromVersion("1.0.0.0");
        ArtifactHandler artifactHandler = new DefaultArtifactHandler( "jar" );
        Artifact artifact = new DefaultArtifact("group","artifact",versionRange, null, "jar", null, artifactHandler);

        MavenProject project = getMavenProjectStub();
        project.setGroupId( artifact.getGroupId() );
        project.setArtifactId( artifact.getArtifactId() );
        project.setVersion( artifact.getVersion() );
        project.setArtifact( artifact );
        project.setArtifacts( Collections.EMPTY_SET );
        project.setDependencyArtifacts( Collections.EMPTY_SET );
        File bundleFile = getTestFile( "src/test/resources/org.apache.maven.maven-model_2.1.0.SNAPSHOT.jar" );
        artifact.setFile( bundleFile );

        BundleInfo bundleInfo = plugin.bundle( project );

        Map exports = bundleInfo.getExportedPackages();
        String[] packages = new String[]
            { "org.apache.maven.model.io.jdom", "org.apache.maven.model" };

        for ( int i = 0; i < packages.length; i++ )
        {
            assertTrue( "Bundle info does not contain a package that it is  exported in the manifest: " + packages[i],
                exports.containsKey( packages[i] ) );
        }

        assertFalse( "Bundle info contains a package that it is not exported in the manifest",
            exports.containsKey( "org.apache.maven.model.io.xpp3" ) );
    }

    //    public void testRewriting()
    //        throws Exception
    //    {
    //
    //        MavenProjectStub project = new MavenProjectStub();
    //        project.setArtifact( getArtifactStub() );
    //        project.getArtifact().setFile( getTestBundle() );
    //        project.setDependencyArtifacts( Collections.EMPTY_SET );
    //        project.setVersion( project.getArtifact().getVersion() );
    //
    //        File output = new File( plugin.getBuildDirectory(), plugin.getBundleName( project ) );
    //        boolean delete = output.delete();
    //
    //        plugin.bundle( project );
    //
    //        init();
    //        try
    //        {
    //            plugin.bundle( project );
    //            fail();
    //        }
    //        catch ( RuntimeException e )
    //        {
    //            // expected
    //        }
    //    }
}

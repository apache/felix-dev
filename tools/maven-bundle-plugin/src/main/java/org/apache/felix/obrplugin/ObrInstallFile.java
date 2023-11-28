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
package org.apache.felix.obrplugin;


import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;


/**
 * Installs bundle details in the local OBR repository (command-line goal)
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
@Mojo( name = "install-file", requiresProject = false, defaultPhase = LifecyclePhase.INSTALL )
public final class ObrInstallFile extends AbstractFileMojo
{
    /**
     * OBR Repository.
     */
    @Parameter( property = "obrRepository" )
    private String obrRepository;

    /**
     * Project types which this plugin supports.
     */
    @Parameter
    private List supportedProjectTypes = Arrays.asList("jar", "bundle");

    /**
     * Local Repository.
     */
    @Parameter( defaultValue = "${localRepository}", readonly = true, required = true )
    private ArtifactRepository localRepository;


    public void execute() throws MojoExecutionException
    {
        MavenProject project = getProject();
        String projectType = project.getPackaging();

        // ignore unsupported project types, useful when bundleplugin is configured in parent pom
        if ( !supportedProjectTypes.contains( projectType ) )
        {
            getLog().warn(
                "Ignoring project type " + projectType + " - supportedProjectTypes = " + supportedProjectTypes );
            return;
        }
        else if ( "NONE".equalsIgnoreCase( obrRepository ) || "false".equalsIgnoreCase( obrRepository ) )
        {
            getLog().info( "Local OBR update disabled (enable with -DobrRepository)" );
            return;
        }

        Log log = getLog();
        ObrUpdate update;

        String mavenRepository = localRepository.getBasedir();

        URI repositoryXml = ObrUtils.findRepositoryXml( mavenRepository, obrRepository );
        URI obrXmlFile = ObrUtils.toFileURI( obrXml );
        URI bundleJar;

        if ( null == file )
        {
            bundleJar = ObrUtils.getArtifactURI( localRepository, project.getArtifact() );
        }
        else
        {
            bundleJar = file.toURI();
        }

        Config userConfig = new Config();

        update = new ObrUpdate( repositoryXml, obrXmlFile, project, mavenRepository, userConfig, log );
        update.parseRepositoryXml();

        update.updateRepository( bundleJar, null, null );

        update.writeRepositoryXml();
    }
}

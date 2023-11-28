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
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import aQute.bnd.osgi.Builder;


/**
 * Generate Ant script to create the bundle (you should run ant:ant first).
 *
 */
@Mojo( name = "ant", requiresDependencyResolution = ResolutionScope.TEST )
public class AntPlugin extends BundlePlugin
{
    static final String BUILD_XML = "/build.xml";
    static final String BUILD_BND = "/maven-build.bnd";


    @Override
    protected void execute( Map<String, String> originalInstructions,
        ClassPathItem[] classpath ) throws MojoExecutionException
    {
        final String artifactId = getProject().getArtifactId();
        final String baseDir = getProject().getBasedir().getPath();

        try
        {
            // assemble bundle as usual, but don't save it - this way we have all the instructions we need
            Builder builder = buildOSGiBundle(project,
                    originalInstructions, classpath );
            Properties bndProperties = builder.getProperties();

            // cleanup and remove all non-strings from the builder properties
            for ( Iterator i = bndProperties.values().iterator(); i.hasNext(); )
            {
                if ( !( i.next() instanceof String ) )
                {
                    i.remove();
                }
            }

            // save the BND generated bundle to the same output directory that maven uses
            bndProperties.setProperty( "-output", "${maven.build.dir}/${maven.build.finalName}.jar" );

            OutputStream out = buildContext.newFileOutputStream( new File(baseDir + BUILD_BND) );
            bndProperties.store( out, " Merged BND Instructions" );
            IOUtil.close( out );

            // modify build template
            String buildXml = IOUtil.toString( getClass().getResourceAsStream( BUILD_XML ) );
            buildXml = StringUtils.replace( buildXml, "BND_VERSION", builder.getVersion() );
            buildXml = StringUtils.replace( buildXml, "ARTIFACT_ID", artifactId );

            FileUtils.fileWrite( baseDir + BUILD_XML, buildXml );

            // cleanup...
            builder.close();
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Problem creating Ant script", e );
        }

        getLog().info( "Wrote Ant bundle project for " + artifactId + " to " + baseDir );
    }
}

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
import java.io.FilenameFilter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.util.FileUtils;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Jar;


/**
 * Build an OSGi bundle jar for all transitive dependencies.
 *
 * @deprecated The bundleall goal is no longer supported and may be removed in a future release
 */
@Deprecated
@Mojo( name = "bundleall", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.PACKAGE )
public class BundleAllPlugin extends ManifestPlugin
{
    private static final String LS = System.getProperty( "line.separator" );

    private static final Pattern SNAPSHOT_VERSION_PATTERN = Pattern.compile( "[0-9]{8}_[0-9]{6}_[0-9]+" );

    /**
     * Local repository.
     */
    @Parameter( defaultValue = "${localRepository}", readonly = true, required = true )
    private ArtifactRepository localRepository;

    /**
     * Remote repositories.
     */
    @Parameter( defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true )
    private List remoteRepositories;

    /**
     * Import-Package to be used when wrapping dependencies.
     */
    @Parameter( property = "wrapImportPackage", defaultValue = "*" )
    private String wrapImportPackage;

    @Component
    private ArtifactFactory m_factory;

    @Component
    private ArtifactMetadataSource m_artifactMetadataSource;

    /**
     * Artifact resolver, needed to download jars.
     */
    @Component
    private ArtifactResolver m_artifactResolver;

    @Component
    private MavenProjectBuilder m_mavenProjectBuilder;

    /**
     * Ignore missing artifacts that are not required by current project but are required by the
     * transitive dependencies.
     */
    @Parameter
    private boolean ignoreMissingArtifacts;

    private Set m_artifactsBeingProcessed = new HashSet();

    /**
     * Process up to some depth
     */
    @Parameter
    private int depth = Integer.MAX_VALUE;


    @Override
    public void execute() throws MojoExecutionException
    {
        getLog().warn( "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~" );
        getLog().warn( "! The bundleall goal is no longer supported and may be removed in a future release !" );
        getLog().warn( "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~" );

        BundleInfo bundleInfo = bundleAll( getProject() );
        logDuplicatedPackages( bundleInfo );
    }


    /**
     * Bundle a project and all its dependencies
     *
     * @param project
     * @throws MojoExecutionException
     */
    private BundleInfo bundleAll( MavenProject project ) throws MojoExecutionException
    {
        return bundleAll( project, depth );
    }


    /**
     * Bundle a project and its transitive dependencies up to some depth level
     *
     * @param project
     * @param maxDepth how deep to process the dependency tree
     * @throws MojoExecutionException
     */
    protected BundleInfo bundleAll( MavenProject project, int maxDepth ) throws MojoExecutionException
    {
        if ( alreadyBundled( project.getArtifact() ) )
        {
            getLog().debug( "Ignoring project already processed " + project.getArtifact() );
            return null;
        }

        if ( m_artifactsBeingProcessed.contains( project.getArtifact() ) )
        {
            getLog().warn( "Ignoring artifact due to dependency cycle " + project.getArtifact() );
            return null;
        }
        m_artifactsBeingProcessed.add( project.getArtifact() );

        DependencyNode dependencyTree;

        try
        {
            ProjectBuildingRequest request = new DefaultProjectBuildingRequest();
            request.setProject( getProject() );
            request.setRepositorySession( session.getRepositorySession() );
            dependencyTree = dependencyGraphBuilder.buildDependencyGraph( request, null );
        }
        catch ( DependencyGraphBuilderException e )
        {
            throw new MojoExecutionException( "Unable to build dependency tree", e );
        }

        BundleInfo bundleInfo = new BundleInfo();

        if ( dependencyTree.getChildren().isEmpty() )
        {
            /* no need to traverse the tree */
            return bundleRoot( project, bundleInfo );
        }

        getLog().debug( "Will bundle the following dependency tree" + LS + dependencyTree );

        Deque<DependencyNode> stack = new ArrayDeque<>();
        stack.push(dependencyTree);
        Set<DependencyNode> visited = new HashSet<>();
        while (!stack.isEmpty())
        {
            DependencyNode node = stack.pop();
            if ( visited.contains(node) )
            {
                continue;
            }
            visited.add( node );
            if ( node.getChildren() != null )
            {
                stack.addAll( node.getChildren() );
            }

            if ( Artifact.SCOPE_SYSTEM.equals( node.getArtifact().getScope() ) )
            {
                getLog().debug( "Ignoring system scoped artifact " + node.getArtifact() );
                continue;
            }

            Artifact artifact;
            try
            {
                artifact = resolveArtifact( node.getArtifact() );
            }
            catch ( ArtifactNotFoundException e )
            {
                if ( ignoreMissingArtifacts )
                {
                    continue;
                }

                throw new MojoExecutionException( "Artifact was not found in the repo" + node.getArtifact(), e );
            }

            node.getArtifact().setFile( artifact.getFile() );

            if ( stack.size() > maxDepth )
            {
                /* node is deeper than we want */
                getLog().debug(
                    "Ignoring " + node.getArtifact() + ", depth is " + stack.size() + ", bigger than " + maxDepth );
                continue;
            }

            MavenProject childProject;
            try
            {
                childProject = m_mavenProjectBuilder.buildFromRepository( artifact, remoteRepositories,
                    localRepository, true );
                if ( childProject.getDependencyArtifacts() == null )
                {
                    childProject.setDependencyArtifacts( childProject.createArtifacts( m_factory, null, null ) );
                }
            }
            catch ( InvalidDependencyVersionException e )
            {
                throw new MojoExecutionException( "Invalid dependency version for artifact " + artifact );
            }
            catch ( ProjectBuildingException e )
            {
                throw new MojoExecutionException( "Unable to build project object for artifact " + artifact, e );
            }

            childProject.setArtifact( artifact );
            getLog().debug( "Child project artifact location: " + childProject.getArtifact().getFile() );

            if ( ( Artifact.SCOPE_COMPILE.equals( artifact.getScope() ) )
                || ( Artifact.SCOPE_RUNTIME.equals( artifact.getScope() ) ) )
            {
                BundleInfo subBundleInfo = bundleAll( childProject, maxDepth - 1 );
                if ( subBundleInfo != null )
                {
                    bundleInfo.merge( subBundleInfo );
                }
            }
            else
            {
                getLog().debug(
                    "Not processing due to scope (" + childProject.getArtifact().getScope() + "): "
                        + childProject.getArtifact() );
            }
        }

        return bundleRoot( project, bundleInfo );
    }


    /**
     * Bundle the root of a dependency tree after all its children have been bundled
     *
     * @param project
     * @param bundleInfo
     * @return
     * @throws MojoExecutionException
     */
    private BundleInfo bundleRoot( MavenProject project, BundleInfo bundleInfo ) throws MojoExecutionException
    {
        /* do not bundle the project the mojo was called on */
        if ( getProject() != project )
        {
            getLog().debug( "Project artifact location: " + project.getArtifact().getFile() );

            BundleInfo subBundleInfo = bundle( project );
            if ( subBundleInfo != null )
            {
                bundleInfo.merge( subBundleInfo );
            }
        }
        return bundleInfo;
    }


    /**
     * Bundle one project only without building its childre
     *
     * @param project
     * @throws MojoExecutionException
     */
    protected BundleInfo bundle( MavenProject project ) throws MojoExecutionException
    {
        Artifact artifact = project.getArtifact();
        getLog().info( "Bundling " + artifact );

        try
        {
            Map instructions = new LinkedHashMap();
            instructions.put( Analyzer.IMPORT_PACKAGE, wrapImportPackage );

            project.getArtifact().setFile( getFile( artifact ) );
            File outputFile = getOutputFile(artifact);

            if ( project.getArtifact().getFile().equals( outputFile ) )
            {
                /* TODO find the cause why it's getting here */
                return null;
                //                getLog().error(
                //                                "Trying to read and write " + artifact + " to the same file, try cleaning: "
                //                                    + outputFile );
                //                throw new IllegalStateException( "Trying to read and write " + artifact
                //                    + " to the same file, try cleaning: " + outputFile );
            }

            Analyzer analyzer = getAnalyzer( project, instructions, getClasspath( project) );

            Jar osgiJar = new Jar( project.getArtifactId(), project.getArtifact().getFile() );

            outputFile.getAbsoluteFile().getParentFile().mkdirs();

            Collection exportedPackages;
            if ( isOsgi( osgiJar ) )
            {
                /* if it is already an OSGi jar copy it as is */
                getLog().info(
                    "Using existing OSGi bundle for " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                        + project.getVersion() );
                String exportHeader = osgiJar.getManifest().getMainAttributes().getValue( Analyzer.EXPORT_PACKAGE );
                exportedPackages = analyzer.parseHeader( exportHeader ).keySet();
                FileUtils.copyFile( project.getArtifact().getFile(), outputFile );
            }
            else
            {
                /* else generate the manifest from the packages */
                exportedPackages = analyzer.getExports().keySet();
                Manifest manifest = analyzer.getJar().getManifest();
                osgiJar.setManifest( manifest );
                osgiJar.write( outputFile );
            }

            BundleInfo bundleInfo = addExportedPackages( project, exportedPackages );

            // cleanup...
            analyzer.close();
            osgiJar.close();

            return bundleInfo;
        }
        /* too bad Jar.write throws Exception */
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Error generating OSGi bundle for project "
                + getArtifactKey( project.getArtifact() ), e );
        }
    }


    private boolean isOsgi( Jar jar ) throws Exception
    {
        if ( jar.getManifest() != null )
        {
            return jar.getManifest().getMainAttributes().getValue( Analyzer.BUNDLE_NAME ) != null;
        }
        return false;
    }


    private BundleInfo addExportedPackages( MavenProject project, Collection packages )
    {
        BundleInfo bundleInfo = new BundleInfo();
        for ( Iterator it = packages.iterator(); it.hasNext(); )
        {
            String packageName = ( String ) it.next();
            bundleInfo.addExportedPackage( packageName, project.getArtifact() );
        }
        return bundleInfo;
    }


    private String getArtifactKey( Artifact artifact )
    {
        return artifact.getGroupId() + ":" + artifact.getArtifactId();
    }


    private String getBundleName( Artifact artifact )
    {
        return getMaven2OsgiConverter().getBundleFileName( artifact );
    }


    private boolean alreadyBundled( Artifact artifact )
    {
        return getBuiltFile( artifact ) != null;
    }


    /**
     * Use previously built bundles when available.
     *
     * @param artifact
     */
    @Override
    protected File getFile( final Artifact artifact )
    {
        File bundle = getBuiltFile( artifact );

        if ( bundle != null )
        {
            getLog().debug( "Using previously built OSGi bundle for " + artifact + " in " + bundle );
            return bundle;
        }
        return super.getFile( artifact );
    }


    private File getBuiltFile( final Artifact artifact )
    {
        File bundle = null;

        /* if bundle was already built use it instead of jar from repo */
        File outputFile = getOutputFile( artifact );
        if ( outputFile.exists() )
        {
            bundle = outputFile;
        }

        /*
         * Find snapshots in output folder, eg. 2.1-SNAPSHOT will match 2.1.0.20070207_193904_2
         * TODO there has to be another way to do this using Maven libs
         */
        if ( ( bundle == null ) && artifact.isSnapshot() )
        {
            final File buildDirectory = new File( getBuildDirectory() );
            if ( !buildDirectory.exists() )
            {
                buildDirectory.mkdirs();
            }
            File[] files = buildDirectory.listFiles( (FilenameFilter) (dir, name) -> {
			    if ( dir.equals( buildDirectory ) && snapshotMatch( artifact, name ) )
			    {
			        return true;
			    }
			    return false;
			} );
            if ( files.length > 1 )
            {
                throw new RuntimeException( "More than one previously built bundle matches for artifact " + artifact
                    + " : " + Arrays.asList( files ) );
            }
            if ( files.length == 1 )
            {
                bundle = files[0];
            }
        }

        return bundle;
    }


    /**
     * Check that the bundleName provided correspond to the artifact provided.
     * Used to determine when the bundle name is a timestamped snapshot and the artifact is a snapshot not timestamped.
     *
     * @param artifact artifact with snapshot version
     * @param bundleName bundle file name
     * @return if both represent the same artifact and version, forgetting about the snapshot timestamp
     */
    protected boolean snapshotMatch( Artifact artifact, String bundleName )
    {
        String artifactBundleName = getBundleName( artifact );
        int i = artifactBundleName.indexOf( "SNAPSHOT" );
        if ( i < 0 )
        {
            return false;
        }
        artifactBundleName = artifactBundleName.substring( 0, i );

        if ( bundleName.startsWith( artifactBundleName ) )
        {
            /* it's the same artifact groupId and artifactId */
            String timestamp = bundleName.substring( artifactBundleName.length(), bundleName.lastIndexOf( ".jar" ) );
            Matcher m = SNAPSHOT_VERSION_PATTERN.matcher( timestamp );
            return m.matches();
        }
        return false;
    }


    protected File getOutputFile( Artifact artifact )
    {
        return new File( getOutputDirectory(), getBundleName( artifact ) );
    }


    private Artifact resolveArtifact( Artifact artifact ) throws MojoExecutionException, ArtifactNotFoundException
    {
        VersionRange versionRange;
        if ( artifact.getVersion() != null )
        {
            versionRange = VersionRange.createFromVersion( artifact.getVersion() );
        }
        else
        {
            versionRange = artifact.getVersionRange();
        }

        /*
         * there's a bug with ArtifactFactory#createDependencyArtifact(String, String, VersionRange,
         * String, String, String) that ignores the scope parameter, that's why we use the one with
         * the extra null parameter
         */
        Artifact resolvedArtifact = m_factory.createDependencyArtifact( artifact.getGroupId(),
            artifact.getArtifactId(), versionRange, artifact.getType(), artifact.getClassifier(), artifact.getScope(),
            null );

        try
        {
            m_artifactResolver.resolve( resolvedArtifact, remoteRepositories, localRepository );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new MojoExecutionException( "Error resolving artifact " + resolvedArtifact, e );
        }

        return resolvedArtifact;
    }


    /**
     * Log what packages are exported in more than one bundle
     */
    protected void logDuplicatedPackages( BundleInfo bundleInfo )
    {
        Map duplicatedExports = bundleInfo.getDuplicatedExports();

        for ( Iterator it = duplicatedExports.entrySet().iterator(); it.hasNext(); )
        {
            Map.Entry entry = ( Map.Entry ) it.next();
            String packageName = ( String ) entry.getKey();
            Collection artifacts = ( Collection ) entry.getValue();

            getLog().warn( "Package " + packageName + " is exported in more than a bundle: " );
            for ( Iterator it2 = artifacts.iterator(); it2.hasNext(); )
            {
                Artifact artifact = ( Artifact ) it2.next();
                getLog().warn( "  " + artifact );
            }

        }
    }
}

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


import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.apache.maven.wagon.UnsupportedProtocolException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.providers.file.FileWagon;
import org.codehaus.plexus.PlexusContainer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class RemoteFileManagerTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final List<String> lookedUp = new ArrayList<String>();

    private final AtomicInteger released = new AtomicInteger();

    private PlexusContainer container;

    /** Prefixes every password, so a test can tell that the decrypter was used. */
    private final SettingsDecrypter decrypter = new SettingsDecrypter()
    {
        public SettingsDecryptionResult decrypt( SettingsDecryptionRequest request )
        {
            Server server = request.getServers().isEmpty() ? null : request.getServers().get( 0 );
            Proxy proxy = request.getProxies().isEmpty() ? null : request.getProxies().get( 0 );
            if ( server != null )
            {
                server = server.clone();
                server.setPassword( "decrypted-" + server.getPassword() );
            }
            if ( proxy != null )
            {
                proxy = proxy.clone();
                proxy.setPassword( "decrypted-" + proxy.getPassword() );
            }
            return new Result( server, proxy );
        }
    };


    private static final class Result implements SettingsDecryptionResult
    {
        private final Server server;

        private final Proxy proxy;

        Result( Server server, Proxy proxy )
        {
            this.server = server;
            this.proxy = proxy;
        }

        public Server getServer()
        {
            return server;
        }

        public List<Server> getServers()
        {
            return server == null ? new ArrayList<Server>() : Collections.singletonList( server );
        }

        public Proxy getProxy()
        {
            return proxy;
        }

        public List<Proxy> getProxies()
        {
            return proxy == null ? new ArrayList<Proxy>() : Collections.singletonList( proxy );
        }

        public List<SettingsProblem> getProblems()
        {
            return new ArrayList<SettingsProblem>();
        }
    }


    /** A container that only knows the file wagon, as the plugin realm would for the file protocol. */
    @Before
    public void createContainer()
    {
        container = ( PlexusContainer ) java.lang.reflect.Proxy.newProxyInstance( getClass().getClassLoader(),
            new Class<?>[] { PlexusContainer.class }, ( proxy, method, args ) -> {
                if ( "lookup".equals( method.getName() ) && args.length == 2 && args[1] instanceof String )
                {
                    lookedUp.add( ( String ) args[1] );
                    if ( "file".equals( args[1] ) )
                    {
                        return new FileWagon();
                    }
                    throw new org.codehaus.plexus.component.repository.exception.ComponentLookupException(
                        "no such wagon", ( ( Class<?> ) args[0] ).getName(), ( String ) args[1] );
                }
                if ( "release".equals( method.getName() ) )
                {
                    released.incrementAndGet();
                    return null;
                }
                throw new UnsupportedOperationException( method.getName() );
            } );
    }


    private RemoteFileManager manager( Settings settings )
    {
        return new RemoteFileManager( container, decrypter, settings, new SystemStreamLog() );
    }


    private static Server server( String id, String user, String password )
    {
        Server server = new Server();
        server.setId( id );
        server.setUsername( user );
        server.setPassword( password );
        return server;
    }


    @Test
    public void authenticationComesFromTheServerWithTheSameId()
    {
        Settings settings = new Settings();
        settings.addServer( server( "other", "nobody", "secret" ) );
        Server server = server( "obr", "alice", "s3cr3t" );
        server.setPrivateKey( "/home/alice/key" );
        server.setPassphrase( "phrase" );
        settings.addServer( server );

        AuthenticationInfo info = RemoteFileManager.getAuthenticationInfo( settings, decrypter, "OBR" );

        assertEquals( "alice", info.getUserName() );
        assertEquals( "decrypted-s3cr3t", info.getPassword() );
        assertEquals( "/home/alice/key", info.getPrivateKey() );
        assertEquals( "phrase", info.getPassphrase() );
    }


    @Test
    public void authenticationWithoutDecrypterUsesPasswordAsIs()
    {
        Settings settings = new Settings();
        settings.addServer( server( "obr", "alice", "s3cr3t" ) );

        assertEquals( "s3cr3t", RemoteFileManager.getAuthenticationInfo( settings, null, "obr" ).getPassword() );
    }


    @Test
    public void authenticationIsEmptyWhenNoServerMatches()
    {
        Settings settings = new Settings();
        settings.addServer( server( "other", "nobody", "secret" ) );

        AuthenticationInfo info = RemoteFileManager.getAuthenticationInfo( settings, decrypter, "obr" );

        assertNotNull( info );
        assertNull( info.getUserName() );
        assertNull( info.getPassword() );
        assertNotNull( RemoteFileManager.getAuthenticationInfo( null, decrypter, "obr" ) );
        assertNotNull( RemoteFileManager.getAuthenticationInfo( settings, decrypter, null ) );
    }


    private static Proxy proxy( String id, boolean active, String host )
    {
        Proxy proxy = new Proxy();
        proxy.setId( id );
        proxy.setActive( active );
        proxy.setProtocol( "http" );
        proxy.setHost( host );
        proxy.setPort( 3128 );
        proxy.setNonProxyHosts( "localhost|*.example.org" );
        proxy.setUsername( "pu" );
        proxy.setPassword( "pp" );
        return proxy;
    }


    @Test
    public void proxyIsTheFirstActiveProxyWithDecryptedPassword()
    {
        Settings settings = new Settings();
        settings.addProxy( proxy( "off", false, "inactive.example.com" ) );
        settings.addProxy( proxy( "on", true, "proxy.example.com" ) );

        ProxyInfo info = RemoteFileManager.getProxyInfo( settings, decrypter );

        assertEquals( "proxy.example.com", info.getHost() );
        assertEquals( 3128, info.getPort() );
        assertEquals( "http", info.getType() );
        assertEquals( "localhost|*.example.org", info.getNonProxyHosts() );
        assertEquals( "pu", info.getUserName() );
        assertEquals( "decrypted-pp", info.getPassword() );

        assertEquals( "pp", RemoteFileManager.getProxyInfo( settings ).getPassword() );
    }


    @Test
    public void noProxyWhenNoneIsActive()
    {
        Settings settings = new Settings();
        assertNull( RemoteFileManager.getProxyInfo( settings, decrypter ) );
        settings.addProxy( proxy( "off", false, "inactive.example.com" ) );
        assertNull( RemoteFileManager.getProxyInfo( settings, decrypter ) );
        assertNull( RemoteFileManager.getProxyInfo( null, decrypter ) );
    }


    @Test
    public void wagonIsLookedUpByLowerCaseProtocol() throws Exception
    {
        Wagon wagon = manager( new Settings() ).lookupWagon( "FILE" );

        assertTrue( wagon instanceof FileWagon );
        assertEquals( Collections.singletonList( "file" ), lookedUp );
    }


    @Test
    public void unknownProtocolIsRejected()
    {
        try
        {
            manager( new Settings() ).lookupWagon( "gopher" );
            fail( "expected UnsupportedProtocolException" );
        }
        catch ( UnsupportedProtocolException e )
        {
            assertTrue( e.getMessage(), e.getMessage().contains( "gopher" ) );
        }

        try
        {
            manager( new Settings() ).lookupWagon( null );
            fail( "expected UnsupportedProtocolException" );
        }
        catch ( UnsupportedProtocolException e )
        {
            // expected
        }
    }


    @Test
    public void connectReportsUnsupportedProtocol()
    {
        try
        {
            manager( new Settings() ).connect( "obr", "gopher://example.com/obr" );
            fail( "expected MojoExecutionException" );
        }
        catch ( MojoExecutionException e )
        {
            assertEquals( "Unsupported protocol: 'gopher'", e.getMessage() );
        }
    }


    @Test
    public void lockUploadDownloadAndUnlockOnFileRepository() throws Exception
    {
        File remote = tmp.newFolder( "obr" );
        RemoteFileManager manager = manager( new Settings() );
        manager.connect( "obr", remote.toURI().toString() );
        try
        {
            // a missing remote file is returned as a non-existent file
            File missing = manager.get( "repository.xml", ".xml" );
            assertFalse( missing.exists() );

            manager.lockFile( "repository.xml", false );
            assertEquals( "LOCKED", new String( Files.readAllBytes( new File( remote, "repository.xml.lock" ).toPath() ),
                StandardCharsets.UTF_8 ) );
            assertTrue( manager.isLockedFile( "repository.xml" ) );

            File local = tmp.newFile( "local.xml" );
            Files.write( local.toPath(), "<repository/>".getBytes( StandardCharsets.UTF_8 ) );
            manager.put( local, "repository.xml" );
            File downloaded = manager.get( "repository.xml", ".xml" );
            assertEquals( "<repository/>", new String( Files.readAllBytes( downloaded.toPath() ), StandardCharsets.UTF_8 ) );
            downloaded.delete();

            manager.unlockFile( "repository.xml" );
            assertFalse( manager.isLockedFile( "repository.xml" ) );
            assertTrue( manager.toString(), manager.toString().endsWith( "/obr" ) || manager.toString().endsWith( "/obr/" ) );
        }
        finally
        {
            manager.disconnect();
        }

        assertEquals( Collections.singletonList( "file" ), lookedUp );
        assertEquals( 1, released.get() );

        // disconnecting twice does not release the wagon twice
        manager.disconnect();
        assertEquals( 1, released.get() );
    }
}

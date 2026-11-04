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


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Locale;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.UnsupportedProtocolException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;


/**
 * this class is used to manage all connections by wagon.
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class RemoteFileManager
{
    /**
     * save the connection.
     */
    private Wagon m_wagon;

    /**
     * the container used to look up the wagon for a protocol.
     */
    private PlexusContainer m_container;

    /**
     * decrypts the passwords found in the settings, may be null.
     */
    private SettingsDecrypter m_settingsDecrypter;

    /**
     * the project settings.
     */
    private Settings m_settings;

    /**
     * logger instance.
     */
    private Log m_log;

    /**
     * true once the wagon has been given back to the container.
     */
    private boolean m_wagonReleased;


    /**
     * initialize main information.
     * @param container container provided by maven, used to look up the Wagon component of a protocol
     * @param settingsDecrypter decrypts server and proxy passwords, may be null to use them as they are
     * @param settings settings of the current project provides by maven
     * @param log logger
     */
    public RemoteFileManager( PlexusContainer container, SettingsDecrypter settingsDecrypter, Settings settings,
        Log log )
    {
        m_container = container;
        m_settingsDecrypter = settingsDecrypter;
        m_settings = settings;
        m_log = log;
        m_wagon = null;
    }


    /**
     * disconnect the current object.
     */
    public void disconnect()
    {
        try
        {
            if ( m_wagon != null )
            {
                m_wagon.disconnect();
            }
        }
        catch ( ConnectionException e )
        {
            m_log.error( "Error disconnecting Wagon", e );
        }
        finally
        {
            releaseWagon();
        }
    }


    /**
     * Wagons are looked up per use, so give the instance back to the container.
     */
    private void releaseWagon()
    {
        if ( m_wagon != null && !m_wagonReleased )
        {
            m_wagonReleased = true;
            try
            {
                m_container.release( m_wagon );
            }
            catch ( ComponentLifecycleException e )
            {
                m_log.debug( "Unable to release Wagon", e );
            }
        }
    }


    /**
     * Look up the Wagon implementing the given protocol. The wagon providers for file and http(s) are shipped with
     * this plugin, other protocols come from the Wagon providers of the build extensions.
     * @param protocol repository protocol, e.g. file or https
     * @return a new wagon instance
     * @throws UnsupportedProtocolException if no Wagon is available for the protocol
     */
    Wagon lookupWagon( String protocol ) throws UnsupportedProtocolException
    {
        if ( protocol == null )
        {
            throw new UnsupportedProtocolException( "Unspecified protocol" );
        }

        try
        {
            return m_container.lookup( Wagon.class, protocol.toLowerCase( Locale.ENGLISH ) );
        }
        catch ( ComponentLookupException e )
        {
            throw new UnsupportedProtocolException( "Cannot find wagon which supports the requested protocol: "
                + protocol, e );
        }
    }


    /**
     * connect the current object to repository given in constructor.
     * @param id repository id
     * @param url repository url
     * @throws MojoExecutionException
     */
    public void connect( String id, String url ) throws MojoExecutionException
    {
        Repository repository = new Repository( id, url );

        try
        {
            m_wagon = lookupWagon( repository.getProtocol() );
            m_wagonReleased = false;
        }
        catch ( UnsupportedProtocolException e )
        {
            throw new MojoExecutionException( "Unsupported protocol: '" + repository.getProtocol() + "'", e );
        }

        try
        {
            AuthenticationInfo authenticationInfo = getAuthenticationInfo( m_settings, m_settingsDecrypter, id );
            ProxyInfo proxyInfo = getProxyInfo( m_settings, m_settingsDecrypter );
            if ( proxyInfo != null )
            {
                m_wagon.connect( repository, authenticationInfo, proxyInfo );
            }
            else
            {
                m_wagon.connect( repository, authenticationInfo );
            }
        }
        catch ( ConnectionException e )
        {
            releaseWagon();
            throw new MojoExecutionException( "Connection failed", e );
        }
        catch ( AuthenticationException e )
        {
            releaseWagon();
            throw new MojoExecutionException( "Authentication failed", e );
        }
    }


    /**
     * get a file from the current repository connected.
     * @param url url to the targeted file
     * @param suffix suggested file suffix
     * @return get a file descriptor on the required resource
     * @throws MojoExecutionException
     */
    public File get( String url, String suffix ) throws MojoExecutionException
    {
        if ( m_wagon == null )
        {
            m_log.error( "must be connected first!" );
            return null;
        }

        File file = null;
        try
        {
            file = File.createTempFile( String.valueOf( System.currentTimeMillis() ), suffix );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "I/O problem", e );
        }

        try
        {
            m_wagon.get( url, file );
        }
        catch ( TransferFailedException e )
        {
            file.delete(); // cleanup on failure
            throw new MojoExecutionException( "Transfer failed", e );
        }
        catch ( AuthorizationException e )
        {
            file.delete(); // cleanup on failure
            throw new MojoExecutionException( "Authorization failed", e );
        }
        catch ( ResourceDoesNotExistException e )
        {
            file.delete(); // return non-existent file
        }

        return file;
    }


    /**
     * put a file on the current repository connected.
     * @param file file to upload
     * @param url url to copy file
     * @throws MojoExecutionException
     */
    public void put( File file, String url ) throws MojoExecutionException
    {
        if ( m_wagon == null )
        {
            m_log.error( "must be connected first!" );
            return;
        }

        try
        {
            m_wagon.put( file, url );
        }
        catch ( TransferFailedException e )
        {
            throw new MojoExecutionException( "Transfer failed", e );
        }
        catch ( AuthorizationException e )
        {
            throw new MojoExecutionException( "Authorization failed", e );
        }
        catch ( ResourceDoesNotExistException e )
        {
            throw new MojoExecutionException( "Resource does not exist:" + file, e );
        }
    }


    /**
     * Convenience method to map a Proxy object from the user system settings to a ProxyInfo object.
     * The proxy password is used as it is found in the settings.
     * @param settings project settings given by maven
     * @return a proxyInfo object instancied or null if no active proxy is define in the settings.xml
     */
    public static ProxyInfo getProxyInfo( Settings settings )
    {
        return getProxyInfo( settings, null );
    }


    /**
     * Map the active proxy of the user settings to a ProxyInfo object.
     * @param settings project settings given by maven
     * @param decrypter used to decrypt the proxy password, may be null
     * @return a proxyInfo object instancied or null if no active proxy is define in the settings.xml
     */
    static ProxyInfo getProxyInfo( Settings settings, SettingsDecrypter decrypter )
    {
        ProxyInfo proxyInfo = null;
        if ( settings != null && settings.getActiveProxy() != null )
        {
            Proxy settingsProxy = settings.getActiveProxy();
            if ( decrypter != null )
            {
                settingsProxy = decrypter.decrypt( new DefaultSettingsDecryptionRequest( settingsProxy ) ).getProxy();
            }

            proxyInfo = new ProxyInfo();
            proxyInfo.setHost( settingsProxy.getHost() );
            proxyInfo.setType( settingsProxy.getProtocol() );
            proxyInfo.setPort( settingsProxy.getPort() );
            proxyInfo.setNonProxyHosts( settingsProxy.getNonProxyHosts() );
            proxyInfo.setUserName( settingsProxy.getUsername() );
            proxyInfo.setPassword( settingsProxy.getPassword() );
        }

        return proxyInfo;
    }


    /**
     * Find the credentials for a repository id in the user settings, like Maven does for its own repositories.
     * @param settings project settings given by maven
     * @param decrypter used to decrypt the server passwords, may be null
     * @param id repository id, matched case insensitively against the server ids
     * @return the credentials of the matching server, or an empty object if there is none
     */
    static AuthenticationInfo getAuthenticationInfo( Settings settings, SettingsDecrypter decrypter, String id )
    {
        if ( settings != null && id != null )
        {
            List<Server> servers = settings.getServers();
            if ( servers != null )
            {
                for ( Server server : servers )
                {
                    if ( id.equalsIgnoreCase( server.getId() ) )
                    {
                        if ( decrypter != null )
                        {
                            server = decrypter.decrypt( new DefaultSettingsDecryptionRequest( server ) ).getServer();
                        }

                        AuthenticationInfo authInfo = new AuthenticationInfo();
                        authInfo.setUserName( server.getUsername() );
                        authInfo.setPassword( server.getPassword() );
                        authInfo.setPrivateKey( server.getPrivateKey() );
                        authInfo.setPassphrase( server.getPassphrase() );
                        return authInfo;
                    }
                }
            }
        }

        // an empty one, as Wagon expects authentication info to be present
        return new AuthenticationInfo();
    }


    public void lockFile( String fileName, boolean ignoreLock ) throws MojoExecutionException
    {
        if ( !ignoreLock )
        {
            int countError = 0;
            while ( isLockedFile( fileName ) && countError < 2 )
            {
                countError++;
                m_log.warn( "File is currently locked, retry in 10s" );
                try
                {
                    Thread.sleep( 10000 );
                }
                catch ( InterruptedException e )
                {
                    m_log.warn( "Sleep interrupted" );
                }
            }

            if ( countError == 2 )
            {
                m_log.error( "File " + fileName + " is locked. Use -DignoreLock to force uploading" );
                throw new MojoExecutionException( "Remote file locked" );
            }
        }

        File file = null;
        try
        {
            // create a non-empty file used to lock the remote file
            file = File.createTempFile( String.valueOf( System.currentTimeMillis() ), ".lock" );

            Writer writer = new BufferedWriter( new FileWriter( file ) );
            writer.write( "LOCKED" );
            writer.close();

            put( file, fileName + ".lock" );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "I/O problem", e );
        }
        finally
        {
            if ( null != file )
            {
                file.delete();
            }
        }
    }


    public void unlockFile( String fileName ) throws MojoExecutionException
    {
        File file = null;
        try
        {
            // clear the contents of the file used to lock the remote file
            file = File.createTempFile( String.valueOf( System.currentTimeMillis() ), ".lock" );

            Writer writer = new BufferedWriter( new FileWriter( file ) );
            writer.write( " " ); // write 1 byte to force wagon upload
            writer.close();

            put( file, fileName + ".lock" );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "I/O problem", e );
        }
        finally
        {
            if ( null != file )
            {
                file.delete();
            }
        }
    }


    /**
     * this method indicates if the targeted file is locked or not.
     * @param fileName name targeted
     * @return  true if the required file is locked, else false
     * @throws MojoExecutionException
     */
    public boolean isLockedFile( String fileName ) throws MojoExecutionException
    {
        File file = null;
        try
        {
            file = get( fileName + ".lock", ".lock" );

            // file is locked with contents "LOCKED"
            if ( null != file && file.length() <= 2 )
            {
                return false;
            }
        }
        finally
        {
            if ( null != file )
            {
                file.delete();
            }
        }

        return true;
    }


    public String toString()
    {
        return m_wagon.getRepository().getUrl();
    }
}

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
package org.apache.felix.framework.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

/**
 * <p>
 * This is a utility class to centralize all action that should be performed
 * in a <tt>doPrivileged()</tt> block. To perform a secure action, simply
 * create an instance of this class and use the specific method to perform
 * the desired action. When an instance is created, this class will capture
 * the security context and will then use that context when checking for
 * permission to perform the action. Instances of this class should not be
 * passed around since they may grant the receiver a capability to perform
 * privileged actions.
 * </p>
**/
public class SecureAction
{
    private static final byte[] accessor;

    static
    {
        byte[] result;

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             InputStream input = SecureAction.class.getResourceAsStream("accessor.bytes"))
        {

            byte[] buffer = new byte[input.available() > 0 ? input.available() : 1024];
            for (int i = input.read(buffer); i != -1; i = input.read(buffer))
            {
                output.write(buffer, 0, i);
            }
            result = output.toByteArray();
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            result = new byte[0];
        }
        accessor = result;
        getAccessor(URL.class);
    }

    private static final ThreadLocal m_actions = new ThreadLocal()
    {
        public Object initialValue()
        {
            return new Actions();
        }
    };

    protected static transient int BUFSIZE = 4096;

    private AccessControlContext m_acc = null;

    public SecureAction()
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.INITIALIZE_CONTEXT_ACTION, null);
                m_acc = (AccessControlContext) AccessController.doPrivileged(actions);
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            m_acc = AccessController.getContext();
        }
    }

    public String getSystemProperty(String name, String def)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_PROPERTY_ACTION, name, def);
                return (String) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return System.getProperty(name, def);
        }
    }

    public ClassLoader getParentClassLoader(ClassLoader loader)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_PARENT_CLASS_LOADER_ACTION, loader);
                return (ClassLoader) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return loader.getParent();
        }
    }

    public ClassLoader getSystemClassLoader()
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_SYSTEM_CLASS_LOADER_ACTION);
                return (ClassLoader) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return ClassLoader.getSystemClassLoader();
        }
    }

    public ClassLoader getClassLoader(Class clazz)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_CLASS_LOADER_ACTION, clazz);
                return (ClassLoader) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return clazz.getClassLoader();
        }
    }

    public Class forName(String name, ClassLoader classloader) throws ClassNotFoundException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.FOR_NAME_ACTION, name, classloader);
                return (Class) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof ClassNotFoundException)
                {
                    throw (ClassNotFoundException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else if (classloader != null)
        {
            return Class.forName(name, true, classloader);
        }
        else
        {
            return Class.forName(name);
        }
    }

    public URL createURL(String protocol, String host,
        int port, String path, URLStreamHandler handler)
        throws MalformedURLException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.CREATE_URL_ACTION, protocol, host,
                    new Integer(port), path, handler);
                return (URL) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof MalformedURLException)
                {
                    throw (MalformedURLException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return new URL(protocol, host, port, path, handler);
        }
    }

    public URL createURL(URL context, String spec, URLStreamHandler handler)
        throws MalformedURLException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.CREATE_URL_WITH_CONTEXT_ACTION, context,
                    spec, handler);
                return (URL) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof MalformedURLException)
                {
                    throw (MalformedURLException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return new URL(context, spec, handler);
        }
    }

    public Process exec(String command) throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.EXEC_ACTION, command);
                return (Process) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return Runtime.getRuntime().exec(command);
        }
    }

    public String getAbsolutePath(File file)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_ABSOLUTE_PATH_ACTION, file);
                return (String) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return file.getAbsolutePath();
        }
    }

    public boolean fileExists(File file)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.FILE_EXISTS_ACTION, file);
                return ((Boolean) AccessController.doPrivileged(actions, m_acc))
                    .booleanValue();
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return file.exists();
        }
    }

    public boolean isFile(File file)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.FILE_IS_FILE_ACTION, file);
                return ((Boolean) AccessController.doPrivileged(actions, m_acc))
                        .booleanValue();
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return file.isFile();
        }
    }

    public boolean isFileDirectory(File file)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.FILE_IS_DIRECTORY_ACTION, file);
                return ((Boolean) AccessController.doPrivileged(actions, m_acc))
                    .booleanValue();
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return file.isDirectory();
        }
    }

    public boolean mkdir(File file)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.MAKE_DIRECTORY_ACTION, file);
                return ((Boolean) AccessController.doPrivileged(actions, m_acc))
                    .booleanValue();
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return file.mkdir();
        }
    }

    public boolean mkdirs(File file)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.MAKE_DIRECTORIES_ACTION, file);
                return ((Boolean) AccessController.doPrivileged(actions, m_acc))
                    .booleanValue();
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return file.mkdirs();
        }
    }

    public File[] listDirectory(File file)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.LIST_DIRECTORY_ACTION, file);
                return (File[]) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return file.listFiles();
        }
    }

    public boolean renameFile(File oldFile, File newFile)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.RENAME_FILE_ACTION, oldFile, newFile);
                return ((Boolean) AccessController.doPrivileged(actions, m_acc))
                    .booleanValue();
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return oldFile.renameTo(newFile);
        }
    }

    public InputStream getInputStream(File file) throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_INPUT_ACTION, file);
                return (InputStream) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof IOException)
                {
                    throw (IOException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return Files.newInputStream(file.toPath());
        }
    }

    public OutputStream getOutputStream(File file) throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_OUTPUT_ACTION, file);
                return (OutputStream) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof IOException)
                {
                    throw (IOException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return Files.newOutputStream(file.toPath());
        }
    }

    public FileInputStream getFileInputStream(File file) throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_FILE_INPUT_ACTION, file);
                return (FileInputStream) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof IOException)
                {
                    throw (IOException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return new FileInputStream(file);
        }
    }

    public FileOutputStream getFileOutputStream(File file) throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_FILE_OUTPUT_ACTION, file);
                return (FileOutputStream) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof IOException)
                {
                    throw (IOException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return new FileOutputStream(file);
        }
    }

    public FileChannel getFileChannel(File file) throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_FILE_CHANNEL_ACTION, file);
                return (FileChannel) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof IOException)
                {
                    throw (IOException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }
    }

    public URI toURI(File file)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.TO_URI_ACTION, file);
                return (URI) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return file.toURI();
        }
    }

    public InputStream getURLConnectionInputStream(URLConnection conn)
        throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_URL_INPUT_ACTION, conn);
                return (InputStream) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof IOException)
                {
                    throw (IOException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return conn.getInputStream();
        }
    }

    public boolean deleteFile(File target)
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.DELETE_FILE_ACTION, target);
                return ((Boolean) AccessController.doPrivileged(actions, m_acc))
                    .booleanValue();
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return target.delete();
        }
    }

    public File createTempFile(String prefix, String suffix, File dir)
        throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.CREATE_TMPFILE_ACTION, prefix, suffix, dir);
                return (File) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof IOException)
                {
                    throw (IOException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return File.createTempFile(prefix, suffix, dir);
        }
    }

    public void deleteFileOnExit(File file)
        throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.DELETE_FILEONEXIT_ACTION, file);
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof IOException)
                {
                    throw (IOException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            file.deleteOnExit();
        }
    }

    public URLConnection openURLConnection(URL url) throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.OPEN_URLCONNECTION_ACTION, url);
                return (URLConnection) AccessController.doPrivileged(actions,
                    m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof IOException)
                {
                    throw (IOException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return url.openConnection();
        }
    }

    public ZipFile openZipFile(File file) throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.OPEN_ZIPFILE_ACTION, file);
                return (ZipFile) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof IOException)
                {
                    throw (IOException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return new ZipFile(file);
        }
    }

    public JarFile openJarFile(File file) throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.OPEN_JARFILE_ACTION, file);
                return (JarFile) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                if (ex.getException() instanceof IOException)
                {
                    throw (IOException) ex.getException();
                }
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return new JarFile(file);
        }
    }

    public void startActivator(BundleActivator activator, BundleContext context)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.START_ACTIVATOR_ACTION, activator, context);
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                throw ex.getException();
            }
        }
        else
        {
            activator.start(context);
        }
    }

    public void stopActivator(BundleActivator activator, BundleContext context)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.STOP_ACTIVATOR_ACTION, activator, context);
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                throw ex.getException();
            }
        }
        else
        {
            activator.stop(context);
        }
    }

    public Policy getPolicy()
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                Actions actions = (Actions) m_actions.get();
                actions.set(Actions.GET_POLICY_ACTION, null);
                return (Policy) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException ex)
            {
                throw (RuntimeException) ex.getException();
            }
        }
        else
        {
            return Policy.getPolicy();
        }
    }

    public void addURLToURLClassLoader(URL extension, ClassLoader loader) throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.ADD_EXTENSION_URL_ACTION, extension, loader);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            Method addURL =
                URLClassLoader.class.getDeclaredMethod("addURL",
                new Class[] {URL.class});
            getAccessor(URLClassLoader.class).accept(new AccessibleObject[]{addURL});
            addURL.invoke(loader, new Object[]{extension});
        }
    }

    public Constructor getConstructor(Class target, Class[] types) throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.GET_CONSTRUCTOR_ACTION, target, types);
            try
            {
                return (Constructor) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            return target.getConstructor(types);
        }
    }

    public Constructor getDeclaredConstructor(Class target, Class[] types) throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.GET_DECLARED_CONSTRUCTOR_ACTION, target, types);
            try
            {
                return (Constructor) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            return target.getDeclaredConstructor(types);
        }
    }

    public Method getMethod(Class target, String method, Class[] types) throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.GET_METHOD_ACTION, target, method, types);
            try
            {
                return (Method) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            return target.getMethod(method, types);
        }
    }

    public Method getDeclaredMethod(Class target, String method, Class[] types) throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.GET_DECLARED_METHOD_ACTION, target, method, types);
            try
            {
                return (Method) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            return target.getDeclaredMethod(method, types);
        }
    }

    public void setAccesssible(Executable ao)
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.SET_ACCESSIBLE_ACTION, ao);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw (RuntimeException) e.getException();
            }
        }
        else
        {
            getAccessor(ao.getDeclaringClass()).accept(new AccessibleObject[]{ao});
        }
    }

    public Object invoke(Method method, Object target, Object[] params) throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_METHOD_ACTION, method, target, params);
            try
            {
                return AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            getAccessor(method.getDeclaringClass()).accept(new AccessibleObject[]{method});

            return method.invoke(target, params);
        }
    }

    public Object invokeDirect(Method method, Object target, Object[] params) throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_DIRECTMETHOD_ACTION, method, target, params);
            try
            {
                return AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            return method.invoke(target, params);
        }
    }

    public Object invoke(Constructor constructor, Object[] params) throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_CONSTRUCTOR_ACTION, constructor, params);
            try
            {
                return AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            return constructor.newInstance(params);
        }
    }

    public Object getDeclaredField(Class targetClass, String name, Object target)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.GET_FIELD_ACTION, targetClass, name, target);
            try
            {
                return AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            Field field = targetClass.getDeclaredField(name);
            getAccessor(targetClass).accept(new AccessibleObject[]{field});
            return field.get(target);
        }
    }

    public Object swapStaticFieldIfNotClass(Class targetClazz,
        Class targetType, Class condition, String lockName) throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.SWAP_FIELD_ACTION, targetClazz, targetType,
                condition, lockName);
            try
            {
                return AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            return _swapStaticFieldIfNotClass(targetClazz, targetType,
                condition, lockName);
        }
    }

    private static volatile Consumer<AccessibleObject[]> m_accessorCache = null;

    @SuppressWarnings("unchecked")
    private static Consumer<AccessibleObject[]> getAccessor(Class clazz)
    {
        String packageName = clazz.getPackage().getName();
        if ("java.net".equals(packageName) || "jdk.internal.loader".equals(packageName))
        {
            if (m_accessorCache == null)
            {
                try
                {
                    // Use reflection on Unsafe to avoid having to compile against it
                    Class<?> unsafeClass = Class.forName("sun.misc.Unsafe"); //$NON-NLS-1$
                    Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe"); //$NON-NLS-1$
                    // NOTE: deep reflection is allowed on sun.misc package for java 9.
                    theUnsafe.setAccessible(true);
                    Object unsafe = theUnsafe.get(null);
                    Class<Consumer<AccessibleObject[]>> result;
                    try {
                        Method defineAnonymousClass = unsafeClass.getMethod("defineAnonymousClass", Class.class, byte[].class, Object[].class); //$NON-NLS-1$
                        result = (Class<Consumer<AccessibleObject[]>>) defineAnonymousClass.invoke(unsafe, URL.class, accessor , null);;
                    }
                    catch (NoSuchMethodException ex)
                    {
                        long offset = (long) unsafeClass.getMethod("staticFieldOffset", Field.class)
                                .invoke(unsafe, MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP"));

                        MethodHandles.Lookup lookup = (MethodHandles.Lookup) unsafeClass.getMethod("getObject", Object.class, long.class)
                                .invoke(unsafe, MethodHandles.Lookup.class, offset);
                        lookup = lookup.in(URL.class);
                        Class<?> classOption = Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption"); //$NON-NLS-1$
                        Object classOptions = Array.newInstance(classOption, 0);
                        Method defineHiddenClass = MethodHandles.Lookup.class.getMethod("defineHiddenClass", byte[].class, boolean.class, //$NON-NLS-1$
                                classOptions.getClass());
                        lookup = (MethodHandles.Lookup) defineHiddenClass.invoke(lookup, accessor, Boolean.FALSE, classOptions);
                        result = (Class<Consumer<AccessibleObject[]>>) lookup.lookupClass();
                    }
                    m_accessorCache = result.getConstructor().newInstance();
                }
                catch (Throwable t)
                {
                    m_accessorCache = objects -> AccessibleObject.setAccessible(objects, true);
                }
            }
            return m_accessorCache;
        }
        else
        {
            return objects -> AccessibleObject.setAccessible(objects, true);
        }
    }

    private static Object _swapStaticFieldIfNotClass(Class targetClazz,
        Class targetType, Class condition, String lockName) throws Exception
    {

        Object lock = null;
        if (lockName != null)
        {
            try
            {
                Field lockField =
                    targetClazz.getDeclaredField(lockName);
                getAccessor(targetClazz).accept(new AccessibleObject[]{lockField});
                lock = lockField.get(null);
            }
            catch (NoSuchFieldException ex)
            {
            }
        }
        if (lock == null)
        {
            lock = targetClazz;
        }
        synchronized (lock)
        {
            Field[] fields = targetClazz.getDeclaredFields();

            getAccessor(targetClazz).accept(fields);

            Object result = null;
            for (int i = 0; (i < fields.length) && (result == null); i++)
            {
                if (Modifier.isStatic(fields[i].getModifiers()) &&
                    (fields[i].getType() == targetType))
                {
                    result = fields[i].get(null);

                    if (result != null)
                    {
                        if ((condition == null) ||
                            !result.getClass().getName().equals(condition.getName()))
                        {
                            fields[i].set(null, null);
                        }
                    }
                }
            }
            if (result != null)
            {
                if ((condition == null) || !result.getClass().getName().equals(condition.getName()))
                {
                    // reset cache
                    for (int i = 0; i < fields.length; i++)
                    {
                        if (Modifier.isStatic(fields[i].getModifiers()) &&
                            (fields[i].getType() == Hashtable.class))
                        {
                            Hashtable cache = (Hashtable) fields[i].get(null);
                            if (cache != null)
                            {
                                cache.clear();
                            }
                        }
                    }
                }
                return result;
            }
        }
        return null;
    }

    public void flush(Class targetClazz, Object lock) throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.FLUSH_FIELD_ACTION, targetClazz, lock);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            _flush(targetClazz, lock);
        }
    }

    private static void _flush(Class targetClazz, Object lock) throws Exception
    {
        synchronized (lock)
        {
            Field[] fields = targetClazz.getDeclaredFields();
            getAccessor(targetClazz).accept(fields);
            // reset cache
            for (int i = 0; i < fields.length; i++)
            {
                if (Modifier.isStatic(fields[i].getModifiers()) &&
                    ((fields[i].getType() == Hashtable.class) || (fields[i].getType() == HashMap.class)))
                {
                    if (fields[i].getType() == Hashtable.class)
                    {
                        Hashtable cache = (Hashtable) fields[i].get(null);
                        if (cache != null)
                        {
                            cache.clear();
                        }
                    }
                    else
                    {
                        HashMap cache = (HashMap) fields[i].get(null);
                        if (cache != null)
                        {
                            cache.clear();
                        }
                    }
                }
            }
        }
    }

    public void invokeBundleCollisionHook(
        org.osgi.framework.hooks.bundle.CollisionHook ch, int operationType,
        Bundle targetBundle, Collection<Bundle> collisionCandidates)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_BUNDLE_COLLISION_HOOK, ch, operationType, targetBundle, collisionCandidates);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            ch.filterCollisions(operationType, targetBundle, collisionCandidates);
        }
    }

    public void invokeBundleFindHook(
        org.osgi.framework.hooks.bundle.FindHook fh,
        BundleContext bc, Collection<Bundle> bundles)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_BUNDLE_FIND_HOOK, fh, bc, bundles);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            fh.find(bc, bundles);
        }
    }

    public void invokeBundleEventHook(
        org.osgi.framework.hooks.bundle.EventHook eh,
        BundleEvent event, Collection<BundleContext> contexts)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_BUNDLE_EVENT_HOOK, eh, event, contexts);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            eh.event(event, contexts);
        }
    }

    public void invokeWeavingHook(
        org.osgi.framework.hooks.weaving.WeavingHook wh,
        org.osgi.framework.hooks.weaving.WovenClass wc)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_WEAVING_HOOK, wh, wc);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            wh.weave(wc);
        }
    }

    public void invokeServiceEventHook(
        org.osgi.framework.hooks.service.EventHook eh,
        ServiceEvent event, Collection<BundleContext> contexts)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_SERVICE_EVENT_HOOK, eh, event, contexts);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            eh.event(event, contexts);
        }
    }

    public void invokeServiceFindHook(
        org.osgi.framework.hooks.service.FindHook fh,
        BundleContext context, String name, String filter,
        boolean allServices, Collection<ServiceReference<?>> references)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(
                Actions.INVOKE_SERVICE_FIND_HOOK, fh, context, name, filter,
                (allServices) ? Boolean.TRUE : Boolean.FALSE, references);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            fh.find(context, name, filter, allServices, references);
        }
    }

    public void invokeServiceListenerHookAdded(
        org.osgi.framework.hooks.service.ListenerHook lh,
        Collection<ListenerHook.ListenerInfo> listeners)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_SERVICE_LISTENER_HOOK_ADDED, lh, listeners);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            lh.added(listeners);
        }
    }

    public void invokeServiceListenerHookRemoved(
        org.osgi.framework.hooks.service.ListenerHook lh,
        Collection<ListenerHook.ListenerInfo> listeners)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_SERVICE_LISTENER_HOOK_REMOVED, lh, listeners);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            lh.removed(listeners);
        }
    }

    public void invokeServiceEventListenerHook(
        org.osgi.framework.hooks.service.EventListenerHook elh,
        ServiceEvent event,
        Map<BundleContext, Collection<ListenerHook.ListenerInfo>> listeners)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_SERVICE_EVENT_LISTENER_HOOK, elh, event, listeners);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            elh.event(event, listeners);
        }
    }

    public ResolverHook invokeResolverHookFactory(
        org.osgi.framework.hooks.resolver.ResolverHookFactory rhf,
        Collection<BundleRevision> triggers)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_RESOLVER_HOOK_FACTORY, rhf, triggers);
            try
            {
                return (ResolverHook) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            return rhf.begin(triggers);
        }
    }

    public void invokeResolverHookResolvable(
        org.osgi.framework.hooks.resolver.ResolverHook rh,
        Collection<BundleRevision> candidates)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_RESOLVER_HOOK_RESOLVABLE, rh, candidates);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            rh.filterResolvable(candidates);
        }
    }

    public void invokeResolverHookSingleton(
        org.osgi.framework.hooks.resolver.ResolverHook rh,
        BundleCapability singleton,
        Collection<BundleCapability> collisions)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_RESOLVER_HOOK_SINGLETON, rh, singleton, collisions);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            rh.filterSingletonCollisions(singleton, collisions);
        }
    }

    public void invokeResolverHookMatches(
        org.osgi.framework.hooks.resolver.ResolverHook rh,
        BundleRequirement req,
        Collection<BundleCapability> candidates)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_RESOLVER_HOOK_MATCHES, rh, req, candidates);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            rh.filterMatches(req, candidates);
        }
    }

    public void invokeResolverHookEnd(
        org.osgi.framework.hooks.resolver.ResolverHook rh)
        throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_RESOLVER_HOOK_END, rh);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            rh.end();
        }
    }

    public void invokeWovenClassListener(
            org.osgi.framework.hooks.weaving.WovenClassListener wcl,
            org.osgi.framework.hooks.weaving.WovenClass wc)
            throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.INVOKE_WOVEN_CLASS_LISTENER, wcl, wc);
            try
            {
                AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            wcl.modified(wc);
        }
    }

    public <T> T run(PrivilegedAction<T> action)
    {
        if (System.getSecurityManager() != null)
        {
            return AccessController.doPrivileged(action);
        }
        else
        {
            return action.run();
        }
    }

    public <T> T run(PrivilegedExceptionAction<T> action) throws Exception
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                return AccessController.doPrivileged(action);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            return action.run();
        }
    }

    public String getCanonicalPath(File dataFile) throws IOException
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.GET_CANONICAL_PATH, dataFile);
            try
            {
                return (String) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw (IOException) e.getException();
            }
        }
        else
        {
            return dataFile.getCanonicalPath();
        }
    }

    public Object createProxy(ClassLoader classLoader, 
            Class<?>[] interfaces, InvocationHandler handler)
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.CREATE_PROXY, classLoader, interfaces, handler);
            try
            {
                return AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
            	throw (RuntimeException) e.getException();
            }
        }
        else
        {
            return Proxy.newProxyInstance(classLoader, interfaces, handler);
        }
    }

    public long getLastModified(File file)
    {
        if (System.getSecurityManager() != null)
        {
            Actions actions = (Actions) m_actions.get();
            actions.set(Actions.LAST_MODIFIED, file);
            try
            {
                return (Long) AccessController.doPrivileged(actions, m_acc);
            }
            catch (PrivilegedActionException e)
            {
                throw (RuntimeException) e.getException();
            }
        }
        else
        {
            return file.lastModified();
        }
    }

    private static class Actions implements PrivilegedExceptionAction
    {
        public static final int INITIALIZE_CONTEXT_ACTION = 0;
        public static final int ADD_EXTENSION_URL_ACTION = 1;
        public static final int CREATE_TMPFILE_ACTION = 2;
        public static final int CREATE_URL_ACTION = 3;
        public static final int CREATE_URL_WITH_CONTEXT_ACTION = 4;
        public static final int DELETE_FILE_ACTION = 5;
        public static final int EXEC_ACTION = 6;
        public static final int FILE_EXISTS_ACTION = 7;
        public static final int FILE_IS_DIRECTORY_ACTION = 8;
        public static final int FOR_NAME_ACTION = 9;
        public static final int GET_ABSOLUTE_PATH_ACTION = 10;
        public static final int GET_CONSTRUCTOR_ACTION = 11;
        public static final int GET_DECLARED_CONSTRUCTOR_ACTION = 12;
        public static final int GET_DECLARED_METHOD_ACTION = 13;
        public static final int GET_FIELD_ACTION = 14;
        public static final int GET_FILE_INPUT_ACTION = 15;
        public static final int GET_FILE_OUTPUT_ACTION = 16;
        public static final int TO_URI_ACTION = 17;
        public static final int GET_METHOD_ACTION = 18;
        public static final int GET_POLICY_ACTION = 19;
        public static final int GET_PROPERTY_ACTION = 20;
        public static final int GET_PARENT_CLASS_LOADER_ACTION = 21;
        public static final int GET_SYSTEM_CLASS_LOADER_ACTION = 22;
        public static final int GET_URL_INPUT_ACTION = 23;
        public static final int INVOKE_CONSTRUCTOR_ACTION = 24;
        public static final int INVOKE_DIRECTMETHOD_ACTION = 25;
        public static final int INVOKE_METHOD_ACTION = 26;
        public static final int LIST_DIRECTORY_ACTION = 27;
        public static final int MAKE_DIRECTORIES_ACTION = 28;
        public static final int MAKE_DIRECTORY_ACTION = 29;
        public static final int OPEN_ZIPFILE_ACTION = 30;
        public static final int OPEN_URLCONNECTION_ACTION = 31;
        public static final int RENAME_FILE_ACTION = 32;
        public static final int SET_ACCESSIBLE_ACTION = 33;
        public static final int START_ACTIVATOR_ACTION = 34;
        public static final int STOP_ACTIVATOR_ACTION = 35;
        public static final int SWAP_FIELD_ACTION = 36;
        public static final int SYSTEM_EXIT_ACTION = 37;
        public static final int FLUSH_FIELD_ACTION = 38;
        public static final int GET_CLASS_LOADER_ACTION = 39;
        public static final int INVOKE_BUNDLE_FIND_HOOK = 40;
        public static final int INVOKE_BUNDLE_EVENT_HOOK = 41;
        public static final int INVOKE_WEAVING_HOOK = 42;
        public static final int INVOKE_SERVICE_EVENT_HOOK = 43;
        public static final int INVOKE_SERVICE_FIND_HOOK = 44;
        public static final int INVOKE_SERVICE_LISTENER_HOOK_ADDED = 45;
        public static final int INVOKE_SERVICE_LISTENER_HOOK_REMOVED = 46;
        public static final int INVOKE_SERVICE_EVENT_LISTENER_HOOK = 47;
        public static final int INVOKE_RESOLVER_HOOK_FACTORY = 48;
        public static final int INVOKE_RESOLVER_HOOK_RESOLVABLE = 49;
        public static final int INVOKE_RESOLVER_HOOK_SINGLETON = 50;
        public static final int INVOKE_RESOLVER_HOOK_MATCHES = 51;
        public static final int INVOKE_RESOLVER_HOOK_END = 52;
        public static final int INVOKE_BUNDLE_COLLISION_HOOK = 53;
        public static final int OPEN_JARFILE_ACTION = 54;
        public static final int DELETE_FILEONEXIT_ACTION = 55;
        public static final int INVOKE_WOVEN_CLASS_LISTENER = 56;
        public static final int GET_CANONICAL_PATH = 57;
        public static final int CREATE_PROXY = 58;
        public static final int LAST_MODIFIED = 59;
        public static final int FILE_IS_FILE_ACTION = 60;
        public static final int GET_FILE_CHANNEL_ACTION = 61;
        private static final int GET_INPUT_ACTION = 62;
        private static final int GET_OUTPUT_ACTION = 63;

        private int m_action = -1;
        private Object m_arg1 = null;
        private Object m_arg2 = null;
        private Object m_arg3 = null;
        private Object m_arg4 = null;
        private Object m_arg5 = null;
        private Object m_arg6 = null;

        public void set(int action)
        {
            m_action = action;
        }

        public void set(int action, Object arg1)
        {
            m_action = action;
            m_arg1 = arg1;
        }

        public void set(int action, Object arg1, Object arg2)
        {
            m_action = action;
            m_arg1 = arg1;
            m_arg2 = arg2;
        }

        public void set(int action, Object arg1, Object arg2, Object arg3)
        {
            m_action = action;
            m_arg1 = arg1;
            m_arg2 = arg2;
            m_arg3 = arg3;
        }

        public void set(int action, Object arg1, Object arg2, Object arg3,
            Object arg4)
        {
            m_action = action;
            m_arg1 = arg1;
            m_arg2 = arg2;
            m_arg3 = arg3;
            m_arg4 = arg4;
        }

        public void set(int action, Object arg1, Object arg2, Object arg3,
            Object arg4, Object arg5)
        {
            m_action = action;
            m_arg1 = arg1;
            m_arg2 = arg2;
            m_arg3 = arg3;
            m_arg4 = arg4;
            m_arg5 = arg5;
        }

        public void set(int action, Object arg1, Object arg2, Object arg3,
            Object arg4, Object arg5, Object arg6)
        {
            m_action = action;
            m_arg1 = arg1;
            m_arg2 = arg2;
            m_arg3 = arg3;
            m_arg4 = arg4;
            m_arg5 = arg5;
            m_arg6 = arg6;
        }

        private void unset()
        {
            m_action = -1;
            m_arg1 = null;
            m_arg2 = null;
            m_arg3 = null;
            m_arg4 = null;
            m_arg5 = null;
            m_arg6 = null;
        }

        public Object run() throws Exception
        {
            int action =  m_action;
            Object arg1 = m_arg1;
            Object arg2 = m_arg2;
            Object arg3 = m_arg3;
            Object arg4 = m_arg4;
            Object arg5 = m_arg5;
            Object arg6 = m_arg6;

            unset();

            switch (action)
            {
                case INITIALIZE_CONTEXT_ACTION:
                    return AccessController.getContext();
                case ADD_EXTENSION_URL_ACTION:
                    Method addURL =
                        URLClassLoader.class.getDeclaredMethod("addURL",
                        new Class[] {URL.class});
                    getAccessor(URLClassLoader.class).accept(new AccessibleObject[]{addURL});
                    addURL.invoke(arg2, new Object[]{arg1});
                    return null;
                case CREATE_TMPFILE_ACTION:
                    return File.createTempFile((String) arg1, (String) arg2, (File) arg3);
                case CREATE_URL_ACTION:
                    return new URL((String) arg1, (String) arg2,
                        ((Integer) arg3).intValue(), (String) arg4,
                        (URLStreamHandler) arg5);
                case CREATE_URL_WITH_CONTEXT_ACTION:
                    return new URL((URL) arg1, (String) arg2, (URLStreamHandler) arg3);
                case DELETE_FILE_ACTION:
                    return ((File) arg1).delete() ? Boolean.TRUE : Boolean.FALSE;
                case EXEC_ACTION:
                    return Runtime.getRuntime().exec((String) arg1);
                case FILE_EXISTS_ACTION:
                    return ((File) arg1).exists() ? Boolean.TRUE : Boolean.FALSE;
                case FILE_IS_DIRECTORY_ACTION:
                    return ((File) arg1).isDirectory() ? Boolean.TRUE : Boolean.FALSE;
                case FOR_NAME_ACTION:
                    return (arg2 == null) ? Class.forName((String) arg1) : Class.forName((String) arg1, true,
                        (ClassLoader) arg2);
                case GET_ABSOLUTE_PATH_ACTION:
                    return ((File) arg1).getAbsolutePath();
                case GET_CONSTRUCTOR_ACTION:
                    return ((Class) arg1).getConstructor((Class[]) arg2);
                case GET_DECLARED_CONSTRUCTOR_ACTION:
                    return ((Class) arg1).getDeclaredConstructor((Class[]) arg2);
                case GET_DECLARED_METHOD_ACTION:
                    return ((Class) arg1).getDeclaredMethod((String) arg2, (Class[]) arg3);
                case GET_FIELD_ACTION:
                    Field field = ((Class) arg1).getDeclaredField((String) arg2);
                    getAccessor((Class) arg1).accept(new AccessibleObject[]{field});
                    return field.get(arg3);
                case GET_FILE_INPUT_ACTION:
                    return new FileInputStream((File) arg1);
                case GET_FILE_OUTPUT_ACTION:
                    return new FileOutputStream((File) arg1);
                case TO_URI_ACTION:
                    return ((File) arg1).toURI();
                case GET_METHOD_ACTION:
                    return ((Class) arg1).getMethod((String) arg2, (Class[]) arg3);
                case GET_POLICY_ACTION:
                    return Policy.getPolicy();
                case GET_PROPERTY_ACTION:
                    return System.getProperty((String) arg1, (String) arg2);
                case GET_PARENT_CLASS_LOADER_ACTION:
                    return ((ClassLoader) arg1).getParent();
                case GET_SYSTEM_CLASS_LOADER_ACTION:
                    return ClassLoader.getSystemClassLoader();
                case GET_URL_INPUT_ACTION:
                    return ((URLConnection) arg1).getInputStream();
                case INVOKE_CONSTRUCTOR_ACTION:
                    return ((Constructor) arg1).newInstance((Object[]) arg2);
                case INVOKE_DIRECTMETHOD_ACTION:
                    return ((Method) arg1).invoke(arg2, (Object[]) arg3);
                case INVOKE_METHOD_ACTION:
                    getAccessor(((Method) arg1).getDeclaringClass()).accept(new AccessibleObject[]{(Method) arg1});
                    return ((Method) arg1).invoke(arg2, (Object[]) arg3);
                case LIST_DIRECTORY_ACTION:
                    return ((File) arg1).listFiles();
                case MAKE_DIRECTORIES_ACTION:
                    return ((File) arg1).mkdirs() ? Boolean.TRUE : Boolean.FALSE;
                case MAKE_DIRECTORY_ACTION:
                    return ((File) arg1).mkdir() ? Boolean.TRUE : Boolean.FALSE;
                case OPEN_ZIPFILE_ACTION:
                    return new ZipFile((File) arg1);
                case OPEN_URLCONNECTION_ACTION:
                    return ((URL) arg1).openConnection();
                case RENAME_FILE_ACTION:
                    return ((File) arg1).renameTo((File) arg2) ? Boolean.TRUE : Boolean.FALSE;
                case SET_ACCESSIBLE_ACTION:
                    getAccessor(((Executable) arg1).getDeclaringClass()).accept(new AccessibleObject[]{(Executable) arg1});
                    return null;
                case START_ACTIVATOR_ACTION:
                    ((BundleActivator) arg1).start((BundleContext) arg2);
                    return null;
                case STOP_ACTIVATOR_ACTION:
                    ((BundleActivator) arg1).stop((BundleContext) arg2);
                    return null;
                case SWAP_FIELD_ACTION:
                    return _swapStaticFieldIfNotClass((Class) arg1,
                        (Class) arg2, (Class) arg3, (String) arg4);
                case SYSTEM_EXIT_ACTION:
                    System.exit(((Integer) arg1).intValue());
                case FLUSH_FIELD_ACTION:
                    _flush(((Class) arg1), arg2);
                    return null;
                case GET_CLASS_LOADER_ACTION:
                    return ((Class) arg1).getClassLoader();
                case INVOKE_BUNDLE_FIND_HOOK:
                    ((org.osgi.framework.hooks.bundle.FindHook) arg1).find(
                        (BundleContext) arg2, (Collection<Bundle>) arg3);
                    return null;
                case INVOKE_BUNDLE_EVENT_HOOK:
                    ((org.osgi.framework.hooks.bundle.EventHook) arg1).event(
                        (BundleEvent) arg2, (Collection<BundleContext>) arg3);
                    return null;
                case INVOKE_WEAVING_HOOK:
                    ((org.osgi.framework.hooks.weaving.WeavingHook) arg1).weave(
                        (org.osgi.framework.hooks.weaving.WovenClass) arg2);
                    return null;
                case INVOKE_SERVICE_EVENT_HOOK:
                    ((org.osgi.framework.hooks.service.EventHook) arg1).event(
                        (ServiceEvent) arg2, (Collection<BundleContext>) arg3);
                    return null;
                case INVOKE_SERVICE_FIND_HOOK:
                    ((org.osgi.framework.hooks.service.FindHook) arg1).find(
                        (BundleContext) arg2, (String) arg3, (String) arg4,
                        ((Boolean) arg5).booleanValue(),
                        (Collection<ServiceReference<?>>) arg6);
                    return null;
                case INVOKE_SERVICE_LISTENER_HOOK_ADDED:
                    ((org.osgi.framework.hooks.service.ListenerHook) arg1).added(
                        (Collection<ListenerHook.ListenerInfo>) arg2);
                    return null;
                case INVOKE_SERVICE_LISTENER_HOOK_REMOVED:
                    ((org.osgi.framework.hooks.service.ListenerHook) arg1).removed(
                        (Collection<ListenerHook.ListenerInfo>) arg2);
                    return null;
                case INVOKE_SERVICE_EVENT_LISTENER_HOOK:
                    ((org.osgi.framework.hooks.service.EventListenerHook) arg1).event(
                        (ServiceEvent) arg2,
                        (Map<BundleContext, Collection<ListenerHook.ListenerInfo>>) arg3);
                    return null;
                case INVOKE_RESOLVER_HOOK_FACTORY:
                    return ((org.osgi.framework.hooks.resolver.ResolverHookFactory) arg1).begin(
                        (Collection<BundleRevision>) arg2);
                case INVOKE_RESOLVER_HOOK_RESOLVABLE:
                    ((org.osgi.framework.hooks.resolver.ResolverHook) arg1).filterResolvable(
                        (Collection<BundleRevision>) arg2);
                    return null;
                case INVOKE_RESOLVER_HOOK_SINGLETON:
                    ((org.osgi.framework.hooks.resolver.ResolverHook) arg1)
                        .filterSingletonCollisions(
                            (BundleCapability) arg2,
                            (Collection<BundleCapability>) arg3);
                    return null;
                case INVOKE_RESOLVER_HOOK_MATCHES:
                    ((org.osgi.framework.hooks.resolver.ResolverHook) arg1).filterMatches(
                        (BundleRequirement) arg2,
                        (Collection<BundleCapability>) arg3);
                    return null;
                case INVOKE_RESOLVER_HOOK_END:
                    ((org.osgi.framework.hooks.resolver.ResolverHook) arg1).end();
                    return null;
                case INVOKE_BUNDLE_COLLISION_HOOK:
                    ((org.osgi.framework.hooks.bundle.CollisionHook) arg1).filterCollisions((Integer) arg2,
                        (Bundle) arg3, (Collection<Bundle>) arg4);
                    return null;
                case OPEN_JARFILE_ACTION:
                    return new JarFile((File) arg1);
                case DELETE_FILEONEXIT_ACTION:
                    ((File) arg1).deleteOnExit();
                    return null;
                case INVOKE_WOVEN_CLASS_LISTENER:
                    ((org.osgi.framework.hooks.weaving.WovenClassListener) arg1).modified(
                        (org.osgi.framework.hooks.weaving.WovenClass) arg2);
                    return null;
                case GET_CANONICAL_PATH:
                    return ((File) arg1).getCanonicalPath();
                case CREATE_PROXY:
                    return Proxy.newProxyInstance((ClassLoader)arg1, (Class<?>[])arg2,
                            (InvocationHandler) arg3);
                case LAST_MODIFIED:
                    return ((File) arg1).lastModified();
                case FILE_IS_FILE_ACTION:
                    return ((File) arg1).isFile() ? Boolean.TRUE : Boolean.FALSE;
                case GET_FILE_CHANNEL_ACTION:
                    return FileChannel.open(((File) arg1).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                case GET_INPUT_ACTION:
                    return Files.newInputStream(((File) arg1).toPath());
                case GET_OUTPUT_ACTION:
                    return Files.newOutputStream(((File) arg1).toPath());
            }

            return null;
        }
    }
}

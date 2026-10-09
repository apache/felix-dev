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
import java.security.PrivilegedAction;
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
 * This is a utility class centralizing the I/O, reflection and class loading
 * operations performed by the framework.
 * </p>
 * <p>
 * Historically every method here wrapped its operation in a
 * <tt>doPrivileged()</tt> block against a captured security context. Java SE 24
 * permanently disabled the Security Manager (JEP 486), so those blocks no longer
 * had any effect and have been removed; each method now performs its operation
 * directly. The class is retained as the framework's single point of access for
 * these operations.
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

    protected static transient int BUFSIZE = 4096;

    public String getSystemProperty(String name, String def)
    {
        return System.getProperty(name, def);
    }

    public ClassLoader getParentClassLoader(ClassLoader loader)
    {
        return loader.getParent();
    }

    public ClassLoader getSystemClassLoader()
    {
        return ClassLoader.getSystemClassLoader();
    }

    public ClassLoader getClassLoader(Class<?> clazz)
    {
        return clazz.getClassLoader();
    }

    public Class<?> forName(String name, ClassLoader classloader) throws ClassNotFoundException
    {
        if (classloader != null)
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
        return new URL(protocol, host, port, path, handler);
    }

    public URL createURL(URL context, String spec, URLStreamHandler handler)
        throws MalformedURLException
    {
        return new URL(context, spec, handler);
    }

    public Process exec(String command) throws IOException
    {
        return Runtime.getRuntime().exec(command);
    }

    public String getAbsolutePath(File file)
    {
        return file.getAbsolutePath();
    }

    public boolean fileExists(File file)
    {
        return file.exists();
    }

    public boolean isFile(File file)
    {
        return file.isFile();
    }

    public boolean isFileDirectory(File file)
    {
        return file.isDirectory();
    }

    public boolean mkdir(File file)
    {
        return file.mkdir();
    }

    public boolean mkdirs(File file)
    {
        return file.mkdirs();
    }

    public File[] listDirectory(File file)
    {
        return file.listFiles();
    }

    public boolean renameFile(File oldFile, File newFile)
    {
        return oldFile.renameTo(newFile);
    }

    public InputStream getInputStream(File file) throws IOException
    {
        return Files.newInputStream(file.toPath());
    }

    public OutputStream getOutputStream(File file) throws IOException
    {
        return Files.newOutputStream(file.toPath());
    }

    public FileInputStream getFileInputStream(File file) throws IOException
    {
        return new FileInputStream(file);
    }

    public FileOutputStream getFileOutputStream(File file) throws IOException
    {
        return new FileOutputStream(file);
    }

    public FileChannel getFileChannel(File file) throws IOException
    {
        return FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    public URI toURI(File file)
    {
        return file.toURI();
    }

    public InputStream getURLConnectionInputStream(URLConnection conn)
        throws IOException
    {
        return conn.getInputStream();
    }

    public boolean deleteFile(File target)
    {
        return target.delete();
    }

    public File createTempFile(String prefix, String suffix, File dir)
        throws IOException
    {
        return File.createTempFile(prefix, suffix, dir);
    }

    public void deleteFileOnExit(File file)
        throws IOException
    {
        file.deleteOnExit();
    }

    public URLConnection openURLConnection(URL url) throws IOException
    {
        return url.openConnection();
    }

    public ZipFile openZipFile(File file) throws IOException
    {
        return new ZipFile(file);
    }

    public JarFile openJarFile(File file) throws IOException
    {
        return new JarFile(file);
    }

    public void startActivator(BundleActivator activator, BundleContext context)
        throws Exception
    {
        activator.start(context);
    }

    public void stopActivator(BundleActivator activator, BundleContext context)
        throws Exception
    {
        activator.stop(context);
    }

    public void addURLToURLClassLoader(URL extension, ClassLoader loader) throws Exception
    {
        Method addURL =
            URLClassLoader.class.getDeclaredMethod("addURL",
            URL.class);
        getAccessor(URLClassLoader.class).accept(new AccessibleObject[]{addURL});
        addURL.invoke(loader, extension);
    }

    public Constructor<?> getConstructor(Class<?> target, Class<?>[] types) throws Exception
    {
        return target.getConstructor(types);
    }

    public Constructor<?> getDeclaredConstructor(Class<?> target, Class<?>[] types) throws Exception
    {
        return target.getDeclaredConstructor(types);
    }

    public Method getMethod(Class<?> target, String method, Class<?>[] types) throws Exception
    {
        return target.getMethod(method, types);
    }

    public Method getDeclaredMethod(Class<?> target, String method, Class<?>[] types) throws Exception
    {
        return target.getDeclaredMethod(method, types);
    }

    public void setAccesssible(Executable ao)
    {
        getAccessor(ao.getDeclaringClass()).accept(new AccessibleObject[]{ao});
    }

    public Object invoke(Method method, Object target, Object[] params) throws Exception
    {
        getAccessor(method.getDeclaringClass()).accept(new AccessibleObject[]{method});

        return method.invoke(target, params);
    }

    public Object invokeDirect(Method method, Object target, Object[] params) throws Exception
    {
        return method.invoke(target, params);
    }

    public Object invoke(Constructor<?> constructor, Object[] params) throws Exception
    {
        return constructor.newInstance(params);
    }

    public Object getDeclaredField(Class<?> targetClass, String name, Object target)
        throws Exception
    {
        Field field = targetClass.getDeclaredField(name);
        getAccessor(targetClass).accept(new AccessibleObject[]{field});
        return field.get(target);
    }

    public Object swapStaticFieldIfNotClass(Class<?> targetClazz,
        Class<?> targetType, Class<?> condition, String lockName) throws Exception
    {
        return _swapStaticFieldIfNotClass(targetClazz, targetType,
            condition, lockName);
    }

    private static volatile Consumer<AccessibleObject[]> m_accessorCache = null;

    @SuppressWarnings("unchecked")
    private static Consumer<AccessibleObject[]> getAccessor(Class<?> clazz)
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
                        result = (Class<Consumer<AccessibleObject[]>>) defineAnonymousClass.invoke(unsafe, URL.class, accessor , null);
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

    private static Object _swapStaticFieldIfNotClass(Class<?> targetClazz,
        Class<?> targetType, Class<?> condition, String lockName) throws Exception
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
                    for (Field field : fields) {
                        if (Modifier.isStatic(field.getModifiers()) &&
                            (field.getType() == Hashtable.class))
                        {
                            Hashtable<?,?> cache = (Hashtable) field.get(null);
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

    public void flush(Class<?>targetClazz, Object lock) throws Exception
    {
        _flush(targetClazz, lock);
    }

    private static void _flush(Class<?>targetClazz, Object lock) throws Exception
    {
        synchronized (lock)
        {
            Field[] fields = targetClazz.getDeclaredFields();
            getAccessor(targetClazz).accept(fields);
            // reset cache
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) &&
                    ((field.getType() == Hashtable.class) || (field.getType() == HashMap.class)))
                {
                    if (field.getType() == Hashtable.class)
                    {
                        Hashtable<?,?> cache = (Hashtable) field.get(null);
                        if (cache != null)
                        {
                            cache.clear();
                        }
                    }
                    else
                    {
                        HashMap<?,?> cache = (HashMap) field.get(null);
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
        ch.filterCollisions(operationType, targetBundle, collisionCandidates);
    }

    public void invokeBundleFindHook(
        org.osgi.framework.hooks.bundle.FindHook fh,
        BundleContext bc, Collection<Bundle> bundles)
        throws Exception
    {
        fh.find(bc, bundles);
    }

    public void invokeBundleEventHook(
        org.osgi.framework.hooks.bundle.EventHook eh,
        BundleEvent event, Collection<BundleContext> contexts)
        throws Exception
    {
        eh.event(event, contexts);
    }

    public void invokeWeavingHook(
        org.osgi.framework.hooks.weaving.WeavingHook wh,
        org.osgi.framework.hooks.weaving.WovenClass wc)
        throws Exception
    {
        wh.weave(wc);
    }

    public void invokeServiceEventHook(
        org.osgi.framework.hooks.service.EventHook eh,
        ServiceEvent event, Collection<BundleContext> contexts)
        throws Exception
    {
        eh.event(event, contexts);
    }

    public void invokeServiceFindHook(
        org.osgi.framework.hooks.service.FindHook fh,
        BundleContext context, String name, String filter,
        boolean allServices, Collection<ServiceReference<?>> references)
        throws Exception
    {
        fh.find(context, name, filter, allServices, references);
    }

    public void invokeServiceListenerHookAdded(
        org.osgi.framework.hooks.service.ListenerHook lh,
        Collection<ListenerHook.ListenerInfo> listeners)
        throws Exception
    {
        lh.added(listeners);
    }

    public void invokeServiceListenerHookRemoved(
        org.osgi.framework.hooks.service.ListenerHook lh,
        Collection<ListenerHook.ListenerInfo> listeners)
        throws Exception
    {
        lh.removed(listeners);
    }

    public void invokeServiceEventListenerHook(
        org.osgi.framework.hooks.service.EventListenerHook elh,
        ServiceEvent event,
        Map<BundleContext, Collection<ListenerHook.ListenerInfo>> listeners)
        throws Exception
    {
        elh.event(event, listeners);
    }

    public ResolverHook invokeResolverHookFactory(
        org.osgi.framework.hooks.resolver.ResolverHookFactory rhf,
        Collection<BundleRevision> triggers)
        throws Exception
    {
        return rhf.begin(triggers);
    }

    public void invokeResolverHookResolvable(
        org.osgi.framework.hooks.resolver.ResolverHook rh,
        Collection<BundleRevision> candidates)
        throws Exception
    {
        rh.filterResolvable(candidates);
    }

    public void invokeResolverHookSingleton(
        org.osgi.framework.hooks.resolver.ResolverHook rh,
        BundleCapability singleton,
        Collection<BundleCapability> collisions)
        throws Exception
    {
        rh.filterSingletonCollisions(singleton, collisions);
    }

    public void invokeResolverHookMatches(
        org.osgi.framework.hooks.resolver.ResolverHook rh,
        BundleRequirement req,
        Collection<BundleCapability> candidates)
        throws Exception
    {
        rh.filterMatches(req, candidates);
    }

    public void invokeResolverHookEnd(
        org.osgi.framework.hooks.resolver.ResolverHook rh)
        throws Exception
    {
        rh.end();
    }

    public void invokeWovenClassListener(
            org.osgi.framework.hooks.weaving.WovenClassListener wcl,
            org.osgi.framework.hooks.weaving.WovenClass wc)
            throws Exception
    {
        wcl.modified(wc);
    }

    public <T> T run(PrivilegedAction<T> action)
    {
        return action.run();
    }

    public <T> T run(PrivilegedExceptionAction<T> action) throws Exception
    {
        return action.run();
    }

    public String getCanonicalPath(File dataFile) throws IOException
    {
        return dataFile.getCanonicalPath();
    }

    public Object createProxy(ClassLoader classLoader, 
            Class<?>[] interfaces, InvocationHandler handler)
    {
        return Proxy.newProxyInstance(classLoader, interfaces, handler);
    }

    public long getLastModified(File file)
    {
        return file.lastModified();
    }
}

/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.framework;

import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ContentHandler;
import java.net.InetAddress;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.launch.Framework;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.service.url.URLStreamHandlerSetter;

/**
 *
 * @author pauls
 */
public class URLHandlersTest
{

	public URLHandlersTest() {
	}
    @Test
    public void urlHandlers() throws Exception
    {
        String mf = "Bundle-SymbolicName: url.test\n"
            + "Bundle-Version: 1.0.0\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Import-Package: org.osgi.framework,org.osgi.service.url\n"
            + "Manifest-Version: 1.0\n"
            + Constants.BUNDLE_ACTIVATOR + ": " + TestURLHandlersActivator.class.getName() + "\n\n";

        File bundleFile = createBundle(mf, TestURLHandlersActivator.class, UC.class);

        Framework f = createFramework();
        f.init();
        f.start();

        try
        {
            final Bundle bundle = f.getBundleContext().installBundle(bundleFile.toURI().toString());
            bundle.start();
            URL url = bundle.getEntry(TestURLHandlersActivator.class.getName().replace(".", "/") + ".class");
            url.openStream();
            new URL(url.toExternalForm()).openStream();
        }
        finally
        {
            try
            {
                f.stop();
            }
            catch (Throwable t)
            {
            }
        }
    }

    @Test
    void urlHandlersWithClassLoaderIsolation() throws Exception
    {
        DelegatingClassLoader cl1 = new DelegatingClassLoader(this.getClass().getClassLoader());
        DelegatingClassLoader cl2 = new DelegatingClassLoader(this.getClass().getClassLoader());

        Framework f = createFramework();
        f.init();
        f.start();

        String mf = "Bundle-SymbolicName: url.test\n"
            + "Bundle-Version: 1.0.0\n"
            + "Bundle-ManifestVersion: 2\n"
            + "Import-Package: org.osgi.framework\n"
            + "Manifest-Version: 1.0\n"
            + Constants.BUNDLE_ACTIVATOR + ": " + TestURLHandlersActivator.class.getName();

        File bundleFile = createBundle(mf, TestURLHandlersActivator.class, UC.class);

        final Bundle bundle = f.getBundleContext().installBundle(bundleFile.toURI().toString());
        bundle.start();

        Class clazz1 = cl1.loadClass(URLHandlersTest.class.getName());
        clazz1.getMethod("urlHandlers").invoke(clazz1.newInstance());

        bundle.stop();
        bundle.start();
        Class clazz2 = cl2.loadClass(URLHandlersTest.class.getName());

        clazz2.getMethod("urlHandlers").invoke(clazz2.newInstance());
        bundle.stop();
        bundle.start();
        f.stop();
    }

    @Test
    void urlHandlersWithSecurity() throws Exception
    {
        System.setSecurityManager(new SecurityManager()
        {
            @Override
            public void checkPermission(Permission perm) {}
        });
        try
        {
            urlHandlers();
        }
        finally
        {
            System.setSecurityManager(null);
        }
    }

    @Test
    void urlHandlersWithClassLoaderIsolationWithSecurity() throws Exception
    {
        System.setSecurityManager(new SecurityManager()
        {
            @Override
            public void checkPermission(Permission perm) {}
        });
        try
        {
            urlHandlersWithClassLoaderIsolation();
        }
        finally
        {
            System.setSecurityManager(null);
        }
    }

    public static class DelegatingClassLoader extends ClassLoader
    {
        private final Object m_lock = new Object();
        private final ClassLoader m_source;

        public DelegatingClassLoader(ClassLoader source)
        {
            m_source = source;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException
        {
            synchronized (m_lock)
            {
                Class<?> result = findLoadedClass(name);
                if (result != null)
                {
                    return result;
                }
            }
            if (!name.startsWith("org.apache.felix") && !name.startsWith("org.osgi."))
            {
                return m_source.loadClass(name);
            }
            byte[] buffer = new byte[8 * 1024];
            try
            {
                InputStream is = m_source.getResourceAsStream(name.replace('.', '/') + ".class");
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                for (int i = is.read(buffer); i != -1; i = is.read(buffer))
                {
                    os.write(buffer, 0, i);

                }
                is.close();
                os.close();
                buffer = os.toByteArray();
            }
            catch (Exception ex)
            {
                throw new ClassNotFoundException("Unable to load class: " + name + " with cl: " + System.identityHashCode(this), ex);
            }
            return super.defineClass(name, buffer, 0, buffer.length, null);
        }
    }

    public static class UC extends URLConnection
    {
        public UC(URL u)
        {
            super(u);
        }

        @Override
        public void connect() throws IOException
        {

        }

        @Override
        public String getContentType()
        {
            return "test";
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            return null;
        }
    }

    public static class TestURLHandlersActivator extends ContentHandler implements BundleActivator, URLStreamHandlerService
    {

        private volatile ServiceRegistration m_reg = null;

        @Override
		public URLConnection openConnection(URL u) throws IOException
        {
            return new UC(u);//throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public void parseURL(URLStreamHandlerSetter realHandler, URL u, String spec, int start, int limit)
        {
            realHandler.setURL(u, spec, spec, start, spec, spec, spec, spec, spec);
            //throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public String toExternalForm(URL u)
        {
            return u.toString();//throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public boolean equals(URL u1, URL u2)
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public int getDefaultPort()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public InetAddress getHostAddress(URL u)
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public int hashCode(URL u)
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public boolean hostsEqual(URL u1, URL u2)
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public boolean sameFile(URL u1, URL u2)
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public void start(final BundleContext context) throws Exception
        {
            try
            {
                new URL("test" + System.identityHashCode(TestURLHandlersActivator.this) + ":").openConnection();
                throw new Exception("Unexpected url resolve");
            }
            catch (IOException ex)
            {
                // pass
            }

            Hashtable props = new Hashtable<String, String>();
            props.put(URLConstants.URL_HANDLER_PROTOCOL, "test" + System.identityHashCode(TestURLHandlersActivator.this));

            ServiceRegistration reg = context.registerService(URLStreamHandlerService.class, this, props);

            new URL("test" + System.identityHashCode(TestURLHandlersActivator.this) + ":").openConnection();

            if (!(getClass().getProtectionDomain().getCodeSource().getLocation().openConnection() instanceof JarURLConnection)) {
                throw new Exception("Unexpted Code Source");
            }

            try
            {
                if (new URL("test" + System.identityHashCode(TestURLHandlersActivator.this) + ":").getContent() != null)
                {
                    throw new Exception("Unexpected content resolve");
                }
            }
            catch (IOException ex)
            {
                // pass
            }

            props = new Hashtable<String, String>();
            props.put(URLConstants.URL_CONTENT_MIMETYPE, "test");

            try
            {
                ServiceRegistration reg2 = context.registerService(ContentHandler.class, this, props);

                try
                {
                    if (new URL("test" + System.identityHashCode(TestURLHandlersActivator.this) + ":").getContent() != this)
                    {
                        throw new Exception("Unexpected content");
                    }
                }
                finally
                {
                    reg2.unregister();
                }
                try
                {
                    if (new URL("test" + System.identityHashCode(TestURLHandlersActivator.this) + ":").getContent() != null)
                    {
                        throw new Exception("Unexpected content resolve");
                    }
                }
                catch (IOException ex)
                {
                    // pass
                }
            }
            finally
            {
                reg.unregister();
            }

            boolean fail;
            try
            {
                new URL("test" + System.identityHashCode(TestURLHandlersActivator.this) + ":").openConnection();
                fail = true;
            }
            catch (Exception ex)
            {
                // pass
                fail = false;
            }
            if (fail) {
                throw new Exception("Unexpected url resolve");
            }

            Bundle bundle2 = null;
            if (context.getBundle().getSymbolicName().equals("url.test"))
            {

                String mf = "Bundle-SymbolicName: url.test2\n"
                    + "Bundle-Version: 1.0.0\n"
                    + "Bundle-ManifestVersion: 2\n"
                    + "Import-Package: org.osgi.framework,org.osgi.service.url\n"
                    + "Manifest-Version: 1.0\n"
                    + Constants.BUNDLE_ACTIVATOR + ": " + TestURLHandlersActivator.class.getName() + "\n\n";

                File bundleFile = createBundle(mf, TestURLHandlersActivator.class, UC.class);

                bundle2 = context.installBundle(bundleFile.toURI().toURL().toString());
            }
            if (bundle2 != null)
            {
                fail = false;
                try
                {
                    new URL("test" + System.identityHashCode(bundle2) + ":").openConnection();
                    fail = true;
                }
                catch (Exception ex)
                {
                }
                if (fail) {
                    throw new Exception("Unexpected url resolve");
                }
                bundle2.start();
                new URL("test" + System.identityHashCode(bundle2) + ":").openConnection();
                bundle2.stop();
                fail = false;
                try
                {
                    new URL("test" + System.identityHashCode(bundle2) + ":").openConnection();
                    fail = true;
                }
                catch (Exception ex)
                {
                }
                if (fail) {
                    throw new Exception("Unexpected url resolve");
                }
            }
            else
            {
                fail = false;
                try
                {
                    new URL("test" + System.identityHashCode(context.getBundle()) + ":").openConnection();
                    fail = true;
                }
                catch (Exception ex)
                {
                }
                if (fail) {
                    throw new Exception("Unexpected url2 resolve");
                }
                props = new Hashtable();
                props.put(URLConstants.URL_HANDLER_PROTOCOL, "test" + System.identityHashCode(context.getBundle()));
                m_reg = context.registerService(URLStreamHandlerService.class, this, props);
                new URL("test" + System.identityHashCode(context.getBundle()) + ":").openConnection();
            }
        }

        private static File createBundle(String manifest, Class... classes) throws IOException
        {
            File f = File.createTempFile("felix-bundle", ".jar");
            f.deleteOnExit();

            Manifest mf = new Manifest(new ByteArrayInputStream(manifest.getBytes("utf-8")));
            JarOutputStream os = new JarOutputStream(new FileOutputStream(f), mf);

            for (Class clazz : classes)
            {
                String path = clazz.getName().replace('.', '/') + ".class";
                os.putNextEntry(new ZipEntry(path));

                InputStream is = clazz.getClassLoader().getResourceAsStream(path);
                byte[] buffer = new byte[8 * 1024];
                for (int i = is.read(buffer); i != -1; i = is.read(buffer))
                {
                    os.write(buffer, 0, i);
                }
                is.close();
                os.closeEntry();
            }
            os.close();
            return f;
        }

        @Override
		public void stop(BundleContext context) throws Exception
        {
            if (m_reg != null)
            {
                m_reg.unregister();
            }
        }

        @Override
        public Object getContent(URLConnection urlc) throws IOException
        {
            return this;
        }
    }

    private static File createBundle(String manifest, Class... classes) throws IOException
    {
        File f = File.createTempFile("felix-bundle", ".jar");
        f.deleteOnExit();

        Manifest mf = new Manifest(new ByteArrayInputStream(manifest.getBytes("utf-8")));
        JarOutputStream os = new JarOutputStream(new FileOutputStream(f), mf);

        for (Class clazz : classes)
        {
            String path = clazz.getName().replace('.', '/') + ".class";
            os.putNextEntry(new ZipEntry(path));

            InputStream is = clazz.getClassLoader().getResourceAsStream(path);
            byte[] buffer = new byte[8 * 1024];
            for (int i = is.read(buffer); i != -1; i = is.read(buffer))
            {
                os.write(buffer, 0, i);
            }
            is.close();
            os.closeEntry();
        }
        os.close();
        return f;
    }

    private static Felix createFramework() throws Exception
    {
        Map params = new HashMap();
        params.put(Constants.FRAMEWORK_SYSTEMPACKAGES,
            "org.osgi.framework; version=1.4.0,"
            + "org.osgi.service.packageadmin; version=1.2.0,"
            + "org.osgi.service.startlevel; version=1.1.0,"
            + "org.osgi.util.tracker; version=1.3.3,"
            + "org.osgi.service.url; version=1.0.0");
        File cacheDir = File.createTempFile("felix-cache", ".dir");
        if (!cacheDir.delete() || !cacheDir.mkdirs())
        {
            fail("Unable to set-up cache dir");
        }
        String cache = cacheDir.getPath();
        params.put("felix.cache.profiledir", cache);
        params.put("felix.cache.dir", cache);
        params.put(Constants.FRAMEWORK_STORAGE, cache);

        return new Felix(params);
    }
}

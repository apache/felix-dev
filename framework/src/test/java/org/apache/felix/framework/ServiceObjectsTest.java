package org.apache.felix.framework;

import junit.framework.TestCase;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.launch.Framework;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ServiceObjectsTest extends TestCase
{
    public void testServiceObjects() throws Exception
    {
        Map params = new HashMap();
        File cacheDir = File.createTempFile("felix-cache", ".dir");
        cacheDir.delete();
        cacheDir.mkdirs();
        String cache = cacheDir.getPath();
        params.put("felix.cache.profiledir", cache);
        params.put("felix.cache.dir", cache);
        params.put(Constants.FRAMEWORK_STORAGE, cache);
        Framework f = new Felix(params);
        f.init();
        f.start();

        try
        {
            BundleContext context = f.getBundleContext();
            ServiceRegistration<Object> registration =
                    context.registerService(Object.class, new Object(), null);

            ServiceReference<Object> reference = registration.getReference();

            ServiceObjects<Object> serviceObjects = context.getServiceObjects(reference);

            Object service = serviceObjects.getService();

            serviceObjects.ungetService(service);

            assertEquals(service, serviceObjects.getService());
            service = serviceObjects.getService();

            registration.unregister();

            serviceObjects.ungetService(service);
        }
        finally
        {
            f.stop();
            Thread.sleep(1000);
            deleteDir(cacheDir);
        }
    }

    private static void deleteDir(File root) throws IOException
    {
        if (root.isDirectory())
        {
            for (File file : root.listFiles())
            {
                deleteDir(file);
            }
        }
        assertTrue(root.delete());
    }
}

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
package org.apache.felix.useradmin.itest;

import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.Constants.START_LEVEL_SYSTEM_BUNDLES;
import static org.ops4j.pax.exam.Constants.START_LEVEL_TEST_BUNDLE;
import static org.ops4j.pax.exam.CoreOptions.bootDelegationPackage;
import static org.ops4j.pax.exam.CoreOptions.cleanCaches;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.frameworkProperty;
import static org.ops4j.pax.exam.CoreOptions.frameworkStartLevel;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemPackages;
import static org.ops4j.pax.exam.CoreOptions.url;

import javax.inject.Inject;

import org.junit.Before;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.useradmin.UserAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Base class for integration tests.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public abstract class BaseIntegrationTest
{

    private static final int DEFAULT_TIMEOUT = 10000;

    protected static final String ORG_APACHE_FELIX_USERADMIN = "org.apache.felix.useradmin";
    protected static final String ORG_APACHE_FELIX_USERADMIN_FILESTORE = "org.apache.felix.useradmin.filestore";
    protected static final String ORG_APACHE_FELIX_USERADMIN_MONGODBSTORE = "org.apache.felix.useradmin.mongodb";
    protected static final String ORG_MONGODB_MONGO_JAVA_DRIVER = "org.mongodb.mongo-java-driver";

    @Inject
    protected volatile BundleContext m_context;

    @Configuration
    public Option[] config()
    {
        return options(
            bootDelegationPackage("sun.*"),
            systemPackages("javax.naming", "javax.net.ssl", "javax.xml.parsers", "org.w3c.dom", "org.xml.sax", "org.xml.sax.helpers","javax.management","javax.net","javax.crypto",
            		 "javax.crypto.spec","javax.security.sasl","javax.security.auth.callback","org.ietf.jgss"),
            frameworkProperty("org.osgi.framework.system.capabilities.extra").value(
                "osgi.ee;osgi.ee=JavaSE;version=1.7,osgi.ee;osgi.ee=JavaSE;version=1.6,osgi.ee;osgi.ee=JavaSE;version=1.5,osgi.ee;osgi.ee=JavaSE;version=1.4,osgi.ee;osgi.ee=JavaSE;version=1.3"),
            cleanCaches(),
            CoreOptions.systemProperty("logback.configurationFile").value("file:src/test/resources/logback.xml"), //
//            CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8787"),

            mavenBundle("org.slf4j", "slf4j-api").version("1.7.25").startLevel(START_LEVEL_SYSTEM_BUNDLES),
            mavenBundle("ch.qos.logback", "logback-core").version("1.2.3").startLevel(START_LEVEL_SYSTEM_BUNDLES),
            mavenBundle("ch.qos.logback", "logback-classic").version("1.2.3").startLevel(START_LEVEL_SYSTEM_BUNDLES),

            url("link:classpath:META-INF/links/org.ops4j.pax.exam.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
            url("link:classpath:META-INF/links/org.ops4j.pax.exam.inject.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
            url("link:classpath:META-INF/links/org.ops4j.pax.extender.service.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
            url("link:classpath:META-INF/links/org.ops4j.base.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
            url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.core.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
            url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.extender.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
            url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.lifecycle.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
            url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.framework.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
            url("link:classpath:META-INF/links/org.apache.geronimo.specs.atinject.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),

            mavenBundle("org.apache.felix", ORG_APACHE_FELIX_USERADMIN).versionAsInProject().startLevel(START_LEVEL_SYSTEM_BUNDLES),
            mavenBundle("org.apache.felix", ORG_APACHE_FELIX_USERADMIN_FILESTORE).versionAsInProject().noStart(),
            mavenBundle("org.apache.felix", ORG_APACHE_FELIX_USERADMIN_MONGODBSTORE).versionAsInProject().noStart(), mavenBundle("org.mongodb", "mongo-java-driver").versionAsInProject().noStart(),

            junitBundles(), frameworkStartLevel(START_LEVEL_TEST_BUNDLE), felix().version("6.0.3"));
    }

    @Before
    public void setUp() throws Exception
    {
        assertNotNull("No bundle context?!", m_context);
    }

    /**
     * Waits for a service to become available in certain time interval.
     * @param serviceName
     * @return
     * @throws Exception
     */
    protected <T> T awaitService(String serviceName) throws Exception
    {
        ServiceTracker tracker = new ServiceTracker(m_context, serviceName, null);
        tracker.open();
        T result;
        try
        {
            result = (T) tracker.waitForService(DEFAULT_TIMEOUT);
        }
        finally
        {
            tracker.close();
        }
        return result;
    }

    /**
     * @param bsn
     * @return
     */
    protected Bundle findBundle(String bsn)
    {
        for (Bundle bundle : m_context.getBundles())
        {
            if (bsn.equals(bundle.getSymbolicName()))
            {
                return bundle;
            }
        }
        return null;
    }

    protected Bundle getFileStoreBundle()
    {
        Bundle b = findBundle(ORG_APACHE_FELIX_USERADMIN_FILESTORE);
        assertNotNull("Filestore bundle not found?!", b);
        return b;
    }

    protected Bundle getMongoDBStoreBundle()
    {
        Bundle b = findBundle(ORG_APACHE_FELIX_USERADMIN_MONGODBSTORE);
        assertNotNull("MongoDB store bundle not found?!", b);
        return b;
    }

    protected Bundle getMongoDBBundle()
    {
        Bundle b = findBundle(ORG_MONGODB_MONGO_JAVA_DRIVER);
        assertNotNull("MongoDB bundle not found?!", b);
        return b;
    }

    /**
     * Obtains a service without waiting for it to become available.
     * @param serviceName
     * @return
     * @throws Exception
     */
    protected <T> T getService(String serviceName) throws Exception
    {
        ServiceTracker tracker = new ServiceTracker(m_context, serviceName, null);
        tracker.open();
        T result;
        try
        {
            result = (T) tracker.getService();
        }
        finally
        {
            tracker.close();
        }
        return result;
    }

    /**
     * @return the {@link UserAdmin} service instance.
     */
    protected UserAdmin getUserAdmin() throws Exception
    {
        return getService(UserAdmin.class.getName());
    }
}

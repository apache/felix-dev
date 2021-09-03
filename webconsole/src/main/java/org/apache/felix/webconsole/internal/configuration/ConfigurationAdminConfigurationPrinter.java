/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.webconsole.internal.configuration;

import java.io.PrintWriter;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.felix.webconsole.internal.AbstractConfigurationPrinter;
import org.apache.felix.webconsole.internal.misc.ConfigurationRender;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.ObjectClassDefinition;

/**
 * ConfigurationAdminConfigurationPrinter uses the {@link ConfigurationAdmin} service
 * to print the available configurations.
 */
public class ConfigurationAdminConfigurationPrinter extends AbstractConfigurationPrinter
{

    private static final String TITLE = "Configurations";

    /**
     * @see org.apache.felix.webconsole.ConfigurationPrinter#getTitle()
     */
    @Override
    public String getTitle()
    {
        return TITLE;
    }

    /**
     * @see org.apache.felix.webconsole.ConfigurationPrinter#printConfiguration(java.io.PrintWriter)
     */
    @Override
    public void printConfiguration(PrintWriter pw)
    {
        final ServiceReference<ConfigurationAdmin> sr = getBundleContext().getServiceReference( ConfigurationAdmin.class );
        try
        {
            final ConfigurationAdmin ca = (sr == null ? null : getBundleContext().getService(sr));
            if (ca == null)
            {
                pw.println("Status: Configuration Admin Service not available");
            }
            else
            {
                MetaTypeServiceSupport metatypeSupport = null;
                final ServiceReference<?> msr = getBundleContext().getServiceReference( ConfigManager.META_TYPE_NAME );
                try
                {

                    if ( msr != null )
                    {
                        final Object metaTypeService = getBundleContext().getService( msr );
                        if ( metaTypeService != null )
                        {
                            metatypeSupport = new MetaTypeServiceSupport( this.getBundleContext(), metaTypeService );
                        }
                    }

                    final Configuration[] configs = ca.listConfigurations(null);

                    if (configs != null && configs.length > 0)
                    {
                        final Set<String> factories = new HashSet<>();
                        final SortedMap<String, Configuration> sm = new TreeMap<>();
                        for (int i = 0; i < configs.length; i++)
                        {
                            sm.put(configs[i].getPid(), configs[i]);
                            String fpid = configs[i].getFactoryPid();
                            if (null != fpid)
                            {
                                factories.add(fpid);
                            }
                        }
                        if (factories.isEmpty())
                        {
                            pw.println("Status: " + configs.length
                                + " configurations available");
                        }
                        else
                        {
                            pw.println("Status: " + configs.length + " configurations with " + factories.size()
                                    + " different factories available");
                        }
                        pw.println();

                        for (Iterator<Configuration> mi = sm.values().iterator(); mi.hasNext();)
                        {
                            this.printConfiguration(pw, metatypeSupport, mi.next());
                        }
                    }
                    else
                    {
                        pw.println("Status: No Configurations available");
                    }
                }
                finally
                {
                    if ( msr != null )
                    {
                        getBundleContext().ungetService(msr);
                    }

                }
            }
        }
        catch (final Exception ignore)
        {
            pw.println("Status: Configuration Admin Service not accessible");
        }
        finally
        {
            if ( sr != null )
            {
                getBundleContext().ungetService(sr);
            }
        }
    }

    private void printConfiguration(final PrintWriter pw, final MetaTypeServiceSupport metatypeSupport, final Configuration config)
    {
        ConfigurationRender.infoLine(pw, "", "PID", config.getPid());

        if (config.getFactoryPid() != null)
        {
            ConfigurationRender.infoLine(pw, "  ", "Factory PID", config.getFactoryPid());
        }

        if ( config.getBundleLocation() != null )
        {
            ConfigurationRender.infoLine(pw, "  ", "BundleLocation", config.getBundleLocation());
        }

        Dictionary<String, Object> props = config.getProperties();
        if (props != null)
        {
            final Set<String> obfuscateProperties = new HashSet<>();
            if ( metatypeSupport != null )
            {
                if ( config != null )
                {
                    final ObjectClassDefinition ocd = metatypeSupport.getObjectClassDefinition( config, null );
                    if ( ocd != null )
                    {
                        final AttributeDefinition[] ad = ocd.getAttributeDefinitions( ObjectClassDefinition.ALL );
                        if ( ad != null )
                        {
                            for(final AttributeDefinition def : ad)
                            {
                               if ( def.getType() == AttributeDefinition.PASSWORD )
                               {
                                   obfuscateProperties.add(def.getID());
                               }
                            }
                        }
                    }
                }
            }

            final SortedSet<String> keys = new TreeSet<>();
            for (Enumeration<String> ke = props.keys(); ke.hasMoreElements();)
            {
                keys.add(ke.nextElement());
            }

            for(final String key : keys)
            {
                // pid, factory pid and bundle location are already printed
                if ( ConfigAdminSupport.CONFIG_PROPERTIES_HIDE.contains(key) )
                {
                    continue;
                }
                final Object value = (obfuscateProperties.contains(key) || MetaTypeSupport.isPasswordProperty(key)) ? "********" : props.get(key);
                ConfigurationRender.infoLine(pw, "  ", key, value);
            }
        }

        pw.println();
    }

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.webconsole.plugins.memoryusage.internal;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeProvider;
import org.osgi.service.metatype.ObjectClassDefinition;

class MemoryUsageConfigurator implements ManagedService, MetaTypeProvider
{

    private final MemoryUsageSupport support;

    private ObjectClassDefinition ocd;

    MemoryUsageConfigurator(final MemoryUsageSupport support)
    {
        this.support = support;
    }

    public void updated(Dictionary<String, ?> properties) throws ConfigurationException
    {
        // ensure default values if there is no config or config is deleted
        if (properties == null)
        {
            properties = new Hashtable<>();
        }

        final Object thresholdValue = properties.get(MemoryUsageConstants.PROP_DUMP_THRESHOLD);
        if (thresholdValue != null)
        {
            final int threshold;
            if (thresholdValue instanceof Number)
            {
                threshold = ((Number) thresholdValue).intValue();
            }
            else
            {
                // try to convert
                try
                {
                    threshold = Integer.parseInt(thresholdValue.toString());
                }
                catch (NumberFormatException nfe)
                {
                    throw thresholdFailure(thresholdValue);
                }
            }

            try
            {
                support.setThreshold(threshold);
            }
            catch (IllegalArgumentException iae)
            {
                throw thresholdFailure(iae.getMessage());
            }
        }
        else
        {
            support.setThreshold(-1);
        }

        final Object intervalValue = properties.get(MemoryUsageConstants.PROP_DUMP_INTERVAL);
        if (intervalValue != null)
        {
            final int interval;
            if (intervalValue instanceof Number)
            {
                interval = ((Number) intervalValue).intValue();
            }
            else
            {
                // try to convert
                try
                {
                    interval = Integer.parseInt(intervalValue.toString());
                }
                catch (NumberFormatException nfe)
                {
                    throw intervalFailure(intervalValue);
                }
            }

            try
            {
                support.setInterval(interval);
            }
            catch (IllegalArgumentException iae)
            {
                throw intervalFailure(iae.getMessage());
            }
        }
        else
        {
            support.setInterval(-1);
        }

        final Object locationValue = properties.get(MemoryUsageConstants.PROP_DUMP_LOCATION);
        if (locationValue instanceof String)
        {
            support.setDumpLocation((String) locationValue);
        }
        else
        {
            support.setDumpLocation(null);
        }
    }

    public String[] getLocales()
    {
        return null;
    }

    public ObjectClassDefinition getObjectClassDefinition(String id, String locale)
    {
        if (!MemoryUsageConstants.PID.equals(id))
        {
            return null;
        }

        if (ocd == null)
        {

            final ArrayList<AttributeDefinition> adList = new ArrayList<AttributeDefinition>();

            adList.add(new AttributeDefinitionImpl(MemoryUsageConstants.PROP_DUMP_THRESHOLD, "Dump Threshold",
                "Threshold at which to automatically create a memory dump as a percentage in the range "
                + MemoryUsageConstants.MIN_DUMP_THRESHOLD + " to " + MemoryUsageConstants.MAX_DUMP_THRESHOLD
                + " or zero to disable automatic dump creation.", AttributeDefinition.INTEGER, new String[]
                                                                                                          { String.valueOf(MemoryUsageConstants.DEFAULT_DUMP_THRESHOLD) }, 0, null, null)
            {
                @Override
                public String validate(String value)
                {
                    try
                    {
                        int threshold = Integer.parseInt(value);
                        if (!MemoryUsageConstants.isThresholdValid(threshold))
                        {
                            return "Dump Threshold must in the range " + MemoryUsageConstants.MIN_DUMP_THRESHOLD
                                + " to " + MemoryUsageConstants.MAX_DUMP_THRESHOLD + " or zero";
                        }
                        return ""; // everything ok
                    }
                    catch (NumberFormatException nfe)
                    {
                        return "Dump Threshhold must be numeric";
                    }
                }
            });

            adList.add(new AttributeDefinitionImpl(MemoryUsageConstants.PROP_DUMP_INTERVAL, "Dump Interval",
                "The minimum interval between two consecutive memory dumps being taken in seconds. "
                    + "This property allows the limitation of the number of memory dumps being taken. "
                    + "The default value for the interval is 6 hours. This means that a memory threshold "
                    + "event is ignored unless the last memory dump has been taken at least 6 hours earlier. "
                    + "This property allows limiting the number of memory dumps in case memory consumption is "
                    + "oscillating around the threshold point. The property must be an integer value or be "
                    + "parseable to an integer value. This should be a positive value or zero to force each "
                    + "memory threshold event to cause a memory dump (discouraged).", AttributeDefinition.INTEGER,
                new String[]
                    { String.valueOf(MemoryUsageConstants.DEFAULT_DUMP_INTERVAL) }, 0, null, null)
            {
                @Override
                public String validate(String value)
                {
                    try
                    {
                        int interval = Integer.parseInt(value);
                        if (interval < 0)
                        {
                            return "Dump Interval must be zero or a positive number";
                        }
                        return ""; // everything ok
                    }
                    catch (NumberFormatException nfe)
                    {
                        return "Dump Interval must be numeric";
                    }
                }
            });

            adList.add(new AttributeDefinitionImpl(MemoryUsageConstants.PROP_DUMP_LOCATION, "Dumpe Location",
                "The filesystem location where heap dumps are stored. If this is null or empty (the default) the dumps are stored in "
                    + support.getDefaultDumpLocation(), ""));

            ocd = new ObjectClassDefinition()
            {

                private final AttributeDefinition[] attrs = adList.toArray(new AttributeDefinition[adList.size()]);

                public String getName()
                {
                    return "Apache Felix Web Console Memory Usage Plugin";
                }

                public InputStream getIcon(int arg0)
                {
                    return null;
                }

                public String getID()
                {
                    return MemoryUsageConstants.PID;
                }

                public String getDescription()
                {
                    return "Configuration of the Apache Felix Web Console Memory Usage Plugin.";
                }

                public AttributeDefinition[] getAttributeDefinitions(int filter)
                {
                    return (filter == OPTIONAL) ? null : attrs;
                }
            };
        }

        return ocd;
    }

    private ConfigurationException thresholdFailure(final Object invalidValue)
    {
        return new ConfigurationException(MemoryUsageConstants.PROP_DUMP_THRESHOLD, "Invalid Dump Threshold value '"
            + invalidValue + "': Must be an integer number in the range " + MemoryUsageConstants.MIN_DUMP_THRESHOLD
            + " to " + MemoryUsageConstants.MAX_DUMP_THRESHOLD + " or zero to disable");
    }

    private ConfigurationException intervalFailure(final Object invalidValue)
    {
        return new ConfigurationException(MemoryUsageConstants.PROP_DUMP_INTERVAL, "Invalid Dump Interval value '"
            + invalidValue + "': Must be a positive integer number or zero to disable");
    }

    private static class AttributeDefinitionImpl implements AttributeDefinition
    {

        private final String id;
        private final String name;
        private final String description;
        private final int type;
        private final String[] defaultValues;
        private final int cardinality;
        private final String[] optionLabels;
        private final String[] optionValues;

        AttributeDefinitionImpl(final String id, final String name, final String description, final String defaultValue)
        {
            this(id, name, description, STRING, new String[]
                { defaultValue }, 0, null, null);
        }

        AttributeDefinitionImpl(final String id, final String name, final String description, final int type,
            final String[] defaultValues, final int cardinality, final String[] optionLabels,
            final String[] optionValues)
        {
            this.id = id;
            this.name = name;
            this.description = description;
            this.type = type;
            this.defaultValues = defaultValues;
            this.cardinality = cardinality;
            this.optionLabels = optionLabels;
            this.optionValues = optionValues;
        }

        public int getCardinality()
        {
            return cardinality;
        }

        public String[] getDefaultValue()
        {
            return defaultValues;
        }

        public String getDescription()
        {
            return description;
        }

        public String getID()
        {
            return id;
        }

        public String getName()
        {
            return name;
        }

        public String[] getOptionLabels()
        {
            return optionLabels;
        }

        public String[] getOptionValues()
        {
            return optionValues;
        }

        public int getType()
        {
            return type;
        }

        public String validate(String arg0)
        {
            return null;
        }
    }
}

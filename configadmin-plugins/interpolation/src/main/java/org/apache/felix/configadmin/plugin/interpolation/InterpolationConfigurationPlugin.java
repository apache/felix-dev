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
package org.apache.felix.configadmin.plugin.interpolation;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationPlugin;
import org.osgi.util.converter.Converters;
import org.slf4j.Logger;

class InterpolationConfigurationPlugin implements ConfigurationPlugin {

    private static final String TYPE_ENV = "env";

    private static final String TYPE_PROP = "prop";

    private static final String TYPE_SECRET = "secret";

    private static final String TYPE_CONF = "conf";

    private static final String DIRECTIVE_TYPE = "type";

    /** Delimiter for splitting up a single value into an array. */
    private static final String DIRECTIVE_DELIMITER = "delimiter";

    private static final String DIRECTIVE_DEFAULT = "default";

    private static final Map<String, Class<?>> TYPE_MAP = new HashMap<>();
    static {
        // scalar types and primitive types
        TYPE_MAP.put("String", String.class);
        TYPE_MAP.put("Integer", Integer.class);
        TYPE_MAP.put("int", Integer.class);
        TYPE_MAP.put("Long", Long.class);
        TYPE_MAP.put("long", Long.class);
        TYPE_MAP.put("Float", Float.class);
        TYPE_MAP.put("float", Float.class);
        TYPE_MAP.put("Double", Double.class);
        TYPE_MAP.put("double", Double.class);
        TYPE_MAP.put("Byte", Byte.class);
        TYPE_MAP.put("byte", Byte.class);
        TYPE_MAP.put("Short", Short.class);
        TYPE_MAP.put("short", Short.class);
        TYPE_MAP.put("Character", Character.class);
        TYPE_MAP.put("char", Character.class);
        TYPE_MAP.put("Boolean", Boolean.class);
        TYPE_MAP.put("boolean", Boolean.class);
         // array of scalar types and primitive types
        TYPE_MAP.put("String[]", String[].class);
        TYPE_MAP.put("Integer[]", Integer[].class);
        TYPE_MAP.put("int[]", int[].class);
        TYPE_MAP.put("Long[]", Long[].class);
        TYPE_MAP.put("long[]", long[].class);
        TYPE_MAP.put("Float[]", Float[].class);
        TYPE_MAP.put("float[]", float[].class);
        TYPE_MAP.put("Double[]", Double[].class);
        TYPE_MAP.put("double[]", double[].class);
        TYPE_MAP.put("Byte[]", Byte[].class);
        TYPE_MAP.put("byte[]", byte[].class);
        TYPE_MAP.put("Short[]", Short[].class);
        TYPE_MAP.put("short[]", short[].class);
        TYPE_MAP.put("Boolean[]", Boolean[].class);
        TYPE_MAP.put("boolean[]", boolean[].class);
        TYPE_MAP.put("Character[]", Character[].class);
        TYPE_MAP.put("char[]", char[].class);
    }

    private final Function<String, String> propertiesProvider;
    private final List<File> directory;

    private final Charset encodingCharset;

    InterpolationConfigurationPlugin(Function<String, String> pp, String dir, String fileEncoding) {
        propertiesProvider = pp;
        if (dir != null) {
            directory = Stream.of(dir.split("\\s*,\\s*")).map(File::new).collect(toList());
            getLog().info("Configured directory for secrets: {}", dir);
        } else {
            directory = Collections.emptyList();
        }
        if (fileEncoding == null) {
            encodingCharset = Charset.defaultCharset();
        } else {
            encodingCharset = Charset.forName(fileEncoding);
        }

    }

    private Logger getLog() {
        return Activator.LOG;
    }

    @Override
    public void modifyConfiguration(ServiceReference<?> reference, Dictionary<String, Object> properties) {
        final Object pid = properties.get(Constants.SERVICE_PID);
        for (Enumeration<String> keys = properties.keys(); keys.hasMoreElements(); ) {
            String key = keys.nextElement();
            Object val = properties.get(key);
            if (val instanceof String) {
                Object newVal = getNewValue(key, (String) val, pid, properties);
                if (newVal != null && !newVal.equals(val)) {
                    properties.put(key, newVal);
                    getLog().info("Replaced value of configuration property '{}' for PID {}", key, pid);
                }
            } else if (val instanceof String[]) {
                String[] array = (String[]) val;
                List<String> newArray = null;
                for (int i = 0; i < array.length; i++) {
                    Object newVal = getNewValue(key, array[i], pid, properties);
                    if (newVal != null && !newVal.equals(array[i])) {
                        if (newArray == null) {
                            newArray = new ArrayList<>();
                            for(int m=0;m<i;m++) {
                                newArray.add(array[m]);
                            }
                        }
                        if ( newVal.getClass().isArray() ) {
                            for(int m=0;m<Array.getLength(newVal);m++ ) {
                                newArray.add(Array.get(newVal, m).toString());
                            }
                        } else {
                            newArray.add(newVal.toString());
                        }
                    } else if ( newArray != null ) {
                        newArray.add(array[i]);
                    }
                }
                if (newArray != null) {
                    final String[] update = newArray.toArray(new String[newArray.size()]);
                    properties.put(key, update);
                    getLog().info("Replaced value of configuration property '{}' for PID {}", key, pid);
                }
            }
        }
    }

    private Object getNewValue(final String key, final String value, final Object pid, final Dictionary<String, Object> properties) {
        final Object result = replace(key, value, pid, properties);
        if (value.equals(result)) {
            return null;
        }
        return result;
    }

    Object replace(final String key, final String value, final Object pid, final Dictionary<String, Object> properties) {
        final Object result = Interpolator.replace(value, (type, name, dir) -> {
            String v = null;
            if (TYPE_ENV.equals(type)) {
                v = getVariableFromEnvironment(name);

            } else if (TYPE_PROP.equals(type)) {
                v = getVariableFromProperty(name);

            } else if (TYPE_SECRET.equals(type)) {
                v = getVariableFromFile(key, name, pid);

            } else if (TYPE_CONF.equals(type)) {
                v = getVariableFromConfiguration(name, properties);
            }
            if (v == null) {
                v = dir.get(DIRECTIVE_DEFAULT);
            }
            if (v != null && dir.containsKey(DIRECTIVE_TYPE)) {
                return convertType(dir.get(DIRECTIVE_TYPE), v, dir.get(DIRECTIVE_DELIMITER));
            }
            return v;
        });
        return result;
    }

    String getVariableFromConfiguration(final String name, final Dictionary<String, Object> properties) {
        Object val = properties.get(name);
        String result;
        if (val.getClass().isArray()) {
            if (val instanceof int[]) {
                result = Arrays.toString((int[])val);
            } else if (val instanceof long[]) {
                result = Arrays.toString((long[])val);
            } else if (val instanceof float[]) {
                result = Arrays.toString((float[])val);
            } else if (val instanceof double[]) {
                result = Arrays.toString((double[])val);
            } else if (val instanceof byte[]) {
                result = Arrays.toString((byte[])val);
            } else if (val instanceof short[]) {
                result = Arrays.toString((short[])val);
            } else if (val instanceof boolean[]) {
                result = Arrays.toString((boolean[])val);
            } else if (val instanceof char[]) {
                result = Arrays.toString((char[])val);
            } else {
                result =Arrays.toString((Object[])val);
            }
        } else {
            result = String.valueOf(val);
        }
        // prevent circular references
        if (result.startsWith("$[conf:")) {
            getLog().warn("There is a cycle in '{}' for PID {}, returning {}", name, properties.get(Constants.SERVICE_PID), name);
            return name;
        }
        return result;
    }

    String getVariableFromEnvironment(final String name) {
        return System.getenv(name);
    }

    String getVariableFromProperty(final String name) {
        return propertiesProvider.apply(name);
    }

    /**
     * 
     * @param key the property key referencing the variable
     * @param name the name of the file to read
     * @param pid the affected PID
     * @return
     */
    String getVariableFromFile(final String key, final String name, final Object pid) {
        if (directory.isEmpty()) {
            getLog().warn("Cannot replace property value {} for PID {}. No directory configured via framework property {}",
                    key, pid, Activator.DIR_PROPERTY);
            return null;
        }

        if (name.contains("..")) {
            getLog().error("Illegal secret location '{}' in property {}. Going up in the directory structure is not allowed", name, key);
            return null;
        }

        List<File> files = directory.stream().map(d -> new File(d, name)).filter(File::exists).collect(toList());
        if (files.isEmpty()) {
            getLog().warn("Cannot replace secret '{}' in property {}. No file found for name in any of the given directories: '{}'", name, key, directory);
            return null;
        }
        if (files.stream().noneMatch(File::isFile)) {
            getLog().warn("Cannot replace secret '{}' in property {}. Found paths are not regular files: {}", name, key, files);
            return null;
        }

        if (files.stream().map(File::getAbsolutePath).noneMatch(s -> directory.stream().anyMatch(dir -> s.startsWith(dir.getAbsolutePath())))) {
            getLog().error("Illegal secret location '{}' in property {}. Going out the directory structure is not allowed", name, key);
            return null;
        }

        File file = files.stream().findFirst().orElseThrow(
            () -> new IllegalStateException(
                "Something went terribly wrong. This should not be possible."));

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            getLog().error("Problem replacing configuration property '{}' for PID {} from file {}",
                        key, pid, file, e);

            return null;
        }
        return new String(bytes, this.encodingCharset).trim();
    }

    /**
     * Convert the value to the given type
     *
     * @param type      The type (optional)
     * @param value     The value
     * @param delimiter The delimiter for array types (optional)
     * @return The converted value
     */
    Object convertType(String type, final String value, final String delimiter) {
        // if delimiter is specifed but no type, assume String[]
        if ( delimiter != null && type == null ) {
            type = "String[]";
        }
        if (type == null) {
            return value;
        }

        Class<?> cls = TYPE_MAP.get(type);
        if (cls != null) {
            if ((cls.isArray() || Collection.class.isAssignableFrom(cls)) && delimiter != null) {
                final String[] array = split(value, delimiter);
                return Converters.standardConverter().convert(array).to(cls);
            }
            return Converters.standardConverter().convert(value).to(cls);
        }

        getLog().warn("Cannot convert to type: " + type);
        return value;
    }

    /**
     * Split a string into an array of strings. A backslash can be used to escape
     * the delimiter (avoiding splitting), unless that backslash is preceded by
     * another backslash, in which case the two backslashes are replaced by a single
     * one and the value is split after the backslash.
     *
     * @param value     The value to split
     * @param delimiter The delimiter
     * @return The resulting array
     */
    String[] split(String value, final String delimiter) {
        List<String> result = null;
        int start = -1;
        while (start < value.length()) {
            start = value.indexOf(delimiter, start + 1);
            if (start == -1) {
                // no delimiter found -> end
                start = value.length();
                if (result != null) {
                    result.add(value);
                }
            } else {

                boolean split = true;
                if (start > 1 && value.charAt(start - 1) == Interpolator.ESCAPE) {
                    if (start == 1 || value.charAt(start - 2) != Interpolator.ESCAPE) {
                        split = false;
                    } else if (value.charAt(start - 2) == Interpolator.ESCAPE) {
                        value = value.substring(0, start - 2).concat(value.substring(start - 1));
                        start--;
                    }
                }

                if (split) {
                    if (result == null) {
                        result = new ArrayList<String>();
                    }
                    result.add(value.substring(0, start));
                    value = value.substring(start + delimiter.length());
                    start = -1;
                } else {
                    if (start == 1) {
                        value = value.substring(1);
                    } else {
                        value = value.substring(0, start - 1).concat(value.substring(start));
                        start--;
                    }
                }
            }

        }

        if (result == null) {
            return new String[] { value };
        }
        return result.toArray(new String[result.size()]);
    }
}

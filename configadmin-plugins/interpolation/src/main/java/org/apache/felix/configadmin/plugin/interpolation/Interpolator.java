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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Replace place holders in a string
 */
public class Interpolator {

    public static final char END = ']';

    public static final String START = "$[";

    public static final char ESCAPE = '\\';

    /**
     * The value for the replacement is returned by this provider
     */
    @FunctionalInterface
    public static interface Provider {

        Object provide(String type, String name, Map<String, String> directives);
    }

    private static int getNextStartMarker(final AtomicReference<String> valueRef, final int pos) {
        final String value = valueRef.get();
        final int start = value.indexOf(START, pos);
        if (start == -1) {
            // no placeholder found
            return -1;
        }
    
        if (start > 0 && value.charAt(start - 1) == ESCAPE
            && (start == 1 || value.charAt(start - 2) != ESCAPE)) {
            // placeholder is escaped -> remove escape and continue
            valueRef.set(value.substring(0, start - 1).concat(value.substring(start)));
    
            return getNextStartMarker(valueRef, start + 1);
        }
        return start;    
    }

    private static int[] getMarkerBoundaries(final AtomicReference<String> valueRef, final int pos) {
        final int start = getNextStartMarker(valueRef, pos);
        if ( start == -1 ) {
            return null;
        }

        // find END marker
        String value = valueRef.get();
        int index = start + START.length();
        int end = -1;
        int count = 1;
        while ( index < value.length() && count > 0 ) {            
            if ( value.charAt(index) == END ) {
                if ( value.charAt(index - 1) != ESCAPE || value.charAt(index - 2) == ESCAPE ) {
                    end = index;
                    count--;
                } else {
                    // remove escape
                    value = value.substring(0, index - 1).concat(value.substring(index));
                    valueRef.set(value);
                    index--;
                }
            }
            if ( value.charAt(index) == '[' ) {
                if ( value.charAt(index - 1) != ESCAPE || value.charAt(index - 2) == ESCAPE ) {
                    count++;
                } else {
                    // remove escape
                    value = value.substring(0, index - 1).concat(value.substring(index));
                    valueRef.set(value);
                    index--;
                }    
            }
            index++;
        }
        if ( count > 0 ) {
            // no end marker found
            return null;
        }
        return new int[] {start, end};
    } 

    /**
     * Replace all place holders
     *
     * @param value    Value with place holders
     * @param provider Provider for providing the values
     * @return Replaced object (or original value)
     */
    public static Object replace(final String value, final Provider provider) {
        String result = value;
        int index = -1;
        while (index < result.length()) {
            final AtomicReference<String> ref = new AtomicReference<>(result);
            final int[] boundaries = getMarkerBoundaries(ref, index);
            result = ref.get();
            if (boundaries == null) {
                // no placeholder found -> end
                index = result.length();
                continue;
            }

            final String key = result.substring(boundaries[0] + START.length(), boundaries[1]);
            final int sep = key.indexOf(':');
            if (sep == -1) {
                // invalid key
                index = index + START.length();
                continue;
            }

            final String type = key.substring(0, sep);
            final String postfix = key.substring(sep + 1);

            final int dirPos = postfix.indexOf(';');
            final Map<String, String> directives;
            final String name;
            if (dirPos != -1) {
                directives = parseDirectives(postfix.substring(dirPos + 1));
                name = postfix.substring(0, dirPos);
            } else {
                directives = Collections.emptyMap();
                name = postfix;
            }

            // recursive replacement
            final Object newName = replace(name, provider);

            Object replacement = provider.provide(type, newName.toString(), directives);
            if (replacement == null) {
                // no replacement found -> leave as is and continue
                index = index + START.length();
            } else {
                // if replacement is not a string and placeholder is complete string, return that object
                if (!(replacement instanceof String) && boundaries[0] == 0 && boundaries[1] == result.length() - 1) {
                    return replacement;
                }
                // replace and continue with replacement
                result = result.substring(0, boundaries[0]).concat(replacement.toString()).concat(result.substring(boundaries[1] + 1));
            }
        }
        return result;
    }

    public static Map<String,String> parseDirectives(String value) {
        final Map<String,String> directives = new HashMap<>();
        int index = 0;
        while ( index <= value.length()) {
            boolean split = false;
            if ( index == value.length() ) {
                split = true;
            } else  if ( value.charAt(index) == ';' ) {
                if ( index > 0 && value.charAt(index - 1) == ESCAPE && (index == 1 || value.charAt(index - 2) != ESCAPE) ) {
                    // escape
                    value = value.substring(0, index - 1).concat(value.substring(index));
                    index--;
                } else {
                    split = true;
                }
            }
            if ( split ) {
                final String[] kv = value.substring(0, index).split("=", 2);
                if (kv.length > 0) {
                    final String directiveValue = kv.length == 2 ? kv[1] : "";
                    directives.put(kv[0], directiveValue);
                }
                if ( index == value.length() ) {
                    break;
                }
                value = value.substring(index + 1);
                index = -1;         
            }
            index++;
        }

        return directives;
    }
}
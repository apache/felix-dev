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
package org.apache.felix.framework;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;

class FilterTest
{
    @Test
    void missingAttribute()
    {
        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("one", "one-value");
        dict.put("two", "two-value");
        dict.put("three", "three-value");
        Filter filter = null;
        try
        {
            filter = FrameworkUtil.createFilter("(missing=value)");
        }
        catch (Exception ex)
        {
            assertThat(false).as("Filter should parse: " + ex).isTrue();
        }
        assertThat(filter.match(dict)).as("Filter should not match: " + filter).isFalse();
    }

    @Test
    void array() throws InvalidSyntaxException
    {
        Filter filter = FrameworkUtil.createFilter(
            "(&(checkBool=true)(checkString=A)(checkString=B))");

        //Array
        String[] array = new String[] { "A", "B" };
        assertThat(filter.match(createTestDict(array))).isTrue();

        //ArrayList
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.addAll(Arrays.asList(array));
        assertThat(filter.match(createTestDict(arrayList))).isTrue();

        //unmodifiableList
        List<String> unmodifiableList = Collections.unmodifiableList(arrayList);
        assertThat(filter.match(createTestDict(unmodifiableList))).isTrue();

        //unmodCollection
        Collection<String> unmodCollection = Collections.unmodifiableCollection(
            arrayList);
        assertThat(filter.match(createTestDict(unmodCollection))).isTrue();

        //hashSet
        Set<String> hashSet = new HashSet<>();
        hashSet.addAll(arrayList);
        assertThat(filter.match(createTestDict(hashSet))).isTrue();

        //synchronizedCollection
        Collection<String> synchronizedCollection = Collections.synchronizedCollection(
            arrayList);
        assertThat(filter.match(createTestDict(synchronizedCollection))).isTrue();

        //linkedList
        Collection<String> linkedList = new LinkedList<>(arrayList);
        assertThat(filter.match(createTestDict(linkedList))).isTrue();
    }

    private static Dictionary<String, Object> createTestDict(Object o)
    {
        Hashtable<String, Object> dictionary = new Hashtable<>();
        dictionary.put("checkBool", true);
        dictionary.put("checkString", o);
        return dictionary;
    }

}

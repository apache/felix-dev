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

import junit.framework.TestCase;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;

public class FilterTest extends TestCase
{
    public void testMissingAttribute()
    {
        Dictionary<String, Object> dict = new Hashtable<String, Object>();
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
            assertTrue("Filter should parse: " + ex, false);
        }
        assertFalse("Filter should not match: " + filter, filter.match(dict));
    }

    public void testArray() throws InvalidSyntaxException
    {
        Filter filter = FrameworkUtil.createFilter(
            "(&(checkBool=true)(checkString=A)(checkString=B))");

        //Array
        String[] array = new String[] { "A", "B" };
        assertTrue(filter.match(createTestDict(array)));

        //ArrayList
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.addAll(Arrays.asList(array));
        assertTrue(filter.match(createTestDict(arrayList)));

        //unmodifiableList
        List<String> unmodifiableList = Collections.unmodifiableList(arrayList);
        assertTrue(filter.match(createTestDict(unmodifiableList)));

        //unmodCollection
        Collection<String> unmodCollection = Collections.unmodifiableCollection(
            arrayList);
        assertTrue(filter.match(createTestDict(unmodCollection)));

        //hashSet
        Set<String> hashSet = new HashSet<String>();
        hashSet.addAll(arrayList);
        assertTrue(filter.match(createTestDict(hashSet)));

        //synchronizedCollection
        Collection<String> synchronizedCollection = Collections.synchronizedCollection(
            arrayList);
        assertTrue(filter.match(createTestDict(synchronizedCollection)));

        //linkedList
        Collection<String> linkedList = new LinkedList<String>(arrayList);
        assertTrue(filter.match(createTestDict(linkedList)));
    }

    private static Dictionary<String, Object> createTestDict(Object o)
    {
        Hashtable<String, Object> dictionary = new Hashtable<String, Object>();
        dictionary.put("checkBool", true);
        dictionary.put("checkString", o);
        return dictionary;
    }

}

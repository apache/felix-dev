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

import java.util.*;


/**
 * This is a simple class that implements a <tt>Dictionary</tt>
 * from a <tt>Map</tt>. The resulting dictionary is immutable.
**/
public class MapToDictionary extends Dictionary
{
    /**
     * Map source.
    **/
    private Map m_map = null;

    public MapToDictionary(Map map)
    {
        if (map == null)
        {
            throw new IllegalArgumentException("Source map cannot be null.");
        }
        m_map = map;
    }

    @Override
	public Enumeration elements()
    {
        return Collections.enumeration(m_map.values());
    }

    @Override
	public Object get(Object key)
    {
        return m_map.get(key);
    }

    @Override
	public boolean isEmpty()
    {
        return m_map.isEmpty();
    }

    @Override
	public Enumeration keys()
    {
        return Collections.enumeration(m_map.keySet());
    }

    @Override
	public Object put(Object key, Object value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
	public Object remove(Object key)
    {
        throw new UnsupportedOperationException();
    }

    @Override
	public int size()
    {
        return m_map.size();
    }

    @Override
	public String toString()
    {
        return m_map.toString();
    }
}
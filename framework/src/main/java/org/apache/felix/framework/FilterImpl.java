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

import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.felix.framework.ServiceRegistrationImpl.ServiceReferenceImpl;
import org.apache.felix.framework.capabilityset.CapabilitySet;
import org.apache.felix.framework.capabilityset.SimpleFilter;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.framework.wiring.BundleCapabilityImpl;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleRevision;

public class FilterImpl implements Filter
{
    private final SimpleFilter m_filter;

    public FilterImpl(String filterStr) throws InvalidSyntaxException
    {
        try
        {
            m_filter = SimpleFilter.parse(filterStr);
        }
        catch (Throwable th)
        {
            throw new InvalidSyntaxException(th.getMessage(), filterStr);
        }
    }

    @Override
	public boolean match(ServiceReference<?> sr)
    {
        if (sr instanceof ServiceReferenceImpl)
        {
            return CapabilitySet.matches((ServiceReferenceImpl) sr, m_filter);
        }
        else
        {
            return CapabilitySet.matches(new WrapperCapability(sr), m_filter);
        }
    }

    @Override
	public boolean match(Dictionary<String, ? > dctnr)
    {
        return CapabilitySet.matches(new WrapperCapability(dctnr, false), m_filter);
    }

    @Override
	public boolean matchCase(Dictionary<String, ? > dctnr)
    {
        return CapabilitySet.matches(new WrapperCapability(dctnr, true), m_filter);
    }

    @Override
	public boolean matches(Map<String, ?> map)
    {
        return CapabilitySet.matches(new WrapperCapability(map), m_filter);
    }

    @Override
	public boolean equals(Object o)
    {
        return toString().equals(o.toString());
    }

    @Override
	public int hashCode()
    {
        return toString().hashCode();
    }

    @Override
	public String toString()
    {
        return m_filter.toString();
    }

    static class WrapperCapability extends BundleCapabilityImpl
    {
        private final Map<String, Object> m_map;

        public WrapperCapability(Map<String, ?> map)
        {
            super(null, null, Collections.emptyMap(), Collections.emptyMap());
                m_map = Collections.emptyMap();
                if(map != null ) {
            }
            m_map.putAll(map);
        }

        public WrapperCapability(Dictionary<String, ?> dict, boolean caseSensitive)
        {
            super(null, null, Collections.emptyMap(), Collections.emptyMap());
            m_map = new DictionaryToMap(dict, caseSensitive);
        }

        public WrapperCapability(ServiceReference<?> sr)
        {
            super(null, null, Collections.emptyMap(), Collections.emptyMap());
            m_map = new DictionaryToMap(sr.getProperties(), false);
        }

        @Override
        public BundleRevision getRevision()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String getNamespace()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Map<String, String> getDirectives()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Map<String, Object> getAttributes()
        {
            return m_map;
        }

        @Override
        public List<String> getUses()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static class DictionaryToMap implements Map<String,Object>
    {
        private final Map<String,Object> m_map;
        private final Dictionary<String,?> m_dict;

        public DictionaryToMap(Dictionary<String,?> dict, boolean caseSensitive)
        {
            if (!caseSensitive)
            {
                m_dict = null;
                m_map = new StringMap<Object>();
                if (dict != null)
                {
                    Enumeration<String> keys = dict.keys();
                    while (keys.hasMoreElements())
                    {
                        String key = keys.nextElement();
                        if (m_map.get(key) == null)
                        {
                            m_map.put(key, dict.get(key));
                        }
                        else
                        {
                            throw new IllegalArgumentException(
                                "Duplicate attribute: " + key.toString());
                        }
                    }
                }
            }
            else
            {
                m_dict = dict;
                m_map = null;
            }
        }

        @Override
		public int size()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public boolean isEmpty()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public boolean containsKey(Object o)
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public boolean containsValue(Object o)
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public Object get(Object o)
        {
            if (m_dict != null)
            {
                return m_dict.get(o);
            }
            else if (m_map != null)
            {
                return m_map.get(o);
            }
            return null;
        }

        @Override
		public Object put(String k, Object v)
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public Object remove(Object o)
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public void putAll(Map<? extends String, ? extends Object> map)
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public void clear()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public Set<String> keySet()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public Collection<Object> values()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
		public Set<Entry<String, Object>> entrySet()
        {
            return Collections.emptySet();
        }
    }
}
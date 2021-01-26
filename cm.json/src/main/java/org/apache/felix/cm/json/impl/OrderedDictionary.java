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
package org.apache.felix.cm.json.impl;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A dictionary implementation with predictable iteration order.
 *
 * Actually this class is a simple adapter from the Dictionary interface
 * to a synchronized LinkedHashMap
 */
public class OrderedDictionary extends Hashtable<String, Object> implements Serializable {

    private static final long serialVersionUID = -525111601546803041L;

    private Map<CaseInsensitiveKey, Object> map = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(final Object key) {
        if ( key == null ) {
            return false;
        }
        return map.containsKey(new CaseInsensitiveKey(key.toString()));
    }

    @Override
    public boolean containsValue(final Object value) {
        return map.containsValue(value);
    }

    @Override
    public Enumeration<String> keys() {
        return new KeyEnumeration(map.keySet().iterator());
    }

    @Override
    public Enumeration<Object> elements() {
        return Collections.enumeration(map.values());
    }

    @Override
    public Object get(final Object key) {
        if ( key == null ) {
            return null;
        }
        return map.get(new CaseInsensitiveKey(key.toString()));
    }

    @Override
    public Object put(final String key, final Object value) {
        // Make sure the value is not null
        if (value == null) {
            throw new NullPointerException();
        }
        final CaseInsensitiveKey k = new CaseInsensitiveKey(key);
        final Object oldValue = this.map.remove(k);
        this.map.put(k, value);
        return oldValue;
    }

    @Override
    public Object remove(final Object key) {
        return map.remove(new CaseInsensitiveKey(key.toString()));
    }

    @Override
    public void putAll(final Map<? extends String, ? extends Object> m) {
        for(final Map.Entry<? extends String, ? extends Object> e : m.entrySet()) {
            this.put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<String> keySet() {
        return new KeySet();
    }

    @Override
    public Collection<Object> values() {
        return this.map.values();
    }

    @Override
    public Set<java.util.Map.Entry<String, Object>> entrySet() {
        return new EntrySet();
    }

    @Override
    public boolean equals(final Object o) {
        return map.equals(o);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    private static final class CaseInsensitiveKey {

        private final String value;

        private final int hashCode;

        CaseInsensitiveKey(final String v) {
            this.value = v;
            this.hashCode = v.toUpperCase().hashCode();
        }

		@Override
		public int hashCode() {
			return this.hashCode;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
                return true;
            }
			if (!(obj instanceof CaseInsensitiveKey)) {
				return false;
            }
            final CaseInsensitiveKey other = (CaseInsensitiveKey) obj;
            if ( value == null ) {
                if ( other.value == null ) {
                    return true;
                }
                return false;
            }
            if ( other.value == null ) {
                return false;
            }
            return value.equalsIgnoreCase(other.value);
		}
    }

    private static class KeyEnumeration implements Enumeration<String> {

        private final Iterator<CaseInsensitiveKey> iterator;

        KeyEnumeration(final Iterator<CaseInsensitiveKey> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasMoreElements() {
            return iterator.hasNext();
        }

        @Override
        public String nextElement() {
            return iterator.next().value;
        }
    }


    private final class KeySet extends AbstractSet<String> {

		@Override
		public int size() {
			return OrderedDictionary.this.size();
		}

		@Override
		public boolean isEmpty() {
			return OrderedDictionary.this.isEmpty();
		}

		@Override
		public boolean contains(final Object o) {
			return OrderedDictionary.this.containsKey(o);
		}

		@Override
		public Iterator<String> iterator() {
			return new KeyIterator(OrderedDictionary.this.map.keySet());
		}

		@Override
		public boolean remove(final Object o) {
			return OrderedDictionary.this.remove(o) != null;
		}

		@Override
		public void clear() {
			OrderedDictionary.this.clear();
		}
	}

	private static final class KeyIterator implements Iterator<String> {

        private final Iterator<CaseInsensitiveKey> i;

		KeyIterator(final Collection<CaseInsensitiveKey> c) {
			this.i = c.iterator();
		}

		@Override
		public boolean hasNext() {
			return i.hasNext();
		}

		@Override
		public String next() {
			final CaseInsensitiveKey k = i.next();
			return k.value;
		}

		@Override
		public void remove() {
			i.remove();
		}
	}

	private final class EntrySet extends AbstractSet<Map.Entry<String, Object>> {

		@Override
		public int size() {
			return OrderedDictionary.this.size();
		}

		@Override
		public boolean isEmpty() {
			return OrderedDictionary.this.isEmpty();
		}

		@Override
		public Iterator<Map.Entry<String, Object>> iterator() {
			return new EntryIterator(OrderedDictionary.this.map.entrySet());
		}

		@Override
		public void clear() {
			OrderedDictionary.this.clear();
		}
	}

	private static final class EntryIterator implements Iterator<Map.Entry<String, Object>> {
        
        private final Iterator<Map.Entry<CaseInsensitiveKey, Object>> i;

		EntryIterator(final Collection<Map.Entry<CaseInsensitiveKey, Object>> c) {
			this.i = c.iterator();
		}

		@Override
		public boolean hasNext() {
			return i.hasNext();
		}

		@Override
		public Map.Entry<String, Object> next() {
			return new CaseInsentiveEntry(i.next());
		}

		@Override
		public void remove() {
			i.remove();
		}
	}

	private static final class CaseInsentiveEntry implements Map.Entry<String, Object> {
        
        private final Map.Entry<CaseInsensitiveKey, Object> entry;

		CaseInsentiveEntry(final Map.Entry<CaseInsensitiveKey, Object> entry) {
			this.entry = entry;
		}

		@Override
		public String getKey() {
			return entry.getKey().value;
		}

		@Override
		public Object getValue() {
			return entry.getValue();
		}

		@Override
		public Object setValue(final Object value) {
			return entry.setValue(value);
		}

		@Override
		public int hashCode() {
            return entry.hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj instanceof CaseInsentiveEntry) {
                final CaseInsentiveEntry other = (CaseInsentiveEntry) obj;
                return Objects.equals(other.entry.getKey(), this.entry.getKey()) && Objects.equals(other.entry.getValue(), this.entry.getValue());
			} else if ( obj instanceof Map.Entry ) {
                final Map.Entry<?, ?> other = (Map.Entry<?, ?>) obj;
                return Objects.equals(other.getKey(), this.entry.getKey()) && Objects.equals(other.getValue(), this.entry.getValue());
            }
			return false;
		}
	}
}

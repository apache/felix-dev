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
package org.apache.felix.http.base.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class HttpConfig {

    public static final String PROP_INVALIDATE_SESSION = "org.apache.felix.http.session.invalidate";

    public static final boolean DEFAULT_INVALIDATE_SESSION = true;

    public static final String PROP_UNIQUE_SESSION_ID = "org.apache.felix.http.session.uniqueid";

    public static final boolean DEFAULT_UNIQUE_SESSION_ID = true;

    private volatile boolean uniqueSessionId;

    private volatile boolean invalidateContainerSession;

    public static final String PROP_CONTAINER_ADDED_ATTRIBUTE = "org.apache.felix.http.session.container.attribute";

    private volatile Set<String> containerAddedAttribueSet;

    public boolean isUniqueSessionId() {
        return uniqueSessionId;
    }

    public void setUniqueSessionId(boolean appendSessionId) {
        this.uniqueSessionId = appendSessionId;
    }

    public boolean isInvalidateContainerSession() {
        return invalidateContainerSession;
    }

    public void setInvalidateContainerSession(boolean invalidateContainerSession) {
        this.invalidateContainerSession = invalidateContainerSession;
    }

    public Set<String> getContainerAddedAttribueSet() {
        return containerAddedAttribueSet;
    }

    public void setContainerAddedAttribueSet(Set<String> containerAddedAttribueSet) {
        this.containerAddedAttribueSet = containerAddedAttribueSet;
    }


    public void configure(@NotNull final Dictionary<String, Object> props) {
        this.setUniqueSessionId(this.getBooleanProperty(props, PROP_UNIQUE_SESSION_ID, DEFAULT_UNIQUE_SESSION_ID));
        this.setInvalidateContainerSession(this.getBooleanProperty(props, PROP_INVALIDATE_SESSION, DEFAULT_INVALIDATE_SESSION));
        this.setContainerAddedAttribueSet(this.getStringSetProperty(props, PROP_CONTAINER_ADDED_ATTRIBUTE));
    }


    private boolean getBooleanProperty(final Dictionary<String, Object> props, final String name, final boolean defValue)
    {
        final Object v = props.get(name);
        if ( v != null )
        {
            final String value = String.valueOf(v);
            return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
        }

        return defValue;
    }


    /**
     * Get the property value as a string array.
     * Empty values are filtered out - if the resulting array is empty
     * the default value is returned.
     */
    private String[] getStringArrayProperty(final Dictionary<String, Object> props,String name, String[] defValue)
    {
        Object value = props.get(name);
        if (value instanceof String)
        {
            final String stringVal = ((String) value).trim();
            if (stringVal.length() > 0)
            {
                return stringVal.split(",");
            }
        }
        else if (value instanceof String[])
        {
            final String[] stringArr = (String[]) value;
            final List<String> list = new ArrayList<>();
            for (final String stringVal : stringArr)
            {
                if (stringVal.trim().length() > 0)
                {
                    list.add(stringVal.trim());
                }
            }
            if (list.size() > 0)
            {
                return list.toArray(new String[list.size()]);
            }
        }
        else if (value instanceof Collection)
        {
            final ArrayList<String> conv = new ArrayList<>();
            for (Iterator<?> vi = ((Collection<?>) value).iterator(); vi.hasNext();)
            {
                Object object = vi.next();
                if (object != null)
                {
                    conv.add(String.valueOf(object));
                }
            }
            if (conv.size() > 0)
            {
                return conv.toArray(new String[conv.size()]);
            }
        }
        return defValue;
    }

    /**
     * get Property values in set format,so that it can directly compare with remaining
     * attributes of session.using set, as it will take O(1) time for searching.
     * @param props
     * @param name
     * @return
     */
    private Set<String> getStringSetProperty(final Dictionary<String, Object> props,String name) {

        String array[] = getStringArrayProperty(props,name,new String[0]) ;
        Set<String> propertySet = new HashSet<>();

        for(String property : array){

            if(property != null && !"".equals(property.trim())){
                propertySet.add(property);
            }
        }

        return  propertySet;
    }
}

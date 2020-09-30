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

import java.util.Dictionary;

import org.jetbrains.annotations.NotNull;

public class HttpConfig {

    public static final String PROP_INVALIDATE_SESSION = "org.apache.felix.http.session.invalidate";

    public static final boolean DEFAULT_INVALIDATE_SESSION = true;

    public static final String PROP_UNIQUE_SESSION_ID = "org.apache.felix.http.session.uniqueid";

    public static final boolean DEFAULT_UNIQUE_SESSION_ID = true;

    private volatile boolean uniqueSessionId;

    private volatile boolean invalidateContainerSession;

    public static final String PROP_CONTAINER_ADDED_ATTRIBUTE = "org.apache.felix.http.session.container.attribute";

    public static final String DEFAULT_CONTAINER_ADDED_ATTRIBUTE = "org.eclipse.jetty.security.sessionCreatedSecure";

    private volatile String containerAddedAttribue ;

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

    public String getContainerAddedAttribue() { return containerAddedAttribue; }

    public void setContainerAddedAttribue(String containerAddedAttribue) { this.containerAddedAttribue = containerAddedAttribue; }


    public void configure(@NotNull final Dictionary<String, Object> props) {
        this.setUniqueSessionId(this.getBooleanProperty(props, PROP_UNIQUE_SESSION_ID, DEFAULT_UNIQUE_SESSION_ID));
        this.setInvalidateContainerSession(this.getBooleanProperty(props, PROP_INVALIDATE_SESSION, DEFAULT_INVALIDATE_SESSION));
        this.setContainerAddedAttribue(this.getStringProperty(props, PROP_CONTAINER_ADDED_ATTRIBUTE, DEFAULT_CONTAINER_ADDED_ATTRIBUTE));
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

    private String getStringProperty(final Dictionary<String, Object> props,String name, String defValue) {
        Object value = props.get(name);

        if (value !=null && value instanceof String) {
            final String stringVal = ((String) value).trim();
            return stringVal ;
        }
        return defValue;
    }
}

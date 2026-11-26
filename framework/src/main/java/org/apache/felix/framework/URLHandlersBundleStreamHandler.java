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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.*;

import org.apache.felix.framework.plurl.PlurlStreamHandlerBase;

import org.apache.felix.framework.util.SecureAction;
import org.apache.felix.framework.util.Util;

class URLHandlersBundleStreamHandler extends PlurlStreamHandlerBase
{
    private final Object m_framework;
    private final SecureAction m_action;

    public URLHandlersBundleStreamHandler(Object framework, SecureAction action)
    {
        m_framework = framework;
        m_action = action;
    }

    public URLHandlersBundleStreamHandler(SecureAction action)
    {
        m_framework = null;
        m_action = action;
    }

    @Override
	public URLConnection openConnection(URL url) throws IOException
    {
        Object framework = m_framework;

        if (framework == null)
        {
            framework = URLHandlers.getFrameworkFromContext(Util.getFrameworkUUIDFromURL(url.getHost()));
        }

        if (framework != null)
        {
            if (framework instanceof Felix)
            {
                return new URLHandlersBundleURLConnection(url, (Felix) framework);
            }
            try
            {
                ClassLoader loader = m_action.getClassLoader(framework.getClass());

                Class<?> targetClass = loader.loadClass(
                    URLHandlersBundleURLConnection.class.getName());

                Constructor<?> constructor = m_action.getConstructor(targetClass,
                        new Class[]{URL.class, loader.loadClass(
                                Felix.class.getName())});
                m_action.setAccesssible(constructor);
                return (URLConnection) m_action.invoke(constructor, new Object[]{url, framework});
            }
            catch (Exception ex)
            {
                throw new IOException(ex.getMessage());
            }
        }
        throw new IOException("No framework context found");
    }

    @Override
	protected void parseURL(URL u, String spec, int start, int limit)
    {
        super.parseURL(u, spec, start, limit);

        super.setURL(u, u.getProtocol(), u.getHost(), u.getPort(), "felix", u.getUserInfo(), u.getPath(), u.getQuery(), u.getRef());
    }

    @Override
	public String toExternalForm(URL u)
    {
        StringBuilder result = new StringBuilder();
        result.append(u.getProtocol());
        result.append("://");
        result.append(u.getHost());
        result.append(':');
        result.append(u.getPort());
        if (u.getPath() != null)
        {
            result.append(u.getPath());
        }
        if (u.getQuery() != null)
        {
            result.append('?');
            result.append(u.getQuery());
        }
        if (u.getRef() != null)
        {
            result.append("#");
            result.append(u.getRef());
        }
        return result.toString();
    }

    @Override
	public java.net.InetAddress getHostAddress(URL u)
    {
        return null;
    }
}

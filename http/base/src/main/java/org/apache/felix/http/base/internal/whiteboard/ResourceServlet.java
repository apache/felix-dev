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
package org.apache.felix.http.base.internal.whiteboard;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.felix.http.base.internal.util.MimeTypes;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The resource servlet
 */
public class ResourceServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** The path of the resource registration. */
    private final String prefix;

    /**
     * The prefix for the resource
     * @param prefix The prefix
     */
    public ResourceServlet(final String prefix) {
        this.prefix = prefix;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse res)
            throws ServletException, IOException {
        final String target = req.getPathInfo();
        final String resName = (target == null ? this.prefix : this.prefix.concat(target));

        final URL url = getServletContext().getResource(resName);

        if (url == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            handle(req, res, url, resName);
        }
    }

    private void handle(final HttpServletRequest req, final HttpServletResponse res, final URL url, final String resName)
    throws IOException {
        String contentType = getServletContext().getMimeType(resName);
        if (contentType == null) {
            contentType = MimeTypes.get().getByFile(resName);
        }
        if (contentType != null) {
            res.setContentType(contentType);
        }

        final URLConnection conn = url.openConnection();

        final long lastModified = getLastModified(conn);
        if (lastModified != 0) {
            res.setDateHeader("Last-Modified", lastModified);
        }

        if (!resourceModified(lastModified, req.getDateHeader("If-Modified-Since"))) {
            res.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        } else {
            copyResource(conn, res);
        }
    }

    private File getFile(final URL url) {
        if (url.getProtocol().equals("file")) {
            try {
                return new File(url.toURI());
            } catch (URISyntaxException e) {
                return new File(url.getPath());
            }
        }
        return null;
    }

    private long getLastModified(final URLConnection conn) {
        long lastModified = conn.getLastModified();

        if (lastModified == 0) {
            final File f = getFile(conn.getURL());
            if ( f != null && f.exists()) {
                lastModified = f.lastModified();
            }
        }

        return lastModified;
    }

    private boolean resourceModified(long resTimestamp, long modSince) {
        modSince /= 1000;
        resTimestamp /= 1000;

        return resTimestamp == 0 || modSince == -1 || resTimestamp > modSince;
    }

    private void copyResource(final URLConnection conn, final HttpServletResponse res) throws IOException {
        try(final InputStream is = conn.getInputStream()) {
            // FELIX-3987 content length should be set *before* any streaming is done
            // as headers should be written before the content is actually written...
            final long len = getContentLength(conn);
            if (len >= 0) {
                res.setContentLengthLong(len);
            }

            byte[] buf = new byte[1024];
            int n;

            // no need to close output stream as this is done by the servlet container
            final OutputStream os = res.getOutputStream();
            while ((n = is.read(buf, 0, buf.length)) > 0) {
                os.write(buf, 0, n);
            }
            os.flush();
        }
    }

    private long getContentLength(final URLConnection conn) {
        long length = conn.getContentLengthLong();
        if (length < 0) {
            // Unknown, try whether it is a file, and if so, use the file
            // API to get the length of the content...
            final File f = getFile(conn.getURL());
            if ( f != null && f.exists()) {
                length = f.length();
            }
        }
        return length;
    }
}

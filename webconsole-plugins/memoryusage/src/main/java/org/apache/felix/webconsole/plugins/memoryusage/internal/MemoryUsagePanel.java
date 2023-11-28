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
package org.apache.felix.webconsole.plugins.memoryusage.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.NoSuchElementException;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.felix.inventory.Format;
import org.apache.felix.inventory.InventoryPrinter;
import org.apache.felix.inventory.ZipAttachmentProvider;
import org.apache.felix.webconsole.servlet.AbstractServlet;
import org.apache.felix.webconsole.servlet.RequestVariableResolver;
import org.osgi.service.log.LogService;

class MemoryUsagePanel extends AbstractServlet implements ZipAttachmentProvider, InventoryPrinter {

    private final MemoryUsageSupport support;

    MemoryUsagePanel(final MemoryUsageSupport support)
    {
        this.support = support;
    }

    // ---------- AbstractWebConsolePlugin

    @Override
    public void renderContent(HttpServletRequest req, HttpServletResponse res) throws IOException
    {
        final PrintWriter pw = res.getWriter();

        final StringBuilder statusBuf = new StringBuilder(1024);
        statusBuf.append('{');

        final StringBuilder filesBuf = new StringBuilder(1024);
        filesBuf.append('[');

        final File[] files = support.getDumpFiles();
        if (files != null)
        {
            long totalSize = 0;

            for (File file : files)
            {
                filesBuf.append('{');
                filesBuf.append("'name':'").append(file.getName());
                filesBuf.append("',").append("'date':").append(file.lastModified());
                support.formatNumber(filesBuf, "size", file.length());
                filesBuf.append("},");
                totalSize += file.length();
            }

            statusBuf.append("'files':").append(files.length);
            support.formatNumber(statusBuf, "total", totalSize);
        }
        else
        {
            statusBuf.append("'files':0,'total':0");
        }

        filesBuf.append(']');
        statusBuf.append('}');

        JsonPrintHelper jph = new JsonPrintHelper();
        support.printOverallMemory(jph);

        RequestVariableResolver resolver = this.getVariableResolver(req);
        resolver.put("__files__", filesBuf.toString());
        resolver.put("__status__", statusBuf.toString());
        resolver.put("__threshold__", String.valueOf(support.getThreshold()));
        resolver.put("__interval__", String.valueOf(support.getInterval()));
        resolver.put("__overall__", jph.getString());
        resolver.put("__pools__", support.getMemoryPoolsJson());

        String template = readTemplateFile("/templates/memoryusage.html");
        pw.println(template);
    }

    // ---------- Configuration Printer

    public void print(final PrintWriter pw, final Format format, final boolean isZip) {
        support.printMemory(new PrintWriterPrintHelper(pw));
    }

    // ---------- AttachmentProvider

    public void addAttachments(final ZipOutputStream stream, final String prefix) throws IOException {
        File[] dumpFiles = support.getDumpFiles();
        if (dumpFiles != null) {
            for(final File f : dumpFiles) {
                final ZipEntry entry = new ZipEntry(prefix + f.getName());
                entry.setTime(f.lastModified());
                stream.putNextEntry(entry);

                try(final FileInputStream fis = new FileInputStream(f)) {
                    final byte[] buf = new byte[32768];
                    int rd = 0;
                    while ((rd = fis.read(buf)) >= 0) {
                        stream.write(buf, 0, rd);
                    }
                }
                stream.closeEntry();
            }
        }
    }

    // ---------- GenericServlet

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {

        final DumpFile dumpFile = getDumpFile(request);
        if (dumpFile != null)
        {
            spool(dumpFile.dumpFile, response, dumpFile.compress);
        }

        super.doGet(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {

        if ("DELETE".equals(req.getParameter("X-Request-Method")))
        {
            doDelete(req, resp);
        }
        else
        {
            String command = req.getParameter("command");
            if ("dump".equals(command))
            {
                resp.setContentType("text/plain; charset=UTF-8");
                try
                {
                    File file = support.dumpHeap(null, false);
                    resp.getWriter().print("Dumped heap to " + file.getName());
                }
                catch (NoSuchElementException e)
                {
                    resp.getWriter().print(
                        "Failed dumping the heap, JVM does not provide known mechanism to create a Heap Dump");
                    support.log(LogService.LOG_ERROR, "Heap Dump creation failed: JVM has no known Heap Dump API");
                }
            }
            else if ("gc".equals(command))
            {
                System.gc();
            }
            else if ("threshold".equals(command))
            {
                try
                {
                    int threshold = Integer.parseInt(req.getParameter("threshold"));
                    support.setThreshold(threshold);
                }
                catch (Exception e)
                {
                    // ignore
                }
                resp.sendRedirect(req.getRequestURI());
            }
            else if ("interval".equals(command))
            {
                try
                {
                    int interval = Integer.parseInt(req.getParameter("interval"));
                    support.setInterval(interval);
                }
                catch (Exception e)
                {
                    // ignore
                }
                resp.sendRedirect(req.getRequestURI());
            }
            else
            {
                super.doPost(req, resp);
            }
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        final DumpFile dumpFile = getDumpFile(request);
        if (dumpFile != null)
        {
            dumpFile.dumpFile.delete();
            response.setStatus(HttpServletResponse.SC_OK);
        }
        else
        {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    // ---------- internal

    private DumpFile getDumpFile(final HttpServletRequest request)
    {
        final String pathInfo = request.getPathInfo();
        if (pathInfo != null && !pathInfo.endsWith(MemoryUsageConstants.LABEL))
        {
            final int lastSlash = pathInfo.lastIndexOf('/');
            if (lastSlash > 0)
            {
                String label = pathInfo.substring(lastSlash + 1);
                boolean isZip = false;
                if (label.endsWith(".zip"))
                {
                    label = label.substring(0, label.length() - 4);
                    isZip = true;
                }
                File dumpFile = support.getDumpFile(label);
                if (dumpFile != null)
                {
                    return new DumpFile(dumpFile, isZip);
                }
            }
        }

        return null;
    }

    private void spool(final File dumpFile, final HttpServletResponse response, boolean compress) throws IOException
    {
        InputStream ins = null;
        try
        {
            ins = new FileInputStream(dumpFile);

            response.setDateHeader("Last-Modified", dumpFile.lastModified());
            AbstractServlet.setNoCache(response);

            OutputStream out = response.getOutputStream();

            response.setStatus(HttpServletResponse.SC_OK);
            if (compress)
            {
                ZipOutputStream zip = new ZipOutputStream(out);
                zip.setLevel(Deflater.BEST_SPEED);

                ZipEntry entry = new ZipEntry(dumpFile.getName());
                entry.setTime(dumpFile.lastModified());
                entry.setMethod(ZipEntry.DEFLATED);

                zip.putNextEntry(entry);

                out = zip;

                // zip output with unknown length
                response.setContentType("application/zip");

            }
            else
            {

                String type = getServletContext().getMimeType(dumpFile.getName());
                if (type == null)
                {
                    type = "application/octet-stream";
                }

                response.setContentType(type);
                response.setHeader("Content-Length", String.valueOf(dumpFile.length())); // might be bigger than
                // int
            }

            byte[] buf = new byte[32768];
            int rd = 0;
            while ((rd = ins.read(buf)) >= 0)
            {
                out.write(buf, 0, rd);
            }

            if (compress)
            {
                out.flush();
                ((ZipOutputStream) out).closeEntry();
                ((ZipOutputStream) out).finish();
            }

        }
        finally
        {
            if (ins != null)
            {
                try
                {
                    ins.close();
                }
                catch (IOException ignore)
                {
                    // ignore
                }
            }
        }
    }

    private static class DumpFile
    {

        final File dumpFile;

        final boolean compress;

        DumpFile(final File dumpFile, final boolean compress)
        {
            this.dumpFile = dumpFile;
            this.compress = compress;
        }
    }

    private static class PrintWriterPrintHelper implements MemoryUsageSupport.PrintHelper
    {

        private static final String INDENTS = "          ";

        private final PrintWriter pw;

        private String indent;

        PrintWriterPrintHelper(final PrintWriter pw)
        {
            this.pw = pw;
            this.indent = "";
        }

        public void title(String title, int level)
        {
            pw.printf("%n%s%s%n", getIndent(level), title);
            indent = getIndent(level + 1);
        }

        public void val(String value)
        {
            pw.printf("%s%s%n", indent, value);
        }

        public void keyVal(final String key, final Object value)
        {
            if (value == null)
            {
                val(key);
            }
            else
            {
                pw.printf("%s%s: %s%n", indent, key, value);
            }
        }

        private static String getIndent(final int level)
        {
            final int indent = 2 * level;
            if (indent > INDENTS.length())
            {
                return INDENTS;
            }
            return INDENTS.substring(0, indent);
        }
    }

    private static class JsonPrintHelper implements MemoryUsageSupport.PrintHelper
    {

        private final StringBuilder buf;

        JsonPrintHelper()
        {
            buf = new StringBuilder();
            buf.append('{');
        }

        String getString()
        {
            final String result = buf.append('}').toString();
            buf.delete(1, buf.length());
            return result;
        }

        public void title(String title, int level)
        {
        }

        public void keyVal(String key, Object value)
        {
            if (value == null)
            {
                val(key);
            }
            else
            {
                buf.append('\'');
                buf.append(key);
                buf.append("':'");
                buf.append(value);
                buf.append("',");
            }
        }

        public void val(String value)
        {
            buf.append("'");
            buf.append(value);
            buf.append("':'',");
        }

    }
}

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
package org.apache.felix.webconsole.internal.servlet;

import java.util.Map;
import java.util.HashMap;

public final class MimeTypes {

    private static final Map<String, String> extMap = new HashMap<String, String>();
    static {
        extMap.put("abs", "audio/x-mpeg");
        extMap.put("ai", "application/postscript");
        extMap.put("aif", "audio/x-aiff");
        extMap.put("aifc", "audio/x-aiff");
        extMap.put("aiff", "audio/x-aiff");
        extMap.put("aim", "application/x-aim");
        extMap.put("art", "image/x-jg");
        extMap.put("asf", "video/x-ms-asf");
        extMap.put("asx", "video/x-ms-asf");
        extMap.put("au", "audio/basic");
        extMap.put("avi", "video/x-msvideo");
        extMap.put("avx", "video/x-rad-screenplay");
        extMap.put("bcpio", "application/x-bcpio");
        extMap.put("bin", "application/octet-stream");
        extMap.put("bmp", "image/bmp");
        extMap.put("body", "text/html");
        extMap.put("cdf", "application/x-cdf");
        extMap.put("cer", "application/x-x509-ca-cert");
        extMap.put("class", "application/java");
        extMap.put("cpio", "application/x-cpio");
        extMap.put("csh", "application/x-csh");
        extMap.put("css", "text/css");
        extMap.put("dib", "image/bmp");
        extMap.put("doc", "application/msword");
        extMap.put("dtd", "application/xml-dtd");
        extMap.put("dv", "video/x-dv");
        extMap.put("dvi", "application/x-dvi");
        extMap.put("eps", "application/postscript");
        extMap.put("etx", "text/x-setext");
        extMap.put("exe", "application/octet-stream");
        extMap.put("gif", "image/gif");
        extMap.put("gk", "application/octet-stream");
        extMap.put("gtar", "application/x-gtar");
        extMap.put("gz", "application/x-gzip");
        extMap.put("hdf", "application/x-hdf");
        extMap.put("hqx", "application/mac-binhex40");
        extMap.put("htc", "text/x-component");
        extMap.put("htm", "text/html");
        extMap.put("html", "text/html");
        extMap.put("hqx", "application/mac-binhex40");
        extMap.put("ief", "image/ief");
        extMap.put("jad", "text/vnd.sun.j2me.app-descriptor");
        extMap.put("jar", "application/java-archive");
        extMap.put("java", "text/plain");
        extMap.put("jnlp", "application/x-java-jnlp-file");
        extMap.put("jpe", "image/jpeg");
        extMap.put("jpeg", "image/jpeg");
        extMap.put("jpg", "image/jpeg");
        extMap.put("js", "text/javascript");
        extMap.put("kar", "audio/x-midi");
        extMap.put("latex", "application/x-latex");
        extMap.put("m3u", "audio/x-mpegurl");
        extMap.put("mac", "image/x-macpaint");
        extMap.put("man", "application/x-troff-man");
        extMap.put("mathml", "application/mathml+xml");
        extMap.put("me", "application/x-troff-me");
        extMap.put("mid", "audio/x-midi");
        extMap.put("midi", "audio/x-midi");
        extMap.put("mif", "application/x-mif");
        extMap.put("mov", "video/quicktime");
        extMap.put("movie", "video/x-sgi-movie");
        extMap.put("mp1", "audio/x-mpeg");
        extMap.put("mp2", "audio/x-mpeg");
        extMap.put("mp3", "audio/x-mpeg");
        extMap.put("mpa", "audio/x-mpeg");
        extMap.put("mpe", "video/mpeg");
        extMap.put("mpeg", "video/mpeg");
        extMap.put("mpega", "audio/x-mpeg");
        extMap.put("mpg", "video/mpeg");
        extMap.put("mpv2", "video/mpeg2");
        extMap.put("ms", "application/x-wais-source");
        extMap.put("nc", "application/x-netcdf");
        extMap.put("oda", "application/oda");
        extMap.put("ogg", "application/ogg");
        extMap.put("pbm", "image/x-portable-bitmap");
        extMap.put("pct", "image/pict");
        extMap.put("pdf", "application/pdf");
        extMap.put("pgm", "image/x-portable-graymap");
        extMap.put("pic", "image/pict");
        extMap.put("pict", "image/pict");
        extMap.put("pls", "audio/x-scpls");
        extMap.put("png", "image/png");
        extMap.put("pnm", "image/x-portable-anymap");
        extMap.put("pnt", "image/x-macpaint");
        extMap.put("ppm", "image/x-portable-pixmap");
        extMap.put("ppt", "application/powerpoint");
        extMap.put("ps", "application/postscript");
        extMap.put("psd", "image/x-photoshop");
        extMap.put("qt", "video/quicktime");
        extMap.put("qti", "image/x-quicktime");
        extMap.put("qtif", "image/x-quicktime");
        extMap.put("ras", "image/x-cmu-raster");
        extMap.put("rdf", "application/rdf+xml");
        extMap.put("rgb", "image/x-rgb");
        extMap.put("rm", "application/vnd.rn-realmedia");
        extMap.put("roff", "application/x-troff");
        extMap.put("rtf", "application/rtf");
        extMap.put("rtx", "text/richtext");
        extMap.put("sh", "application/x-sh");
        extMap.put("shar", "application/x-shar");
        extMap.put("shtml", "text/x-server-parsed-html");
        extMap.put("sit", "application/x-stuffit");
        extMap.put("smf", "audio/x-midi");
        extMap.put("snd", "audio/basic");
        extMap.put("src", "application/x-wais-source");
        extMap.put("sv4cpio", "application/x-sv4cpio");
        extMap.put("sv4crc", "application/x-sv4crc");
        extMap.put("svg", "image/svg+xml");
        extMap.put("svgz", "image/svg+xml");
        extMap.put("swf", "application/x-shockwave-flash");
        extMap.put("t", "application/x-troff");
        extMap.put("tar", "application/x-tar");
        extMap.put("tcl", "application/x-tcl");
        extMap.put("tex", "application/x-tex");
        extMap.put("texi", "application/x-texinfo");
        extMap.put("texinfo", "application/x-texinfo");
        extMap.put("tif", "image/tiff");
        extMap.put("tiff", "image/tiff");
        extMap.put("tr", "application/x-troff");
        extMap.put("tsv", "text/tab-separated-values");
        extMap.put("txt", "text/plain");
        extMap.put("ulw", "audio/basic");
        extMap.put("ustar", "application/x-ustar");
        extMap.put("xbm", "image/x-xbitmap");
        extMap.put("xml", "text/xml");
        extMap.put("xpm", "image/x-xpixmap");
        extMap.put("xsl", "application/xml");
        extMap.put("xslt", "application/xslt+xml");
        extMap.put("xwd", "image/x-xwindowdump");
        extMap.put("vsd", "application/x-visio");
        extMap.put("vxml", "application/voicexml+xml");
        extMap.put("wav", "audio/x-wav");
        extMap.put("wbmp", "image/vnd.wap.wbmp");
        extMap.put("wml", "text/vnd.wap.wml");
        extMap.put("wmlc", "application/vnd.wap.wmlc");
        extMap.put("wmls", "text/vnd.wap.wmls");
        extMap.put("wmlscriptc", "application/vnd.wap.wmlscriptc");
        extMap.put("wrl", "x-world/x-vrml");
        extMap.put("xht", "application/xhtml+xml");
        extMap.put("xhtml", "application/xhtml+xml");
        extMap.put("xls", "application/vnd.ms-excel");
        extMap.put("xul", "application/vnd.mozilla.xul+xml");
        extMap.put("Z", "application/x-compress");
        extMap.put("z", "application/x-compress");
        extMap.put("zip", "application/zip");
    }

    public static String getByFile(final String file) {
        if (file == null) {
            return null;
        }
        final int dot = file.lastIndexOf(".");
        if (dot < 0) {
            return null;
        }
        final String ext = file.substring(dot + 1).toLowerCase();
        return getByExtension(ext);
    }

    public static String getByExtension(final String ext){
        if (ext == null) {
            return null;
        }
        return extMap.get(ext);
    }
}

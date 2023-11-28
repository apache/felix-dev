/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.felix.hc.webconsole.impl;

import java.io.PrintWriter;

import org.owasp.encoder.Encode;
import org.owasp.encoder.Encoder;
import org.owasp.encoder.Encoders;

/** Webconsole plugin helper for writing html. */
class WebConsoleHelper {

    final PrintWriter pw;

    final Encoder encoder = Encoders.forName(Encoders.HTML);

    WebConsoleHelper(final PrintWriter w) {
        pw = w;
    }

    PrintWriter writer() {
        return pw;
    }

    void tdContent() {
        pw.print("<td class='content' colspan='2'>");
    }

    void closeTd() {
        pw.print("</td>");
    }

    void closeTr() {
        pw.println("</tr>");
    }

    void tdLabel(final String label) {
        pw.print("<td class='content'>");
        pw.print(escapeHtmlContent(label));
        pw.println("</td>");
    }

    void tr() {
        pw.println("<tr class='content'>");
    }

    void titleHtml(String title, String description) {
        tr();
        pw.print("<th colspan='3' class='content container'>");
        pw.print(escapeHtmlContent(title));
        pw.println("</th>");
        closeTr();

        if (description != null) {
            tr();
            pw.print("<td colspan='3' class='content'>");
            pw.print(escapeHtmlContent(description));
            pw.println("</th>");
            closeTr();
        }
    }
    
    String escapeHtmlContent(String text) {
        if (text==null) {
            return "";
        }
        return Encode.forHtmlContent(text);
    }

    String escapeHtmlAttr(String text) {
        if (text==null) {
            return "";
        }
        return Encode.forHtmlAttribute(text);
    }
}

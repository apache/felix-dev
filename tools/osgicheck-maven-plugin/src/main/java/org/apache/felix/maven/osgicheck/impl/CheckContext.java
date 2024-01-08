/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.felix.maven.osgicheck.impl;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.jar.Manifest;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.StringUtils;

public interface CheckContext {

    File getRootDir();

    Manifest getManifest();

    Log getLog();

    Map<String, String> getConfiguration();

    /**
     * This method is invoked by a {@link Check} to report
     * a warning.
     * @param message The message.
     */
    void reportWarning(String message);

    /**
     * This method is invoked by a {@link Check} to report
     * an error.
     * @param message The message.
     */
    void reportError(String message);

    /**
     * This method is invoked by a {@link Check} to report
     * an exception.
     * @param message The message.
     */
    default void reportError(String message, Throwable cause) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        if (StringUtils.isNotBlank(message)) {
            pw.print(message);
            pw.println(":");
        }
        cause.printStackTrace(pw);
        reportError(sw.toString());
    }
}

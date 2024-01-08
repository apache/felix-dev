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
package org.apache.felix.maven.osgicheck.impl.mddocgen;

import java.text.MessageFormat;

import org.apache.felix.scr.impl.logger.BundleLogger;
import org.apache.felix.scr.impl.logger.ComponentLogger;
import org.apache.maven.plugin.logging.Log;
import org.osgi.framework.Bundle;

final class MavenScrLogger implements BundleLogger, ComponentLogger {

    private final Log log;
    private String prefix;

    public MavenScrLogger(final Log log) {
        this.log = log;
    }

    @Override
    public boolean isLogEnabled(Level level) {
        switch (level) {
            case DEBUG:
                return log.isDebugEnabled();

            case ERROR:
                return log.isErrorEnabled();

            case INFO:
                return log.isInfoEnabled();

            case WARN:
                return log.isWarnEnabled();

            default:
                return false;
        }
    }

    @Override
    public void log(Level level, String message, Throwable ex, Object... args) {
        String msg = MessageFormat.format(message, args);

        log(level, msg, ex);
    }

    @Override
    public void log(Level level, String message, Throwable ex) {
        if (prefix != null && prefix.length() > 0)
        {
            message = prefix.concat(" ").concat(message);
        }
        switch (level) {
            case DEBUG:
                if (ex != null) {
                    log.debug(message, ex);
                } else {
                    log.debug(message);
                }
                break;

            case ERROR:
                if (ex != null) {
                    log.error(message, ex);
                } else {
                    log.error(message);
                }
                break;

            case INFO:
                if (ex != null) {
                    log.info(message, ex);
                } else {
                    log.info(message);
                }
                break;

            case WARN:
                if (ex != null) {
                    log.warn(message, ex);
                } else {
                    log.warn(message);
                }
                break;

            default:
                break;
        }
    }

    @Override
    public ComponentLogger component(Bundle m_bundle, String implementationClassName, String name) {
        return this;
    }

    @Override
    public void setComponentId(long m_componentId) {
        this.prefix = "(" + m_componentId + ") ";
    }

}

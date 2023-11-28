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
package org.apache.felix.http.base.internal.wrappers;

import org.apache.felix.http.jakartawrappers.FilterWrapper;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.servlet.whiteboard.Preprocessor;

/**
 * Preprocessor wrapper
 */
public class PreprocessorWrapper
    extends FilterWrapper
    implements Preprocessor {

    /**
     * Create new preprocessor
     * @param filter wrapped filter
     */
    public PreprocessorWrapper(@NotNull final javax.servlet.Filter filter) {
        super(filter);
    }

    /**
     * Get the preprocessor
     * @return The preprocessor
     */
    public org.osgi.service.http.whiteboard.Preprocessor getPreprocessor() {
        return (org.osgi.service.http.whiteboard.Preprocessor) super.getFilter();
    }
}

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
package org.apache.felix.http.base.internal.runtime;

import java.util.Map;
import java.util.Objects;

import org.apache.felix.http.base.internal.wrappers.PreprocessorWrapper;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.ServiceReference;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;
import org.osgi.service.servlet.whiteboard.Preprocessor;

/**
 * Provides registration information for a {@link Preprocessor}.
 * <p>
 * This class only provides information used at registration time, and as such differs
 * slightly from the corresponding DTO
 * </p>
 */
public class PreprocessorInfo extends WhiteboardServiceInfo<Preprocessor>
{
    /**
     * The preprocessor initialization parameters as provided during registration of the preprocessor.
     */
    private final Map<String, String> initParams;

    public PreprocessorInfo(final ServiceReference<Preprocessor> ref)
    {
        super(ref);
        this.initParams = getInitParams(ref, HttpWhiteboardConstants.HTTP_WHITEBOARD_PREPROCESSOR_INIT_PARAM_PREFIX);
    }

    /**
     * Returns an immutable map of the init parameters.
     */
    public Map<String, String> getInitParameters()
    {
        return initParams;
    }


    @Override
    public @NotNull String getType() {
        return "Preprocessor";
    }

    /**
     * Get the class name of the preprocessor
     * @param preprocessor The preprocesor
     * @return The class name
     */
    public @NotNull String getClassName(@NotNull final Preprocessor preprocessor) {
        if (preprocessor instanceof PreprocessorWrapper ) {
            return ((PreprocessorWrapper)preprocessor).getPreprocessor().getClass().getName();
        }
        return preprocessor.getClass().getName();
    }


    @Override
    public boolean isSame(AbstractInfo<Preprocessor> other) {
        if (!super.isSame(other)) {
            return false;
        }
        final PreprocessorInfo o = (PreprocessorInfo) other;
        return Objects.equals(this.initParams, o.initParams);
    }
}

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
package org.apache.felix.scr.integration.components;


import java.util.Map;
import java.util.Optional;

import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;

public class InjectOptionalComponent
{
    public enum Mode
    {
        FIELD_STATIC_MANDATORY(1, false, true), //
        FIELD_DYNAMIC_MANDATORY(1, true, true), //
        FIELD_STATIC_OPTIONAL(1, false, false), //
        FIELD_DYNAMIC_OPTIONAL(1, true, false), //
        CONSTRUCTOR_MANDATORY(2, false, true), //
        CONSTRUCTOR_OPTIONAL(2, false, false);

        final int initCount;
        final int initialState;
        final int secondState;
        final boolean isMandatory;
        final boolean isDynamic;

        Mode(int initCount, boolean isDynamic, boolean isMandatory)
        {
            this.initCount = initCount;
            this.initialState = isMandatory
                ? ComponentConfigurationDTO.UNSATISFIED_REFERENCE
                : ComponentConfigurationDTO.SATISFIED;
            this.secondState = isMandatory ? ComponentConfigurationDTO.SATISFIED
                : ComponentConfigurationDTO.ACTIVE;
            this.isMandatory = isMandatory;
            this.isDynamic = isDynamic;
        }

        public int getInitCount()
        {
            return initCount;
        }

        public int getInitialState()
        {
            return initialState;
        }

        public int getSecondState()
        {
            return secondState;
        }

        public final boolean isMandatory()
        {
            return isMandatory;
        }

        public final boolean isDynamic()
        {
            return isDynamic;
        }

    }

    private final Mode mode;

    private final Optional<ConstructorSingleReference> refConstructor;
    private Optional<ConstructorSingleReference> refFieldStatic = null;
    private volatile Optional<ConstructorSingleReference> refFieldDynamic = null;

    public InjectOptionalComponent(Map<String, Object> props, Optional<ConstructorSingleReference> single)
    {
        this.mode = getMode(props);
        this.refConstructor = single;
    }

    public InjectOptionalComponent(Map<String, Object> props)
    {
        this(props, null);
    }

    private Mode getMode(Map<String, Object> props)
    {
        return Mode.valueOf((String) props.get(ComponentConstants.COMPONENT_NAME));
    }

    public boolean checkMode(Mode mode, ConstructorSingleReference expected)
    {
        if (this.mode != mode)
        {
            throw new AssertionError(
                "Wrong mode, expected \"" + mode + "\" but was\"" + this.mode);
        }
        Optional<ConstructorSingleReference> optional = getOptional();
        if (expected != null)
        {
            return optional.map((c) -> checkExpected(expected, c)).orElseGet(
                () -> throwAssertionError(
                    "FAILED - expected: " + expected + " but got empty optional."));
        }
        else
        {
            return optional.map(
                (c) -> throwAssertionError(
                    "FAILED - expected empty optional but got: " + c)).orElse(true);
        }
    }

    private boolean throwAssertionError(String message)
    {
        throw new AssertionError(message);
    }

    private boolean checkExpected(ConstructorSingleReference expected,
        ConstructorSingleReference actual)
    {
        if (expected == actual)
        {
            return true;
        }
        throw new AssertionError(
            "FAILED - expected: " + expected + " bug got: " + actual);
    }

    private Optional<ConstructorSingleReference> getOptional()
    {
        switch (mode)
        {
            case CONSTRUCTOR_MANDATORY:
            case CONSTRUCTOR_OPTIONAL:
                return refConstructor;
            case FIELD_DYNAMIC_MANDATORY:
            case FIELD_DYNAMIC_OPTIONAL:
                return refFieldDynamic;
            case FIELD_STATIC_MANDATORY:
            case FIELD_STATIC_OPTIONAL:
                return refFieldStatic;
            default:
                throw new UnsupportedOperationException(String.valueOf(mode));
        }
    }

}

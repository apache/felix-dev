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
package org.apache.felix.systemready;

import static java.util.stream.Collectors.minBy;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * This framework is deprecated.
 * @deprecated Use the Apache Felix Healthchecks instead.
 */
@Deprecated
public final class CheckStatus {
    public enum State {
        // Be aware that the order of the enum declarations matters for the Comparator
        RED, YELLOW, GREEN;

        public static State fromBoolean(boolean ready) {
            return (ready) ? State.GREEN : State.YELLOW;
        }

        public static State worstOf(Stream<State> states) {
            return states.collect(minBy(State::compareTo)).orElse(State.GREEN);
        }
    }

    private final String checkName;

    private final StateType type;

    private final State state;

    private final String details;

    public CheckStatus(String checkName, StateType type, State state, String details) {
        this.checkName = Objects.requireNonNull(checkName);
        this.type = Objects.requireNonNull(type);
        this.state = Objects.requireNonNull(state);
        this.details = Objects.toString(details, "");
    }

    public String getCheckName() {
        return checkName;
    }

    public StateType getType() {
        return type;
    }

    public State getState() {
        return state;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "CheckStatus{" + "state=" + state + ", details='" + details + '\'' + '}';
    }

}

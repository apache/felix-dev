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
package org.apache.felix.hc.api.util;

public class FormatUtil {

    private static final String PLACEHOLDER = "{}";

    private FormatUtil() {

    }

    public static String format(String message, Object ... arguments) {
        if (message == null) {
            return null;
        }
        if (arguments == null || arguments.length == 0) {
            return message;
        }

        final StringBuilder builder = new StringBuilder();

        int argumentIndex = 0;
        int from = 0;
        int index;
        while (true) {
            index = message.indexOf(PLACEHOLDER, from);

            if (index == -1) {
                // No remaining placeholder found, breaking while loop
                break;
            }

            // Append all text up to the placeholder
            builder.append(message, from, index);

            if (argumentIndex < arguments.length) {
                // Append the argument from the array whom's index corresponds with the occurrence of the placeholder
                builder.append(arguments[argumentIndex]);
                argumentIndex++;
            } else {
                // If there are no arguments left, append the placeholder to the output
                builder.append(PLACEHOLDER);
            }

            // Update the 'from' to search for the next placeholder
            from = index + PLACEHOLDER.length();
        }

        if (from < message.length()) {
            // Append the remainder of the input string to complete the output
            builder.append(message.substring(from));
        }

        return builder.toString();
    }
}

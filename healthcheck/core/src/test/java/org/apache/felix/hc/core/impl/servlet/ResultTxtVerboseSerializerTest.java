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
package org.apache.felix.hc.core.impl.servlet;

import static org.junit.Assert.*;

import org.junit.Test;

public class ResultTxtVerboseSerializerTest {

    ResultTxtVerboseSerializer resultTxtVerboseSerializer = new ResultTxtVerboseSerializer();
    @Test
    public void testWordWrap() {
        String exampleText = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10";

        assertEquals("word1 word2 word3\nword4 word5 word6\nword7 word8 word9\nword10", resultTxtVerboseSerializer.wordWrap(exampleText, 20, "\n"));
        assertEquals("word1 word2 word3 word4 word5 word6\nword7 word8 word9 word10", resultTxtVerboseSerializer.wordWrap(exampleText, 40, "\n"));
        
        String longText = "Disk Usage /very/long/path/to/some/location/on/disk/somewhere/.: 88.6% of 465.6GB used / 53.1GB free";
        assertEquals("Disk Usage\n/very/long/path/to/some/locati\non/on/disk/somewhere/.: 88.6%\nof 465.6GB used / 53.1GB free", resultTxtVerboseSerializer.wordWrap(longText, 30, "\n"));
        
    }

}

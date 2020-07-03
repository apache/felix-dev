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
package org.apache.felix.hc.core.impl.scheduling.cron.embedded;

import static org.junit.Assert.assertEquals;

import java.util.Date;

import org.junit.Test;

@SuppressWarnings("deprecation")
public class EmbeddedCronParserTest {

    @Test
    public void testAt50Seconds() {
        assertEquals(new Date(2012, 6, 2, 1, 0).getTime(),
                new EmbeddedCronParser("*/15 * 1-4 * * *").next(new Date(2012, 6, 1, 9, 53, 50).getTime()));
    }

    @Test
    public void testAt0Seconds() {
        assertEquals(new Date(2012, 6, 2, 1, 0).getTime(),
                new EmbeddedCronParser("*/15 * 1-4 * * *").next(new Date(2012, 6, 1, 9, 53).getTime()));
    }

    @Test
    public void testAt0Minutes() {
        assertEquals(new Date(2012, 6, 2, 1, 0).getTime(),
                new EmbeddedCronParser("0 */2 1-4 * * *").next(new Date(2012, 6, 1, 9, 0).getTime()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWith0Increment() {
        new EmbeddedCronParser("*/0 * * * * *").next(new Date(2012, 6, 1, 9, 0).getTime());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithNegativeIncrement() {
        new EmbeddedCronParser("*/-1 * * * * *").next(new Date(2012, 6, 1, 9, 0).getTime());
    }

}
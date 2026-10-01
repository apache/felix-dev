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
package org.apache.felix.webconsole.servlet;

import java.net.URL;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import junit.framework.TestCase;

public class AbstractServletTest extends TestCase {

    public void testGetResourceNormalizesPath() {
        final TestServlet servlet = new TestServlet();

        assertNotNull(servlet.getResource("/test/res/ui/webconsole.css"));
        assertNotNull(servlet.getResource("/test/res/ui/../ui/webconsole.css"));
        assertNull(servlet.getResource("/test/res/../../META-INF/MANIFEST.MF"));
    }

    private static final class TestServlet extends AbstractServlet {

        @Override
        public void renderContent(final HttpServletRequest request, final HttpServletResponse response)
                throws ServletException {
            // nothing to render
        }

        @Override
        protected URL getResource(final String path) {
            return super.getResource(path);
        }
    }
}

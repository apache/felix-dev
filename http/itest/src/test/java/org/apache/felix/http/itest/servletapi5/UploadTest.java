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

package org.apache.felix.http.itest.servletapi5;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_MULTIPART_ENABLED;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_MULTIPART_MAXFILESIZE;
import static org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class UploadTest extends Servlet5BaseIntegrationTest {

    private static final String PATH = "/post";

    private CountDownLatch receivedLatch;

    public void setupServlet(final Map<String, Long> contents) throws Exception {
        long counter = this.getRuntimeCounter();
        Dictionary<String, Object> servletProps = new Hashtable<String, Object>();
        servletProps.put(HTTP_WHITEBOARD_SERVLET_PATTERN, PATH);
        servletProps.put(HTTP_WHITEBOARD_SERVLET_MULTIPART_ENABLED, Boolean.TRUE);
        servletProps.put(HTTP_WHITEBOARD_SERVLET_MULTIPART_MAXFILESIZE, 1024L);

        TestServlet servletWithErrorCode = new TestServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException, ServletException {
                try {
                    final Collection<Part> parts = req.getParts();
                    for(final Part p : parts) {
                        contents.put(p.getName(), p.getSize());
                    }
                    resp.setStatus(201);
                } finally{
                    receivedLatch.countDown();
                }

            }
        };

        registrations.add(m_context.registerService(Servlet.class.getName(), servletWithErrorCode, servletProps));
        this.waitForRuntime(counter);
    }

    private void postContent(final char c, final long length, final int expectedRT) throws IOException {
        final URL url = createURL(PATH);
        final CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            final HttpPost httppost = new HttpPost(url.toExternalForm());

            final StringBuilder sb = new StringBuilder();
            for(int i=0;i<length;i++) {
                sb.append(c);
            }
            final StringBody text = new StringBody(sb.toString(), ContentType.TEXT_PLAIN);

            final HttpEntity reqEntity = MultipartEntityBuilder.create()
                    .addPart("text", text)
                    .build();


            httppost.setEntity(reqEntity);

            final CloseableHttpResponse response = httpclient.execute(httppost);
            try {
                final HttpEntity resEntity = response.getEntity();
                EntityUtils.consume(resEntity);
                assertEquals(expectedRT, response.getStatusLine().getStatusCode());
            } finally {
                response.close();
            }
        } finally {
            httpclient.close();
        }
    }

    @Test
    public void testUpload() throws Exception {
        this.receivedLatch = new CountDownLatch(1);

        final Map<String, Long> contents = new HashMap<>();
        setupServlet(contents);

        postContent('a', 500, 201);
        assertTrue(receivedLatch.await(5, TimeUnit.SECONDS));
        assertEquals(1, contents.size());
        assertEquals(500L, (long)contents.get("text"));
    }

    @Test
    public void testMaxFileSize() throws Exception {
        this.receivedLatch = new CountDownLatch(1);

        final Map<String, Long> contents = new HashMap<>();
        setupServlet(contents);

        postContent('b', 2048, 500);
        assertTrue(receivedLatch.await(5, TimeUnit.SECONDS));
        assertTrue(contents.isEmpty());
    }
}

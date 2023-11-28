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
package org.apache.felix.http.base.internal.dispatch;

import java.util.Objects;

import org.apache.commons.fileupload.disk.DiskFileItemFactory;

public final class MultipartConfig
{
    public static final MultipartConfig DEFAULT_CONFIG = new MultipartConfig(null, null, -1, -1, -1);

    public static final MultipartConfig INVALID_CONFIG = new MultipartConfig(null, null, -1, -1, -1);

    /**
     * Specifies the multipart threshold
     */
    public final int multipartThreshold;

    /**
     * Specifies the multipart location
     */
    public final String multipartLocation;

    /**
     * Specifies the multipart max file size
     */
    public final long multipartMaxFileSize;

    /**
     * Specifies the multipart max request size
     */
    public final long multipartMaxRequestSize;

    /**
     * Specifies the multipart max files
     */
    public final long multipartMaxFileCount;

    public MultipartConfig(final Integer threshold,
            final String location,
            final long maxFileSize,
            final long maxRequestSize,
            final long maxFileCount)
    {
        if ( threshold != null && threshold > 0)
        {
            this.multipartThreshold = threshold;
        }
        else
        {
            this.multipartThreshold = DiskFileItemFactory.DEFAULT_SIZE_THRESHOLD;
        }
        this.multipartLocation = location;
        if ( maxFileSize > 0 || maxFileSize == -1 ) {
            this.multipartMaxFileSize = maxFileSize;
        }
        else
        {
            this.multipartMaxFileSize = -1;
        }
        if ( maxRequestSize > 0 || maxRequestSize == -1 ) {
            this.multipartMaxRequestSize = maxRequestSize;
        }
        else
        {
            this.multipartMaxRequestSize = -1;
        }
        if ( maxFileCount > 0 ) {
            this.multipartMaxFileCount = maxFileCount;
        }
        else
        {
            this.multipartMaxFileCount = 50;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(multipartThreshold, multipartLocation, multipartMaxFileSize, multipartMaxRequestSize,
                multipartMaxFileCount);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MultipartConfig other = (MultipartConfig) obj;
        return multipartThreshold == other.multipartThreshold
                && Objects.equals(multipartLocation, other.multipartLocation)
                && multipartMaxFileSize == other.multipartMaxFileSize
                && multipartMaxRequestSize == other.multipartMaxRequestSize
                && multipartMaxFileCount == other.multipartMaxFileCount;
    }
}

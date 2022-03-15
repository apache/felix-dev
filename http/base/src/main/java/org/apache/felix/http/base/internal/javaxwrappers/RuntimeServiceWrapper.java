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
package org.apache.felix.http.base.internal.javaxwrappers;

import org.osgi.framework.ServiceReference;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.servlet.whiteboard.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.whiteboard.runtime.dto.ErrorPageDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.FailedErrorPageDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.FailedFilterDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.FailedListenerDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.FailedPreprocessorDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.FailedResourceDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.FailedServletContextDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.FailedServletDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.FilterDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.ListenerDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.PreprocessorDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.RequestInfoDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.ResourceDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.RuntimeDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.ServletContextDTO;
import org.osgi.service.servlet.whiteboard.runtime.dto.ServletDTO;

/**
 * Wrapper for the service runtime
 */
public class RuntimeServiceWrapper implements org.osgi.service.http.runtime.HttpServiceRuntime {

    private final HttpServiceRuntime runtime;

    private volatile ServiceReference<org.osgi.service.http.runtime.HttpServiceRuntime> reference;

    /**
     * Create a new wrapper
     * @param runtime The original runtime service
     */
    public RuntimeServiceWrapper(final HttpServiceRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Set the service reference
     * @param reference The reference
     */
    public void setServiceReference(final ServiceReference<org.osgi.service.http.runtime.HttpServiceRuntime> reference) {
        this.reference = reference;
    }

    @Override
    public org.osgi.service.http.runtime.dto.RuntimeDTO getRuntimeDTO() {
        final RuntimeDTO orig = this.runtime.getRuntimeDTO();
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.RuntimeDTO dto = new org.osgi.service.http.runtime.dto.RuntimeDTO();
            dto.failedErrorPageDTOs = copy(orig.failedErrorPageDTOs);
            dto.failedFilterDTOs = copy(orig.failedFilterDTOs);
            dto.failedListenerDTOs = copy(orig.failedListenerDTOs);
            dto.failedPreprocessorDTOs = copy(orig.failedPreprocessorDTOs);
            dto.failedResourceDTOs = copy(orig.failedResourceDTOs);
            dto.failedServletContextDTOs = copy(orig.failedServletContextDTOs);
            dto.failedServletDTOs = copy(orig.failedServletDTOs);
            dto.preprocessorDTOs = copy(orig.preprocessorDTOs);
            dto.serviceDTO = this.reference.adapt(ServiceReferenceDTO.class);
            dto.servletContextDTOs = copy(orig.servletContextDTOs);
            return dto;
        }
        return null;
    }

    @Override
    public org.osgi.service.http.runtime.dto.RequestInfoDTO calculateRequestInfoDTO(final String path) {
        final RequestInfoDTO result = this.runtime.calculateRequestInfoDTO(path);
        if ( result != null ) {
            final org.osgi.service.http.runtime.dto.RequestInfoDTO dto = new org.osgi.service.http.runtime.dto.RequestInfoDTO();
            dto.path = result.path;
            dto.servletContextId = result.servletContextId;
            dto.filterDTOs = copy(result.filterDTOs);
            dto.resourceDTO = copy(result.resourceDTO);
            dto.servletDTO = copy(result.servletDTO);
            return dto;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.FailedErrorPageDTO[] copy(final FailedErrorPageDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.FailedErrorPageDTO[] dtos = new org.osgi.service.http.runtime.dto.FailedErrorPageDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.FailedErrorPageDTO();
                dtos[i].asyncSupported = orig[i].asyncSupported;
                dtos[i].errorCodes = orig[i].errorCodes;
                dtos[i].exceptions = orig[i].exceptions;
                dtos[i].failureReason = orig[i].failureReason;
                dtos[i].initParams = orig[i].initParams;
                dtos[i].name = orig[i].name;
                dtos[i].serviceId = orig[i].serviceId;
                dtos[i].servletContextId = orig[i].servletContextId;
                dtos[i].servletInfo = orig[i].servletInfo;
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.FailedFilterDTO[] copy(final FailedFilterDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.FailedFilterDTO[] dtos = new org.osgi.service.http.runtime.dto.FailedFilterDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.FailedFilterDTO();
                dtos[i].asyncSupported = orig[i].asyncSupported;
                dtos[i].dispatcher = orig[i].dispatcher;
                dtos[i].failureReason = orig[i].failureReason;
                dtos[i].initParams = orig[i].initParams;
                dtos[i].name = orig[i].name;
                dtos[i].patterns = orig[i].patterns;
                dtos[i].regexs = orig[i].regexs;
                dtos[i].serviceId = orig[i].serviceId;
                dtos[i].servletContextId = orig[i].servletContextId;
                dtos[i].servletNames = orig[i].servletNames;
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.FailedListenerDTO[] copy(final FailedListenerDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.FailedListenerDTO[] dtos = new org.osgi.service.http.runtime.dto.FailedListenerDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.FailedListenerDTO();
                dtos[i].failureReason = orig[i].failureReason;
                dtos[i].serviceId = orig[i].serviceId;
                dtos[i].servletContextId = orig[i].servletContextId;
                dtos[i].types = orig[i].types;
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.FailedPreprocessorDTO[] copy(final FailedPreprocessorDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.FailedPreprocessorDTO[] dtos = new org.osgi.service.http.runtime.dto.FailedPreprocessorDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.FailedPreprocessorDTO();
                dtos[i].failureReason = orig[i].failureReason;
                dtos[i].initParams = orig[i].initParams;
                dtos[i].serviceId = orig[i].serviceId;
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.FailedResourceDTO[] copy(final FailedResourceDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.FailedResourceDTO[] dtos = new org.osgi.service.http.runtime.dto.FailedResourceDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.FailedResourceDTO();
                dtos[i].failureReason = orig[i].failureReason;
                dtos[i].patterns = orig[i].patterns;
                dtos[i].prefix = orig[i].prefix;
                dtos[i].serviceId = orig[i].serviceId;
                dtos[i].servletContextId = orig[i].servletContextId;
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.FailedServletContextDTO[] copy(final FailedServletContextDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.FailedServletContextDTO[] dtos = new org.osgi.service.http.runtime.dto.FailedServletContextDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.FailedServletContextDTO();
                dtos[i].attributes = orig[i].attributes;
                dtos[i].contextPath = orig[i].contextPath;
                dtos[i].errorPageDTOs = copy(orig[i].errorPageDTOs);
                dtos[i].failureReason = orig[i].failureReason;
                dtos[i].filterDTOs = copy(orig[i].filterDTOs);
                dtos[i].initParams = orig[i].initParams;
                dtos[i].listenerDTOs = copy(orig[i].listenerDTOs);
                dtos[i].name = orig[i].name;
                dtos[i].resourceDTOs = copy(orig[i].resourceDTOs);
                dtos[i].serviceId = orig[i].serviceId;
                dtos[i].servletDTOs = copy(orig[i].servletDTOs);
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.FailedServletDTO[] copy(final FailedServletDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.FailedServletDTO[] dtos = new org.osgi.service.http.runtime.dto.FailedServletDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.FailedServletDTO();
                dtos[i].asyncSupported = orig[i].asyncSupported;
                dtos[i].failureReason = orig[i].failureReason;
                dtos[i].initParams = orig[i].initParams;
                dtos[i].multipartEnabled = orig[i].multipartEnabled;
                dtos[i].multipartFileSizeThreshold = orig[i].multipartFileSizeThreshold;
                dtos[i].multipartLocation = orig[i].multipartLocation;
                dtos[i].multipartMaxFileSize = orig[i].multipartMaxFileSize;
                dtos[i].multipartMaxRequestSize = orig[i].multipartMaxRequestSize;
                dtos[i].name = orig[i].name;
                dtos[i].patterns = orig[i].patterns;
                dtos[i].serviceId = orig[i].serviceId;
                dtos[i].servletContextId = orig[i].servletContextId;
                dtos[i].servletInfo = orig[i].servletInfo;
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.ErrorPageDTO[] copy(final ErrorPageDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.ErrorPageDTO[] dtos = new org.osgi.service.http.runtime.dto.ErrorPageDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.ErrorPageDTO();
                dtos[i].asyncSupported = orig[i].asyncSupported;
                dtos[i].errorCodes = orig[i].errorCodes;
                dtos[i].exceptions = orig[i].exceptions;
                dtos[i].initParams = orig[i].initParams;
                dtos[i].name = orig[i].name;
                dtos[i].serviceId = orig[i].serviceId;
                dtos[i].servletContextId = orig[i].servletContextId;
                dtos[i].servletInfo = orig[i].servletInfo;
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.FilterDTO[] copy(final FilterDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.FilterDTO[] dtos = new org.osgi.service.http.runtime.dto.FilterDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.FilterDTO();
                dtos[i].asyncSupported = orig[i].asyncSupported;
                dtos[i].dispatcher = orig[i].dispatcher;
                dtos[i].initParams = orig[i].initParams;
                dtos[i].name = orig[i].name;
                dtos[i].patterns = orig[i].patterns;
                dtos[i].regexs = orig[i].regexs;
                dtos[i].serviceId = orig[i].serviceId;
                dtos[i].servletContextId = orig[i].servletContextId;
                dtos[i].servletNames = orig[i].servletNames;
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.ListenerDTO[] copy(final ListenerDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.ListenerDTO[] dtos = new org.osgi.service.http.runtime.dto.ListenerDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.ListenerDTO();
                dtos[i].serviceId = orig[i].serviceId;
                dtos[i].servletContextId = orig[i].servletContextId;
                dtos[i].types = orig[i].types;
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.PreprocessorDTO[] copy(final PreprocessorDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.PreprocessorDTO[] dtos = new org.osgi.service.http.runtime.dto.PreprocessorDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.PreprocessorDTO();
                dtos[i].initParams = orig[i].initParams;
                dtos[i].serviceId = orig[i].serviceId;
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.ResourceDTO[] copy(final ResourceDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.ResourceDTO[] dtos = new org.osgi.service.http.runtime.dto.ResourceDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = copy(orig[i]);
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.ResourceDTO copy(final ResourceDTO orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.ResourceDTO dto = new org.osgi.service.http.runtime.dto.ResourceDTO();
            dto.patterns = orig.patterns;
            dto.prefix = orig.prefix;
            dto.serviceId = orig.serviceId;
            dto.servletContextId = orig.servletContextId;
            return dto;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.ServletContextDTO[] copy(final ServletContextDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.ServletContextDTO[] dtos = new org.osgi.service.http.runtime.dto.ServletContextDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = new org.osgi.service.http.runtime.dto.ServletContextDTO();
                dtos[i].attributes = orig[i].attributes;
                dtos[i].contextPath = orig[i].contextPath;
                dtos[i].errorPageDTOs = copy(orig[i].errorPageDTOs);
                dtos[i].filterDTOs = copy(orig[i].filterDTOs);
                dtos[i].initParams = orig[i].initParams;
                dtos[i].listenerDTOs = copy(orig[i].listenerDTOs);
                dtos[i].name = orig[i].name;
                dtos[i].resourceDTOs = copy(orig[i].resourceDTOs);
                dtos[i].serviceId = orig[i].serviceId;
                dtos[i].servletDTOs = copy(orig[i].servletDTOs);
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.ServletDTO[] copy(final ServletDTO[] orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.ServletDTO[] dtos = new org.osgi.service.http.runtime.dto.ServletDTO[orig.length];
            for(int i=0;i<orig.length;i++) {
                dtos[i] = copy(orig[i]);
            }
            return dtos;
        }
        return null;
    }

    private org.osgi.service.http.runtime.dto.ServletDTO copy(final ServletDTO orig) {
        if ( orig != null ) {
            final org.osgi.service.http.runtime.dto.ServletDTO dto = new org.osgi.service.http.runtime.dto.ServletDTO();
            dto.asyncSupported = orig.asyncSupported;
            dto.initParams = orig.initParams;
            dto.multipartEnabled = orig.multipartEnabled;
            dto.multipartFileSizeThreshold = orig.multipartFileSizeThreshold;
            dto.multipartLocation = orig.multipartLocation;
            dto.multipartMaxFileSize = orig.multipartMaxFileSize;
            dto.multipartMaxRequestSize = orig.multipartMaxRequestSize;
            dto.name = orig.name;
            dto.patterns = orig.patterns;
            dto.serviceId = orig.serviceId;
            dto.servletContextId = orig.servletContextId;
            dto.servletInfo = orig.servletInfo;
            return dto;
        }
        return null;
    }
}

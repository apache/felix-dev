/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.felix.feature.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.osgi.service.feature.FeatureArtifact;
import org.osgi.service.feature.FeatureArtifactBuilder;
import org.osgi.service.feature.ID;

class ArtifactBuilderImpl implements FeatureArtifactBuilder {
    private final ID id;

    private final Map<String,Object> metadata = new LinkedHashMap<>();

    ArtifactBuilderImpl(ID id) {
        this.id = id;
    }

    @Override
    public FeatureArtifactBuilder addMetadata(String key, Object value) {
    	if (key == null)
    		throw new IllegalArgumentException("Metadata key cannot be null");

    	if (key.length() == 0)
    		throw new IllegalArgumentException("Key must not be empty");

    	if ("id".equalsIgnoreCase(key))
    		throw new IllegalArgumentException("Key cannot be 'id'");
    	
    	checkMetadataValue(value);
    	    	
        this.metadata.put(key, value);
        return this;
    }

	@Override
    public FeatureArtifactBuilder addMetadata(Map<String,Object> md) {
    	if (md.keySet().contains(null))
    		throw new IllegalArgumentException("Metadata key cannot be null");
    	
    	if (md.keySet().contains(""))
    		throw new IllegalArgumentException("Key must not be empty");

    	if (md.keySet().stream()
    		.map(String::toLowerCase)
    		.anyMatch(s -> "id".equals(s))) {
    		throw new IllegalArgumentException("Key cannot be 'id'");    		
    	}
    	
    	md.values().stream()
			.forEach(this::checkMetadataValue);

    	this.metadata.putAll(md);
        return this;
    }

    private void checkMetadataValue(Object value) {
    	if (value instanceof String) 
    		return;
    	
    	if (value instanceof Boolean)
    		return;
    	
    	if (value instanceof Number)
    		return;
    	
    	throw new IllegalArgumentException("Illegal metadata value: " + value);
	}

    @Override
    public FeatureArtifact build() {
        return new ArtifactImpl(id, metadata);
    }

    private static class ArtifactImpl implements FeatureArtifact {
        private final ID id;
        private final Map<String, Object> metadata;

        private ArtifactImpl(ID id, Map<String, Object> metadata) {
            this.id = id;
            this.metadata = Collections.unmodifiableMap(metadata);
        }

        @Override
        public ID getID() {
            return id;
        }
        
        @Override
        public Map<String, Object> getMetadata() {
            return metadata;
        }

        @Override
		public int hashCode() {
			return Objects.hash(id, metadata);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ArtifactImpl other = (ArtifactImpl) obj;
			return Objects.equals(id, other.id) && Objects.equals(metadata, other.metadata);
		}

		@Override
        public String toString() {
            return "ArtifactImpl [getID()=" + getID() + "]";
        }
    }
}

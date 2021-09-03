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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.osgi.service.feature.FeatureArtifact;
import org.osgi.service.feature.FeatureExtension;
import org.osgi.service.feature.FeatureExtension.Kind;
import org.osgi.service.feature.FeatureExtension.Type;
import org.osgi.service.feature.FeatureExtensionBuilder;

class ExtensionBuilderImpl implements FeatureExtensionBuilder {
    private final String name;
    private final Type type;
    private final Kind kind;

    private final List<String> content = new ArrayList<>();
    private final List<FeatureArtifact> artifacts = new ArrayList<>();

    ExtensionBuilderImpl(String name, Type type, Kind kind) {
        this.name = name;
        this.type = type;
        this.kind = kind;
    }

    @Override
    public FeatureExtensionBuilder addText(String text) {
        if (type != Type.TEXT)
            throw new IllegalStateException("Cannot add text to extension of type " + type);

        content.add(text);
        return this;
    }

    @Override
    public FeatureExtensionBuilder setJSON(String json) {
        if (type != Type.JSON)
            throw new IllegalStateException("Cannot add text to extension of type " + type);

        content.clear(); // Clear any previous value
        content.add(json);
        return this;
    }

    @Override
    public FeatureExtensionBuilder addArtifact(FeatureArtifact art) {
        if (type != Type.ARTIFACTS)
            throw new IllegalStateException("Cannot add artifacts to extension of type " + type);

        artifacts.add(art);
        return this;
    }
    
    @Override
    public FeatureExtension build() {
        return new ExtensionImpl(name, type, kind, content, artifacts);
    }

    private static class ExtensionImpl implements FeatureExtension {
        private final String name;
        private final Type type;
        private final Kind kind;
        private final List<String> content;
        private final List<FeatureArtifact> artifacts;

        private ExtensionImpl(String name, Type type, Kind kind, List<String> content, List<FeatureArtifact> artifacts) {
            this.name = name;
            this.type = type;
            this.kind = kind;
            this.content = Collections.unmodifiableList(content);
            this.artifacts = Collections.unmodifiableList(artifacts);
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public Kind getKind() {
            return kind;
        }

        public String getJSON() {
            if (type != Type.JSON)
                throw new IllegalStateException("Extension is not of type JSON " + type);

            if (content.isEmpty())
                return null;

            return content.get(0);
        }

        public List<String> getText() {
            if (type != Type.TEXT)
                throw new IllegalStateException("Extension is not of type Text " + type);

            return content;
        }

        public List<FeatureArtifact> getArtifacts() {
            if (type != Type.ARTIFACTS)
                throw new IllegalStateException("Extension is not of type Text " + type);

            return artifacts;
        }

        @Override
		public int hashCode() {
			return Objects.hash(artifacts, content, kind, name, type);
		}

        @Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ExtensionImpl other = (ExtensionImpl) obj;
			return Objects.equals(artifacts, other.artifacts) && Objects.equals(content, other.content)
					&& kind == other.kind && Objects.equals(name, other.name) && type == other.type;
		}

		@Override
        public String toString() {
            return "ExtensionImpl [name=" + name + ", type=" + type + "]";
        }
    }
}

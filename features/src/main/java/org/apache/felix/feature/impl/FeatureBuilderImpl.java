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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureBuilder;
import org.osgi.service.feature.FeatureBundle;
import org.osgi.service.feature.FeatureConfiguration;
import org.osgi.service.feature.FeatureExtension;
import org.osgi.service.feature.ID;

class FeatureBuilderImpl implements FeatureBuilder {
    private final ID id;

    private String name;
    private String description;
    private String docURL;
    private String license;
    private String scm;
    private String vendor;
    private boolean complete;

    private final List<FeatureBundle> bundles = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final Map<String,FeatureConfiguration> configurations = new LinkedHashMap<>();
    private final Map<String,FeatureExtension> extensions = new LinkedHashMap<>();
    private final Map<String,Object> variables = new LinkedHashMap<>();

    FeatureBuilderImpl(ID id) {
        this.id = id;
    }

    @Override
    public FeatureBuilder setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public FeatureBuilder setDocURL(String url) {
        this.docURL = url;
        return this;
    }

    @Override
    public FeatureBuilder setVendor(String vendor) {
        this.vendor = vendor;
        return this;
    }

    @Override
    public FeatureBuilder setLicense(String license) {
        this.license = license;
        return this;
    }

    @Override
    public FeatureBuilder setComplete(boolean complete) {
        this.complete = complete;
        return this;
    }

    @Override
    public FeatureBuilder setDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public FeatureBuilder setSCM(String scm) {
        this.scm = scm;
        return this;
    }

    @Override
    public FeatureBuilder addBundles(FeatureBundle ... bundles) {
        this.bundles.addAll(Arrays.asList(bundles));
        return this;
    }

    
    @Override
	public FeatureBuilder addCategories(String ...categories) {
    	this.categories.addAll(Arrays.asList(categories));
		return this;
	}

	@Override
    public FeatureBuilder addConfigurations(FeatureConfiguration ... configs) {
        for (FeatureConfiguration cfg : configs) {
            this.configurations.put(cfg.getPid(), cfg);
        }
        return this;
    }

    @Override
    public FeatureBuilder addExtensions(FeatureExtension ... extensions) {
        for (FeatureExtension ex : extensions) {
            this.extensions.put(ex.getName(), ex);
        }
        return this;
    }

    @Override
    public FeatureBuilder addVariable(String key, Object value) {
        this.variables.put(key, value);
        return this;
    }

    @Override
    public FeatureBuilder addVariables(Map<String,Object> variables) {
        this.variables.putAll(variables);
        return this;
    }

    @Override
    public Feature build() {
        return new FeatureImpl(id, name, description, docURL,
                license, scm, vendor, complete,
                bundles, categories, configurations, extensions, variables);
    }

    private static class FeatureImpl implements Feature {
        private final ID id;
        private final Optional<String> name;
        private final Optional<String> description;
        private final Optional<String> docURL;
        private final Optional<String> license;
        private final Optional<String> scm;
        private final Optional<String> vendor;
        private final boolean complete;

        private final List<FeatureBundle> bundles;
        private final List<String> categories;
        private final Map<String,FeatureConfiguration> configurations;
        private final Map<String,FeatureExtension> extensions;
        private final Map<String,Object> variables;

        private FeatureImpl(ID id, String aName, String desc, String docs, String lic, String sc, String vnd,
                boolean comp, List<FeatureBundle> bs, List<String> cats, Map<String,FeatureConfiguration> cs,
                Map<String,FeatureExtension> es, Map<String,Object> vars) {
            this.id = id;
            name = Optional.ofNullable(aName);
            description = Optional.ofNullable(desc);
            docURL = Optional.ofNullable(docs);
            license = Optional.ofNullable(lic);
            scm = Optional.ofNullable(sc);
            vendor = Optional.ofNullable(vnd);
            complete = comp;

            bundles = Collections.unmodifiableList(bs);
            categories = Collections.unmodifiableList(cats);
            configurations = Collections.unmodifiableMap(cs);
            extensions = Collections.unmodifiableMap(es);
            variables = Collections.unmodifiableMap(vars);
        }

        @Override
        public ID getID() {
            return id;
        }
        
        @Override
        public Optional<String> getName() {
            return name;
        }

        @Override
        public Optional<String> getDescription() {
            return description;
        }

        @Override
        public Optional<String> getVendor() {
            return vendor;
        }

        @Override
        public Optional<String> getLicense() {
            return license;
        }

        @Override
        public Optional<String> getDocURL() {
            return docURL;
        }

        @Override
        public Optional<String> getSCM() {
            return scm;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public List<FeatureBundle> getBundles() {
            return bundles;
        }

        @Override
        public List<String> getCategories() {
            return categories;
        }

        @Override
        public Map<String,FeatureConfiguration> getConfigurations() {
            return configurations;
        }

        @Override
        public Map<String,FeatureExtension> getExtensions() {
            return extensions;
        }

        @Override
        public Map<String,Object> getVariables() {
            return variables;
        }

        @Override
		public int hashCode() {
			return Objects.hash(bundles, categories, complete, configurations, description, docURL,
					extensions, id, license, name, scm, variables, vendor);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			FeatureImpl other = (FeatureImpl) obj;
			return Objects.equals(bundles, other.bundles) && Objects.equals(categories, other.categories)
					&& complete == other.complete && Objects.equals(configurations, other.configurations)
					&& Objects.equals(description, other.description)
					&& Objects.equals(docURL, other.docURL) && Objects.equals(extensions, other.extensions)
					&& Objects.equals(id, other.id) && Objects.equals(license, other.license)
					&& Objects.equals(name, other.name) && Objects.equals(scm, other.scm)
					&& Objects.equals(variables, other.variables) && Objects.equals(vendor, other.vendor);
		}

		@Override
        public String toString() {
            return "FeatureImpl [getID()=" + getID() + "]";
        }
    }
}

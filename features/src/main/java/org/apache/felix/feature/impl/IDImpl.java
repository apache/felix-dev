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

import java.util.Objects;
import java.util.Optional;

import org.osgi.service.feature.ID;

public class IDImpl implements ID {
    private final String groupId;
    private final String artifactId;
    private final String version; // The Artifact Version may not follow OSGi version rules
    private final String type;
    private final String classifier;

    /**
	 * Construct an ID from a Maven ID. Maven IDs have the following syntax:
	 * <p>
	 * {@code group-id ':' artifact-id [ ':' [type] [ ':' classifier ] ] ':' version}
	 *
	 * @param mavenID
	 * @return The ID
	 * @throws IllegalArgumentException if the mavenID does not match the Syntax
	 */
	public static IDImpl fromMavenID(String mavenID)
			throws IllegalArgumentException {
        String[] parts = mavenID.split(":");

        if (parts.length < 3 || parts.length > 5)
            throw new IllegalArgumentException("Not a valid maven ID" + mavenID);

        String gid = parts[0];
        String aid = parts[1];
		String ver = null;
		String t = null;
		String c = null;

		if (parts.length == 3) {
			ver = parts[2];
		} else if (parts.length == 4) {
			t = parts[2];
			ver = parts[3];
		} else {
			t = parts[2];
			c = parts[3];
			ver = parts[4];
		}
        return new IDImpl(gid, aid, ver, t, c);
    }

    /**
	 * Construct an ID
	 * 
	 * @param groupId The group ID.
	 * @param artifactId The artifact ID.
	 * @param version The version.
	 * @param type The type identifier.
	 * @param classifier The classifier.
	 * @throws NullPointerException if one of the parameters (groupId,
	 *             artifactId, version) is null.
	 * @throws IllegalArgumentException if one of the parameters is empty or
	 *             contains an colon `:` or if a classifier is used without a
	 *             type.
	 */
	public IDImpl(String groupId, String artifactId, String version, String type,
			String classifier)
			throws NullPointerException, IllegalArgumentException {

		Objects.requireNonNull(groupId, "groupId");
		Objects.requireNonNull(artifactId, "artifact");
		Objects.requireNonNull(version, "version");

		if (groupId.isEmpty()) {
			throw new IllegalArgumentException("groupId must not be empty");
		}
		if (artifactId.isEmpty()) {
			throw new IllegalArgumentException("artifactId must not be empty");
		}
		if (version.isEmpty()) {
			throw new IllegalArgumentException("version must not be empty");
		}

		if (type != null && type.isEmpty()) {
			throw new IllegalArgumentException("type must not be empty");
		}

		if (classifier != null && classifier.isEmpty()) {
			throw new IllegalArgumentException("classifier must not be empty");
		}

		if (groupId.contains(":")) {
			throw new IllegalArgumentException(
					"groupId must not contain a colon `:`");
		}
		if (artifactId.contains(":")) {
			throw new IllegalArgumentException(
					"artifactId must not contain a colon `:`");
		}
		if (version.contains(":")) {
			throw new IllegalArgumentException(
					"version must not contain a colon `:`");
		}
		if (type != null && type.contains(":")) {
			throw new IllegalArgumentException(
					"type must not contain a colon `:`");
		}
		if (classifier != null && classifier.contains(":")) {
			throw new IllegalArgumentException(
					"classifier must not contain a colon `:`");
		}
		if (type == null && classifier != null) {
			throw new IllegalArgumentException(
					"type must not be `null` if a classifier is set");
		}
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.type = type;
		this.classifier = classifier;
    }

    /**
     * Get the group ID.
     * @return The group ID.
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Get the artifact ID.
     * @return The artifact ID.
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Get the version.
     * @return The version.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Get the type identifier.
     * @return The type identifier.
     */
    public Optional<String> getType() {
        return Optional.ofNullable(type);
    }

    /**
     * Get the classifier.
     * @return The classifier.
     */
    public Optional<String> getClassifier() {
        return Optional.ofNullable(classifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, classifier, groupId, type, version);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof IDImpl))
            return false;
        IDImpl other = (IDImpl) obj;
        return Objects.equals(artifactId, other.artifactId) && Objects.equals(classifier, other.classifier)
                && Objects.equals(groupId, other.groupId) && Objects.equals(type, other.type)
                && Objects.equals(version, other.version);
    }

	/**
	 * Returns the the mavenID. Maven IDs have the following syntax:
	 * <p>
	 * {@code group-id ':' artifact-id [ ':' [type] [ ':' classifier ] ] ':' version}
	 * 
	 * @return the mavenID.
	 */
    @Override
    public String toString() {
		StringBuilder sb = new StringBuilder(groupId).append(":")
				.append(artifactId);

		if (type != null) {
			sb = sb.append(":").append(type);
			if (classifier != null) {
				sb = sb.append(":").append(classifier);
			}
		}
		return sb.append(":").append(version).toString();
    }
}

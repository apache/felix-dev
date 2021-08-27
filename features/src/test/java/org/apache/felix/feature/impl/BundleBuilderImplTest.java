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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.osgi.service.feature.FeatureBundle;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.feature.ID;

public class BundleBuilderImplTest {
	private FeatureService featureService;
	
	@Before
	public void setUp() {
		featureService = new FeatureServiceImpl();
	}
	
	@Test
	public void testMetadata() {
		ID id = featureService.getID("g", "a", "v");
		BundleBuilderImpl bb = new BundleBuilderImpl(id);
		
		bb.addMetadata("foo", "bar");
		
		try {
			bb.addMetadata("blah", null);
			fail();
		} catch (IllegalArgumentException iae) {
			// good
		}

		try {
			bb.addMetadata(null, "blah");
			fail();
		} catch (IllegalArgumentException iae) {
			// good
		}

		try {
			bb.addMetadata("id", "blah");
			fail();
		} catch (IllegalArgumentException iae) {
			// good
		}
		
		FeatureBundle bundle = bb.build();
		assertEquals("g:a:v", bundle.getID().toString());
		assertEquals(Collections.singletonMap("foo", "bar"),
				bundle.getMetadata());
	}
	
	@Test
	public void testMetadataMap() {
		ID id = featureService.getID("g", "a", "v", "t", "c");
		BundleBuilderImpl bb = new BundleBuilderImpl(id);
		
		bb.addMetadata(Collections.singletonMap("foo", "bar"));
		
		try {
			bb.addMetadata(Collections.singletonMap("blah", null));
			fail();
		} catch (IllegalArgumentException iae) {
			// good
		}

		try {
			bb.addMetadata(Collections.singletonMap(null, "blah"));
			fail();
		} catch (IllegalArgumentException iae) {
			// good
		}

		try {
			bb.addMetadata(Collections.singletonMap("id", "blah"));
			fail();
		} catch (IllegalArgumentException iae) {
			// good
		}
		
		FeatureBundle bundle = bb.build();
		assertEquals("g:a:t:c:v", bundle.getID().toString());
		assertEquals(Collections.singletonMap("foo", "bar"),
				bundle.getMetadata());
	}	
}

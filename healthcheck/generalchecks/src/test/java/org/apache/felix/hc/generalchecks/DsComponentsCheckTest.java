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
package org.apache.felix.hc.generalchecks;

import static org.junit.Assert.assertEquals;

import org.apache.felix.hc.api.Result;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.service.component.runtime.ServiceComponentRuntime;

public class DsComponentsCheckTest {

	@Test
	public void testCaching() {
		DsComponentsCheck check = Mockito.spy(new DsComponentsCheck());
		
		final Result resultOk = new Result (Result.Status.OK,"ok");
		final Result resultTemporarilyUnavailable = new Result (Result.Status.TEMPORARILY_UNAVAILABLE,
				"temporarily unavailable");
				
		Mockito.when(check.executeInternal())
			.thenReturn(resultOk)
			.thenReturn(resultTemporarilyUnavailable)
			.thenReturn(resultTemporarilyUnavailable)
			.thenReturn(resultOk);
		
		assertEquals(resultOk, check.execute());
		check.updatedServiceComponentRuntime(null);
		assertEquals(resultTemporarilyUnavailable, check.execute());
		assertEquals(resultTemporarilyUnavailable, check.execute());
		check.updatedServiceComponentRuntime(null);
		assertEquals(resultOk, check.execute());	
	}
	
}

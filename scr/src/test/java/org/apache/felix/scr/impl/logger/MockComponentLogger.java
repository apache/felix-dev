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
package org.apache.felix.scr.impl.logger;

public class MockComponentLogger implements ComponentLogger
{

	@Override
	public void log(Level level, String message, Throwable ex) {
	}

	@Override
	public void log(Level level, String message, Throwable ex, Object... args) {
	}

	@Override
	public boolean isLogEnabled(Level level) {
		return true;
	}

	@Override
	public void setComponentId(long m_componentId) {
	}
}
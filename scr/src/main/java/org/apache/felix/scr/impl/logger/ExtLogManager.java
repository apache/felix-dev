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

import org.apache.felix.scr.impl.manager.ScrConfiguration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * Implements an extension to the SCR log manager that uses logger names to
 * create a hierarchy of loggers. All messages will be logged via the SCR
 * logger's bundle unlike the classic scr log manager that used the bundle's logger.
 * 
 * <ul>
 * <li>An ScrLogger will log with the name {@value #SCR_LOGGER_NAME}
 * <li>A BundleLogger will log with the name {@value #SCR_LOGGER_PREFIX} + the
 * bundle symbolic name
 * <li>A ComponentLogger will log with the name {@value #SCR_LOGGER_PREFIX} +
 * the bundle symbolic name + "." + component name
 * </ul>
 */
class ExtLogManager extends ScrLogManager {
	public static String	SCR_LOGGER_NAME		= "org.apache.felix.scr.impl";
	public static String	SCR_LOGGER_PREFIX	= "org.apache.felix.scr.";
	private final Bundle	bundle;

	ExtLogManager(BundleContext context, ScrConfiguration config) {
		super(context, config);
		this.bundle = context.getBundle();
	}

	@Override
	public ScrLogger scr() {
		return getLogger(bundle, SCR_LOGGER_NAME, ScrLoggerFacade.class);
	}

	@Override
	public BundleLogger bundle(Bundle bundle) {
		return getLogger(bundle, SCR_LOGGER_PREFIX.concat(bundle.getSymbolicName()), ScrLoggerFacade.class);
	}

	@Override
	public ComponentLogger component(Bundle bundle, String implementationClass, String componentName) {

		assert bundle != null;
		assert bundle.getSymbolicName() != null : "scr requires recent bundles";
		assert implementationClass != null;
		assert componentName != null;

		String loggerName = SCR_LOGGER_PREFIX.concat(bundle.getSymbolicName()).concat(".").concat(componentName);
		ScrLoggerFacade logger = getLogger(bundle, loggerName, ScrLoggerFacade.class);
		logger.setPrefix("["+componentName+"]");
		return logger;
	}

	String componentPrefix(ScrLoggerFacade slf, long id) {
		assert slf.prefix != null; 
		if ( slf.prefix.indexOf(')')<0)
			return slf.prefix.replace("]", "(" + id + ")]");
		else
			return slf.prefix;
	}
}

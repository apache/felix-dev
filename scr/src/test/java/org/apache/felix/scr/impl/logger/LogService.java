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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerConsumer;
import org.osgi.service.log.LoggerFactory;

import aQute.lib.collections.MultiMap;

public class LogService implements ServiceFactory<LoggerFactory> {
	public BundleContext						context;
	public final List<LogEntry>					entries			= new ArrayList<>();
	public final Map<String, LogLevel>			levels			= new HashMap<>();
	public LogLevel								defaultLogLevel	= LogLevel.DEBUG;
	public List<LoggerFactory>					factories		= new ArrayList<>();
	public MultiMap<Bundle, Logger>				loggers			= new MultiMap<>();

	public ServiceRegistration<LoggerFactory>	registration;

	public static class LogEntry {

		@Override
		public String toString() {
			return "LogEntry [level=" + level + ", format=" + format + ", args=" + Arrays.toString(args) + ", bundle="
					+ bundle + ", loggername=" + loggername + "]";
		}

		public LogLevel	level;
		public String	format;
		public Object[]	args;
		public Bundle	bundle;
		public String	loggername;

		public LogEntry(Bundle bundle, String loggername, LogLevel level, String format, Object[] args) {
			this.bundle = bundle;
			this.loggername = loggername;
			this.level = level;
			this.format = format;
			this.args = args;
		}

	}

	public void register() {
		registration = context.registerService(LoggerFactory.class, this, null);
	}

	public void unregister() {
		assert registration != null;
		registration.unregister();
		registration = null;
	}

	public LogService(BundleContext context) {
		this.context = context;
	}

	@Override
	public LoggerFactory getService(final Bundle bundle, ServiceRegistration<LoggerFactory> registration) {
		LoggerFactory factory = new LoggerFactory() {

			@Override
			public Logger getLogger(String name) {
				return getLogger(bundle, name, Logger.class);
			}

			@Override
			public Logger getLogger(Class<?> clazz) {
				return getLogger(bundle, clazz.getName(), Logger.class);
			}

			@Override
			public <L extends Logger> L getLogger(String name, Class<L> loggerType) {
				return getLogger(bundle, name, loggerType);
			}

			@Override
			public <L extends Logger> L getLogger(Class<?> clazz, Class<L> loggerType) {
				return getLogger(bundle, clazz.getName(), loggerType);
			}

			@Override
			public <L extends Logger> L getLogger(final Bundle bundle, final String name, Class<L> loggerType) {
				Logger logger = new Logger() {

					@Override
					public String getName() {
						return name;
					}

					@Override
					public boolean isTraceEnabled() {
						return getLevel().implies(LogLevel.TRACE);
					}

					@Override
					public void trace(String message) {
						log(LogLevel.TRACE, message);
					}

					@Override
					public void trace(String format, Object arg) {
						log(LogLevel.TRACE, format, arg);
					}

					@Override
					public void trace(String format, Object arg1, Object arg2) {
						log(LogLevel.TRACE, format, arg1, arg2);
					}

					@Override
					public void trace(String format, Object... arguments) {
						log(LogLevel.TRACE, format, arguments);
					}

					@Override
					public <E extends Exception> void trace(LoggerConsumer<E> consumer) throws E {
						consumer.accept(this);
					}

					@Override
					public boolean isDebugEnabled() {
						return getLevel().implies(LogLevel.DEBUG);
					}

					@Override
					public void debug(String message) {
						log(LogLevel.DEBUG, message);
					}

					@Override
					public void debug(String format, Object arg) {
						log(LogLevel.DEBUG, format, arg);
					}

					@Override
					public void debug(String format, Object arg1, Object arg2) {
						log(LogLevel.DEBUG, format, arg1, arg2);
					}

					@Override
					public void debug(String format, Object... arguments) {
						log(LogLevel.DEBUG, format, arguments);
					}

					@Override
					public <E extends Exception> void debug(LoggerConsumer<E> consumer) throws E {
						consumer.accept(this);
					}

					@Override
					public boolean isInfoEnabled() {
						return getLevel().implies(LogLevel.INFO);
					}

					@Override
					public void info(String message) {
						log(LogLevel.INFO, message);
					}

					@Override
					public void info(String format, Object arg) {
						log(LogLevel.INFO, format, arg);
					}

					@Override
					public void info(String format, Object arg1, Object arg2) {
						log(LogLevel.INFO, format, arg1, arg2);
					}

					@Override
					public void info(String format, Object... arguments) {
						log(LogLevel.INFO, format, arguments);
					}

					@Override
					public <E extends Exception> void info(LoggerConsumer<E> consumer) throws E {
						consumer.accept(this);
					}

					@Override
					public boolean isWarnEnabled() {
						return getLevel().implies(LogLevel.WARN);
					}

					@Override
					public void warn(String message) {
						log(LogLevel.WARN, message);
					}

					@Override
					public void warn(String format, Object arg) {
						log(LogLevel.WARN, format, arg);
					}

					@Override
					public void warn(String format, Object arg1, Object arg2) {
						log(LogLevel.WARN, format, arg1, arg2);
					}

					@Override
					public void warn(String format, Object... arguments) {
						log(LogLevel.WARN, format, arguments);
					}

					@Override
					public <E extends Exception> void warn(LoggerConsumer<E> consumer) throws E {
						consumer.accept(this);
					}

					@Override
					public boolean isErrorEnabled() {
						return getLevel().implies(LogLevel.ERROR);
					}

					@Override
					public void error(String message) {
						log(LogLevel.ERROR, message);
					}

					@Override
					public void error(String format, Object arg) {
						log(LogLevel.ERROR, format, arg);
					}

					@Override
					public void error(String format, Object arg1, Object arg2) {
						log(LogLevel.ERROR, format, arg1, arg2);
					}

					@Override
					public void error(String format, Object... arguments) {
						log(LogLevel.ERROR, format, arguments);
					}

					@Override
					public <E extends Exception> void error(LoggerConsumer<E> consumer) throws E {
						consumer.accept(this);
					}

					@Override
					public void audit(String message) {
						log(LogLevel.AUDIT, message);
					}

					@Override
					public void audit(String format, Object arg) {
						log(LogLevel.AUDIT, format, arg);
					}

					@Override
					public void audit(String format, Object arg1, Object arg2) {
						log(LogLevel.AUDIT, format, arg1, arg2);
					}

					@Override
					public void audit(String format, Object... arguments) {
						log(LogLevel.AUDIT, format, arguments);
					}

					public void log(LogLevel level, String format, Object... args) {
						if (getLevel().implies(level)) {
							entries.add(new LogEntry(bundle, name, level, format, args));
						}
					}

					public LogLevel getLevel() {
						String ancestor = name;
						while (true) {
							LogLevel logLevel = levels.get(ancestor);
							if (logLevel != null) {
								return logLevel;
							}

							int n = ancestor.lastIndexOf('.');
							if (n < 0) {
								LogLevel logLevel2 = levels.get(Logger.ROOT_LOGGER_NAME);
								if ( logLevel2 != null)
									return logLevel2;
								
								return defaultLogLevel;
							}

							ancestor = ancestor.substring(0, n);
						}
					}

				};
				loggers.add(bundle, logger);
				return loggerType.cast(logger);
			}
		};
		factories.add(factory);
		return factory;
	}

	@Override
	public void ungetService(Bundle bundle, ServiceRegistration<LoggerFactory> registration, LoggerFactory service) {
		factories.add(service);
	}

	public void clear() {
		loggers.clear();
		entries.clear();
		factories.clear();
		levels.clear();
	}
}

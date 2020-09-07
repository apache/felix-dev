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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.felix.scr.impl.logger.InternalLogger.Level;
import org.apache.felix.scr.impl.logger.LogManager.LoggerFacade;
import org.apache.felix.scr.impl.logger.LogService.LogEntry;
import org.apache.felix.scr.impl.manager.ScrConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.Logger;

import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.JarResource;
import aQute.lib.io.IO;

public class LoggerTest {

	private Framework	framework;
	private Bundle		scr;
	private Bundle		component;
	private Bundle		log;
	private File		tmp	= IO.getFile("target/tmp/loggertest");

	@Before
	public void setup() throws Exception {
		Map<String, String> configuration = new HashMap<>();
		configuration.put(Constants.FRAMEWORK_STORAGE, tmp.getAbsolutePath());
		configuration.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
		framework = ServiceLoader.load(org.osgi.framework.launch.FrameworkFactory.class).iterator().next()
				.newFramework(configuration);
		framework.init();
		framework.start();

		scr = framework.getBundleContext().installBundle("scr", makeBundle("scr").openInputStream());
		component = framework.getBundleContext().installBundle("component",
				makeBundle("component").openInputStream());
		log = framework.getBundleContext().installBundle("component", makeBundle("log").openInputStream());
		scr.start();
		component.start();
		log.start();
	}

	@After
	public void after() throws Exception {
		framework.stop();
		framework.waitForStop(100000);
	}

	static class Buf extends FilterOutputStream {
		StringWriter		buffer	= new StringWriter();
		final PrintStream	stream;

		public Buf(PrintStream out) {
			super(out);
			this.stream = out;
		}

		@Override
		public void write(int b) throws IOException {
			stream.write(b);
			buffer.write(b);
		}

		PrintStream out() {
			return new PrintStream(this);
		}

		public void reset() {
			buffer = new StringWriter();
		}
	}

	@Test
	public void formatTest() {
		LogService l = new LogService(log.getBundleContext());
		l.register();
		ScrConfiguration config = mock(ScrConfiguration.class);
		when(config.getLogLevel()).thenReturn(Level.DEBUG);
		ExtLogManager elm = new ExtLogManager(scr.getBundleContext(), config);
        elm.open();

		ComponentLogger clog = elm.component(component, "i.c", "c");

		clog.log(Level.ERROR, "error {0} {1} {2}", null, "a", 1, scr);
		LogEntry le = l.entries.get(0);
		assertThat(le.format).isEqualTo("[c] error a 1 bundle scr:0.0.0 (1)");
	}

	@Test
	public void testStandardOutput() throws IOException {
		ScrConfiguration config = mock(ScrConfiguration.class);
		when(config.getLogLevel()).thenReturn(Level.DEBUG);
		ExtLogManager elm = new ExtLogManager(scr.getBundleContext(), config);
        elm.open();
		try (Buf out = new Buf(System.out);
				Buf err = new Buf(System.err);) {
			try {
				System.setOut(out.out());
				System.setErr(err.out());
				ScrLogger scr = elm.scr();
				{
					scr.log(Level.AUDIT, "audit ", null);
					scr.log(Level.AUDIT, "audit", new Exception("FOOBAR"));
					scr.log(Level.TRACE, "audit", new Exception("FOOBAR"));

					String logged = (Level.AUDIT.err() ? err : out).buffer.toString();
					assertThat(logged).startsWith("AUDIT : audit").contains("FOOBAR");
				}

				{
					int n = 0;
					reset(config);

					for (Level configLevel : Level.values()) {

						for (Level requestedLevel : Level.values()) {
							out.reset();
							err.reset();
							when(config.getLogLevel()).thenReturn(configLevel);

							String message = configLevel + ":" + requestedLevel;
							scr.log(requestedLevel, message, null);
							verify(config, times(++n)).getLogLevel();

							String logged = (requestedLevel.err() ? err : out).buffer.toString();

							if (configLevel.implies(requestedLevel)) {
								assertThat(logged).contains(message);
							} else {
								assertThat(logged).isEmpty();
							}
						}
					}
				}
			} finally {
				System.setOut(out.stream);
				System.setErr(err.stream);
			}
		}
	}

	@Test
	public void testSelectionOfExtensionManager() {
		LogService l = new LogService(log.getBundleContext());
		l.register();

		ScrConfiguration config = mock(ScrConfiguration.class);
		when(config.getLogLevel()).thenReturn(Level.DEBUG);
		when(config.isLogExtension()).thenReturn(true);
		ScrLogger logger = ScrLogManager.scr(scr.getBundleContext(), config);
		BundleLogger bundle = logger.bundle(component);
		bundle.log(Level.ERROR, "Ext", null);
		assertThat(l.entries).hasSize(1);
		LogEntry le = l.entries.get(0);
        assertThat(le.bundle).isEqualTo(component);
	}

	@Test
	public void testSelectionOfBaseManager() {
		LogService l = new LogService(log.getBundleContext());
		l.register();

		ScrConfiguration config = mock(ScrConfiguration.class);
		when(config.isLogExtension()).thenReturn(false);
		when(config.getLogLevel()).thenReturn(Level.DEBUG);

		ScrLogger logger = ScrLogManager.scr(scr.getBundleContext(), config);
		BundleLogger bundle = logger.bundle(component);
		bundle.log(Level.ERROR, "Ext", null);
		assertThat(l.entries).hasSize(1);
		LogEntry le = l.entries.get(0);
		assertThat(le.bundle).isEqualTo(component);
	}

	@Test
	public void testExtensionLogLevelNotLoggingWhenRootSetToInfoAndLevelIsDebug() {
		ScrConfiguration config = mock(ScrConfiguration.class);
		when(config.isLogExtension()).thenReturn(true);
		when(config.getLogLevel()).thenReturn(Level.DEBUG);

		LogService l = new LogService(log.getBundleContext());
		l.register();
		l.levels.put(Logger.ROOT_LOGGER_NAME, LogLevel.INFO);
		l.defaultLogLevel = LogLevel.TRACE;

		ScrLogger lscr = ScrLogManager.scr(scr.getBundleContext(), config);

		assertThat(lscr.isLogEnabled(Level.DEBUG)).isFalse();
		assertThat(lscr.isLogEnabled(Level.INFO)).isTrue();

		lscr.log(Level.DEBUG, "I should not be reported", null);

		assertThat(l.entries).isEmpty();
	}

	@Test
	public void testExtensionLogLevelNotLoggingWhenPartialNameSetToInfoAndLevelIsDebug() {
		ScrConfiguration config = mock(ScrConfiguration.class);
		when(config.isLogExtension()).thenReturn(true);
		when(config.getLogLevel()).thenReturn(Level.DEBUG);

		LogService l = new LogService(log.getBundleContext());
		l.defaultLogLevel = LogLevel.TRACE;
		l.register();
		l.levels.put("org.apache.felix.scr", LogLevel.INFO);

		ScrLogger lscr = ScrLogManager.scr(scr.getBundleContext(), config);

		assertThat(lscr.isLogEnabled(Level.DEBUG)).isFalse();
		assertThat(lscr.isLogEnabled(Level.INFO)).isTrue();

		lscr.log(Level.DEBUG, "I should not be reported", null);

		assertThat(l.entries).isEmpty();
	}

	@Test
	public void testExtensionLogManager() {
		ScrConfiguration config = mock(ScrConfiguration.class);
		when(config.isLogExtension()).thenReturn(true);
		when(config.getLogLevel()).thenReturn(Level.DEBUG);

		LogService l = new LogService(log.getBundleContext());
		l.register();

		ScrLogManager lm = new ExtLogManager(scr.getBundleContext(), config);
        lm.open();

		{
			l.entries.clear();
			ScrLogger scr = lm.scr();
			scr.log(Level.ERROR, "Scr", null);
			assertThat(l.entries).hasSize(1);
			LogEntry le = l.entries.get(0);
			assertThat(le.format).isEqualTo("Scr");
			assertThat(le.bundle).isEqualTo(this.scr);
			assertThat(le.loggername).isEqualTo(ExtLogManager.SCR_LOGGER_NAME);
		}

		{
			l.entries.clear();
			BundleLogger blog = lm.bundle(component);
			blog.log(Level.ERROR, "Bundle", null);
			assertThat(l.entries).hasSize(1);
			LogEntry le = l.entries.get(0);
			assertThat(le.format).isEqualTo("Bundle");
            assertThat(le.bundle).isEqualTo(component);
			assertThat(le.loggername).isEqualTo(ExtLogManager.SCR_LOGGER_PREFIX + "component");
		}

		{
			l.entries.clear();
			ComponentLogger clog = lm.component(component, "implementation.class", "name");
			clog.log(Level.ERROR, "Component", null);
			assertThat(l.entries).hasSize(1);
			LogEntry le = l.entries.get(0);
			assertThat(le.format).isEqualTo("[name] Component");
			assertThat(le.bundle).isEqualTo(component);
			assertThat(le.loggername).isEqualTo(ExtLogManager.SCR_LOGGER_PREFIX + "component.name");

			l.entries.clear();
			clog.setComponentId(100);
			clog.log(Level.ERROR, "Component", null);
			le = l.entries.get(0);
			assertThat(le.format).isEqualTo("[name(100)] Component");
		}

		{
			lm.scr().close();
		}
	}

	@Test
	public void testBackwardCompatibilityOutput() {
		ScrConfiguration config = mock(ScrConfiguration.class);
		when(config.isLogExtension()).thenReturn(false);
		when(config.getLogLevel()).thenReturn(Level.DEBUG);

		LogService l = new LogService(log.getBundleContext());
		l.register();

		ScrLogManager lm = new ScrLogManager(scr.getBundleContext(), config);
        lm.open();

		{
			l.entries.clear();
			ScrLogger scr = lm.scr();
			scr.log(Level.ERROR, "Scr", null);
			assertThat(l.entries).hasSize(1);
			LogEntry le = l.entries.get(0);
			assertThat(le.format).isEqualTo("bundle scr:0.0.0 (1) Scr");
			assertThat(le.bundle).isEqualTo(this.scr);
			assertThat(le.loggername).isEqualTo(Logger.ROOT_LOGGER_NAME);
		}

		{
			l.entries.clear();
			BundleLogger blog = lm.bundle(component);
			blog.log(Level.ERROR, "Bundle", null);
			assertThat(l.entries).hasSize(1);
			LogEntry le = l.entries.get(0);
			assertThat(le.format).isEqualTo("bundle component:0.0.0 (2) Bundle");
			assertThat(le.bundle).isEqualTo(component);
			assertThat(le.loggername).isEqualTo(Logger.ROOT_LOGGER_NAME);
		}

		{
			l.entries.clear();
			ComponentLogger clog = lm.component(component, "implementation.class", "name");
			clog.log(Level.ERROR, "Component", null);
			assertThat(l.entries).hasSize(1);
			LogEntry le = l.entries.get(0);
			assertThat(le.format).isEqualTo("bundle component:0.0.0 (2)[implementation.class] : Component");
			assertThat(le.bundle).isEqualTo(component);
			assertThat(le.loggername).isEqualTo("implementation.class");

			l.entries.clear();
			clog.setComponentId(100);
			clog.log(Level.ERROR, "Component", null);
			le = l.entries.get(0);
			assertThat(le.format).isEqualTo("bundle component:0.0.0 (2)[implementation.class(100)] : Component");
		}
	}

	@Test
	public void testLifeCycle() {

		LogManager lm = new LogManager(scr.getBundleContext());
        lm.open();
		LoggerFacade facade = lm.getLogger(scr, "lifecycle", LoggerFacade.class);
		assertThat(facade.logger).isNull();

		LogService l = new LogService(log.getBundleContext());
		l.register();
		assertThat(l.loggers.get(scr)).isNull();

        Logger logger = (Logger) facade.getLogger();

		assertThat(logger).isNotNull();
		assertThat(facade.logger).isEqualTo(logger);
		assertThat(l.loggers.get(scr)).hasSize(1);

		assertThat(logger.getName()).isEqualTo("lifecycle");

        Logger logger2 = (Logger) facade.getLogger();
		assertThat(logger2).isEqualTo(logger);
		assertThat(l.loggers.get(scr)).hasSize(1);

		l.unregister();
		l.register();

		assertThat(facade.logger).isNull();
        logger = (Logger) facade.getLogger();
		assertThat(l.loggers.get(scr)).hasSize(2);

		assertThat(facade.logger).isNotNull();
		lm.close();
		assertThat(facade.logger).isNull();
	}

	@Test
	public void testPrioritiesLogService() {

		LogManager lm = new LogManager(scr.getBundleContext());
        lm.open();
		LoggerFacade facade = lm.getLogger(scr, "lifecycle", LoggerFacade.class);
		assertThat(facade.logger).isNull();

		LogService la = new LogService(log.getBundleContext());
		la.register();
        Logger loggera = (Logger) facade.getLogger();
		assertThat(loggera).isNotNull();
		assertThat(facade.logger).isEqualTo(loggera);
		assertThat(la.loggers.get(scr)).hasSize(1);

		LogService higherRanking = new LogService(log.getBundleContext()).ranking(10).register();

		assertThat(facade.logger).isNull();

        Logger loggerb = (Logger) facade.getLogger();
		assertThat(loggerb).isNotNull();
		assertThat(higherRanking.loggers.get(scr)).hasSize(1);

		LogService lowerRanking = new LogService(log.getBundleContext()).ranking(-10).register();
		assertThat(facade.logger).isNotNull();
		assertThat(lowerRanking.loggers.get(scr)).isNull();
	}

	@Test
	public void testLifeCycleOfComponentBundle() throws BundleException, InterruptedException {

		ScrConfiguration config = mock(ScrConfiguration.class);

		LogService l = new LogService(log.getBundleContext());
		l.register();

		ScrLogManager lm = new ScrLogManager(scr.getBundleContext(), config);
        lm.open();
		lm.component(component, "implementation.class", "component");

		assertThat(lm.lock.domains).hasSize(1);
		component.stop();

		for (int i = 0; i < 100; i++) {
			if (lm.lock.domains.isEmpty())
				return;
			Thread.sleep(10);
		}
		lm.close();
		fail("domains not cleared within 1 sec");
	}

	@Test
	public void testLogLevels() {

		ScrConfiguration config = mock(ScrConfiguration.class);

		when(config.getLogLevel()).thenReturn(Level.DEBUG);
		when(config.isLogExtension()).thenReturn(false);

		LogService l = new LogService(log.getBundleContext());

		l.register();

		ScrLogManager lm = new ScrLogManager(scr.getBundleContext(), config);
        lm.open();
		ScrLogger facade = lm.scr();

		assert LogLevel.values().length == Level.values().length;
		for (int i = 0; i < LogLevel.values().length; i++) {
			assert LogLevel.values()[i].name().equals(Level.values()[i].name());
		}

		Exception ex = new Exception("exception");

		for (LogLevel logLevel : LogLevel.values()) {

			l.defaultLogLevel = logLevel;

			for (Level level : Level.values()) {

				if (logLevel.ordinal() >= level.ordinal())
					assertThat(facade.isLogEnabled(level)).isTrue();
				else
					assertThat(facade.isLogEnabled(level)).isFalse();

				facade.log(level, "level " + level, null);
				facade.log(level, "level " + level, ex);
			}
		}
		assertThat(l.entries).hasSize(42);
	}

	private JarResource makeBundle(String bsn) throws Exception {
		@SuppressWarnings("resource")
		Builder b = new Builder();
		b.setBundleSymbolicName(bsn);
		Jar jar = b.build();
		b.removeClose(jar);
		return new JarResource(jar);
	}

}

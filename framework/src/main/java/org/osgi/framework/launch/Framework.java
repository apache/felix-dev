/*
 * Copyright (c) OSGi Alliance (2008, 2020). All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.osgi.framework.launch;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

import org.osgi.annotation.versioning.ProviderType;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;

/**
 * A Framework instance. A Framework is also known as a System Bundle.
 * 
 * <p>
 * Framework instances are created using a {@link FrameworkFactory}. The methods
 * of this interface can be used to manage and control the created framework
 * instance.
 * 
 * @ThreadSafe
 * @author $Id: bf960bdc39d19a780694a8cab5a555b3e0dc0fde $
 */
@ProviderType
public interface Framework extends Bundle {

	/**
	 * Initialize this Framework.
	 * <p>
	 * This method performs the same function as calling
	 * {@link #init(FrameworkListener...)} with no framework listeners.
	 * 
	 * @throws BundleException If this Framework could not be initialized.
	 * @throws SecurityException If the Java Runtime Environment supports
	 *         permissions and the caller does not have the appropriate
	 *         {@code AdminPermission[this,EXECUTE]} or if there is a security
	 *         manager already installed and the
	 *         {@link Constants#FRAMEWORK_SECURITY} configuration property is
	 *         set.
	 * @see #init(FrameworkListener...)
	 */
	void init() throws BundleException;

	/**
	 * Initialize this Framework. After calling this method, this Framework
	 * must:
	 * <ul>
	 * <li>Have generated a new {@link Constants#FRAMEWORK_UUID framework UUID}.
	 * </li>
	 * <li>Be in the {@link #STARTING} state.</li>
	 * <li>Have a valid Bundle Context.</li>
	 * <li>Be at start level 0.</li>
	 * <li>Have event handling enabled.</li>
	 * <li>Have reified Bundle objects for all installed bundles.</li>
	 * <li>Have registered any framework services. For example,
	 * {@code ConditionalPermissionAdmin}.</li>
	 * <li>Be {@link #adapt(Class) adaptable} to the OSGi defined types to which
	 * a system bundle can be adapted.</li>
	 * <li>Have called the {@code start} method of the extension bundle
	 * activator for all resolved extension bundles.</li>
	 * </ul>
	 * 
	 * <p>
	 * This Framework will not actually be started until {@link #start() start}
	 * is called.
	 * 
	 * <p>
	 * This method does nothing if called when this Framework is in the
	 * {@link #STARTING}, {@link #ACTIVE} or {@link #STOPPING} states.
	 * 
	 * <p>
	 * All framework events fired by this method are also delivered to the
	 * specified FrameworkListeners in the order they are specified before
	 * returning from this method. After returning from this method the
	 * specified listeners are no longer notified of framework events.
	 * 
	 * @param listeners Zero or more listeners to be notified when framework
	 *        events occur while initializing the framework. The specified
	 *        listeners do not need to be otherwise registered with the
	 *        framework. If a specified listener is registered with the
	 *        framework, it will be notified twice for each framework event.
	 * @throws BundleException If this Framework could not be initialized.
	 * @throws SecurityException If the Java Runtime Environment supports
	 *         permissions and the caller does not have the appropriate
	 *         {@code AdminPermission[this,EXECUTE]} or if there is a security
	 *         manager already installed and the
	 *         {@link Constants#FRAMEWORK_SECURITY} configuration property is
	 *         set.
	 * @since 1.2
	 */
	void init(FrameworkListener... listeners) throws BundleException;

	/**
	 * Wait until this Framework has completely stopped. The {@code stop} and
	 * {@code update} methods on a Framework performs an asynchronous stop of
	 * the Framework. This method can be used to wait until the asynchronous
	 * stop of this Framework has completed. This method will only wait if
	 * called when this Framework is in the {@link #STARTING}, {@link #ACTIVE},
	 * or {@link #STOPPING} states. Otherwise it will return immediately.
	 * <p>
	 * A Framework Event is returned to indicate why this Framework has stopped.
	 * 
	 * @param timeout Maximum number of milliseconds to wait until this
	 *            Framework has completely stopped. A value of zero will wait
	 *            indefinitely.
	 * @return A Framework Event indicating the reason this method returned. The
	 *         following {@code FrameworkEvent} types may be returned by this
	 *         method.
	 *         <ul>
	 *         <li>{@link FrameworkEvent#STOPPED STOPPED} - This Framework has
	 *         been stopped.</li>
	 *         <li>{@link FrameworkEvent#STOPPED_UPDATE STOPPED_UPDATE} - This
	 *         Framework has been updated which has shutdown and will now
	 *         restart.</li>
	 *         <li>{@link FrameworkEvent#STOPPED_SYSTEM_REFRESHED
	 *         STOPPED_SYSTEM_REFRESHED} - The Framework has been stopped
	 *         because of a refresh operation on the system bundle. A new class
	 *         loader must be used to restart the Framework.</li>
	 *         <li>{@link FrameworkEvent#ERROR ERROR} - The Framework
	 *         encountered an error while shutting down or an error has occurred
	 *         which forced the framework to shutdown.</li>
	 *         <li>{@link FrameworkEvent#WAIT_TIMEDOUT WAIT_TIMEDOUT} - This
	 *         method has timed out and returned before this Framework has
	 *         stopped.</li>
	 *         </ul>
	 * @throws InterruptedException If another thread interrupted the current
	 *             thread before or while the current thread was waiting for
	 *             this Framework to completely stop. The <i>interrupted
	 *             status</i> of the current thread is cleared when this
	 *             exception is thrown.
	 * @throws IllegalArgumentException If the value of timeout is negative.
	 */
	FrameworkEvent waitForStop(long timeout) throws InterruptedException;

	/**
	 * Start this Framework.
	 * 
	 * <p>
	 * The following steps are taken to start this Framework:
	 * <ol>
	 * <li>If this Framework is not in the {@link #STARTING} state,
	 * {@link #init() initialize} this Framework.</li>
	 * <li>All installed bundles must be started in accordance with each
	 * bundle's persistent <i>autostart setting</i>. This means some bundles
	 * will not be started, some will be started with <i>eager activation</i>
	 * and some will be started with their <i>declared activation</i> policy.
	 * The start level of this Framework is moved to the start level specified
	 * by the {@link Constants#FRAMEWORK_BEGINNING_STARTLEVEL beginning start
	 * level} framework property, as described in the <i>Start Level
	 * Specification</i>. If this framework property is not specified, then the
	 * start level of this Framework is moved to start level one (1). Any
	 * exceptions that occur during bundle starting must be wrapped in a
	 * {@link BundleException} and then published as a framework event of type
	 * {@link FrameworkEvent#ERROR}</li>
	 * <li>This Framework's state is set to {@link #ACTIVE}.</li>
	 * <li>A framework event of type {@link FrameworkEvent#STARTED} is fired</li>
	 * </ol>
	 * 
	 * @throws BundleException If this Framework could not be started.
	 * @throws SecurityException If the caller does not have the appropriate
	 *         {@code AdminPermission[this,EXECUTE]}, and the Java Runtime
	 *         Environment supports permissions.
	 * @see "Start Level Specification"
	 */
	@Override
	void start() throws BundleException;

	/**
	 * Start this Framework.
	 * 
	 * <p>
	 * Calling this method is the same as calling {@link #start()}. There are no
	 * start options for the Framework.
	 * 
	 * @param options Ignored. There are no start options for the Framework.
	 * @throws BundleException If this Framework could not be started.
	 * @throws SecurityException If the caller does not have the appropriate
	 *         {@code AdminPermission[this,EXECUTE]}, and the Java Runtime
	 *         Environment supports permissions.
	 * @see #start()
	 */
	@Override
	void start(int options) throws BundleException;

	/**
	 * Stop this Framework.
	 * 
	 * <p>
	 * The method returns immediately to the caller after initiating the
	 * following steps to be taken on another thread.
	 * <ol>
	 * <li>This Framework's state is set to {@link #STOPPING}.</li>
	 * <li>All installed bundles must be stopped without changing each bundle's
	 * persistent <i>autostart setting</i>. The start level of this Framework is
	 * moved to start level zero (0), as described in the <i>Start Level
	 * Specification</i>. Any exceptions that occur during bundle stopping must
	 * be wrapped in a {@link BundleException} and then published as a framework
	 * event of type {@link FrameworkEvent#ERROR}</li>
	 * <li>Unregister all services registered by this Framework.</li>
	 * <li>Event handling is disabled.</li>
	 * <li>This Framework's state is set to {@link #RESOLVED}.</li>
	 * <li>All resources held by this Framework are released. This includes
	 * threads, bundle class loaders, open files, etc.</li>
	 * <li>Notify all threads that are waiting at {@link #waitForStop(long)
	 * waitForStop} that the stop operation has completed.</li>
	 * </ol>
	 * <p>
	 * After being stopped, this Framework may be discarded, initialized or
	 * started.
	 * 
	 * @throws BundleException If stopping this Framework could not be
	 *         initiated.
	 * @throws SecurityException If the caller does not have the appropriate
	 *         {@code AdminPermission[this,EXECUTE]}, and the Java Runtime
	 *         Environment supports permissions.
	 * @see "Start Level Specification"
	 */
	@Override
	void stop() throws BundleException;

	/**
	 * Stop this Framework.
	 * 
	 * <p>
	 * Calling this method is the same as calling {@link #stop()}. There are no
	 * stop options for the Framework.
	 * 
	 * @param options Ignored. There are no stop options for the Framework.
	 * @throws BundleException If stopping this Framework could not be
	 *         initiated.
	 * @throws SecurityException If the caller does not have the appropriate
	 *         {@code AdminPermission[this,EXECUTE]}, and the Java Runtime
	 *         Environment supports permissions.
	 * @see #stop()
	 */
	@Override
	void stop(int options) throws BundleException;

	/**
	 * The Framework cannot be uninstalled.
	 * 
	 * <p>
	 * This method always throws a BundleException.
	 * 
	 * @throws BundleException This Framework cannot be uninstalled.
	 * @throws SecurityException If the caller does not have the appropriate
	 *         {@code AdminPermission[this,LIFECYCLE]}, and the Java Runtime
	 *         Environment supports permissions.
	 */
	@Override
	void uninstall() throws BundleException;

	/**
	 * Stop and restart this Framework.
	 * 
	 * <p>
	 * The method returns immediately to the caller after initiating the
	 * following steps to be taken on another thread.
	 * <ol>
	 * <li>Perform the steps in the {@link #stop()} method to stop this
	 * Framework.</li>
	 * <li>Perform the steps in the {@link #start()} method to start this
	 * Framework.</li>
	 * </ol>
	 * 
	 * @throws BundleException If stopping and restarting this Framework could
	 *         not be initiated.
	 * @throws SecurityException If the caller does not have the appropriate
	 *         {@code AdminPermission[this,LIFECYCLE]}, and the Java Runtime
	 *         Environment supports permissions.
	 */
	@Override
	void update() throws BundleException;

	/**
	 * Stop and restart this Framework.
	 * 
	 * <p>
	 * Calling this method is the same as calling {@link #update()} except that
	 * any provided InputStream is immediately closed.
	 * 
	 * @param in Any provided InputStream is immediately closed before returning
	 *        from this method and otherwise ignored.
	 * @throws BundleException If stopping and restarting this Framework could
	 *         not be initiated.
	 * @throws SecurityException If the caller does not have the appropriate
	 *         {@code AdminPermission[this,LIFECYCLE]}, and the Java Runtime
	 *         Environment supports permissions.
	 */
	@Override
	void update(InputStream in) throws BundleException;

	/**
	 * Returns the Framework unique identifier. This Framework is assigned the
	 * unique identifier zero (0) since this Framework is also a System Bundle.
	 * 
	 * @return 0.
	 * @see Bundle#getBundleId()
	 */
	@Override
	long getBundleId();

	/**
	 * Returns the Framework location identifier. This Framework is assigned the
	 * unique location &quot;{@code System Bundle}&quot; since this Framework is
	 * also a System Bundle.
	 * 
	 * @return The string &quot;{@code System Bundle}&quot;.
	 * @throws SecurityException If the caller does not have the appropriate
	 *         {@code AdminPermission[this,METADATA]}, and the Java Runtime
	 *         Environment supports permissions.
	 * @see Bundle#getLocation()
	 * @see Constants#SYSTEM_BUNDLE_LOCATION
	 */
	@Override
	String getLocation();

	/**
	 * Returns the symbolic name of this Framework. The symbolic name is unique
	 * for the implementation of the framework. However, the symbolic name
	 * &quot;{@code system.bundle}&quot; must be recognized as an alias to the
	 * implementation-defined symbolic name since this Framework is also a
	 * System Bundle.
	 * 
	 * @return The symbolic name of this Framework.
	 * @see Bundle#getSymbolicName()
	 * @see Constants#SYSTEM_BUNDLE_SYMBOLICNAME
	 */
	@Override
	String getSymbolicName();

	/**
	 * Returns {@code null} as a framework implementation does not have a proper
	 * bundle from which to return entry paths.
	 * 
	 * @param path Ignored.
	 * @return {@code null} as a framework implementation does not have a proper
	 *         bundle from which to return entry paths.
	 */
	@Override
	Enumeration<String> getEntryPaths(String path);

	/**
	 * Returns {@code null} as a framework implementation does not have a proper
	 * bundle from which to return an entry.
	 * 
	 * @param path Ignored.
	 * @return {@code null} as a framework implementation does not have a proper
	 *         bundle from which to return an entry.
	 */
	@Override
	URL getEntry(String path);

	/**
	 * Returns the time when the set of bundles in this framework was last
	 * modified. The set of bundles is considered to be modified when a bundle
	 * is installed, updated or uninstalled.
	 * 
	 * <p>
	 * The time value is the number of milliseconds since January 1, 1970,
	 * 00:00:00 UTC.
	 * 
	 * @return The time when the set of bundles in this framework was last
	 *         modified.
	 */
	@Override
	long getLastModified();

	/**
	 * Returns {@code null} as a framework implementation does not have a proper
	 * bundle from which to return entries.
	 * 
	 * @param path Ignored.
	 * @param filePattern Ignored.
	 * @param recurse Ignored.
	 * @return {@code null} as a framework implementation does not have a proper
	 *         bundle from which to return entries.
	 */
	@Override
	Enumeration<URL> findEntries(String path, String filePattern, boolean recurse);

	/**
	 * Adapt this Framework to the specified type.
	 * 
	 * <p>
	 * Adapting this Framework to the specified type may require certain checks,
	 * including security checks, to succeed. If a check does not succeed, then
	 * this Framework cannot be adapted and {@code null} is returned. If this
	 * Framework is not {@link #init() initialized}, then {@code null} is
	 * returned if the specified type is one of the OSGi defined types to which
	 * a system bundle can be adapted.
	 * 
	 * @param <A> The type to which this Framework is to be adapted.
	 * @param type Class object for the type to which this Framework is to be
	 *        adapted.
	 * @return The object, of the specified type, to which this Framework has
	 *         been adapted or {@code null} if this Framework cannot be adapted
	 */
	@Override
	<A> A adapt(Class<A> type);
}

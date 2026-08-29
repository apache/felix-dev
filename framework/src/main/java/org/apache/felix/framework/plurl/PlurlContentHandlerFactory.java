/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.apache.felix.framework.plurl;

import java.net.ContentHandlerFactory;

/**
 * A {@link ContentHandlerFactory} that also implements {@link PlurlFactory}
 */
public interface PlurlContentHandlerFactory extends ContentHandlerFactory, PlurlFactory {
	// a marker interface for a ContentHandlerFactory that implements PlurlFactory
}

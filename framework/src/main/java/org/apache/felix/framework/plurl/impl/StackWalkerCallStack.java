/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *******************************************************************************/
package org.apache.felix.framework.plurl.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class StackWalkerCallStack implements CallStack {

	static final Class<?> stackWalkerClass;
	static final Object stackWalker;
	static final Method forEach;
	static final Method getDeclaringClass;
	static final CallStack fallback;
	static {
		Class<?> tmpStackWalkerClass = null;
		Object tmpStackWalker = null;
		Method tmpForEach = null;
		Method tmpGetDeclaringClass = null;
		CallStack tmpFallback = null;
		try {
			Class<?> stackWalkerOptionClass = Class.forName("java.lang.StackWalker$Option"); //$NON-NLS-1$
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Object RETAIN_CLASS_REFERENCE = Enum.valueOf((Class) stackWalkerOptionClass, "RETAIN_CLASS_REFERENCE"); //$NON-NLS-1$
			tmpStackWalkerClass = Class.forName("java.lang.StackWalker"); //$NON-NLS-1$
			tmpStackWalker = tmpStackWalkerClass.getMethod("getInstance", stackWalkerOptionClass).invoke(null, //$NON-NLS-1$
					RETAIN_CLASS_REFERENCE);
			tmpForEach = tmpStackWalkerClass.getMethod("forEach", Consumer.class); //$NON-NLS-1$
			tmpGetDeclaringClass = Class.forName("java.lang.StackWalker$StackFrame").getMethod("getDeclaringClass"); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (Throwable t) {
			// null all out
			tmpStackWalkerClass = null;
			tmpStackWalker = null;
			tmpForEach = null;
			tmpGetDeclaringClass = null;
			// fallback to security manager
			try {
				tmpFallback = new SecurityManagerCallStack();
			} catch (Throwable fallbackException) {
				// this is bad
				fallbackException.printStackTrace();
			}
		}
		stackWalkerClass = tmpStackWalkerClass;
		stackWalker = tmpStackWalker;
		forEach = tmpForEach;
		getDeclaringClass = tmpGetDeclaringClass;
		fallback = tmpFallback;
	}


	public Class<?>[] getClassContext() {
		if (fallback != null) {
			return fallback.getClassContext();
		}
		List<Class<?>> result = new ArrayList<>();
		if (stackWalker != null) {
			try {
				forEach.invoke(stackWalker, new Consumer<Object>() {
					public void accept(Object s) {
						try {
							result.add((Class<?>) getDeclaringClass.invoke(s));
						} catch (Throwable t) {
							t.printStackTrace();
						}
					}
				});
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		return result.toArray(new Class<?>[0]);
	}
}

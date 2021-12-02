/*
 * Copyright © 2018-2021 Jesse Gallagher
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
 */
package org.openntf.openliberty.domino.jvm;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

/**
 * Implementation of {@link JavaRuntimeProvider} that uses the current JVM
 * location.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class RunningJVMJavaRuntimeProvider implements JavaRuntimeProvider {
	public static final String TYPE_RUNNINGJVM = "RunningJVM"; //$NON-NLS-1$

	@Override
	public boolean canProvide(JVMIdentifier identifier) {
		return TYPE_RUNNINGJVM.equals(identifier.getType());
	}

	@Override
	public Path getJavaHome(JVMIdentifier identifier) {
		String javaHome = System.getProperty("java.home"); //$NON-NLS-1$
		if(StringUtil.isEmpty(javaHome)) {
			throw new IllegalStateException("Unable to locate Java home from java.home property");
		}
		return Paths.get(javaHome);
	}

}

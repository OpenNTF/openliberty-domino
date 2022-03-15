/*
 * Copyright © 2018-2022 Jesse Gallagher
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

/**
 * Defines a service that provides the location of a Java runtime to use for
 * launching servers.
 * 
 * @author Jesse Gallagher
 * @since 2.0.0
 */
public interface JavaRuntimeProvider {
	public static String SERVICE_ID = JavaRuntimeProvider.class.getName();
	
	/**
	 * Determines whether this instance can provide the given JVM type and version
	 * to the runtime.
	 * 
	 * @param identifier the desired JVM version
	 * @return {@code true} if this implementation can provide a Java home; {@code false} otherwise
	 * @since 3.0.0
	 */
	boolean canProvide(JVMIdentifier identifier);
	
	/**
	 * Provides a {@link Path} to the Java home for the requested Java version and type.
	 * 
	 * @param identifier the desired JVM version
	 * @return a {@link Path} to the requested runtime
	 */
	Path getJavaHome(JVMIdentifier identifier);
	
	/**
	 * Determines the priority of this provider relative to other implementations that can also
	 * provide the same type. A higher number means higher priority.
	 * 
	 * @return a priority value for this provider
	 */
	default int getPriority() {
		return 0;
	}
}

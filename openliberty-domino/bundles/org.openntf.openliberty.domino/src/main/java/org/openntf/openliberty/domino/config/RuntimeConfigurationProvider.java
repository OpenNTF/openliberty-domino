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
package org.openntf.openliberty.domino.config;

import java.nio.file.Path;

/**
 * This extension interface specifies a service that can provide global configuration
 * options.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public interface RuntimeConfigurationProvider {
	/**
	 * @return the base working directory for the runtime
	 */
	Path getBaseDirectory();
	
	/**
	 * @return the host name to use when connecting to Domino via HTTP
	 */
	String getDominoHostName();
	/**
	 * @return the port to use when connect to Domino via HTTP, or {@code -1}
	 * 		if HTTP is disabled
	 */
	int getDominoPort();
	/**
	 * @return whether to use TLS when connecting to Domino via HTTP
	 */
	boolean isDominoHttps();
	/**
	 * @return whether Domino is configured to use HTTP connector headers
	 */
	boolean isUseDominoConnectorHeaders();
	
	/**
	 * @return the current Domino runtime version
	 * @since 3.0.0
	 */
	String getDominoVersion();
	
	/**
	 * @return the current Domino executable directory
	 * @since 3.0.0
	 */
	Path getDominoProgramDirectory();
}

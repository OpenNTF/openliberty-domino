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
package org.openntf.openliberty.domino.server.wlp;

import java.io.InputStream;

/**
 * Defines a service that provides an ESA-packaged Liberty feature file
 * to be deployed alongside the servers.
 * 
 * <p>These services should be registered as a {@code ServiceLoader} service using the
 * {@code org.openntf.openliberty.domino.server.wlp.LibertyExtensionDeployer} name.</p>
 * 
 * @author Jesse Gallagher
 * @since 1.18004
 */
public interface LibertyExtensionDeployer {
	public static String SERVICE_ID = LibertyExtensionDeployer.class.getName();
	
	/**
	 * @return an {@link InputStream} for the ESA bundle file data
	 * @since 1.2.0
	 */
	InputStream getEsaData();
	
	/**
	 * @return the short name of the feature, e.g. {@code "notesRuntime"}
	 * @since 2.0.0
	 */
	String getShortName();
	
	/**
	 * @return the short version of the feature, e.g. {@code "1.0"}
	 * @since 2.0.0
	 */
	String getFeatureVersion();
}
